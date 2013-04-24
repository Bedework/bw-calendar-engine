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
import org.bedework.calfacade.util.ChangeTableEntry;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cmt.calendar.XcalUtil;
import edu.rpi.sss.util.Util;

import ietf.params.xml.ns.icalendar_2.DateDatetimePropertyType;

import java.util.Set;

/**
 * @author douglm
 *
 */
public class ExdatePropUpdater extends DateDatetimePropUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      BwEvent ev = ui.getEvent();
      ChangeTableEntry cte = ui.getCte();

      Set<BwDateTime> evDts = ev.getExdates();
      DateDatetimePropertyType dt = (DateDatetimePropertyType)ui.getProp();

      String dtUTC = XcalUtil.getUTC(dt, ui.getTzs());

      if (ui.isRemove()) {
        removeDt(dtUTC, evDts, cte);

        return UpdateResult.getOkResult();
      }

      BwDateTime prdt = BwDateTime.makeBwDateTime(dt);

      if (ui.isAdd()) {
        addDt(prdt, evDts, cte);

        return UpdateResult.getOkResult();
      }

      /* Changing exdate maybe just changing the parameters (UTC unchanged) or
       * an actual value change. Second case is really a remove and add
       */

      BwDateTime newdt = BwDateTime.makeBwDateTime(
               (DateDatetimePropertyType)ui.getUpdprop());

      if (prdt.getDate().equals(newdt.getDate())) {
        // tzid or date only?
        if (prdt.getTzid().equals(newdt.getTzid()) &&
            (prdt.getDateType() == newdt.getDateType())) {
          // Unchanged
          return UpdateResult.getOkResult();
        } else {
          evDts.remove(prdt);
          evDts.add(newdt);
          cte.addChangedValue(newdt);
        }
      }

      /* Do remove then add */
      removeDt(prdt.getDate(), evDts, cte);
      addDt(newdt, evDts, cte);

      return UpdateResult.getOkResult();
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private boolean removeDt(final String dtUTC,
                           final Set<BwDateTime> evDts,
                           final ChangeTableEntry cte) {
    if (Util.isEmpty(evDts)) {
      // Nothing to remove
      return false;
    }

    for (BwDateTime evDt: evDts) {
      if (evDt.getDate().equals(dtUTC)) {
        evDts.remove(evDt);
        cte.addRemovedValue(evDt);
        break;
      }
    }

    return true;
  }

  private boolean addDt(final BwDateTime newdt,
                        final Set<BwDateTime> evDts,
                        final ChangeTableEntry cte) {
    if (!Util.isEmpty(evDts)) {
      for (BwDateTime evDt: evDts) {
        if (evDt.getDate().equals(newdt.getDate())) {
          // Already there
          return false;
        }
      }
    }

    evDts.add(newdt);
    cte.addAddedValue(newdt);

    return true;
  }
}
