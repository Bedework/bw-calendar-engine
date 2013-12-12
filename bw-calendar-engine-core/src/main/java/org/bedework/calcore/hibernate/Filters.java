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

import org.bedework.calcorei.HibSession;
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
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCategoryFilter;
import org.bedework.calfacade.filter.BwHrefFilter;
import org.bedework.calfacade.filter.BwObjectFilter;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import net.fortuna.ical4j.model.DateTime;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
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
public class Filters implements Serializable {
  private transient Logger log;

  private boolean debug;

  /* The complete filter with some filter elements replaced by bedework specific
   * classes
   */
  private FilterBase fullFilter;

  /* A copy of the filter modified for hsql generation for overrides (and annotations
   * These can only include date and collection path terms.
   *
   * If null the unmodified filter is fine and no post-processing is needed
   */
  private FilterBase overrideFilter;

  /* Set true during reconstruction of the filter for overrides if any terms
   * are dropped
   */
  private boolean termsDropped;

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
    new HashMap<String, String>();

  /** This provides an index to append to parameter names in the
   * generated query.
   */
  private int qi;

  /** This is set for the second pass when we set the pars
   */
  private HibSession sess;

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
   * @param filter
   * @throws CalFacadeException
   */
  public Filters(final FilterBase filter) throws CalFacadeException {
    /* Reconstruct the filter and create a date only filter for overrides. */

    fullFilter = reconstruct(filter, false);
    overrideFilter = reconstruct(filter, true);

    debug = getLogger().isDebugEnabled();
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
   * @throws CalFacadeException
   */
  public void joinPass(final List<BwIcalPropertyInfoEntry> retrieveListFields)
          throws CalFacadeException {
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
   * @throws CalFacadeException
   */
  public void addWhereFilters() throws CalFacadeException {
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
   * @throws CalFacadeException
   */
  public void parPass(final HibSession sess) throws CalFacadeException {
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

  /** This should only be called for override/annotation processing
   *
   * @param ev
   * @param userHref - current user for whom we are filtering
   * @return true for a match
   * @throws CalFacadeException
   */
  public boolean postFilter(final BwEvent ev,
                            final String userHref) throws CalFacadeException {
    return match(fullFilter, ev, userHref);
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

  /* Return null if there are no terms to test for - otherwise return the
   * modified filter.
   *
   * Mostly we replace ObjectFilter with its wrapped form.
   */
  private FilterBase reconstruct(final FilterBase f,
                             final boolean forOverrides) throws CalFacadeException {
    if (f == null) {
      return null;
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      boolean itsAnd = (f instanceof AndFilter);

      List<FilterBase> fs = new ArrayList<FilterBase>();

      for (FilterBase flt: f.getChildren()) {
        FilterBase chf = reconstruct(flt, forOverrides);

        if (chf != null) {
          fs.add(chf);
        }
      }

      if (fs.size() == 0) {
        return null;
      }

      if (fs.size() == 1) {
        return fs.get(0);
      }

      FilterBase res;

      if (itsAnd) {
        res = new AndFilter();
      } else {
        res = new OrFilter();
      }

      for (FilterBase flt: fs) {
        res.addChild(flt);
      }

      return res;
    }

    /* Not AND/OR */

    if (!(f instanceof PropertyFilter)) {
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }

    PropertyFilter pf = (PropertyFilter)f;
    if (pf.getPropertyIndex() == PropertyInfoIndex.HREF) {
      // Special case this
      return f;
    }

    if (BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex()) == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    if (f instanceof PresenceFilter) {
      if (!forOverrides) {
        return f;
      }

      if ((pf.getPropertyIndex() == PropertyInfoIndex.COLLECTION) ||
          (pf.getPropertyIndex() == PropertyInfoIndex.DTEND) ||
          (pf.getPropertyIndex() == PropertyInfoIndex.DTSTART) ||
          (pf.getPropertyIndex() == PropertyInfoIndex.DURATION)) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof EntityTimeRangeFilter) {
      return f;
    } else if (pf instanceof TimeRangeFilter) {
      if (!forOverrides) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof BwCategoryFilter) {
      if (!forOverrides) {
        return f;
      }

      termsDropped = true;
      return null;
    } else if (pf instanceof ObjectFilter) {
      if (!forOverrides) {
        if (pf instanceof EntityTypeFilter) {
          EntityTypeFilter etf = (EntityTypeFilter)pf;

          if ((etf.getEntity() == IcalDefs.entityTypeVavailability) ||
              (etf.getEntity() == IcalDefs.entityTypeVavailability)) {
            // Ensure we get or exclude both
            boolean not = etf.getNot();
            FilterBase both;

            if (not) {
              both = new AndFilter();
            } else {
              both = new OrFilter();
            }

            try {
              both.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                                  "vavailability",
                                                                  false));
              both.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                                  "available",
                                                                  false));
            } catch (Throwable t) {
              throw new CalFacadeException(t);
            }

            return both;
          }
        }
        return new BwObjectFilter(null, (ObjectFilter)pf);
      }

      /* An override - filter on collection as well */

      if (pf.getPropertyIndex() != PropertyInfoIndex.COLLECTION) {
        termsDropped = true;
        return null;
      }

      return new BwObjectFilter(null, (ObjectFilter)pf);
    } else {
      /* We assume we can't handle this one as a query.
       */
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }
  }

  private boolean match(final FilterBase f,
                        final BwEvent ev,
                        final String userHref) throws CalFacadeException {
    if (f == null) {
      return true;
    }

    if (debug) {
      debug("match " + f);
    }

    if ((f instanceof AndFilter) || (f instanceof OrFilter)) {
      boolean itsAnd = (f instanceof AndFilter);

      for (FilterBase flt: f.getChildren()) {
        if (match(flt, ev, userHref)) {
          if (!itsAnd) {
            // Success for OR
            if (debug) {
              debug("match true");
            }

            return true;
          }
        } else if (itsAnd) {
          debug("match true");
          return false;
        }
      }

      // For AND all matched, for OR nothing matched
      debug("match " + itsAnd);
      return itsAnd;
    }

    if (f instanceof EntityTimeRangeFilter) {
      return true; // Matched in db query
    }

    if (!(f instanceof PropertyFilter)) {
      /* We assume we can't handle this one as a query.
       */
      throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                   String.valueOf(f));
    }

    PropertyFilter pf = (PropertyFilter)f;
    BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

    if (pi == null) {
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                   String.valueOf(pf.getPropertyIndex()));
    }

