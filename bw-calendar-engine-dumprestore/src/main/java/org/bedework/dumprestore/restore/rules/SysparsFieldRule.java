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
 * @author Mike Douglass   douglm@rpi.edu
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

    /* Any values set in syspars take precedence */

    BwSystem syspars = globals.getSyspars();

    if (name.equals("name")) {
      ent.setName(parval(syspars.getName(), stringFld()));
    } else if (name.equals("tzid")) {
      ent.setTzid(parval(syspars.getTzid(), stringFld()));
    } else if (name.equals("systemid")) {
       ent.setSystemid(parval(syspars.getSystemid(), stringFld()));

    } else if (name.equals("principalRoot")) {
      // 3.3.1 and before
    } else if (name.equals("userPrincipalRoot")) {
      // 3.3.1 and before
    } else if (name.equals("groupPrincipalRoot")) {
      // 3.3.1 and before

    } else if (name.equals("publicCalendarRoot")) {
      ent.setPublicCalendarRoot(parval(syspars.getPublicCalendarRoot(),
                                       stringFld()));
    } else if (name.equals("userCalendarRoot")) {
      ent.setUserCalendarRoot(parval(syspars.getUserCalendarRoot(),
                                     stringFld()));
    } else if (name.equals("userDefaultCalendar")) {
      ent.setUserDefaultCalendar(parval(syspars.getUserDefaultCalendar(),
                                        stringFld()));
    } else if (name.equals("defaultTrashCalendar")) {
      ent.setDefaultTrashCalendar(parval(syspars.getDefaultTrashCalendar(),
                                         stringFld()));
    } else if (name.equals("userInbox")) {
      ent.setUserInbox(parval(syspars.getUserInbox(), stringFld()));
    } else if (name.equals("userOutbox")) {
      ent.setUserOutbox(parval(syspars.getUserOutbox(), stringFld()));
    } else if (name.equals("defaultNotificationsName")) {
      ent.setDefaultNotificationsName(parval(syspars.getDefaultNotificationsName(), stringFld()));
    } else if (name.equals("deletedCalendar")) {
      ent.setDeletedCalendar(parval(syspars.getDeletedCalendar(), stringFld()));
    } else if (name.equals("busyCalendar")) {
      ent.setBusyCalendar(parval(syspars.getBusyCalendar(), stringFld()));

    } else if (name.equals("defaultUserViewName")) {
      ent.setDefaultUserViewName(parval(syspars.getDefaultUserViewName(),
                                        stringFld()));
    } else if (name.equals("defaultUserHour24")) {
      ent.setDefaultUserHour24(booleanFld());

    } else if (name.equals("publicUser")) {
      ent.setPublicUser(parval(syspars.getPublicUser(), stringFld()));

    } else if (name.equals("httpConnectionsPerUser")) {
      // Unused
    } else if (name.equals("httpConnectionsPerHost")) {
      // Unused
    } else if (name.equals("httpConnections")) {
      // Unused

    } else if (name.equals("maxPublicDescriptionLength")) {
      ent.setMaxPublicDescriptionLength(intFld());
    } else if (name.equals("maxUserDescriptionLength")) {
      ent.setMaxUserDescriptionLength(intFld());
    } else if (name.equals("maxUserEntitySize")) {
      ent.setMaxUserEntitySize(intFld());
    } else if (name.equals("defaultUserQuota")) {
      ent.setDefaultUserQuota(parval(syspars.getDefaultUserQuota(),
                                     longFld()));

    } else if (name.equals("maxInstances")) {
      ent.setMaxInstances(intFld());
    } else if (name.equals("maxYears")) {
      ent.setMaxYears(intFld());

    } else if (name.equals("userauthClass")) {
      ent.setUserauthClass(parval(syspars.getUserauthClass(), stringFld()));
    } else if (name.equals("mailerClass")) {
      ent.setMailerClass(parval(syspars.getMailerClass(), stringFld()));
    } else if (name.equals("admingroupsClass")) {
      ent.setAdmingroupsClass(parval(syspars.getAdmingroupsClass(), stringFld()));
    } else if (name.equals("usergroupsClass")) {
      ent.setUsergroupsClass(parval(syspars.getUsergroupsClass(), stringFld()));

    } else if (name.equals("directoryBrowsingDisallowed")) {
      ent.setDirectoryBrowsingDisallowed(booleanFld());

    } else if (name.equals("indexRoot")) {
      ent.setIndexRoot(parval(syspars.getIndexRoot(), stringFld()));

    } else if (name.equals("localeList")) {
      ent.setLocaleList(parval(syspars.getLocaleList(), stringFld()));

    } else if (name.equals("rootUsers")) {
      ent.setRootUsers(parval(syspars.getRootUsers(), stringFld()));
    } else {
      unknownTag(name);
    }
  }

  private long parval(final long sysparVal, final long val) {
    if (sysparVal != 0) {
      return sysparVal;
    }

    return val;
  }

  private String parval(final String sysparVal, final String val) {
    if (sysparVal != null) {
      return sysparVal;
    }

    return val;
  }
}
