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
package org.bedework.icalendar;

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDuration;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwRecurrenceInstance;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

/** Some help with recurrences.
 *
 * @author Mike Douglass     douglm - rpi.edu
 *  @version 1.0
 */
public class RecurUtil {
  private static BwLogger logger =
          new BwLogger().setLoggedClass(RecurUtil.class);

  /**
   */
  public static class RecurRange {
    /** The earliest date */
    public Date rangeStart;

    /** The latest date */
    public Date rangeEnd;
  }

  /**
   * Returns range of dates for this recurring event possibly bounded by
   * the supplied maximum end date.
   *
   * @param ev        the recurring event
   * @param maxYears  Provide an upper limit
   * @return the range for this event
   * @throws CalFacadeException
   */
  @SuppressWarnings("unchecked")
  public static RecurRange getRange(final BwEvent ev,
                                    final int maxYears) throws CalFacadeException {
    PropertyList evprops = new PropertyList();
    VEventUtil.doRecurring(ev, evprops);
    RecurRange rr = new RecurRange();

    DtStart start = ev.getDtstart().makeDtStart();
    DtEnd end = ev.getDtend().makeDtEnd();
    Duration duration = new Duration(null, ev.getDuration());

    //boolean durSpecified = ev.getEndType() == BwEvent.endTypeDuration;

    rr.rangeStart = start.getDate();

    for (Object o: evprops){
      if (o instanceof RDate) {
        RDate rd = (RDate)o;

        for (Object o1: rd.getDates()) {
          Date d = (Date)o1;

          if (d.before(rr.rangeStart)) {
            rr.rangeStart = d;
          }
        }
      }
    }

    /* Limit date according to system settings
     */
    Dur dur = new Dur(maxYears * 365, 0, 0, 0);

    Date maxRangeEnd = new Date(dur.getTime(rr.rangeStart));

    if (ev.getParent() != null) {
      BwDateTime pend = ev.getParent().getDtend();

      if (pend != null) {
        Date dt = pend.makeDate();

        if (dt.before(maxRangeEnd)) {
          maxRangeEnd = dt;
        }
      }
    }

    rr.rangeEnd = getLatestRecurrenceDate(evprops,
                                          start, end, duration,
                                          maxRangeEnd);

    if ((rr.rangeEnd == null) || (rr.rangeEnd.after(maxRangeEnd))) {
      rr.rangeEnd = maxRangeEnd;
    }

    return rr;
  }

  /**
   */
  public static class RecurPeriods {
    /** The recurrences */
    public Collection<Period> instances;
    //public PeriodList instances;

    /** The earliest date */
    public Date rangeStart;

    /** The latest date */
    public Date rangeEnd;
  }

  /**
   * Returns a list of instances for this recurring event possibly bounded by
   * the supplied maximum end date.
   *
   * <p>This is mostly a copy of VEvent.getConsumedTime()
   *
   * @param ev        the recurring event
   * @param maxYears  Provide an upper limit
   * @param maxInstances to limit
   * @return a list of periods for this event
   * @throws CalFacadeException on error
   */
  @SuppressWarnings("unchecked")
  public static RecurPeriods getPeriods(final BwEvent ev,
                                        final int maxYears,
                                        final int maxInstances) throws CalFacadeException {
    return getPeriods(ev, maxYears, maxInstances, null, null);
  }

