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
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.CalSvcI;
import org.bedework.icalendar.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import java.util.TreeSet;

/** Handles attendee updates - updates the organizers copy with the new partstat,
 * essentially a REPLY.
 *
 * @author Mike Douglass
 */
public class SchedAttendeeUpdate extends SchedProcessor {
  /**
   * @param svci
   */
  public SchedAttendeeUpdate(final CalSvcI svci) {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.inoutsched.processors.SchedProcessor#process(org.bedework.calfacade.svc.EventInfo)
   */
  @Override
  public SchedProcResult process(final EventInfo ei) throws CalFacadeException {
    SchedProcResult pr = new SchedProcResult();

    pr.sr = attendeeRespond(ei, Icalendar.methodTypeReply);
    return pr;
  }

  private ScheduleResult attendeeRespond(final EventInfo ei,
                                         final int method) throws CalFacadeException {
    ScheduleResult sr = new ScheduleResult();
    BwEvent ev = ei.getEvent();
    SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();
    String attUri = getSvc().getDirectories().principalToCaladdr(getSvc().getPrincipal());

    check: {
      /* Check that the current user is actually an attendee of the event.
       * Note we may have a suppressed master and/or multiple overrides
       */
      BwAttendee att = null;

      if (!ev.getSuppressed()) {
        att = ev.findAttendee(attUri);

        if (att == null) {
          sr.errorCode = CalFacadeException.schedulingNotAttendee;
          break check;
        }
      }

      if (ei.getNumOverrides() > 0) {
        for (EventInfo oei: ei.getOverrides()) {
          att = oei.getEvent().findAttendee(attUri);

          if (att == null) {
            sr.errorCode = CalFacadeException.schedulingNotAttendee;
            break check;
          }
        }
      }

      if (ev.getOriginator() == null) {
        sr.errorCode = CalFacadeException.schedulingNoOriginator;
        break check;
      }

      //EventInfo outEi = makeReplyEventInfo(ei, getUser().getPrincipalRef());
      EventInfo outEi = sched.copyEventInfo(ei, getPrincipal());
      BwEvent outEv = outEi.getEvent();

      if (!Util.isEmpty(outEv.getRecipients())) {
        outEv.getRecipients().clear();
      }

      if (!Util.isEmpty(outEv.getAttendees())) {
        outEv.getAttendees().clear();
      }

      // XXX we should get a comment from non db field in event
      //if (comment != null) {
      //  // Just add for the moment
      //  outEv.addComment(null, comment);
      //}

      outEv.addRecipient(outEv.getOrganizer().getOrganizerUri());
      outEv.setOriginator(att.getAttendeeUri());
      outEv.updateDtstamp();
      outEv.getOrganizer().setDtstamp(outEv.getDtstamp());

      String delegate = att.getDelegatedTo();
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
        outEv.setScheduleMethod(Icalendar.methodTypeReply);

        // Additional attendee
        BwAttendee delAtt = new BwAttendee();
        delAtt.setAttendeeUri(delegate);
        delAtt.setDelegatedFrom(att.getAttendeeUri());
        delAtt.setPartstat(IcalDefs.partstatValNeedsAction);
        delAtt.setRsvp(true);
        delAtt.setRole(att.getRole());
        outEv.addAttendee(delAtt);

        // ei is 'original "REQUEST"'. */
        EventInfo delegateEi = sched.copyEventInfo(ei, getPrincipal());
        BwEvent delegateEv = delegateEi.getEvent();

        delegateEv.addRecipient(delegate);
        delegateEv.addAttendee((BwAttendee)delAtt.clone()); // Not in RFC
        delegateEv.setScheduleMethod(Icalendar.methodTypeRequest);

        att.setPartstat(IcalDefs.partstatValDelegated);
        att.setRsvp(false);
        att.setDelegatedTo(delegate);

        // XXX Not sure if this is correct
        sched.schedule(delegateEi, Icalendar.methodTypeRequest,
                       null, null, false);
      } else if (method == Icalendar.methodTypeReply) {
        // Only attendee should be us

        setOnlyAttendee(outEi, ei, att.getAttendeeUri());

        outEv.setScheduleMethod(Icalendar.methodTypeReply);
      } else if (method == Icalendar.methodTypeCounter) {
        // Only attendee should be us

        setOnlyAttendee(outEi, ei, att.getAttendeeUri());

        /* Not sure how much we can change - at least times of the meeting.
         */
        outEv.setScheduleMethod(Icalendar.methodTypeCounter);
      } else {
        throw new RuntimeException("Never get here");
      }

      outEv.addRequestStatus(new BwRequestStatus(IcalDefs.requestStatusSuccess.getCode(),
                                                 IcalDefs.requestStatusSuccess.getDescription()));
      sr = sched.scheduleResponse(outEi);
      outEv.setScheduleState(BwEvent.scheduleStateProcessed);
      ev.getOrganizer().setScheduleStatus(IcalDefs.deliveryStatusDelivered);
    }

    return sr;
  }

  /** Set the attendee in the output event from the corresponding component in
   * the calendar event.
   *
   * @param outEi - destined for somebodies inbox
   * @param ei
   * @param attUri
   * @throws CalFacadeException
   */
  private void setOnlyAttendee(final EventInfo outEi,
                               final EventInfo ei,
                               final String attUri) throws CalFacadeException {
    if (!ei.getEvent().getSuppressed()) {
      BwEvent ev = ei.getEvent();
      BwEvent outEv = outEi.getEvent();

      if (!Util.isEmpty(outEv.getAttendees())) {
        outEv.getAttendees().clear();
      }
      BwAttendee att = ev.findAttendee(attUri);
      outEv.addAttendee((BwAttendee)att.clone());
    }

    if (ei.getNumOverrides() > 0) {
      for (EventInfo oei: ei.getOverrides()) {
        BwEvent ev = oei.getEvent();
        EventInfo oOutEi = outEi.findOverride(ev.getRecurrenceId());
        BwEvent outEv = oOutEi.getEvent();

        if (!Util.isEmpty(outEv.getAttendees())) {
          outEv.getAttendees().clear();
        }
        BwAttendee att = ev.findAttendee(attUri);
        outEv.addAttendee((BwAttendee)att.clone());
      }
    }
  }
}
