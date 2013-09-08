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

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.dumprestore.restore.PrincipalHref;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.util.misc.Util;

import org.xml.sax.Attributes;

import java.util.ArrayList;

/** Retrieve a member and add to the group.
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class MemberRule extends RestoreRule {
  MemberRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) {
    push(new PrincipalHref());
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    /* Top should be the principal info, underneath is the actual entity -
     */

    PrincipalHref oi = (PrincipalHref)pop();

    try {
      if (oi.prefix == null) {
        error("Unable to handle principal type " + oi.getKind());
      }

      oi.href = Util.buildPath(true, oi.prefix, "/", oi.account);
    } catch (Throwable t) {
      error("Unable to get user principal root", t);
      return;
    }

    BwPrincipal pr = globals.principalsTbl.get(oi);

    if (top() instanceof BwGroup) {
      BwGroup gr = (BwGroup)top();
      if (pr == null) {
        if (gr instanceof BwAdminGroup) {
          ArrayList<PrincipalHref> m = globals.adminGroupMembers.get(gr.getAccount());
          if (m == null) {
            m = new ArrayList<PrincipalHref>();
            globals.adminGroupMembers.put(gr.getAccount(), m);
          }

          m.add(oi);
        } else {
          error("Cannot handle group " + gr);
        }
        return;
      }

      gr.addGroupMember(pr);
      return;
    }

    error("Unknown class for member " + top());
  }
}
