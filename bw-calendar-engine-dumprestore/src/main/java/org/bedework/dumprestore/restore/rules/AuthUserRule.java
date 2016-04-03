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

import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.svc.prefs.CalendarPref;
import org.bedework.calfacade.svc.prefs.CategoryPref;
import org.bedework.calfacade.svc.prefs.ContactPref;
import org.bedework.calfacade.svc.prefs.LocationPref;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AuthUserRule extends EntityRule {
  /** Constructor
   *
   * @param globals
   */
  public AuthUserRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) throws Exception {
    super.begin(ns, name, att);

    BwAuthUser au = (BwAuthUser)top();
    BwAuthUserPrefs prefs = au.getPrefs();

    if (prefs == null) {
      prefs = new BwAuthUserPrefs();
      au.setPrefs(prefs);
    }

    if (prefs.getCalendarPrefs() == null) {
      prefs.setCalendarPrefs(new CalendarPref());
    }

    if (prefs.getContactPrefs() == null) {
      prefs.setContactPrefs(new ContactPref());
    }

    if (prefs.getLocationPrefs() == null) {
      prefs.setLocationPrefs(new LocationPref());
    }

    if (prefs.getCategoryPrefs() == null) {
      prefs.setCategoryPrefs(new CategoryPref());
    }
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwAuthUser au = (BwAuthUser)pop();
    globals.counts[globals.authusers]++;

    try {
      globals.rintf.restoreAuthUser(au);
    } catch (Throwable t) {
      error("Error restoring " + au);
      throw new Exception(t);
    }
  }
}

