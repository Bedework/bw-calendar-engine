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
package org.bedework.dumprestore.dump;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.dumprestore.AliasEntry;
import org.bedework.dumprestore.AliasInfo;
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.InfoLines;
import org.bedework.dumprestore.dump.dumpling.DumpAliases;
import org.bedework.dumprestore.dump.dumpling.DumpAll;
import org.bedework.dumprestore.dump.dumpling.ExtSubs;
import org.bedework.dumprestore.prdump.DumpPrincipal;
import org.bedework.dumprestore.prdump.DumpPublic;
import org.bedework.dumprestore.prdump.DumpSystem;
import org.bedework.util.misc.Logged;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Application to dump calendar data.
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 3.1
 */
public class Dump extends Logged implements Defs {
  /* Where we dump to.
   */
  private String fileName;

  /* Where we dump aliases.
   */
  private String aliasesFileName;

  private final DumpGlobals globals = new DumpGlobals();

  @SuppressWarnings("FieldCanBeLocal")
  private final String adminUserAccount = "admin";

  private final boolean newDumpFormat;

  /* ===================================================================
   *                       Constructor
   * =================================================================== */

  /**
   * @param info lines
   * @throws Throwable on error
   */
  public Dump(final InfoLines info,
              final boolean newDumpFormat) throws Throwable {
    this.newDumpFormat = newDumpFormat;
    globals.info = info;
  }

  /* ===================================================================
   *                       Dump methods
   * =================================================================== */

  public void setDirPath(final String val) {
    globals.setDirPath(val);
  }

  /**
   * @param val - filename for old style dump
   */
  public void setFilename(final String val) {
    fileName = val;
  }

  /**
   * @param val - filename to dump to
   */
  public void setAliasesFilename(final String val) {
    aliasesFileName = val;
  }

  /**
   * @param noOutput  we don't intend doing any output (extsubs)
   * @throws Throwable on error
   */
  public void open(final boolean noOutput) throws Throwable {
    globals.svci = getSvci();
    globals.svci.open();
    globals.di = globals.svci.getDumpHandler();

    if (noOutput) {
      return;
    }

    if (!newDumpFormat) {
      boolean error = false;

      if (fileName == null) {
        error("Must have an output file set");
        error = true;
      }

      if (aliasesFileName == null) {
        error("Must have an output file for aliases set");
        error = true;
      }

      if (error) {
        return;
      }

      globals.setOut(
              new OutputStreamWriter(new FileOutputStream(fileName),
                                     "UTF-8"),
              new OutputStreamWriter(
                      new FileOutputStream(aliasesFileName),
                      "UTF-8"));
    }
  }

  /**
   * @throws Throwable on error
   */
  public void close() throws Throwable {
    if (globals.svci != null) {
      globals.svci.close();
    }
    globals.close();
  }

  /**
   * @throws Throwable on error
   */
  public void doDump() throws Throwable {
    if (newDumpFormat) {
      // TODO - start a separate thread for public
      final DumpPublic dumpPub = new DumpPublic(globals);
      if (dumpPub.open()) {
        dumpPub.doDump();
        dumpPub.close();
      }

      final DumpSystem dumpSys = new DumpSystem(globals);
      if (dumpSys.open()) {
        dumpSys.doDump();
        dumpSys.close();
      }

      final DumpPrincipal dumpPr = new DumpPrincipal(globals);

      final Iterator<BwPrincipal> it = globals.di.getAllPrincipals();
      while (it.hasNext()) {
        final BwPrincipal pr = it.next();
        if (dumpPr.open(pr)) {
          dumpPr.doDump();
          dumpPr.close();
        }
      }
    } else {
      new DumpAll(globals).dumpSection(null);
      new DumpAliases(globals).dumpSection(null);
    }
  }

  /** Just get list of external subscriptions
   *
   * @throws Throwable on error
   */
  public void doExtSubs() throws Throwable {
    new ExtSubs(globals).getSubs();
  }

  /**
   * @return list of external subscriptions
   */
  public List<AliasInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  /**
   * @return table of aliases by path
   */
  public Map<String, AliasEntry> getAliasInfo() {
    return globals.aliasInfo;
  }

  /**
  *
  * @param infoLines - null for logged output only
  */
 public void stats(final List<String> infoLines) {
   globals.stats(infoLines);
 }

  /**
   * @throws Throwable on error
   */
  public void getConfigProperties() throws Throwable {
    globals.init(new CalSvcFactoryDefault().getSystemConfig().getBasicSystemProperties());
  }

  private CalSvcI getSvci() throws Throwable {
    final CalSvcIPars pars =
            new CalSvcIPars("dump",
                            adminUserAccount,
                            null,    // user
                            null,   // calsuite
                            !newDumpFormat,   // publicAdmin
                            !newDumpFormat,   // superUser,
                            true,   // service
                            false,  // public submission
                            true,  // adminCanEditAllPublicCategories
                            true,  // adminCanEditAllPublicLocations
                            true,  // adminCanEditAllPublicSponsors
                            false);    // sessionless
    return new CalSvcFactoryDefault().getSvc(pars);
  }
}
