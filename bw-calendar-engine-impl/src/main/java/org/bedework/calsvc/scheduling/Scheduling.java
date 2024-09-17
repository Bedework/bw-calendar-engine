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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import java.util.Collection;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece handles a few general methods. Work down the chain of extends
 * (or look in the package) to find the other parts of the story.
 *
 * @author Mike Douglass
 *
 */
public class Scheduling extends ImplicitSchedulingHandler {
  /** Constructor
   *
   * @param svci service interface
   */
  public Scheduling(final CalSvc svci) {
    super(svci);
  }

  @Override
  public EventInfo getStoredMeeting(final BwEvent ev) {
    final String preferred = getSvc().getCalendarsHandler().
            getPreferred(IcalDefs.entityTypeIcalNames[ev.getEntityType()]);
    if (preferred == null) {
      throw new CalFacadeException(CalFacadeException.schedulingNoCalendar);
    }

    if (debug()) {
      debug("Look for event " + ev.getUid() +
                    " in " + preferred);
    }

    final Collection<EventInfo> evs = getSvc().getEventsHandler().
            getByUid(preferred,
                     ev.getUid(),
                     null,
                     RecurringRetrievalMode.overrides);

    if (Util.isEmpty(evs)) {
      return null;
    }

    /* Return the active meeting */

    for (final EventInfo ei: evs) {
      final BwEvent e = ei.getEvent();
      // Skip anything other than a calendar collection
      final BwCalendar evcal = getSvc().getCalendarsHandler().get(e.getColPath());

      if (!evcal.getCollectionInfo().scheduling) {
        continue;
      }

      if (e.getOrganizerSchedulingObject() || e.getAttendeeSchedulingObject()) {
        return ei;
      }

      if (e.getSuppressed()) {
        // See if the overrrides are scheduling objects
        for (final BwEvent oe: ei.getOverrideProxies()) {
          if (oe.getOrganizerSchedulingObject() ||
              oe.getAttendeeSchedulingObject()) {
            return ei;
          }
        }
      }
    }

    // Not found.
    return null;
  }

  /*
   * 2.1.5 Message Sequencing

     CUAs that handle the [iTIP] application protocol must often correlate
     a component in a calendar store with a component received in the
     [iTIP] message. For example, an event may be updated with a later
     revision of the same event. To accomplish this, a CUA must correlate
     the version of the event already in its calendar store with the
     version sent in the [iTIP] message. In addition to this correlation,
     there are several factors that can cause [iTIP] messages to arrive in
     an unexpected order.  That is, an "Organizer" could receive a reply
     to an earlier revision of a component AFTER receiving a reply to a
     later revision.

     To maximize interoperability and to handle messages that arrive in an
     unexpected order, use the following rules:

     1.  The primary key for referencing a particular iCalendar component
         is the "UID" property value. To reference an instance of a
         recurring component, the primary key is composed of the "UID" and
         the "RECURRENCE-ID" properties.

     2.  The secondary key for referencing a component is the "SEQUENCE"
         property value.  For components where the "UID" is the same, the
         component with the highest numeric value for the "SEQUENCE"
         property obsoletes all other revisions of the component with
         lower values.

     3.  "Attendees" send "REPLY" messages to the "Organizer".  For
         replies where the "UID" property value is the same, the value of
         the "SEQUENCE" property indicates the revision of the component
         to which the "Attendee" is replying.  The reply with the highest
         numeric value for the "SEQUENCE" property obsoletes all other
         replies with lower values.

     4.  In situations where the "UID" and "SEQUENCE" properties match,
         the "DTSTAMP" property is used as the tie-breaker. The component
         with the latest "DTSTAMP" overrides all others. Similarly, for
         "Attendee" responses where the "UID" property values match and
         the "SEQUENCE" property values match, the response with the
         latest "DTSTAMP" overrides all others.

       We compare two events for order according to the above rules. We retrieved
       the second event from the outbox or inbox using the uid so we know the uids
       match.

       return 0 for equal.
             -1 for event 1 < event 2
              1 for event 1 > event 2
   * /
  private int checkSequence(BwEvent ev1, BwEvent ev2) {
    int seq1 = ev1.getSequence();
    int seq2 = ev2.getSequence();

    if (seq1 < seq2) {
      return -1;
    }

    if (seq1 > seq2) {
      return 1;
    }

    String dtstamp1 = ev1.getDtstamp();
    String dtstamp2 = ev2.getDtstamp();

    return CalFacadeUtil.compareStrings(dtstamp1, dtstamp2);
  }*/
}
