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
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.Utils;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.util.misc.Util;

import java.io.File;
import java.nio.file.Path;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Restore public calendar data from a dump in the
 * supplied directory.
 *
\ * @author Mike Douglass   douglm @ bedework.org
 * @version 4.0
 */
public class RestorePublic extends Restorer {
  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   */
  public RestorePublic(final RestoreGlobals globals) {
    super(globals);
  }

  /**
   * @return true if ok
   * @throws CalFacadeException on error
   */
  public boolean open() throws CalFacadeException {
    pushPath(globals.getDirPath());
    if (openDir("public") == null) {
      return false;
    }
    
    return true;
  }
  
  /**
   */
  public void close() {
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

      restoreCategories();
      restoreContacts();
      restoreLocations();
      
      restoreCollections();
      
      /* Restore all the admin groups
       */
      
      final Path agDirPath = openDir(Defs.adminGroupsDirName);
      if (agDirPath == null) {
        info("No admin groups data");
        return false;
      }
      
      final File agDir = agDirPath.toFile();
      if ((agDir == null) || !agDir.exists() || !agDir.isDirectory()) {
        info("No admin groups data");
        return false;
      }

      final String[] agDirs = agDir.list();

      if ((agDirs == null) || (agDirs.length == 0)) {
        info("No admin groups data");
        return false;
      }

      for (final String agp: agDirs) {
        final BwPrincipal userPr = BwPrincipal.makeUserPrincipal();

        userPr.setAccount(agp);

        userPr.setPrincipalRef(
                Util.buildPath(colPathEndsWithSlash,
                               RestoreGlobals.getUserPrincipalRoot(), 
                               "/", agp));

        globals.setPrincipalHref(userPr);

        try (RestorePrincipal restorer = new RestorePrincipal(globals)) {
          restorer.open(userPr);

          restorer.doRestore();
        }
      }
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
