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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.responses.Response;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calsvc.CalSvcDb;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex;

/** Abstract class to support processing of inbox scheduling messages.
 *
 * @author Mike Douglass
 */
public abstract class InProcessor extends CalSvcDb {
  /**
   * @param svci for this processor
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
   * @param ei the event
   * @return ProcessResult
   * @throws CalFacadeException on fatal error
   */
  public abstract ProcessResult process(final EventInfo ei) throws CalFacadeException;

  /** Update the inbox according to it's owners wishes (what if owner and proxy
   * have different wishes)
   *
   * @param ei - the pending inbox event
   * @param inboxOwnerHref href of
   * @param attendeeAccepting - is this the result of a REPLY with PARTSTAT accept?
   * @param forceDelete - it's inbox noise, delete it
   * @return status
   */
  public Response pendingToInbox(final EventInfo ei,
                                 final String inboxOwnerHref,
                                 final boolean attendeeAccepting,
                                 final boolean forceDelete) {
    var resp = new Response();
    boolean delete = forceDelete;

    if (!delete) {
      final BwPrincipal principal =
              getSvc().getUsersHandler().getPrincipal(inboxOwnerHref);

      if (principal == null) {
        delete = true;
      } else {
        final BwPreferences prefs = getSvc().getPrefsHandler().get(principal);

        final int sapr = prefs.getScheduleAutoProcessResponses();

        if (sapr == BwPreferences.scheduleAutoProcessResponsesNoNotify) {
          delete = true;
        } else if (sapr == BwPreferences.scheduleAutoProcessResponsesNoAcceptNotify) {
          delete = attendeeAccepting;
        }
      }
    }

    final BwEvent ev = ei.getEvent();

    final boolean vpoll = ev.getEntityType() ==
            IcalDefs.entityTypeVpoll;

    if (delete) {
      if (debug()) {
        debug("Delete event - don't move to inbox");
      }

      var delResp = deleteEvent(ei, false, false);
      if (!delResp.isError()) {
        return resp;
      }

      return Response.fromResponse(resp, delResp);
    }

    try {
      final BwCalendar inbox = getSvc().getCalendarsHandler().
              getSpecial(BwCalendar.calTypeInbox, false);
      if (inbox == null) {
        return resp;
      }

//      if (vpoll) {
      /* Delete other notifications for the same event
       * NOTE: DON'T for non-vpoll - this was deleting changes that had to
       * be processed. Still an opportunity to improve this though.
       */

      final var inevs = getEventsByUid(inbox.getPath(),
                                       ev.getUid());

      if (inevs.isError()) {
        return Response.fromResponse(resp, inevs);
      }

      for (final EventInfo inei : inevs.getEntities()) {
        final BwEvent inev = inei.getEvent();

        if (inev.getScheduleState() != BwEvent.scheduleStateProcessed) {
          continue;
        }

        /* Discard the earlier message */

        if (debug()) {
          debug("delete earlier? event from inbox: " + inev
                  .getName());
        }
        deleteEvent(inei, true, false);
      }
//      }

      if (debug()) {
        debug("set event to scheduleStateProcessed: " + ev.getName());
      }

      final ChangeTable chg = ei.getChangeset(inboxOwnerHref);

      chg.changed(PropertyIndex.PropertyInfoIndex.SCHEDULE_STATE,
                  ev.getScheduleState(),
                  BwEvent.scheduleStateProcessed);
      ev.setScheduleState(BwEvent.scheduleStateProcessed);
      chg.changed(PropertyIndex.PropertyInfoIndex.COLPATH,
                  ev.getColPath(), inbox.getPath());
      ev.setColPath(inbox.getPath());
      getSvc().getEventsHandler().update(ei, true, null);
    } catch (final CalFacadeException cfe) {
      return Response.error(resp, cfe);
    }

    return resp;
  }
}
