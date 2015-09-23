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
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextPropertyType;

import java.util.Set;

/**
 * @author douglm
 *
 */
@SuppressWarnings("unused")
public class ContactPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      final ChangeTableEntry cte = ui.getCte();
      final BwEvent ev = ui.getEvent();

      final Set<BwContact> contacts = ev.getContacts();

      final BwString nm =
              new BwString(UpdaterUtil.getLang(ui.getProp()),
                           ((TextPropertyType)ui.getProp()).getText());

      final String altrep = UpdaterUtil.getAltrep(ui.getProp());

      if (ui.isAdd()) {
        if (!Util.isEmpty(contacts)) {
          for (final BwContact cnct: contacts) {
            if (cnct.getCn().equals(nm)) {
              // Already there
              return UpdateResult.getOkResult();
            }
          }
        }

        // Add it
        BwContact cnct = ui.getIcalCallback().findContact(nm);

        if (cnct == null) {
          cnct = BwContact.makeContact();
          cnct.setCn(nm);
          cnct.setLink(altrep);

          ui.getIcalCallback().addContact(cnct);
        }

        ev.addContact(cnct);
        cte.addAddedValue(cnct);

        return UpdateResult.getOkResult();
      }

      if (ui.isRemove()) {
        if (Util.isEmpty(contacts)) {
          // Nothing to remove
          return UpdateResult.getOkResult();
        }

        for (final BwContact cnct: contacts) {
          if (cnct.getCn().equals(nm)) {
            if (ev.removeContact(cnct)) {
              cte.addRemovedValue(cnct);
            }

            return UpdateResult.getOkResult();
          }
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isChange()) {
        // Change a value
        if (Util.isEmpty(contacts)) {
          // Nothing to change
          return new UpdateResult("No contact to change");
        }

        for (final BwContact evcnct: contacts) {
          if (evcnct.getCn().equals(nm)) {
            // Found - remove that one and add a new one.
            final BwString newnm =
                    new BwString(UpdaterUtil.getLang(ui.getUpdprop()),
                                 ((TextPropertyType)ui.getUpdprop()).getText());

            BwContact cnct = ui.getIcalCallback().findContact(newnm);

            if (cnct == null) {
              cnct = new BwContact();
              cnct.setCn(newnm);
              cnct.setLink(altrep);

              ui.getIcalCallback().addContact(cnct);
            }

            if (ev.removeContact(evcnct)) {
              cte.addRemovedValue(evcnct);
            }

            ev.addContact(cnct);
            cte.addAddedValue(cnct);

            return UpdateResult.getOkResult();
          }
        }
      }

      return UpdateResult.getOkResult();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }
}
