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

import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public class UserPrefsFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<>();

    skippedNames.add("properties");
    skippedNames.add("views");
    skippedNames.add("collectionPaths");
  }

  UserPrefsFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  /* (non-Javadoc)
   * @see org.apache.commons.digester.Rule#begin(java.lang.String, java.lang.String, org.xml.sax.Attributes)
   */
  @Override
  public void begin(final String namespace,
                    final String name,
                    final Attributes attributes) throws Exception {
    super.begin(namespace, name, attributes);

    if (name.equals("view")) {
      push(new BwView());
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.rules.EntityFieldRule#field(java.lang.String)
   */
  @Override
  public void field(final String name) throws Throwable{
    if (skippedNames.contains(name)) {
      return;
    }

    if (top() instanceof BwView) {
      BwView view = (BwView)top();

      if (name.equals("view")) {
        processViewEnd(view);
        return;
      }

      if (!viewField(view, name)) {
        unknownTag(name);
      }

      return;
    }

    BwPreferences p = (BwPreferences)getTop(BwPreferences.class, name);

    try {
      if (ownedEntityTags(p, name)) {
        return;
      }

      switch (name) {
        case "email":
          p.setEmail(stringFld());
          break;
        case "default-calendar-path":   // PRE3.5
          p.setDefaultCalendarPath(stringFld());
          break;
        case "defaultCalendarPath":
          p.setDefaultCalendarPath(stringFld());
          break;
        case "skinName":
          p.setSkinName(stringFld());
          break;
        case "skinStyle":
          p.setSkinStyle(stringFld());
          break;
        case "preferredView":
          p.setPreferredView(stringFld());
          break;
        case "preferredViewPeriod":
          p.setPreferredViewPeriod(stringFld());
          break;
        case "workDays":
          p.setWorkDays(stringFld());
          break;
        case "workdayStart":
          p.setWorkdayStart(intFld());
          break;
        case "workdayEnd":
          p.setWorkdayEnd(intFld());
          break;
        case "preferredEndType":
          p.setPreferredEndType(stringFld());
          break;
        case "userMode":
          p.setUserMode(intFld());
          break;
        case "pageSize":
          p.setPageSize(intFld());
          break;
        case "hour24":
          p.setHour24(booleanFld());
          break;
        case "scheduleAutoRespond":
          p.setScheduleAutoRespond(booleanFld());
          break;
        case "scheduleAutoCancelAction":
          p.setScheduleAutoCancelAction(intFld());
          break;
        case "scheduleDoubleBook":
          p.setScheduleDoubleBook(booleanFld());
          break;
        case "scheduleAutoProcessResponses":
          p.setScheduleAutoProcessResponses(intFld());

          break;
        case "byteSize":
          p.setByteSize(intFld());

          break;
        default:
          unknownTag(name);
          break;
      }
    } catch (Throwable t) {
      error("Exception setting prefs " + p);
      handleException(t);
    }
  }

  /* View definitions.
   */
  private boolean viewField(final BwView view, final String name) throws Throwable {
    if (name.equals("id") || name.equals("seq")) {
      return true;
    }

    if (name.equals("name")) {
      view.setName(stringFld());
      return true;
    }

    if (name.equals("path")) {
      view.addCollectionPath(stringFld());
      return true;
    }

    if (name.equals("byteSize")) {
      view.setByteSize(intFld());
      return true;
    }

    return false;
  }

  private void processViewEnd(final BwView view) throws Throwable {
    pop();

    BwPreferences p = (BwPreferences)getTop(BwPreferences.class, "view");

    p.addView(view);
  }
}

