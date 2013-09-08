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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.CalendarsI;
import org.bedework.dumprestore.ExternalSubInfo;
import org.bedework.dumprestore.InfoLines;
import org.bedework.util.misc.Util;

import org.apache.log4j.Logger;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.List;

/** Dump all calendar data for the supplied principal.
 *
 * <p>We will create a directory for the principal under the supplied directory
 * path. So if we get a path of <br/>
 * /data/dumps <br/>
 * and we are dumping principal "/principals/user/mike" we will build the dumped
 * data under <br/>
 * /data/dumps/principals/user/mike <br/>
 * which we can refer to as dump-home
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
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 4.0
 */
public class DumpPrincipal {
  private transient Logger log;

  private BwPrincipal pr;

  /* Where we dump to.
   */
  private String dirPath;

  private Deque<String> pathStack = new ArrayDeque<String>();

  private DumpGlobals globals = new DumpGlobals();

  private final static String collectionsDirName = "collections";

  /** ===================================================================
   *                       Constructor
   *  =================================================================== */

  /**
   * @param pr
   * @param dirPath
   * @param info
   * @throws CalFacadeException
   */
  public DumpPrincipal(final BwPrincipal pr,
                       final String dirPath,
                       final InfoLines info) throws CalFacadeException {
    globals.info = info;
    globals.svci = getSvci();

    this.dirPath = dirPath;
    this.pr = pr;
  }

  /**
   * @throws CalFacadeException
   */
  public void open() throws CalFacadeException {
    globals.svci.open();
    globals.di = globals.svci.getDumpHandler();

    if (dirPath == null) {
      throw new CalFacadeException("Null directory name");
    }

    /* Create a directory for the principal */

    if (File.pathSeparator.equals("/")) {
      dirPath = Util.buildPath(true, dirPath, "/", pr.getPrincipalRef());
    } else {
      if (!dirPath.endsWith(File.pathSeparator)) {
        dirPath += File.pathSeparator;
      }

      String prPath = pr.getPrincipalRef().replace('/', File.pathSeparatorChar);
      dirPath += prPath.substring(1); // Drop leading path separator
    }

    File f = new File(dirPath);

    if (f.exists()) {
      throw new CalFacadeException("Path " + dirPath + " already exists.");
    }

    f.mkdirs();

    pathStack.push(dirPath);
  }

  /**
   * @throws CalFacadeException
   */
  public void close() throws CalFacadeException {
    if (globals.svci != null) {
      globals.svci.close();
    }
    globals.close();
  }

  /** Dump everything owned by this principal
   *
   * @throws CalFacadeException
   */
  public void doDump() throws CalFacadeException {
    File f = makeFile(dirPath, pr.getAccount() + ".xml");

    pr.dump(f);

    /* Dump calendar collections - as we go we will create location, contact and
     * category directories.
     */

    pathStack.push(makeDir(dirPath, collectionsDirName));

    CalendarsI cols = globals.svci.getCalendarsHandler();

    dumpCol(cols.getHome());

    pathStack.pop();
  }

  private void dumpCol(final BwCalendar col) throws CalFacadeException {
    CalendarsI colsI = globals.svci.getCalendarsHandler();

    pathStack.push(makeDir(pathStack.peek(), col.getName()));

    col.dump(makeFile(pathStack.peek(), col.getName() + ".xml"));

    /* Dump any events in this collection */

    /* Now dump any children */
    Collection<BwCalendar> cols = colsI.getChildren(col);

    if (Util.isEmpty(cols)){
      return;
    }

    for (BwCalendar ch: cols) {
      dumpCol(ch);
    }

    pathStack.pop();
  }

  /**
   * @return list of external subscriptions
   */
  public List<ExternalSubInfo> getExternalSubs() {
    return globals.externalSubs;
  }

  /**
  *
  * @param infoLines - null for logged output only
  */
 public void stats(final List<String> infoLines) {
   globals.stats(infoLines);
 }

  /**
   * @param args
   * @throws CalFacadeException
   */
  public void getConfigProperties(final String[] args) throws CalFacadeException {
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

  protected File makeFile(final String dirPath,
                          final String name) throws CalFacadeException {
    try {
      Path p =  FileSystems.getDefault().getPath(dirPath, name);

      File f = p.toFile();

      if (!f.createNewFile()) {
        throw new CalFacadeException("Unable to create file " + p);
      }

      return f;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  protected String makeDir(final String dirPath,
                          final String name) throws CalFacadeException {
    try {
      Path p =  FileSystems.getDefault().getPath(dirPath, name);

      File f = p.toFile();

      if (!f.mkdir()) {
        throw new CalFacadeException("Unable to create directory " + p);
      }

      return p.toString();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private CalSvcI getSvci() throws CalFacadeException {
    CalSvcIPars pars = new CalSvcIPars(pr.getAccount(), // Need to use href
                                       null,   // calsuite
                                       true,   // publicAdmin
                                       true,   // superUser,
                                       true,   // service
                                       true,  // adminCanEditAllPublicCategories
                                       true,  // adminCanEditAllPublicLocations
                                       true,  // adminCanEditAllPublicSponsors
                                       false);    // sessionless
    CalSvcI svci = new CalSvcFactoryDefault().getSvc(pars);

    return svci;
  }
}
