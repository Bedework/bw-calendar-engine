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
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.Participant;
import org.bedework.calfacade.SchedulingOwner;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.CalSvcI;
import org.bedework.convert.RecurUtil;
import org.bedework.convert.RecurUtil.Recurrence;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;
import org.bedework.util.timezones.DateTimeUtil;

import net.fortuna.ical4j.model.Period;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

/** Handles incoming method REQUEST scheduling messages.
 *
 * @author Mike Douglass
 */
public class InRequest extends InProcessor {
  /**
   * @param svci interface
   */
  public InRequest(final CalSvcI svci) {
    super(svci);
  }

  @Override
  public ProcessResult process(final EventInfo ei)  {
    /* We are acting as an attendee getting a request from the organizer, either
     * a first invitation or an update
     */

    final ProcessResult pr = new ProcessResult();

    final BwPreferences prefs = getSvc().getPrefsHandler().get();
    final BwEvent ev = ei.getEvent();
    final String owner = ev.getOwnerHref();

    final boolean schedAssistant = ev.isSchedulingAssistant();

    if (debug()) {
      debug("InSchedule schedAssistant = " + schedAssistant);
    }

    /* First we save or update the event in the users default scheduling calendar
     */

    final SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

    final String uri = getSvc().getDirectories()
                               .principalToCaladdr(getSvc().getPrincipal());
    String colPath = null;
    EventInfo ourCopy;
    boolean adding = false;

    ev.setAttendeeSchedulingObject(true);
    ev.setOrganizerSchedulingObject(false);

    check: {
      ourCopy = sched.getStoredMeeting(ev);

      if (ourCopy != null) {
        /* Update */

        if (debug()) {
          debug("InSchedule update for " + owner);
        }

        colPath = ourCopy.getEvent().getColPath();
        final boolean vpoll =
                ev.getEntityType() == IcalDefs.entityTypeVpoll;

        if (vpoll) {
          if (!updateAttendeePollCopy(ourCopy, ei, uri)) {
            break check;
          }
        } else if (!updateAttendeeCopy(ourCopy, ei, uri)) {
          break check;
        }

        pr.removeInboxEntry = !anySignificantChange(ourCopy);
      } else {
        /* New invitation - Save in default */

        adding = true;

        if (debug()) {
          debug("InSchedule add for " + owner);
        }

        final String prefSched = getSvc().getCalendarsHandler().
                                         getPreferred(IcalDefs.entityTypeIcalNames[ev.getEntityType()]);
        if (prefSched == null) {
          // SCHED - status = no default collection
          if (debug()) {
            debug("InSchedule - no default collection for " + owner);
          }

          // XXX set error code in request status

          pr.removeInboxEntry = true;
          return pr;
        }

        ourCopy = newAttendeeCopy(getSvc(), prefSched, ei, uri);
        if (ourCopy == null) {
          if (debug()) {
            debug("InSchedule - unable to add to calendar for " + owner);
          }

          // XXX set error code in request status

          pr.removeInboxEntry = true;
          return pr;
        }

        ev.addXproperty(new BwXproperty(BwXproperty.bedeworkSchedulingNew,
                                        null, "true"));

        pr.removeInboxEntry = false;
      }
    } // end check

    if (schedAssistant) {
      // Don't need the notification
      pr.removeInboxEntry = true;
    }

    ev.addXproperty(new BwXproperty(BwXproperty.bedeworkSchedulingEntityPath,
                                    null,
                                    colPath));

    /* We've saved it in the users calendar - now see if they want to auto
     * respond.
     */

    boolean noInvites = true;

    final boolean doAutoRespond = !pr.removeInboxEntry &&
                            prefs.getScheduleAutoRespond();

    if (doAutoRespond) {
      if (debug()) {
        debug("InSchedule - auto responding for " + owner);
      }

      noInvites = !autoRespond(getSvc(), ourCopy, ei,
                               prefs.getScheduleDoubleBook(), uri);
    }

    if (adding) {
      final String namePrefix = ourCopy.getEvent().getUid();

      final Response resp =
              sched.addEvent(ourCopy, namePrefix,
                             BwCalendar.calTypeCalendarCollection,
                             noInvites);
      if (!resp.isOk()) {
        if (debug()) {
          debug("Schedule - error " + resp +
                " adding event for " + owner);
        }

        return Response.fromResponse(pr, resp);
      }
    } else {
      final UpdateResult ur = 
              getSvc().getEventsHandler().update(ourCopy,
                                                 noInvites,
                                                 null,
                                                 false); // autocreate

      if (debug()) {
        debug("Schedule - update result " + pr +
                      " for event" + ourCopy.getEvent());
      }

      if (!ur.isOk()) {
        return Response.fromResponse(pr, ur);
      }
    }

    pr.attendeeAccepting =
      !Util.isEmpty(ev.getXproperties(
              BwXproperty.bedeworkSchedulingReplyUpdate));

    return pr;
  }

