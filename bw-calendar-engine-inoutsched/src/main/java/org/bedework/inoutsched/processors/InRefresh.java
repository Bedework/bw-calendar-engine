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

import org.bedework.calfacade.Participant;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.response.Response;

/** Handles incoming method REFRESH scheduling messages.
 *
 * @author Mike Douglass
 */
public class InRefresh extends InProcessor {
  /**
   * @param svci interface
   */
  public InRefresh(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei the event
   * @return ScheduleResult
   */
  @Override
  public ProcessResult process(final EventInfo ei) {
    final SchedulingI sched =  getSvc().getScheduler();
    final BwEvent ev = ei.getEvent();
    final ProcessResult pr = new ProcessResult();

    pr.noInboxChange = true;

    /* Refresh request from attendee - send our copy
     */

    /* Should be exactly one attendee. */
    final var si = ev.getSchedulingInfo();
    final var recipientResp = si.getOnlyParticipant();

    if (!recipientResp.isOk()) {
      return Response.fromResponse(pr, recipientResp);
    }

    final Participant att = recipientResp.getEntity();

    /* We can only do this if there is an active copy */

    final EventInfo calEi = sched.getStoredMeeting(ev);

    if (calEi == null) {
      return null;
    }

    calEi.getEvent().setScheduleMethod(ScheduleMethods.methodTypeRequest);

    /* Just send a copy to the attendee. */
    sched.schedule(calEi,
                   att.getCalendarAddress(), null, false, pr);

    if (!pr.isOk()) {
      return pr;
    }

    final Response resp = getSvc().getEventsHandler()
                                  .delete(ei, false);

    if (!resp.isOk()) {
      return Response.fromResponse(pr, resp);
    }

    return pr;
  }
}
