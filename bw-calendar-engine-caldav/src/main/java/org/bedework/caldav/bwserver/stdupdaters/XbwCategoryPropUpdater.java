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
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.TextListPropertyType;

import java.util.List;
import java.util.Set;

/** Updates the x-property which saves the original category property
 * for the event.
 *
 * <p>We will also add a real category property if this property value
 * corresponds to a known bedework category</p>
 *
 * <p>We will assume the known categories are of the form <br/>
 *   /public/.bedework/categories/cat-name
 * </p>
 *
 * @author douglm
 */
@SuppressWarnings("UnusedDeclaration")
public class XbwCategoryPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    try {
      final ChangeTableEntry cte = ui.getCte();
      final BwEvent ev = ui.getEvent();

      final List<BwXproperty> xcats = ev.getXproperties(
              BwXproperty.xBedeworkCategories);

      final Set<BwCategory> cats = ev.getCategories();

      final String lang = UpdaterUtil.getLang(ui.getProp());
      final String xval = getValue(ui.getProp());

      final BwString cstr = new BwString(lang, xval);

      if (ui.isRemove()) {
        if (Util.isEmpty(xcats)) {
          // Nothing to remove
          return UpdateResult.getOkResult();
        }

        for (final BwXproperty xp: xcats) {
          if (!xp.getValue().equals(xval)) {
            continue;
          }

          // Found
          ev.removeXproperty(xp);
          cte.addRemovedValue(xp);

           /* Do we have a corresponding category */
          for (final BwCategory c: cats) {
            if (c.getWord().equals(cstr)) {
              ev.removeCategory(c);
              cte.addRemovedValue(c);
              break;
            }
          }

          return UpdateResult.getOkResult();
        }

        return UpdateResult.getOkResult();
      }

      if (ui.isAdd()) {
        for (final BwXproperty xp: xcats) {
          if (xp.getValue().equals(xval)) {
            return new UpdateResult(
                    "Entity already has " + ui.getPropName() +
                            " property with that value - cannot add");
          }
        }

        final BwXproperty xp = makeXprop(lang, xval);
        ev.addXproperty(xp);
        cte.addValue(xp);

        checkCategory(ui, ev, cats, lang, xval);

        return UpdateResult.getOkResult();
      }

      if (ui.isChange()) {
        for (final BwXproperty xp : xcats) {
          if (xp.getValue().equals(xval)) {
            // Found

            ev.removeXproperty(xp);
            cte.addRemovedValue(xp);

            final String nlang = UpdaterUtil
                    .getLang(ui.getUpdprop());
            final String nxval = getValue(ui.getUpdprop());

            final BwXproperty nxp = makeXprop(nlang, nxval);
            ev.addXproperty(nxp);
            cte.addValue(nxp);

            checkCategory(ui, ev, cats, nlang, nxval);

            return UpdateResult.getOkResult();
          }
        }
      }

      return UpdateResult.getOkResult();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
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

  private void checkCategory(final UpdateInfo ui,
                             final BwEvent ev,
                             final Set<BwCategory> cats,
                             final String lang,
                             final String val) throws CalFacadeException {
    final BwString sval = new BwString(lang, val);

    final BwCategory cat = ui.getIcalCallback().findCategory(sval);

    if (cat == null) {
      return;
    }

    for (final BwCategory c: cats) {
      if (c.getWord().equals(sval)) {
        // Already present
        return;
      }
    }

    ev.addCategory(cat);

    ui.getCte(PropertyIndex.PropertyInfoIndex.CATEGORIES).addValue(cat);
  }
}
