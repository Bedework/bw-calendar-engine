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
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.util.ChangeTableEntry;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.DurationPropType;

/** This could be updating the event duration or the duration property in an
 * alarm.
 *
 * @author douglm
 *
 */
public class DurationPropUpdater extends DateDatetimePropUpdater {
  private static PropertyUpdater alarmDurationPropUpdater =
      new AlarmDurationPropUpdater();

  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    if (ui.getSubComponent() != null) {
      return alarmDurationPropUpdater.applyUpdate(ui);
    }

    BwEvent ev = ui.getEvent();

    DurationPropType dur = (DurationPropType)ui.getProp();

    DatesState ds = (DatesState)ui.getState(DatesState.stateName);
    if (ds == null) {
      ds = new DatesState(ev);
      ui.saveState(DatesState.stateName, ds);
    }

    ChangeTableEntry cte = ui.getCte();
    if (ui.isRemove()) {
      if (ev.getEndType() != StartEndComponent.endTypeDuration) {
        return new UpdateResult("Entity has no duration - cannot remove");
      }
      cte.setDeleted(ev.getDuration());
      ds.duration = null;

      return UpdateResult.getOkResult();
    }

    if (ui.isAdd()) {
      if (ev.getEndType() == StartEndComponent.endTypeDuration) {
        return new UpdateResult("Entity already has duration - cannot add");
      }

      ds.duration = dur.getDuration();
      cte.setAdded(ds.duration);
      return UpdateResult.getOkResult();
    }

    /* Changing duration */
    if (ev.getEndType() != StartEndComponent.endTypeDuration) {
      return new UpdateResult("Entity has no duration - cannot change");
    }

    dur = (DurationPropType)ui.getUpdprop();

    if (!dur.getDuration().equals(ev.getDuration())) {
      cte.setChanged(ev.getDuration(), dur.getDuration());
    }
    ds.duration = dur.getDuration();

    return UpdateResult.getOkResult();
  }
}
