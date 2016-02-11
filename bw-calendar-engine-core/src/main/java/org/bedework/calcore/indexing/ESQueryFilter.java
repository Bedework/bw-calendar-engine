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
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.BaseFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.NotFilterBuilder;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;
import org.elasticsearch.index.query.TermsFilterBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter implements CalintfDefs {
  private transient Logger log;

  private boolean debug;

  private boolean publick;
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

  private static final String colpathJname = getJname(PropertyInfoIndex.COLLECTION);
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
    debug = getLog().isDebugEnabled();

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
               addTerm(makePropertyRef(index), val));
  }

  public FilterBuilder buildFilter(final FilterBase f) throws CalFacadeException {
    FilterBuilder fb = makeFilter(f);

    if (fb instanceof TermOrTerms) {
      fb = ((TermOrTerms)fb).makeFb();
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

      return and(fb, limit);
    }

    if (rmode.mode == Rmode.overrides) {
      final FilterBuilder limit =
              or(addTerm(PropertyInfoIndex.MASTER, "true"),
                 addTerm(PropertyInfoIndex.OVERRIDE, "true"));

      return and(fb, limit);
    }

    return fb;
  }

  public FilterBuilder addLimits(final FilterBuilder f,
                                 final FilterBase defaultFilterContext) throws CalFacadeException {
    FilterBuilder fb = f;

    if (!queryLimited) {
      if (defaultFilterContext != null) {
        fb = and(fb, buildFilter(defaultFilterContext));
      }

      if (!queryLimited) {
        fb = principalFilter(fb);
      }
    }

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
                     addTerm(PropertyInfoIndex.RECURRING, "false")));

      return and(fb, limit);
    }

    if (recurRetrieval.mode == Rmode.entityOnly) {
      FilterBuilder limit = not(addTerm("_type",
                                        BwIndexer.docTypeEvent));

      limit = or(limit,
                 addTerm(PropertyInfoIndex.MASTER, "true"));

      return and(fb, limit);
    }

    /* if the query is not filtered we can limit to the master and
       overrides only
     */

    if (queryFiltered) {
      return fb;
    }

    FilterBuilder limit = not(addTerm("_type",
                                      BwIndexer.docTypeEvent));

    limit = or(limit, addTerm(PropertyInfoIndex.MASTER, "true"));

    limit = or(limit, addTerm(PropertyInfoIndex.OVERRIDE, "true"));

    queryFiltered = false; // Reset it.

    return and(fb, limit);
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
   * @param start
   * @param end
   * @return
   * @throws CalFacadeException
   */
  public FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                          final String start,
                                          final String end) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return filter;
    }

    queryFiltered = true;

    final String startRef;
    final String endRef;

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

      fb = and(fb, rfb);
    }

    if (start != null) {
      // End of events must be before the end of the range
      final RangeFilterBuilder rfb = new RangeFilterBuilder(endRef);

      rfb.gte(dateTimeUTC(start));
      rfb.includeLower(false);

      fb = and(fb, rfb);
    }

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
               FilterBuilders.termFilter(hrefJname, href));
  }

  /** Add filter for the current principal - or public - to limit search
   * to entities owned by the current principal.
   *
   * @param filter - or null
   * @return a filter
   * @throws CalFacadeException
   */
  public FilterBuilder principalFilter(final FilterBuilder filter) throws CalFacadeException {
    boolean publicEvents = publick ||
            (currentMode == guestMode) ||
            (currentMode == publicAdminMode);

    //boolean all = (currentMode == guestMode) || ignoreCreator;
    boolean all = publicEvents || superUser;

    /* While we have public/user indexes we don't need to filter on
       the public flag

    FilterBuilder fb = and(filter,
                           FilterBuilders.termFilter(
                                   publicJname,
                                   String.valueOf(publicEvents)));

    if (all) {
      return fb;
    }

    return and(fb, FilterBuilders.termFilter(ownerJname,
                                             principal.getPrincipalRef()));
                                             */
    if (all) {
      return filter;
    }

    return and(filter, FilterBuilders.termFilter(ownerJname,
                                                 principal.getPrincipalRef()));
  }

  /**
   *
   * @param pi
   * @param val
   * @return TermFilterBuilder or AndFilterBuilder
   * @throws CalFacadeException
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
                            final FilterBuilder newFilter) {
    if (filter == null) {
      return newFilter;
    }

    if (filter instanceof AndFilterBuilder) {
      ((AndFilterBuilder)filter).add(newFilter);

      return filter;
    }

    final AndFilterBuilder afb = new AndFilterBuilder(filter);

    afb.add(newFilter);

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

  private static String getJname(PropertyInfoIndex pi) {
    BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  private String dateTimeUTC(final String dt) {
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
    RangeFilterBuilder rfb = FilterBuilders.rangeFilter(fld);

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
  }

  private class TermOrTerms extends BaseFilterBuilder {
    String fldName;
    Object value;
    private String exec;
    boolean isTerms;
    boolean not;

    /* If true we don't merge this term with other terms. This might
     * help with the expansion of views which we would expect to turn
     * up frequently.
     */
    boolean dontMerge;

    TermOrTerms(final String fldName,
                final Object value,
                final boolean not) {
      this.fldName = fldName;
      this.value = value;
      this.not = not;
    }

    TermOrTerms anding(final boolean anding) {
      if (anding) {
        exec = "and";
      } else {
        exec = "or";
      }

      return this;
    }

    FilterBuilder makeFb() {
      FilterBuilder fb;
      boolean hrefOrPath = fldName.equals(hrefJname) ||
              fldName.equals(colpathJname);

      if (!isTerms) {
        fb = FilterBuilders.termFilter(fldName, value);
      } else {
        fb = FilterBuilders.termsFilter(fldName,
                                        (Iterable <?>)value).execution(exec);
      }

      queryFiltered |= !hrefOrPath;

      if (!not) {
        queryLimited |= hrefOrPath;
        return fb;
      }

      return new NotFilterBuilder(fb);
    }

    void addValue(final Object val) {
      if (value == null) {
        value = val;
      } else if (value instanceof Collection) {
        ((Collection)value).add(val);
      } else {
        List vals = new ArrayList();

        vals.add(value);
        vals.add(val);

        value = vals;
        isTerms = true;
      }
    }

    @Override
    public XContentBuilder toXContent(final XContentBuilder builder,
                                      final Params params)
            throws IOException {
      return null;
    }

    @Override
    protected void doXContent(final XContentBuilder builder,
                              final Params params)
            throws IOException {
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

  private FilterBuilder makeFilter(final FilterBase f) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if (f instanceof AndFilter) {
      List<FilterBuilder> fbs = makeFilters(f.getChildren(), true);

      if (fbs.size() == 1) {
        return fbs.get(0);
      }

      AndFilterBuilder afb = new AndFilterBuilder();

      for (FilterBuilder fb: fbs) {
        if (fb instanceof TermOrTerms) {
          afb.add(((TermOrTerms)fb).makeFb());
        } else {
          afb.add(fb);
        }
      }

      return afb;
    }

    if (f instanceof OrFilter) {
      List<FilterBuilder> fbs = makeFilters(f.getChildren(), false);

      if (fbs.size() == 1) {
        return fbs.get(0);
      }

      OrFilterBuilder ofb = new OrFilterBuilder();

      for (FilterBuilder fb: fbs) {
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

      return FilterBuilders.termFilter(hrefJname,
                                       ((BwHrefFilter)f).getHref());
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    PropertyFilter pf = (PropertyFilter)f;
/*
    if (pf.getPropertyIndex() == PropertyInfoIndex.CATEGORY_PATH) {
      // Special case this one.
      return new TermOrTerms(PropertyInfoIndex.CATEGORY_PATH.getJname(),
                             ((ObjectFilter)pf).getEntity());
    }*/

    BwIcalPropertyInfo.BwIcalPropertyInfoEntry pi =
            BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    String fieldName;

    PropertyInfoIndex pii = pf.getPropertyIndex();

    if (pf.getParentPropertyIndex() != null) {
      fieldName = makePropertyRef(pf.getParentPropertyIndex(), pii);
    } else {
      fieldName = getJname(pii);
    }

    if (f instanceof PresenceFilter) {
      PresenceFilter prf = (PresenceFilter)f;

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
                             pf.getNot());
    }

    if (pf instanceof EntityTypeFilter) {
      final EntityTypeFilter etf = (EntityTypeFilter)pf;

      return new TermOrTerms("_type",
                             IcalDefs.entityTypeNames[etf.getEntity()],
                             pf.getNot());
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

      return addDateRangeFilter(null, start, end);
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
    FilterBuilder fb = makeFilter(vf.getFilter());

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

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debug(final String msg) {
    getLog().debug(msg);
  }

  protected void warn(final String msg) {
    getLog().warn(msg);
  }
}
