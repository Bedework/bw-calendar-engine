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

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.util.misc.response.Response;
import org.bedework.webdav.servlet.shared.WebdavException;

/**
 * @author douglm
 *
 */
public class CategoryPropUpdater extends LangTextListPropUpdater {
  @Override
  protected void addValue(final BwEvent ev,
                          final BwString val,
                          final UpdateInfo ui) throws WebdavException {
    if (ev.getNumCategories() == 0) {
      // Nothing to do
      return;
    }

    BwCategory cat = null;

    for (BwCategory evcat: ev.getCategories()) {
      if (evcat.getWord().equals(val)) {
        // Found the category attached to the event.
        cat = evcat;
        break;
      }
    }

    if (cat != null) {
      // Nothing to do
      return;
    }

    var resp = ui.getIcalCallback().findCategory(val);

    if (resp.getStatus() == Response.Status.notFound) {
      cat = BwCategory.makeCategory();
      cat.setWord(val);

      ui.getIcalCallback().addCategory(cat);
    } else if (resp.isOk()) {
      cat = resp.getEntity();
    } else {
      throw new WebdavException(ui.getPropName().toString() +
                                        ": failed. Status: " + resp.getStatus() +
                                        ", msg: " + resp.getMessage());
    }

    ev.addCategory(cat);
    ui.getCte().addAddedValue(cat);
  }

  @Override
  protected boolean removeValue(final BwEvent ev,
                                final BwString val,
                                final UpdateInfo ui) throws WebdavException {
    if (ev.getNumCategories() == 0) {
      return false;
    }

    BwCategory cat = null;

    for (BwCategory evcat: ev.getCategories()) {
      if (evcat.getWord().equals(val)) {
        // Found the category attached to the event.
        cat = evcat;
        break;
      }
    }

    if (cat == null) {
      // Nothing to do
      return true;
    }

    ev.removeCategory(cat);
    ui.getCte().addRemovedValue(cat);

    return true;
  }
}
