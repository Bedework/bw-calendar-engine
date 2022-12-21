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
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextPropertyType;

/**
 * @author douglm
 *
 */
@SuppressWarnings("UnusedDeclaration")
public class DescriptionPropUpdater implements PropertyUpdater {
  @Override
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    final BwEvent ev = ui.getEvent();
    if (ui.isRemove()) {
      ui.getCte().setDeleted(ev.getDescription());
      ev.setDescription(null);

      return UpdateResult.getOkResult();
    }

    if (ui.getUpdprop() == null) {
      // No change - parameters only updated?
      return UpdateResult.getOkResult();
    }

    final String val = ((TextPropertyType)ui.getUpdprop()).getText();

    if (ui.getCte().setChanged(ev.getDescription(), val)) {
      ev.setDescription(val);
    }

    return UpdateResult.getOkResult();
  }
}
