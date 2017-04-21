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
package org.bedework.dumprestore.prdump;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calsvci.CalendarsI;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.Utils;
import org.bedework.dumprestore.dump.DumpGlobals;
import org.bedework.util.misc.Util;

import java.io.File;
import java.util.Collection;

/** Dump all calendar data for the supplied principal.
 *
 * <p>We will create a directory for the principal under the supplied directory
 * path. So if we get a path of <br/>
 * /data/dumps <br/>
 * and we are dumping principal "/principals/user/mike" we will build the dumped
 * data under <br/>
 * /data/dumps/principals/user/m/mike <br/>
 * which we can refer to as dump-home
 * 
 * <p>The directory with the name "m" is the first lowercased character 
 * of the principal if in the set 0-9a-z. Anything else goes in "_". This
 * is all to reduce the number of files in directories so it is navigable</p>
 *
 * <p>We dump the data in standard forms where possible. These forms are:
 * <ul>
 * <li><em>BwPrincipal</em> vcard</li>
 * <li><em>BwLocation</em> vcard</li>
 * <li><em>BwContact</em> vcard</li>
 * <li><em>BwResource</em> Appropriate type if known otherwise binary</li>
 * <li><em>BwEvent</em> xCal</li>
 * </ul>
 *
 * <p>Many of these need to be accompanied by meta-data for the restore. These
 * will be output as XML meta-data files with the same name as the resource but
 * within a meta subdirectory. Each meta data file has a name with a prefix
 * indicating the type of file - either "file-" or "dir-". Some directorieshave
 * associated meta-data, for example shared calendars.
 *
 * <p>We build a directory structure under the dump-home. Within the dump-home we
 * write the principal vcard and the meta data directory for that vcard.
 *
 * <p>We create other directories as needed for events, locations, contacts etc.
 *
 * <p>At the moment categories consist mostly of meta-data. The created files are
 * simply to avoid a special case.
 *
 * <p>The directory structure under the events directory mirrors the structure
 * of the principals calendar home. In general we do not dump the contents of
 * shared calendars. For a full dump they are saved elsewhere. We will allow
 * users to optionally include the shared calendars - though this could result
 * in a vary large dump.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 4.0
 */
public class DumpPrincipal extends Dumper {
  private BwPrincipal pr;

  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   * @param globals for dump
   */
  public DumpPrincipal(final DumpGlobals globals) {
    super(globals);
  }

  /**
   * @param pr the principal
   * @return true if ok
   * @throws CalFacadeException on error
   */
  public boolean open(final BwPrincipal pr) throws CalFacadeException {
    this.pr = pr;
    if (!open(Utils.principalDirPath(pr))) {
      return false;
    }
    
    incCount(DumpGlobals.users);

    getDi().startPrincipal(pr);
    return true;
  }
  
  public boolean open(final String dirName) throws CalFacadeException {
    super.open();
    /* Create a directory for the principal */

    if (!makeDir(dirName, true)) {
      // Duplicate user entry?

      incCount(DumpGlobals.duplicateUsers);
      
      for (int i = 0; i < 100; i++) {
        if (makeDir(dirName + "-dup-" + i, true)) {
          break;
        }
        
        if (i == 99) {
          addLn("Too many duplicates for " + dirName);
          return false;
        }
        
        popPath();
      }
    }
    
    return true;
  }

  /**
   * @throws CalFacadeException on error
   */
  public void close() throws CalFacadeException {
    getDi().endPrincipal(pr);
  }

  /** Dump everything owned by this principal
   *
   * @throws CalFacadeException on error
   */
  public void doDump() throws CalFacadeException {
    final File f = makeFile("principal.xml");

    pr.dump(f);

    final BwPreferences prefs = getSvc().getPrefsHandler().get();
    if (prefs == null) {
      warn("No preferences for " + pr.getPrincipalRef());
    } else {
      incCount(DumpGlobals.userPrefs);
      prefs.dump(makeFile("preferences.xml"));
    }
    
    dumpCategories(false);
    dumpContacts(false);
    dumpLocations(false);

    /* Dump calendar collections - as we go we will create location, contact and
     * category directories.
     */

    try {
      makeDir(Defs.collectionsDirName, false);

      final CalendarsI cols = getSvc().getCalendarsHandler();

      final BwCalendar home = cols.getHome();

      if (home == null) {
        warn("No home for " + pr.getPrincipalRef());
        return;
      }
      dumpCol(home, true);
    } finally {
      popPath();
    }
  }

  protected void dumpCol(final BwCalendar col, 
                         final boolean doChildren) throws CalFacadeException {
    final CalendarsI colsI = getSvc().getCalendarsHandler();

    try {
      incCount(DumpGlobals.collections);
      
      makeDir(col.getName(), false);

      col.dump(makeFile(col.getName() + ".xml"));

      /* Dump any events in this collection */

      /* Now dump any children */

      if (!doChildren || !col.getCollectionInfo().childrenAllowed) {
        return;
      }
      
      final Collection<BwCalendar> cols = colsI.getChildren(col);

      if (Util.isEmpty(cols)) {
        return;
      }

      for (final BwCalendar ch : cols) {
        dumpCol(ch, true);
      }
    } finally {
      popPath();
    }
  }
}
