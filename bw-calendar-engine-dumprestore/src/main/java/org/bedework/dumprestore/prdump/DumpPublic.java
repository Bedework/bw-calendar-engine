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
import org.bedework.dumprestore.Defs;
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
   */
  public DumpPublic(final DumpGlobals globals) {
    super(globals);
  }

  /**
   * @return true if ok
   */
  public boolean open() {
    return super.open("public");
  }

  /**
   */
  public void close() {
  }

  /** Dump everything owned by this principal
   *
   * @throws CalFacadeException on error
   */
  public void doDump() throws CalFacadeException {
    dumpCategories(true);
    dumpContacts(true);
    dumpLocations(true);

    try {
      makeDir(Defs.collectionsDirName, false);

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
