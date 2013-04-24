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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;

/** Handles method CANCEL scheduling messages.
 *
 * @author Mike Douglass
 */
public class InCancel extends InProcessor {
  /**
   * @param svci
   */
  public InCancel(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei
   * @return ScheduleResult
   * @throws CalFacadeException
   */
  @Override
  public ProcessResult process(final EventInfo ei) throws CalFacadeException {
    /* We, as an attendee, received a CANCEL from the organizer.
     *
     */

    ProcessResult pr = new ProcessResult();
    BwEvent ev = ei.getEvent();
    SchedulingI sched = getSvc().getScheduler();

    pr.removeInboxEntry = true;

    check: {
      if (ev.getOriginator() == null) {
        pr.sr.errorCode = CalFacadeException.schedulingNoOriginator;
        break check;
      }

      BwPreferences prefs = getSvc().getPrefsHandler().get();
      EventInfo colEi = sched.getStoredMeeting(ei.getEvent());

      if (colEi == null) {
        break check;
      }

      BwEvent colEv = colEi.getEvent();

      if (prefs.getScheduleAutoCancelAction() ==
        BwPreferences.scheduleAutoCancelSetStatus) {
        if (colEv.getSuppressed()) {
          if (colEi.getOverrides() != null) {
            for (EventInfo oei: colEi.getOverrides()) {
              oei.getEvent().setStatus(BwEvent.statusCancelled);
            }
          }
        } else {
          colEv.setStatus(BwEvent.statusCancelled);
        }
        getSvc().getEventsHandler().update(colEi, true, null);
      } else {
        getSvc().getEventsHandler().delete(colEi, false);
      }

      pr.removeInboxEntry = false;
    }

    return pr;
  }
}