  /**
   * Returns a list of instances for this recurring event possibly bounded by
   * the supplied maximum end date.
   *
   * <p>This is mostly a copy of VEvent.getConsumedTime()
   *
   * @param ev        the recurring event
   * @param maxYears  Provide an upper limit
   * @param maxInstances to limit
   * @param startRange null or set earliest
   * @param endRange null or set latest
   * @return a list of periods for this event
   * @throws CalFacadeException on error
   */
  public static RecurPeriods getPeriods(final BwEvent ev,
                                        final int maxYears,
                                        final int maxInstances,
                                        final String startRange, 
                                        final String endRange) throws CalFacadeException {
    final PropertyList evprops = new PropertyList();
    VEventUtil.doRecurring(ev, evprops);
    final RecurPeriods rp = new RecurPeriods();

    //DtStart vstart = (DtStart)IcalUtil.getProperty(comp, Property.DTSTART);
    /* BwDateTime evstart = ev.getDtstart();
    String tzid = evstart.getTzid();

    DtStart start = new DtStart();

    if (tzid != null) {
      start.setTimeZone(timezones.getTimeZone(tzid));
    }

    try {
      start.setValue(evstart.getDtval());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }*/

    final DtStart start = ev.getDtstart().makeDtStart();

    if (startRange != null) {
      try {
        rp.rangeStart = new DateTime(startRange);
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      }
    } else {
      //boolean durSpecified = ev.getEndType() == BwEvent.endTypeDuration;

      rp.rangeStart = start.getDate();

      for (final Object o : evprops) {
        if (o instanceof RDate) {
          final RDate rd = (RDate)o;

          for (final Object o1 : rd.getDates()) {
            final Date d = (Date)o1;

            if (d.before(rp.rangeStart)) {
              rp.rangeStart = d;
            }
          }
        }
      }
    }

    /* Limit date according to system settings
     */
    final Dur dur = new Dur(maxYears * 365, 0, 0, 0);

    Date maxRangeEnd = new Date(dur.getTime(rp.rangeStart));

    if (ev.getParent() != null) {
      final BwDateTime pend = ev.getParent().getDtend();

      if (pend != null) {
        final Date dt = pend.makeDate();

        if (dt.before(maxRangeEnd)) {
          maxRangeEnd = dt;
        }
      }
    }

    final DtEnd end = ev.getDtend().makeDtEnd();
    
    if (endRange != null) {
      try {
        rp.rangeEnd = new DateTime(endRange);
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      }
    } else {
      final Duration duration = new Duration(null, ev.getDuration());
      rp.rangeEnd = getLatestRecurrenceDate(evprops, start, end,
                                            duration,
                                            maxRangeEnd);

      if ((rp.rangeEnd == null) || (rp.rangeEnd.after(maxRangeEnd))) {
        rp.rangeEnd = maxRangeEnd;
      }
    }
    
    Period rangePeriod = new Period(new DateTime(rp.rangeStart),
                                    new DateTime(rp.rangeEnd));

    VEvent vev = new VEvent();
    PropertyList vevprops = vev.getProperties();
    vevprops.addAll(evprops);
    if (!ev.getSuppressed()) {
      // Allow inclusion of master start/end
      vevprops.add(start);
      vevprops.add(end);
    } else {
      // Move start/end outside of our period
      Dur evdur = new Dur(ev.getDuration());
      Dur setback = evdur.add(new Dur(1, 0, 0, 0)); // Ensure at least a day

      boolean dateOnly = ev.getDtstart().getDateType();
      Date adjustedEnd;

      if (dateOnly) {
        adjustedEnd = new Date(rp.rangeStart);
      } else {
        adjustedEnd = new DateTime(rp.rangeStart);
      }
      adjustedEnd.setTime(setback.negate().getTime(rp.rangeStart).getTime());
      vevprops.add(new DtEnd(adjustedEnd));

      // End now before range - make start evdur before that
      Date adjustedStart;

      if (dateOnly) {
        adjustedStart = new Date(adjustedEnd);
      } else {
        adjustedStart = new DateTime(adjustedEnd);
      }
      adjustedStart.setTime(evdur.negate().getTime(adjustedEnd).getTime());
      vevprops.add(new DtStart(adjustedStart));
    }

    PeriodList pl = vev.calculateRecurrenceSet(rangePeriod);

    /*
    PeriodList periods = new PeriodList();
    if (ev.getDtstart().isUTC()) {
      periods.setUtc(true);
    } else if (start.getDate() instanceof DateTime) {
      periods.setTimeZone(((DateTime)start.getDate()).getTimeZone());
    } else {
      try {
        periods.setTimeZone(Timezones.getDefaultTz());
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }
    rp.instances = periods;

    // if no start date return empty list..
    if (start == null) {
      return rp;
    }
    // if an explicit event duration is not specified, derive a value for recurring
    // periods from the end date..
    Dur rDuration;
    Dur adjustDuration;
    if (duration == null) {
      if (end == null) {
        rDuration = new Dur(0);
        adjustDuration = new Dur(0, 0, 0, 1); // 1 second fudge
      } else {
        rDuration = new Dur(start.getDate(), end.getDate());
        adjustDuration = rDuration;
      }
    } else {
      rDuration = duration.getDuration();
      adjustDuration = rDuration;
    }
    // adjust range start back by duration to allow for recurrences that
    // start before the range but finish inside..
//  FIXME: See bug #1325558..
    Date adjustedRangeStart = new DateTime(rp.rangeStart);
    adjustedRangeStart.setTime(adjustDuration.negate().getTime(rp.rangeStart).getTime());

    // recurrence dates..
    PropertyList rDates = evprops.getProperties(Property.RDATE);
    for (Iterator i = rDates.iterator(); i.hasNext();) {
      RDate rdate = (RDate) i.next();

      if (Value.PERIOD.equals(rdate.getParameter(Parameter.VALUE))) {
        /* These fully define the period * /
        for (Iterator j = rdate.getPeriods().iterator(); j.hasNext();) {
          Period period = (Period) j.next();
          if (period.getStart().before(rp.rangeEnd) &&
              period.getEnd().after(rp.rangeStart)) {
            periods.add(period);
          }
        }
      } else {
        // Create a period based on rdate and duration
        DateList startDates = rdate.getDates();
        for (int j = 0; j < startDates.size(); j++) {
          Date startDate = (Date) startDates.get(j);
          periods.add(new Period(new DateTime(startDate), rDuration));
        }
      }
    }

    Value startVal = (Value)start.getParameter(Parameter.VALUE);
    if (startVal == null) {
      startVal = Value.DATE_TIME;
    }

    // recurrence rules..
    PropertyList rRules = evprops.getProperties(Property.RRULE);
    for (Iterator i = rRules.iterator(); i.hasNext();) {
      RRule rrule = (RRule) i.next();
      Recur recur = rrule.getRecur();

      // Limit nummber of instances.

      DateList startDates = recur.getDates(start.getDate(),
                                           adjustedRangeStart,
                                           rp.rangeEnd,
                                           startVal,
                                           maxInstances);
//    DateList startDates = rrule.getRecur().getDates(start.getDate(), rangeStart, rangeEnd, (Value) start.getParameters().getParameter(Parameter.VALUE));
      for (int j = 0; j < startDates.size(); j++) {
        Date startDate = (Date) startDates.get(j);
        periods.add(new Period(new DateTime(startDate), rDuration));
      }
    }

    // add initial instance if intersection with the specified period.

    if (!ev.getSuppressed()) {
      Period startPeriod = null;
      if (!durSpecified) {
        startPeriod = new Period(new DateTime(start.getDate()),
                                 new DateTime(end.getDate()));
      } else {
        startPeriod = new Period(new DateTime(start.getDate()),
                                 duration.getDuration());
      }
      if (rangePeriod.intersects(startPeriod)) {
          periods.add(startPeriod);
      }
    }

    // exception dates..
    PropertyList exDates = evprops.getProperties(Property.EXDATE);
    for (Iterator i = exDates.iterator(); i.hasNext();) {
      ExDate exDate = (ExDate) i.next();
      for (Iterator j = periods.iterator(); j.hasNext();) {
        Period period = (Period) j.next();
        DateList dl = exDate.getDates();
        // for DATE-TIME instances check for DATE-based exclusions also..
        if (dl.contains(period.getStart())) {
          j.remove();
        } else if (dl.contains(new Date(period.getStart()))) {
          j.remove();
        }
      }
    }
    // exception rules..
    // FIXME: exception rules should be consistent with exception dates (i.e. not use periods?)..
    PropertyList exRules = evprops.getProperties(Property.EXRULE);
    PeriodList exPeriods = new PeriodList();
    for (Iterator i = exRules.iterator(); i.hasNext();) {
      ExRule exrule = (ExRule) i.next();
//    DateList startDates = exrule.getRecur().getDates(start.getDate(), adjustedRangeStart, rangeEnd, (Value) start.getParameters().getParameter(Parameter.VALUE));
      DateList startDates = exrule.getRecur().getDates(start.getDate(),
                                                       rp.rangeStart,
                                                       rp.rangeEnd,
                                                       startVal);
      for (Iterator j = startDates.iterator(); j.hasNext();) {
        Date startDate = (Date) j.next();
        exPeriods.add(new Period(new DateTime(startDate), rDuration));
      }
    }
    // apply exceptions..
    if (!exPeriods.isEmpty()) {
      periods = periods.subtract(exPeriods);
    }
    // if periods already specified through recurrence, return..
    // ..also normalise before returning.
//    if (!periods.isEmpty()) {
//      periods = periods.normalise();
//    }
 *
 */
    if (pl.size() <= maxInstances) {
      rp.instances = pl;
    } else {
      rp.instances = new TreeSet<Period>();

      for (Object o: pl) {
        rp.instances.add((Period)o);
        if (rp.instances.size() == maxInstances) {
          break;
        }
      }
    }

    return rp;
  }

