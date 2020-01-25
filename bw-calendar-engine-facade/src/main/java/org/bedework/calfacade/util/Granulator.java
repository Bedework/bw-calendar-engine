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
import org.bedework.calfacade.svc.EventInfo;

import net.fortuna.ical4j.model.Period;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;

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
    //boolean debug = false;

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
   */
  public static Collection<?> getPeriodsEvents(GetPeriodsPars pars) {
    ArrayList<Object> al = new ArrayList<>();
    //long millis = 0;
    //if (pars.debug) {
    //  millis = System.currentTimeMillis();
    //}

    if (pars.endDt != null) {
      pars.startDt = pars.endDt.copy();
    }
    pars.endDt = pars.startDt.addDuration(pars.dur);
    String start = pars.startDt.getDate();
    String end = pars.endDt.getDate();

    EntityRange er = new EntityRange();
    for (final Object o : pars.periods) {
      er.setEntity(o);

      /* Period is within range if:
             ((evstart < end) and ((evend > start) or
                 ((evstart = evend) and (evend >= start))))
       */

      int evstSt = er.start.compareTo(end);

      //debug("                   event " + evStart + " to " + evEnd);

      if (evstSt < 0) {
        int evendSt = er.end.compareTo(start);

        if ((evendSt > 0) ||
                (er.start.equals(er.end) && (evendSt >= 0))) {
          // Passed the tests.
          //if (debug()) {
          //  debug("Event passed range " + start + "-" + end +
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

    void setEntity(Object o) {
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
}
