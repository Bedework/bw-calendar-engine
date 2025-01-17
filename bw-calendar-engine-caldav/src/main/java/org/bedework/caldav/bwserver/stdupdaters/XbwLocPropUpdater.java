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
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.BwXproperty.Xpar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.xml.tagdefs.XcalTags;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.BaseParameterType;
import ietf.params.xml.ns.icalendar_2.TextParameterType;
import ietf.params.xml.ns.icalendar_2.TextPropertyType;

import java.util.List;

import static org.bedework.calfacade.BwXproperty.xBedeworkLocation;
import static org.bedework.util.misc.response.Response.Status.ok;

/** Updates the x-property which saves the original location property
 * for the event.
 *
 * <p>We will also add a real location property if this property value
 * corresponds to a known bedework location</p>
 *
 * @author douglm
 */
@SuppressWarnings("UnusedDeclaration")
public class XbwLocPropUpdater implements Logged, PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) {
    try {
      final ChangeTableEntry cte = ui.getCte();
      final BwEvent ev = ui.getEvent();

      final List<BwXproperty> xlocs = ev.getXproperties(
              xBedeworkLocation);
      // Should only be one or zero
      final BwXproperty xloc;

      if (Util.isEmpty(xlocs)) {
        xloc = null;
      } else {
        xloc = xlocs.get(0);
      }

      final String xval = ((TextPropertyType)ui.getProp()).getText();
      final BaseParameterType keyParam = XcalUtil.findParam(ui.getProp(),
                                                            XcalTags.xBedeworkLocationKey);
      final String keyName;
      if (keyParam != null) {
        keyName = ((TextParameterType)keyParam).getText();
      } else {
        keyName = null;
      }

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

        if (!checkLocation(ui, ev, xval, keyName)) {
          final BwXproperty xp = makeXprop(xval);
          ev.addXproperty(xp);
          cte.addAddedValue(xp);
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isChange()) {
        if (xloc == null) {
          return new UpdateResult("Entity has no " + ui.getPropName() +
                                          " property - cannot change");
        }

        if (CalFacadeUtil.cmpObjval(xval, xloc.getValue()) != 0) {
          return new UpdateResult("Values don't match for update to " +
                                          ui.getPropName());
        }

        ev.removeXproperty(xloc);
        cte.addRemovedValue(xloc);

        final String nxval = ((TextPropertyType)ui.getUpdprop()).getText();

        if (!checkLocation(ui, ev, nxval, keyName)) {
          final BwXproperty nxp = makeXprop(nxval);
          if (keyName != null) {
            nxp.getParameters()
               .add(new Xpar(XcalTags.xBedeworkLocationKey.getLocalPart(),
                             keyName));
          }

          ev.addXproperty(nxp);
          cte.addAddedValue(nxp);
        }
      }

      return UpdateResult.getOkResult();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private BwXproperty makeXprop(final String val) {
    return new BwXproperty(xBedeworkLocation,
                           null, val);
  }

  private boolean checkLocation(final UpdateInfo ui,
                                final BwEvent ev,
                                final String val,
                                final String keyName) {
    final boolean locPresent = ev.getLocation() != null;

    final GetEntityResponse<BwLocation> resp;

    if (keyName == null) {
      resp = ui.getIcalCallback().fetchLocationByCombined(val, true);
      if (logger.debug()) {
        debug("Attempt to fetch location with val \"" + val + "\"");
        debug("Response was " + resp.getStatus());
      }
    } else {
      resp = ui.getIcalCallback().fetchLocationByKey(keyName,
                                                     val);
    }

    if (resp.getStatus() != ok) {
      return false;
    }

    final BwLocation loc = resp.getEntity();

    ev.setLocation(loc);

    if (locPresent) {
      ui.getCte(PropertyIndex.PropertyInfoIndex.LOCATION).addChangedValue(loc);
    } else {
      ui.getCte(PropertyIndex.PropertyInfoIndex.LOCATION).addValue(loc);
    }

    return true;
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
