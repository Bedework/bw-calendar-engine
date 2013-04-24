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
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvcDb;
import org.bedework.calsvci.CalSvcI;

/** Abstract class to support processing of inbox scheduling messages.
 *
 * @author Mike Douglass
 */
public abstract class InProcessor extends CalSvcDb {
  /**
   * @param svci
   */
  public InProcessor(final CalSvcI svci) {
    super(null);
    setSvc(svci);
  }

  /** Result from processing */
  public static class ProcessResult {
    /** Result of the scheduling operations */
    public ScheduleResult sr = new ScheduleResult();

    /** processors set this true when appropriate */
    public boolean noInboxChange;

    /** Errors imply removal of associated inbox entry if any */
    public boolean removeInboxEntry;

    /** Update was just attendee accepting */
    public boolean attendeeAccepting;
  }

  /**
   * @param ei
   * @return ProcessResult
   * @throws CalFacadeException
   */
  public abstract ProcessResult process(final EventInfo ei) throws CalFacadeException;

  /** Update the inbox according to it's owners wishes (what if owner and proxy
   * have different wishes)
   *
   * @param ei - the inbox event
   * @param inboxOwnerHref
   * @param attendeeAccepting - is this the result of a REPLY with PARTSTAT accept?
   * @param forceDelete - it's inbox noise, delete it
   * @throws CalFacadeException
   */
  public void updateInbox(final EventInfo ei,
                          final String inboxOwnerHref,
                          final boolean attendeeAccepting,
                          final boolean forceDelete) throws CalFacadeException {
    boolean delete = forceDelete;

    if (!delete) {
      BwPrincipal principal = getSvc().getUsersHandler().getPrincipal(inboxOwnerHref);

      if (principal == null) {
        delete = true;
      } else {
        BwPreferences prefs = getSvc().getPrefsHandler().get(principal);

        int sapr = prefs.getScheduleAutoProcessResponses();

        if (sapr == BwPreferences.scheduleAutoProcessResponsesNoNotify) {
          delete = true;
        } else if (sapr == BwPreferences.scheduleAutoProcessResponsesNoAcceptNotify) {
          delete = attendeeAccepting;
        }
      }
    }

    BwEvent ev = ei.getEvent();

    if (delete) {
      getSvc().getEventsHandler().delete(ei, false);
    } else {
      /* Delete other notifications for the same event
       * NOTE: DON'T - this was deleting changes that had to
       * be processed. Still an opportunity to improve this though.
       * /

       RecurringRetrievalMode rrm =
         new RecurringRetrievalMode(Rmode.overrides);

      Collection<EventInfo> inevs = getEvents(ev.getColPath(),
                                              ev.getUid(),
                                              ev.getRecurrenceId(),
                                              true,
                                              rrm);

      for (EventInfo inei: inevs) {
        BwEvent inev = inei.getEvent();

        if (inev.getScheduleState() != BwEvent.scheduleStateProcessed) {
          continue;
        }

        /* Discard the earlier message * /

        if (debug) {
          trace("delete earlier? event from inbox: " + inev.getName());
        }
        deleteEvent(inei, true, false);
      }
      */

      if (debug) {
        trace("set event to scheduleStateProcessed: " + ev.getName());
      }
      ev.setScheduleState(BwEvent.scheduleStateProcessed);
      getSvc().getEventsHandler().update(ei, true, null);
    }
  }
}
