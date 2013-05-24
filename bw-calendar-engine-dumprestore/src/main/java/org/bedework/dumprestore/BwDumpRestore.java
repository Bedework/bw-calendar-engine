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
package org.bedework.dumprestore;

import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.CalendarsI.CheckSubscriptionResult;
import org.bedework.dumprestore.dump.Dump;
import org.bedework.dumprestore.restore.Restore;
import org.bedework.indexer.BwIndexerMBean;

import edu.rpi.cmt.jmx.MBeanUtil;
import edu.rpi.sss.util.Args;
import edu.rpi.sss.util.DateTimeUtil;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * @author douglm
 *
 */
public class BwDumpRestore implements BwDumpRestoreMBean, GBeanLifecycle {
  /** Geronimo gbean info
   */
  public static final GBeanInfo GBEAN_INFO;
  static {
    GBeanInfoBuilder infoB =
        GBeanInfoBuilder.createStatic("DumpRestore", BwDumpRestore.class);
    infoB.addAttribute("account", String.class, true);
    infoB.addAttribute("skipPaths", String.class, true);

    infoB.addAttribute("Create", "boolean", true);

    /* Statement delimiter */
    infoB.addAttribute("Delimiter", String.class, true);

    /* Export to database? - false for safety - set true in console */
    infoB.addAttribute("Export", "boolean", true);

    /* Schema Output file name - full path, folders must exist */
    infoB.addAttribute("SchemaOutFile", String.class, true);

    /* XML data input file name - full path. Used for data restore */
    infoB.addAttribute("DataIn", String.class, true);

    /* XML data output directory - full path. Used for data dump */
    infoB.addAttribute("DataOut", String.class, true);

    /* XML data output file prefix - for data dump */
    infoB.addAttribute("DataOutPrefix", String.class, true);

    GBEAN_INFO = infoB.getBeanInfo();
  }

  private transient Logger log;

  private boolean started;

  private String account;

  private String appname = "dumpres";

  private String dataIn;

  private String dataOut;

  private String dataOutPrefix;

  private String timezonesUri;

  private List<ExternalSubInfo> externalSubs;

  private String curSvciOwner;

  private CalSvcI svci;

  private class RestoreThread extends Thread {
    InfoLines infoLines = new InfoLines();

    RestoreThread() {
      super("RestoreData");
    }

    @Override
    public void run() {
      try {
        if (!disableIndexer()) {
          infoLines.add("***********************************\n");
          infoLines.add("********* Unable to disable indexer\n");
          infoLines.add("***********************************\n");
        }

        infoLines.addLn("Started restore of data");

        long startTime = System.currentTimeMillis();

        Restore restorer = new Restore();

        String[] args = new String[] {"-appname",
                                      appname
        };

        restorer.getConfigProperties(new Args(args));

        infoLines.addLn("Restore file: " + getDataIn());
        info("Restore file: " + getDataIn());

        restorer.setFilename(getDataIn());
        restorer.setTimezonesUri(timezonesUri);

        restorer.open();

        restorer.doRestore(infoLines);

        externalSubs = restorer.getExternalSubs();

        restorer.close();

        restorer.stats(infoLines);

        long millis = System.currentTimeMillis() - startTime;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds -= (minutes * 60);

        infoLines.addLn("Elapsed time: " + minutes + ":" +
                        Restore.twoDigits(seconds));

        infoLines.add("Restore complete" + "\n");
      } catch (Throwable t) {
        error(t);
        infoLines.exceptionMsg(t);
      } finally {
        infoLines.addLn("Restore completed - about to start indexer");

        try {
          if (!reindex()) {
            infoLines.addLn("***********************************");
            infoLines.addLn("********* Unable to reindex");
            infoLines.addLn("***********************************");
          }
        } catch (Throwable t) {
          error(t);
          infoLines.exceptionMsg(t);
        }
      }
    }
  }

  private RestoreThread restore;

  private class DumpThread extends Thread {
    InfoLines infoLines = new InfoLines();
    boolean dumpAll;

    DumpThread(final boolean dumpAll) {
      super("DumpData");

      this.dumpAll = dumpAll;
    }

