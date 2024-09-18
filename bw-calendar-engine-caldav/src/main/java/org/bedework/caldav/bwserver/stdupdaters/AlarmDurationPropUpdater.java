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

import ietf.params.xml.ns.icalendar_2.DurationPropType;

/** This could be updating the event duration or the duration property in an
 * alarm.
 *
 * @author douglm
 *
 */
public class AlarmDurationPropUpdater extends DateDatetimePropUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) {
    BwAlarm alarm = ui.getSubComponent().getAlarm();
    DurationPropType dur = (DurationPropType)ui.getProp();

    if (ui.isRemove()) {
      if (alarm.getDuration() == null) {
        return new UpdateResult("Entity has no duration - cannot remove");
      }

      alarm.setDuration(null);
      flagChange(alarm, ui);

      return UpdateResult.getOkResult();
    }

    if (ui.isAdd()) {
      if (alarm.getDuration() != null) {
        return new UpdateResult("Entity already has duration - cannot add");
      }

      alarm.setDuration(dur.getDuration());
      flagChange(alarm, ui);

      return UpdateResult.getOkResult();
    }

    /* Changing duration */

    dur = (DurationPropType)ui.getUpdprop();

    if (!dur.getDuration().equals(alarm.getDuration())) {
      alarm.setDuration(dur.getDuration());
      flagChange(alarm, ui);
    }

    return UpdateResult.getOkResult();
  }
}
