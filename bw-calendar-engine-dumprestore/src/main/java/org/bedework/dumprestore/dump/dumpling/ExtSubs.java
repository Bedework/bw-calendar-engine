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
package org.bedework.dumprestore.dump.dumpling;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.dump.DumpGlobals;

import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.Iterator;

/** Get all external subscriptions for checking.
 *
 * @author Mike Douglass   douglm  rpi.edu
 * @version 1.0
 *
 */
public class ExtSubs implements Defs {
  protected DumpGlobals globals;

  private transient Logger log;

  /**
   * @param globals
   */
  public ExtSubs(final DumpGlobals globals) {
    this.globals = globals;
  }

  /** Iterate over the calendar collections creating a list of external
   * subscriptions.
   *
   * @throws Throwable
   */
  public void getSubs() throws Throwable {
    try {
      globals.svci.beginTransaction();

      getSubs(globals.di.getCalendars());

    } finally {
      globals.svci.endTransaction();
    }
  }

  private void getSubs(final Iterator<BwCalendar> it) throws Throwable {
    while (it.hasNext()) {
      globals.counts[globals.collections]++;

      if ((globals.counts[globals.collections] % 100) == 0) {
        info("        ... " + globals.counts[globals.collections]);
      }

      BwCalendar col = unwrap(it.next());

      if (col.getExternalSub() && !col.getTombstoned()) {
        globals.counts[globals.externalSubscriptions]++;
        globals.externalSubs.add(
                AliasInfo.getExternalSubInfo(col.getPath(),
                                             col.getAliasUri(),
                                             col.getPublick(),
                                             col.getOwnerHref()));
      }

      Collection<BwCalendar> cs = globals.di.getChildren(col);
      if (cs != null) {
        getSubs(cs.iterator());
      }
    }
  }

  private BwCalendar unwrap(final BwCalendar val) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      return val;
    }

    return ((CalendarWrapper)val).fetchEntity();
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final String msg) {
    getLog().info(msg);
    if (globals.info != null) {
      globals.info.addLn(msg);
    }
  }

  protected void error(final String msg) {
    getLog().error(msg);
    if (globals.info != null) {
      globals.info.addLn("ERROR:" + msg);
    }
  }

  protected void trace(final String msg) {
    getLog().debug(msg);
  }
}

