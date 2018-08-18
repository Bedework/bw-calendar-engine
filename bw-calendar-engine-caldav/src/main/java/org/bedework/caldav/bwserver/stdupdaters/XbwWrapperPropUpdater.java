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
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.TextListPropertyType;
import ietf.params.xml.ns.icalendar_2.XBedeworkWrapperPropType;

import java.util.List;

/** Updates the x-property wrapped by this property.
 *
 * @author douglm
 */
@SuppressWarnings("UnusedDeclaration")
public class XbwWrapperPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    final ChangeTableEntry cte = ui.getCte();
    final BwEvent ev = ui.getEvent();

    /* Create an x-property from the selector */

    final XBedeworkWrapperPropType wrapper =
            (XBedeworkWrapperPropType)ui.getProp();

    final BwXproperty theProp = new BwXproperty(
            UpdaterUtil.getWrapperName(wrapper),
            UpdaterUtil.getParams(wrapper),
            wrapper.getText());

    if (ui.isRemove()) {
      ev.removeXproperty(theProp);
      cte.addRemovedValue(theProp);

      return UpdateResult.getOkResult();
    }

    if (ui.isAdd()) {
      ev.addXproperty(theProp);
      cte.addValue(theProp);

      return UpdateResult.getOkResult();
    }

    if (ui.isChange()) {
      ev.removeXproperty(theProp);
      cte.addRemovedValue(theProp);

      final BwXproperty newProp = new BwXproperty(
              UpdaterUtil.getWrapperName(wrapper),
              UpdaterUtil.getParams(ui.getUpdprop()),
              ((XBedeworkWrapperPropType)ui.getUpdprop()).getText());
      ev.addXproperty(newProp);
      cte.addValue(newProp);

      return UpdateResult.getOkResult();
    }

    return UpdateResult.getOkResult();
  }

  private String getValue(final BasePropertyType bp) throws WebdavException {
    final List<String> xvals = ((TextListPropertyType)bp).getText();

      /* We normalize to a single category per property */
    if (xvals.size() != 1) {
      throw new WebdavException("Entity categories not normalized");
    }

    return xvals.get(0);
  }

  private BwXproperty makeXprop(final String lang,
                                final String val) {
    final String pars;
    if (lang == null) {
      pars = null;
    } else {
      pars = "lang=" + lang;
    }

    return new BwXproperty(BwXproperty.xBedeworkCategories,
                           pars, val);
  }
}