  /* ============================================================
                      Private methods
     ============================================================ */

  private boolean autoRespond(final CalSvcI svci,
                              final EventInfo ourCopy,
                              final EventInfo inboxEi,
                              final boolean doubleBookOk,
                              final String uri) {
    final BwEvent inboxEv = inboxEi.getEvent();
    final String owner = inboxEv.getOwnerHref();
    final var inSi = inboxEv.getSchedulingInfo();

    if (ourCopy == null) {
      // Error - deleted while we did this?
      if (debug()) {
        debug("InSchedule - no event for auto respond for " + owner);
      }

      return false;
    }

    final var inOwner = inSi.newSchedulingOwner();
    inOwner.setCalendarAddress(uri);

    final BwEvent ourEvent = ourCopy.getEvent();
    final var ourSi = ourEvent.getSchedulingInfo();

    final String now = DateTimeUtil.isoDateTimeUTC(new Date());

    if (!ourEvent.getRecurring()) {
      /* Don't bother if it's in the past */

      if (ourEvent.getDtend().getDate().compareTo(now) < 0) {
        return false;
      }

      final Participant part = ourSi.findParticipant(uri);

      if (part == null) {
        // Error?
        if (debug()) {
          debug("InSchedule - no attendee on our copy for auto respond for " +
                owner);
        }

        return false;
      }

      if (debug()) {
        debug("send response event for " + owner + " " + inboxEv.getName());
      }

      part.setExpectReply(false); // We're about to reply.

      String partStat = IcalDefs.partstatValAccepted;

      ourEvent.removeXproperties(BwXproperty.appleNeedsReply);

      if (!doubleBookOk) {
        // See if there are any events booked during this time.
        if (checkBusy(svci,
                      ourEvent.getUid(),
                      inboxEv.getDtstart(), inboxEv.getDtend(),
                      inOwner,
                      inboxEv.getUid())) {
          partStat = IcalDefs.partstatValDeclined;
        } else {
          ourEvent.setTransparency(IcalDefs.transparencyOpaque);
        }
      }

      part.setParticipationStatus(partStat);

      ourEvent.setScheduleMethod(ScheduleMethods.methodTypeReply);
      ourSi.removeParticipant(part);
      ourSi.addParticipant(part);

      return true;
    }

    // Recurring event - do the above per recurrence

    final AuthProperties authpars = svci.getAuthProperties();
    final int maxYears = authpars.getMaxYears();
    final int maxInstances = authpars.getMaxInstances();

    final Collection<Recurrence> recurrences =
            RecurUtil.getRecurrences(inboxEi,
                                     maxYears,
                                     maxInstances,
                                     now,
                                     null);

    if (Util.isEmpty(recurrences)) {
      return false;
    }

    if (debug()) {
      debug("autoRespond: " + recurrences.size() + " instances");
    }

    /* Assume accept and then override with declines - assuming we're in the
     * master
     */

    ourEvent.removeXproperties(BwXproperty.appleNeedsReply);
    boolean masterSupressed = true;

    if (!inboxEv.getSuppressed()) {
      masterSupressed = false;
      final Participant part = ourSi.findParticipant(uri);

      if (part != null) {
        // It should never be null

        part.setExpectReply(false);
        part.setParticipationStatus(IcalDefs.partstatValAccepted);
      }
      ourEvent.setTransparency(IcalDefs.transparencyOpaque);
    }

    final RecurInfo rinfo = checkBusy(svci,
                                      ourEvent.getUid(),
                                      recurrences, inOwner,
                                      inboxEv.getUid(), doubleBookOk);

    /* If we have a master then we should set its status to cover the largest
     * number of overrides - if any.
     *
     * The easiest case is no overrides and all accepted or all declined.
     *
     * Otherwise we have to update or add overrides.
     */

    final boolean allAcceptedOrDeclined =
            (rinfo.availCt == 0) || (rinfo.busyCt == 0);
    final boolean masterAccept = rinfo.availCt >= rinfo.busyCt;
    final String masterPartStat;

    if (masterAccept) {
      masterPartStat = IcalDefs.partstatValAccepted;
    } else {
      masterPartStat = IcalDefs.partstatValDeclined;
    }

    if (!masterSupressed) {
      final Participant part = ourSi.findParticipant(uri);

      if (!masterPartStat.equals(part.getParticipationStatus())) {
        part.setParticipationStatus(masterPartStat);
      }

      if (masterAccept) {
        ourEvent.setTransparency(IcalDefs.transparencyOpaque);
      } else {
        ourEvent.setTransparency(IcalDefs.transparencyTransparent);
      }
    }

    if (allAcceptedOrDeclined) {
      // Ensure any overrides have the same status
      for (final var oei: ourCopy.getOverrides()) {
        final var override = oei.getEvent();
        final var ovSi = override.getSchedulingInfo();
        final var ovpart = ovSi.findParticipant(uri);

        ovpart.setExpectReply(false); // We're about to reply.

        if (!masterPartStat.equals(ovpart.getParticipationStatus())) {
          ovpart.setParticipationStatus(masterPartStat);
        }

        if (masterAccept) {
          override.setTransparency(IcalDefs.transparencyOpaque);
        } else {
          override.setTransparency(IcalDefs.transparencyTransparent);
        }

        override.removeXproperties(BwXproperty.appleNeedsReply);
      }

      return true;
    }

    /* Some accepts and some declines */

    for (final RecurrenceInfo ri: rinfo.ris) {
      final Recurrence r = ri.r;

      //if (!masterSupressed && !ri.busy && (r.override == null)) {
      //  // fine
      //  continue;
      //}

      final boolean mustOverride = masterAccept == ri.busy;

      final EventInfo oei = ourCopy.findOverride(r.recurrenceId,
                                                 mustOverride);
      if (oei == null) {
        continue;
      }

      final BwEvent override = oei.getEvent();
      final var ovSi = override.getSchedulingInfo();
      final var ovpart = ovSi.findParticipant(uri);

      if (((BwEventProxy)override).getRef().unsaved()) {
        override.setDtstart(r.start);
        override.setDtend(r.end);
        override.setDuration(BwDateTime.makeDuration(r.start, r.end).toString());
      }

      if (ovpart == null) {
        // Guess we weren't invited
        continue;
      }

      ovpart.setExpectReply(false); // We're about to reply.

      if (!ri.busy) {
        ovpart.setParticipationStatus(IcalDefs.partstatValAccepted);
        override.setTransparency(IcalDefs.transparencyOpaque);
      } else {
        ovpart.setParticipationStatus(IcalDefs.partstatValDeclined);
        override.setTransparency(IcalDefs.transparencyTransparent);
      }

      override.removeXproperties(BwXproperty.appleNeedsReply);
    }

    return true;
  }

