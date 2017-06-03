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

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;

/**
 * @author douglm
 *
 */
public class ProcessSetstatus extends CmdUtilHelper {
  ProcessSetstatus(final ProcessState pstate) {
    super(pstate);
  }

  @Override
  boolean process() throws Throwable {
    final String type = word();

    if (type == null) {
      return false;
    }

    if ("help".equals(type)) {
      addInfo("setstatus category <uid> \"status\"\\\n" +
                      "      change the status of the category with the given href");

      addInfo("setstatus contact <uid> \"status\"\\n" +
                      "      change the status of the contact with the given href");

      addInfo("setstatus location <uid> \"status\"\\n" +
                      "      change the status of the location with the given href");
      
      return true;
    }

    final String uid = quotedVal();
    
    if ("category".equals(type)) {
      return setStatusCategory(uid, wordOrQuotedVal());
    }

    if ("contact".equals(type)) {
      return setStatusContact(uid, wordOrQuotedVal());
    }

    if ("location".equals(type)) {
      return setStatusLocation(uid, wordOrQuotedVal());
    }

    return false;
  }

  @Override
  String command() {
    return "setstatus";
  }

  @Override
  String description() {
    return "set status for category, contact or location";
  }

  private boolean setStatusCategory(final String uid, 
                                    final String status) throws Throwable {
    try {
      if (uid == null) {
        error("Expected a uid");
        return false;
      }

      open();

      final BwCategory ent = getSvci().getCategoriesHandler().getPersistent(uid);

      if (ent == null) {
        error("No entity with uid " + uid);
        return false;
      }

      ent.setStatus(status);
      
      getSvci().getCategoriesHandler().update(ent);
      
      return true;
    } finally {
      close();
    }
  }

  private boolean setStatusContact(final String uid,
                                   final String status) throws Throwable {
    try {
      if (uid == null) {
        error("Expected a uid");
        return false;
      }

      open();

      final BwContact ent = getSvci().getContactsHandler().getPersistent(uid);

      if (ent == null) {
        error("No entity with uid " + uid);
        return false;
      }

      ent.setStatus(status);

      getSvci().getContactsHandler().update(ent);

      return true;
    } finally {
      close();
    }
  }

  private boolean setStatusLocation(final String uid,
                                    final String status) throws Throwable {
    try {
      if (uid == null) {
        error("Expected a uid");
        return false;
      }

      open();

      final BwLocation ent = getSvci().getLocationsHandler().getPersistent(uid);

      if (ent == null) {
        error("No entity with uid " + uid);
        return false;
      }

      ent.setStatus(status);

      getSvci().getLocationsHandler().update(ent);

      return true;
    } finally {
      close();
    }
  }
}
