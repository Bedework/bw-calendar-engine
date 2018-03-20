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

import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.UserAuth;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class ProcessAuthUser extends CmdUtilHelper {
  private final static String[] utypeWds = {
          "approver",
          "publicevents",
          "content"
  };
  private final static Set<String> utypes =
          new TreeSet<>(Arrays.asList(utypeWds));

  ProcessAuthUser(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      pstate.addInfo("authuser <account> <action>\n" +
                             "   carry out action on given user(s)\n" +
                             "account may be '*' for all users\n" +
                             "action may be\n" +
                             "   approver|publicevents|content=false/true");

      return true;
    }

    final boolean all = "*".equals(wd);

    final String wd1 = word();
    Boolean val = null;

    if (wd1 != null) {
      if (!utypes.contains(wd1) || !testToken('=')) {
        pstate.addError("Expected <action>=\"");
        return false;
      }

      val = boolFor(wd1);

      if (val == null) {
        return false;
      }
    }

    try {
      open();

      if (!all) {
        final UserAuth ua = getSvci().getUserAuth();
        final BwAuthUser au = ua.getUser(wd);

        if (au == null) {
          pstate.addError("Unknown auth user " + wd);
          return false;
        }

        doUser(au, wd1, val);
        return true;
      }

      for (final BwAuthUser au : getSvci().getUserAuth().getAll()) {
        doUser(au, wd1, val);
      }

      return true;
    } finally {
      close();
    }
  }
  
  protected void doUser(final BwAuthUser au,
                        final String fld,
                        final Boolean val) throws Throwable {
    if (fld == null) {
      pstate.addInfo("authuser: " + au.getUserHref() +
      " approver: " + au.isApproverUser() +
      " publicevents: " + au.isPublicEventUser() +
      " content: " + au.isPublicEventUser());
      return;
    }

    int typeval = -1;

    switch (fld) {
      case "approver":
        typeval = UserAuth.approverUser;
        break;
      case "publicevents":
        typeval = UserAuth.publicEventUser;
        break;
      case "content":
        typeval = UserAuth.contentAdminUser;
    }

    int utype = au.getUsertype();
    if (val) {
      utype |= typeval;
    } else {
      utype &= ~typeval;
    }

    au.setUsertype(utype);

    getSvci().getUserAuth().updateUser(au);
  }

  @Override
  String command() {
    return "authuser";
  }

  @Override
  String description() {
    return "set role of authuser";
  }
}
