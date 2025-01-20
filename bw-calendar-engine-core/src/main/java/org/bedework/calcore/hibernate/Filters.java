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
package org.bedework.calcore.hibernate;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.common.FiltersCommon;
import org.bedework.calcorei.Calintf;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.AndFilter;
import org.bedework.caldav.util.filter.EntityTimeRangeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.caldav.util.filter.PresenceFilter;
import org.bedework.caldav.util.filter.PropertyFilter;
import org.bedework.caldav.util.filter.TimeRangeFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.filter.BwObjectFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/** Class to carry out some of the manipulations needed for filter support.
 * We attempt to modify the query so that the minimum  number of events are
 * returned. Later on we run the results through the filters to deal with
 * the remaining conditions.
 *
 * <p>We process the query in a number of passes to allow the query sections
 * to be built.
 *
 * <p>Pass 1 is the join pass and assumes we have added the select and from
 * clauses. It gives the method the opportunity to add any join clauses.
 *
 * <p>Pass 2 the where pass, allows us to add additional where clauses.
 * There are two methods associated with this: addWhereSubscriptions
 * and addWhereFilters
 *
 * <p>Pass 3 is parameter replacement and the methods can now replace any
 * parameters.
 *
 * <p>Pass 4 the post execution pass allows the filters to process the
 * result and handle any filters that could not be handled by the query.
 *
 * <p>The kind of query we're looking to build is something like:
 * XXX out of date
 * <br/>
 * select ev from BwEvent<br/>
 * [ join ev.categories as keyw ] --- only for any category matching <br/>
 * where<br/>
 * [ date-ranges-match and ] -- usually have this<br/>
 * ( public-or-creator-test <br/>
 *   [ or  subscriptions ] )<br/>
 * and ( filters )<br/>
 *
 * <p>where the filters and subscriptions are a bunch of parenthesised tests.
 *
 * @author Mike Douglass   douglm rpi.edu
 */
public class Filters extends FiltersCommon {
  private EventQueryBuilder selectClause;

  /** The query we are building
   */
  private EventQueryBuilder whereClause;

  /** The query segment we are building
   */
  private StringBuilder qseg;

  /* True if somewhere in the query we specify a collection or a set of hrefs.
   * Allows us to limit queries to the current user if not otherwise restricted.
   */
  private boolean queryLimited = false;

  private HashMap<String, String> joinDone =
    new HashMap<>();

  /** This provides an index to append to parameter names in the
   * generated query.
   */
  private int qi;

  /** This is set for the second pass when we set the pars
   */
  private HibSession sess;

  private final Calintf intf;

  /** Name of the master event we are testing. e.g. for a recurrence it might be
   * ev.master
   */
  private String masterName;

  /** Name of the entity we test for dates. e.g. for a recurrence instance it will be
   * the instance itself.
   */
  private String dtentName;

  private boolean forOverrides;

  private boolean suppressFilter;

  /**
   * Prefix for filterquery parameters
   */
  private String parPrefix = "fq__";

  /** Constructor
   *
   * @param intf - needed until we change the schema - we need to get the
   *             persistent copy of the category for searches.
   * @param filter
   */
  public Filters(final Calintf intf,
                 final FilterBase filter) {
    super(filter);
    this.intf = intf;
  }

  /** Call for each query
   *
   * @param selectClause
   * @param whereClause
   * @param masterName
   * @param dtentName - entity we test for date terms
   * @param forOverrides
   * @param suppressFilter
   */
  public void init(final EventQueryBuilder selectClause,
                   final EventQueryBuilder whereClause,
                   final String masterName,
                   final String dtentName,
                   final boolean forOverrides,
                   final boolean suppressFilter) {
    this.selectClause = selectClause;
    this.whereClause = whereClause;
    this.masterName = masterName;
    this.dtentName = dtentName;
    this.forOverrides = forOverrides;
    this.suppressFilter = suppressFilter;

    qi = 0;
  }

  /** Pass 1 is the join pass and assumes we have added the select and from
   * clauses. It gives the method the opportunity to add any join clauses.
   *
   * @param retrieveListFields
   */
  public void joinPass(final List<BwIcalPropertyInfoEntry> retrieveListFields) {
    if (getFilter() == null) {
      return;
    }

    joinDone.clear();

    if (!Util.isEmpty(retrieveListFields)) {
      for (BwIcalPropertyInfoEntry ipie: retrieveListFields) {
        if (!ipie.getMultiValued()) {
          // No join needed
          continue;
        }

        String fname = ipie.getDbFieldName();

        if (joinDone.get(fname) != null) {
          // Already done
          continue;
        }

        joinDone.put(fname, fname);

        selectClause.append(" left outer join ");
        selectClause.append(masterName);
        selectClause.append(".");
        selectClause.append(fname);

        selectClause.append(" as joined_");
        selectClause.append(fname);
        selectClause.append(" ");
      }
    }

    addJoins(getFilter());
  }

