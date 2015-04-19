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
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.svc.prefs.CalendarPref;
import org.bedework.calfacade.svc.prefs.CategoryPref;
import org.bedework.calfacade.svc.prefs.ContactPref;
import org.bedework.calfacade.svc.prefs.LocationPref;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.util.misc.Util;

/**
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public class AuthUserFieldRule extends EntityFieldRule {
  private boolean inCategoryPrefs;
  private boolean inCollectionsPrefs;
  private boolean inContactPrefs;
  private boolean inLocationPrefs;

  AuthUserFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void fieldStart(final String name) throws Exception {
    if (name.equals("categoryPrefs")) {
      inCategoryPrefs = true;
    } else if (name.equals("calendarPrefs")) {
      inCollectionsPrefs = true;
    } else if (name.equals("contactPrefs")) {
      inContactPrefs = true;
    } else if (name.equals("locationPrefs")) {
      inLocationPrefs = true;
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.rules.EntityFieldRule#field(java.lang.String)
   */
  @Override
  public void field(final String name) throws Throwable {
    BwEventProperty ep = null;
    BwCalendar cal = null;

    try {
      if (top() instanceof BwEventProperty) {
        ep = (BwEventProperty)pop();
      } else if (top() instanceof BwCalendar) {
        cal = (BwCalendar)pop();
      }

      BwAuthUser au = (BwAuthUser)top();

      if (name.equals("id") || name.equals("seq")) {
        return;
      }

      if (name.equals("userHref")) {
        au.setUserHref(Util.buildPath(true, stringFld()));
      //} else if (name.equals("account")) {   old?
      //  au.setUserHref(globals.rintf.getUser(stringFld()).getPrincipalRef());
      //} else if (name.equals("user")) {
      //  // done above
      } else if (name.equals("usertype")) {
        int type = intFld();

        au.setUsertype(type);

        /* Prefs stuff next */

      } else if (name.equals("autoAdd")) {
        if (inCategoryPrefs) {
          getCategoryPrefs(au).setAutoAdd(booleanFld());
        } else if (inCollectionsPrefs) {
          getCalendarPrefs(au).setAutoAdd(booleanFld());
        } else if (inContactPrefs) {
          getContactPrefs(au).setAutoAdd(booleanFld());
        } else if (inLocationPrefs) {
          getLocationPrefs(au).setAutoAdd(booleanFld());
        } else {
          error("Not in any prefs for autoAdd");
        }

      } else if (name.equals("category")) {
        au.getPrefs().getCategoryPrefs().add(((BwCategory)ep).getUid());
      } else if (name.equals("collection")) {
        au.getPrefs().getCalendarPrefs().add(cal);
      } else if (name.equals("contact")) {
        au.getPrefs().getContactPrefs().add((BwContact)ep);
      } else if (name.equals("location")) {
        au.getPrefs().getLocationPrefs().add((BwLocation)ep);

      } else if (name.equals("categoryPrefs")) {
        inCategoryPrefs = false;
      } else if (name.equals("calendarPrefs")) {
        inCollectionsPrefs = false;
      } else if (name.equals("contactPrefs")) {
        inContactPrefs = false;
      } else if (name.equals("locationPrefs")) {
        inLocationPrefs = false;

      } else if (name.equals("prefs")) {

      } else if (name.equals("byteSize")) {
      } else {
        unknownTag(name);
      }
    } catch (Throwable t) {
      handleException(t);
    }
  }

  /**
   * @param au
   * @return prefs
   */
  public BwAuthUserPrefs getPrefs(final BwAuthUser au) {
    BwAuthUserPrefs aup = au.getPrefs();

    if (aup == null) {
      aup = new BwAuthUserPrefs();
      au.setPrefs(aup);
    }

    return aup;
  }

  private CategoryPref getCategoryPrefs(final BwAuthUser au) {
    BwAuthUserPrefs aup = getPrefs(au);

    CategoryPref p = aup.getCategoryPrefs();
    if (p == null) {
      p = new CategoryPref();
      aup.setCategoryPrefs(p);
    }

    return p;
  }

  private LocationPref getLocationPrefs(final BwAuthUser au) {
    BwAuthUserPrefs aup = getPrefs(au);

    LocationPref p = aup.getLocationPrefs();
    if (p == null) {
      p = new LocationPref();
      aup.setLocationPrefs(p);
    }

    return p;
  }

  private ContactPref getContactPrefs(final BwAuthUser au) {
    BwAuthUserPrefs aup = getPrefs(au);

    ContactPref p = aup.getContactPrefs();
    if (p == null) {
      p = new ContactPref();
      aup.setContactPrefs(p);
    }

    return p;
  }

  private CalendarPref getCalendarPrefs(final BwAuthUser au) {
    BwAuthUserPrefs aup = getPrefs(au);

    CalendarPref p = aup.getCalendarPrefs();
    if (p == null) {
      p = new CalendarPref();
      aup.setCalendarPrefs(p);
    }

    return p;
  }
}

