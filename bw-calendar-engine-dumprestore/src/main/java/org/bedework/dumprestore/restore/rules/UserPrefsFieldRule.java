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
import org.bedework.util.misc.Util;

import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm bedework.edu
 * @version 1.0
 */
public class UserPrefsFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

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

      if (name.equals("email")) {
        p.setEmail(stringFld());
      } else if (name.equals("defaultCalendarPath")) {
        p.setDefaultCalendarPath(Util.buildPath(true, stringFld()));
      } else if (name.equals("skinName")) {
        p.setSkinName(stringFld());
      } else if (name.equals("skinStyle")) {
        p.setSkinStyle(stringFld());
      } else if (name.equals("preferredView")) {
        p.setPreferredView(stringFld());
      } else if (name.equals("preferredViewPeriod")) {
        p.setPreferredViewPeriod(stringFld());
      } else if (name.equals("workDays")) {
        p.setWorkDays(stringFld());
      } else if (name.equals("workdayStart")) {
        p.setWorkdayStart(intFld());
      } else if (name.equals("workdayEnd")) {
        p.setWorkdayEnd(intFld());
      } else if (name.equals("preferredEndType")) {
        p.setPreferredEndType(stringFld());
      } else if (name.equals("userMode")) {
        p.setUserMode(intFld());
      } else if (name.equals("pageSize")) {
        p.setPageSize(intFld());
      } else if (name.equals("hour24")) {
        p.setHour24(booleanFld());
      } else if (name.equals("scheduleAutoRespond")) {
        p.setScheduleAutoRespond(booleanFld());
      } else if (name.equals("scheduleAutoCancelAction")) {
        p.setScheduleAutoCancelAction(intFld());
      } else if (name.equals("scheduleDoubleBook")) {
        p.setScheduleDoubleBook(booleanFld());
      } else if (name.equals("scheduleAutoProcessResponses")) {
        p.setScheduleAutoProcessResponses(intFld());

      } else if (name.equals("byteSize")) {
        p.setByteSize(intFld());

      } else {
        unknownTag(name);
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
      view.addCollectionPath(Util.buildPath(true, stringFld()));
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

