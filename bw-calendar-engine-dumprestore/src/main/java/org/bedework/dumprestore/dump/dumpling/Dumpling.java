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
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.base.DumpEntity.DumpType;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.dump.DumpGlobals;
import org.bedework.util.misc.Util;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.QName;

/** Helper classes for the calendar data dump utility.
 *
 * @author Mike Douglass   douglm  rpi.edu
 * @version 1.0
 *
 * @param <T> class we are dumping
 */
public class Dumpling<T extends DumpEntity> implements Defs {
  protected DumpGlobals globals;

  protected QName sectionTag;

  protected int countIndex;

  private transient Logger log;

  /**
   * @param globals our global stuff
   * @param sectionTag xml output tag
   * @param countIndex index into counters
   */
  public Dumpling(final DumpGlobals globals,
                  final QName sectionTag,
                  final int countIndex) {
    this.globals = globals;
    this.sectionTag = sectionTag;
    this.countIndex = countIndex;
  }

  /** Dump the whole section e.g. locations or events.
   * The order this takes place in is important for a couple of reasons.
   * <ul><li>Database constraints may require we restore in a certain order</li>
   * <li>The restore process also assumes the availability of some objects
   * either in internal tables or in the db.</li></ul>
   *
   * @param it iterator over the entities
   * @throws Throwable
   */
  public void dumpSection(final Iterator<T> it) throws Throwable {
    info("Dumping " + sectionTag.getLocalPart());

    tagStart(sectionTag);

    dumpCollection(it);

    tagEnd(sectionTag);
  }

  private void dumpCollection(final Iterator<T> it) throws Throwable {
    while (it.hasNext()) {
      final DumpEntity d = unwrap(it.next());

      globals.counts[countIndex]++;

      if ((globals.counts[countIndex] % 100) == 0) {
        info("        ... " + globals.counts[countIndex]);
      }

      if (d instanceof BwResource) {
        dumpResource((BwResource)d);
        continue;
      }

      if (d instanceof BwCalendar) {
        dumpCollection((BwCalendar)d);
        continue;
      }

      if (d instanceof BwEvent) {
        dumpEvent((BwEvent)d);
        continue;
      }

      /* Just dump any remaining classes - no special treatment */
      d.dump(globals.xml, DumpType.def);
    }
  }

  private void dumpResource(final BwResource r) throws Throwable {
    globals.di.getResourceContent(r);

    if (r.getContent() == null) {
      error("No content for resource " +
                    Util.buildPath(false, r.getColPath(), "/", r.getName()));
    }

    r.dump(globals.xml, DumpType.def);

    // Let GC take it away
    r.setContent(null);
  }

  private void dumpCollection(final BwCalendar col) throws Throwable {
    col.dump(globals.xml, DumpType.def);

    if (col.getInternalAlias() && !col.getTombstoned()) {
      final String target = col.getInternalAliasPath();

      final AliasInfo ai = new AliasInfo(col.getPath(),
                                         target,
                                         col.getPublick(),
                                         col.getOwnerHref());
      List<AliasInfo> ais = globals.aliasInfo.get(target);

      if (ais == null) {
        ais = new ArrayList<>();
        globals.aliasInfo.put(target, ais);
      }
      ais.add(ai);
      globals.counts[globals.aliases]++;
    }

    if (col.getExternalSub() && !col.getTombstoned()) {
      globals.counts[globals.externalSubscriptions]++;
      globals.externalSubs.add(AliasInfo.getExternalSubInfo(col.getPath(),
                                                            col.getPublick(),
                                                            col.getOwnerHref()));
    }

    // Should I be dumping external subscriptions?

    final Collection<BwCalendar> cs = globals.di.getChildren(col);
    if (cs != null) {
      //noinspection unchecked
      dumpCollection((Iterator<T>)cs.iterator());
    }
  }

  private void dumpEvent(final BwEvent ev) throws Throwable {
    ev.dump(globals.xml, DumpType.def);

    if (ev.getOverrides() != null) {
      globals.counts[globals.eventOverrides] += ev.getOverrides().size();
    }
  }

  private DumpEntity unwrap(final DumpEntity val) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      return val;
    }

    return ((CalendarWrapper)val).fetchEntity();
  }

  protected void tagStart(final QName tag) throws Throwable {
    globals.xml.openTag(tag);
  }

  protected void tagEnd(final QName tag) throws Throwable {
    globals.xml.closeTag(tag);
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

  protected void warn(final String msg) {
    getLog().warn(msg);
    if (globals.info != null) {
      globals.info.addLn("WARN:" + msg);
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

