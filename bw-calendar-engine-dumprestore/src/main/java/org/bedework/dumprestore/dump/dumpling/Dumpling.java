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
import org.bedework.calfacade.BwVersion;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.dumprestore.AliasEntry;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.dump.DumpGlobals;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;
import org.bedework.util.xml.XmlEmit;

import java.util.Collection;
import java.util.Iterator;

import javax.xml.namespace.QName;

/** Helper classes for the calendar data dump utility.
 *
 * @author Mike Douglass   douglm  rpi.edu
 * @version 1.0
 *
 * @param <T> class we are dumping
 */
public class Dumpling<T extends DumpEntity<?>>
        implements Defs, Logged {
  protected DumpGlobals globals;

  protected QName sectionTag;

  protected int countIndex;

  protected XmlEmit xml;

  /**
   * @param globals our global stuff
   * @param sectionTag xml output tag
   * @param countIndex index into counters
   */
  public Dumpling(final DumpGlobals globals,
                  final QName sectionTag,
                  final int countIndex,
                  final XmlEmit xml) {
    this.globals = globals;
    this.sectionTag = sectionTag;
    this.countIndex = countIndex;
    this.xml = xml;
  }

  /** Dump the whole section e.g. locations or events.
   * The order this takes place in is important for a couple of reasons.
   * <ul><li>Database constraints may require we restore in a certain order</li>
   * <li>The restore process also assumes the availability of some objects
   * either in internal tables or in the db.</li></ul>
   *
   * @param it iterator over the entities
   * @throws Throwable on fatal error
   */
  public void dumpSection(final Iterator<T> it) throws Throwable {
    info("Dumping " + sectionTag.getLocalPart());

    tagStart(sectionTag);

    if (it != null) {
      dumpCollection(it);
    }

    tagEnd(sectionTag);
  }

  protected void versionDate() {
    xml.property(new QName(majorVersionTag),
                 String.valueOf(BwVersion.bedeworkMajorVersion));
    xml.property(new QName(minorVersionTag),
                 String.valueOf(BwVersion.bedeworkMinorVersion));
    if (BwVersion.bedeworkUpdateVersion != 0) {
      xml.property(new QName(updateVersionTag),
                   String.valueOf(BwVersion.bedeworkUpdateVersion));
    }

    //noinspection ConstantConditions
    if (BwVersion.bedeworkPatchLevel != null) {
      xml.property(new QName(patchLevelTag),
                   BwVersion.bedeworkPatchLevel);
    }

    xml.property(new QName(versionTag), BwVersion.bedeworkVersion);

    xml.property(new QName(dumpDateTag), DateTimeUtil.isoDateTime());
  }

  private void dumpCollection(final Iterator<T> it) throws Throwable {
    while (it.hasNext()) {
      final T d = unwrap(it.next());

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
      d.dump(xml);
    }
  }

  private void dumpResource(final BwResource r) throws Throwable {
    globals.di.getResourceContent(r);

    if (r.getContent() == null) {
      error("No content for resource " +
                    Util.buildPath(false, r.getColPath(), "/", r.getName()));
    }

    r.dump(xml);

    // Let GC take it away
    r.setContent(null);
  }

  private void dumpCollection(final BwCalendar col) throws Throwable {
    col.dump(xml);

    if (col.getInternalAlias() && !col.getTombstoned()) {
      final String target = col.getInternalAliasPath();

      final AliasInfo ai = new AliasInfo(col.getPath(),
                                         target,
                                         col.getPublick(),
                                         col.getOwnerHref());
      AliasEntry ae = globals.aliasInfo.get(target);

      if (ae == null) {
        ae = new AliasEntry();
        ae.setTargetPath(target);

        globals.aliasInfo.put(target, ae);
      }
      ae.getAliases().add(ai);
      globals.counts[Counters.aliases]++;
    }

    if (col.getExternalSub() && !col.getTombstoned()) {
      globals.counts[Counters.externalSubscriptions]++;
      globals.externalSubs.add(AliasInfo.getExternalSubInfo(col.getPath(),
                                                            col.getAliasUri(),
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
    ev.dump(xml);

    if (ev.getOverrides() != null) {
      globals.counts[Counters.eventOverrides] += ev.getOverrides().size();
    }
  }

  private T unwrap(final T val) {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      return val;
    }

    return (T)((CalendarWrapper)val).fetchEntity();
  }

  protected void tagStart(final QName tag) {
    xml.openTag(tag);
  }

  protected void tagEnd(final QName tag) {
    xml.closeTag(tag);
  }

  public void info(final String msg) {
    Logged.super.info(msg);
    if (globals.info != null) {
      globals.info.addLn(msg);
    }
  }

  public void warn(final String msg) {
    Logged.super.warn(msg);
    if (globals.info != null) {
      globals.info.addLn("WARN:" + msg);
    }
  }

  public void error(final String msg) {
    Logged.super.error(msg);
    if (globals.info != null) {
      globals.info.addLn("ERROR:" + msg);
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

