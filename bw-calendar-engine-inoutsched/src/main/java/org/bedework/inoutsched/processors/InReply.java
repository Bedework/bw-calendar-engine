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
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.Participant;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.base.response.Response;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.timezones.TimezonesException;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ParticipantType;

import java.text.ParseException;
import java.util.Map;

/** Handles incoming method REPLY scheduling messages.
 *
 * @author Mike Douglass
 */
public class InReply extends InProcessor {
  private static final String acceptPartstat =
          IcalDefs.partstats[IcalDefs.partstatAccepted];

  /**
   * @param svci the interface
   */
  public InReply(final CalSvcI svci) {
    super(svci);
  }

  @Override
  public ProcessResult process(final EventInfo ei) {
    /* Process a response we as the organizer, or their proxy, received from
     * an attendee
     */

    final ProcessResult pr = new ProcessResult();
    final SchedulingI sched = getSvc().getScheduler();
    BwEvent ev = ei.getEvent();

    final EventInfo colEi = sched.getStoredMeeting(ev);

    /* The event should have a calendar set to the inbox it came from.
     * That inbox may be owned by somebody other than the current user if a
     * calendar user has delegated control of their inbox to some other user
     * e.g. secretary.
     */

    pr.attendeeAccepting = true;

    check: {
      if (colEi == null) {
        // No corresponding stored meeting
        break check;
      }

      if (ev.getOriginator() == null) {
        return Response.error(pr, new BedeworkException(
                CalFacadeErrorCode.schedulingNoOriginator));
      }

      String attUri = null;

      /* Should be exactly one attendee */
      if (!ev.getSuppressed()) {
        final var si = ev.getSchedulingInfo();
        final var recipientResp = si.getOnlyParticipant();

        if (!recipientResp.isOk()) {
          return Response.fromResponse(pr, recipientResp);
        }

        final Participant att = recipientResp.getEntity();

        if (!att.getParticipationStatus().equals(acceptPartstat)) {
          pr.attendeeAccepting = false;
        }

        attUri = att.getCalendarAddress();
      }

      if (ei.getNumOverrides() > 0) {
        for (final EventInfo oei: ei.getOverrides()) {
          ev = oei.getEvent();

          final var si = ev.getSchedulingInfo();
          final var recipients = si.getRecipientParticipants();

          if (recipients.size() != 1) {
            return Response.error(pr, new BedeworkException(
                    CalFacadeErrorCode.schedulingExpectOneAttendee));
          }

          final Participant att = recipients.values()
                                            .iterator().next();

          if (!att.getParticipationStatus().equals(acceptPartstat)) {
            pr.attendeeAccepting = false;
          }

          if (attUri == null) {
            attUri = att.getCalendarAddress();
          } else if (!attUri.equals(att.getCalendarAddress())) {
            return Response.error(pr, new BedeworkException(
                    CalFacadeErrorCode.schedulingExpectOneAttendee));
          }
        }
      }

      if (attUri == null) {
        return Response.error(pr, new BedeworkException(
                CalFacadeErrorCode.schedulingExpectOneAttendee));
      }

      /*TODO If the sequence of the incoming event is lower than the sequence on the
       * calendar event we ignore it.
       */

      final boolean vpoll = colEi.getEvent().getEntityType() ==
              IcalDefs.entityTypeVpoll;
      if (vpoll) {
        if (!updateOrganizerPollCopy(colEi, ei, attUri, pr)) {
          break check;
        }
      } else if (!updateOrganizerCopy(colEi, ei, attUri, pr)) {
        break check;
      }

      pr.removeInboxEntry = false;
    }

    return pr;
  }

  /* =============================================================
                      Private methods
     ============================================================= */

  private boolean updateOrganizerPollCopy(final EventInfo colEi,
                                          final EventInfo inBoxEi,
                                          final String attUri,
                                          @SuppressWarnings("UnusedParameters") final ScheduleResult sr) {
    /* We have a single voter and their responses to each item.
       Update the Participant component for that respondee.
     */

    final BwEvent colEv = colEi.getEvent();
    final var colSi = colEv.getSchedulingInfo();
    final Map<String, Participant> voters =
            colSi.getParticipantsWithRoles(ParticipantType.VALUE_VOTER);

    final BwEvent inEv = inBoxEi.getEvent();

    final Map<String, Participant> invoters =
            inEv.getSchedulingInfo()
                .getParticipantsWithRoles(ParticipantType.VALUE_VOTER);

    /* Should only be one Participant for this attendee */

    if (invoters.size() != 1) {
      return true; // Ignore it.
    }

    final Participant inVoter = invoters.get(attUri);

    if (inVoter == null) {
      return true; // Ignore it.
    }

    final var colVoter = voters.get(attUri);
    if (colVoter == null) {
      // This is party crashing _ ignore?
      //colSi.addParticipant(inVoter);
      return true;
    }

    inVoter.copyTo(colVoter);
    colSi.markChanged();
    colEv.getChangeset(getSvc().getPrincipal()
                               .getHref())
         .changed(PropertyIndex.PropertyInfoIndex.PARTICIPANT,
                  colVoter, null);

    getSvc().getEventsHandler().update(colEi, false, attUri,
                                       false); // autocreate

    return true;
  }

