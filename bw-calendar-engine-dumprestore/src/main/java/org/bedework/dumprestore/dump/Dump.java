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

import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Map;

/** Application to dump calendar data.
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 3.1
 */
public class Dump implements Defs {
  private transient Logger log;

  /* Where we dump to.
   */
  private String fileName;

  /* Where we dump aliases.
   */
  private String aliasesFileName;

  private final DumpGlobals globals = new DumpGlobals();

  @SuppressWarnings("FieldCanBeLocal")
  private final String adminUserAccount = "admin";

  /** ===================================================================
   *                       Constructor
   *  =================================================================== */

  /**
   * @param info lines
   * @throws Throwable
   */
  public Dump(final InfoLines info) throws Throwable {
    globals.info = info;
    globals.svci = getSvci();
  }

  /** ===================================================================
   *                       Dump methods
   *  =================================================================== */

  /**
   * @param val - filename to dump to
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
   * @throws Throwable
   */
  public void open(final boolean noOutput) throws Throwable {
    globals.svci.open();
    globals.di = globals.svci.getDumpHandler();

    if (noOutput) {
      return;
    }

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

    globals.setOut(new OutputStreamWriter(new FileOutputStream(fileName),
                                          "UTF-8"),
                   new OutputStreamWriter(new FileOutputStream(aliasesFileName),
                                          "UTF-8"));
  }

  /**
   * @throws Throwable
   */
  public void close() throws Throwable {
    if (globals.svci != null) {
      globals.svci.close();
    }
    globals.close();
  }

  /**
   * @throws Throwable
   */
  public void doDump() throws Throwable {
    new DumpAll(globals).dumpSection(null);
    new DumpAliases(globals).dumpSection(null);
  }

  /** Just get list of external subscriptions
   *
   * @throws Throwable
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
   * @throws Throwable
   */
  public void getConfigProperties() throws Throwable {
    globals.init(new CalSvcFactoryDefault().getSystemConfig().getBasicSystemProperties());
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void error(final String msg) {
    getLog().error(msg);
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }

  protected void trace(final String msg) {
    getLog().debug(msg);
  }

  private CalSvcI getSvci() throws Throwable {
    final CalSvcIPars pars =
            new CalSvcIPars("dump",
                            adminUserAccount,
                            null,    // user
                            null,   // calsuite
                            true,   // publicAdmin
                            true,   // superUser,
                            true,   // service
                            false,  // public submission
                            true,  // adminCanEditAllPublicCategories
                            true,  // adminCanEditAllPublicLocations
                            true,  // adminCanEditAllPublicSponsors
                            false);    // sessionless
    return new CalSvcFactoryDefault().getSvc(pars);
  }
}
