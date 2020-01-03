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

import org.bedework.caldav.bwserver.ParameterUpdater;
import org.bedework.caldav.bwserver.PropertyUpdater;
import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.Response;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextParameterType;
import ietf.params.xml.ns.icalendar_2.TextPropertyType;

/**
 * @author douglm
 *
 */
@SuppressWarnings("UnusedDeclaration")
public class LocationPropUpdater implements PropertyUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      final BwEvent ev = ui.getEvent();
      final ChangeTableEntry cte = ui.getCte();

      BwString val = new BwString(UpdaterUtil.getLang(ui.getProp()),
                                  ((TextPropertyType)ui.getProp()).getText());
      final BwLocation evLoc = ev.getLocation();
      BwString evVal = null;
      if (evLoc != null) {
        evVal = evLoc.getAddress();
      }

      if (ui.isRemove()) {
        if (evVal == null) {
          return new UpdateResult("Entity has no " + ui.getPropName() +
                                  " property - cannot remove");
        }
        val = null;
      } else if (ui.isAdd()) {
        if (evVal != null) {
          return new UpdateResult("Entity already has " + ui.getPropName() +
                                  " property - cannot add");
        }
      } else if (!ui.isChange()) {
        final ParameterUpdater.UpdateInfo langUpd =
            UpdaterUtil.findLangUpdate(ui.getParamUpdates());

        if (langUpd == null) {
          return new UpdateResult("No update specified for " + ui.getPropName());
        }

        String lang = val.getLang();

        if (langUpd.isRemove()) {
          lang = null;
        } else if (langUpd.isAdd()) {
          lang = ((TextParameterType)langUpd.getParam()).getText();
        } else if (langUpd.getUpdparam() != null) {
          lang = ((TextParameterType)langUpd.getUpdparam()).getText();
        }

        if (!Util.equalsString(lang, val.getLang())) {
          val = new BwString(lang, val.getValue());
        }
      } else {
        if (!val.equals(evVal)) {
          return new UpdateResult("Values don't match for update to " +
                                  ui.getPropName());
        }

        val = new BwString(UpdaterUtil.getLang(ui.getUpdprop()),
                           ((TextPropertyType)ui.getUpdprop()).getText());
      }

      if (val == null) {
        cte.addRemovedValue(ev.getLocation());
        ev.setLocation(null);
      } else if (Util.cmpObjval(val, evVal) != 0) {
        var resp = ui.getIcalCallback().findLocation(val);
        final BwLocation loc;

        if (resp.getStatus() == Response.Status.notFound) {
          loc = BwLocation.makeLocation();
          loc.setAddress(val);
          ui.getIcalCallback().addLocation(loc);
        } else if (resp.isOk()) {
          loc = resp.getEntity();
        } else {
          return new UpdateResult(ui.getPropName().toString() +
                                          ": failed. Status: " + resp.getStatus() +
                                          ", msg: " + resp.getMessage());
        }

        if (cte.setChanged(evLoc, loc)) {
          ev.setLocation(loc);
        }
      }

      return UpdateResult.getOkResult();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }
}
