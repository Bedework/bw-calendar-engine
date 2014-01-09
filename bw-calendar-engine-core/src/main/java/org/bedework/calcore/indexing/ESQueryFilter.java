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

import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calcorei.CalintfDefs;
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

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter implements CalintfDefs {
  private transient Logger log;

  private boolean debug;

  private final int currentMode;
  private final BwPrincipal principal;
  private final boolean superUser;

  private boolean queryLimited;

  private static String colpathJname = getJname(PropertyInfoIndex.COLLECTION);
  private static String dtendJname = getJname(PropertyInfoIndex.DTEND);
  private static String dtstartJname = getJname(PropertyInfoIndex.DTSTART);

  /** */
  public static String hrefJname = getJname(PropertyInfoIndex.HREF);
  private static String ownerJname = getJname(PropertyInfoIndex.OWNER);
  private static String publicJname = getJname(PropertyInfoIndex.PUBLIC);

  private static String indexEndJname = getJname(PropertyInfoIndex.INDEX_END);
  private static String indexStartJname = getJname(PropertyInfoIndex.INDEX_START);

  private static String dtEndUTCRef = makePropertyRef(PropertyInfoIndex.DTEND,
                                                          PropertyInfoIndex.UTC);
  private static String dtStartUTCRef = makePropertyRef(
          PropertyInfoIndex.DTSTART,
          PropertyInfoIndex.UTC);

  private static String indexEndUTCRef = makePropertyRef(PropertyInfoIndex.INDEX_END,
                                                      PropertyInfoIndex.UTC);
  private static String indexStartUTCRef = makePropertyRef(
          PropertyInfoIndex.INDEX_START,
          PropertyInfoIndex.UTC);

  /**
   *
   * @param currentMode - guest, user,publicAdmin
   * @param principal - only used to add a filter for non-public
   * @param superUser - true if the principal is a superuser.
   */
  public ESQueryFilter(final int currentMode,
                       final BwPrincipal principal,
                       final boolean superUser) {
    debug = getLog().isDebugEnabled();

    this.currentMode = currentMode;
    this.principal = principal;
    this.superUser = superUser;
  }

  /** Build a filter for a single entity identified by the property
   * reference and value. The search willbe limited by the current
   * mode.
   *
   * @param val
   * @param index
   * @return
   * @throws CalFacadeException
   */
  public FilterBuilder singleEntityFilter(final String val,
                                          final PropertyInfoIndex... index) throws CalFacadeException {
    FilterBuilder fb = principalFilter(addTerm(null,
                                               makePropertyRef(index),
                                               val));

    return fb;
  }

  public FilterBuilder buildFilter(final FilterBase f) throws CalFacadeException {
    FilterBuilder fb = makeFilter(f);

    if (fb instanceof TermOrTerms) {
      fb = ((TermOrTerms)fb).makeFb();
    }

    if (!queryLimited) {
      fb = principalFilter(fb);
    }

    return fb;
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
   * @param recurRetrieval
   * @return
   * @throws CalFacadeException
   */
  public FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                          final String start,
                                          final String end,
                                          final RecurringRetrievalMode recurRetrieval) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return filter;
    }

    String startRef;
    String endRef;

    if (recurRetrieval.mode == Rmode.expanded) {
      startRef = dtStartUTCRef;
      endRef = dtEndUTCRef;
    } else {
      startRef = indexStartUTCRef;
      endRef = indexEndUTCRef;
    }

    FilterBuilder fb = filter;

    if (end != null) {
      // End of events must be on or after the start of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder(startRef);

      rfb.lt(dateTimeUTC(end));

      fb = and(fb, rfb);
    }

    if (start != null) {
      // Start of events must be before the end of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder(endRef);

      rfb.gte(dateTimeUTC(start));

      fb = and(fb, rfb);
    }

    return fb;
  }

  /** Add a filter for the given href.
   *
   * @param filter - or null
   * @param href
   * @return a filter
   * @throws CalFacadeException
   */
  public FilterBuilder hrefFilter(final FilterBuilder filter,
                                  final String href) throws CalFacadeException {
    return and(filter,
               FilterBuilders.termFilter(hrefJname,href));
  }

  /** Add filter for the current principal - or public - to limit search
   * to entities owned by the current principal.
   *
   * @param filter - or null
   * @return a filter
   * @throws CalFacadeException
   */
  public FilterBuilder principalFilter(final FilterBuilder filter) throws CalFacadeException {
    boolean publicEvents = (currentMode == guestMode) ||
            (currentMode == publicAdminMode);

    //boolean all = (currentMode == guestMode) || ignoreCreator;
    boolean all = publicEvents || superUser;

    FilterBuilder fb = and(filter,
                           FilterBuilders.termFilter(
                                   publicJname,
                                   String.valueOf(publicEvents)));

    if (all) {
      return fb;
    }

    return and(fb, FilterBuilders.termFilter(ownerJname,
                                             principal.getPrincipalRef()));
  }

  /**
   *
   * @param filter - null or filter to AND with new term
   * @param pi
   * @param val
   * @return TermFilterBuilder or AndFilterBuilder
   * @throws CalFacadeException
   */
  public FilterBuilder addTerm(final FilterBuilder filter,
                               final PropertyInfoIndex pi,
                               final String val) throws CalFacadeException {
    return addTerm(filter, getJname(pi), val);
  }

  /**
   *
   * @param filter - null or filter to AND with new term
   * @param name
   * @param val
   * @return TermFilterBuilder or AndFilterBuilder
   * @throws CalFacadeException
   */
  public FilterBuilder addTerm(final FilterBuilder filter,
                               final String name,
                               final String val) throws CalFacadeException {
    return and(filter, FilterBuilders.termFilter(name, val));
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

  private FilterBuilder and(final FilterBuilder filter,
                            final FilterBuilder newFilter) {
    if (filter == null) {
      return newFilter;
    }

    if (filter instanceof AndFilterBuilder) {
      ((AndFilterBuilder)filter).add(newFilter);

      return filter;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);

    afb.add(newFilter);

    return afb;
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

  private FilterBuilder doTimeRange(final TimeRangeFilter trf,
                                    final boolean dateTimeField,
                                    final String fld,
                                    final String subfld) {
    TimeRange tr = trf.getEntity();

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
      if (!isTerms) {
        fb = FilterBuilders.termFilter(fldName, value);
      } else {
        fb = FilterBuilders.termsFilter(fldName,
                                        (Iterable <?>)value).execution(exec);
      }

      if (!not) {
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
      return doTimeRange((TimeRangeFilter)pf, false, fieldName, null);
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

      BwCalendar col = ((BwCollectionFilter)pf).getEntity();

      return new TermOrTerms(colpathJname,
                             col.getPath(),
                             pf.getNot());
    }

    if (pf instanceof EntityTypeFilter) {
      EntityTypeFilter etf = (EntityTypeFilter)pf;

      return new TermOrTerms("_type",
                             IcalDefs.entityTypeNames[etf.getEntity()],
                             pf.getNot());
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

      warn("Fuzzy search for " + of);
      return "";
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
