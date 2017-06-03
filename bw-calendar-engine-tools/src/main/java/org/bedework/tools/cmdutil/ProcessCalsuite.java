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

/**
 * @author douglm
 *
 */
public class ProcessCalsuite extends ProcessUser {
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
                             "   switch to event owner for calsuite");

      return true;
    }
    
    final String account;

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

      BwPrincipal ownerPr = getSvci().getPrincipal(ownerHref);

      if (ownerPr == null) {
        error("No user with owner href " + ownerHref +
                      " in admin group " + adminGrp +
                      " for calsuite " + wd);
        return true;
      }
      
      pstate.setCalsuite(cs);
      
      account = ownerPr.getAccount();
    } finally {
      close();
    }
    
    setUser(account, false);

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
