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
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.caldav.server.sysinterface.Host;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.ScheduleRecipientResult;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvc.scheduling.hosts.BwHosts;
import org.bedework.convert.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleStates;
import org.bedework.util.misc.Uid;
import org.bedework.util.misc.Util;
import org.bedework.base.response.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static java.lang.String.format;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece handles outbound schduling methods - those going to an inbox
 *
 * @author douglm
 *
 */
public abstract class OutboundSchedulingHandler extends IScheduleHandler {
  OutboundSchedulingHandler(final CalSvc svci) {
    super(svci);
  }

  /* Send the meeting request. If recipient is non-null send only to that recipient
   * (used for REFRESH handling), otherwise send to recipients in event.
   */
  protected void sendSchedule(final ScheduleResult<?> sr,
                              final EventInfo ei,
                              final String recipient,
                              final String fromAttUri,
                              final boolean fromOrganizer) {
    /* Recipients external to the system. */
    final BwEvent ev = ei.getEvent();
    final boolean freeBusyRequest = ev.getEntityType() ==
      IcalDefs.entityTypeFreeAndBusy;

    ev.updateDtstamp();

    if (recipient != null) {
      getRecipientInbox(ei, recipient, fromAttUri, sr, freeBusyRequest);
    } else if (ev.getRecipients() == null) {
      if (debug()) {
        debug("No recipients for event");
      }
      return;
    } else {
      for (final String recip: ev.getRecipients()) {
        getRecipientInbox(ei, recip, fromAttUri, sr, freeBusyRequest);
      }
    }

    /* As we go through the inbox info, we gather together those for the same
     * host but external to this system.
     *
     * We then send off one request to each external host.
     */
    final Map<String, Collection<UserInbox>> hostMap = new HashMap<>();

    for (final ScheduleRecipientResult sres: sr.recipientResults.values()) {
      final UserInbox ui = (UserInbox)sres;

      if (sr.ignored) {
        ui.setStatus(ScheduleStates.scheduleIgnored);
        continue;
      }

      if (ui.getStatus() == ScheduleStates.scheduleUnprocessed) {
        if (ui.getHost() != null) {
          /* Needs to be sent to an external destination. Add it
           * to the list of inboxes for that host.
           */
          final Collection<UserInbox> inboxes = hostMap
                  .computeIfAbsent(ui.getHost().getHostname(),
                                   k -> new ArrayList<>());

          inboxes.add(ui);

          continue;
        }

        /* Going to an internal destination */

        String deliveryStatus = null;

        try {
          if (freeBusyRequest) {
            sres.freeBusy = getFreeBusy(null, ui.principal,
                                        ev.getDtstart(), ev.getDtend(),
                                        ev.getSchedulingInfo()
                                          .getSchedulingOwner()
                                          .makeOrganizer(),
                                        ev.getUid(),
                                        null);

            ui.setStatus(ScheduleStates.scheduleOk);
          } else if (!ui.principal.getPrincipalRef().equals(getPrincipal().getPrincipalRef())) {
            if (addToInbox(ui.inboxPath, ui.principal, ei,
                           fromOrganizer).isOk()) {
              ui.setStatus(ScheduleStates.scheduleOk);
              deliveryStatus = IcalDefs.deliveryStatusDelivered;
            } else {
              ui.setStatus(ScheduleStates.scheduleError);
              deliveryStatus = IcalDefs.deliveryStatusFailed;
            }
          } else {
            // That's us
            ui.setAttendeeScheduleStatus(null);
            ui.setStatus(ScheduleStates.scheduleOk);
          }
        } catch (final BedeworkAccessException ignored) {
          ui.setStatus(ScheduleStates.scheduleNoAccess);
          deliveryStatus = IcalDefs.deliveryStatusNoAccess;
        }

        if (fromOrganizer) {
          if (deliveryStatus != null) {
            ui.setAttendeeScheduleStatus(deliveryStatus);
          }
//      } else {
//          ev.getOrganizer().setScheduleStatus(deliveryStatus);
        }
      }

      if (debug()) {
        debug("added recipient " + ui.recipient + " status = " + ui.getStatus());
      }
    }

    for (final Collection<UserInbox> inboxes: hostMap.values()) {
      /* Send any ischedule requests to external servers. */
      sendExternalRequest(sr, ei, inboxes);
    }
  }