  /** Pass 2 the where pass, allows us to add additional where clauses.
   * filters are added to the existing statement.
   *
   * Generate a where clause for a query which selects the events for the
   * given filter.
   *
   */
  public void addWhereFilters() {
    if (getFilter() == null) {
      return;
    }

    qseg = new StringBuilder();
    makeWhere(getFilter());

    if (qseg.length() != 0) {
      whereClause.and();
      whereClause.paren(qseg);
    }
  }

  /** Pass 3 is parameter replacement and the methods can now replace any
   * parameters
   *
   * @param sess
   */
  public void parPass(final HibSession sess) {
    if (getFilter() == null) {
      return;
    }

    qi = 0;
    this.sess = sess;

    parReplace(getFilter());
  }

  /**
   * @return true if we had a term like colPath=x
   */
  public boolean getQueryLimited() {
    return queryLimited;
  }

  /**
   * @return true if some filters were supplied
   */
  public boolean getFiltered() {
    return fullFilter != null;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private FilterBase getFilter() {
    if (suppressFilter) {
      return null;
    }

    if (!forOverrides) {
      return fullFilter;
    }

    if (!termsDropped) {
      return fullFilter;
    }

    return overrideFilter;
  }

  /* Generate a where clause for a query which selects the events for the
   * given filter.
   *
   * @param f         Filter element.
   */
  private void makeWhere(final FilterBase f) {
    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      boolean itsAnd = (f instanceof AndFilter);

      qseg.append('(');

      boolean first = true;

      for (FilterBase flt: f.getChildren()) {
        if (!first) {
          if (itsAnd) {
            qseg.append(" and ");
          } else {
            qseg.append(" or ");
          }
        }

        makeWhere(flt);

        first = false;
      }

      qseg.append(")");
    }

    if (f instanceof BwHrefFilter) {
      // Special case this
      qseg.append('(');
      qseg.append(masterName);
      qseg.append(".");
      qseg.append("colPath");
      qseg.append("=:");
      parTerm();
      qseg.append(" and ");
      qseg.append(masterName);
      qseg.append(".");
      qseg.append("name");
      qseg.append("=:");
      parTerm();
      qseg.append(')');

      queryLimited = true;
    } else if (f instanceof PropertyFilter) {
      final PropertyFilter pf = (PropertyFilter)f;

      final BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

      if (pi == null) {
        throw new BedeworkException("org.bedework.filters.unknownproperty",
                                    String.valueOf(pf.getPropertyIndex()));
      }

      String fieldName = pi.getDbFieldName();
      final boolean multi = pi.getMultiValued();
      boolean param = pi.getParam();

      if (param) {
        BwIcalPropertyInfoEntry parentPi =
          BwIcalPropertyInfo.getPinfo(pf.getParentPropertyIndex());

        fieldName = parentPi.getDbFieldName() + "." + fieldName;
      }

      if (multi) {
        if (f instanceof PresenceFilter) {
          PresenceFilter prf = (PresenceFilter)f;
          //qseg.append("(size(");
          qseg.append("((select count(*) from ");
          qseg.append(masterName);
          qseg.append(".");
          qseg.append(fieldName);
          if (pi.getPresenceField() != null) {
            qseg.append(".");
            qseg.append(pi.getPresenceField());
          }
          qseg.append(")");

          if (prf.getTestPresent()) {
            qseg.append(">0)");
          } else {
            qseg.append("=0)");
          }
        } else if (pf instanceof TimeRangeFilter) {
          String fld = "joined_" + pi.getDbFieldName();
          String subfld = "unknown";

          if (pi.getPindex() == PropertyInfoIndex.VALARM) {
            subfld = "triggerTime";
          }

          doTimeRange((TimeRangeFilter)pf, false, fld, subfld);
        } else if (pf instanceof BwCategoryFilter) {
          BwCategory cat = ((BwCategoryFilter)pf).getEntity();
          if (cat.unsaved()) {
            ((BwCategoryFilter)pf).setEntity(intf.getCategory(
                    cat.getUid()));
          }

          qseg.append("(:");
          parTerm();
          if (f.getNot()) {
            qseg.append(" not");
          }
          qseg.append(" in elements(");
          qseg.append(masterName);
          qseg.append(".");
          qseg.append(fieldName);
          qseg.append("))");
        } else if (pf instanceof BwObjectFilter) {
//          String fld = "joined_" + pi.getField();
          String subfld = "value";

//          if (pi.getPindex() == PropertyInfoIndex.CATEGORIES) {
  //          subfld = "word.value";
    //      }

          doObject(((BwObjectFilter)pf).getEntity(),
                   masterName, fieldName, subfld, true);
        } else {
          qseg.append("(:");
          parTerm();
          if (f.getNot()) {
            qseg.append(" not");
          }
          qseg.append(" in elements(");
          qseg.append(masterName);
          qseg.append(".");
          qseg.append(fieldName);
          qseg.append("))");
        }

        // not multi follow
      } else if (f instanceof PresenceFilter) {
        PresenceFilter prf = (PresenceFilter)f;
        qseg.append('(');
        qseg.append(masterName);
        qseg.append(".");
        qseg.append(fieldName);

        if (prf.getTestPresent()) {
          qseg.append(" is not null");
        } else {
          qseg.append(" is null");
        }
        qseg.append(")");
      } else if (pf instanceof EntityTimeRangeFilter) {
        doEntityTimeRange((EntityTimeRangeFilter)pf);
      } else if (pf instanceof TimeRangeFilter) {
        doTimeRange((TimeRangeFilter)pf,
                    (pi.getFieldType().getName().equals(BwDateTime.class.getName())),
                    masterName, fieldName);
      } else if (pf instanceof BwObjectFilter) {
        doObject(((BwObjectFilter)pf).getEntity(),
                 masterName, fieldName, null, false);
      } else {
        /* We assume we can't handle this one as a query.
         */
        throw new BedeworkException("org.bedework.filters.unknownfilter",
                                     String.valueOf(f));
      }
    }
  }

