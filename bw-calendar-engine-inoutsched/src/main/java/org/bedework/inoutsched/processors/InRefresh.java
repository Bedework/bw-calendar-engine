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
package org.bedework.inoutsched.processors;

import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;
import org.bedework.icalendar.Icalendar;

import java.util.Collection;

/** Handles incoming method REFRESH scheduling messages.
 *
 * @author Mike Douglass
 */
public class InRefresh extends InProcessor {
  /**
   * @param svci
   */
  public InRefresh(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei
   * @return ScheduleResult
   * @throws CalFacadeException
   */
  @Override
  public ProcessResult process(final EventInfo ei) throws CalFacadeException {
    SchedulingI sched =  getSvc().getScheduler();
    BwEvent ev = ei.getEvent();
    ProcessResult pr = new ProcessResult();

    pr.noInboxChange = true;

    /* Refresh request from attendee - send our copy
     */

    /* Should be exactly one attendee. */
    Collection<BwAttendee> atts = ev.getAttendees();
    if ((atts == null) || (atts.size() != 1)) {
      return null;
    }

    BwAttendee att = atts.iterator().next();

    /* We can only do this if there is an active copy */

    EventInfo calEi = sched.getStoredMeeting(ev);

    if (calEi == null) {
      return null;
    }

    /* Just send it to the attendee. */
    pr.sr = sched.schedule(calEi,
                           Icalendar.methodTypeRefresh,
                           att.getAttendeeUri(), null, false);

    if (pr.sr.errorCode == null) {
      getSvc().getEventsHandler().delete(ei, false);
    }

    return pr;
  }
}