  /**
   * @author douglm
   *
   */
  public static class Recurrence {
    /** */
    public BwDateTime start;
    /** */
    public BwDateTime end;
    /** */
    public String recurrenceId;

    /** non-null if this is due to an override */
    public BwEvent override;

    /**
     * @param start
     * @param end
     * @param recurrenceId
     * @param override
     */
    public Recurrence(final BwDateTime start,
                      final BwDateTime end,
                      final String recurrenceId,
                      final BwEvent override) {
      this.start = start;
      this.end = end;
      this.recurrenceId = recurrenceId;
      this.override = override;
    }
  }

  /** Generate a recurrence instance for the given master event
   * based on the recurrenceId and the date/time info in the master.
   *
   * @param master event
   * @param recurrenceId for the instance.
   * @return instance object filled in.
   * @throws CalFacadeException
   */
  public static BwRecurrenceInstance fromRecurrencId(final BwEvent master,
                                                     String recurrenceId)
          throws CalFacadeException {
    final String stzid = master.getDtstart().getTzid();
    final boolean dateOnly = master.getDtstart().getDateType();

    final BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly,
                                                        recurrenceId,
                                                        stzid);
    final BwDateTime rend = rstart.addDuration(
            BwDuration.makeDuration(master.getDuration()));


    final BwRecurrenceInstance ri = new BwRecurrenceInstance();

