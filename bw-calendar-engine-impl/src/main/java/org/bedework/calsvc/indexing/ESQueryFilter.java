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
package org.bedework.calsvc.indexing;

import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.BwCreatorFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;

import edu.rpi.cct.misc.indexing.SearchLimits;

import org.apache.log4j.Logger;
import org.elasticsearch.index.query.AndFilterBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.OrFilterBuilder;
import org.elasticsearch.index.query.RangeFilterBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Build filters for ES searching
 *
 * @author Mike Douglass douglm @ rpi.edu
 *
 */
public class ESQueryFilter {
  private transient Logger log;

  private boolean debug;

  private final boolean publick;
  private final String principal;
  private boolean queryLimited;

  public ESQueryFilter(final boolean publick,
                       final String principal) {
    debug = getLog().isDebugEnabled();

    this.publick = publick;
    this.principal = principal;
  }

  public FilterBuilder buildFilter(final String field,
                                   final String val) throws CalFacadeException {
    FilterBuilder fb = addPrincipal(addTerm(null, field, val));

    if (debug) {
      if (fb == null) {
        debug("Null FilterBuilder");
      } else {
        debug(fb.toString());
      }
    }

    return fb;
  }

  public FilterBuilder buildFilter(final FilterBase f) throws CalFacadeException {
    FilterBuilder fb = makeFilter(f);

    if (!queryLimited) {
      fb = addPrincipal(fb);
    }

    if (debug) {
      if (fb == null) {
        debug("Null FilterBuilder");
      } else {
        debug(fb.toString());
      }
    }

    return fb;
  }

  /** If the filters don't limit the query to at least one collection
   * we should add a filter term limiting the query to the current users
   * entities.
   *
   * @return true if we had a term like colPath=x
   */
  public boolean getQueryLimited() {
    return queryLimited;
  }

  public FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                          final SearchLimits limits) throws CalFacadeException {
    if (limits == null) {
      return filter;
    }

    FilterBuilder fb = addDateRangeFilter(filter,
                                          limits.fromDate,
                                          limits.toDate);

    if (debug) {
      if (fb == null) {
        debug("Null FilterBuilder");
      } else {
        debug(fb.toString());
      }
    }

    return fb;
  }

  private FilterBuilder addDateRangeFilter(final FilterBuilder filter,
                                           final String start,
                                           final String end) throws CalFacadeException {
    if ((start == null) && (end == null)) {
      return filter;
    }

    FilterBuilder fb = filter;

    if (start != null) {
      // End of events must be on or after the start of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder("end_utc");

      rfb.gte(dateTimeUTC(start));

      if (fb == null) {
        fb = rfb;
      } else {
        AndFilterBuilder afb = new AndFilterBuilder();
        afb.add(fb);
        afb.add(rfb);

        fb = afb;
      }
    }

    if (end != null) {
      // Start of events must be before the end of the range
      RangeFilterBuilder rfb = new RangeFilterBuilder("start_utc");

      rfb.lt(dateTimeUTC(end));

      if (fb == null) {
        fb = rfb;
      } else if (fb instanceof AndFilterBuilder) {
        ((AndFilterBuilder)fb).add(rfb);
      } else {
        AndFilterBuilder afb = new AndFilterBuilder();
        afb.add(fb);
        afb.add(rfb);

        fb = afb;
      }
    }

    return fb;
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

  private FilterBuilder addTerm(final FilterBuilder filter,
                                final String name,
                                final String val) throws CalFacadeException {
    FilterBuilder fb = FilterBuilders.termFilter(name, val);

    if (filter == null) {
      return fb;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);

    afb.add(fb);

    return afb;
  }

  private FilterBuilder makeFilter(final FilterBase f) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if (f instanceof AndFilter) {
      AndFilterBuilder fb = new AndFilterBuilder();

      for (FilterBase flt: f.getChildren()) {
        fb.add(makeFilter(flt));
      }

      return fb;
    }

    if (f instanceof OrFilter) {
      OrFilterBuilder fb = new OrFilterBuilder();

      for (FilterBase flt: f.getChildren()) {
        fb.add(makeFilter(flt));
      }

      return fb;
    }

    if (f instanceof BwHrefFilter) {
      AndFilterBuilder fb = new AndFilterBuilder();

      fb.add(FilterBuilders.termFilter("colPath",
                                       ((BwHrefFilter)f)
                                               .getPathPart()));
      fb.add(FilterBuilders.termFilter("name",
                                       ((BwHrefFilter)f)
                                               .getNamePart()));

      queryLimited = true;

      return fb;
    }

    if (!(f instanceof PropertyFilter)) {
      return null;
    }

    PropertyFilter pf = (PropertyFilter)f;

    BwIcalPropertyInfo.BwIcalPropertyInfoEntry pi =
            BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    String fieldName = pi.getDbFieldName();
    boolean multi = pi.getMultiValued();
    boolean param = pi.getParam();

    if (param) {
      BwIcalPropertyInfo.BwIcalPropertyInfoEntry parentPi =
              BwIcalPropertyInfo.getPinfo(pf.getParentPropertyIndex());

      fieldName = parentPi.getDbFieldName() + "." + fieldName;
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

    if (pf instanceof BwCreatorFilter) {
      return FilterBuilders.termFilter("creator",
                                       ((BwCreatorFilter)pf)
                                               .getEntity());
    }

    if (pf instanceof BwCategoryFilter) {
      BwCategory cat = ((BwCategoryFilter)pf).getEntity();
      return FilterBuilders.termFilter("category_uid",
                                       cat.getUid());
    }

    if (pf instanceof BwCollectionFilter) {
      BwCalendar col = ((BwCollectionFilter)pf).getEntity();

      return FilterBuilders.termFilter("path",
                                       col.getPath());
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

  private FilterBuilder doObject(final ObjectFilter of,
                                 final String fld,
                                 final String subfld) throws CalFacadeException {
    String dbfld = fld;

    if (subfld != null) {
      dbfld += "." + subfld;
    }

    Object o = of.getEntity();
    Collection c = null;

    boolean doCaseless = false;

    if (o instanceof BwCalendar) {
      if (!of.getNot()) {
        queryLimited = true;
      }
    } else {
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

      doCaseless = isString && of.getCaseless();
    }

    return FilterBuilders.termFilter(dbfld, getValue(of));
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

  private FilterBuilder addPrincipal(final FilterBuilder filter) throws CalFacadeException {
    if (publick) {
      return filter;
    }

    FilterBuilder fb = FilterBuilders.termFilter("owner", principal);

    if (filter == null) {
      return fb;
    }

    AndFilterBuilder afb = new AndFilterBuilder(filter);
    afb.add(fb);

    return afb;
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
