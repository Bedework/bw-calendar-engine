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
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.icalendar.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;

import org.bedework.access.PrivilegeDefs;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece handles the organizer to attendee methods
 *
 * @author douglm
 *
 */
public abstract class OrganizerSchedulingHandler extends OutboundSchedulingHandler {
  //private static String acceptPartstat = IcalDefs.partstats[IcalDefs.partstatAccepted];

  OrganizerSchedulingHandler(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ScheduleResult schedule(final EventInfo ei,
                                 final int method,
                                 final String recipient,
                                 final String fromAttUri,
                                 final boolean iSchedule) throws CalFacadeException {
    /* A request (that is we are (re)sending a meeting request) or a publish
     *
     * <p>We handle the following iTIP methods<ul>
     * <li>ADD</li>
     * <li>CANCEL</li>
     * <li>DECLINECOUNTER</li>
     * <li>PUBLISH</li>
     * <li>REQUEST</li>
     * </ul>
     *
     * <p>That is, messages from organizer to attendee(s)
     *
     * <pre>
     * Do the usual checks and init
     * For each recipient
     *    If internal to system, add to their inbox
     *    otherwise add to list of external recipients
     *
     * If any external recipients - leave in outbox with unprocessed status.
     * </pre>
     */
    ScheduleResult sr = new ScheduleResult();
    BwEvent ev = ei.getEvent();

    try {
      if (!Icalendar.itipRequestMethodType(method)) {
        sr.errorCode = CalFacadeException.schedulingBadMethod;
        return sr;
      }

      /* For each recipient within this system add the event to their inbox.
       *
       * If there are any external users add it to the outbox and it will be
       * mailed to the recipients.
       */

      int outAccess;
      boolean freeBusyRequest = ev.getEntityType() ==
        IcalDefs.entityTypeFreeAndBusy;

      if (freeBusyRequest) {
        // freebusy
        outAccess = PrivilegeDefs.privScheduleFreeBusy;
      } else {
        outAccess = PrivilegeDefs.privScheduleRequest;
      }

      /* For a request type action the organizer should be the current user. */

      if (!initScheduleEvent(ei, false, iSchedule)) {
        return sr;
      }

      /* Do this here to check we have access. We might need the outbox later
       */
      BwCalendar outBox = null;

      BwPrincipal currentUser = getPrincipal();
      if (!currentUser.getUnauthenticated()) {
        outBox = getSpecialCalendar(getPrincipal(),
                                    BwCalendar.calTypeOutbox,
                                    true, outAccess);
      }

      sendSchedule(sr, ei, recipient, fromAttUri, true);

      if ((sr.errorCode != null) || sr.ignored) {
        return sr;
      }

      //if (freeBusyRequest && !imipFreeBusyOk) {
      if (freeBusyRequest) {
        // Don't ever email freebusy requests
        return sr;
      }

      if (!iSchedule &&
          (outBox != null) &&  // We have something to mail
          (!Util.isEmpty(sr.externalRcs))) {
        sr.errorCode = addToOutBox(ei, outBox, sr.externalRcs);
      }

      return sr;
    } catch (Throwable t) {
      getSvc().rollbackTransaction();
      if (t instanceof CalFacadeException) {
        throw (CalFacadeException)t;
      }
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SchedulingI#declineCounter(org.bedework.calfacade.svc.EventInfo, java.lang.String, org.bedework.calfacade.BwAttendee)
   */
  @Override
  public ScheduleResult declineCounter(final EventInfo ei,
                                       final String comment,
                                       final BwAttendee fromAtt) throws CalFacadeException {
    BwEvent ev = ei.getEvent();

    EventInfo outEi = copyEventInfo(ei, getPrincipal());
    ev = outEi.getEvent();
    ev.setScheduleMethod(ScheduleMethods.methodTypeDeclineCounter);

    if (comment != null) {
      ev.addComment(null, comment);
    }

    return schedule(outEi,
                    ScheduleMethods.methodTypeDeclineCounter,
                    fromAtt.getAttendeeUri(), null, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SchedulingI#processResponse(org.bedework.calfacade.svc.EventInfo)
   * /
  public ScheduleResult processResponse(final EventInfo ei) throws CalFacadeException {
    /* Process a response we as the organizer, or their proxy, received from
     * an attendee
     * /

    ScheduleResult sr = new ScheduleResult();
    BwEvent ev = ei.getEvent();

    EventInfo colEi = getStoredMeeting(ev);

    BwCalendar inbox = getSvc().getCalendarsHandler().get(ev.getColPath());

    /* The event should have a calendar set to the inbox it came from.
     * That inbox may be owned by somebody other than the current user if a
     * calendar user has delegated control of their inbox to some other user
     * e.g. secretary.
     * /

    boolean forceDelete = true;
    boolean acceptingAll = true;

    check: {
      if (colEi == null) {
        // No corresponding stored meeting
        break check;
      }

      if (inbox.getCalType() != BwCalendar.calTypeInbox) {
        sr.errorCode = CalFacadeException.schedulingBadSourceCalendar;
        break check;
      }

      if (ev.getOriginator() == null) {
        sr.errorCode = CalFacadeException.schedulingNoOriginator;
        break check;
      }

      String attUri = null;

      /* Should be exactly one attendee * /
      if (!ev.getSuppressed()) {
        Collection<BwAttendee> atts = ev.getAttendees();
        if ((atts == null) || (atts.size() != 1)) {
          sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
          break check;
        }

        BwAttendee att = atts.iterator().next();
        if (!att.getPartstat().equals(acceptPartstat)) {
          acceptingAll = false;
        }

        attUri = att.getAttendeeUri();
      }

      if (ei.getNumOverrides() > 0) {
        for (EventInfo oei: ei.getOverrides()) {
          ev = oei.getEvent();
          Collection<BwAttendee> atts = ev.getAttendees();
          if ((atts == null) || (atts.size() != 1)) {
            sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
            break check;
          }

          BwAttendee att = atts.iterator().next();
          if (!att.getPartstat().equals(acceptPartstat)) {
            acceptingAll = false;
          }

          if (attUri == null) {
            attUri = att.getAttendeeUri();
          } else if (!attUri.equals(att.getAttendeeUri())) {
            sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
            break check;
          }
        }
      }

      if (attUri == null) {
        sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
        break check;
      }

      /*TODO If the sequence of the incoming event is lower than the sequence on the
       * calendar event we ignore it.
       * /

      if (!updateOrganizerCopy(colEi, ei, attUri, sr, 0)) {
        break check;
      }

      forceDelete = false;
    }

    updateInbox(ei, inbox.getOwnerHref(),
                acceptingAll,       // attendeeAccepting
                forceDelete);  // forceDelete

    return sr;
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  /*
  private boolean updateOrganizerCopy(final EventInfo colEi,
                                      final EventInfo inBoxEi,
                                      final String attUri,
                                      final ScheduleResult sr,
                                      final int action) throws CalFacadeException {
    BwEvent inBoxEv = inBoxEi.getEvent();
    BwEvent calEv = colEi.getEvent();

    /* Only set true if the inbox copy needs to stay as notification.
     * Do not set true for status updates
     * /
    boolean changed = false;

    if (debug) {
      trace("Update for attendee " + attUri);
    }

    if (inBoxEv.getScheduleMethod() != Icalendar.methodTypeReply) {
      sr.errorCode = CalFacadeException.schedulingBadMethod;
      return false;
    }

    /* If the incoming sequence is less than that in the organizers event
     * then ignore the incoming reply?
     * /
    /* Update the participation status from the incoming attendee * /

    BwAttendee calAtt = null;

    if (!inBoxEv.getSuppressed()) {
      calAtt = calEv.findAttendee(attUri);
      if (calAtt == null) {
        if (debug) {
          trace("Not an attendee of " + calEv);
        }
        sr.errorCode = CalFacadeException.schedulingUnknownAttendee;
        sr.extraInfo = attUri;
        return false;
      }

      // For a recurring instance we replace or we update all recurring instances.
      boolean recurringInstance = (calEv instanceof BwEventProxy);
      BwAttendee att = inBoxEv.findAttendee(attUri);

      if (calAtt.changedBy(att)) {
        changed = true;

        if (recurringInstance) {
          calEv.removeAttendee(att);

          calAtt = (BwAttendee)att.clone();
        } else {
          att.copyTo(calAtt);
        }
      }

      calAtt.setScheduleStatus(getRstat(inBoxEv));

      if (recurringInstance) {
        calEv.addAttendee(calAtt);
      }

      // XXX Ensure no name change
      if (calEv instanceof BwEventProxy) {
        BwEventProxy pr = (BwEventProxy)calEv;

        BwEventAnnotation ann = pr.getRef();
        ann.setName(null);
      }
    }

    /* The above changed the master - now we need to update or add any overrides
     * /
    if (calEv.getRecurring() && (inBoxEi.getOverrides() != null)) {
      for (EventInfo oei: inBoxEi.getOverrides()) {
        BwEvent oev = oei.getEvent();
        EventInfo cei = colEi.findOverride(oev.getRecurrenceId() /*, false * /);

        /*
        if (cei == null) {
          // Organizer must have deleted the override.
          if (debug) {
            trace("Skipping missing override " + oev.getRecurrenceId());
          }
          continue;
        }* /

        BwEvent ocalEv = cei.getEvent();

        if (((BwEventProxy)ocalEv).getRef().unsaved()) {
          // New Override
          try {
            String rid = oev.getRecurrenceId();
            Date dt = new DateTime(rid);

            if (calEv.getDtstart().getDateType()) {
              // RECUR - fix all day recurrences sometime
              if (rid.length() > 8) {
                // Try to fix up bad all day recurrence ids. - assume a local timezone
                ((DateTime)dt).setTimeZone(null);
                dt = new Date(dt.toString().substring(0, 8));
              }
            }

            DtStart st = new DtStart(dt);
            String tzid = calEv.getDtstart().getTzid();
            if (tzid != null) {
              TimeZone tz = Timezones.getTz(tzid);
              st.setTimeZone(tz);
            }
            ocalEv.setDtstart(BwDateTime.makeDateTime(st));
            ocalEv.setDuration(calEv.getDuration());
            ocalEv.setDtend(ocalEv.getDtstart().addDur(new Dur(calEv.getDuration())));
          } catch (CalFacadeException cfe) {
            throw cfe;
          } catch (Throwable t) {
            throw new CalFacadeException(t);
          }
        }

        BwAttendee ovatt = oev.findAttendee(attUri);
        calAtt = ocalEv.findAttendee(attUri);

        if (calAtt == null) {
          // Organizer must have removed the attendee.
          if (debug) {
            trace("Skipping override " + attUri +
                  " is not attending");
          }
          continue;
        }

        if (calAtt.changedBy(ovatt)) {
          changed = true;

          ocalEv.removeAttendee(ovatt);

          calAtt = (BwAttendee)ovatt.clone();
          calAtt.setScheduleStatus(getRstat(oev));

          ocalEv.addAttendee(calAtt);
        }
      }
    }

    boolean noinvites = !changed;

    colEi.setReplyUpdate(true);
    getSvc().getEventsHandler().update(colEi, noinvites, attUri);

    return changed;
  }

  private String getRstat(final BwEvent ev) {
    String rstat = null;

    for (BwRequestStatus bwrstat: ev.getRequestStatuses()) {
      if (rstat != null) {
        rstat += ",";
        rstat += bwrstat.getCode();
      } else {
        rstat = bwrstat.getCode();
      }
    }

    if (rstat == null) {
      rstat = IcalDefs.deliveryStatusSuccess;
    }

    return rstat;
  }*/
}
