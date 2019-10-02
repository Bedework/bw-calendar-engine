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
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.util.misc.Util;

import java.util.List;

/**
 * @author douglm
 *
 */
public class ProcessCalsuite extends CmdUtilHelper {
  ProcessCalsuite(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      pstate.addInfo("calsuite <name>\n" +
                             "   switch to event owner for calsuite\n" +
                             "calsuite <name> addapprover <account>\n" +
                             "   add an approver\n" +
                             "calsuite <name> remapprover <account>\n" +
                             "   remove an approver");

      return true;
    }
    
    String account = null;

    try {
      open();
      final BwCalSuite cs = getSvci().getCalSuitesHandler().get(wd);

      if (cs == null) {
        error("No calsuite with name " + wd);
        return true;
      }

      final BwAdminGroup adminGrp = cs.getGroup();

      if (adminGrp == null) {
        error("No admin group for calsuite " + wd);
        return true;
      }

      final String ownerHref = adminGrp.getOwnerHref();

      if (ownerHref == null) {
        error("No owner href in admin group " + adminGrp +
                      " for calsuite " + wd);
        return true;
      }

      final BwPrincipal ownerPr = getSvci().getPrincipal(ownerHref);

      if (ownerPr == null) {
        error("No user with owner href " + ownerHref +
                      " in admin group " + adminGrp +
                      " for calsuite " + wd);
        return true;
      }

      final String action = word();

      account = ownerPr.getAccount();
      if (action == null) {
        pstate.setCalsuite(cs);

        return true;
      }

      final boolean addAppprover = "addapprover".equals(action);
      if (!addAppprover && !"remapprover".equals(action)) {
        error("Expected addapprover or remapprover");
        return false;
      }

      final String appAccount = word();
      if (appAccount == null) {
        error("Expected an account");
      }

      final BwPreferences prefs = getSvci().getPrefsHandler()
                                           .get(ownerPr);

      final List<String> approvers = prefs.getCalsuiteApproversList();

      if (Util.isEmpty(approvers)) {
        if (addAppprover) {
          prefs.setCalsuiteApprovers(appAccount);
        }
      } else {
        if (addAppprover) {
          if (!approvers.contains(appAccount)) {
            approvers.add(appAccount);
          }
        } else {
          approvers.remove(appAccount);
        }

        prefs.setCalsuiteApprovers(String.join(",", approvers));
      }

      getSvci().getPrefsHandler().update(prefs);
    } finally {
      close();

      if (account != null) {
        setUser(account, false);
      }
    }

    return true;
  }

  @Override
  String command() {
    return "calsuite";
  }

  @Override
  String description() {
    return "set calsuite and user we are acting as";
  }
}
