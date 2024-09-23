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

import org.bedework.calfacade.Attendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.SchedulingInfo;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvc.CalSvcHelperRw;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;

import static org.bedework.util.calendar.IcalDefs.scheduleAgentServer;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece introduces the interface and provides some commonly used
 * methods.
 *
 * @author douglm
 *
 */
public abstract class SchedulingBase extends CalSvcHelperRw
        implements SchedulingIntf {
  SchedulingBase(final CalSvc svci) {
    super(svci);
  }

  /** Add an entry to the queue
  *
  * @param inBox - true if it's an inbox event (inbound)
  * @param principalHref identify principal
  * @param eventName name of event
  */
  protected void addAutoScheduleMessage(final boolean inBox,
                                        final String principalHref,
                                        final String eventName) {
    getSvc().postNotification(
            SysEvent.makeEntityQueuedEvent(SysEvent.SysCode.SCHEDULE_QUEUED,
                                           principalHref,
                                           eventName,
                                           inBox));
  }

  /** Return true if there is a significant change for the entity or any overrides
   *
   * @param ei EventInfo
   * @return true if something important changed
   */
  protected boolean significantChange(final EventInfo ei) {
    if (ei.getNewEvent() ||
        ei.getChangeset(getPrincipalHref()).getSignificantChange()) {
      return true;
    }

    final BwEvent ev = ei.getEvent();
    final boolean override = ev instanceof BwEventProxy;

    if (override|| (ei.getOverrides() == null)) {
      return false;
    }

    for (final EventInfo oei: ei.getOverrides()) {
      if (significantChange(oei)) {
        return true;
      }
    }

    return false;
  }

  /** Copy an event to send as a request or a response. Non-recurring is easy,
   * we just copy it.
   *
   * <p>Recurring events introduce a number of complications. We are only supposed
   * to send as much as the recipient needs to know, that is, if an attendee
   * has been disinvited from one instance we should not send that instance but
   * instead add an EXDATE. For that case we will have an override that does not
   * include the attendee.
   *
   * <p>For the case that an attendee has been added we should remove any rules
   * and add RDATES for all instances in which the attendee is present.
   *
   * @param ei EventInfo
   * @param owner BwPrincipal
   * @return a copy of the event.
   */
  @Override
  public EventInfo copyEventInfo(final EventInfo ei,
                                 final BwPrincipal<?> owner) {
    return copyEventInfo(ei, false, owner);
  }

  /** Same as copyEventInfo(EventInfo, BwPrincipal) except it only copies
   * significant changes.
   *
   * @param ei event to copy
   * @param significantChangesOnly true to copy only significant
   * @param owner for new event
   * @return a copy of the event.
   */
  protected EventInfo copyEventInfo(final EventInfo ei,
                                    final boolean significantChangesOnly,
                                    final BwPrincipal<?> owner) {
    final BwEvent ev = ei.getEvent();
    final BwEvent newEv = copyEvent(ev, null, owner);
    final StringBuilder changeInfo = new StringBuilder();

    changeInfo.append(ev.getDtstamp());

    final boolean cancel = ev.getScheduleMethod() == ScheduleMethods.methodTypeCancel;
    final boolean adding = ei.getUpdResult().adding;
    final boolean reply = ev.getScheduleMethod() == ScheduleMethods.methodTypeReply;

    if (cancel) {
      changeInfo.append(";CANCEL");
    } else if (adding) {
      changeInfo.append(";CREATE");
    } else if (reply) {
      changeInfo.append(";REPLY");
    } else {
      changeInfo.append(";UPDATE");
    }

    if (adding || cancel) {
      changeInfo.append(";MASTER");
    } else {
      addChangeInfo(ei, changeInfo, "MASTER");
    }

    if (!ev.getRecurring()) {
      setChangeInfo(newEv, changeInfo);
      return new EventInfo(newEv);
    }

    if (cancel) {
      /* Collect any attendees in overrides not in the copied master */

      final Set<EventInfo> overrides = ei.getOverrides();
      final var parts = newEv.getParticipants();

      if (overrides != null) {
        for (final EventInfo oei: overrides) {
          final var ovParts = oei.getEvent().getParticipants();
          for (final var ovatt: ovParts.getAttendees()) {
            if (parts.findAttendee(ovatt.getCalendarAddress()) == null) {
              parts.copyAttendee(ovatt);
            }
          }
        }
      }

      setChangeInfo(newEv, changeInfo);
      return new EventInfo(newEv);
    }

    boolean fromOrganizer = ev.getOrganizerSchedulingObject();
    if (!fromOrganizer && !Util.isEmpty(ei.getOverrides())) {
      for (final var oei: ei.getOverrides()) {
        if (oei.getEvent().getOrganizerSchedulingObject()) {
          fromOrganizer = true;
          break;
        }
      }
    }
    boolean attendeeInMaster = false;

    final String uri = getSvc().getDirectories()
                               .principalToCaladdr(owner);
    if (fromOrganizer) {
      attendeeInMaster = ev.findAttendee(uri) != null;
    }

    /* Save the status - we may change to master-suppressed */
    final String masterStatus = newEv.getStatus();

    /* We may suppress the master event if there is no significant change and
     * we only want significant changes or if the attendee is not an attendee
     * of the master event.
     */
    boolean masterSuppressed = false;
    boolean significant;

    if (fromOrganizer && !attendeeInMaster) {
      if (debug()) {
        debug("Sending invite with suppressed master");
      }
      masterSuppressed = true;
    }

    List<String> deletedRecurids = null;

    if (masterSuppressed) {
      // Attendee will appear in overrides. Remove rules and r/exdates
      if (!Util.isEmpty(ei.getUpdResult().deletedInstances)) {

        deletedRecurids =
                new ArrayList<>(ei.getUpdResult().deletedInstances);
      }

      if (newEv.getRrules() != null) {
        newEv.getRrules().clear();
      }
      if (newEv.getRdates() != null) {
        newEv.getRdates().clear();
      }
      if (newEv.getExrules() != null) {
        newEv.getExrules().clear();
      }
      if (newEv.getExdates() != null) {
        newEv.getExdates().clear();
      }
      newEv.setSuppressed(true);
    }

    final Set<EventInfo> overrides = ei.getOverrides();
    final Set<EventInfo> newovs = new TreeSet<>();

    if (overrides != null) {
      for (final EventInfo oei: overrides) {
        final BwEvent oev = oei.getEvent();

        final boolean attendeeInOverride =
                oev.findAttendee(uri) != null;

        if (!masterSuppressed && attendeeInMaster && attendeeInOverride) {
          // Don't try to suppress this one. We need the exdate

          if (significantChangesOnly) {
            significant = oei.getChangeset(getPrincipalHref()).getSignificantChange();

            if (!oei.getNewEvent() && !significant) {
              continue;
            }
          }
        }

        if (fromOrganizer) {
          if (!masterSuppressed && attendeeInMaster) {
            /* If the attendee is not in this override, add an exdate to the master
             */
            if (!attendeeInOverride) {
              final String rid = oev.getRecurrenceId();
              final BwDateTime bwrdt =
                      BwDateTime.fromUTC(rid.length() == 8, rid);
              newEv.addExdate(bwrdt);

              continue;
            }
          } else if (attendeeInOverride) {
            /* Add this override as an rdate */
            final String rid = oev.getRecurrenceId();
            final BwDateTime bwrdt =
                    BwDateTime.fromUTC(rid.length() == 8, rid);
            newEv.addRdate(bwrdt);

            if ((deletedRecurids != null) &&
                    deletedRecurids.contains(oev.getRecurrenceId())) {
              oev.setStatus(BwEvent.statusCancelled);
            } else if ((oev.getStatus() != null) &&
                oev.getStatus().equals(BwEvent.statusMasterSuppressed)) {
              // Not overridden - set to saved master value
              oev.setStatus(masterStatus);
            }

            oev.setSequence(ev.getSequence());
          } else {
            continue;
          }
        }

        if (adding || cancel) {
          changeInfo.append(";RID=");
          changeInfo.append(oev.getRecurrenceId());
        } else {
          addChangeInfo(ei, changeInfo, oev.getRecurrenceId());
        }

        newovs.add(new EventInfo(copyEvent(oev, newEv,
                                           owner)));
      }
    }

    setChangeInfo(newEv, changeInfo);
    return new EventInfo(newEv, newovs);
  }

  private void addChangeInfo(final EventInfo ei,
                             final StringBuilder changeInfo,
                             final String entity) {
    if (ei.getChangeset(getPrincipalHref()).isEmpty()) {
     // Forced update?

      appendEntity(changeInfo,  entity);
      return;
    }

    boolean changed = false;

    final Collection<ChangeTableEntry> ctes =
            ei.getChangeset(getPrincipalHref()).getEntries();

    for (final ChangeTableEntry cte: ctes) {
      if (!cte.getChanged()) {
        continue;
      }

      if (!changed) {
        appendEntity(changeInfo,  entity);
        changeInfo.append(";CHANGES");
      }
      changed = true;

      changeInfo.append(";");

      /* TODO - fix this at source */
      if ((cte.getIndex() == PropertyInfoIndex.DTEND) &&
          (ei.getEvent().getEntityType() == IcalDefs.entityTypeTodo)) {
        changeInfo.append(PropertyInfoIndex.DUE);
      } else {
        changeInfo.append(cte.getIndex().toString());
      }
    }
  }

  private void appendEntity(final StringBuilder changeInfo,
                            final String entity) {
    changeInfo.append(";");
    if ("MASTER".equals(entity)) {
      changeInfo.append(entity);
    } else {
      changeInfo.append("RID=");
      changeInfo.append(entity);
    }
  }

  private void setChangeInfo(final BwEvent ev,
                             final StringBuilder changeInfo) {
    if (changeInfo.isEmpty()) {
      return;
    }

    ev.removeXproperties(BwXproperty.bedeworkChanges);
    ev.addXproperty(new BwXproperty(BwXproperty.bedeworkChanges,
                                    null,
                                    changeInfo.toString()));
  }

  protected BwEvent copyEvent(final BwEvent origEv,
                              final BwEvent masterEv,
                              final BwPrincipal<?> owner) {
    final BwEvent newEv;
    BwEventProxy proxy = null;
    final String ownerHref = owner.getPrincipalRef();

    if (origEv instanceof BwEventProxy) {
      proxy = (BwEventProxy)origEv;

      //proxy.setRef((BwEventAnnotation)getSvc().merge(proxy.getRef()));
      //getSvc().reAttach(proxy.getRef());

      if (masterEv == null) {
        /* we are being asked to copy an instance of a recurring event - rather than
         * a complete recurring event + all overrides - clone the master
         */
        newEv = new BwEventObj();
        origEv.copyTo(newEv);
        newEv.setRecurring(false);
        proxy = null; // Return the instance copy
      } else {
        // Clone the annotation and set the master and target to our new master

        proxy = proxy.clone(masterEv, masterEv); // ANNOTATION
        newEv = proxy.getRef();
      }
    } else {
      //getSvc().reAttach(origEv);

      newEv = (BwEvent)origEv.clone();
    }

    /* Remove some stuff we won't be sending */

    for (final var att: newEv.getParticipants().getAttendees()) {
      att.setScheduleStatus(null);
    }

    //newEv.removeXproperties(BwXproperty.appleNeedsReply);

    newEv.getSchedulingOwner().setScheduleStatus(null);

    newEv.setOwnerHref(ownerHref);
    newEv.setCreatorHref(ownerHref);
    newEv.setDtstamps(getCurrentTimestamp());

    if (owner.equals(getPrincipal())) {
      if (proxy != null) {
        return proxy;
      }
      return newEv;
    }

    /* Copy event entities */
    BwLocation loc = newEv.getLocation();
    if (loc != null) {
      loc = (BwLocation)loc.clone();
      loc.setOwnerHref(ownerHref);
      loc.setCreatorHref(ownerHref);
      loc.initUid();
      newEv.setLocation(loc);
    }

    BwContact contact = newEv.getContact();
    if (contact != null) {
      contact = (BwContact)contact.clone();
      contact.setOwnerHref(ownerHref);
      contact.setCreatorHref(ownerHref);
      contact.initUid();
      newEv.setContact(contact);
    }

    if (proxy != null) {
      return proxy;
    }

    return newEv;
  }

  private static final AtomicLong suffixValue = new AtomicLong();

  @Override
  public Response addEvent(final EventInfo ei,
                           final String namePrefix,
                           final int calType,
                           final boolean noInvites) {
    final BwEvent ev = ei.getEvent();
    String prefix = namePrefix;

    final boolean schedulingBox = (calType == BwCalendar.calTypeInbox) ||
            (calType == BwCalendar.calTypePendingInbox) ||
            (calType == BwCalendar.calTypeOutbox);

    /* We can get a lot of adds and deletions in scheduling inboxes.
       An event will arrive, be processed and deleted from the inbox
       only for another to arrive moments later.

       This has an implication for naming - OpenSearch will ignore
       updates for a deleted entity for a period following the
       deletion. We need to make it look like a different entity for
       each add.
      */

    if (schedulingBox) {
      prefix += suffixValue.getAndIncrement();
    }

    for (int i = 0; i < 100; i++) {  // Avoid malicious users
      ev.setName(prefix + ".ics");

      final var resp = getSvc().getEventsHandler()
                               .add(ei, noInvites,
                                    schedulingBox,
                                    true,
                                    false);

      if (resp.isOk()) {
        return resp;
      }

      if (CalFacadeException.duplicateName.equals(resp.getMessage())) {
        prefix += suffixValue.getAndIncrement();
        continue;    // Try again
      }

      if (CalFacadeException.duplicateGuid.equals(resp.getMessage())) {
        getSvc().rollbackTransaction();
      }

      return resp;
    }

    /* Ran out of tries. */

    getSvc().rollbackTransaction();

    return Response.notOk(new Response(), Response.Status.failed,
                          CalFacadeException.duplicateName);
  }

  /** Find the attendee in this event which corresponds to the current user
   *
   * @param ei to search
   * @return attendee or null.
   */
  public Attendee findUserAttendee(final EventInfo ei) {
    final String attUri = getSvc().getDirectories().principalToCaladdr(getPrincipal());

    final BwEvent ev = ei.getEvent();

    if (!ev.getSuppressed()) {
      return ev.getParticipants().findAttendee(attUri);
    }

    if (ei.getNumOverrides() > 0) {
      for (final EventInfo oei: ei.getOverrides()) {
        final BwEvent oev = oei.getEvent();

        final Attendee att = oev.getParticipants()
                                .findAttendee(attUri);
        if (att != null) {
          return att;
        }
      }
    }

    return null;
  }

  @Override
  public void setupReschedule(final EventInfo ei) {
    final var event = ei.getEvent();
    final var parts = event.getParticipants();

    final Attendee userAttendee = findUserAttendee(ei);
    if (userAttendee == null) {
      throw new CalFacadeException(CalFacadeException.schedulingNotAttendee);
    }

    //event.setSequence(event.getSequence() + 1);

    /* Set the PARTSTAT to needs action for all attendees - except us */
    parts.getAttendees().stream()
         .filter(att -> !att.equals(userAttendee)).forEach(att -> {
      att.setParticipationStatus(IcalDefs.partstatValNeedsAction);
      att.setExpectReply(true);
    });
  }

  protected boolean initScheduleEvent(final EventInfo ei,
                                      final boolean response,
                                      final boolean iSchedule) {
    final SchedulingInfo si = ei.getSchedulingInfo();
    final BwEvent event = ei.getEvent();

    if (!iSchedule) {
      /* Gather together recipients from all attendees in the master + overrides
       */

      if (!Util.isEmpty(event.getRecipients())) {
        event.getRecipients().clear();
      }

      if (response) {
        event.addRecipient(si.getSchedulingOwner()
                             .getCalendarAddress());
      } else {
        getRecipients(event, event);

        if (ei.getNumOverrides() > 0) {
          ei.getOverrides()
            .forEach(oei -> getRecipients(event, oei.getEvent()));
        }
      }
    }

    setupSharableEntity(event, getPrincipal().getPrincipalRef());
    event.setDtstamps(getCurrentTimestamp());

    event.assignGuid(getSvc().getSystemProperties().getSystemid()); // no-op if already set

    /* Ensure attendees have sequence and dtstamp of event */
    for (final var att: event.getParticipants().getAttendees()) {
      if (!att.getScheduleAgent().equalsIgnoreCase(
              IcalDefs.scheduleAgents[scheduleAgentServer])) {
        continue;
      }

      att.setSequence(event.getSequence());
      att.setSchedulingDtStamp(event.getDtstamp());

      if (response) {
        att.setScheduleStatus(IcalDefs.deliveryStatusSuccess);
      }
    }

    return true;
  }

  private void getRecipients(final BwEvent master, final BwEvent ev) {
    for (final var att: ev.getParticipants().getAttendees()) {
      if (!att.getScheduleAgent().equalsIgnoreCase(
              IcalDefs.scheduleAgents[scheduleAgentServer])) {
        continue;
      }

      master.addRecipient(att.getCalendarAddress());
    }
  }
}
