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
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.XcalTags;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.RelatedToPropType;
import ietf.params.xml.ns.icalendar_2.ReltypeParamType;

import java.util.Arrays;

/**
 * @author douglm
 *
 */
public class RelatedToPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    ChangeTableEntry cte = ui.getCte();
    BwEvent ev = ui.getEvent();

    RelatedToPropType pr = (RelatedToPropType)ui.getProp();
    ReltypeParamType rtparm = (ReltypeParamType)UpdaterUtil.getParam(pr,
                                                                     XcalTags.reltype);
    String propRelType = "";
    String propValType;
    String propVal;

    if (rtparm != null) {
      propRelType = rtparm.getText();
    }

    if (pr.getText() != null) {
      propValType = "";
      propVal = pr.getText();
    } else if (pr.getUri() != null) {
      propValType = "URI";
      propVal = pr.getUri();
    } else {
      propValType = "UID";
      propVal = pr.getUid();
    }

    /* We encode related to (maybe) as triples - reltype, value-type, value */

    String[] info = null;
    BwXproperty xprop = null;

    BwRelatedTo relto = ev.getRelatedTo();
    if (relto != null) {
      info = new String[3];

      info[0] = relto.getRelType();
      info[1] = ""; // default
      info[2] = relto.getValue();
    } else {
      xprop = ev.findXproperty(BwXproperty.bedeworkRelatedTo);

      if (xprop != null) {
        String relx = xprop.getValue();

        if (relx != null) {
          info = Util.decodeArray(relx);
        }
      }
    }

    int pos = findRT(info, propRelType, propValType, propVal);

    if (ui.isAdd()) {
      if (pos >= 0) {
        // Already there
        return UpdateResult.getOkResult();
      }

      int iPos = info.length;
      info = Arrays.copyOf(info, info.length + 3);
      info[iPos] = propRelType;
      info[iPos + 1] = propValType;
      info[iPos + 2] = propVal;

      ev.setRelatedTo(null);
      if (xprop == null) {
        xprop = new BwXproperty(BwXproperty.bedeworkRelatedTo, null, null);
        ev.addXproperty(xprop);
      }

      String newval = Util.encodeArray(info);
      xprop.setValue(newval);

      cte.addAddedValue(newval);

      return UpdateResult.getOkResult();
    }

    if (ui.isRemove()) {
      if (pos < 0) {
        // Nothing to remove
        return UpdateResult.getOkResult();
      }

      if (info.length == 3) {
        // removing only related-to
        ev.setRelatedTo(null);
        ev.removeXproperties(BwXproperty.bedeworkRelatedTo);
        return UpdateResult.getOkResult();
      }

      String[] newInfo = new String[info.length - 3];
      for (int i = 0; i < pos; i++) {
        newInfo[i] = info[i];
      }

      for (int i = pos + 3; i < info.length; i++) {
        newInfo[i] = info[i];
      }

      ev.setRelatedTo(null);
      if (xprop == null) {
        xprop = new BwXproperty(BwXproperty.bedeworkRelatedTo, null, null);
        ev.addXproperty(xprop);
      }

      String newval = Util.encodeArray(newInfo);
      xprop.setValue(newval);

      cte.addRemovedValue(newval);

      return UpdateResult.getOkResult();
    }

    if (ui.isChange()) {
      // Change a value
      if (pos < 0) {
        // Nothing to change
        return new UpdateResult("No comment to change");
      }

      info[pos] = propRelType;
      info[pos + 1] = propValType;
      info[pos + 2] = propVal;

      ev.setRelatedTo(null);
      if (xprop == null) {
        xprop = new BwXproperty(BwXproperty.bedeworkRelatedTo, null, null);
        ev.addXproperty(xprop);
      }

      String newval = Util.encodeArray(info);
      xprop.setValue(newval);

      cte.addChangedValue(newval);

      return UpdateResult.getOkResult();
    }

    return UpdateResult.getOkResult();
  }

  private int findRT(final String[] evRt,
                     final String propRelType,
                     final String propValType,
                     final String propVal) {
    if (evRt == null) {
      return -1;
    }

    int i = 0;

    while (i < evRt.length) {
      if (!evRt[i].equals(propRelType)) {
        i += 3;
        continue;
      }

      if (!evRt[i + 1].equals(propValType)) {
        i += 3;
        continue;
      }

      if (evRt[i + 2].equals(propVal)) {
        return i;
      }

      i += 3;
    }

    return -1;
  }
}
