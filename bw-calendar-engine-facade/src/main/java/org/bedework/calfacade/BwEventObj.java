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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.exc.CalFacadeException;

import edu.rpi.cmt.calendar.IcalDefs;
import edu.rpi.cmt.calendar.ScheduleMethods;

import java.util.Set;

/** An Event in Bedework.
 *
 *
 *
 *  @version 1.0
 */
@Dump(elementName="event", keyFields={"colPath", "uid", "recurrenceId"},
      firstFields = {"ownerHref"})
public class BwEventObj extends BwEvent implements ScheduleMethods {

  /** Constructor
   */
  public BwEventObj() {
    super();
  }

  /** Make a persistable freebusy request
   *
   * @param start
   * @param end
   * @param organizer
   * @param originator
   * @param attendees
   * @param recipients
   * @return event object
   */
  public static BwEvent makeFreeBusyRequest(final BwDateTime start,
                                     final BwDateTime end,
                                     BwOrganizer organizer,
                                     final String originator,
                                     final Set<BwAttendee> attendees,
                                     final Set<String> recipients) {
    BwEvent fbreq = new BwEventObj();

    if (organizer == null) {
      if (originator == null) {
        return null;
      }

      organizer = new BwOrganizer();
      organizer.setOrganizerUri(originator);
    }

    if (attendees == null) {
      if (recipients == null) {
        return null;
      }

      for (String r: recipients) {
        BwAttendee att = new BwAttendee();

        att.setAttendeeUri(r);
        fbreq.addAttendee(att);
      }
    } else {
      fbreq.setAttendees(attendees);
    }

    fbreq.setDtstart(start);
    fbreq.setDtend(end);
    fbreq.setEndType(BwEvent.endTypeDate);
    try {
      fbreq.setDuration(BwDateTime.makeDuration(fbreq.getDtstart(),
                                                fbreq.getDtend()).toString());
    } catch (CalFacadeException e) {
      return null;
    }

    fbreq.setEntityType(IcalDefs.entityTypeFreeAndBusy);
    fbreq.setRecipients(recipients);
    fbreq.setOrganizer(organizer);
    fbreq.setOriginator(originator);
    fbreq.setScheduleMethod(methodTypeRequest);

    // Various fields that must be set when persisting

    fbreq.setNoStart(false);
    fbreq.setRecurring(false);

    return fbreq;
  }

  @Override
  public Object clone() {
    BwEventObj ev = new BwEventObj();

    copyTo(ev);

    return ev;
  }
}