  /* If both dates are null just return. Otherwise return the appropriate
   * terms with the ids: <br/>
   * fromDate    -- first day
   * toDate      -- last day
   *
   * We build two near identical terms.
   *
   *   (floatFlag=true AND <drtest>) OR
   *   (floatFlag is null AND <drtest-utc>)
   *
   * where drtest uses the local from and to times and
   *       drtest-utc uses the utc from and to.
   *
   */
  private void doEntityTimeRange(final EntityTimeRangeFilter trf) {
    TimeRange tr = trf.getEntity();

    if ((tr.getEnd() == null) && (tr.getStart() == null)) {
      return;
    }

    qseg.append("(");
    appendDrtestTerms(true, tr);
    qseg.append(" or ");
    appendDrtestTerms(false, tr);
    qseg.append(")");
  }

  private void appendDrtestTerms(final boolean floatingTest, final TimeRange tr) {
    /* We always test the time in the actual entity, not the related master */
    String startField = dtentName + ".dtstart.date";
    String endField = dtentName + ".dtend.date";

    String startFloatTest;
    String endFloatTest;

    if (floatingTest) {
      startFloatTest = dtentName + ".dtstart.floatFlag=true and ";
      endFloatTest = dtentName + ".dtend.floatFlag=true and ";
    } else {
      startFloatTest = dtentName + ".dtstart.floatFlag is null and ";
      endFloatTest = dtentName + ".dtend.floatFlag is null and ";
    }

    /* Note that the comparisons below are required to ensure that the
     *  start date is inclusive and the end date is exclusive.
     * From CALDAV:
     * A VEVENT component overlaps a given time-range if:
     *
     * (DTSTART <= start AND DTEND > start) OR
     * (DTSTART <= start AND DTSTART+DURATION > start) OR
     * (DTSTART >= start AND DTSTART < end) OR
     * (DTEND   > start AND DTEND < end)
     *
     *  case 1 has the event starting between the dates.
     *  case 2 has the event ending between the dates.
     *  case 3 has the event starting before and ending after the dates.
     */

    if (tr.getStart() == null) {
      qseg.append("(");
      qseg.append(startFloatTest);

      qseg.append(startField);
      qseg.append(" < :");
      parTerm();
      qseg.append(")");
      return;
    }

    if (tr.getEnd() == null) {
      qseg.append("(");
      qseg.append(endFloatTest);

      qseg.append(endField);
      qseg.append(" > :");
      parTerm();
      qseg.append(")");
      return;
    }

    qseg.append("(");
    qseg.append(startFloatTest); // XXX Inadequate?? - should check each field separately?
    qseg.append("(");

    qseg.append(startField);
    qseg.append(" < :");
    parTerm();
    qseg.append(") and ((");

    qseg.append(endField);
    qseg.append(" > :");
    parTerm();
    qseg.append(") or ((");

    qseg.append(startField);
    qseg.append("=");
    qseg.append(endField);
    qseg.append(") and (");
    qseg.append(endField);
    qseg.append(" >= :");
    parTerm();
    qseg.append("))))");

    /*
    ((start < to) and ((end > from) or
      ((start = end) and (end >= from))))
     */
  }

