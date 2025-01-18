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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;
import org.bedework.base.response.Response;

/** Handles method CANCEL scheduling messages.
 *
 * @author Mike Douglass
 */
public class InCancel extends InProcessor {
  /**
   * @param svci the interface
   */
  public InCancel(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei the incoming event
   * @return ScheduleResult
   */
  @Override
  public ProcessResult process(final EventInfo ei) {
    /* We, as an attendee, received a CANCEL from the organizer.
     *
     */

    final ProcessResult pr = new ProcessResult();
    final BwEvent ev = ei.getEvent();
    final SchedulingI sched = getSvc().getScheduler();

    pr.removeInboxEntry = true;

    check: {
      if (ev.getOriginator() == null) {
        return Response.error(pr,
                              new BedeworkException(
                                      CalFacadeErrorCode.schedulingNoOriginator));
      }

      final BwPreferences prefs = getPrefs();
      final EventInfo colEi = sched.getStoredMeeting(ei.getEvent());

      if (colEi == null) {
        break check;
      }

      final BwEvent colEv = colEi.getEvent();

      if (prefs.getScheduleAutoCancelAction() ==
        BwPreferences.scheduleAutoCancelSetStatus) {
        if (colEv.getSuppressed()) {
          if (colEi.getOverrides() != null) {
            for (final EventInfo oei: colEi.getOverrides()) {
              final BwEvent oev = oei.getEvent();
              oev.setStatus(BwEvent.statusCancelled);
              oev.setSequence(ev.getSequence());
            }
          }
        } else {
          colEv.setStatus(BwEvent.statusCancelled);
          colEv.setSequence(ev.getSequence());
        }
        getSvc().getEventsHandler().update(colEi, true, null,
                                           false); // autocreate
      } else {
        final Response resp = getSvc().getEventsHandler()
                                      .delete(ei, false);

        if (!resp.isOk()) {
          pr.removeInboxEntry = false;
          return Response.fromResponse(pr, resp);
        }
      }

      pr.removeInboxEntry = false;
    }

    return pr;
  }
}
