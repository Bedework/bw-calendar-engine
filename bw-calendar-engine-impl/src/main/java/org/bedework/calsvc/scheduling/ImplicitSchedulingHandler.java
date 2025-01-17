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

import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.Participant;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvci.EventsI;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;

import static java.lang.String.format;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece implements implicit scheduling methods
 *
 * @author douglm
 *
 */
public abstract class ImplicitSchedulingHandler extends AttendeeSchedulingHandler {
  ImplicitSchedulingHandler(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ScheduleResult implicitSchedule(final EventInfo ei,
                                         final boolean noInvites) {
    UpdateResult uer = ei.getUpdResult();
    uer.wasScheduled = true;

    final BwEvent ev = ei.getEvent();

    boolean organizerSchedulingObject = ev.getOrganizerSchedulingObject();
    boolean attendeeSchedulingObject = ev.getAttendeeSchedulingObject();
    var orgCalAddr = ev.getSchedulingInfo()
                       .getSchedulingOwner().getCalendarAddress();

    /* We may have an event with a suppressed master (attendee invited
       to one or more instances), or we may have an organizer sending
       such an event - real master with no attendees but an override
       with attendees.
     */

    if (!Util.isEmpty(ei.getOverrides())) {
      for (final EventInfo oei: ei.getOverrides()) {
        uer = oei.getUpdResult();
        final BwEvent oev = oei.getEvent();

        if (orgCalAddr == null) {
          orgCalAddr = oev.getSchedulingInfo()
                          .getSchedulingOwner().getCalendarAddress();
        }

        if (oev.getOrganizerSchedulingObject()) {
          organizerSchedulingObject = true;
        }

        if (oev.getAttendeeSchedulingObject()) {
          attendeeSchedulingObject = true;
        }
      }
    }

    if (!organizerSchedulingObject &&
        !attendeeSchedulingObject) {
      // Not a scheduling event
      if (debug()) {
        debug(format("Not a scheduling object: uid=\"%s\", just return",
                     ev.getUid()));
      }
      return Response.ok(uer);
    }

    if (orgCalAddr == null) {
      return Response.error(uer,
                            new CalFacadeBadRequest(
                                    CalFacadeErrorCode.missingEventProperty));
    }

    // Ensure we have an originator for ischedule

    if (ev.getOriginator() == null) {
      ev.setOriginator(orgCalAddr);
    }

    if (uer.reply) {
      final int meth;

      if (ei.getReplyAttendeeURI() != null) {
        meth = ScheduleMethods.methodTypeRefresh;
      } else {
        meth = ScheduleMethods.methodTypeReply;
      }

      ev.setScheduleMethod(meth);
      attendeeRespond(ei, meth, uer);
      return uer;
    }

    if (uer.deleting) {
      if (organizerSchedulingObject) {
        //if (schedMethod == Icalendar.methodTypeCancel) {
        //  /* We already canceled this one */
        //  return;
        //}

        ev.setScheduleMethod(ScheduleMethods.methodTypeCancel);
      } else {
        // Reply from attendee setting partstat
        ev.setScheduleMethod(ScheduleMethods.methodTypeReply);
      }
    } else {
      ev.setScheduleMethod(ScheduleMethods.methodTypeRequest);
    }

    if (!noInvites) {
      schedule(ei,
               ei.getReplyAttendeeURI(),
               uer.fromAttUri,
               false, uer);
      if (!uer.isOk()) {
        return uer;
      }
    }

    if (!uer.adding && !Util.isEmpty(uer.deletedAttendees)) {
      /* Send cancel to removed attendees */
      for (final BwAttendee att: uer.deletedAttendees) {
        if (Util.compareStrings(att.getPartstat(),
                                IcalDefs.partstats[IcalDefs.partstatDeclined]) == 0) {
          // Already declined - send nothing
          continue;
        }

        /* Clone is adequate here. For a CANCEL we just send either the master
         * or the particular instance.
         */
        final BwEvent cncl = (BwEvent)ev.clone();

        cncl.setAttendees(null);
        cncl.addAttendee((BwAttendee)att.clone());

        cncl.setRecipients(null);
        cncl.addRecipient(att.getAttendeeUri());

        cncl.setScheduleMethod(ScheduleMethods.methodTypeCancel);
        cncl.setOrganizerSchedulingObject(true);
        cncl.setAttendeeSchedulingObject(false);

        final EventInfo cei = new EventInfo(cncl);

        final ScheduleResult cnclr = schedule(cei,
                                              null, null, false, null);
        if (debug()) {
          trace(cnclr.toString());
        }
      }
    }

    if (ei.getInboxEventName() != null) {
      // Delete the given event from the inbox.
      final EventsI events = getSvc().getEventsHandler();

      final BwCalendar inbox = getSvc().getCalendarsHandler()
                                       .getSpecial(BwCalendar.calTypeInbox, true);
      final EventInfo inboxei = events.get(inbox.getPath(),
                                           ei.getInboxEventName());

      if (inboxei != null) {
        final Response resp = events.delete(inboxei, false);

        if (!resp.isOk()) {
          return Response.fromResponse(uer, resp);
        }
      }
    }

    return uer;
  }

  @Override
  public ScheduleResult sendReply(final EventInfo ei,
                                  final int partstat,
                                  final String comment) {
    final ScheduleResult sr = new ScheduleResult();
    final BwEvent ev = ei.getEvent();

    if (!ev.getAttendeeSchedulingObject()) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeErrorCode.schedulingBadMethod));
    }

    final Participant att = findUserAttendee(ei);

    if (att == null) {
      return Response.error(sr, new CalFacadeException(
              CalFacadeErrorCode.schedulingNotAttendee));
    }

    final BwEvent outEv = new BwEventObj();
    final EventInfo outEi = new EventInfo(outEv);
    final var outSi = outEv.getSchedulingInfo();

    // Attendees first
    final var outAtt = outSi.copyParticipant(att);
    outAtt.setParticipationStatus(IcalDefs.partstats[partstat]);
    outAtt.setExpectReply(partstat == IcalDefs.partstatNeedsAction);
    outEv.setOriginator(outAtt.getCalendarAddress());

    outEv.setScheduleMethod(ScheduleMethods.methodTypeReply);
    outEv.addRequestStatus(new BwRequestStatus(
            IcalDefs.requestStatusSuccess.getCode(),
            IcalDefs.requestStatusSuccess.getDescription()));

    final var evSowner = ev.getSchedulingInfo().getSchedulingOwner();
    outEv.addRecipient(evSowner.getCalendarAddress());
    outEv.updateDtstamp();
    final var outSowner = outSi.copySchedulingOwner(evSowner);
    outSowner.setSchedulingDtStamp(outEv.getDtstamp());
    outEv.setUid(ev.getUid());
    outEv.setRecurrenceId(ev.getRecurrenceId());

    outEv.setDtstart(ev.getDtstart());
    outEv.setDtend(ev.getDtend());
    outEv.setDuration(ev.getDuration());
    outEv.setNoStart(ev.getNoStart());

    outEv.setSummary(ev.getSummary());

    outEv.setRecurring(false);

    if (comment != null) {
      outEv.addComment(new BwString(null, comment));
    }

    scheduleResponse(outEi, sr);
    outEv.setScheduleState(BwEvent.scheduleStateProcessed);

    return sr;
  }
}
