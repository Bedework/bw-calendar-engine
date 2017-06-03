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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwView;

/**
 * @author douglm
 *
 */
public class ProcessAdd extends CmdUtilHelper {
  ProcessAdd(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      addInfo("add admbr <account> (group | user) to <group>\n" +
                      "   add member to admin group");

      addInfo("add vmbr \"<name>\" \"<href>\"\n" +
                      "   add collection to view");

      return true;
    }

    if ("admbr".equals(wd)) {
      return addToAdminGroup(wordOrQuotedVal());
    }

    if ("vmbr".equals(wd)) {
      return addToView(quotedVal());
    }

    return false;
  }

  @Override
  String command() {
    return "add";
  }

  @Override
  String description() {
    return "add member to admin group";
  }

  private boolean addToAdminGroup(final String account) throws Throwable {
    if (debug) {
      debug("About to add member " + account + " to a group");
    }
    
    if (account == null) {
      pstate.addError("Must supply account");
      return false;
    }
    
    final String kind = word();

    final boolean group;
    
    if ("group".equals(kind)) {
      group = true;
    } else if ("user".equals(kind)) {
      group = false;
    } else {
      pstate.addError("Invalid kind: " + kind);
      return false;
    }
    
    if (!"to".equals(word())) {
      pstate.addError("Invalid syntax - expected 'to'");
      return false;
    }

    final String toGrp = wordOrQuotedVal();
    
    try {
      open();

      final BwAdminGroup grp =
              (BwAdminGroup)getSvci().getAdminDirectories()
                                     .findGroup(toGrp);

      if (grp == null) {
        pstate.addError("Unknown group " + toGrp);
        return false;
      }

      if (grp.isMember(account, "group".equals(kind))) {
        pstate.addError("Already a member: " + account);
        return false;
      }

      final BwPrincipal nmbr = newMember(account, !group);

      getSvci().getAdminDirectories().addMember(grp, nmbr);
      getSvci().getAdminDirectories().updateGroup(grp);

      return true;
    } finally {
      close();
    }
  }

  private boolean addToView(final String name) throws Throwable {
    if (debug) {
      debug("About to add to view " + name);
    }

    if (name == null) {
      addError("Must supply name");
      return false;
    }

    try {
      open();

      final BwView view = getSvci().getViewsHandler().find(name);

      if (view == null) {
        addError("View " + name +
                         " not found");
        return false;
      }

      final BwCalendar col = getCal();
      
      if (col == null) {
        return false;
      }
      
      return getSvci().getViewsHandler().addCollection(name, 
                                                       col.getPath());
    } finally {
      close();
    }
  }
}