  /** Add a copy of senderEi to the users inbox and add to the autoschedule queue.
   * The 'sender' may be the organizer of a meeting, if it's REQUEST etc., or the
   * attendee replying.
   *
   * @param inboxPath - eventual destination
   * @param attPrincipal - attendees principal
   * @param senderEi the event
   * @param fromOrganizer - true if it's coming from the organizer
   * @return status
   */
  private Response<?> addToInbox(final String inboxPath,
                                 final BwPrincipal<?> attPrincipal,
                                 final EventInfo senderEi,
                                 final boolean fromOrganizer) {
    final var resp = new Response<>();
    final EventInfo ei = copyEventInfo(senderEi, fromOrganizer, attPrincipal);
    final BwEvent ev = ei.getEvent();

    if (senderEi.getReplyUpdate()) {
      // Flag as a trivial update to attendee status
      ev.addXproperty(new BwXproperty(BwXproperty.bedeworkSchedulingReplyUpdate,
                                      null, "true"));
    }

    // Recipients should not be able to see other recipients.

    if (!Util.isEmpty(ev.getRecipients())) {
      ev.getRecipients().clear();
    }
    ev.addRecipient(getSvc().getDirectories().principalToCaladdr(attPrincipal));

    /*
    if (destAtt != null) {
      String attPartStat = destAtt.getPartstat();

      if ((attPartStat == null) ||   // default - needs-action
          (!attPartStat.equalsIgnoreCase(IcalDefs.partstatValAccepted) &&
           !attPartStat.equalsIgnoreCase(IcalDefs.partstatValCompleted) &&
           !attPartStat.equalsIgnoreCase(IcalDefs.partstatValDelegated))) {
        ev.setTransparency(IcalDefs.transparencyTransparent);
      }
    }*/

    final String evDtstamp = ev.getDtstamp();

    ev.setScheduleState(BwEvent.scheduleStateNotProcessed);
    ev.setColPath(inboxPath);

    if (ei.getNumContainedItems() > 0) {
      for (final EventInfo cei: ei.getContainedItems()) {
        cei.getEvent().setColPath(inboxPath);
      }
    }

    /* Before we add this we should see if there is an earlier one we can
     * discard. As attendees update their status we get many requests sent to
     * each attendee.
     *
     * Also this current message may be earlier than one already in the inbox.
     *
     * TODO - fix recurrences
     *
     * We could get separate messages for the same uid but with different
     * recurrence ids if we are an attendee to some instances only.
     *
     * In the inbox these will be separate events with the same uid -
     * possibly, They probably need to be combined in the users
     * calendar as a single recurring event.
     */

    final int smethod = ev.getScheduleMethod();

    if (Icalendar.itipRequestMethodType(smethod)) {
      final var inevs = getEventsByUid(inboxPath,
                                       ev.getUid());

      if (inevs.isError()) {
        return resp.fromResponse(inevs);
      }

      for (final EventInfo inei: inevs.getEntities()) {
        final BwEvent inev = inei.getEvent();

        final int cres = evDtstamp.compareTo(inev.getDtstamp());

        if (cres <= 0) {
          // Discard the new one
          return new Response<>().ok();
        }

        /* Discard the earlier message */

        /* XXX What if this message is currently being processed by the inbox
         * handler process? Does it matter - will it reappear?
         *
         * Probably need to handle stale-state exceptions at the other end.
         */

        final var delResp = getSvc().getEventsHandler()
                                    .delete(ei, true, false);
        if (delResp.isError()) {
          return resp.fromResponse(delResp);
        }
      }
    }

    /* Add it and post to the autoscheduler */
    final var addResp = addEvent(ei,
                                 "In-" + Uid.getUid() + "-" + evDtstamp,
                                 BwCollection.calTypePendingInbox,
                                 true);

    if (!addResp.isOk()) {
      return resp.fromResponse(addResp);
    }

    if (debug()) {
      debug("Add event with name " + ev.getName() +
            " and summary " + ev.getSummary() +
            " to " + ev.getColPath());
    }

    addAutoScheduleMessage(true,
                           attPrincipal.getPrincipalRef(),
                           ev.getName());

    return resp;
  }

