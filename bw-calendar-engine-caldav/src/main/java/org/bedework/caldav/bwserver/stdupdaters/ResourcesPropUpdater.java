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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.util.misc.Util;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;

import java.util.Set;

/**
 * @author douglm
 *
 */
public class ResourcesPropUpdater extends LangTextListPropUpdater {
  @Override
  protected void addValue(final BwEvent ev,
                          final BwString val,
                          final UpdateInfo ui) throws WebdavException {
    Set<BwString> ress = ev.getResources();

    if (Util.isEmpty(ress) || ress.contains(val)) {
      // Nothing to do
      return;
    }

    ress.add(val);

    ui.getCte().addAddedValue(val);
  }

  @Override
  protected boolean removeValue(final BwEvent ev,
                                final BwString val,
                                final UpdateInfo ui) throws WebdavException {
    Set<BwString> ress = ev.getResources();

    if (Util.isEmpty(ress)) {
      return false;
    }

    if (!ress.contains(val)) {
      // Nothing to do
      return true;
    }

    ev.removeResource(val);
    ui.getCte().addRemovedValue(val);

    return true;
  }
}
