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
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTableEntry;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cmt.calendar.IcalDefs;
import edu.rpi.cmt.calendar.ScheduleMethods;

import ietf.params.xml.ns.icalendar_2.DtstartPropType;

import javax.xml.ws.Holder;

/**
 * @author douglm
 *
 */
public class DtStartPropUpdater extends DateDatetimePropUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      BwEvent ev = ui.getEvent();
      boolean scheduleReply = ev.getScheduleMethod() == ScheduleMethods.methodTypeReply;
      // No dates valid for reply

      DtstartPropType dt = (DtstartPropType)ui.getProp();

      DatesState ds = (DatesState)ui.getState(DatesState.stateName);
      if (ds == null) {
        ds = new DatesState(ev);
        ui.saveState(DatesState.stateName, ds);
      }

      ChangeTableEntry cte = ui.getCte();
      if (ui.isRemove()) {
        if (!scheduleReply &&
            (ev.getEntityType() != IcalDefs.entityTypeTodo)) {
          return new UpdateResult("Entity requires start date");
        }

        cte.setDeleted(ev.getDtstart());
        ds.start = null;
        return UpdateResult.getOkResult();
      }

      if (ui.isAdd()) {
        if (!ev.getNoStart()) {
          return new UpdateResult("Entity already has start date - cannot add");
        }

        ds.start = BwDateTime.makeBwDateTime(dt);
        cte.setAdded(ds.start);
        return UpdateResult.getOkResult();
      }

      /* Changing dtstart - either value or parameters */
      if (ev.getNoStart()) {
        return new UpdateResult("Entity has no start date - cannot change");
      }

      Holder<BwDateTime> resdt = new Holder<BwDateTime>();

      UpdateResult ur = makeDt(ev.getDtstart(), resdt, ui);
      if (!ur.getOk()) {
        return ur;
      }

      if (resdt.value != null) {
        cte.setChanged(ev.getDtstart(), resdt.value);
        ds.start = resdt.value;
      }

      return UpdateResult.getOkResult();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }
}
