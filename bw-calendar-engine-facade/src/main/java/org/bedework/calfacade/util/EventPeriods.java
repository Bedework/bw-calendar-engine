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
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.logging.Logged;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;

import java.util.TreeSet;

/** Class to help handling of event periods when building free busy information
 * @author douglm
 *
 */
public class EventPeriods implements Logged {
  private BwDateTime start;
  private String startTzid;
  private DateTime dtstart;

  private BwDateTime end;
  private String endTzid;
  private DateTime dtend;

  private Object[] periods = new Object[4];

  /**
   * @param start
   * @param end
   * @throws CalFacadeException
   */
  public EventPeriods(final BwDateTime start,
                      final BwDateTime end) throws CalFacadeException {
    this.start = start;
    this.end = end;

    startTzid = start.getTzid();
    endTzid = end.getTzid();

    try {
      dtstart = new DateTime(Timezones.getUtc(start.getDtval(),
                                              startTzid));
      dtend = new DateTime(Timezones.getUtc(end.getDtval(), endTzid));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    dtstart.setUtc(true);
    dtend.setUtc(true);
  }

  /**
   * @param pstart
   * @param pend
   * @param type
   * @throws CalFacadeException
   */
  public void addPeriod(final BwDateTime pstart, final BwDateTime pend,
                        final int type) throws CalFacadeException {
    // Ignore if times were specified and this period is outside the times

    /* Don't report out of the requested period */

    String dstart;
    String dend;

    if ((pstart.after(end)) || (pend.before(start))) {
      //XXX Should get here - but apparently we do.
      return;
    }

    if (pstart.before(start)) {
      dstart = start.getDate();
    } else {
      dstart = pstart.getDate();
    }

    if (pend.after(end)) {
      dend = end.getDate();
    } else {
      dend = pend.getDate();
    }

    try {
      DateTime psdt = new DateTime(dstart);
      DateTime pedt = new DateTime(dend);

      psdt.setUtc(true);
      pedt.setUtc(true);

      add(new EventPeriod(psdt, pedt, type));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param pstart
   * @param pend
   * @param type
   * @throws CalFacadeException
   */
  public void addPeriod(DateTime pstart, DateTime pend,
                        final int type) throws CalFacadeException {
    // Ignore if times were specified and this period is outside the times

    /* Don't report out of the requested period */

    if ((pstart.after(dtend)) || (pend.before(dtstart))) {
      //XXX Should get here - but apparently we do.
      return;
    }

    if (pstart.before(dtstart)) {
      pstart = dtstart;
    }

    if (pend.after(dtend)) {
      pend = dtend;
    }

    add(new EventPeriod(pstart, pend, type));
  }

  /**
   * @param type
   * @return BwFreeBusyComponent or null for no entries
   * @throws CalFacadeException
   */
  @SuppressWarnings("unchecked")
  public BwFreeBusyComponent makeFreeBusyComponent(final int type) throws CalFacadeException {
    TreeSet<EventPeriod> eventPeriods = (TreeSet<EventPeriod>)periods[type];
    if (eventPeriods == null) {
      return null;
    }

    BwFreeBusyComponent fbc = new BwFreeBusyComponent();
    fbc.setType(type);

    Period p = null;

    for (EventPeriod ep: eventPeriods) {
      if (debug()) {
        trace(ep.toString());
      }

      if (p == null) {
        p = new Period(ep.start, ep.end);
      } else if (ep.start.after(p.getEnd())) {
        // Non adjacent periods
        fbc.addPeriod(p.getStart(), p.getEnd());

        p = new Period(ep.start, ep.end);
      } else if (ep.end.after(p.getEnd())) {
        // Extend the current period
        p = new Period(p.getStart(), ep.end);
      } // else it falls within the existing period
    }

    if (p != null) {
      fbc.addPeriod(p.getStart(), p.getEnd());
    }

    return fbc;
  }

  @SuppressWarnings("unchecked")
  private void add(final EventPeriod p) {
    TreeSet<EventPeriod> eps = (TreeSet<EventPeriod>)periods[p.type];
    if (eps == null) {
      eps = new TreeSet<EventPeriod>();
      periods[p.type] = eps;
    }
    eps.add(p);
  }
}
