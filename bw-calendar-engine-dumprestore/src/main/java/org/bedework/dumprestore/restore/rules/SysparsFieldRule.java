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

import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.BwSystem;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public class SysparsFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

    skippedNames.add("id");
    skippedNames.add("seq");
    skippedNames.add("properties");
  }

  SysparsFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Exception {
    if (skippedNames.contains(name)) {
      return;
    }

    if (top() instanceof BwProperty) {
      // Processing a property
      return;
    }

    BwSystem ent = (BwSystem)top();

    if (name.equals("name")) {
      ent.setName(stringFld());
    } else if (name.equals("tzid")) {
    } else if (name.equals("systemid")) {
    } else if (name.equals("principalRoot")) {
    } else if (name.equals("userPrincipalRoot")) {
    } else if (name.equals("groupPrincipalRoot")) {
    } else if (name.equals("publicCalendarRoot")) {
    } else if (name.equals("userCalendarRoot")) {
    } else if (name.equals("userDefaultCalendar")) {
    } else if (name.equals("defaultTrashCalendar")) {
    } else if (name.equals("userInbox")) {
    } else if (name.equals("userOutbox")) {
    } else if (name.equals("defaultNotificationsName")) {
    } else if (name.equals("deletedCalendar")) {
    } else if (name.equals("busyCalendar")) {
    } else if (name.equals("defaultUserViewName")) {
    } else if (name.equals("defaultUserHour24")) {
    } else if (name.equals("publicUser")) {
    } else if (name.equals("httpConnectionsPerUser")) {
    } else if (name.equals("httpConnectionsPerHost")) {
    } else if (name.equals("httpConnections")) {
    } else if (name.equals("maxPublicDescriptionLength")) {
    } else if (name.equals("maxUserDescriptionLength")) {
    } else if (name.equals("maxUserEntitySize")) {
    } else if (name.equals("defaultUserQuota")) {
    } else if (name.equals("maxInstances")) {
    } else if (name.equals("maxYears")) {
    } else if (name.equals("userauthClass")) {
    } else if (name.equals("mailerClass")) {
    } else if (name.equals("admingroupsClass")) {
    } else if (name.equals("usergroupsClass")) {
    } else if (name.equals("directoryBrowsingDisallowed")) {
    } else if (name.equals("indexRoot")) {
    } else if (name.equals("localeList")) {
    } else if (name.equals("rootUsers")) {
    } else {
      unknownTag(name);
    }
  }
}
