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
package org.bedework.calsvc.scheduling;

import org.bedework.access.PrivilegeDefs;
import org.bedework.calfacade.Attendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwParticipant;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.SchedulingInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.convert.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;

import java.util.Map;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece handles the attendee to organizer methods from the attendee end.
 *
 * @author Mike Douglass
 *
 */
public abstract class AttendeeSchedulingHandler extends OrganizerSchedulingHandler {
  AttendeeSchedulingHandler(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ScheduleResult requestRefresh(final EventInfo ei,
                                       final String comment) {
    final ScheduleResult sr = new ScheduleResult();
    final BwEvent ev = ei.getEvent();

    if (ev.getScheduleMethod() != ScheduleMethods.methodTypeRequest) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeException.schedulingBadMethod));
    }

    final Attendee att = findUserAttendee(ei);

    if (att == null) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeException.schedulingNotAttendee));
    }

    final BwEvent outEv = new BwEventObj();
    final EventInfo outEi = new EventInfo(outEv);

    outEv.setScheduleMethod(ScheduleMethods.methodTypeRefresh);

    outEv.addRecipient(ev.getOrganizer().getOrganizerUri());
    outEv.setOriginator(att.getCalendarAddress());
    outEv.updateDtstamp();
    outEv.setOrganizer((BwOrganizer)ev.getOrganizer().clone());
    outEv.getOrganizer().setDtstamp(outEv.getDtstamp());
    outEv.getParticipants().copyAttendee(att);
    outEv.setUid(ev.getUid());
    outEv.setRecurrenceId(ev.getRecurrenceId());

    outEv.setDtstart(ev.getDtstart());
    outEv.setDtend(ev.getDtend());
    outEv.setDuration(ev.getDuration());
    outEv.setNoStart(ev.getNoStart());

    outEv.setRecurring(false);

    if (comment != null) {
      outEv.addComment(new BwString(null, comment));
    }

    scheduleResponse(outEi, sr);
    if (sr.isOk()) {
      outEv.setScheduleState(BwEvent.scheduleStateProcessed);
    }

    return sr;
  }

  @Override
  public ScheduleResult attendeeRespond(final EventInfo ei,
                                        final int method,
                                        final ScheduleResult res) {
    final ScheduleResult sr;

    if (res == null) {
      sr = new ScheduleResult();
    } else {
      sr = res;
    }

    final BwEvent ev = ei.getEvent();
    final SchedulingInfo si = ei.getSchedulingInfo();
    final var outParticipants = ev.getParticipants();

    /* Check that the current user is actually the only attendee of the event.
       * Note we may have a suppressed master and/or multiple overrides
       */
    final Attendee att = findUserAttendee(ei);

    if (att == null) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeException.schedulingNotAttendee));
    }

    if (ev.getOriginator() == null) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeException.schedulingNoOriginator));
    }

    final EventInfo outEi = copyEventInfo(ei, getPrincipal());
    final BwEvent outEv = outEi.getEvent();

    if (!Util.isEmpty(outEv.getRecipients())) {
      outEv.getRecipients().clear();
    }

    final var outParts = outEv.getParticipants();
    outParts.clearAttendees();

    // XXX we should get a comment from non db field in event
    //if (comment != null) {
    //  // Just add for the moment
    //  outEv.addComment(null, comment);
    //}

    if (si.getOrganizer() == null) {
      throw new CalFacadeException("No organizer");
    }

    outEv.addRecipient(si.getOrganizer().getOrganizerUri());
    outEv.setOriginator(att.getCalendarAddress());
    outEv.updateDtstamp();
    si.getOrganizer().setDtstamp(outEv.getDtstamp());

    final String delegate = att.getDelegatedTo();

    if (delegate != null) {
      /* RFC 2446 4.2.5 - Delegating an event
         *
         * When delegating an event request to another "Calendar User", the
         * "Delegator" must both update the "Organizer" with a "REPLY" and send
         * a request to the "Delegate". There is currently no protocol
         * limitation to delegation depth. It is possible for the original
         * delegate to delegate the meeting to someone else, and so on. When a
         * request is delegated from one CUA to another there are a number of
         * responsibilities required of the "Delegator". The "Delegator" MUST:
         *
         *   .  Send a "REPLY" to the "Organizer" with the following updates:
         *   .  The "Delegator's" "ATTENDEE" property "partstat" parameter set
         *      to "delegated" and the "delegated-to" parameter is set to the
         *      address of the "Delegate"
         *   .  Add an additional "ATTENDEE" property for the "Delegate" with
         *      the "delegated-from" property parameter set to the "Delegator"
         *   .  Indicate whether they want to continue to receive updates when
         *      the "Organizer" sends out updated versions of the event.
         *      Setting the "rsvp" property parameter to "TRUE" will cause the
         *      updates to be sent, setting it to "FALSE" causes no further
         *      updates to be sent. Note that in either case, if the "Delegate"
         *      declines the invitation the "Delegator" will be notified.
         *   .  The "Delegator" MUST also send a copy of the original "REQUEST"
         *      method to the "Delegate".
         */

      // outEv is the reply
      outEv.setScheduleMethod(ScheduleMethods.methodTypeReply);

      // Additional attendee
      final Attendee delAtt = outParticipants.makeAttendee();
      delAtt.setCalendarAddress(delegate);
      delAtt.setDelegatedFrom(att.getCalendarAddress());
      delAtt.setParticipationStatus(
              IcalDefs.partstatValNeedsAction);
      delAtt.setExpectReply(true);
      delAtt.setParticipationStatus(att.getParticipationStatus());

      // ei is 'original "REQUEST"'. */
      final EventInfo delegateEi = copyEventInfo(ei,
                                                 getPrincipal());
      final BwEvent delegateEv = delegateEi.getEvent();

      delegateEv.addRecipient(delegate);

      delegateEv.getParticipants()
                .copyAttendee(delAtt); // Not in RFC
      delegateEv.setScheduleMethod(
              ScheduleMethods.methodTypeRequest);

      att.setParticipationStatus(IcalDefs.partstatValDelegated);
      att.setExpectReply(false);
      att.setDelegatedTo(delegate);

      // XXX Not sure if this is correct
      if (!schedule(delegateEi, null, null, false, sr).isOk()) {
        return sr;
      }
    } else if (method == ScheduleMethods.methodTypeReply) {
      // Only attendee should be us

      outEv.getParticipants().setOnlyAttendee(att);

      if (ev.getEntityType() == IcalDefs.entityTypeVpoll) {
        setPollResponse(outEi, ei, att.getCalendarAddress());
      }

      outEv.setScheduleMethod(ScheduleMethods.methodTypeReply);
    } else if (method == ScheduleMethods.methodTypeCounter) {
      // Only attendee should be us

      outEv.getParticipants().setOnlyAttendee(att);

      /* Not sure how much we can change - at least times of the meeting.
         */
      outEv.setScheduleMethod(ScheduleMethods.methodTypeCounter);
    } else {
      throw new CalFacadeException("Never get here");
    }

    outEv.addRequestStatus(new BwRequestStatus(
            IcalDefs.requestStatusSuccess.getCode(),
            IcalDefs.requestStatusSuccess.getDescription()));
    scheduleResponse(outEi, sr);

    if (sr.isOk()) {
      outEv.setScheduleState(BwEvent.scheduleStateProcessed);
      si.getOrganizer()
        .setScheduleStatus(IcalDefs.deliveryStatusDelivered);
    }

    return sr;
  }

  /*
  public ScheduleResult processCancel(final EventInfo ei) throws CalFacadeException {
    /* We, as an attendee, received a CANCEL from the organizer.
     *
     * /

    ScheduleResult sr = new ScheduleResult();
    BwEvent ev = ei.getEvent();
    BwCalendar inbox = getSvc().getCalendarsHandler().get(ev.getColPath());

    boolean forceDelete = true;

    check: {
      if (inbox.getCalType() != BwCalendar.calTypeInbox) {
        sr.errorCode = CalFacadeException.schedulingBadSourceCalendar;
        break check;
      }

      if (ev.getOriginator() == null) {
        sr.errorCode = CalFacadeException.schedulingNoOriginator;
        break check;
      }

      BwPreferences prefs = getSvc().getPrefsHandler().get();
      EventInfo colEi = getStoredMeeting(ei.getEvent());

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

      forceDelete = false;
    }

    updateInbox(ei, inbox.getOwnerHref(),
                false,           // attendeeAccepting
                forceDelete);  // forceDelete

    return sr;
  }
  */

  @Override
  public ScheduleResult scheduleResponse(final EventInfo ei,
                                         final ScheduleResult res) {
    /* As an attendee, respond to a scheduling request.
     *
     *    Copy event
     *    remove all attendees and readd this user
     *    Add to organizers inbox if internal
     *    Put in outbox if external.
     */
    final ScheduleResult sr = new ScheduleResult();

    try {
      final int smethod = ei.getEvent().getScheduleMethod();

      if (!Icalendar.itipReplyMethodType(smethod)) {
        return Response.error(sr, new CalFacadeException(
                CalFacadeException.schedulingBadMethod));
      }

      /* For each recipient within this system add the event to their inbox.
       *
       * If there are any external users add it to the outbox and it will be
       * mailed to the recipients.
       */

      final int outAccess = PrivilegeDefs.privScheduleReply;

      /* There should only be one attendee for a reply */
      if (ei.getSchedulingInfo().getMaxAttendees() > 1) {
        return Response.error(sr, new CalFacadeException(
                CalFacadeException.schedulingBadAttendees));
      }

      if (!initScheduleEvent(ei, true, false)) {
        return sr;
      }

      /* Do this here to check we have access. We might need the outbox later
       */
      final BwCalendar outBox =
              getSpecialCalendar(getPrincipal(),
                                 BwCalendar.calTypeOutbox,
                                 true, outAccess);

      sendSchedule(sr, ei, null, null, false);

      if (sr.ignored) {
        return sr;
      }

      if (!sr.externalRcs.isEmpty()) {
        final var addResp = addToOutBox(ei, outBox, sr.externalRcs);

        if (!addResp.isOk()) {
          return Response.fromResponse(sr, addResp);
        }
      }

      return sr;
    } catch (final Throwable t) {
      getSvc().rollbackTransaction();
      return Response.error(sr, t);
    }
  }

  /** Set the poll response for the given voter in the output event
   * from the voting state in the incoming event. The output event
   * should only have one PARTICIPANT sub-component for this voter with
   * its state set by the current voters state.
   *
   * @param outEi - destined for somebodies inbox
   * @param ei - the voters copy
   * @param attUri - uri of the voter
   */
  private void setPollResponse(final EventInfo outEi,
                               final EventInfo ei,
                               final String attUri) {
    /* This requires us to parse out the PARTICIPANT components - find our voter
       and add a poll item id property in the output.

       Note that this is implementing poll mode basic.
     */

    final BwEvent ev = ei.getEvent();
    final BwEvent outEv = outEi.getEvent();

    try {
      final var parts = ev.getParticipants();
      final Map<String, BwParticipant> voters =
              parts.getVoters();

      final var v = voters.get(attUri);
      if (v == null) {
        warn("No participant element for " + attUri);
        return;
      }

      outEv.getParticipants().addParticipant(v);
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
