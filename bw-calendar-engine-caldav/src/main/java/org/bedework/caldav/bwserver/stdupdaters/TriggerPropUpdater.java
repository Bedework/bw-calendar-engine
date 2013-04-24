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

import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cmt.calendar.XcalUtil;
import edu.rpi.sss.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.RelatedParamType;
import ietf.params.xml.ns.icalendar_2.TriggerPropType;

/**
 * @author douglm
 *
 */
public class TriggerPropUpdater extends AlarmPropUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    BwAlarm alarm = ui.getSubComponent().getAlarm();
    TriggerPropType pr = (TriggerPropType)ui.getProp();

    /* Need to remove parameter ei and add list of affected entities */

    if (ui.isRemove()) {
      return new UpdateResult("Cannot remove " + ui.getPropName());
    }

    if (ui.isAdd()) {
      return new UpdateResult("Cannot add " + ui.getPropName());
    }

    if (!ui.isChange()) {
      return new UpdateResult("No update specified for " + ui.getPropName());
    }

    /* Ensure the old value matches */
    if (pr.getDateTime() != null) {
      if (!alarm.getTriggerDateTime()) {
        return new UpdateResult("Values don't match for update to " +
            ui.getPropName());
      }
    } else {
      if (alarm.getTriggerDateTime()) {
        return new UpdateResult("Values don't match for update to " +
            ui.getPropName());
      }
    }

    pr = (TriggerPropType)ui.getUpdprop();

    if (pr.getDateTime() != null) {
      alarm.setTrigger(XcalUtil.getIcalFormatDateTime(pr.getDateTime()));
      alarm.setTriggerDateTime(true);
    } else {
      alarm.setTrigger(pr.getDuration());

      RelatedParamType r = (RelatedParamType)XcalUtil.findParam(pr, XcalTags.related);

      alarm.setTriggerStart((r == null) ||
                         (r.getText().toUpperCase().equals("START")));
    }

    flagChange(alarm, ui);

    return UpdateResult.getOkResult();
  }
}
