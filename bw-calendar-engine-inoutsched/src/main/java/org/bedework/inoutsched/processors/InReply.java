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
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.PollItmId;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.SchedulingI;
import org.bedework.icalendar.IcalUtil;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.parameter.Response;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Voter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** Handles incoming method REPLY scheduling messages.
 *
 * @author Mike Douglass
 */
public class InReply extends InProcessor {
  private static String acceptPartstat = IcalDefs.partstats[IcalDefs.partstatAccepted];

  /**
   * @param svci
   */
  public InReply(final CalSvcI svci) {
    super(svci);
  }

  /**
   * @param ei
   * @return ScheduleResult
   * @throws CalFacadeException
   */
  @Override
  public ProcessResult process(final EventInfo ei) throws CalFacadeException {
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
        pr.sr.errorCode = CalFacadeException.schedulingNoOriginator;
        break check;
      }

      String attUri = null;

      /* Should be exactly one attendee */
      if (!ev.getSuppressed()) {
        final Collection<BwAttendee> atts = ev.getAttendees();
        if ((atts == null) || (atts.size() != 1)) {
          pr.sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
          break check;
        }

        final BwAttendee att = atts.iterator().next();
        if (!att.getPartstat().equals(acceptPartstat)) {
          pr.attendeeAccepting = false;
        }

        attUri = att.getAttendeeUri();
      }

      if (ei.getNumOverrides() > 0) {
        for (final EventInfo oei: ei.getOverrides()) {
          ev = oei.getEvent();
          final Collection<BwAttendee> atts = ev.getAttendees();
          if ((atts == null) || (atts.size() != 1)) {
            pr.sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
            break check;
          }

          final BwAttendee att = atts.iterator().next();
          if (!att.getPartstat().equals(acceptPartstat)) {
            pr.attendeeAccepting = false;
          }

          if (attUri == null) {
            attUri = att.getAttendeeUri();
          } else if (!attUri.equals(att.getAttendeeUri())) {
            pr.sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
            break check;
          }
        }
      }

      if (attUri == null) {
        pr.sr.errorCode = CalFacadeException.schedulingExpectOneAttendee;
        break check;
      }

      /*TODO If the sequence of the incoming event is lower than the sequence on the
       * calendar event we ignore it.
       */

      final boolean vpoll = colEi.getEvent().getEntityType() ==
              IcalDefs.entityTypeVpoll;
      if (vpoll) {
        if (!updateOrganizerPollCopy(colEi, ei, attUri, pr.sr, 0)) {
          break check;
        }
      } else if (!updateOrganizerCopy(colEi, ei, attUri, pr.sr, 0)) {
        break check;
      }

      pr.removeInboxEntry = false;
    }

    return pr;
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private boolean updateOrganizerPollCopy(final EventInfo colEi,
                                          final EventInfo inBoxEi,
                                          final String attUri,
                                          final ScheduleResult sr,
                                          final int action) throws CalFacadeException {
    /* We have a single voter and their responses to each item.
       Add a VOTER property to each candidate with the response.
     */

    /* First parse out the poll items */
    try {
      final BwEvent colEv = colEi.getEvent();
      final Map<Integer, Component> comps = IcalUtil.parseVpollCandidates(colEv);

      colEv.clearPollItems();  // We'll add them back

      final BwEvent inEv = inBoxEi.getEvent();

      final Set<PollItmId> pids = inEv.getPollItemIds();

      for (final PollItmId pid: pids) {
        final Component comp = comps.get(pid.getId());

        if (comp == null) {
          continue;
        }

        final PropertyList pl = comp.getProperties(Property.VOTER);

        if (pl == null) {
          continue;
        }

        Voter voter = null;

        for (final Object vo: pl) {
          final Voter v = (Voter)vo;

          if (v.getValue().equals(attUri)) {
            voter = v;
            break;
          }
        }

        if (voter == null) {
          // Make a new VOTER property for this respondee
          voter = new Voter(attUri);
          comp.getProperties().add(voter);
        }

        final Response resp = (Response)voter.getParameter(Parameter.RESPONSE);

        if (resp != null) {
          voter.getParameters().remove(resp);
        }

        voter.getParameters().add(new Response(pid.getResponse()));
      }

      for (final Component comp: comps.values()) {
        colEv.addPollItem(comp.toString());
      }
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    getSvc().getEventsHandler().update(colEi, false, attUri);

    return true;
  }

  private boolean updateOrganizerCopy(final EventInfo colEi,
                                      final EventInfo inBoxEi,
                                      final String attUri,
                                      final ScheduleResult sr,
                                      final int action) throws CalFacadeException {
    final BwEvent inBoxEv = inBoxEi.getEvent();
    final BwEvent calEv = colEi.getEvent();

    /* Only set true if the inbox copy needs to stay as notification.
     * Do not set true for status updates
     */
    boolean changed = false;

    if (debug) {
      trace("Update for attendee " + attUri);
    }

    if (inBoxEv.getScheduleMethod() != ScheduleMethods.methodTypeReply) {
      sr.errorCode = CalFacadeException.schedulingBadMethod;
      return false;
    }

    /* If the incoming sequence is less than that in the organizers event
     * then ignore the incoming reply?
     */
    /* Update the participation status from the incoming attendee */

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
      final boolean recurringInstance = (calEv instanceof BwEventProxy);
      final BwAttendee att = inBoxEv.findAttendee(attUri);

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
        final BwEventProxy pr = (BwEventProxy)calEv;

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

        /*
        if (cei == null) {
          // Organizer must have deleted the override.
          if (debug) {
            trace("Skipping missing override " + oev.getRecurrenceId());
          }
          continue;
        }*/

        final BwEvent ocalEv = cei.getEvent();

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

            DtStart st = new DtStart(dt);
            String tzid = calEv.getDtstart().getTzid();
            if (tzid != null) {
              TimeZone tz = Timezones.getTz(tzid);
              st.setTimeZone(tz);
            }
            ocalEv.setDtstart(BwDateTime.makeBwDateTime(st));
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

    /* Update the organizer copy. This will broadcast the changes tp all
     * attendees
     */
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
  }
}
