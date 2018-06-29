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
package org.bedework.calcore.common.indexing;

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
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.elasticsearch.ESQueryFilterBase;

import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.BoolFilterBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.FilteredQueryBuilder;
import org.elasticsearch.index.query.MatchAllFilterBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.NestedFilterBuilder;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.index.query.TermFilterBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsFilterBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.ScoreFunctionBuilders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.includeDeleted;
import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.onlyDeleted;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter extends ESQueryFilterBase implements CalintfDefs {
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
   * @return a filter builder
   */
  public FilterBuilder singleEntityFilter(final String doctype,
                                          final String val,
                                          final PropertyInfoIndex... index) {
    return and(addTerm("_type", doctype),
               addTerm(makePropertyRef(index), val),
               null);
  }

  /** Build a filter for a single event identified by the href
   * and recurrenceid. If recurrenceId is null we target a master only
   * otherwise we target an instance only.
   *
   * @param href  of event
   * @param recurrenceId of instance
   * @return a filter builder
   */
  public FilterBuilder singleEventFilter(final String href,
                                          final String recurrenceId) {
    FilterBuilder anded = and(addTerm("_type", docTypeEvent),
                              addTerm(makePropertyRef(PropertyInfoIndex.HREF), href),
                              null);

    if (recurrenceId == null) {
      anded = and(anded, addTerm(PropertyInfoIndex.MASTER, "true"), null);
    } else {
      anded = and(anded, addTerm(PropertyInfoIndex.INSTANCE, "true"),
                  null);
      anded = and(anded, addTerm(PropertyInfoIndex.RECURRENCE_ID,
                                 recurrenceId),
                  null);
    }

    return anded;
  }

  /** Build a filter for a single event identified by the colPath
   * and uid.
   *
   * @param colPath to event
   * @param guid of event
   * @return a filter builder
   */
  public FilterBuilder singleEventFilterGuid(final String colPath,
                                             final String guid) {
    FilterBuilder anded = and(addTerm("_type", docTypeEvent),
                              addTerm(makePropertyRef(PropertyInfoIndex.COLPATH), colPath),
                              null);

    anded = and(anded, addTerm(makePropertyRef(PropertyInfoIndex.UID), guid),
                null);
    anded = and(anded, addTerm(PropertyInfoIndex.MASTER, "true"), null);

    return anded;
  }

  /** Build a filter for a single location identified by the key with
   * the given name and value.
   *
   * @param name - of key field
   * @param val - expected full value
   * @return a filter builder
   */
  public FilterBuilder locationKeyFilter(final String name,
                                         final String val) throws CalFacadeException {
    final ObjectFilter<String> kf =
            new ObjectFilter<>(null,
                               PropertyInfoIndex.LOC_KEYS_FLD,
                               null,
                               name);
    kf.setEntity(val);
    kf.setExact(true);
    kf.setCaseless(false);

    return and(addTerm("_type",
                       docTypeLocation),
               buildFilter(kf),
               "and");
  }

  public FilterBase addTypeFilter(final FilterBase f,
                                  final String docType) {
    final ObjectFilter<String> ef =
            new ObjectFilter<>(null,
                               PropertyInfoIndex.DOCTYPE,
                               null,
                               null);
    ef.setEntity(docType);
    ef.setExact(true);
    ef.setCaseless(false);

    final AndFilter and = new AndFilter();

    and.addChild(ef);
    and.addChild(f);

    return and;
  }

  public FilterBuilder buildFilter(final FilterBase f) throws CalFacadeException {
    FilterBuilder fb = makeFilter(f);

    if (fb instanceof TermOrTerms) {
      fb = makeFilter((TermOrTerms)fb);
    }

    return fb;
  }

  public QueryBuilder buildQuery(final FilterBase f) throws CalFacadeException {
    QueryBuilder qb = makeQuery(f);

    if (qb instanceof TermOrTermsQuery) {
      qb = ((TermOrTermsQuery)qb).makeQb();
    }

    return qb;
  }

  /* TODO we need to provide a chain of filters when we have deep paths,
       e.g. entity[key1].entity[key2].value = "something"
   */
  public FilterBuilder makeFilter(final List<PropertyInfoIndex> pis,
                                  final Object val,
                                  final Integer intKey,
                                  final String strKey,
                                  final OperationType opType,
                                  final boolean negate) throws CalFacadeException {
    /* Work backwards through the property list building a path.
       When the head of the path is a nested type:
         If it's the first we found:
            generate a match or term query based on the leaf
         otherwise:
            we already have a nested query to push inside a new one
    
       If the top entry has a keyindex we expect a String or Numeric 
       key value we generate a bool query with 2 must match terms.
     */

    FilterBuilder fb = null;
    FilterBuilder nfb = null; // current nested level
    PropertyInfoIndex leafPii = null;

    /* See if we need to build a nested query */
    final BwIcalPropertyInfoEntry rootPie = 
            BwIcalPropertyInfo.getPinfo(pis.get(0));
    final boolean isNested = rootPie.getNested();

    for (int plistIndex = pis.size() - 1; plistIndex >= 0; plistIndex--) {
      final PropertyInfoIndex pii = pis.get(plistIndex);

      if (leafPii == null) {
        leafPii = pii;
      }
      
      final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pii);

      final String fullTermPath;
      if (bwPie.getTermsField() == null) {
        fullTermPath = null;
      } else {
        fullTermPath = makePropertyRef(pis, plistIndex,
                                       bwPie.getTermsField());
      }

      if (isNested) {
        final FilterBuilder nested;
        String path = makePropertyRef(pis, plistIndex, null);

        if (nfb != null) {
          if (plistIndex == 0) {
            // TODO Temp fix this
            path = "event." + path;
          }
          nested = new NestedFilterBuilder(path, nfb);
        } else {
          fb = makeFilter(leafPii,
                         makePropertyRef(pis, null),
                         fullTermPath,
                         val, opType);

          /* Is the parent indexed? */
          final BwIcalPropertyInfoEntry parentPie;
          if (plistIndex == 0) {
            // No parent
            parentPie = null;
          } else {
            parentPie = BwIcalPropertyInfo.getPinfo(pis.get(plistIndex - 1));
          }
          
          if ((parentPie != null) && 
                  (parentPie.getKeyindex() != PropertyInfoIndex.UNKNOWN_PROPERTY)) {
            final BoolFilterBuilder bfb = new BoolFilterBuilder();

            if (fb == null) {
              error("No nested query for " + pii);
              return null;
            }
            bfb.must(fb);
            
            final List<PropertyInfoIndex> indexPis = new ArrayList<>();
            indexPis.add(pis.get(plistIndex - 1));
            indexPis.add(parentPie.getKeyindex());
            final String indexPath = makePropertyRef(indexPis, null);

            if (intKey != null) {
              bfb.must(new TermFilterBuilder(indexPath, 
                                            intKey));
            } else if (strKey != null) {
              bfb.must(new TermFilterBuilder(indexPath, 
                                            strKey));
            } else {
              error("Missing key for index for " + pii);
              return null;
            }
            
            fb = bfb;
          }
           
          nested = fb;
        }

        nfb = nested;
      } else if (plistIndex == 0) {
        // No nested types found
        fb = makeFilter(leafPii, makePropertyRef(pis, null),
                        fullTermPath,
                        val, opType);
      }
    }

    if (nfb != null) {
      fb = nfb;
    }

    if (negate) {
      return FilterBuilders.notFilter(fb);
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
                                        docTypeEvent));

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
                                 final FilterBase defaultFilterContext,
                                 final DeletedState delState) throws CalFacadeException {
    if ((f != null) && (f instanceof MatchNone)) {
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
          
          if ((fb != null) && (fb.getChildren() != null)) {
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
        if (f == null) {
          nfbs.add(new NamedFilterBuilder(null, new MatchAllFilterBuilder()));
        } else {
          nfbs.add(new NamedFilterBuilder(null, principalFilter(f)));
        }
      }
    } else if (f == null) {
      nfbs.add(new NamedFilterBuilder(null, new MatchAllFilterBuilder()));
    } else {
      nfbs.add(new NamedFilterBuilder(null, f));
    }
    
    FilterBuilder recurFb = recurTerms();

    FilterBuilder fb;

    if (nfbs.size() == 1) {
      fb = nfbs.get(0).fb;

      if (recurFb != null) {
        fb = and(fb, recurFb, null);
      }
    } else {
      fb = null;
      if (recurFb == null) {
        recurFb = new MatchAllFilterBuilder();
      }

      for (final NamedFilterBuilder nfb : nfbs) {
        fb = or(fb, and(nfb.fb, recurFb, nfb.name));
      }
    }

    if (delState == includeDeleted) {
      return fb;
    }

    return and(fb,
               addTerm(PropertyInfoIndex.DELETED,
                       String.valueOf(delState == onlyDeleted)),
               null);

  }
  
  final FilterBuilder overridesOnly(final String uid) {
    FilterBuilder flt = new TermFilterBuilder("_type",
                                              docTypeEvent);
    
    flt = and(flt, addTerm(PropertyInfoIndex.UID, uid), null);
    return and(flt, addTerm(PropertyInfoIndex.OVERRIDE, "true"), null);
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
                                        docTypeEvent));

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
                                        docTypeEvent));

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
                                      docTypeEvent));

    limit = or(limit, addTerm(PropertyInfoIndex.MASTER, "true"));

    limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

