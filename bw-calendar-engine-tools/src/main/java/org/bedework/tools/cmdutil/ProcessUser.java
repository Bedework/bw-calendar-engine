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

/**
 * @author douglm
 *
 */
public class ProcessUser extends CmdUtilHelper {
  ProcessUser(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String wd = word();

    if (wd == null) {
      return false;
    }

    if ("help".equals(wd)) {
      pstate.addInfo("user <account> [super]\n" +
                             "   switch to given user - possibly run as super user");

      return true;
    }

    if (wd.equals(pstate.getAccount())) {
      return true; // No change
    }

    final String wd1 = word();

    if (wd1 != null) {
      if (!"super".equals(wd1)) {
        pstate.addError("Expected nothing or \"super\"");
        return false;
      }
    }

    setUser(wd, wd1 != null);

    return true;
  }
  
  protected void setUser(final String account, 
                         final boolean superUser) throws Throwable {
    if (account.equals(pstate.getAccount())) {
      info("Account is already " + account);
      return; // No change
    }
    
    info("Setting account to " + account);

    pstate.closeSvci();

    pstate.setAccount(account);
    pstate.setSuperUser(superUser);

    // Open to force creation of account
    try {
      open();
    } finally {
      close();
    }
  }

  @Override
  String command() {
    return "user";
  }

  @Override
  String description() {
    return "set user we are acting as";
  }
}
