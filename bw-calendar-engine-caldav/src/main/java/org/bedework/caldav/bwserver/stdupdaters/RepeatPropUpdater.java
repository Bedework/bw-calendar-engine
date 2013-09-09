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

import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.BwAlarm;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.RepeatPropType;

/**
 * @author douglm
 *
 */
public class RepeatPropUpdater extends AlarmPropUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    BwAlarm alarm = ui.getSubComponent().getAlarm();
    RepeatPropType pr = (RepeatPropType)ui.getProp();

    /* Need to remove parameter ei and add list of affected entities */

    if (ui.isRemove()) {
      alarm.setRepeat(0);
      flagChange(alarm, ui);

      return UpdateResult.getOkResult();
    }

    if (ui.isAdd()) {
      if (alarm.getRepeat() != 0) {
        return new UpdateResult("Entity already has repeat - cannot add");
      }

      alarm.setRepeat(pr.getInteger().intValue());
      flagChange(alarm, ui);

      return UpdateResult.getOkResult();
    }

    if (!ui.isChange()) {
      return new UpdateResult("No update specified for " + ui.getPropName());
    }

    /* Ensure the old value matches */
    if (pr.getInteger().intValue() != alarm.getRepeat()) {
        return new UpdateResult("Values don't match for update to " +
            ui.getPropName());
    }

    pr = (RepeatPropType)ui.getUpdprop();

    alarm.setRepeat(pr.getInteger().intValue());
    flagChange(alarm, ui);

    return UpdateResult.getOkResult();
  }
}
