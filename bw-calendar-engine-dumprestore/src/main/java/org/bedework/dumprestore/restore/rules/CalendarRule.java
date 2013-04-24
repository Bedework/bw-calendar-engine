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

package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwSystem;
import org.bedework.dumprestore.ExternalSubInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class CalendarRule extends EntityRule {
  /** Constructor
   *
   * @param globals
   */
  public CalendarRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) throws Exception {
    super.begin(ns, name, att);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwCalendar entity = (BwCalendar)pop();
    boolean special = false;

    globals.counts[globals.collections]++;

    if ((globals.counts[globals.collections] % 100) == 0) {
      info("Restore calendar # " + globals.counts[globals.collections]);
    }

    fixSharableEntity(entity, "Calendar");

    if (globals.skipSpecialCals &&
        (entity.getCalType() == BwCalendar.calTypeFolder)) {
      // might need to fix if from 3.0
      BwSystem sys = globals.getSyspars();
      String calpath = entity.getPath();
      String[] pes = calpath.split("/");
      int pathLength = pes.length - 1;  // First element is empty string

      if ((pathLength == 3) &&
          sys.getUserCalendarRoot().equals(pes[1])) {
        String calname = pes[3];

        if (!calname.equals(entity.getName())) {
          throw new Exception("Got path wrong - len = " + pathLength +
                              " path = " + calpath +
                              " calname = " + calname);
        }

        if (calname.equals(sys.getDefaultTrashCalendar())) {
          entity.setCalType(BwCalendar.calTypeTrash);
          special = true;
        } else if (calname.equals(sys.getDeletedCalendar())) {
          entity.setCalType(BwCalendar.calTypeDeleted);
          special = true;
        } else if (calname.equals(sys.getBusyCalendar())) {
          entity.setCalType(BwCalendar.calTypeBusy);
          special = true;
        } else if (calname.equals(sys.getUserInbox())) {
          entity.setCalType(BwCalendar.calTypeInbox);
        } else if (calname.equals(sys.getUserOutbox())) {
          entity.setCalType(BwCalendar.calTypeOutbox);
        } else if (calname.equals(sys.getDefaultNotificationsName())) {
          entity.setCalType(BwCalendar.calTypeNotifications);
        }
      }
    }

    if (special && globals.skipSpecialCals) {
      return;
    }

    if (entity.getExternalSub() && !entity.getTombstoned()) {
      globals.counts[globals.externalSubscriptions]++;
      globals.externalSubs.add(new ExternalSubInfo(entity.getPath(),
                                                   entity.getPublick(),
                                                   entity.getOwnerHref()));
    }

    try {
      if (globals.rintf != null) {
        /* If the parent is null then this should be one of the root calendars,
         */
        String parentPath = entity.getColPath();
        if (!globals.onlyUsersMap.check(entity)) {
          return;
        }

        if (parentPath == null) {
          // Ensure root
          globals.rintf.saveRootCalendar(entity);
        } else {
          globals.rintf.addCalendar(entity);
        }
      }
    } catch (Throwable t) {
      throw new Exception(t);
    }
  }
}
