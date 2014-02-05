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
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.CalSvcI;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.Recurrence;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.Period;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Handles incoming method REQUEST scheduling messages.
 *
 * @author Mike Douglass
 */
public class InRequest extends InProcessor {
  /**
   * @param svci
   */
  public InRequest(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei
   * @return ScheduleResult
   * @throws CalFacadeException
   */
  @Override
  public ProcessResult process(final EventInfo ei) throws CalFacadeException {
    /* We are acting as an attendee getting a request from the organizer, either
     * a first invitation or an update
     */

    ProcessResult pr = new ProcessResult();

    BwPreferences prefs = getSvc().getPrefsHandler().get();
    BwEvent ev = ei.getEvent();
    String owner = ev.getOwnerHref();

    boolean schedAssistant = ev.isSchedulingAssistant();

    if (debug) {
      trace("InSchedule schedAssistant = " + schedAssistant);
    }

    /* First we save or update the event in the users default scheduling calendar
     */

    SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

    String uri = getSvc().getDirectories().principalToCaladdr(getSvc().getPrincipal());
    String colPath = null;
    EventInfo ourCopy = null;
    boolean adding = false;

    check: {
      ourCopy = sched.getStoredMeeting(ev);

      if (ourCopy != null) {
        /* Update */

        if (debug) {
          trace("InSchedule update for " + owner);
        }

        colPath = ourCopy.getEvent().getColPath();
        if (!updateAttendeeCopy(ourCopy, ei, uri)) {
          break check;
        }

        pr.removeInboxEntry = !anySignificantChange(ourCopy);
      } else {
        /* New invitation - Save in default */

        adding = true;

        if (debug) {
          trace("InSchedule add for " + owner);
        }

        String prefSched = getSvc().getCalendarsHandler().
                getPreferred(IcalDefs.entityTypeIcalNames[ev.getEntityType()]);
        if (prefSched == null) {
          // SCHED - status = no default collection
          if (debug) {
            trace("InSchedule - no default collection for " + owner);
          }

          // XXX set error code in request status

          pr.removeInboxEntry = true;
          return pr;
        }

        ourCopy = newAttendeeCopy(getSvc(), prefSched, ei, uri);
        if (ourCopy == null) {
          if (debug) {
            trace("InSchedule - unable to add to calendar for " + owner);
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

    boolean doAutoRespond = !pr.removeInboxEntry &&
                            !schedAssistant &&
                            prefs.getScheduleAutoRespond();

    if (doAutoRespond) {
      if (debug) {
        trace("InSchedule - auto responding for " + owner);
      }

      noInvites = !autoRespond(getSvc(), ourCopy, ei,
                               prefs.getScheduleDoubleBook(), uri);
    }

    if (adding) {
      String namePrefix = ourCopy.getEvent().getUid();

      pr.sr.errorCode = sched.addEvent(ourCopy, namePrefix,
                                       BwCalendar.calTypeCalendarCollection,
                                       noInvites);
      if (pr.sr.errorCode != null) {
        if (debug) {
          trace("Schedule - error " + pr.sr.errorCode +
                " adding event for " + owner);
        }

        return pr;
      }
    } else {
      UpdateResult ur = getSvc().getEventsHandler().update(ourCopy,
                                                       noInvites,
                                                       null);

      if (ur.schedulingResult != null) {
        pr.sr = ur.schedulingResult;
      }
    }

    pr.attendeeAccepting =
      !Util.isEmpty(ev.getXproperties(
              BwXproperty.bedeworkSchedulingReplyUpdate));

    return pr;
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private boolean autoRespond(final CalSvcI svci,
                              final EventInfo ourCopy,
                              final EventInfo inboxEi,
                              final boolean doubleBookOk,
                              final String uri) throws CalFacadeException {
    BwEvent inboxEv = inboxEi.getEvent();
    String owner = inboxEv.getOwnerHref();

    if (ourCopy == null) {
      // Error - deleted while we did this?
      if (debug) {
        trace("InSchedule - no event for auto respond for " + owner);
      }

      return false;
    }

    BwOrganizer org = new BwOrganizer();
    org.setOrganizerUri(uri);

    BwAttendee att = null;

    BwEvent ourEvent = ourCopy.getEvent();

    if (!ourEvent.getRecurring()) {
      att = ourEvent.findAttendee(uri);

      if (att == null) {
        // Error?
        if (debug) {
          trace("InSchedule - no attendee on our copy for auto respond for " +
                owner);
        }

        return false;
      }

      if (debug) {
        trace("send response event for " + owner + " " + inboxEv.getName());
      }

      att.setRsvp(false); // We're about to reply.

      String partStat = IcalDefs.partstatValAccepted;

      ourEvent.removeXproperties(BwXproperty.appleNeedsReply);

      if (!doubleBookOk) {
        // See if there are any events booked during this time.
        if (checkBusy(svci,
                      ourEvent.getUid(),
                      inboxEv.getDtstart(), inboxEv.getDtend(), org,
                      inboxEv.getUid())) {
          partStat = IcalDefs.partstatValDeclined;
        } else {
          ourEvent.setTransparency(IcalDefs.transparencyOpaque);
        }
      }

      ourEvent.setScheduleMethod(ScheduleMethods.methodTypeReply);
      att = (BwAttendee)att.clone();
      ourEvent.removeAttendee(att);
      ourEvent.addAttendee(att);

      att.setPartstat(partStat);

      return true;
    }

    // Recurring event - do the above per recurrence

    AuthProperties authpars = svci.getAuthProperties();
    int maxYears = authpars.getMaxYears();
    int maxInstances = authpars.getMaxInstances();

    Collection<Recurrence> recurrences = RecurUtil.getRecurrences(inboxEi,
                                                                  maxYears,
                                                                  maxInstances);

    if (Util.isEmpty(recurrences)) {
      return false;
    }

    if (debug) {
      trace("autoRespond: " + recurrences.size() + " instances");
    }

    /* Assume accept and then override with declines - assuming we're in the
     * master
     */

    ourEvent.removeXproperties(BwXproperty.appleNeedsReply);
    boolean masterSupressed = true;

    if (!inboxEv.getSuppressed()) {
      masterSupressed = false;
      att = ourEvent.findAttendee(uri);

      if (att != null) {
        // It should never be null

        att.setRsvp(false);
        att.setPartstat(IcalDefs.partstatValAccepted);
      }
      ourEvent.setTransparency(IcalDefs.transparencyOpaque);
    }

    RecurInfo rinfo = checkBusy(svci,
                                ourEvent.getUid(),
                                recurrences, org,
                                inboxEv.getUid(), doubleBookOk);

    /* If we have a master then we should set its status to cover the largest
     * number of overrides - if any.
     *
     * The easiest case is no overrides and all accepted or all declined.
     *
     * Otherwise we have to update or add overrides.
     */

    boolean allAcceptedOrDeclined = (rinfo.availCt == 0) || (rinfo.busyCt == 0);
    boolean masterAccept = rinfo.availCt >= rinfo.busyCt;
    String masterPartStat;

    if (masterAccept) {
      masterPartStat = IcalDefs.partstatValAccepted;
    } else {
      masterPartStat = IcalDefs.partstatValDeclined;
    }

    if (!masterSupressed) {
      att = ourEvent.findAttendee(uri);

      if (!masterPartStat.equals(att.getPartstat())) {
        att.setPartstat(masterPartStat);
      }

      if (masterAccept) {
        ourEvent.setTransparency(IcalDefs.transparencyOpaque);
      } else {
        ourEvent.setTransparency(IcalDefs.transparencyTransparent);
      }
    }

    if (allAcceptedOrDeclined) {
      // Ensure any overrides have the same status
      for (EventInfo oei: ourCopy.getOverrides()) {
        BwEvent override = oei.getEvent();

        att = override.findAttendee(uri);
        att = (BwAttendee)att.clone();
        att.setRsvp(false); // We're about to reply.

        override.removeAttendee(att);
        override.addAttendee(att);

        if (!masterPartStat.equals(att.getPartstat())) {
          att.setPartstat(masterPartStat);
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

    for (RecurrenceInfo ri: rinfo.ris) {
      Recurrence r = ri.r;

      //if (!masterSupressed && !ri.busy && (r.override == null)) {
      //  // fine
      //  continue;
      //}

      boolean mustOverride = masterAccept == ri.busy;

      EventInfo oei = ourCopy.findOverride(r.recurrenceId,
                                           mustOverride);
      if (oei == null) {
        continue;
      }

      BwEvent override = oei.getEvent();

      if (((BwEventProxy)override).getRef().unsaved()) {
        override.setDtstart(r.start);
        override.setDtend(r.end);
        override.setDuration(BwDateTime.makeDuration(r.start, r.end).toString());
      }

      att = override.findAttendee(uri);
      if (att == null) {
        // Guess we weren't invited
        continue;
      }

      att = (BwAttendee)att.clone();
      att.setRsvp(false); // We're about to reply.

      override.removeAttendee(att);
      override.addAttendee(att);

      if (!ri.busy) {
        att.setPartstat(IcalDefs.partstatValAccepted);
        override.setTransparency(IcalDefs.transparencyOpaque);
      } else {
        att.setPartstat(IcalDefs.partstatValDeclined);
        override.setTransparency(IcalDefs.transparencyTransparent);
      }

      override.removeXproperties(BwXproperty.appleNeedsReply);
    }

    return true;
  }

  private class RecurrenceInfo {
    Recurrence r;
    boolean busy;

    RecurrenceInfo(final Recurrence r,
                   final boolean busy) {
      this.r = r;
      this.busy = busy;
    }
  }

  private class RecurInfo {
    List<RecurrenceInfo> ris = new ArrayList<RecurrenceInfo>();
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
                              final BwOrganizer org,
                              final String uid,
                              final boolean doubleBookOk) throws CalFacadeException {
    /* TODO
     * We should chunk up the freebusy into fewer requests over longer periods.
     * That means we have to figure out the overlaps with the returned info ourselves.
     *
     * If we don't do that we could fire off 100s of freebusy queries.
     */
    RecurInfo res = new RecurInfo();

    for (Recurrence r: recurrences) {
      boolean busy = false;

      if (!doubleBookOk) {
        // See if there are any events booked during this time.
        busy = checkBusy(svci,
                         excludeUid,
                         r.start, r.end, org, uid);
        if (debug) {
          trace("busy=" + busy + " for " + r.start + " to " + r.end);
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
                            final BwOrganizer org,
                            final String uid) throws CalFacadeException {
    BwEvent fb = svci.getScheduler().getFreeBusy(null, svci.getPrincipal(),
                                                 start, end, org,
                                                 uid, excludeUid);

    Collection<BwFreeBusyComponent> times = fb.getFreeBusyPeriods();

    if (!Util.isEmpty(times)) {
      for (BwFreeBusyComponent fbc: times) {
        if (fbc.getType() != BwFreeBusyComponent.typeFree) {
          Collection<Period> periods = fbc.getPeriods();

          if (!Util.isEmpty(periods)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /** Add the event to newCol from the incoming request
   *
   * @param svci
   * @param newCol - path for new copy
   * @param inEi
   * @param attUri - our attendee uri
   * @return event added or null if not added
   * @throws CalFacadeException
   */
  private EventInfo newAttendeeCopy(final CalSvcI svci,
                                   final String newCol,
                                   final EventInfo inEi,
                                   final String attUri) throws CalFacadeException {
    SchedulingIntf sched = (SchedulingIntf)svci.getScheduler();

    // Adding a copy
    EventInfo calEi = sched.copyEventInfo(inEi, svci.getPrincipal());
    BwEvent calEv = calEi.getEvent();
    //String ridStr = calEv.getRecurrenceId();

    if (!initAttendeeCopy(svci, newCol, calEv, attUri)) {
      return null;
    }

    if (calEi.getNumOverrides() == 0) {
      return calEi;
    }

    for (EventInfo ei: calEi.getOverrides()) {
      if (!initAttendeeCopy(svci, newCol, ei.getEvent(), attUri)) {
        return null;
      }
    }

    return calEi;
  }

  private boolean initAttendeeCopy(final CalSvcI svci,
                                   final String newColPath,
                                   final BwEvent ev,
                                   final String uri) throws CalFacadeException {
    ev.setColPath(newColPath);

    ev.setAttendeeSchedulingObject(true);
    ev.setOrganizerSchedulingObject(false);

    if (!(ev instanceof BwEventProxy) &&
        ev.getSuppressed()) {
      return true;
    }

    BwAttendee att = ev.findAttendee(uri);

    if (att == null) {
      // Error?
      if (debug) {
        trace("Schedule - no attendee with uri " + uri +
              " for " + svci.getPrincipal().getPrincipalRef());
      }

      return false;
    }

    //att.setScheduleStatus(IcalDefs.deliveryStatusSuccess);
    if ((att.getPartstat() == null) ||
        att.getPartstat().equals(IcalDefs.partstatValNeedsAction)) {
      if (att.getPartstat() == null) {
        att.setPartstat(IcalDefs.partstatValNeedsAction);
      }

      ev.setTransparency(IcalDefs.transparencyTransparent);

      // Apple ical seems to expect an x-prop.
      //ev.addXproperty(new BwXproperty(BwXproperty.appleNeedsReply,
      //                                null, "TRUE"));

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
   * @param ourCopy
   * @param inCopy
   * @param attUri - our attendee uri
   * @return boolean true for OK
   * @throws CalFacadeException
   */
  private boolean updateAttendeeCopy(final EventInfo ourCopy,
                                     final EventInfo inCopy,
                                     final String attUri) throws CalFacadeException {
    /* Update from an incoming inbox event. The incoming event may be a partial
     * recurring event, that is we may have a suppressed master and an incomplete
     * set of overrides.
     *
     * If the master is suppressed we simply update from each supplied override.
     *
     * We do not remove overrides if they are not in the incoming event. We need
     * an explicit CANCEL.
     */

    BwEvent ourEv = ourCopy.getEvent();
    BwEvent inEv = inCopy.getEvent();

    boolean ourMaster = !(ourEv instanceof BwEventProxy);
    boolean inMaster = !(inEv instanceof BwEventProxy);

    if (ourMaster != inMaster) {
      throw new CalFacadeException("Only one master event for updateAttendeeCopy");
    }

    boolean ourRecurMaster = ourMaster && ourEv.getRecurring();

    if (!inMaster || !inEv.getSuppressed()) {
      // Not a suppressed master event

      if (debug) {
        trace("Update the master event or single recurrence");
      }
      if (!updateAttendeeFields(ourCopy, inCopy, attUri)) {
        return false;
      }
    }

    if (ourRecurMaster && (inCopy.getOverrides() != null)) {
      // Go through all the overrides

      Collection<Recurrence> recurrences = null;

      for (EventInfo inOvei: inCopy.getOverrides()) {
        BwEvent inOv = inOvei.getEvent();

        String rid = inOv.getRecurrenceId();

        EventInfo ourOvei = ourCopy.findOverride(rid);

        if (ourOvei.getEvent().unsaved()) {
          // New override - add rdate if not in current recurrence set

          if (recurrences == null) {
            recurrences = getRecurrences(ourCopy);
          }

          Recurrence rec = null;

          for (Recurrence r: recurrences) {
            if (rid.equals(r.recurrenceId)) {
              rec = r;
              break;
            }
          }

          if (rec == null) {
            // Not in set
            BwDateTime bwrdt = BwDateTime.fromUTC(rid.length() == 8, rid);
            ourEv.addRdate(bwrdt);
          }
        }

        if (!updateAttendeeCopy(ourOvei, inOvei, attUri)) {
          return false;
        }
      }
    }

    return true;
  }

  private Collection<Recurrence> getRecurrences(final EventInfo ei) throws CalFacadeException {
    AuthProperties authpars = getSvc().getAuthProperties();
    int maxYears = authpars.getMaxYears();
    int maxInstances = authpars.getMaxInstances();

    return RecurUtil.getRecurrences(ei,
                                    maxYears,
                                    maxInstances);
  }

  private boolean updateAttendeeFields(final EventInfo ourCopy,
                                       final EventInfo inBoxEi,
                                       final String attUri) throws CalFacadeException {
    BwEvent ourEv = ourCopy.getEvent();
    BwEvent inEv = inBoxEi.getEvent();
    boolean flagNeedsReply = false;

    ChangeTable chg = ourCopy.getChangeset(getPrincipalHref());

    for (PropertyInfoIndex ipi: PropertyInfoIndex.values()) {
      BwIcalPropertyInfoEntry bipie = BwIcalPropertyInfo.getPinfo(ipi);

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

          char c = inEv.getEndType();
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
          if (chg.changed(ipi,
                          ourEv.getOrganizer(), inEv.getOrganizer())) {
            ourEv.setOrganizer((BwOrganizer)inEv.getOrganizer().clone());
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

          BwAttendee ourAtt = null;

          for (BwAttendee inAtt: inEv.getAttendees()) {
            BwAttendee att = (BwAttendee)inAtt.clone();
            att.setScheduleStatus(null);
            String inAttUri = att.getAttendeeUri();

            if (inAttUri.equals(attUri)) {
              // It's ours
              ourAtt = att;

              if ((att.getPartstat() == null) ||
                  att.getPartstat().equals(IcalDefs.partstatValNeedsAction)) {
                transparency = IcalDefs.transparencyTransparent;

                // Apple ical seems to expect an x-prop.
                flagNeedsReply = true;
              }

              //att.setScheduleStatus(IcalDefs.deliveryStatusSuccess);
            }

            /* See if it's in the current set and if anything significant changed

            for (BwAttendee calAtt: ourEv.getAttendees()) {
              if (calAtt.getAttendeeUri().equals(inAttUri)) {
                if (calAtt.changedBy(inAtt, false)) {
                  ourEv.setSignificantChange(true);
                }
              }
            }*/
            chg.addValue(PropertyInfoIndex.ATTENDEE, att);
          }

          if (ourAtt == null) {
            // Error?
            if (debug) {
              trace("InSchedule - no attendee for " + ourEv.getOwnerHref());
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
            for (BwCategory cat: inEv.getCategories()) {
              chg.addValue(ipi, cat);
            }
          }
          break;

        case COMMENT:
          for (BwString s: inEv.getComments()) {
            chg.addValue(ipi, s);
          }
          break;

        case CONTACT:
          for (BwContact ct: inEv.getContacts()) {
            chg.addValue(ipi, ct.clone());
          }
          break;

        case EXDATE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (BwDateTime bdt: inEv.getExdates()) {
            chg.addValue(ipi, bdt);
          }
          break;

        case EXRULE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (String s: inEv.getExrules()) {
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
          for (BwString bs: inEv.getResources()) {
            chg.addValue(ipi, bs);
          }
          break;

        case RDATE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (BwDateTime bdt: inEv.getRdates()) {
            chg.addValue(ipi, bdt);
          }
          break;

        case RRULE:
          // Only for master events
          if (ourEv instanceof BwEventProxy) {
            break;
          }

          for (String s: inEv.getRrules()) {
            chg.addValue(ipi, s);
          }
          break;

        case XPROP:
          for (BwXproperty x: inEv.getXproperties()) {
            chg.addValue(ipi, x);
          }
          break;

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

        case LANG: // Param
        case TZIDPAR: // Param
          break;

        default:
          warn("Not handling icalendar property " + ipi);
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
    Collection<ChangeTableEntry> changes = chg.getEntries();
    ChangeTableEntry attChanges = null;

    ourEv.setSignificantChange(false);

    for (ChangeTableEntry cte: changes) {
      if (!cte.getChanged()) {
        continue;
      }

      if (cte.getIndex() == PropertyInfoIndex.ATTENDEE) {
        attChanges = cte;
        continue;
      }

      ourEv.setSignificantChange(true);
    }

    if (debug) {
      trace("After change check getSignificantChange=" +
            ourEv.getSignificantChange());
    }

    if (flagNeedsReply) {
      // Apple ical seems to expect an x-prop.
      //chg.addValue(PropertyInfoIndex.XPROP,
      //             new BwXproperty(BwXproperty.appleNeedsReply,
      //                             null, "TRUE"));
    }

    chg.processChanges(ourEv, true);

    if (debug) {
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

    if (debug) {
      trace("After attendee change check getSignificantChange=" +
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

    for (EventInfo oei: ei.getOverrides()) {
      if (oei.getEvent().getSignificantChange()) {
        return true;
      }
    }

    return false;
  }
}