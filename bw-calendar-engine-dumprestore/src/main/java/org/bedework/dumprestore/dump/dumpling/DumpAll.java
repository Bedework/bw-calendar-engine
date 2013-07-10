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

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.BwVersion;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.dumprestore.dump.DumpGlobals;

import edu.rpi.sss.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import javax.xml.namespace.QName;

/** Helper classes for the calendar data dump utility.
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class DumpAll extends Dumpling {
  /** Constructor
   *
   * @param globals
   */
  public DumpAll(final DumpGlobals globals) {
    super(globals, new QName(dumpTag), -1);
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.dump.dumpling.Dumpling#dumpSection(java.util.Iterator)
   */
  @Override
  public void dumpSection(final Iterator it) throws Throwable {
    tagStart(sectionTag);
    globals.xml.property(new QName(majorVersionTag),
                         String.valueOf(BwVersion.bedeworkMajorVersion));
    globals.xml.property(new QName(minorVersionTag),
                         String.valueOf(BwVersion.bedeworkMinorVersion));
    if (BwVersion.bedeworkUpdateVersion != 0) {
      globals.xml.property(new QName(updateVersionTag),
                           String.valueOf(BwVersion.bedeworkUpdateVersion));
    }

    if (BwVersion.bedeworkPatchLevel != null) {
      globals.xml.property(new QName(patchLevelTag),
                           BwVersion.bedeworkPatchLevel);
    }

    globals.xml.property(new QName(versionTag), BwVersion.bedeworkVersion);

    globals.xml.property(new QName(dumpDateTag), DateTimeUtil.isoDateTime());

    open();
    Collection<BwSystem> syspars = new ArrayList<BwSystem>();

    syspars.add(globals.svci.getSysparsHandler().get());
    new Dumpling<BwSystem>(globals,
                           new QName(sectionSyspars),
                           globals.syspars).dumpSection(syspars.iterator());
    close();

    open();
    new Dumpling<BwPrincipal>(globals,
                         new QName(sectionUsers),
                         globals.users).dumpSection(globals.di.getAllPrincipals());
    close();

    open();
    new Dumpling<BwCategory>(globals,
                             new QName(sectionCategories),
                             globals.categories).dumpSection(globals.di.getCategories());
    close();

    open();
    new Dumpling<BwCalendar>(globals,
                             new QName(sectionCollections),
                             globals.collections).dumpSection(globals.di.getCalendars());
    close();

    open();
    new Dumpling<BwLocation>(globals,
                             new QName(sectionLocations),
                             globals.locations).dumpSection(globals.di.getLocations());
    close();

    open();
    new Dumpling<BwContact>(globals,
                             new QName(sectionContacts),
                             globals.contacts).dumpSection(globals.di.getContacts());
    close();

    /* These all reference the above */

    open();
    new Dumpling<BwAuthUser>(globals,
                             new QName(sectionAuthUsers),
                             globals.authusers).dumpSection(globals.di.getAuthUsers());
    close();

    open();
    new Dumpling<BwEvent>(globals,
                          new QName(sectionEvents),
                             globals.events).dumpSection(globals.di.getEvents());
    close();

    open();
    new Dumpling<BwEventAnnotation>(globals,
                                    new QName(sectionEventAnnotations),
                                    globals.eventAnnotations).dumpSection(globals.di.getEventAnnotations());
    close();

    open();
    new Dumpling<BwFilterDef>(globals,
                              new QName(sectionFilters),
                              globals.filters).dumpSection(globals.di.getFilters());
    close();

    open();
    new Dumpling<BwAdminGroup>(globals,
                               new QName(sectionAdminGroups),
                               globals.filters).dumpSection(globals.di.getAdminGroups());
    close();

    open();
    new Dumpling<BwPreferences>(globals,
                                new QName(sectionUserPrefs),
                                globals.userPrefs).dumpSection(globals.di.getPreferences());
    close();

    open();
    new Dumpling<BwResource>(globals,
                             new QName(sectionResources),
                             globals.resources).dumpSection(globals.di.getResources());
    close();

    open();
    new Dumpling<BwCalSuite>(globals,
                             new QName(sectionCalSuites),
                             globals.calSuites).dumpSection(globals.di.getCalSuites());
    close();

    tagEnd(sectionTag);
  }

  private void open() throws Throwable {
    globals.svci.beginTransaction();
  }

  private void close() throws Throwable {
    globals.svci.endTransaction();
  }
}

