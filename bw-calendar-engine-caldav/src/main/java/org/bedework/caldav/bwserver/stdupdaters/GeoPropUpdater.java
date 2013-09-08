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
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.GeoPropType;

import java.math.BigDecimal;

/**
 * @author douglm
 *
 */
public class GeoPropUpdater implements PropertyUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    BwEvent ev = ui.getEvent();
    GeoPropType pr = (GeoPropType)ui.getProp();
    ChangeTableEntry cte = ui.getCte();

    BwGeo val = new BwGeo(new BigDecimal(String.valueOf(pr.getLatitude())),
                          new BigDecimal(String.valueOf(pr.getLongitude())));
    BwGeo evVal = ev.getGeo();

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
      return new UpdateResult("No update specified for " + ui.getPropName());
    } else {
      if (!val.equals(evVal)) {
        return new UpdateResult("Values don't match for update to " +
                                ui.getPropName());
      }

      pr = (GeoPropType)ui.getUpdprop();
      val = new BwGeo(new BigDecimal(String.valueOf(pr.getLatitude())),
                      new BigDecimal(String.valueOf(pr.getLongitude())));
    }

    if (Util.cmpObjval(val, evVal) != 0) {
      cte.setChanged(evVal, val);
      ev.setGeo(val);
    }

    return UpdateResult.getOkResult();
  }
}