  private static class RecurrenceInfo {
    Recurrence r;
    boolean busy;

    RecurrenceInfo(final Recurrence r,
                   final boolean busy) {
      this.r = r;
      this.busy = busy;
    }
  }

  private static class RecurInfo {
    List<RecurrenceInfo> ris = new ArrayList<>();
    int availCt;
    int busyCt;

    void add(final Recurrence r,
             final boolean busy) {
      ris.add(new RecurrenceInfo(r, busy));
      if (busy) {
        busyCt++;
      } else{
        availCt++;
      }
    }
  }

  private RecurInfo checkBusy(final CalSvcI svci,
                              final String excludeUid,
                              final Collection<Recurrence> recurrences,
                              final SchedulingOwner org,
                              final String uid,
                              final boolean doubleBookOk) throws CalFacadeException {
    /* TODO
     * We should chunk up the freebusy into fewer requests over longer periods.
     * That means we have to figure out the overlaps with the returned info ourselves.
     *
     * If we don't do that we could fire off 100s of freebusy queries.
     */
    final RecurInfo res = new RecurInfo();

    for (final Recurrence r: recurrences) {
      boolean busy = false;

      if (!doubleBookOk) {
        // See if there are any events booked during this time.
        busy = checkBusy(svci,
                         excludeUid,
                         r.start, r.end, org, uid);
        if (debug()) {
          debug("busy=" + busy + " for " + r.start + " to " + r.end);
        }
      }

      res.add(r, busy);
    }

    return res;
  }

