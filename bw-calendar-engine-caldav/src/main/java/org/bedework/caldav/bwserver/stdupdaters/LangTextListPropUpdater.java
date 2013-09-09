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
import org.bedework.calfacade.BwString;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextListPropertyType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** handle properties which take a comma separated list and may have a lang param
 * e.g. catgories, resources.
 *
 * <p>Bedework normalizes such properties into a single collection. The update may
 * be the result of a client doing the equivalent of changing
 * CATEGORIES:catA,catC,catD
 * to
 * CATEGORIES:catA,catD,catF
 *
 * We'll do what amounts to a diff on the values and turn a change into a
 * series of deletes and adds.
 *
 * @author douglm
 *
 */
public abstract class LangTextListPropUpdater implements PropertyUpdater {
  protected abstract void addValue(BwEvent ev,
                                   BwString val,
                                   UpdateInfo ui) throws WebdavException;

  /**
   * @param ev
   * @param val
   * @param ui
   * @return false if collection is empty
   * @throws WebdavException
   */
  protected abstract boolean removeValue(BwEvent ev,
                                         BwString val,
                                         UpdateInfo ui) throws WebdavException;

  public UpdateResult applyUpdate(final UpdateInfo ui) throws WebdavException {
    BwEvent ev = ui.getEvent();
    Collection<String> adds = new ArrayList<String>();
    Collection<String> removes = new ArrayList<String>();

    /* Figure out what we need to add/remove */

    TextListPropertyType prop = (TextListPropertyType)ui.getProp();
    String lang = UpdaterUtil.getLang(ui.getProp());

    if (ui.isAdd()) {
      adds.addAll(prop.getText());
    } else if (ui.isRemove()) {
      removes.addAll(prop.getText());
    } else {
      // Diff the prop value and the updProp value
      TextListPropertyType updProp = (TextListPropertyType)ui.getUpdprop();
      List<String> oldVals = prop.getText();
      List<String> updVals = updProp.getText();

      for (String s: updVals) {
        if (!oldVals.contains(s)) {
          adds.add(s);
        }
      }

      for (String s: oldVals) {
        if (!updVals.contains(s)) {
          removes.add(s);
        }
      }
    }

    /* Now add categories to the event
     */

    for (String s: adds) {
      addValue(ev, new BwString(lang, s), ui);
    }

    /* Now remove categories from the event
     */

    for (String s: removes) {
      if (!removeValue(ev, new BwString(lang, s), ui)) {
        break;
      }
    }

    return UpdateResult.getOkResult();
  }
}
