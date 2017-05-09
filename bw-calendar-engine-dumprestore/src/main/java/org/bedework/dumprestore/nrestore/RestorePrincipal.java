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
package org.bedework.dumprestore.nrestore;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.Utils;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.dumprestore.restore.XmlFile;

import java.io.File;

/** Restore calendar data for the supplied principal from a dump in the
 * supplied directory.
 *
\ * @author Mike Douglass   douglm @ bedework.org
 * @version 4.0
 */
public class RestorePrincipal extends Restorer {
  private BwPrincipal pr;

  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   */
  public RestorePrincipal(final RestoreGlobals globals) {
    super(globals);
  }

  /**
   * @param pr the principal
   * @return true if ok
   * @throws CalFacadeException on error
   */
  public boolean open(final BwPrincipal pr) throws CalFacadeException {
    this.pr = pr;
    pushPath(globals.getDirPath());
    if (openDir(Utils.principalDirPath(pr)) == null) {
      return false;
    }
    
    incCount(RestoreGlobals.users);

//    getRi().startPrincipal(pr);
    return true;
  }
  
  /**
   */
  public void close() {
//    getRi().endPrincipal(pr);
    popPath();
  }

  /** Restore everything owned by this principal
   *
   * @throws CalFacadeException on error
   */
  public boolean doRestore() throws CalFacadeException {
    try {
      final String prPath = topPath();
      
      final File pdir =
              Utils.directory(prPath);
      if (pdir == null) {
        addInfo("No user data found at " + prPath);
        return false;
      }

      final XmlFile prXml =
              new XmlFile(pdir, "principal.xml", false);
      final XmlFile prefsXml =
              new XmlFile(pdir, "preferences.xml", false);

      final BwPrincipal pr =
              fxml.fromXml(prXml.getRoot(),
                           BwUser.class,
                           BwPrincipal.getRestoreCallback());

      final BwPreferences prefs =
              fxml.fromXml(prefsXml.getRoot(),
                           BwPreferences.class,
                           BwPreferences.getRestoreCallback());

      incCount(Counters.users);
      incCount(Counters.userPrefs);

      if (getGlobals().getDryRun()) {
        info(pr.toString());
        info(prefs.toString());
      } else {
        getRi().restorePrincipal(pr);
        getRi().restoreUserPrefs(prefs);
      }
      
      restoreCategories();
      restoreContacts();
      restoreLocations();
      
      restoreCollections();
    } catch (final CalFacadeException ce) {
      throw ce;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
    return true;
  }
  
  private void addInfo(final String msg) {
    globals.info.addLn(msg);
  }
}