    @Override
    public void run() {
      try {
        long startTime = System.currentTimeMillis();

        Dump d = new Dump(infoLines);

        String[] args = new String[] {"-appname",
                                      appname
        };

        d.getConfigProperties(args);

        if (dumpAll) {
          infoLines.addLn("Started dump of data");
          StringBuilder fname = new StringBuilder(getDataOut());
          if (!getDataOut().endsWith("/")) {
            fname.append("/");
          }

          fname.append(getDataOutPrefix());

          /* append "yyyyMMddTHHmmss" */
          fname.append(DateTimeUtil.isoDateTime());
          fname.append(".xml");

          d.setFilename(fname.toString());

          d.open();

          d.doDump();
        } else {
          infoLines.addLn("Started search for external subscriptions");
          d.open();

          d.doExtSubs();
        }

        externalSubs = d.getExternalSubs();

        d.close();

        d.stats(infoLines);

        long millis = System.currentTimeMillis() - startTime;
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds -= (minutes * 60);

        infoLines.addLn("Elapsed time: " + minutes + ":" +
            Restore.twoDigits(seconds));

        infoLines.addLn("Complete");
      } catch (Throwable t) {
        error(t);
        infoLines.exceptionMsg(t);
      }
    }
  }

  private DumpThread dump;

  private class SubsThread extends Thread {
    InfoLines infoLines = new InfoLines();

    SubsThread() {
      super("Subs");
    }

    @Override
    public void run() {
      try {
        boolean debug = getLogger().isDebugEnabled();

        if (externalSubs.isEmpty()) {
          infoLines.addLn("No external subscriptions");

          return;
        }

        /** Number for which no action was required */
        int okCt = 0;

        /** Number not found */
        int notFoundCt = 0;

        /** Number for which no action was required */
        int notExternalCt = 0;

        /** Number resubscribed */
        int resubscribedCt = 0;

        /** Number of failures */
        int failedCt = 0;

        if (debug) {
          trace("About to process " + externalSubs.size() + " external subs");
        }

        int ct = 0;
        int accessErrorCt = 0;
        int errorCt = 0;

        resubscribe:
          for (ExternalSubInfo esi: externalSubs) {
            getSvci(esi);

            try {
              CheckSubscriptionResult csr =
                  svci.getCalendarsHandler().checkSubscription(esi.path);

              switch (csr) {
              case ok:
                okCt++;
                break;
              case notFound:
                notFoundCt++;
                break;
              case notExternal:
                notExternalCt++;
                break;
              case resubscribed:
                resubscribedCt++;
                break;
              case noSynchService:
                infoLines.addLn("Synch service is unavailable");
                info("Synch service is unavailable");
                break resubscribe;
              case failed:
                failedCt++;
                break;
              } // switch

              if ((csr != CheckSubscriptionResult.ok) &&
                  (csr != CheckSubscriptionResult.resubscribed)) {
                infoLines.addLn("Status: " + csr + " for " + esi.path +
                                " owner: " + esi.owner);
              }

              ct++;

              if ((ct % 100) == 0) {
                info("Checked " + ct + " collections");
              }
            } catch (CalFacadeAccessException cae) {
              accessErrorCt++;

              if ((accessErrorCt % 100) == 0) {
                info("Had " + accessErrorCt + " access errors");
              }
            } catch (Throwable t) {
              error(t);
              errorCt++;

              if ((errorCt % 100) == 0) {
                info("Had " + errorCt + " errors");
              }
            } finally {
              closeSvci();
            }
          } // resubscribe

        infoLines.addLn("Checked " + ct + " collections");
        infoLines.addLn("       errors: " + errorCt);
        infoLines.addLn("access errors: " + accessErrorCt);
        infoLines.addLn("           ok: " + okCt);
        infoLines.addLn("    not found: " + notFoundCt);
        infoLines.addLn("  notExternal: " + notExternalCt);
        infoLines.addLn(" resubscribed: " + resubscribedCt);
        infoLines.addLn("       failed: " + failedCt);
      } catch (Throwable t) {
        error(t);
        infoLines.exceptionMsg(t);
      }
    }
  }

