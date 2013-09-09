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
package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.OwnerUidKey;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.icalendar.IcalTranslator.SkipThis;

/** Retrieve an owner and leave on the stack.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 1.0
 */
public class OwnerRule extends CreatorRule {
  OwnerRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    error("OwnerRule called");
    BwPrincipal p = doPrincipal();

    if (top() instanceof OwnerUidKey) {
      OwnerUidKey key = (OwnerUidKey)top();

      key.setOwnerHref(p.getPrincipalRef());
      globals.inOwnerKey = false;
      return;
    }

    if (top() instanceof BwAdminGroup) {
      BwAdminGroup ag = (BwAdminGroup)top();

      if (name.equals("owner")) {
        ag.setOwnerHref(p.getPrincipalRef());
      } else if (name.equals("owner-key")) {     // PRE3.5
        ag.setOwnerHref(p.getPrincipalRef());
      } else {
        ag.setGroupOwnerHref(p.getPrincipalRef());
      }
      globals.inOwnerKey = false;
      return;
    }

    BwOwnedDbentity o = null;

    if (top() == null) {
      error("Null stack top when setting owner.  Match: " +
            getDigester().getMatch());
      return;
    }

    if (top() instanceof EventInfo) {
      o = ((EventInfo)top()).getEvent();
    } else if (!(top() instanceof BwOwnedDbentity)) {
      /* Possibly restoring old data? */

      if (top() instanceof BwOrganizer) {
        // No owner now
      } else if (top() instanceof BwView) {
        // No owner now
      } else if (top() instanceof SkipThis) {
      } else {
        // We expect organizer in old data
        warn("top() is not BwOwnedDbentity:" + top().getClass().getCanonicalName());
        warn("  match: " + getDigester().getMatch());
      }
      globals.inOwnerKey = false;
      return;
    } else {
      o = (BwOwnedDbentity)top();
    }

    o.setOwnerHref(p.getPrincipalRef());
    globals.inOwnerKey = false;
  }
}
