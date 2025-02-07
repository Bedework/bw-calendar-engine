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
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.IcalDefs;

import ietf.params.xml.ns.icalendar_2.DateDatetimePropertyType;
import ietf.params.xml.ns.icalendar_2.DuePropType;

import jakarta.xml.ws.Holder;

/**
 * @author douglm
 *
 */
public class DtEndDuePropUpdater extends DateDatetimePropUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) {
    /* For start, end and duration we have to finish up at the end after all
     * changes are made.
     */
    BwEvent ev = ui.getEvent();

    DateDatetimePropertyType dt = (DateDatetimePropertyType)ui.getProp();

    if (dt instanceof DuePropType) {
      if (ev.getEntityType() != IcalDefs.entityTypeTodo) {
        return new UpdateResult("DUE only valid for tasks");
      }
    } else {
      if (ev.getEntityType() == IcalDefs.entityTypeTodo) {
        return new UpdateResult("DUE required for tasks");
      }
    }

    DatesState ds = (DatesState)ui.getState(DatesState.stateName);
    if (ds == null) {
      ds = new DatesState(ev);
      ui.saveState(DatesState.stateName, ds);
    }

    ChangeTableEntry cte = ui.getCte();
    if (ui.isRemove()) {
      if (ev.getEndType() != StartEndComponent.endTypeDate) {
        return new UpdateResult("Entity has no end date - cannot remove");
      }
      cte.setDeleted(ev.getDtend());
      ds.end = null;
      // Finish off later

      return UpdateResult.getOkResult();
    }

    if (ui.isAdd()) {
      if (ev.getEndType() == StartEndComponent.endTypeDate) {
        return new UpdateResult("Entity already has end date - cannot add");
      }

      ds.end = BwDateTime.makeBwDateTime(dt);
      cte.setAdded(ds.end);
      return UpdateResult.getOkResult();
    }

    /* Changing dtend - either value or parameters */
    if (ev.getEndType() != StartEndComponent.endTypeDate) {
      return new UpdateResult("Entity has no end date - cannot change");
    }

    Holder<BwDateTime> resdt = new Holder<BwDateTime>();

    UpdateResult ur = makeDt(ev.getDtend(), resdt, ui);
    if (!ur.getOk()) {
      return ur;
    }

    if (resdt.value != null) {
      cte.setChanged(ev.getDtend(), resdt.value);
      ds.end = resdt.value;
    }

    return UpdateResult.getOkResult();
  }
}
