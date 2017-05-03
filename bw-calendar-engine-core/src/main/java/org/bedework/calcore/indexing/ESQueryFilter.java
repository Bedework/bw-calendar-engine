/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.calcore.indexing;

import org.bedework.calcorei.CalintfDefs;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.EntityTimeRangeFilter;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.filter.BwViewFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Logged;

import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MatchAllFilterBuilder;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.index.query.TermsFilterBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter extends Logged implements CalintfDefs {
  private final boolean publick;
  private final int currentMode;
  private final BwPrincipal principal;
  private final boolean superUser;

  private RecurringRetrievalMode recurRetrieval;

  /* True if we have a limitation to owner or collection */
  private boolean queryLimited;

  /* True if we have a filter on a property which is not owner or
     collection.

     These can only be satisfied by searching the instances */
  private boolean queryFiltered;
  
  private String latestStart;
  private String earliestEnd;

  private static final String entityTypeJname = getJname(PropertyInfoIndex.ENTITY_TYPE);
  public static final String colpathJname = getJname(PropertyInfoIndex.COLLECTION);
  //private static final String dtendJname = getJname(PropertyInfoIndex.DTEND);
  //private static final String dtstartJname = getJname(PropertyInfoIndex.DTSTART);

  /** */
  public static final String hrefJname = getJname(PropertyInfoIndex.HREF);
  private static final String ownerJname = getJname(PropertyInfoIndex.OWNER);
  private static final String publicJname = getJname(PropertyInfoIndex.PUBLIC);
  //public static final String recurrenceidJname =
  //        getJname(PropertyInfoIndex.RECURRENCE_ID);

  //private static String indexEndJname = getJname(PropertyInfoIndex.INDEX_END);
  //private static String indexStartJname = getJname(PropertyInfoIndex.INDEX_START);

  private final static String dtEndUTCRef = makePropertyRef(PropertyInfoIndex.DTEND,
                                                            PropertyInfoIndex.UTC);
  private final static String dtStartUTCRef = makePropertyRef(
          PropertyInfoIndex.DTSTART,
          PropertyInfoIndex.UTC);

  private final static String indexEndUTCRef = makePropertyRef(PropertyInfoIndex.INDEX_END,
                                                               PropertyInfoIndex.UTC);
  private final static String indexStartUTCRef = makePropertyRef(
          PropertyInfoIndex.INDEX_START,
          PropertyInfoIndex.UTC);

  private final static String nextTriggerRef = makePropertyRef(
          PropertyInfoIndex.VALARM,
          PropertyInfoIndex.NEXT_TRIGGER_DATE_TIME);
          
  /**
   *
   * @param publick true for a public indexer
   * @param currentMode - guest, user,publicAdmin
   * @param principal - only used to add a filter for non-public
   * @param superUser - true if the principal is a superuser.
   * @param recurRetrieval  - value modifies search
   */
  public ESQueryFilter(final boolean publick,
                       final int currentMode,
                       final BwPrincipal principal,
                       final boolean superUser,
                       final RecurringRetrievalMode recurRetrieval) {
    this.publick = publick;
    this.currentMode = currentMode;
    this.principal = principal;
    this.superUser = superUser;
    this.recurRetrieval = recurRetrieval;
    if (recurRetrieval == null) {
      this.recurRetrieval = RecurringRetrievalMode.expanded;
    }
  }

  /** Build a filter for a single entity identified by the property
   * reference and value. The search will be limited by the current
   * mode.
   *
   * @param doctype  type of document we want
   * @param val the value to match
   * @param index list of terms for dot reference
   * @return
   * @throws CalFacadeException
   */
  public FilterBuilder singleEntityFilter(final String doctype,
                                          final String val,
                                          final PropertyInfoIndex... index) throws CalFacadeException {
    return and(addTerm("_type", doctype),
               addTerm(makePropertyRef(index), val),
               null);
  }

  public FilterBuilder buildFilter(final FilterBase f) throws CalFacadeException {
    FilterBuilder fb = makeFilter(f);

    if (fb instanceof TermOrTerms) {
      fb = makeFilter((TermOrTerms)fb);
    }

    return fb;
  }

  public FilterBuilder multiHrefFilter(final Set<String> hrefs,
                                       final RecurringRetrievalMode rmode) throws CalFacadeException {
    FilterBuilder fb = null;

    for (final String href: hrefs) {
      fb = or(fb, addTerm(hrefJname, href));
    }

    if (rmode.mode == Rmode.entityOnly) {
      FilterBuilder limit = not(addTerm("_type",
                                        BwIndexer.docTypeEvent));

      limit = or(limit,
                 addTerm(PropertyInfoIndex.MASTER, "true"));

      return and(fb, limit, null);
    }

    if (rmode.mode == Rmode.overrides) {
      final FilterBuilder limit =
              or(addTerm(PropertyInfoIndex.MASTER, "true"),
                 addTerm(PropertyInfoIndex.OVERRIDE, "true"));

      return and(fb, limit, null);
    }

    return fb;
  }

  private static class NamedFilterBuilder {
    final String name;
    final FilterBuilder fb;

    public NamedFilterBuilder(final String name,
                              final FilterBuilder fb) {
      this.name = name;
      this.fb = fb;
    }
  } 
  
  /** This method adds extra limits to the search if they are necessary.
   * If the search is already limited to one or more collections or 
   * specific href(s) then we don't need to add anything.
   * 
   * <p>Otherwise we apply a default search context (if any). If the 
   * result is still not limited we limit to entities owned by the 
   * current principal</p>
   * 
   * @param f current filter
   * @param defaultFilterContext set if we have one
   * @return augmented filter
   * @throws CalFacadeException on error
   */
  public FilterBuilder addLimits(final FilterBuilder f,
                                 final FilterBase defaultFilterContext) throws CalFacadeException {
    if (f instanceof MatchNone) {
      return f;
    }
    
    final List<NamedFilterBuilder> nfbs = new ArrayList<>();

    if (!queryLimited) {
      if (defaultFilterContext != null) {
        if (defaultFilterContext instanceof BwViewFilter) {
          /* Treat this specially. Create a named query for each 
             child filter. The name will be the filter name which
             itself is derived from the collection href.
          */

          final FilterBase fb = 
                  ((BwViewFilter)defaultFilterContext).getFilter();
          
          if (fb != null) {
            for (final FilterBase vfb : fb.getChildren()) {
              nfbs.add(new NamedFilterBuilder(vfb.getName(),
                                              and(buildFilter(vfb), f,
                                                  vfb.getName())));
            }
          }
        } else {
          final FilterBuilder limFb = buildFilter(
                  defaultFilterContext);
          nfbs.add(new NamedFilterBuilder(null, and(f, limFb, null)));
        }
      }

      if (!queryLimited) {
        nfbs.add(new NamedFilterBuilder(null, principalFilter(f)));
      }
    } else {
      nfbs.add(new NamedFilterBuilder(null, f));
    }
    
    FilterBuilder recurFb = recurTerms();
    
    if (nfbs.size() == 1) {
      final FilterBuilder fb = nfbs.get(0).fb;

      if (recurFb == null) {
        return fb;
      }

      return and(fb, recurFb, null);
    }

    FilterBuilder fb = null;
    if (recurFb == null) {
      recurFb = new MatchAllFilterBuilder();
    }
    
    for (final NamedFilterBuilder nfb: nfbs) {
      fb = or(fb, and(nfb.fb, recurFb, nfb.name));
    }
    
    return fb;
  }
  
  final FilterBuilder recurTerms() throws CalFacadeException {
    /* If the search is for expanded events we want instances or
       overrides or non-recurring masters only.

       For non-expanded, if it's filtered we don't limit -
       the filter needs to check the instances and maybe the overrides.
       However, we will return only the hrefs and do a secondary
       retrieval of the events.

       For non-expanded and non-filtered we want the master and
       overrides only.
     */

    if (recurRetrieval.mode == Rmode.expanded) {
      // Limit events to instances only //
      FilterBuilder limit = not(addTerm("_type",
                                        BwIndexer.docTypeEvent));

      limit = or(limit, addTerm(PropertyInfoIndex.INSTANCE, "true"));
      limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

      limit = or(limit,
                 and(addTerm(PropertyInfoIndex.MASTER, "true"),
                     addTerm(PropertyInfoIndex.RECURRING, "false"),
                     null));

      return limit;
    }

    if (recurRetrieval.mode == Rmode.entityOnly) {
      FilterBuilder limit = not(addTerm("_type",
                                        BwIndexer.docTypeEvent));

      limit = or(limit,
                 addTerm(PropertyInfoIndex.MASTER, "true"));

      return limit;
    }

    /* if the query is not filtered we can limit to the master and
       overrides only
     */

    if (queryFiltered) {
      return null;
    }

    FilterBuilder limit = not(addTerm("_type",
                                      BwIndexer.docTypeEvent));

    limit = or(limit, addTerm(PropertyInfoIndex.MASTER, "true"));

    limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

//    queryFiltered = false; // Reset it.

    return limit;
  }

  /**
   * 
   * @return non null if we have a start date/time limitation
   */
  public String getLatestStart() {
    return latestStart;
  }

  /**
   *
   * @return non null if we have an end date/time limitation
   */
  public String getEarliestEnd() {
    return earliestEnd;
  }

  /** If we have a filtered query the search result will only contain
   * the master and/or overrides that match the query.
   *
   * <p> If we are not returning expansions we need to return the
   * entire event - master and all overrides. FOr non-expanded we
   * have to do a fetch of the entire event so we don't want parts of
   * it returned for the search. Instead we'll just ask for the matching
   * hrefs and then do a fetch for those.
   *</p>
   *
   * @return true if we are not fetching expanded and we have a filtered
   *          query which examines the instances. In this case we
   *          fetch only the href and do a secondary fetch for master
   *          and overrides.
   */
  public boolean requiresSecondaryFetch() {
    return (recurRetrieval.mode != Rmode.expanded) &&
            queryFiltered;
  }

  /**
   *
   * @return true if we are fetching expanded.
   */
  public boolean canPage() {
    return recurRetrieval.mode == Rmode.expanded;
  }

  /** Add date range terms to filter. The actual terms depend on
   * how we want recurring events returned - expanded or as master
   * and overrides.
   *
   * <p>For expanded we just search on the actual dtstart and
   * end. This will find all the entities that fall in the desired
   * range</p>
   *
   * <p>For master + overrides we need to return any override that
   * overrides an instance in the range but itself is outside the
   * range. For these we search on the index start/end</p>
   *
   * @param filter
   * @param entityType
   * @param start
   * @param end
   * @return the filter or a filter with anded date range
   * @throws CalFacadeException on error
   */
  public FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                          final int entityType,
                                          final String start,
                                          final String end) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return filter;
    }

    queryFiltered = true;
    
    final String startRef;
    final String endRef;

    if (entityType == IcalDefs.entityTypeAlarm) {
      return and(filter, range(nextTriggerRef, start, end), null);
    }      
     
    if (recurRetrieval.mode == Rmode.expanded) {
      startRef = dtStartUTCRef;
      endRef = dtEndUTCRef;
    } else {
      startRef = indexStartUTCRef;
      endRef = indexEndUTCRef;
    }

    FilterBuilder fb = filter;

    if (end != null) {
      // Start of events must be before the end of the range
      final RangeFilterBuilder rfb = new RangeFilterBuilder(startRef);

      rfb.lt(dateTimeUTC(end));

      fb = and(fb, rfb, null);
    }

    if (start != null) {
      // End of events must be before the end of the range
      final RangeFilterBuilder rfb = new RangeFilterBuilder(endRef);

      rfb.gte(dateTimeUTC(start));
      rfb.includeLower(false);

      fb = and(fb, rfb, null);
    }
    
    adjustTimeLimits(start, end);

    return fb;
  }

  /** Add a filter for the given href.
   *
   * @param filter - or null
   * @param href to match
   * @return a filter
   * @throws CalFacadeException
   */
  public FilterBuilder hrefFilter(final FilterBuilder filter,
                                  final String href) throws CalFacadeException {
    return and(filter,
               FilterBuilders.termFilter(hrefJname, href),
               null);
  }

  /** Add filter for the current principal - or public - to limit search
   * to entities owned by the current principal.
   *
   * @param filter - or null
   * @return a filter
   * @throws CalFacadeException on error
   */
  public FilterBuilder principalFilter(final FilterBuilder filter) throws CalFacadeException {
    final boolean publicEvents = publick ||
            (currentMode == guestMode) ||
            (currentMode == publicAdminMode);

    //boolean all = (currentMode == guestMode) || ignoreCreator;

    if (publicEvents) {
      return and(filter,
               FilterBuilders.termFilter(
                       publicJname,
                       String.valueOf(true)),
                 null);
    }

    if (superUser) {
      return filter;
    }

    return and(filter, FilterBuilders.termFilter(ownerJname,
                                                 principal.getPrincipalRef()),
               null);
  }

  /**
   *
   * @param pi property info index
   * @param val value
   * @return TermFilterBuilder or AndFilterBuilder
   * @throws CalFacadeException on error
   */
  public FilterBuilder addTerm(final PropertyInfoIndex pi,
                               final String val) throws CalFacadeException {
    if ((pi != PropertyInfoIndex.HREF) &&
            (pi != PropertyInfoIndex.COLLECTION)) {
      queryFiltered = true;
    }
    return addTerm(getJname(pi), val);
  }

  /**
   *
   * @param name
   * @param val
   * @return TermFilterBuilder or AndFilterBuilder
   * @throws CalFacadeException
   */
  public FilterBuilder addTerm(final String name,
                               final String val) throws CalFacadeException {
    return FilterBuilders.termFilter(name, val);
  }

  /**
   *
   * @param pis
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final List<PropertyInfoIndex> pis) {
    String delim = "";

    StringBuilder sb = new StringBuilder();

    for (PropertyInfoIndex pi: pis) {
      sb.append(delim);
      sb.append(getJname(pi));
      delim = ".";
    }

    return sb.toString();
  }

  /**
   *
   * @param pis
   * @return dot delimited property reference
   */
  public static String makePropertyRef(PropertyInfoIndex... pis) {
    String delim = "";

    StringBuilder sb = new StringBuilder();

    for (PropertyInfoIndex pi: pis) {
      sb.append(delim);
      sb.append(getJname(pi));
      delim = ".";
    }

    return sb.toString();
  }

  private FilterBuilder not(final FilterBuilder filter) {
    return new NotFilterBuilder(filter);
  }

  private FilterBuilder and(final FilterBuilder filter,
                            final FilterBuilder newFilter,
                            final String name) {
    if (filter == null) {
      return newFilter;
    }

    if (filter instanceof AndFilterBuilder) {
      ((AndFilterBuilder)filter).add(newFilter);

      return filter;
    }

    final AndFilterBuilder afb = new AndFilterBuilder(filter);

    afb.add(newFilter);
    
    if (name != null) {
      afb.filterName(name);
    }

    return afb;
  }

  private FilterBuilder or(final FilterBuilder filter,
                           final FilterBuilder newFilter) {
    if (filter == null) {
      return newFilter;
    }

    if (filter instanceof OrFilterBuilder) {
      ((OrFilterBuilder)filter).add(newFilter);

      return filter;
    }

    OrFilterBuilder ofb = new OrFilterBuilder(filter);

    ofb.add(newFilter);

    return ofb;
  }

  static String getJname(final PropertyInfoIndex pi) {
    final BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  private String dateTimeUTC(final String dt) {
    if (dt == null) {
      return null;
    }
    
    if (dt.length() == 16) {
      return dt;
    }

    if (dt.length() == 15) {
      return dt + "Z";
    }

    if (dt.length() == 8) {
      return dt + "T000000Z";
    }

    return dt; // It's probably a bad date
  }

  private FilterBuilder doTimeRange(final TimeRange tr,
                                    final boolean dateTimeField,
                                    final String fld,
                                    final String subfld) {
    final RangeFilterBuilder rfb = FilterBuilders.rangeFilter(fld);
    final String start;
    final String end;

    if (tr.getEnd() == null) {
      end = null;
    } else {
      end = tr.getEnd().toString();
    }
      
    if (tr.getStart() == null) {
      start = null;
    } else {
      start = tr.getStart().toString();
    }

    return range(fld, start, end);
  }
  
  private RangeFilterBuilder range(final String fld, 
                                   final String start, 
                                   final String end) {
    if ((start == null) && (end == null)) {
      return null;
    }
    
    final RangeFilterBuilder rfb = FilterBuilders.rangeFilter(fld);
    
    if (end == null) {
      rfb.gte(start);
    } else if (start == null) {
      rfb.lt(end);
    } else {
      rfb.from(start);
      rfb.to(end);
    }
    rfb.includeLower(true);
    rfb.includeUpper(false);

    adjustTimeLimits(start, end);

    return rfb;
  }
  
  private void adjustTimeLimits(final String start, final String end) {
    final String s = dateTimeUTC(start);
    final String e = dateTimeUTC(end);
    
    if (s != null) {
      if ((latestStart == null) ||
              (s.compareTo(latestStart) > 0)) {
        latestStart = s;
      }
    }

    if (e != null) {
      if ((earliestEnd == null) ||
              (e.compareTo(earliestEnd) < 0)) {
        earliestEnd = e;
      }
    }
  }

  private List<FilterBuilder> makeFilters(final List<FilterBase> fs,
                                          final boolean anding) throws CalFacadeException {
    List<FilterBuilder> fbs = new ArrayList<>();

    /* We'll try to compact the filters - if we have a whole bunch of
          "term" {"category_uid": "abcd"} for example we can turn it into
          "terms" {"category_uid": ["abcd", "pqrs"]}
     */
    TermOrTerms lastFb = null;

    for (FilterBase f: fs) {
      FilterBuilder fb = makeFilter(f);

      if (fb == null) {
        continue;
      }

      if (lastFb == null) {
        if (!(fb instanceof TermOrTerms)) {
          fbs.add(fb);
          continue;
        }

        lastFb = (TermOrTerms)fb;
      } else if (!(fb instanceof TermOrTerms)) {
        fbs.add(fb);
      } else {
        /* Can we combine them? */
        TermOrTerms thisFb = (TermOrTerms)fb;

        if (thisFb.dontMerge ||
                !lastFb.fldName.equals(thisFb.fldName) ||
                (lastFb.not != thisFb.not)) {
          fbs.add(lastFb);
          lastFb = thisFb;
        } else {
          lastFb = lastFb.anding(anding);

          if (thisFb.isTerms) {
            for (Object o: (Collection)thisFb.value) {
              lastFb.addValue(o);
            }
          } else {
            lastFb.addValue(thisFb.value);
          }
        }
      }
    }

    if (lastFb != null) {
      fbs.add(lastFb);
    }

    return fbs;
  }

  private FilterBuilder makeFilter(final TermOrTerms tort) throws CalFacadeException {
    final boolean hrefOrPath = tort.fldName.equals(hrefJname) ||
            tort.fldName.equals(colpathJname);
    
    queryFiltered |= !hrefOrPath;

    if (!tort.not) {
      queryLimited |= hrefOrPath;
    }
    
    return tort.makeFb();
  }

  private FilterBuilder makeFilter(final FilterBase f) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if (f instanceof BooleanFilter) {
      final BooleanFilter bf = (BooleanFilter)f;
      if (!bf.getValue()) {
        return new MatchNone();
      } else {
        return new MatchAllFilterBuilder();
      }
    }
    
    if (f instanceof AndFilter) {
      final List<FilterBuilder> fbs = makeFilters(f.getChildren(), true);

      if (fbs.size() == 1) {
        return fbs.get(0);
      }

      final AndFilterBuilder afb = new AndFilterBuilder();
      afb.filterName(f.getName());

      for (final FilterBuilder fb: fbs) {
        if (fb instanceof TermOrTerms) {
          afb.add(makeFilter((TermOrTerms)fb));
        } else {
          afb.add(fb);
        }
      }

      return afb;
    }

    if (f instanceof OrFilter) {
      final List<FilterBuilder> fbs = makeFilters(f.getChildren(), false);

      if (fbs.size() == 1) {
        return fbs.get(0);
      }

      final OrFilterBuilder ofb = new OrFilterBuilder();
      ofb.filterName(f.getName());

      for (final FilterBuilder fb: fbs) {
        if (fb instanceof TermOrTerms) {
          ofb.add(((TermOrTerms)fb).makeFb());
        } else {
          ofb.add(fb);
        }
      }

      return ofb;
    }

    if (f instanceof BwViewFilter) {
      return doView((BwViewFilter)f);
    }

    if (f instanceof BwHrefFilter) {
      queryLimited = true;

      final TermFilterBuilder tfb = 
              FilterBuilders.termFilter(hrefJname,
                                        ((BwHrefFilter)f).getHref());
      tfb.filterName(f.getName());
      return tfb;
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    final PropertyFilter pf = (PropertyFilter)f;
/*
    if (pf.getPropertyIndex() == PropertyInfoIndex.CATEGORY_PATH) {
      // Special case this one.
      return new TermOrTerms(PropertyInfoIndex.CATEGORY_PATH.getJname(),
                             ((ObjectFilter)pf).getEntity());
    }*/

    final BwIcalPropertyInfo.BwIcalPropertyInfoEntry pi =
            BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    final String fieldName;

    final PropertyInfoIndex pii = pf.getPropertyIndex();

    if (pf.getParentPropertyIndex() != null) {
      fieldName = makePropertyRef(pf.getParentPropertyIndex(), pii);
    } else {
      fieldName = getJname(pii);
    }

    if (f instanceof PresenceFilter) {
      final PresenceFilter prf = (PresenceFilter)f;

      if (prf.getTestPresent()) {
        return FilterBuilders.existsFilter(fieldName);
      }

      return FilterBuilders.missingFilter(fieldName);
    }

    if (pf instanceof TimeRangeFilter) {
      return doTimeRange(((TimeRangeFilter)pf).getEntity(),
                         false, fieldName, null);
    }

    /*
    if (pf instanceof BwCreatorFilter) {
      return new TermOrTerms("creator",
                             ((BwCreatorFilter)pf).getEntity(),
                             pf.getNot());
    }

    if (pf instanceof BwCategoryFilter) {
      BwCategory cat = ((BwCategoryFilter)pf).getEntity();
      return new TermOrTerms(PropertyInfoIndex.CATEGORIES.getJname() +
              "." + PropertyInfoIndex.UID.getJname(),
                             cat.getUid());
    }*/

    if (pf instanceof BwCollectionFilter) {
      if (!pf.getNot()) {
        queryLimited = true;
      }

      final BwCalendar col = ((BwCollectionFilter)pf).getEntity();

      return new TermOrTerms(colpathJname,
                             col.getPath(),
                             pf.getNot(),
                             f.getName());
    }

    if (pf instanceof EntityTypeFilter) {
      final EntityTypeFilter etf = (EntityTypeFilter)pf;

      return new TermOrTerms(entityTypeJname,
                             IcalDefs.entityTypeNames[etf.getEntity()],
                             pf.getNot(),
                             f.getName());
    }

    if (pf instanceof EntityTimeRangeFilter) {
      final EntityTimeRangeFilter etrf = (EntityTimeRangeFilter)pf;

      final TimeRange tr = etrf.getEntity();
      String start = null;
      String end = null;

      if (tr.getStart() != null) {
        start = tr.getStart().toString();
      }

      if (tr.getEnd() != null) {
        end = tr.getEnd().toString();
      }

      return addDateRangeFilter(null, etrf.getEntityType(),
                                start, end);
    }

    if (pii == PropertyInfoIndex.COLLECTION) {
      if (!pf.getNot()) {
        queryLimited = true;
      }
    }

    if (pf instanceof ObjectFilter) {
      return doObject((ObjectFilter)pf, fieldName, null);
    }

    return null;

    /*
    if (f instanceof TimeRangeFilter) {
      addThisJoin(pi);
      return;
    }

    if (f instanceof BwObjectFilter) {
      addThisJoin(pi);
      return;
    }*/
  }

  private FilterBuilder doView(final BwViewFilter vf) throws CalFacadeException {
    final FilterBuilder fb = makeFilter(vf.getFilter());

    return fb;
  }

  private FilterBuilder doObject(final ObjectFilter of,
                                 final String fld,
                                 final String subfld) throws CalFacadeException {
    String dbfld = fld;

    if (subfld != null) {
      dbfld += "." + subfld;
    }

    Object o = of.getEntity();

    Object val = getValue(of);
    FilterBuilder fb;

    if (val instanceof Collection) {
      TermsFilterBuilder tfb = FilterBuilders.termsFilter(dbfld, (Collection)val);
      tfb.execution("or");

      fb = tfb;
    } else {
      fb = FilterBuilders.termFilter(dbfld, val);
    }

    if (!of.getNot()) {
      return fb;
    }

    return new NotFilterBuilder(fb);
  }

  private Object getValue(final ObjectFilter of) throws CalFacadeException {
    Object o = of.getEntity();
    Collection c = null;

    boolean isString = o instanceof String;

    if (!isString) {
      if (o instanceof Collection) {
        c = (Collection)o;
        if (c.size() > 0) {
          o = c.iterator().next();
          isString = o instanceof String;
        }
      }
    }

    boolean doCaseless = isString && of.getCaseless();

    if (c != null) {
      // TODO - Assuming String collection
      List res = new ArrayList();

      for (Object co: c) {
        String s = (String)co;
        if (doCaseless) {
          s = s.toLowerCase();
        }

        res.add(s);
      }

      return res;
    }

    if (o instanceof BwCalendar) {
      BwCalendar cal = (BwCalendar)o;
      return cal.getPath();
    }

    if (o instanceof BwPrincipal) {
      return ((BwPrincipal)o).getPrincipalRef();
    }

    if (o instanceof BwDbentity) {
      warn("Got db entity in search " + o);
      return "";
    }

    if (of.getExact()) {
      if (doCaseless) {
        o = ((String)o).toLowerCase();
      }

      return o;
    }

    if (of.getEntity() instanceof String) {
      String s = o.toString();

      if (doCaseless) {
        s = s.toLowerCase();
      }

      //warn("Fuzzy search for " + of);
      return s;
      //sess.setString(parPrefix + qi, "%" + s + "%");
    }

    //sess.setString(parPrefix + qi, "%" + o + "%");

    warn("Fuzzy search for " + of);
    return "";
  }
}