    ri.setDtstart(rstart);
    ri.setDtend(rend);
    ri.setRecurrenceId(ri.getDtstart().getDate());
    ri.setMaster(master);

    return ri;
  }

  /**
   * @param ei       master event
   * @param maxYears Max number of years to prodice
   * @param maxInstances max number of instances
   * @param fromDate UTC date-time - if non-null only this and after
   * @param toDate UTC date-time - if non-null before this date
   * @return collection of recurrences
   * @throws CalFacadeException
   */
  public static Collection<Recurrence> getRecurrences(final EventInfo ei,
                                                      final int maxYears,
                                                      final int maxInstances,
                                                      final String fromDate,
                                                      final String toDate) throws CalFacadeException {
    try {
      final BwEvent ev = ei.getEvent();

      final RecurPeriods rp = getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return null;
      }

      final Collection<Recurrence> recurrences = new ArrayList<>();

      final String stzid = ev.getDtstart().getTzid();

      // ev.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(),
      // stzid));
      int instanceCt = maxInstances;

      final boolean dateOnly = ev.getDtstart().getDateType();

      Map<String, BwEvent> overrides = new HashMap<>();

      if (!Util.isEmpty(ei.getOverrideProxies())) {
        for (final BwEvent ov: ei.getOverrideProxies()) {
          overrides.put(ov.getRecurrenceId(), ov);
        }
      }

      for (final Period p: rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        final String rid = rstart.getDate();
        BwDateTime rend = null;
        final BwEvent override = overrides.get(rid);

        if (override != null) {
          rstart = override.getDtstart();
          rend = override.getDtend();
        } else {
          dtval = p.getEnd().toString();
          if (dateOnly) {
            dtval = dtval.substring(0, 8);
          }

          rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);
        }

        if (inDateTimeRange(fromDate, toDate,
                            rstart.getDate(), rend.getDate())) {
          recurrences.add(new Recurrence(rstart, rend, rid, override));

          instanceCt--;
          if (instanceCt == 0) {
            // That's all you're getting from me
            break;
          }
        }
      }

      return recurrences;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }


  /** Returns true if the period start and end dates overlap the specified
   * limits. Either or both of rangeStart and rangeEnd may be null.
   *
   * @param rangeStart - UTC date/time or null
   * @param rangeEnd - UTC date/time or null
   * @param periodStart - UTC date/time, NOT null
   * @param periodEnd - UTC date/time, NOT null
   * @return true if event satisfies the limits
   */
  public static boolean inDateTimeRange(final String rangeStart,
                                        final String rangeEnd,
                                        final String periodStart,
                                        final String periodEnd) {
    int evstSt;

    if (rangeEnd == null) {
      evstSt = -1;   // < infinity
    } else {
      evstSt = periodStart.compareTo(rangeEnd);
    }

    if (evstSt >= 0) {
      return false;
    }

    int evendSt;

    if (rangeStart == null) {
      evendSt = 1;   // > infinity
    } else {
      evendSt = periodEnd.compareTo(rangeStart);
    }

    if ((evendSt > 0) ||
            (periodStart.equals(periodEnd) && (evendSt >= 0))) {
      // Passed the tests.
      return true;
    }

    return false;
  }

  /** Return the absolute latest end date for this event. Note that
   * exclusions may mean the actual latest date is earlier.
   *
   * @param evprops
   * @param vstart
   * @param enddt
   * @param d
   * @param maxRangeEnd
   * @return date
   * @throws CalFacadeException
   */
  private static Date getLatestRecurrenceDate(final PropertyList evprops,
                                              final DtStart vstart,
                                              final DtEnd enddt,
                                              final Duration d,
                                              final Date maxRangeEnd) throws CalFacadeException {
    try {
      Date start = vstart.getDate();
      Date until = null;

      /* Get the duration of the event to get us past the end
       */
      Dur dur;

      if  (d != null) {
        dur = d.getDuration();
      } else {
        Date evend;

        if (enddt == null) {
          evend = start;
        } else {
          evend = enddt.getDate();
        }

        dur = new Dur(start, evend);
      }

      /* Make a new duration incremented a little to avoid any boundary
          conditions */
      if  (dur.getWeeks() != 0) {
        dur = new Dur(dur.getWeeks() + 1);
      } else {
        dur = new Dur(dur.getDays() + 1, dur.getHours(), dur.getMinutes(),
                      dur.getSeconds());
      }

      PropertyList rrules = evprops.getProperties(Property.RRULE);
      PropertyList rdts = evprops.getProperties(Property.RDATE);

      if ((rrules == null) && (rdts == null)) {
        // Not a recurring event
        return null;
      }

      if (rrules != null) {
        Iterator rit = rrules.iterator();
        while (rit.hasNext()) {
          RRule r = (RRule)rit.next();

          Date nextUntil = getLastDate(r.getRecur(), start, maxRangeEnd);
          if (nextUntil == null) {
            /* We have a rule without an end date so it's infinite.
             */
            return null;
          }

          if (logger.debug()) {
            logger.debug("Last date for recur=" + nextUntil);
          }

          if ((until == null) || (nextUntil.after(until))) {
            until = nextUntil;
          }
        }

        /* We have some rules - none have an end date so it's infinite.
         */
        if (until == null) {
          // infinite
          return null;
        }
      }

      if (rdts != null) {
        // Get the latest date from each
        // XXX are these sorted?
        Iterator rit = rdts.iterator();
        while (rit.hasNext()) {
          RDate r = (RDate)rit.next();

          if (Value.PERIOD.equals(r.getParameter(Parameter.VALUE))) {
            PeriodList pl = r.getPeriods();

            Iterator it = pl.iterator();

            while (it.hasNext()) {
              Period p = (Period)it.next();

              // Not sure if a single date gives a null end
              Date nextUntil = p.getEnd();

              if (nextUntil == null) {
                nextUntil = p.getStart();
              }

              if ((until == null) || (nextUntil.after(until))) {
                until = nextUntil;
              }
            }
          } else {
            // date or datetime
            DateList startDates = r.getDates();
            for (int j = 0; j < startDates.size(); j++) {
              Date startDate = (Date)startDates.get(j);
              Date endDate = new Date(dur.getTime(startDate));

              if ((until == null) || (endDate.after(until))) {
                until = endDate;
              }
            }
          }
        }
      }

      if (logger.debug()) {
        logger.debug("Last date before fix=" + until);
      }

      /* Now add the duration of the event to get us past the end
       */
      if (until instanceof DateTime) {
        until = new DateTime(dur.getTime(until));
        ((DateTime)until).setUtc(true);
      } else {
        until = new Date(dur.getTime(until));
      }

      if (logger.debug()) {
        logger.debug("Last date after fix=" + until);
      }

      return until;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the highest possible start date from this recurrence or null
   * if no count or until date specified
   */
  private static Date getLastDate(final Recur r, Date start,
                                  final Date maxRangeEnd) {
    Date seed = start;
    Date until = r.getUntil();

    if (until != null) {
      return until;
    }

    int count = r.getCount();
    if (count < 1) {
      return null;
    }

    Dur days100 = new Dur(100, 0, 0, 0);
    int counted = 0;

    while ((counted < count) && (start.before(maxRangeEnd))) {
      Date end = new DateTime(days100.getTime(start));
      DateList dl = r.getDates(seed, start, end, Value.DATE_TIME);

      int sz = dl.size();
      counted += sz;
      if (sz != 0) {
        until = (Date)dl.get(sz - 1);
      }
      start = end;
    }

    return until;
  }
}