  private void doTimeRange(final TimeRangeFilter trf, final boolean dateTimeField,
                           final String fld, final String subfld) {
    TimeRange tr = trf.getEntity();

    qseg.append('(');

    if (tr.getStart() != null) {
      qseg.append('(');
      qseg.append(fld);
      qseg.append(".");
      qseg.append(subfld);

      if (dateTimeField) {
        qseg.append(".date");
      }

      qseg.append(">=:");
      parTerm();
      qseg.append(')');
    }

    if (tr.getEnd() != null) {
      if (tr.getStart() != null) {
        qseg.append(" and ");
      }

      qseg.append('(');
      qseg.append(fld);
      qseg.append(".");
      qseg.append(subfld);

      if (dateTimeField) {
        qseg.append(".date");
      }

      qseg.append("<:");
      parTerm();
      qseg.append(')');
    }

    qseg.append(')');
  }

  private void doObject(final ObjectFilter of,
                        final String master,
                        final String fld, final String subfld,
                        final boolean multi) {
    String dbfld;

    qseg.append('(');

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

    if (multi) {
      qseg.append("(select count(*) from ");

      qseg.append(master);
      qseg.append(".");
      qseg.append(fld);

      dbfld = fld + "_x";

      qseg.append(" ");
      qseg.append(dbfld);
      qseg.append(" where ");
    } else {
      dbfld = master + "." + fld;
    }

    if (doCaseless) {
      qseg.append("lower(");
    }

    qseg.append(dbfld);

    if (subfld != null) {
      qseg.append(".");
      qseg.append(subfld);
    }

    if (doCaseless) {
      qseg.append(")");
    }

    if (!of.getExact()) {
      if (of.getNot()) {
        qseg.append(" not");
      }
      qseg.append(" like :");
      parTerm();
    } else if (c != null) {
      if (of.getNot()) {
        qseg.append(" not in (");
      } else {
        qseg.append(" in (");
      }

      for (int i = 0; i < c.size(); i++) {
        if (i > 0) {
          qseg.append(", ");
        }
        qseg.append(":");
        parTerm();
      }
      qseg.append(")");
    } else if (of.getNot()) {
      qseg.append("<>:");
      parTerm();
    } else {
      qseg.append("=:");
      parTerm();
    }

    if (multi) {
      qseg.append(") > 0");
    }

    qseg.append(")");
  }

  /* Fill in the parameters after we generated the query.
   */
  private void parReplace(final FilterBase f) {
    if (f instanceof AndFilter) {
      AndFilter fb = (AndFilter)f;

      for (FilterBase flt: fb.getChildren()) {
        parReplace(flt);
      }

      return;
    }

    if (f instanceof BwHrefFilter) {
      BwHrefFilter hf = (BwHrefFilter)f;

      // Special case this
      sess.setString(parPrefix + qi, hf.getPathPart());
      qi++;
      sess.setString(parPrefix + qi, hf.getNamePart());
      qi++;
    }

    if (f instanceof BwCategoryFilter) {
      BwCategoryFilter cf = (BwCategoryFilter)f;

      BwCategory cat = cf.getEntity();
      /* XXX - this is what we want to be able to do
      sess.setString(parPrefix + qi, cat.getUid());
      */
      sess.setEntity(parPrefix + qi, cat);

      qi++;

      return;
    }

    if (f instanceof EntityTimeRangeFilter) {
      doEntityTimeRangeReplace((EntityTimeRangeFilter)f);

      return;
    }

    if (f instanceof TimeRangeFilter) {
      TimeRangeFilter trf = (TimeRangeFilter)f;
      TimeRange tr = trf.getEntity();

      if (tr.getStart() != null) {
        sess.setString(parPrefix + qi, tr.getStart().toString());
        qi++;
      }

      if (tr.getEnd() != null) {
        sess.setString(parPrefix + qi, tr.getEnd().toString());
        qi++;
      }

      return;
    }

    if (f instanceof BwObjectFilter) {
      ObjectFilter of = ((BwObjectFilter)f).getEntity();

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
        for (final Object co: c) {
          String s = (String)co;
          if (doCaseless) {
            s = s.toLowerCase();
          }

          sess.setString(parPrefix + qi, s);
          qi++;
        }

        return;
      }

      if (o instanceof BwCalendar) {
        final BwCalendar cal = unwrap((BwCalendar)o);
        sess.setString(parPrefix + qi, cal.getPath());
      } else if (o instanceof BwPrincipal) {
        sess.setString(parPrefix + qi, ((BwPrincipal)o).getPrincipalRef());
      } else if (o instanceof BwDbentity) {
        sess.setEntity(parPrefix + qi, o);
      } else if (of.getExact()) {
        String s = (String)o;
        if (doCaseless) {
          s = s.toLowerCase();
        }

        sess.setString(parPrefix + qi, s);
      } else if (of.getEntity() instanceof String) {
        String s = o.toString();

        if (doCaseless) {
          s = s.toLowerCase();
        }

        sess.setString(parPrefix + qi, "%" + s + "%");
      } else {
        sess.setString(parPrefix + qi, "%" + o + "%");
      }

      qi++;

      return;
    }

