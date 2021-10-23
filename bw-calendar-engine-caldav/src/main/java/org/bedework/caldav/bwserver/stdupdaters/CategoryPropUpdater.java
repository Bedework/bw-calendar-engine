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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.svc.BwPreferences.CategoryMapping;
import org.bedework.util.misc.response.GetEntityResponse;
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
    /* See if this is a value to be mapped */
    final CategoryMapping catMap =
            ui.getCatMapInfo().findMapping(val.getValue());

    BwCategory cat = null;

    for (final BwCategory evcat: ev.getCategories()) {
      if (evcat.getWord().equals(val)) {
        // Found the category attached to the event.
        cat = evcat;
        break;
      }
    }

    if (cat != null) {
      if (catMap == null) {
        // Nothing to do
        return;
      }

      // Delete the category - we are (now) mapping it.
      ev.removeCategory(cat);
      ui.getCte().addRemovedValue(cat);
    }

    if ((catMap != null) && (catMap.isTopicalArea())) {
      final BwCalendar mapTo = ui.getCatMapInfo().getTopicalArea(catMap);

      if (mapTo == null) {
        // Should warn
        return;
      }

      // Add an x-prop to define the alias. Categories will be added by realias.

      final BwCalendar aliasTarget = mapTo.getAliasTarget();
      final BwXproperty xp = BwXproperty.makeBwAlias(
              mapTo.getName(),
              mapTo.getAliasUri().substring(
                      BwCalendar.internalAliasUriPrefix.length()),
              aliasTarget.getPath(),
              mapTo.getPath());
      ev.addXproperty(xp);
      ui.getCte().addValue(xp);
      return;
    }

    final BwString catVal;
    if (catMap == null) {
      catVal = val;
    } else {
      // Mapped to different category
      catVal = new BwString(null, catMap.getTo());
    }

    final GetEntityResponse<BwCategory> resp =
            ui.getIcalCallback().findCategory(catVal);

    if (resp.getStatus() == Response.Status.notFound) {
      cat = BwCategory.makeCategory();
      cat.setWord(catVal);

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
