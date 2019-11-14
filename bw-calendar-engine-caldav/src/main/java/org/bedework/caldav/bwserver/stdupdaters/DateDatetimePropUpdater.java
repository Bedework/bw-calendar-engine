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
import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.DateDatetimePropertyType;
import ietf.params.xml.ns.icalendar_2.TzidParamType;

import javax.xml.ws.Holder;

/** Base class for DateDatetime properties - might also be used for alarm
 * properties
 *
 * @author douglm
 */
public abstract class DateDatetimePropUpdater extends AlarmPropUpdater {
  /** Used to retain state of the date/time changes to an entity
   *
   * @author douglm
   */
  public static class DatesState {
    /** */
    public BwDateTime start;
    /** */
    public BwDateTime end;
    /** */
    public String duration;

    /** */
    public static final String stateName = "org.bedework.updstate.dates";

    DatesState(final BwEvent ev) {
      if (!ev.getNoStart()) {
        start = ev.getDtstart();
      }

      if (ev.getEndType() == BwEvent.endTypeDate) {
        end = ev.getDtend();
      } else if (ev.getEndType() == BwEvent.endTypeDuration) {
        duration = ev.getDuration();
      }
    }
  }

  protected UpdateResult makeDt(final BwDateTime evdt,
                                final Holder<BwDateTime> resdt,
                                final UpdateInfo ui) throws WebdavException {
    String tzid = evdt.getTzid();
    String dtval = evdt.getDtval();
    boolean dateOnly = evdt.getDateType();

    BwDateTime newdt = null;

    /* New or changed tzid? */
    for (ParameterUpdater.UpdateInfo parui: ui.getParamUpdates()) {
      if (parui.getParam() instanceof TzidParamType) {
        if (parui.isRemove()) {
          tzid = null;
          break;
        }

        if (parui.isAdd()) {
          if (tzid != null) {
            return new UpdateResult(ui.getPropName().toString() +
                                            " already has tzid");
          }

          tzid = ((TzidParamType)parui.getParam()).getText();
          break;
        }

        if (tzid == null) {
          return new UpdateResult(ui.getPropName().toString() +
                                          " has no tzid to change");
        }

        tzid = ((TzidParamType)parui.getUpdparam()).getText();
        break;
      }
    }

    if (ui.getUpdprop() != null) {
      // Has new value
      DateDatetimePropertyType newdts = (DateDatetimePropertyType)ui.getUpdprop();

      dateOnly = newdts.getDate() != null;

      newdt = BwDateTime.makeBwDateTime(newdts, tzid);
    }

    if ((newdt == null) && (!Util.equalsString(tzid, evdt.getTzid()))) {
      // Tzid changed
      newdt = BwDateTime.makeBwDateTime(dateOnly, dtval, tzid);
    }

    if (newdt != null) {
      // Validate
      int res = newdt.validate();

      if (res == BwDateTime.dtBadDtval) {
        return new UpdateResult("Bad date value for " + ui.getPropName());
      }

      if (res == BwDateTime.dtBadTz) {
        return new UpdateResult("Bad tzid for " + ui.getPropName());
      }
    }

    resdt.value = newdt;

    return UpdateResult.getOkResult();
  }
}
