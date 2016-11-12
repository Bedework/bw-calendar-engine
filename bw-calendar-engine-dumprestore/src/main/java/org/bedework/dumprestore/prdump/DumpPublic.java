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
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalendarsI;
import org.bedework.dumprestore.dump.DumpGlobals;

/** Dump all calendar data for the public events.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 4.0
 */
public class DumpPublic extends DumpPrincipal {
  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   * @param globals for dump
   * @throws CalFacadeException on error
   */
  public DumpPublic(final DumpGlobals globals) throws CalFacadeException {
    super(globals);
  }

  /**
   * @return true if ok
   * @throws CalFacadeException on error
   */
  public boolean open() throws CalFacadeException {
    super.open("public");
    
    return true;
  }

  /**
   * @throws CalFacadeException on error
   */
  public void close() throws CalFacadeException {
  }

  /** Dump everything owned by this principal
   *
   * @throws CalFacadeException on error
   */
  public void doDump() throws CalFacadeException {
    dumpCategories();

    /* Dump calendar collections - as we go we will create location, contact and
     * category directories.
     */

    try {
      makeDir(collectionsDirName, false);

      final CalendarsI cols = getSvc().getCalendarsHandler();

      final BwCalendar pubcal = cols.getPublicCalendars();

      if (pubcal == null) {
        warn("Unable to fetch public calendar root");
        return;
      }
      
      dumpCol(pubcal, true);
    } finally {
      popPath();
    }
  }
}
