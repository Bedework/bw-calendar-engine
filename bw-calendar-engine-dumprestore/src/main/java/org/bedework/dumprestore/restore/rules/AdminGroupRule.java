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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.bedework.access.WhoDefs;

import java.util.Collection;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AdminGroupRule extends EntityRule {
  /** Cobstructor
   *
   * @param globals
   */
  public AdminGroupRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwAdminGroup entity = (BwAdminGroup)pop();

    try {
      if (entity.getGroupOwnerHref() == null) {
        error("Missing group owner for admin group " + entity);
        return;
      }

      if (entity.getOwnerHref() == null) {
        error("Missing owner for admin group " + entity);
        return;
      }

      if (entity.getPrincipalRef() == null) {
        // Pre 3.5?
        globals.setPrincipalHref(entity);
      }

      globals.counts[globals.adminGroups]++;
      globals.principalsTbl.put(entity);

      if (globals.rintf != null) {
        globals.rintf.restoreAdminGroup(entity);

        /* Save members. */

        Collection<BwPrincipal> c = entity.getGroupMembers();
        if (c == null) {
          return;
        }

        for (BwPrincipal pr: c) {
          if ((pr.getKind() == WhoDefs.whoTypeUser) &&
              !globals.onlyUsersMap.check(pr.getPrincipalRef())) {
            continue;
          }

          globals.rintf.addAdminGroupMember(entity, pr);
        }

      }
    } catch (Throwable t) {
      error("Unable to restore admin group " + entity);
      throw new Exception(t);
    }
  }
}

