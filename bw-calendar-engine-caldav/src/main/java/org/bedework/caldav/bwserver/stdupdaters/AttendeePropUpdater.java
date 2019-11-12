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

package org.bedework.caldav.bwserver.stdupdaters;

import org.bedework.caldav.bwserver.PropertyUpdater;
import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.AttendeePropType;

import java.util.Set;

/** As an organizer attendees may be added or removed from a meeting.
 *
 * <p>As an attendee,updates to attendees normally only take place when an
 * attendee is updating their own partstat. The partstat of other attendees is
 * updated as a result of the background implicit scheduling.
 *
 * @author douglm
 */
public class AttendeePropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    ChangeTableEntry cte = ui.getCte();
    BwEvent ev = ui.getEvent();

    Set<BwAttendee> atts = ev.getAttendees();

    AttendeePropType pr = (AttendeePropType)ui.getProp();

    String attUri = ui.getIcalCallback().getCaladdr(
            ui.getIcalCallback().getPrincipal().getPrincipalRef());

    /* Must have an organizer propery */
    BwOrganizer org = ev.getOrganizer();

    if (org == null) {
      return new UpdateResult("No organizer for attendee update");
    }

    boolean isOrganizer = attUri.equals(org.getOrganizerUri());

    if (!isOrganizer) {
      /* Options are pretty limited here - change partstat only to our own entry
       */
      if (!pr.getCalAddress().equals(attUri)) {
        return new UpdateResult("Cannot update other attendees");
      }

      if (ui.isAdd() || ui.isRemove()) {
        return new UpdateResult("Cannot add or remove attendees");
      }

      if (!ui.isChange()) {
        // Nothing to do
        return UpdateResult.getOkResult();
      }

//        return new UpdateResult("unimplemented - attendee update");
      throw new WebdavException("Unimplemented - attendees update");
    }

    /* This is the organizer */

    if (ui.isAdd()) {
      if (!Util.isEmpty(atts)) {
        for (BwAttendee att: atts) {
          if (att.getAttendeeUri().equals(pr.getCalAddress())) {
            // Already there
            return UpdateResult.getOkResult();
          }
        }
      }

      BwAttendee newAtt = makeAttendee(pr);
      ev.addAttendee(newAtt);
      cte.addAddedValue(newAtt);

      return UpdateResult.getOkResult();
    }

    if (ui.isRemove()) {
      if (Util.isEmpty(atts)) {
        // Nothing to remove
        return UpdateResult.getOkResult();
      }

      BwAttendee remAtt = makeAttendee(pr);
      if (ev.removeAttendee(remAtt)) {
        cte.addRemovedValue(remAtt);
      }

      return UpdateResult.getOkResult();
    }

    if (ui.isChange()) {
      // Change a value
      if (Util.isEmpty(atts)) {
        // Nothing to change
        return new UpdateResult("No comment to change");
      }

      for (BwAttendee att: atts) {
        if (att.getAttendeeUri().equals(pr.getCalAddress())) {
          // Found
          throw new WebdavException("Unimplemented - attendees update");
          //return UpdateResult.getOkResult();
        }
      }
    }

    return UpdateResult.getOkResult();
  }

  private BwAttendee makeAttendee(final AttendeePropType pr) {
    return null;
  }
}