  private SubsThread subs;

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.BwDumpRestoreMBean#getName()
   */
  @Override
  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return "org.bedework:service=DumpRestore";
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.BwDumpRestoreMBean#setAccount(java.lang.String)
   */
  @Override
  public void setAccount(final String val) {
    account = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.BwDumpRestoreMBean#getAccount()
   */
  @Override
  public String getAccount() {
    return account;
  }

  @Override
  public String getAppname() {
    return appname;
  }

  @Override
  public void setDataIn(final String val) {
    dataIn = val;
  }

  @Override
  public String getDataIn() {
    return dataIn;
  }

  @Override
  public void setDataOut(final String val) {
    dataOut = val;
  }

  @Override
  public String getDataOut() {
    return dataOut;
  }

  @Override
  public void setDataOutPrefix(final String val) {
    dataOutPrefix = val;
  }

  @Override
  public String getDataOutPrefix() {
    return dataOutPrefix;
  }

  @Override
  public void setTimezonesUri(final String val) {
    timezonesUri = val;
  }

  @Override
  public String getTimezonesUri() {
    return timezonesUri;
  }

  @Override
  public synchronized String restoreData() {
    try {
      restore = new RestoreThread();

      restore.start();

      return "OK";
    } catch (Throwable t) {
      error(t);

      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public synchronized List<String> restoreStatus() {
    if (restore == null) {
      InfoLines infoLines = new InfoLines();

      infoLines.addLn("Restore has not been started");

      return infoLines;
    }

    return restore.infoLines;
  }

  @Override
  public String fetchExternalSubs() {
    try {
      dump = new DumpThread(false);

      dump.start();

      return "OK";
    } catch (Throwable t) {
      error(t);

      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public String checkExternalSubs() {
    try {
      subs = new SubsThread();

      subs.start();

      return "OK";
    } catch (Throwable t) {
      error(t);

      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public List<String> checkSubsStatus() {
    if (subs == null) {
      InfoLines infoLines = new InfoLines();

      infoLines.addLn("Subscriptions check has not been started");

      return infoLines;
    }

    return subs.infoLines;
  }

  @Override
  public String dumpData() {
    try {
      dump = new DumpThread(true);

      dump.start();

      return "OK";
    } catch (Throwable t) {
      error(t);

      return "Exception: " + t.getLocalizedMessage();
    }
  }

  @Override
  public synchronized List<String> dumpStatus() {
    if (dump == null) {
      InfoLines infoLines = new InfoLines();

      infoLines.addLn("Dump has not been started");

      return infoLines;
    }

    return dump.infoLines;
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.BwDumpRestoreMBean#create()
   */
  @Override
  public void create() {
    // An opportunity to initialise
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#start()
   */
  @Override
  public void start() {
    started = true;
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#stop()
   */
  @Override
  public void stop() {
    started = false;
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#isStarted()
   */
  @Override
  public boolean isStarted() {
    return started;
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.BwDumpRestoreMBean#destroy()
   */
  @Override
  public void destroy() {
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private BwIndexerMBean indexer;

  private boolean disableIndexer() throws CalFacadeException {
    try {
      if (indexer == null) {
        indexer = (BwIndexerMBean)MBeanUtil.getMBean(BwIndexerMBean.class,
                                                     "org.bedework:service=Indexer");
      }

      indexer.setDiscardMessages(true);
      return true;
    } catch (Throwable t) {
      error(t);
      return false;
    }
  }

  private boolean reindex() throws CalFacadeException {
    try {
      if (indexer == null) {
        return false;
      }
      indexer.rebuildIndex();
      indexer.setDiscardMessages(false);
      return true;
    } catch (Throwable t) {
      error(t);
      return false;
    }
  }

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @return svci object
   * @throws CalFacadeException
   */
  private CalSvcI getSvci(final ExternalSubInfo esi) throws CalFacadeException {
    if ((svci != null) && svci.isOpen()) {
      return svci;
    }

    boolean publicAdmin = false;

    if ((curSvciOwner == null) || !curSvciOwner.equals(esi.owner)) {
      svci = null;

      curSvciOwner = esi.owner;
      publicAdmin = esi.publick;
    }

    if (svci == null) {
      CalSvcIPars pars = CalSvcIPars.getServicePars(curSvciOwner,
                                                    publicAdmin,   // publicAdmin
                                                    true);   // Allow super user
      svci = new CalSvcFactoryDefault().getSvc(pars);
    }

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   * @throws CalFacadeException
   */
  public void closeSvci() throws CalFacadeException {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();
    } catch (Throwable t) {
      try {
        svci.close();
      } catch (Throwable t1) {
      }
    }

    try {
      svci.close();
    } catch (Throwable t) {
    }
  }

  /* ========================================================================
   * Geronimo lifecycle methods
   * ======================================================================== */

  /**
   * @return gbean info
   */
  public static GBeanInfo getGBeanInfo() {
    return GBEAN_INFO;
  }

  @Override
  public void doFail() {
    stop();
  }

  @Override
  public void doStart() throws Exception {
    start();
  }

  @Override
  public void doStop() throws Exception {
    stop();
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