  /* Get the inbox for the recipient from the search result. If there is
   * no inbox object already it will be added. If the recipient is not local to this
   * system, we mark the inbox entry as deferred and add the recipient to the
   * list of external recipients. We will possibly mail the request or try
   * ischedule to another server.
   *
   * If fromAtt is not null and the recipient is that attendee we skip it. This
   * is the result of a reply from that attendee being broadcast to the other
   * attendees.
   *
   * If reinvite is true we are resending the invitation to all attendees,
   * including those who previously declined. Otherwise, we skip those who
   * declined.
   *
   * Note we have to search all overrides to get the information we need. We can
   * short circuit this to some extent as we fill in information about attendees.
   */
  private void getRecipientInbox(final EventInfo ei,
                                 final String recip,
                                 final String fromAttUri,
                                 final ScheduleResult<?> sr,
                                 final boolean freeBusyRequest) {
    final BwEvent ev = ei.getEvent();
    if (debug()) {
      debug(format("Get inbox for %s", recip));
    }

    /* See if the attendee is in this event */
    final var att = ev.getSchedulingInfo().findParticipant(recip);

    if ((att != null) && (fromAttUri != null) &&
        fromAttUri.equals(att.getCalendarAddress())) {
      // Skip this one, they were the ones that sent the reply.
      return;
    }

    final UserInbox ui = getInbox(ev, sr, recip, freeBusyRequest);

    if (att != null) {
      ui.addAttendee(att);

      if (Util.compareStrings(att.getParticipationStatus(),
                              IcalDefs.partstatValDeclined) == 0) {
        // Skip this one, they declined.
        return;
      }

      att.setScheduleStatus(IcalDefs.deliveryStatusPending);
    }

    if (ui.getStatus() == ScheduleStates.scheduleDeferred) {
      sr.externalRcs.add(recip);
    } else if (ui.getStatus() == ScheduleStates.scheduleNoAccess) {
      sr.error(new BedeworkException(
              CalFacadeErrorCode.schedulingAttendeeAccessDisallowed));

      if (att != null) {
        att.setScheduleStatus(IcalDefs.deliveryStatusNoAccess);
      }
    } else if ((ui.principal == null) && (ui.getHost() != null)) {
      sr.externalRcs.add(recip);
    }

    if (ei.getNumOverrides() > 0) {
      for (final EventInfo oei: ei.getOverrides()) {
        getRecipientInbox(oei, recip, fromAttUri, sr, freeBusyRequest);
      }
    }
  }

  /* Return with deferred for external user.
   *
   * For an internal user - skips it if it's ourselves - we don't want
   * our own message in our inbox. Otherwise, checks that we have access
   * to send the message. If so sets the path of the inbox.
   */
  private UserInbox getInbox(final BwEvent ev,
                             final ScheduleResult<?> sr,
                             final String recipient,
                             final boolean freeBusyRequest) {
    UserInbox ui = (UserInbox)sr.recipientResults.get(recipient);

    if (ui != null) {
      return ui;
    }

    ui = new UserInbox(ev, recipient);
    sr.addRecipientResult(ui);

    final BwPrincipal<?> principal = getSvc().getDirectories().caladdrToPrincipal(recipient);

    if (principal == null) {
      /* External to the system */
      ui.setHost(BwHosts.getHostForRecipient(recipient));

      final Host hi = ui.getHost();
      if (hi == null) {
        ui.setStatus(ScheduleStates.scheduleDeferred);
        return ui;
      }

      if (freeBusyRequest) {
        // All can handle that
        return ui;
      }

      if (!hi.getSupportsISchedule() &&
          !hi.getSupportsCaldav() &&
          !hi.getSupportsBedework()) {
        ui.setStatus(ScheduleStates.scheduleDeferred);
      }

      return ui;
    }

    try {
      if (principal.getPrincipalRef().equals(getPrincipal().getPrincipalRef())) {
        /* This is our own account. Let's not add it to our inbox.
         */
        ui.principal = getPrincipal();
        ui.setStatus(ScheduleStates.scheduleUnprocessed);
        return ui;
      }

      ui.principal = principal;

      final int priv;
      if (freeBusyRequest) {
        priv = PrivilegeDefs.privScheduleFreeBusy;
      } else {
        priv = PrivilegeDefs.privScheduleRequest;
      }

      final BwCollection inbox = getSpecialCalendar(ui.principal,
                                                    BwCollection.calTypePendingInbox,
                                                    true, priv);
      if (inbox == null) {
        ui.setStatus(ScheduleStates.scheduleNoAccess);
      } else {
        ui.inboxPath = inbox.getPath();
      }
    } catch (final BedeworkAccessException ignored) {
      ui.setStatus(ScheduleStates.scheduleNoAccess);
    }

    return ui;
  }
}
