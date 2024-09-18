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
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.util.misc.Util;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.TextPropertyType;

import java.util.Set;

/**
 * @author douglm
 *
 */
@SuppressWarnings("UnusedDeclaration")
public class CommentPropUpdater implements PropertyUpdater {
  public UpdateResult applyUpdate(final UpdateInfo ui) {
    final ChangeTableEntry cte = ui.getCte();
    final BwEvent ev = ui.getEvent();

    final Set<BwString> comments = ev.getComments();

    final BwString cstr = new BwString(UpdaterUtil.getLang(ui.getProp()),
                                 ((TextPropertyType)ui.getProp()).getText());

    if (ui.isAdd()) {
      if (Util.isEmpty(comments)) {
        ev.addComment(cstr);
      } else {
        for (final BwString s: comments) {
          if (s.equals(cstr)) {
            // Already there
            return UpdateResult.getOkResult();
          }
        }
        ev.addComment(cstr);
      }

      cte.addAddedValue(cstr);

      return UpdateResult.getOkResult();
    }

    if (ui.isRemove()) {
      if (Util.isEmpty(comments)) {
        // Nothing to remove
        return UpdateResult.getOkResult();
      }

      if (ev.removeComment(cstr)) {
        cte.addRemovedValue(cstr);
      }

      return UpdateResult.getOkResult();
    }

    if (ui.isChange()) {
      // Change a value
      if (Util.isEmpty(comments)) {
        // Nothing to change
        return new UpdateResult("No comment to change");
      }

      for (final BwString s: comments) {
        if (s.equals(cstr)) {
          // Found

          final BwString newcstr = new BwString(UpdaterUtil.getLang(ui.getUpdprop()),
                                          ((TextPropertyType)ui.getUpdprop()).getText());

          if (s.update(newcstr)) {
            cte.addChangedValue(s);
          }
          return UpdateResult.getOkResult();
        }
      }
    }

    return UpdateResult.getOkResult();
  }
}