    if (f instanceof OrFilter) {
      OrFilter fb = (OrFilter)f;

      for (FilterBase flt: fb.getChildren()) {
        parReplace(flt);
      }

      return;
    }
  }

  private void doEntityTimeRangeReplace(final EntityTimeRangeFilter trf) {
    TimeRange tr = trf.getEntity();

    if ((tr.getEnd() == null) && (tr.getStart() == null)) {
      return;
    }

    qseg.append("(");
    drReplace(true, tr);
    qseg.append(" or ");
    drReplace(false, tr);
    qseg.append(")");
  }

  private void drReplace(final boolean floatingTest,
                         final TimeRange tr) {
    String startVal = null;
    String endVal = null;
    DateTime start = tr.getStart();
    DateTime end = tr.getEnd();

    if (floatingTest) {
      if (start != null) {
        startVal = tr.getStartExpanded().toString();
      }

      if (end != null) {
        endVal = tr.getEndExpanded().toString();
      }
    } else {
      if (start != null) {
        startVal = start.toString();
      }

      if (end != null) {
        endVal = end.toString();
      }
    }

    if (start == null) {
      sess.setString(parPrefix + qi, endVal);
      qi++;
      return;
    }

    if (end == null) {
      sess.setString(parPrefix + qi, startVal);
      qi++;
      return;
    }

    sess.setString(parPrefix + qi, endVal);
    qi++;
    sess.setString(parPrefix + qi, startVal);
    qi++;
    sess.setString(parPrefix + qi, startVal);
    qi++;
  }

  /*
  private String makeUtcformat(String dt) {
    int len = dt.length();

    if (len == 16) {
      return dt;
    }

    if (len == 15) {
      return dt + "Z";
    }

    if (len == 8) {
      return dt + "T000000Z";
    }

    throw new RuntimeException("Bad date " + dt);
  }*/

  private void addJoins(final FilterBase f) {
    if ((f instanceof AndFilter) ||
        (f instanceof OrFilter)) {
      for (FilterBase flt: f.getChildren()) {
        addJoins(flt);
      }

      return;
    }

    if (!(f instanceof PropertyFilter)) {
      return;
    }

    if (f instanceof PresenceFilter) {
      return;
    }

    if (f instanceof BwHrefFilter) {
      return;
    }

    PropertyFilter pf = (PropertyFilter)f;
    BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new BedeworkException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    if (!pi.getMultiValued()) {
      return;
    }
/*
    if (f instanceof BwCategoryFilter) {
      addThisJoin(pi);
      return;
    }*/

    if (f instanceof TimeRangeFilter) {
      addThisJoin(pi);
      return;
    }

    if (f instanceof BwObjectFilter) {
      addThisJoin(pi);
      return;
    }
  }

  private void addThisJoin(final BwIcalPropertyInfoEntry pi) {
    if (joinDone.get(pi.getDbFieldName()) != null) {
      // Already done
      return;
    }

    joinDone.put(pi.getDbFieldName(), pi.getDbFieldName());

    selectClause.append(" left outer join ");
    selectClause.append(masterName);
    selectClause.append(".");
    selectClause.append(pi.getDbFieldName());

    selectClause.append(" as joined_");
    selectClause.append(pi.getDbFieldName());
    selectClause.append(" ");

//    if (pi.getPindex().equals(PropertyInfoIndex.CATEGORIES)) {
  //    selectClause.append("\n  left join joined_categories.word as joined_catwords");
    //}
  }

  private void parTerm() {
    qseg.append(parPrefix);
    qseg.append(qi);
    qi++;
  }

  protected BwCalendar unwrap(final BwCalendar val) {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      // We get these at the moment - getEvents at svci level
      return val;
      // CALWRAPPER throw new BedeworkException("org.bedework.not.wrapped");
    }

    return ((CalendarWrapper)val).fetchEntity();
  }
}

