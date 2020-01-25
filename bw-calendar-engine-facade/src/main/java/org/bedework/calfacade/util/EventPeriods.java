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
import org.bedework.util.logging.BwLogger;
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
   * @param start bw date/time
   * @param end bw date/time
   */
  public EventPeriods(final BwDateTime start,
                      final BwDateTime end) {
    this.start = start;
    this.end = end;

    startTzid = start.getTzid();
    endTzid = end.getTzid();

    try {
      dtstart = new DateTime(Timezones.getUtc(start.getDtval(),
                                              startTzid));
      dtend = new DateTime(Timezones.getUtc(end.getDtval(), endTzid));
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    dtstart.setUtc(true);
    dtend.setUtc(true);
  }

  /**
   * @param pstart bw date/time
   * @param pend bw date/time
   * @param type from BwFreeBusyComponent
   */
  public void addPeriod(final BwDateTime pstart, final BwDateTime pend,
                        final int type) {
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

    DateTime psdt;
    DateTime pedt;

    try {
      psdt = new DateTime(dstart);
      pedt = new DateTime(dend);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    psdt.setUtc(true);
    pedt.setUtc(true);

    add(new EventPeriod(psdt, pedt, type));
  }

  /**
   * @param pstart bw date/time
   * @param pend bw date/time
   * @param type from BwFreeBusyComponent
   */
  public void addPeriod(DateTime pstart,
                        DateTime pend,
                        final int type) {
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
   * @param type from BwFreeBusyComponent
   * @return BwFreeBusyComponent or null for no entries
   */
  @SuppressWarnings("unchecked")
  public BwFreeBusyComponent makeFreeBusyComponent(final int type) {
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
        p = new Period(ep.getStart(), ep.getEnd());
      } else if (ep.getStart().after(p.getEnd())) {
        // Non adjacent periods
        fbc.addPeriod(p.getStart(), p.getEnd());

        p = new Period(ep.getStart(), ep.getEnd());
      } else if (ep.getEnd().after(p.getEnd())) {
        // Extend the current period
        p = new Period(p.getStart(), ep.getEnd());
      } // else it falls within the existing period
    }

    if (p != null) {
      fbc.addPeriod(p.getStart(), p.getEnd());
    }

    return fbc;
  }

  @SuppressWarnings("unchecked")
  private void add(final EventPeriod p) {
    TreeSet<EventPeriod> eps =
            (TreeSet<EventPeriod>)periods[p.getType()];
    if (eps == null) {
      eps = new TreeSet<>();
      periods[p.getType()] = eps;
    }
    eps.add(p);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
