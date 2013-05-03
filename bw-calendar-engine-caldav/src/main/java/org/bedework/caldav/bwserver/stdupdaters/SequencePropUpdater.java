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
import org.bedework.calfacade.util.ChangeTableEntry;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.SequencePropType;

/**
 * @author douglm
 *
 */
public class SequencePropUpdater implements PropertyUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    BwEvent ev = ui.getEvent();
    SequencePropType pr = (SequencePropType)ui.getProp();
    ChangeTableEntry cte = ui.getCte();

    int val = pr.getInteger().intValue();
    int evVal = ev.getSequence();

    if (ui.isRemove()) {
      if (evVal == 0) {
        return new UpdateResult("Entity has no " + ui.getPropName() +
                                " property - cannot remove");
      }

      val = 0;
    } else if (ui.isAdd()) {
      if (evVal != 0) {
        return new UpdateResult("Entity already has " + ui.getPropName() +
                                " property - cannot add");
      }
    } else if (!ui.isChange()) {
      return new UpdateResult("No update specified for " + ui.getPropName());
    } else {
      if (val != evVal) {
        return new UpdateResult("Values don't match for update to " +
                                ui.getPropName());
      }

      val = ((SequencePropType)ui.getUpdprop()).getInteger().intValue();
    }

    if (val < 0) {
      return new UpdateResult("Value for " + ui.getPropName() +
                              " must be >= 0 ");
    }

    if (val != evVal) {
      cte.setChanged(evVal, val);
      ev.setSequence(val);
    }

    return UpdateResult.getOkResult();
  }
}
