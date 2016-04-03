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

import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public class AdminGroupFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

    skippedNames.add("member");

    //PRE3.5

    skippedNames.add("groupOwner-key");
    skippedNames.add("member-key");
    skippedNames.add("owner-key");
  }

  AdminGroupFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Exception {
    if (skippedNames.contains(name)) {
      return;
    }

    BwAdminGroup ag = (BwAdminGroup)top();

    if (groupTags(ag, name)) {
      return;
    }

    if (name.equals("groupOwnerHref")) {
      ag.setGroupOwnerHref(principalHrefFld());
    } else if (name.equals("ownerHref")) {
      ag.setOwnerHref(principalHrefFld());

    } else if (name.equals("description")) {
      ag.setDescription(stringFld());
    } else if (name.equals("groupMembers")) {
    } else if (name.equals("byteSize")) {
      ag.setByteSize(intFld());
    } else {
      unknownTag(name);
    }
  }
}

