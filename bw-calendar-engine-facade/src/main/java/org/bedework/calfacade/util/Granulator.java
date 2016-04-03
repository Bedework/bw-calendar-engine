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
package org.bedework.calfacade.util;

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDuration;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/** Select periods in the Collection of periods which fall within a given
 * time period. By incrementing that time period we can break up the given
 * periods into time periods of a given granularity.
 *
 * <p>Don't make much sense? Try an example:
 *
 * <p>The given set of periods is a set of events for the week. The granularity
 * is one day - each call gives events appearing in that day - possibly extending
 * into previous and next days.
 *
 * <p>Another? The periods are a set of freebusy objects defining busy time for
 * one day.
 * The granularity is 30 minutes. The result is a free busy for a day divided
 * into 30 minute periods.
 *
 * @author Mike Douglass douglm at bedework.edu
 */
public class Granulator {
  private Granulator() {}

  /** This class defines the entities which occupy time and the period of
   * interest and can be passed repeatedly to getPeriodsEvents.
   *
   * <p>The end datetime will be updated ready for the next call. If endDt is
   * non-null on entry it will be used to set the startDt.
   */
  public static class GetPeriodsPars implements Serializable {
    boolean debug = false;

    /** Event Info or EventPeriod or Period objects to extract from */
    public Collection<?> periods;
    /** Start of period - updated at each call from endDt */
    public BwDateTime startDt;
    /** Duration of period or granularity */
    public BwDuration dur;

    /** On return has the end date of the period. */
    public BwDateTime endDt;
  }

  /** Select the events in the collection which fall within the period
   * defined by the start and duration.
   *
   * @param   pars      GetPeriodsPars object
   * @return  Collection of EventInfo being one days events or empty for no events.
   * @throws CalFacadeException
   */
  public static Collection<?> getPeriodsEvents(GetPeriodsPars pars) throws CalFacadeException {
    ArrayList<Object> al = new ArrayList<Object>();
    long millis = 0;
    if (pars.debug) {
      millis = System.currentTimeMillis();
    }

    if (pars.endDt != null) {
      pars.startDt = pars.endDt.copy();
    }
    pars.endDt = pars.startDt.addDuration(pars.dur);
    String start = pars.startDt.getDate();
    String end = pars.endDt.getDate();

    if (pars.debug) {
      debugMsg("Did UTC stuff in " + (System.currentTimeMillis() - millis));
    }

    EntityRange er = new EntityRange();
    Iterator<?> it = pars.periods.iterator();
    while (it.hasNext()) {
      er.setEntity(it.next());

      /* Period is within range if:
             ((evstart < end) and ((evend > start) or
                 ((evstart = evend) and (evend >= start))))
       */

      int evstSt = er.start.compareTo(end);

      //debugMsg("                   event " + evStart + " to " + evEnd);

      if (evstSt < 0) {
        int evendSt = er.end.compareTo(start);

        if ((evendSt > 0) ||
            (er.start.equals(er.end) && (evendSt >= 0))) {
          // Passed the tests.
          //if (debug) {
          //  debugMsg("Event passed range " + start + "-" + end +
          //           " with dates " + evStart + "-" + evEnd +
          //           ": " + ev.getSummary());
          //}
          al.add(er.entity);
        }
      }
    }

    return al;
  }

  private static class EntityRange {
    Object entity;

    String start;
    String end;

    void setEntity(Object o) throws CalFacadeException {
      entity = o;

      if (o instanceof EventInfo) {
        EventInfo ei = (EventInfo)o;
        BwEvent ev = ei.getEvent();

        start = ev.getDtstart().getDate();
        end = ev.getDtend().getDate();

        return;
      }

      if (o instanceof EventPeriod) {
        EventPeriod ep = (EventPeriod)o;

        start = String.valueOf(ep.getStart());
        end = String.valueOf(ep.getEnd());

        return;
      }

      if (o instanceof Period) {
        Period p = (Period)o;

        start = String.valueOf(p.getStart());
        end = String.valueOf(p.getEnd());

        return;
      }

      start = null;
      end = null;
    }
  }

  /**
   *
   */
  public static class EventPeriod implements Comparable<EventPeriod>,
                                             Serializable {
    private DateTime start;
    private DateTime end;
    private int type;  // from BwFreeBusyComponent

    /* Number of busy entries this period - for the free/busy aggregator */
    private int numBusy;

    /* Number of tentative entries this period - for the free/busy aggregator */
    private int numTentative;

    /** Constructor
     *
     * @param start
     * @param end
     * @param type
     */
    public EventPeriod(DateTime start, DateTime end, int type) {
      this.start = start;
      this.end = end;
      this.type = type;
    }

    /**
     * @return DateTime
     */
    public DateTime getStart() {
      return start;
    }

    /**
     * @return DateTime
     */
    public DateTime getEnd() {
      return end;
    }

    /**
     * @param val int
     */
    public void setType(int val) {
      type = val;
    }

    /**
     * @return int
     */
    public int getType() {
      return type;
    }

    public int compareTo(EventPeriod that) {
      /* Sort by type first */
      if (type < that.type) {
        return -1;
      }

      if (type > that.type) {
        return 1;
      }

      int res = start.compareTo(that.start);
      if (res != 0) {
        return res;
      }

      return end.compareTo(that.end);
    }

    /**
     * @param val
     */
    public void setNumBusy(int val) {
      numBusy = val;
    }

    /**
     * @return int
     */
    public int getNumBusy() {
      return numBusy;
    }

    /**
     * @param val
     */
    public void setNumTentative(int val) {
      numTentative = val;
    }

    /**
     * @return int
     */
    public int getNumTentative() {
      return numTentative;
    }

    public boolean equals(Object o) {
      return compareTo((EventPeriod)o) == 0;
    }

    public int hashCode() {
      return 7 * (type + 1) * (start.hashCode() + 1) * (end.hashCode() + 1);
    }

    public String toString() {
      StringBuffer sb = new StringBuffer("EventPeriod{start=");

      sb.append(start);
      sb.append(", end=");
      sb.append(end);
      sb.append(", type=");
      sb.append(type);
      sb.append("}");

      return sb.toString();
    }
  }

  private static void debugMsg(String msg) {
    Logger.getLogger(Granulator.class).debug(msg);
  }
}
