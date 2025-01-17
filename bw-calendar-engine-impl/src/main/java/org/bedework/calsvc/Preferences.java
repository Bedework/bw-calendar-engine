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
package org.bedework.calsvc;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.prefs.BwCommonUserPrefs;
import org.bedework.calfacade.svc.prefs.CalendarPref;
import org.bedework.calfacade.svc.prefs.CategoryPref;
import org.bedework.calfacade.svc.prefs.ContactPref;
import org.bedework.calfacade.svc.prefs.LocationPref;
import org.bedework.calsvci.PreferencesI;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.Collections;

import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;

/** This acts as an interface to the database for user preferences.
 *
 * @author Mike Douglass       douglm - bedework.edu
 */
class Preferences extends CalSvcDb implements PreferencesI {
  private BwPreferences prefs;

  /**
   * @param svci interface
   */
  Preferences(final CalSvc svci) {
    super(svci);
  }

  /** Call at svci open
   *
   */
  @Override
  public void open() {
    super.open();
    prefs = null;
  }

  /** Call at svci close
   *
   */
  @Override
  public void close() {
    super.close();
    prefs = null;
  }

  @Override
  public BwPreferences get() {
    if (prefs != null) {
      if (prefs.getOwnerHref() == null) {
        if (getPrincipal().getUnauthenticated()) {
          return prefs;
        }
      } else if (prefs.getOwnerHref().equals(getPrincipal().getPrincipalRef())) {
        return prefs;
      }
    }

    prefs = fetch();

    if (prefs == null) {
      // An uninitialised user?

      if (getPrincipal().getUnauthenticated()) {
        prefs = new BwPreferences();
        return prefs;
      }

      getSvc().getUsersHandler().initPrincipal(getPrincipal());

      prefs = fetch();
    }

    if (prefs == null) {
      throw new BedeworkException(
              "org.bedework.unable.to.initialise");
    }

    return prefs;
  }

  @Override
  public BwPreferences get(final BwPrincipal<?> principal) {
     return fetch(principal);
  }

  @Override
  public void update(final BwPreferences val) {
    if (val.getPublick() == null) {
      // Fix the data
      val.setPublick(val.getOwnerHref().equals(BwPrincipal.publicUserHref));
    }

    getCal().saveOrUpdate(val);
  }

  @Override
  public void delete(final BwPreferences val) {
    getCal().delete(val);
    getCal().getIndexer(docTypePreferences)
            .unindexEntity(val.getHref());
  }

  public void updateAdminPrefs(final boolean remove,
                               final BwEventProperty<?> ent) {
    if (ent instanceof BwCategory) {
      updateAdminPrefs(remove, null, 
                       Collections.singletonList((BwCategory)ent), 
                       null, null);
    } else if (ent instanceof BwLocation) {
      updateAdminPrefs(remove, null, null, (BwLocation)ent, null);
    } else if (ent instanceof BwContact) {
      updateAdminPrefs(remove, null, null, null, (BwContact)ent);
    }
  }

  public void updateAdminPrefs(final boolean remove,
                               final BwCalendar cal,
                               final Collection<BwCategory> cats,
                               final BwLocation loc,
                               final BwContact ctct) {
    BwCommonUserPrefs prefs = null;
    boolean update = false;
    BwAuthUser au = null;

    if (getPars().getPublicAdmin()) {
      au = getSvc().getUserAuth().getUser(getPars().getAuthUser());

      if (au != null) {
        prefs = au.getPrefs();
      }
    }

    if (prefs == null) {
      // XXX until we get non admin user preferred calendars etc
      return;
    }

    if (cal != null) {
      if (!remove) {
        if (cal.getCalendarCollection()) {
          final CalendarPref p = prefs.getCalendarPrefs();
          if (p.getAutoAdd() && p.add(cal)) {
            update = true;
          }
        }
      } else {
        getSvc().removeFromAllPrefs(cal);
      }
    }

    if (!Util.isEmpty(cats)) {
      for (final BwCategory cat: cats) {
        if (!remove) {
          final CategoryPref p = prefs.getCategoryPrefs();
          if (p.getAutoAdd() && p.add(cat)) {
            update = true;
          }
        } else {
          getSvc().removeFromAllPrefs(cat);
        }
      }
    }

    if (loc != null) {
      if (!remove) {
        final LocationPref p = prefs.getLocationPrefs();
        if (p.getAutoAdd() && p.add(loc)) {
          update = true;
        }
      } else {
        getSvc().removeFromAllPrefs(loc);
      }
    }

    if (ctct != null) {
      if (!remove) {
        final ContactPref p = prefs.getContactPrefs();
        if (p.getAutoAdd() && p.add(ctct)) {
          update = true;
        }
      } else {
        getSvc().removeFromAllPrefs(ctct);
      }
    }

    if (update) {
      if (getPars().getPublicAdmin()) {
        getSvc().getUserAuth().updateUser(au);
      }
    }
  }

  @Override
  public String getAttachmentsPath() {
    String path = get().getAttachmentsPath();

    if (path == null) {
      path = Util.buildPath(BasicSystemProperties.colPathEndsWithSlash,
                            getSvc().getCalendarsHandler().getHomePath(),
                            "/",
                            "attachments");
      get().setAttachmentsPath(path);
      getSvc().getPrefsHandler().update(get());
    }

    return path;
  }

  @Override
  public void setAttachmentsPath(final String val) {
    if (val == null) {
      return;
    }

    get().setAttachmentsPath(val);
    getSvc().getPrefsHandler().update(get());
  }

  /* ====================================================================
   *                    Private methods
   * ==================================================================== */

  /** Fetch the preferences for the current user from the db
   *
   * @return the preferences for the current user
   */
  private BwPreferences fetch() {
    return fetch(getPrincipal());
  }

  /** Fetch the preferences for the given principal from the db
   *
   * @param principal owning the prefs
   * @return the preferences for the current user
   */
  private BwPreferences fetch(final BwPrincipal<?> principal) {
    final BwPreferences prefs = getSvc().getPreferences(principal.getPrincipalRef());

    final BwPrincipalInfo pinfo = principal.getPrincipalInfo();
    if (pinfo == null) {
      return prefs;
    }

    if (getSvc().getDirectories().mergePreferences(prefs, pinfo)) {
      getSvc().getPrefsHandler().update(prefs);
    }

    return prefs;
  }
}

