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
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextPropertyType;

import java.util.List;

/** Updates the x-property which saves the original location property
 * for the event.
 *
 * <p>We will also add a real location property if this property value
 * corresponds to a known bedework location</p>
 *
 * @author douglm
 */
@SuppressWarnings("UnusedDeclaration")
public class XbwLocPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      final ChangeTableEntry cte = ui.getCte();
      final BwEvent ev = ui.getEvent();

      final List<BwXproperty> xlocs = ev.getXproperties(
              BwXproperty.xBedeworkLocation);
      // Should only be one or zero
      final BwXproperty xloc;

      if (Util.isEmpty(xlocs)) {
        xloc = null;
      } else {
        xloc = xlocs.get(0);
      }

      final String lang = UpdaterUtil.getLang(ui.getProp());
      final String xval = ((TextPropertyType)ui.getProp()).getText();

      final BwLocation evLoc = ev.getLocation();

      if (ui.isRemove()) {
        if (xlocs == null) {
          // Nothing to remove
          return UpdateResult.getOkResult();
        }

        // TODO - match values?
        ev.removeXproperty(xloc);
        cte.addRemovedValue(xloc);

        if (evLoc != null) {
          ev.setLocation(null);
          cte.addRemovedValue(evLoc);
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isAdd()) {
        if (xloc != null) {
          return new UpdateResult("Entity already has " + ui.getPropName() +
                                          " property - cannot add");
        }

        /* We shouldn't have a location either but we'll just end up
            replacing one if it exists
          */

        if (!checkLocation(ui, ev, lang, xval)) {
          final BwXproperty xp = makeXprop(lang, xval);
          ev.addXproperty(xp);
          cte.addAddedValue(xp);
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isChange()) {
        BwXproperty nxp = makeXprop(lang, xval);

        if (!nxp.equals(xloc)) {
          return new UpdateResult("Values don't match for update to " +
                                          ui.getPropName());
        }

        ev.removeXproperty(xloc);
        cte.addRemovedValue(xloc);

        final String nlang = UpdaterUtil.getLang(ui.getUpdprop());
        final String nxval = ((TextPropertyType)ui.getUpdprop()).getText();

        if (!checkLocation(ui, ev, nlang, nxval)) {
          nxp = makeXprop(nlang, nxval);
          ev.addXproperty(nxp);
          cte.addAddedValue(nxp);
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

    return new BwXproperty(BwXproperty.xBedeworkLocation,
                           pars, val);
  }

  private boolean checkLocation(final UpdateInfo ui,
                                final BwEvent ev,
                                final String lang,
                                final String val) throws CalFacadeException {
    final BwString sval = new BwString(lang, val);
    final boolean locPresent = ev.getLocation() != null;

    final BwLocation loc = ui.getIcalCallback().getLocation(sval);

    if (loc == null) {
      return false;
    }

    ev.setLocation(loc);

    if (locPresent) {
      ui.getCte(PropertyIndex.PropertyInfoIndex.LOCATION).addChangedValue(loc);
    } else {
      ui.getCte(PropertyIndex.PropertyInfoIndex.LOCATION).addValue(loc);
    }

    return true;
  }
}