    String fieldName = pi.getDbFieldName();
    boolean param = pi.getParam();

    if (param) {
      BwIcalPropertyInfoEntry parentPi =
        BwIcalPropertyInfo.getPinfo(pf.getParentPropertyIndex());

      fieldName = parentPi.getDbFieldName() + "." + fieldName;
    }

    try {
      if (pf instanceof BwCategoryFilter) {
        return traceMatch(pf.match(ev, userHref)) ;
      }

      if (f instanceof PresenceFilter) {
        return traceMatch(matchPresence(pf.getPropertyIndex(), ev, userHref));
      }

      if (pf instanceof TimeRangeFilter) {
        return traceMatch(match((TimeRangeFilter)pf, ev));
      }

      if (pf instanceof BwObjectFilter) {
        return traceMatch(pf.match(ev, userHref));
      }
    } catch (WebdavException wde) {
      throw new CalFacadeException(wde);
    }

    throw new CalFacadeException("org.bedework.filters.unknownfilter",
                                 String.valueOf(f));
  }

  private boolean traceMatch(final boolean val) {
    if (debug) {
      debug("match " + val);
    }

    return val;
  }

  private boolean match(final TimeRangeFilter f,
                        final BwEvent ev) throws CalFacadeException {

    switch (f.getPropertyIndex()) {
    case COMPLETED:
      return (ev.getCompleted() != null) &&
             matchTimeRange(f, ev.getCompleted());

    case DTSTAMP:
      return matchTimeRange(f, ev.getDtstamp());

    case LAST_MODIFIED:
      return matchTimeRange(f, ev.getLastmod());

    case VALARM:
      for (BwAlarm a: ev.getAlarms()) {
        if (matchTimeRange(f, a.getTrigger())) {
          return true;
        }
      }

      return false;

    default:
      return false;
    }
  }

  private boolean matchTimeRange(final TimeRangeFilter trf,
                                 final String fld) {
    TimeRange tr = trf.getEntity();

    if (tr.getStart() != null) {
      if (fld.compareTo(tr.getStart().toString()) < 0) {
        return false;
      }
    }

    if (tr.getEnd() != null) {
      if (fld.compareTo(tr.getEnd().toString()) >= 0) {
        return false;
      }
    }

    return true;
  }

  private boolean matchPresence(final PropertyInfoIndex pi,
                                final BwEvent ev,
                                final String userHref) throws CalFacadeException {

    switch (pi) {
    case CLASS:
      return ev.getClassification() != null;

    case CREATED:
      return true;

    case DESCRIPTION:
      return ev.getDescriptions().size() > 0;

    case DTSTAMP:
      return true;

    case DTSTART:
      return !ev.getNoStart();

    case DURATION:
      return ev.getEndType() == StartEndComponent.endTypeDuration;

    case GEO:
      return ev.getGeo() != null;

    case LAST_MODIFIED:
      return true;

    case LOCATION:
      return ev.getLocation() != null;

    case ORGANIZER:
      return ev.getOrganizer() != null;

    case PRIORITY:
      return ev.getPriority() != null;

    case RECURRENCE_ID:
      return ev.getRecurrenceId() != null;

    case SEQUENCE:
      return true;

    case STATUS:
      return ev.getStatus() != null;

    case SUMMARY:
      return ev.getSummaries().size() > 0;

    case UID:
      return true;

    case URL:
      return ev.getLink() != null;

    /* Event only */

    case DTEND:
      return ev.getEndType() == StartEndComponent.endTypeDate;

    case TRANSP:
      return ev.getPeruserTransparency(userHref) != null;

    /* Todo only */

    case COMPLETED:
      return ev.getCompleted() != null;

    case DUE:
      return ev.getEndType() == StartEndComponent.endTypeDate;

    case PERCENT_COMPLETE:
      return ev.getPercentComplete() != null;

    /* ---------------------------- Multi valued --------------- */

    /* Event and Todo */

    case ATTACH:
      break;

    case ATTENDEE :
      break;

    case CATEGORIES:
      return ev.getCategories().size() > 0;

    case COMMENT:
      return ev.getComments().size() > 0;

    case CONTACT:
      return ev.getContacts().size() > 0;

    case EXDATE:
      return ev.getExdates().size() > 0;

    case EXRULE :
      return ev.getExrules().size() > 0;

    case REQUEST_STATUS:
      return ev.getRequestStatuses().size() > 0;

    case RELATED_TO:
      return ev.getRelatedTo() != null;

    case RESOURCES:
      return ev.getResources().size() > 0;

    case RDATE:
      return ev.getRdates().size() > 0;

    case RRULE :
      return ev.getRrules().size() > 0;

    /* -------------- Other non-event: non-todo ---------------- */

    case FREEBUSY:
      break;

    case TZID:
      break;

    case TZNAME:
      break;

    case TZOFFSETFROM:
      break;

    case TZOFFSETTO:
      break;

    case TZURL:
      break;

    case ACTION:
      break;

    case REPEAT:
      break;

    case TRIGGER:
      break;

    case CREATOR:
      return true;

    case OWNER:
      return true;

    case ENTITY_TYPE:
      break;

    }

    return false;
  }

  /* Generate a where clause for a query which selects the events for the
   * given filter.
   *
   * @param f         Filter element.
   */
  private void makeWhere(final FilterBase f) throws CalFacadeException {
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
      PropertyFilter pf = (PropertyFilter)f;

      BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pf.getPropertyIndex());

      if (pi == null) {
        throw new CalFacadeException("org.bedework.filters.unknownproperty",
                                     String.valueOf(pf.getPropertyIndex()));
      }

      String fieldName = pi.getDbFieldName();
      boolean multi = pi.getMultiValued();
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
        throw new CalFacadeException("org.bedework.filters.unknownfilter",
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
  private void parReplace(final FilterBase f) throws CalFacadeException {
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
      sess.setString(parPrefix + qi, cat.getUid());
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
        sess.setParameter(parPrefix + qi, tr.getStart().toString());
        qi++;
      }

      if (tr.getEnd() != null) {
        sess.setParameter(parPrefix + qi, tr.getEnd().toString());
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
        for (Object co: c) {
          String s = (String)co;
          if (doCaseless) {
            s = s.toLowerCase();
          }

          sess.setParameter(parPrefix + qi, s);
          qi++;
        }

        return;
      }

      if (o instanceof BwCalendar) {
        BwCalendar cal = unwrap((BwCalendar)o);
        sess.setString(parPrefix + qi, cal.getPath());
      } else if (o instanceof BwPrincipal) {
        sess.setString(parPrefix + qi, ((BwPrincipal)o).getPrincipalRef());
      } else if (o instanceof BwDbentity) {
        sess.setEntity(parPrefix + qi, o);
      } else if (of.getExact()) {
        if (doCaseless) {
          o = ((String)o).toLowerCase();
        }

        sess.setParameter(parPrefix + qi, o);
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

  private void doEntityTimeRangeReplace(final EntityTimeRangeFilter trf) throws CalFacadeException {
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
                         final TimeRange tr) throws CalFacadeException {
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

  private void addJoins(final FilterBase f) throws CalFacadeException {
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
      throw new CalFacadeException("org.bedework.filters.unknownproperty",
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

  protected BwCalendar unwrap(final BwCalendar val) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      // We get these at the moment - getEvents at svci level
      return val;
      // CALWRAPPER throw new CalFacadeException("org.bedework.not.wrapped");
    }

    return ((CalendarWrapper)val).fetchEntity();
  }

  /* Get a logger for messages
   */
  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void debug(final String msg) {
    getLogger().debug(msg);
  }
}