//    queryFiltered = false; // Reset it.

    return limit;
  }
  
  public QueryBuilder getAllForReindex(final String docType) {
    FilterBuilder limit;
    if (docType != null) {
      limit = addTerm("_type", docType);
    } else {
      FilterBuilder f = addTerm("_type",
                                      BwIndexer.docTypeCategory);
      f = or(f, addTerm("_type",
                        BwIndexer.docTypeContact));
      f = or(f, addTerm("_type",
                        BwIndexer.docTypeLocation));
      f = or(f, addTerm("_type",
                        docTypeEvent));
      limit = not(f);

      f = addTerm("_type",
                  docTypeEvent);
      f = and(f, addTerm(PropertyInfoIndex.MASTER, "true"), null);
      limit = or(limit, f);
    }

    return new FilteredQueryBuilder(null, limit);
  }

  public QueryBuilder getAllForReindexStats() {
    FilterBuilder limit = not(addTerm("_type",
                                      docTypeEvent));

    limit = or(limit, addTerm(PropertyInfoIndex.MASTER, "true"));

    limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

    return new FilteredQueryBuilder(null, limit);
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
   */
  public FilterBuilder addTerm(final PropertyInfoIndex pi,
                               final String val) {
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
   */
  public FilterBuilder addTerm(final String name,
                               final String val) {
    return FilterBuilders.termFilter(name, val);
  }

  /**
   *
   * @param pis the refs
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final List<PropertyInfoIndex> pis,
                                       final String lastElJname) {
    return makePropertyRef(pis, pis.size() - 1, lastElJname);
  }

  /**
   *
   * @param pis the refs
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final List<PropertyInfoIndex> pis,
                                       final int numElements,
                                       final String lastElJname) {
    String delim = "";
    final int last = numElements - 1;

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i <= numElements; i++) {
      sb.append(delim);
      delim = ".";

      if ((i < last) || (lastElJname == null)) {
        final PropertyInfoIndex pi = pis.get(i);
        sb.append(getJname(pi));
      } else {
        sb.append(lastElJname);
      }
    }

    return sb.toString();
  }

  /**
   *
   * @param pis list of indexes
   * @return dot delimited property reference
   */
  public static String makePropertyRef(final PropertyInfoIndex... pis) {
    String delim = "";

    final StringBuilder sb = new StringBuilder();

    for (final PropertyInfoIndex pi: pis) {
      sb.append(delim);
      sb.append(getJname(pi));
      delim = ".";
    }

    return sb.toString();
  }

  private QueryBuilder makeQuery(final PropertyInfoIndex pii,
                                 final String path,
                                 final String fullTermPath,
                                 final Object val,
                                 final OperationType opType,
                                 final float boost) throws CalFacadeException {
    final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pii);

    switch (opType) {
      case compare:
        if (bwPie.getAnalyzed()) {
          if (bwPie.getTermsField() == null) {
            return new MatchQueryBuilder(path, val).operator(
                    MatchQueryBuilder.Operator.AND);
          }

          /* Build something like this
              "query" : {
                "function_score" : {
                  "query" : {
                    "match" : {
                      "loc_all" : {
                        "query" : "hill",
                        "operator" : "AND"
                      }
                    }
                  },
                  "functions" : [{
                    "weight": 10,
                    "filter": {
                      "term" : {
                        "loc_all_terms" : "hill"
                      }
                    }
                  }]
                }
              },...
           */
          QueryBuilder qb = new MatchQueryBuilder(path, val).operator(
                  MatchQueryBuilder.Operator.AND);
          final FunctionScoreQueryBuilder fsqb =
                  new FunctionScoreQueryBuilder(qb);

          final FilterBuilder fb = new TermFilterBuilder(fullTermPath,
                                                         val);
          fsqb.add(fb, ScoreFunctionBuilders.weightFactorFunction(10));

          return fsqb;
        }

        final TermQueryBuilder tqb = new TermQueryBuilder(path, val);
        if (boost != 1) {
          tqb.boost(boost);
        }
        return tqb;
      case prefix:
      case timeRange:
      case absence:
      case presence:
        throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                     opType.toString());
      default:
        throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                     opType.toString());
    }
  }

  private FilterBuilder makeFilter(final PropertyInfoIndex pii,
                                   final String path,
                                   final String fullTermPath,
                                   final Object val,
                                   final OperationType opType) throws CalFacadeException {
    final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pii);

    switch (opType) {
      case compare:
        if (bwPie.getAnalyzed()) {
          if (val instanceof Collection) {
            final String[] vals;

            try {
              final Collection valsC = (Collection)val;
              vals = (String[]) (valsC).toArray(new String[valsC.size()]);
            } catch (final Throwable t) {
              throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                           "Invalid query. Multi match only allowed on strings");
            }
            final MultiMatchQueryBuilder mmqb =
                    new MultiMatchQueryBuilder(path, vals);
            return new QueryFilterBuilder(mmqb);
          }

          if (bwPie.getTermsField() == null) {
            return new QueryFilterBuilder(
                    new MatchQueryBuilder(path, val).operator(
                            MatchQueryBuilder.Operator.AND));
          }

          /* Build something like this
              "query": {
                "bool": {
                  "must": [
                    {
                      "match": {
                        "loc_all": {
                          "value":...,
                          "operator": "AND"
                        }
                      }
                    }
                  ],
                  "should": [
                    {
                      "term": {
                        "top_users": {
                          "value": "1",
                          "boost": 2
                        }
                      }
                    }
                  ]
                }
              }
           */
          QueryBuilder qb = new MatchQueryBuilder(path, val).operator(
                  MatchQueryBuilder.Operator.AND);
          final BoolQueryBuilder bqb = new BoolQueryBuilder();

          bqb.must(qb);

          qb = new TermQueryBuilder(fullTermPath, val).boost(
                  (float)2.0);

          bqb.should(qb);

          return new QueryFilterBuilder(bqb);
        }

        if (val instanceof Collection) {
          final TermsFilterBuilder tfb =
                  FilterBuilders.termsFilter(path, (Collection) val);
          tfb.execution("or");

          return tfb;
        }

        return new TermFilterBuilder(path, val);

      case timeRange:
        final RangeFilterBuilder rfb = FilterBuilders.rangeFilter(path);

        final TimeRange tr = (TimeRange)val;
        if (tr.getEnd() == null) {
          rfb.gte(tr.getStart().toString());

          return rfb;
        }

        if (tr.getStart() == null) {
          rfb.lt(tr.getEnd().toString());

          return rfb;
        }

        rfb.from(tr.getStart().toString());
        rfb.to(tr.getEnd().toString());
        rfb.includeLower(true);
        rfb.includeUpper(false);

        return rfb;

      case absence:
        return FilterBuilders.missingFilter(path);

      case presence:
        return FilterBuilders.existsFilter(path);

      case prefix:
        return FilterBuilders.prefixFilter(path, (String)val);

      default:
        throw new CalFacadeException(CalFacadeException.filterBadOperator,
                                     opType.toString());
    }
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
        return makeFilter(pf.getPropertyIndexes(), null,
                          pf.getIntKey(), pf.getStrKey(),
                          OperationType.presence,
                          pf.getNot());
      }

      return makeFilter(pf.getPropertyIndexes(), null,
                        pf.getIntKey(), pf.getStrKey(),
                        OperationType.absence,
                        pf.getNot());
    }

    if (pf instanceof TimeRangeFilter) {
      return makeFilter(pf.getPropertyIndexes(),
                        ((TimeRangeFilter)pf).getEntity(),
                        pf.getIntKey(), pf.getStrKey(),
                        OperationType.timeRange,
                        pf.getNot());
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
      final OperationType op;
      if (((ObjectFilter) pf).getPrefixMatch()) {
        op = OperationType.prefix;
      } else {
        op = OperationType.compare;
      }

      return makeFilter(pf.getPropertyIndexes(),
                        getValue((ObjectFilter)pf),
                        pf.getIntKey(), pf.getStrKey(),
                        op,
                        pf.getNot());
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

  private QueryBuilder makeQuery(final FilterBase f) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if (f instanceof AndFilter) {
      final List<QueryBuilder> qbs = makeQueries(f.getChildren(), true);

      if (qbs.size() == 1) {
        return qbs.get(0);
      }

      final BoolQueryBuilder bqb = new BoolQueryBuilder();

      for (final QueryBuilder qb: qbs) {
        if (qb instanceof TermOrTermsQuery) {
          bqb.must(((TermOrTermsQuery)qb).makeQb());
        } else {
          bqb.must(qb);
        }
      }

      return bqb;
    }

    if (f instanceof OrFilter) {
      final List<QueryBuilder> qbs = makeQueries(f.getChildren(), false);

      if (qbs.size() == 1) {
        return qbs.get(0);
      }

      final BoolQueryBuilder bqb = new BoolQueryBuilder().minimumShouldMatch("1");

      for (final QueryBuilder qb: qbs) {
        if (qb instanceof TermOrTermsQuery) {
          bqb.should(((TermOrTermsQuery)qb).makeQb());
        } else {
          bqb.should(qb);
        }
      }

      return bqb;
    }

    if (f instanceof BwHrefFilter) {
      queryLimited = true;

      return QueryBuilders.termQuery(hrefJname,
                                     ((BwHrefFilter)f).getHref());
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    final PropertyFilter pf = (PropertyFilter)f;

    if (pf instanceof EntityTypeFilter) {
      final EntityTypeFilter etf = (EntityTypeFilter)pf;

      return new TermOrTermsQuery("_type",
                                  etf.getEntity(),
                                  pf.getNot());
    }

    if (f instanceof PresenceFilter) {
      final PresenceFilter prf = (PresenceFilter)f;

      if (prf.getTestPresent()) {
        return makeQuery(pf.getPropertyIndex(),
                         makePropertyRef(pf.getPropertyIndexes(), null),
                         null,
                         null, OperationType.presence, 1);
      }

      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       null,
                       null, OperationType.absence, 1);
    }

    if (pf instanceof TimeRangeFilter) {
      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       null,
                       ((TimeRangeFilter)pf).getEntity(),
                       OperationType.timeRange,
                       1);
    }

    if (pf instanceof ObjectFilter) {
      final BwIcalPropertyInfoEntry bwPie = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

      final String fullTermsPath;
      if (bwPie.getTermsField() == null) {
        fullTermsPath = null;
      } else {
        fullTermsPath = makePropertyRef(pf.getPropertyIndexes(),
                                        bwPie.getTermsField());
      }
      return makeQuery(pf.getPropertyIndex(),
                       makePropertyRef(pf.getPropertyIndexes(), null),
                       fullTermsPath,
                       getValue((ObjectFilter)pf),
                       OperationType.compare,
                       1);
    }

    return null;
  }

  private List<QueryBuilder> makeQueries(final List<FilterBase> fs,
                                         final boolean anding) throws CalFacadeException {
    final List<QueryBuilder> qbs = new ArrayList<>();

        /* We'll try to compact the queries - if we have a whole bunch of
          "term" {"category_uid": "abcd"} for example we can turn it into
          "terms" {"category_uid": ["abcd", "pqrs"]}
        */
    TermOrTermsQuery lastQb = null;

    for (final FilterBase f: fs) {
      final QueryBuilder qb = makeQuery(f);

      if (qb == null) {
        continue;
      }

      if (lastQb == null) {
        if (!(qb instanceof TermOrTermsQuery)) {
          qbs.add(qb);
          continue;
        }

        lastQb = (TermOrTermsQuery) qb;
      } else if (!(qb instanceof TermOrTermsQuery)) {
        qbs.add(qb);
      } else {
                /* Can we combine them? */
        final TermOrTermsQuery thisQb = (TermOrTermsQuery)qb;

        if (thisQb.dontMerge ||
                !lastQb.fldName.equals(thisQb.fldName) ||
                (lastQb.not != thisQb.not)) {
          qbs.add(lastQb);
          lastQb = thisQb;
        } else {
          lastQb = lastQb.anding(anding);

          if (thisQb.isTerms) {
            for (final Object o: (Collection)thisQb.value) {
              lastQb.addValue(o);
            }
          } else {
            lastQb.addValue(thisQb.value);
          }
        }
      }
    }

    if (lastQb != null) {
      qbs.add(lastQb);
    }

    return qbs;
  }

  private FilterBuilder doView(final BwViewFilter vf) throws CalFacadeException {
    final FilterBuilder fb = makeFilter(vf.getFilter());

    return fb;
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
