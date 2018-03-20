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

import org.bedework.calfacade.BwCalendar;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class CalendarFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

    skippedNames.add("categories");
    skippedNames.add("properties");
  }

  CalendarFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Throwable {
    if (skippedNames.contains(name)) {
      return;
    }

    BwCalendar ent = (BwCalendar)top();

    if (shareableContainedEntityTags(ent, name)) {
      return;
    }

    if (name.equals("name")) {
      ent.setName(stringFld());
    } else if (name.equals("path")) {
      ent.setPath(stringFld());
    } else if (name.equals("summary")) {
      ent.setSummary(stringFld());
    } else if (name.equals("description")) {
      ent.setDescription(stringFld());
    } else if (name.equals("mailListId")) {
      ent.setDescription(stringFld());
    } else if (name.equals("calType")) {
      ent.setCalType(intFld());
    } else if (name.equals("created")) {
      ent.setCreated(stringFld());
    } else if (name.equals("aliasUri")) {
      ent.setAliasUri(stringFld());
    } else if (name.equals("display")) {
      ent.setDisplay(booleanFld());
    } else if (name.equals("affectsFreeBusy")) {
      ent.setAffectsFreeBusy(booleanFld());
    } else if (name.equals("ignoreTransparency")) {
      ent.setIgnoreTransparency(booleanFld());
    } else if (name.equals("unremoveable")) {
      ent.setUnremoveable(booleanFld());
    } else if (name.equals("refreshRate")) {
      ent.setRefreshRate(intFld());
    } else if (name.equals("lastRefresh")) {
      ent.setLastRefresh(stringFld());
    } else if (name.equals("lastRefreshStatus")) {
      ent.setLastRefreshStatus(stringFld());
    } else if (name.equals("filterExpr")) {
      ent.setFilterExpr(stringFld());
    } else if (name.equals("remoteId")) {
      ent.setRemoteId(stringFld());
    } else if (name.equals("remotePw")) {
      ent.setRemotePw(stringFld());

    } else if (name.equals("category")) {
      // pre 3.5?
      //ent.addCategory(categoryFld());

    } else if (name.equals("byteSize")) {
      ent.setByteSize(intFld());

    } else if (name.equals("supportedComponents")) {
      // Ignore - got in by error

    } else {
      unknownTag(name);
    }
  }
}