  private boolean checkBusy(final CalSvcI svci,
                            final String excludeUid,
                            final BwDateTime start,
                            final BwDateTime end,
                            final SchedulingOwner org,
                            final String uid) throws CalFacadeException {
    final BwEvent fb = svci.getScheduler()
                           .getFreeBusy(null, svci.getPrincipal(),
                                        start, end,
                                        org.makeOrganizer(),
                                        uid, excludeUid);

    final Collection<BwFreeBusyComponent> times = fb.getFreeBusyPeriods();

    if (!Util.isEmpty(times)) {
      for (final BwFreeBusyComponent fbc: times) {
        if (fbc.getType() != BwFreeBusyComponent.typeFree) {
          final Collection<Period> periods = fbc.getPeriods();

          if (!Util.isEmpty(periods)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /** Make a new event for newCol from the incoming request
   *
   * @param svci service interface
   * @param newCol - path for new copy
   * @param inEi incoming request
   * @param attUri - our attendee uri
   * @return event added or null if not added
   */
  private EventInfo newAttendeeCopy(final CalSvcI svci,
                                   final String newCol,
                                   final EventInfo inEi,
                                   final String attUri) {
    final SchedulingIntf sched = (SchedulingIntf)svci.getScheduler();

    // Adding a copy
    final EventInfo calEi = sched.copyEventInfo(inEi, svci.getPrincipal());
    final BwEvent calEv = calEi.getEvent();
    //String ridStr = calEv.getRecurrenceId();

    if (!initAttendeeCopy(svci, newCol, calEv, attUri)) {
      return null;
    }

    if (calEi.getNumOverrides() == 0) {
      return calEi;
    }

    for (final EventInfo ei: calEi.getOverrides()) {
      if (!initAttendeeCopy(svci, newCol, ei.getEvent(), attUri)) {
        return null;
      }
    }

    return calEi;
  }

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
  private boolean initAttendeeCopy(final CalSvcI svci,
                                   final String newColPath,
                                   final BwEvent ev,
                                   final String uri) {
    ev.setColPath(newColPath);

    ev.setAttendeeSchedulingObject(true);
    ev.setOrganizerSchedulingObject(false);

    if (!(ev instanceof BwEventProxy) &&
        ev.getSuppressed()) {
      return true;
    }

    final Participant part = ev.getSchedulingInfo()
                               .findParticipant(uri);

    if (part == null) {
      // Error?
      if (debug()) {
        debug("Schedule - no attendee with uri {} for {}",
              uri, svci.getPrincipal().getPrincipalRef());
      }

      return false;
    }

    //att.setScheduleStatus(IcalDefs.deliveryStatusSuccess);
    if (part.getParticipationStatus().equals(IcalDefs.partstatValNeedsAction)) {
      ev.setTransparency(IcalDefs.transparencyTransparent);

      // Apple ical seems to expect an x-prop.
      //ev.addXproperty(new BwXproperty(BwXproperty.appleNeedsReply,
      //                                null, "TRUE"));

    }

    return true;
  }

  private boolean updateAttendeePollCopy(final EventInfo ourCopy,
                                         final EventInfo inCopy,
                                         final String attUri) throws CalFacadeException {
    /* Copy VPOLL status into our copy.
       Copy VPOLL winner if set
       Update the actual items in case they changed
       Copy VVOTER status of everybody else - should we preserve our own?
     */

    try {
      final BwEvent ourEv = ourCopy.getEvent();
      final BwEvent inEv = inCopy.getEvent();
      final boolean statusUpdate;

      if (inEv.getScheduleMethod() ==
              ScheduleMethods.methodTypeRequest) {
        if (debug()) {
          debug("Update the poll common fields");
        }
        if (!updateAttendeeFields(ourCopy, inCopy, attUri)) {
          return false;
        }
        statusUpdate = false;
      } else if (inEv.getScheduleMethod() !=
              ScheduleMethods.methodTypePollStatus) {
        if (debug()) {
          debug("Bad method " + inEv.getScheduleMethod());
        }
        return false;
      } else {
        statusUpdate = true;
      }

      // Now update the vpoll items.

      final ChangeTable chg = ourCopy.getChangeset(getPrincipalHref());

      chg.changed(PropertyInfoIndex.STATUS, ourEv.getStatus(),
                  inEv.getStatus());
      ourEv.setStatus(inEv.getStatus());

      final Integer pw = inEv.getPollWinner();

      if (pw != null) {
        chg.changed(PropertyInfoIndex.POLL_WINNER, ourEv.getPollWinner(),
                    inEv.getPollWinner());
        ourEv.setPollWinner(pw);
      }

      final var ourSi = ourEv.getSchedulingInfo();
      ourSi.clearParticipants();

      for (final var a: inEv.getSchedulingInfo().getParticipants()) {
        ourSi.copyParticipant(a);
      }

      if (!statusUpdate) {
        ourEv.clearPollItems();

        for (final String s : inEv.getPollItems()) {
          ourEv.addPollItem(s);
          chg.addValue(PropertyInfoIndex.POLL_ITEM, s);
        }
      }
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
    return true;
  }

  /** Update our (the attendees) copy of the event from the inbox copy. We
   * should have received cancellations for any instances we are no longer
   * attending. This could include extra instances (fewer exdates) for which we
   * have received no notifications.
   *
   * <p>The partstat on those instances should be currently set to NEEDS-ACTION
   * and we need to add some information to the event to allow us to highlight
   * those instances.
   *
   * <p>This may be why Apple is adding a needs reply x-prop?
   *
   * @param ourCopy attendees copy
   * @param inCopy inbox version
   * @param attUri - our attendee uri
   * @return boolean true for OK
   */
  private boolean updateAttendeeCopy(final EventInfo ourCopy,
                                     final EventInfo inCopy,
                                     final String attUri) {
    /* Update from an incoming inbox event. The incoming event may be a partial
     * recurring event, that is we may have a suppressed master and an incomplete
     * set of overrides.
     *
     * If the master is suppressed we simply update from each supplied override.
     *
     * We do not remove overrides if they are not in the incoming event. We need
     * an explicit CANCEL.
     */

    final BwEvent ourEv = ourCopy.getEvent();
    final BwEvent inEv = inCopy.getEvent();

    final boolean ourMaster = !(ourEv instanceof BwEventProxy);
    final boolean inMaster = !(inEv instanceof BwEventProxy);

    if (ourMaster != inMaster) {
      throw new RuntimeException("Only one master event for updateAttendeeCopy");
    }

    final boolean ourRecurMaster = ourMaster && ourEv.getRecurring();

    if (!inMaster || !inEv.getSuppressed()) {
      // Not a suppressed master event

      if (debug()) {
        debug("Update the master event or single recurrence");
      }
      if (!updateAttendeeFields(ourCopy, inCopy, attUri)) {
        return false;
      }
    }

    if (!ourRecurMaster) {
      return true;
    }

    if (inCopy.getOverrides() != null) {
      // Go through all the overrides

      Collection<Recurrence> recurrences = null;

      for (final EventInfo inOvei: inCopy.getOverrides()) {
        final BwEvent inOv = inOvei.getEvent();

        final String rid = inOv.getRecurrenceId();

        final EventInfo ourOvei = findOverride(ourCopy, rid);

        if (ourOvei.getEvent().unsaved()) {
          // New override - add rdate if not in current recurrence set

          if (recurrences == null) {
            recurrences = getRecurrences(ourCopy);
          }

          Recurrence rec = null;

          for (final Recurrence r: recurrences) {
            if (rid.equals(r.recurrenceId)) {
              rec = r;
              break;
            }
          }

          if (rec == null) {
            // Not in set
            final BwDateTime bwrdt = BwDateTime.fromUTC(rid.length() == 8, rid);
            ourEv.addRdate(bwrdt);
          }
        }

        if (!updateAttendeeCopy(ourOvei, inOvei, attUri)) {
          return false;
        }
      }
    }

    /* The incoming event may have exdates that are not in the
       attendee copy. Remove each of those and add an override cancelling
       that instance.
     */

    final Set<BwDateTime> inExdates = inCopy.getEvent().getExdates();

    if (!Util.isEmpty(inExdates)) {
      final Set<BwDateTime> ourExdates = ourCopy.getEvent().getExdates();

      for (final BwDateTime exdt: inExdates) {
        if (!Util.isEmpty(ourExdates) && ourExdates.contains(exdt)) {
          continue;
        }

        final EventInfo ourOvei = findOverride(ourCopy,
                                               exdt.getDate());

        ourOvei.getEvent().setStatus(BwEvent.statusCancelled);
      }
    }

    return true;
  }

  private EventInfo findOverride(final EventInfo ei,
                                 final String recurrenceId) {
    final EventInfo ovei = ei.findOverride(recurrenceId);

    if (ovei.getEvent().unsaved()) {
      // New override - set start/end based on duration
      final BwDateTime start = BwDateTime.fromUTC(recurrenceId.length() == 8,
                                                  recurrenceId,
                                                  ei.getEvent().getDtstart().getTzid());

      final BwDateTime end = start.addDur(ei.getEvent().getDuration());

      ovei.getEvent().setDtstart(start);
      ovei.getEvent().setDtend(end);
    }

    return ovei;
  }

  private Collection<Recurrence> getRecurrences(final EventInfo ei) {
    final AuthProperties authpars = getSvc().getAuthProperties();
    final int maxYears = authpars.getMaxYears();
    final int maxInstances = authpars.getMaxInstances();

    return RecurUtil.getRecurrences(ei,
                                    maxYears,
                                    maxInstances,
                                    null,
                                    null);
  }

  private boolean updateAttendeeFields(final EventInfo ourCopy,
                                       final EventInfo inBoxEi,
                                       final String attUri) {
    final BwEvent ourEv = ourCopy.getEvent();
    final BwEvent inEv = inBoxEi.getEvent();
    final var ourSi = ourEv.getSchedulingInfo();
    final var inSi = inEv.getSchedulingInfo();
    boolean flagNeedsReply = false;

    final ChangeTable chg = ourCopy.getChangeset(getPrincipalHref());

    for (final PropertyInfoIndex ipi: PropertyInfoIndex.values()) {
      final BwIcalPropertyInfoEntry bipie = BwIcalPropertyInfo.getPinfo(ipi);

      if (bipie == null) {
        continue;
      }

      if ((ourEv.getEntityType() == IcalDefs.entityTypeEvent) &&
          !bipie.getEventProperty()) {
        continue;
      }

      if ((ourEv.getEntityType() == IcalDefs.entityTypeTodo) &&
          !bipie.getTodoProperty()) {
        continue;
      }

      switch (ipi) {
        case UNKNOWN_PROPERTY:
          break;

        case BUSYTYPE:
          if (chg.changed(ipi,
                          ourEv.getBusyType(),
                          inEv.getBusyType())) {
            ourEv.setBusyType(inEv.getBusyType());
          }
          break;

        case CLASS:
          if (chg.changed(ipi,
                          ourEv.getClassification(), inEv.getClassification())) {
            ourEv.setClassification(inEv.getClassification());
          }
          break;

        case COMPLETED: /* Todo only */
          if (chg.changed(ipi,
                          ourEv.getCompleted(), inEv.getCompleted())) {
            ourEv.setCompleted(inEv.getCompleted());
          }
          break;

        case CREATED:
          break;

        case DESCRIPTION:
          /*
          for (BwLongString s: inEv.getDescriptions()) {
            chg.addValue(Property.DESCRIPTION, s);
          }
          */
          if (chg.changed(ipi,
                          ourEv.getDescription(), inEv.getDescription())) {
            ourEv.setDescription(inEv.getDescription());
          }
          break;

        case DTEND: /* Event only */
        case DUE: /* Todo only */
          BwDateTime dt = inEv.getDtend();
          if (!CalFacadeUtil.eqObjval(ourEv.getDtend(), dt)) {
            ourEv.setDtend(dt);
            chg.changed(ipi, ourEv.getDtend(), dt);
          }

          final char c = inEv.getEndType();
          if (c != ourEv.getEndType()) {
            ourEv.setEndType(c);
            chg.changed(PropertyInfoIndex.END_TYPE,
                        ourEv.getEndType(), c);
          }

          break;

        case DTSTAMP:
          break;

        case DTSTART:
          dt = inEv.getDtstart();
          if (!CalFacadeUtil.eqObjval(ourEv.getDtstart(), dt)) {
            ourEv.setDtstart(dt);
            chg.changed(ipi,
                        ourEv.getDtstart(), dt);
          }
          break;

        case DURATION:
          if (chg.changed(ipi,
                          ourEv.getDuration(), inEv.getDuration())) {
            ourEv.setDuration(inEv.getDuration());
          }
          break;

        case GEO:
          if (chg.changed(ipi,
                          ourEv.getGeo(), inEv.getGeo())) {
            ourEv.setGeo(inEv.getGeo());
          }
          break;

        case LAST_MODIFIED:
          break;

        case LOCATION:
          if (chg.changed(ipi,
                          ourEv.getLocation(), inEv.getLocation())) {
            ourEv.setLocation((BwLocation)inEv.getLocation().clone());
          }
          break;

        case ORGANIZER:
          final var inOwner = inSi.getSchedulingOwner();
          if (inOwner != null) {
            ourSi.copySchedulingOwner(inOwner);
          }
          break;

        case PRIORITY:
          if (chg.changed(ipi,
                          ourEv.getPriority(), inEv.getPriority())) {
            ourEv.setPriority(inEv.getPriority());
          }
          break;

        case RECURRENCE_ID:
          break;

        case SEQUENCE:
          if (chg.changed(ipi,
                          ourEv.getSequence(), inEv.getSequence())) {
            ourEv.setSequence(inEv.getSequence());
          }
          break;

        case STATUS:
          if (chg.changed(ipi,
                          ourEv.getStatus(), inEv.getStatus())) {
            ourEv.setStatus(inEv.getStatus());
          }
          break;

        case SUMMARY:
          /*
          for (BwString s: inEv.getSummaries()) {
            chg.addValue(Property.SUMMARY, s);
          }
          */
          if (chg.changed(ipi,
                          ourEv.getSummary(), inEv.getSummary())) {
            ourEv.setSummary(inEv.getSummary());
          }
          break;

        case PERCENT_COMPLETE: /* Todo only */
          if (chg.changed(ipi,
                          ourEv.getPercentComplete(), inEv.getPercentComplete())) {
            ourEv.setPercentComplete(inEv.getPercentComplete());
          }
          break;

        case UID:
          break;

        case URL:
          if (chg.changed(ipi,
                          ourEv.getLink(), inEv.getLink())) {
            ourEv.setLink(inEv.getLink());
          }
          break;

        case TRANSP: /* Event only - done with attendee */
          break;

        /* ---------------------------- Multi valued --------------- */

        case ATTACH:
          break;

        case ATTENDEE :
          String transparency = ourEv.getTransparency();

          Participant ourPart = null;

          for (final var inPart: inSi.getRecipientParticipants().values()) {
            final var part = ourSi.copyParticipant(inPart);

            part.setScheduleStatus(null);
            final String inAttUri = inPart.getCalendarAddress();

            if (inAttUri.equals(attUri)) {
              // It's ours
              ourPart = part;

              if ((ourPart.getParticipationStatus() == null) ||
                      ourPart.getParticipationStatus().equals(
                          IcalDefs.partstatValNeedsAction)) {
                transparency = IcalDefs.transparencyTransparent;

                // Apple ical seems to expect an x-prop.
                flagNeedsReply = true;
              }

              //att.setScheduleStatus(IcalDefs.deliveryStatusSuccess);
            }
          }

          if (ourPart == null) {
            // Error?
            if (debug()) {
              debug("InSchedule - no attendee for " + ourEv.getOwnerHref());
            }

            return false;
          }

          /* transparency is set by above */

          if (chg.changed(PropertyInfoIndex.TRANSP,
                          ourEv.getTransparency(), transparency)) {
            ourEv.setTransparency(transparency);
          }

          break;

        case CATEGORIES:
          if (!Util.isEmpty(inEv.getCategories())) {
            for (final BwCategory cat: inEv.getCategories()) {
              chg.addValue(ipi, cat);
            }
          }
          break;

        case COMMENT:
          for (final BwString s: inEv.getComments()) {
            chg.addValue(ipi, s);
          }
          break;

        case CONTACT:
          for (final BwContact ct: inEv.getContacts()) {
            chg.addValue(ipi, ct.clone());
          }
          break;

        case EXDATE:
          // Don't updaye exdate - we add cancelled overrides
          break;

        case EXRULE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (final String s: inEv.getExrules()) {
            chg.addValue(ipi, s);
          }
          break;

        case REQUEST_STATUS:
          break;

        case RELATED_TO:
          if (chg.changed(ipi,
                          ourEv.getRelatedTo(), inEv.getRelatedTo())) {
            ourEv.setRelatedTo(inEv.getRelatedTo());
          }
          break;

        case RESOURCES:
          for (final BwString bs: inEv.getResources()) {
            chg.addValue(ipi, bs);
          }
          break;

        case RDATE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (final BwDateTime bdt: inEv.getRdates()) {
            chg.addValue(ipi, bdt);
          }
          break;

        case RRULE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (final String s: inEv.getRrules()) {
            chg.addValue(ipi, s);
          }
          break;

        case XPROP:
          for (final BwXproperty x: inEv.getXproperties()) {
            chg.addValue(ipi, x);
          }
          break;

        /* -------------- Other event/task fields ------------------ */
        case SCHEDULE_METHOD:
        case SCHEDULE_STATE:
        case SCHEDULE_TAG:
        case TRIGGER_DATE_TIME:
        case URI:

        /* -------------- Other non-event, non-todo ---------------- */

        case FREEBUSY:
        case TZID:
        case TZNAME:
        case TZOFFSETFROM:
        case TZOFFSETTO:
        case TZURL:
        case ACTION:
        case REPEAT:
        case TRIGGER:
          break;

        case COLLECTION: // non ical
        case COST: // non ical
        case CREATOR: // non ical
        case OWNER: // non ical
        case ENTITY_TYPE: // non ical
          break;

        case VALARM: // Component
          break;

        // parameters
        case CN:
        case EMAIL:
        case LANG:
        case TZIDPAR:
        case VALUE:
          break;

        case PUBLISH_URL:
        case POLL_ITEM_ID:
        case END_TYPE:
        case ETAG:
        case HREF:
        case XBEDEWORK_COST:
        case CALSCALE:
        case METHOD:
        case PRODID:
        case VERSION:
        case ACL:
        case AFFECTS_FREE_BUSY:
        case ALIAS_URI:
        case ATTENDEE_SCHEDULING_OBJECT:
        case CALTYPE:
        case COL_PROPERTIES:
        case COLPATH:
        case CTOKEN:
        case DISPLAY:
        case DOCTYPE:
        case EVENTREG_END:
        case EVENTREG_MAX_TICKETS:
        case EVENTREG_MAX_TICKETS_PER_USER:
        case EVENTREG_START:
        case EVENTREG_WAIT_LIST_LIMIT:
        case FILTER_EXPR:
        case IGNORE_TRANSP:
        case IMAGE:
        case INDEX_END:
        case INDEX_START:
        case INSTANCE:
        case LAST_REFRESH:
        case LAST_REFRESH_STATUS:
        case LOCATION_HREF:
        case LOCATION_STR:
          break;

        // Internal bedework properties
        case CALSUITE:
        case CTAG:
        case DELETED:
        case FLOATING:
        case LASTMODSEQ:
        case LOCAL:
        case MASTER:
        case NAME:
        case NEXT_TRIGGER_DATE_TIME:
        case NO_START:
        case ORGANIZER_SCHEDULING_OBJECT:
        case ORIGINATOR:
        case OVERRIDE:
        case PARAMETERS:
        case PUBLIC:
        case RECIPIENT:
        case RECURRING:
        case REFRESH_RATE:
        case REMOTE_ID:
        case REMOTE_PW:
        case SUGGESTED_TO:
        case TAG:
        case TARGET:
        case THUMBIMAGE:
        case TOMBSTONED:
        case TOPICAL_AREA:
        case UNREMOVEABLE:
        case UTC:
        case VIEW:
        case VOTER:
        case VPATH:
        case X_BEDEWORK_CATEGORIES:
        case X_BEDEWORK_CONTACT:
        case X_BEDEWORK_LOCATION:

        // Contact fields
        case CONTACT_ALL:
        case PHONE:

        // Location fields
        case ACCESSIBLE_FLD:
        case ADDRESS:
        case ADDRESS_FLD:
        case ALTADDRESS_FLD:
        case CITY_FLD:
        case CODEIDX_FLD:
        case COUNTRY_FLD:
        case GEOURI_FLD:
        case LOC_ALL:
        case LOC_COMBINED_VALUES:
        case LOC_DONOTUSE_FLD:
        case LOC_KEYS_FLD:
        case LOCTYPE_FLD:
        case ROOM_FLD:
        case STATE_FLD:
        case STREET_FLD:
        case SUBADDRESS:
        case SUB1_FLD:
        case SUB2_FLD:
        case ZIP_FLD:

        // Participant fields
        case EXPECT_REPLY:
        case KIND:
        case MEMBER_OF:
        case PARTICIPANT_TYPE:
        case PARTICIPATION_STATUS:
        case SCHEDULING_FORCE_SEND:
        case SCHEDULING_SEQUENCE:
        case SCHEDULING_STATUS:

        // Fields stored as x-properties
        case ACCEPT_RESPONSE:
        case PARTICIPANT:
        case POLL_COMPLETION:
        case POLL_ITEM:
        case POLL_MODE:
        case POLL_PROPERTIES:
        case POLL_WINNER:
          break;

        default:
          warn("Not handling icalendar property " + ipi);
          /* Seen warnings for these:
             CONCEPT
             ESTIMATED_DURATION
             RANGE
           */
      } // switch
    } // for

    /* ------------------- Cost -------------------- */

    if (chg.changed(PropertyInfoIndex.COST,
                    ourEv.getCost(), inEv.getCost())) {
      ourEv.setCost(inEv.getCost());
    }

    /* Now see if we need to flag a schedule-tag change. We do so only if
     * a. A property other than the attendee changed
     * b. An attendee was added or removed
     */
    final Collection<ChangeTableEntry> changes = chg.getEntries();
    ChangeTableEntry attChanges = null;

    ourEv.setSignificantChange(false);

    for (final ChangeTableEntry cte: changes) {
      if (!cte.getChanged()) {
        continue;
      }

      if (cte.getIndex() == PropertyInfoIndex.ATTENDEE) {
        attChanges = cte;
        continue;
      }

      ourEv.setSignificantChange(true);
    }

    if (debug()) {
      debug("After change check getSignificantChange=" +
            ourEv.getSignificantChange());
    }

    if (flagNeedsReply) {
      // Apple ical seems to expect an x-prop.
      //chg.addValue(PropertyInfoIndex.XPROP,
      //             new BwXproperty(BwXproperty.appleNeedsReply,
      //                             null, "TRUE"));
    }

    chg.processChanges(ourEv, true, true);

    if (debug()) {
      trace(chg.toString());
    }

    /* The attendee change entry will now reflect the changes made to the
     * attendee list. See if any significant change was made there.
     */
    if (attChanges != null) {
      if (!Util.isEmpty(attChanges.getAddedValues()) ||
          !Util.isEmpty(attChanges.getRemovedValues())) {
        ourEv.setSignificantChange(true);
      } else {
        /* TODO - go through the changed entries and look for our entry. See
         * if we are being asked to reply - this can probably be done earlier.
         */

      }
    }

    if (debug()) {
      debug("After attendee change check getSignificantChange=" +
            ourEv.getSignificantChange());
    }

    return true;
  }

  private boolean anySignificantChange(final EventInfo ei) {
    if (ei.getEvent().getSignificantChange()) {
      return true;
    }

    /* Check any overrides */
    if (ei.getOverridesChanged()) {
      return true;
    }

    if (Util.isEmpty(ei.getOverrides())) {
      return false;
    }

    for (final EventInfo oei: ei.getOverrides()) {
      if (oei.getEvent().getSignificantChange()) {
        return true;
      }
    }

    return false;
  }
}