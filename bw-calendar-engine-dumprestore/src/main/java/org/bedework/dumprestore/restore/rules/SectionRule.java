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
import org.bedework.dumprestore.restore.PrincipalHref;
import org.bedework.dumprestore.restore.RestoreGlobals;

import edu.rpi.cmt.access.WhoDefs;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.Collection;

/** Flag start and end of a section
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class SectionRule extends RestoreRule {
  String sectionName;

  SectionRule(final RestoreGlobals globals, final String sectionName) {
    super(globals);

    this.sectionName = sectionName;
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) {
    info("Starting restore of " + sectionName);

    if ("calendars".equals(name)) {
      globals.rintf.setBatchSize(100);
    }
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    try {
      globals.rintf.setBatchSize(0);

      if ("calendars".equals(name)) {
        globals.rintf.endTransactionNow();
      } else if (name.equals("adminGroups")) {
        /* Add any remaining members */
        Collection<String> names = globals.adminGroupMembers.keySet();

        for (String n: names) {
          BwAdminGroup grp = globals.rintf.getAdminGroup(n);

          if (grp == null) {
            error("Unable to get group " + n);
            continue;
          }

          ArrayList<PrincipalHref> m = globals.adminGroupMembers.get(n);
          for (PrincipalHref pi: m) {
            BwPrincipal p = globals.principalsTbl.get(pi);
            if (p == null) {
              error("Unable to get principal for " + pi);
              continue;
            }

            grp.addGroupMember(p);

            globals.rintf.addAdminGroupMember(grp, p);
          }
        }
      }
    } catch (Throwable t) {
      throw new Exception(t);
    }
    info("Ending restore of " + sectionName);
  }
}