  private boolean updateOrganizerCopy(final EventInfo colEi,
                                      final EventInfo inBoxEi,
                                      final String attUri,
                                      final ScheduleResult sr) {
    final BwEvent inBoxEv = inBoxEi.getEvent();
    final BwEvent calEv = colEi.getEvent();
    final ChangeTable chg = calEv.getChangeset(getPrincipalHref());

    /* Only set true if the inbox copy needs to stay as notification.
     * Do not set true for status updates
     */
    boolean changed = false;

    if (debug()) {
      debug("Update for attendee " + attUri);
    }

    if (inBoxEv.getScheduleMethod() != ScheduleMethods.methodTypeReply) {
      Response.error(sr, new BedeworkException(
              CalFacadeErrorCode.schedulingExpectOneAttendee));
      return false;
    }

    /* If the incoming sequence is less than that in the organizers event
     * then ignore the incoming reply?
     */
    /* Update the participation status from the incoming attendee */

    Participant calPart;

    final ChangeTableEntry cte = chg.getEntry(
            PropertyIndex.PropertyInfoIndex.ATTENDEE);
    final var calSi = calEv.getSchedulingInfo();
    final var inSi = inBoxEv.getSchedulingInfo();

    if (!inBoxEv.getSuppressed()) {
      calPart = calSi.findParticipant(attUri);
      if (calPart == null) {
        if (debug()) {
          debug("Not an attendee of " + calEv);
        }
        Response.error(sr, new BedeworkException(
                CalFacadeErrorCode.schedulingUnknownAttendee,
                attUri));
        return false;
      }

      // For a recurring instance we replace or we update all recurring instances.
      final boolean recurringInstance = (calEv instanceof BwEventProxy);
      final Participant inPart = inSi.findParticipant(attUri);

      if (calPart.changedBy(inPart)) {
        changed = true;

        if (recurringInstance) {
          calSi.removeRecipientParticipant(inPart);
        }
        calPart = calSi.copyParticipant(inPart);
      }

      calPart.setScheduleStatus(getRstat(inBoxEv));

      if (recurringInstance) {
        calSi.addParticipant(calPart);
      }

      // XXX Ensure no name change
      if (calEv instanceof final BwEventProxy pr) {
        final BwEventAnnotation ann = pr.getRef();
        ann.setName(null);
      }
    }

    /* The above changed the master - now we need to update or add any overrides
     */
    if (calEv.getRecurring() && (inBoxEi.getOverrides() != null)) {
      for (final EventInfo oei: inBoxEi.getOverrides()) {
        final BwEvent oev = oei.getEvent();
        final EventInfo cei = colEi.findOverride(oev.getRecurrenceId() /*, false */);
        final var oSi = oev.getSchedulingInfo();

        /*
        if (cei == null) {
          // Organizer must have deleted the override.
          if (debug()) {
            debug("Skipping missing override " + oev.getRecurrenceId());
          }
          continue;
        }*/

        final BwEvent ocalEv = cei.getEvent();
        final var ocalSi = ocalEv.getSchedulingInfo();

        if (((BwEventProxy)ocalEv).getRef().unsaved()) {
          // New Override
          try {
            final String rid = oev.getRecurrenceId();
            Date dt = new DateTime(rid);

            if (calEv.getDtstart().getDateType()) {
              // RECUR - fix all day recurrences sometime
              if (rid.length() > 8) {
                // Try to fix up bad all day recurrence ids. - assume a local timezone
                ((DateTime)dt).setTimeZone(null);
                dt = new Date(dt.toString().substring(0, 8));
              }
            }

            final DtStart st = new DtStart(dt);
            final String tzid = calEv.getDtstart().getTzid();
            if (tzid != null) {
              final TimeZone tz = Timezones.getTz(tzid);
              st.setTimeZone(tz);
            }
            ocalEv.setDtstart(BwDateTime.makeBwDateTime(st));
            ocalEv.setDuration(calEv.getDuration());
            ocalEv.setDtend(ocalEv.getDtstart().addDur(calEv.getDuration()));
          } catch (final ParseException | TimezonesException e) {
            throw new RuntimeException(e);
          }
        }

        final var ovpart = oSi.findParticipant(attUri);
        calPart = ocalSi.findParticipant(attUri);

        if (calPart == null) {
          // Organizer must have removed the attendee.
          if (debug()) {
            debug("Skipping override " + attUri +
                          " is not attending");
          }
          continue;
        }

        if (calPart.changedBy(ovpart)) {
          changed = true;

          ocalSi.removeRecipientParticipant(ovpart);

          calPart = ocalSi.addParticipant(calPart);
          calPart.setScheduleStatus(getRstat(oev));
        }
      }
    }

    final boolean noinvites = !changed;

    colEi.setReplyUpdate(true);

    /* Update the organizer copy. This will broadcast the changes tp all
     * attendees
     */
    getSvc().getEventsHandler().update(colEi, noinvites, attUri,
                                       false); // autocreate

    return changed;
  }

  private String getRstat(final BwEvent ev) {
    StringBuilder rstat = null;

    for (final BwRequestStatus bwrstat: ev.getRequestStatuses()) {
      if (rstat != null) {
        rstat.append(",");
        rstat.append(bwrstat.getCode());
      } else {
        rstat = new StringBuilder(bwrstat.getCode());
      }
    }

    if (rstat == null) {
      rstat = new StringBuilder(IcalDefs.deliveryStatusSuccess);
    }

    return rstat.toString();
  }
}
