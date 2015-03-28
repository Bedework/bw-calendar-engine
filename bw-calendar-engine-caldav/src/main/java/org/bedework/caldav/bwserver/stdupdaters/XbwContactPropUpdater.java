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
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextPropertyType;

import java.util.List;
import java.util.Set;

/** Updates the x-property which saves the original location property
 * for the event.
 *
 * <p>We will also add a real location property if this property value
 * corresponds to a known bedework location</p>
 *
 * @author douglm
 */
@SuppressWarnings("UnusedDeclaration")
public class XbwContactPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      final ChangeTableEntry cte = ui.getCte();
      final BwEvent ev = ui.getEvent();

      final List<BwXproperty> xcontacts = ev.getXproperties(
              BwXproperty.xBedeworkContact);

      final Set<BwContact> contacts = ev.getContacts();

      final String lang = UpdaterUtil.getLang(ui.getProp());
      final String xval = ((TextPropertyType)ui.getProp()).getText();

      final BwString cstr = new BwString(lang, xval);

      if (ui.isRemove()) {
        if (Util.isEmpty(xcontacts)) {
          // Nothing to remove
          return UpdateResult.getOkResult();
        }

        for (final BwXproperty xp: xcontacts) {
          if (!xp.getValue().equals(xval)) {
            continue;
          }

          // Found
          ev.removeXproperty(xp);
          cte.addRemovedValue(xp);

           /* Do we have a corresponding contact */
          for (final BwContact c: contacts) {
            if (c.getCn().equals(cstr)) {
              ev.removeContact(c);
              cte.addRemovedValue(c);
              break;
            }
          }

          return UpdateResult.getOkResult();
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isAdd()) {
        for (final BwXproperty xp: xcontacts) {
          if (xp.getValue().equals(xval)) {
            return new UpdateResult(
                    "Entity already has " + ui.getPropName() +
                            " property with that value - cannot add");
          }
        }

        final BwXproperty xp = makeXprop(lang, xval);
        ev.addXproperty(xp);
        cte.addAddedValue(xp);

        checkContact(ui, ev, contacts, lang, xval);

        return UpdateResult.getOkResult();
      }

      if (ui.isChange()) {
        for (final BwXproperty xp : xcontacts) {
          if (xp.getValue().equals(xval)) {
            // Found

            ev.removeXproperty(xp);
            cte.addRemovedValue(xp);

            final String nlang = UpdaterUtil
                    .getLang(ui.getUpdprop());
            final String nxval = ((TextPropertyType)ui.getUpdprop())
                    .getText();

            final BwXproperty nxp = makeXprop(nlang, nxval);
            ev.addXproperty(nxp);
            cte.addAddedValue(nxp);

            checkContact(ui, ev, contacts, nlang, nxval);

            return UpdateResult.getOkResult();
          }
        }
      }

      return UpdateResult.getOkResult();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private BwXproperty makeXprop(final String lang,
                                final String val) {
    final String pars;
    if (lang == null) {
      pars = null;
    } else {
      pars = "lang=" + lang;
    }

    return new BwXproperty(BwXproperty.xBedeworkContact,
                           pars, val);
  }

  private void checkContact(final UpdateInfo ui,
                            final BwEvent ev,
                            final Set<BwContact> contacts,
                            final String lang,
                            final String val) throws CalFacadeException {
    final BwString sval = new BwString(lang, val);

    final BwContact contact = ui.getIcalCallback().findContact(sval);

    if (contact == null) {
      return;
    }

    for (final BwContact c: contacts) {
      if (c.getCn().equals(sval)) {
        // Already present
        return;
      }
    }

    ev.addContact(contact);

    ui.getCte(PropertyIndex.PropertyInfoIndex.CONTACT).addAddedValue(contact);
  }
}
