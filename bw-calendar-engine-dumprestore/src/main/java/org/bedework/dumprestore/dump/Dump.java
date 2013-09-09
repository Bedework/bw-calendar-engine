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
import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.ExternalSubInfo;
import org.bedework.dumprestore.InfoLines;
import org.bedework.dumprestore.dump.dumpling.DumpAll;
import org.bedework.dumprestore.dump.dumpling.ExtSubs;

import org.apache.log4j.Logger;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

/** Application to dump calendar data.
 *
 * @author Mike Douglass   douglm@bedework.edu
 * @version 3.1
 */
public class Dump implements Defs {
  private transient Logger log;

  /* Runtime arg -f Where we dump to.
   */
  private String fileName;

  /* runtime arg -i (id) */
  //private String id = "sa";

  String indent = "";

  private DumpGlobals globals = new DumpGlobals();

  private String adminUserAccount = "admin";

  /** ===================================================================
   *                       Constructor
   *  =================================================================== */

  /**
   * @param info
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
   * @throws Throwable
   */
  public void open() throws Throwable {
    globals.svci.open();
    globals.di = globals.svci.getDumpHandler();

    if (fileName == null) {
      globals.setOut(new OutputStreamWriter(System.out));
    } else {
      globals.setOut(new OutputStreamWriter(new FileOutputStream(fileName),
                                            "UTF-8"));
    }
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

  void processArgs(final String[] args) throws Throwable {
    /* Look for appname arg */

    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("-debug")) {
      } else if (args[i].equals("-ndebug")) {
      } else if (args[i].equals("")) {
        // null arg generated by ant
      } else if (args[i].equals("-noarg")) {
        // noop
      } else if (argpar("-appname", args, i)) {
        i++;
        // done earlier
      } else if (argpar("-f", args, i)) {
        i++;
        fileName = args[i];
      } else if (argpar("-i", args, i)) {
        i++;
        //id = args[i];
      } else {
        error("Illegal argument: " + args[i]);
        throw new Exception("Invalid args");
      }
    }
  }

  boolean argpar(final String n, final String[] args, final int i) throws Exception {
    if (!args[i].equals(n)) {
      return false;
    }

    if ((i + 1) == args.length) {
      throw new Exception("Invalid args");
    }
    return true;
  }

  /**
   * @param args
   * @throws Throwable
   */
  public void getConfigProperties(final String[] args) throws Throwable {
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

  /**
   * @param args
   */
  public static void main(final String[] args) {
    Dump d = null;
    int rtn = 4;  // Wasn't that EOF for MTS?

    try {
      long startTime = System.currentTimeMillis();

      d = new Dump(null);

      d.getConfigProperties(args);

      d.processArgs(args);

      d.open();

      d.doDump();

      d.stats(null);

      long millis = System.currentTimeMillis() - startTime;
      long seconds = millis / 1000;
      long minutes = seconds / 60;
      seconds -= (minutes * 60);

      d.info("Elapsed time: " + minutes + ":" + twoDigits(seconds));

      /*
      Map<Thread, StackTraceElement[]> liveThreads = Thread.getAllStackTraces();

      for (Thread th: liveThreads.keySet()) {
        StackTraceElement[] stes = liveThreads.get(th);

        d.trace("Thread: " + th);
        if (stes != null) {
          for (StackTraceElement ste: stes) {
            d.trace(ste.toString());
          }
        }
      }*/

      rtn = 0;
    } catch (Throwable t) {
      t.printStackTrace();
    } finally {
      try {
        d.close();
      } catch (Throwable t1) {
        t1.printStackTrace();
      }
    }

    System.exit(rtn);
  }

  private CalSvcI getSvci() throws Throwable {
    CalSvcIPars pars = new CalSvcIPars(adminUserAccount,
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

  private static String twoDigits(final long val) {
    if (val < 10) {
      return "0" + val;
    }

    return String.valueOf(val);
  }
}
