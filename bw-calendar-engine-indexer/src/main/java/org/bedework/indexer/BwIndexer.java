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
package org.bedework.indexer;

import org.bedework.indexer.crawler.CrawlStatus;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.GBeanLifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author douglm
 *
 */
public class BwIndexer extends BwIndexApp implements BwIndexerMBean, GBeanLifecycle {
  private boolean running;

  /** Geronimo gbean info
   */
  public static final GBeanInfo GBEAN_INFO;
  static {
    GBeanInfoBuilder infoB =
        GBeanInfoBuilder.createStatic("Bedework-Indexer", BwIndexer.class);
    infoB.addAttribute("account", String.class, true);
    infoB.addAttribute("skipPaths", String.class, true);

    GBEAN_INFO = infoB.getBeanInfo();
  }

  private class ProcessorThread extends Thread {
    boolean showedTrace;

    /**
     * @param name - for the thread
     */
    public ProcessorThread(final String name) {
      super(name);
    }
    @Override
    public void run() {
      while (running) {
        try {
          listen();
        } catch (InterruptedException ie) {
          break;
        } catch (Throwable t) {
          if (!showedTrace) {
            error(t);
//            showedTrace = true;
          } else {
            error(t.getMessage());
          }
        } finally {
          close();
        }
      }
    }
  }

  private ProcessorThread processor;

  private class CrawlThread extends Thread {
    boolean showedTrace;
    CrawlStatus userStatus;
    CrawlStatus publicStatus;
    CrawlStatus status;

    /**
     * @param name - for the thread
     */
    public CrawlThread(final String name) {
      super(name);
    }
    @Override
    public void run() {
      try {
        userStatus = new CrawlStatus();
        publicStatus = new CrawlStatus();
        status = new CrawlStatus();

        long start = System.currentTimeMillis();

        crawl(userStatus, publicStatus, status);

        long millis = System.currentTimeMillis() - start;
        status.infoLines.add("Indexing took " +
          String.format("%d min, %d sec",
                        TimeUnit.MILLISECONDS.toMinutes(millis),
                        TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
                  ));
      } catch (InterruptedException ie) {
      } catch (Throwable t) {
        if (!showedTrace) {
          error(t);
          //            showedTrace = true;
        } else {
          error(t.getMessage());
        }
//      } finally {
  //      close();
      }
    }
  }

  private CrawlThread crawler;

  @Override
  public List<String> rebuildStatus() {
    List<String> res = new ArrayList<String>();

    if (crawler == null) {
      outLine(res, "No rebuild appears to have taken place");
      return res;
    }

    outputStatus("user", crawler.userStatus, res);
    outputStatus("public", crawler.publicStatus, res);
    outputStatus("overall", crawler.status, res);

    return res;
  }

  private void outputStatus(final String name,
                            final CrawlStatus status,
                            final List<String> res) {
    outLine(res, "--------------------------------------");
    if (status == null) {
      outLine(res, "No " + name + " rebuild status");
      return;
    }

    outLine(res, name + " rebuild status");

    outLine(res, "current status: " + status.currentStatus);

    if (status.stats != null) {
      res.addAll(status.stats.statsList());
    }

    outLine(res, "");

    if (!status.infoLines.isEmpty()) {
      res.addAll(status.infoLines);
    }
  }

  private void outLine(final List<String> res,
                       final String msg) {
    res.add(msg + "\n");
  }

  @Override
  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return "org.bedework:service=Indexer";
  }

  /* an example say's we need this  - we'll see
  public MBeanInfo getMBeanInfo() throws Exception {
    InitialContext ic = new InitialContext();
    RMIAdaptor server = (RMIAdaptor) ic.lookup("jmx/rmi/RMIAdaptor");

    ObjectName name = new ObjectName(MBEAN_OBJ_NAME);

    // Get the MBeanInfo for this MBean
    MBeanInfo info = server.getMBeanInfo(name);
    return info;
  }
  */

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#rebuildIndex()
   */
  @Override
  public String rebuildIndex() {
    try {
      if ((crawler != null) && crawler.isAlive()) {
        error("Reindexer already started");
        return "Reindexer already started";
      }

      setIndexBuildLocationPrefix(System.getProperty("org.bedework.data.dir"));

      crawler = new CrawlThread(getName());
      crawler.start();

      return "Started";
    } catch (Throwable t) {
      List<String> infoLines = new ArrayList<String>();

      infoLines.add("***********************************\n");
      infoLines.add("Error rebuilding indexes.\n");
      infoLines.add("***********************************\n");
      error("Error rebuilding indexes.");
      error(t);

      return t.getLocalizedMessage();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#isStarted()
   */
  @Override
  public boolean isStarted() {
    return (processor != null) && processor.isAlive();
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#start()
   */
  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    running = true;

    info("************************************************************");
    info(" * Starting indexer");
    info("************************************************************");

    processor = new ProcessorThread(getName());
    processor.start();
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#stop()
   */
  @Override
  public synchronized void stop() {
    if (processor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping indexer");
    info("************************************************************");

    running = false;

    processor.interrupt();
    try {
      processor.join(20 * 1000);
    } catch (InterruptedException ie) {
    } catch (Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }

    processor = null;

    info("************************************************************");
    info(" * Indexer terminated");
    info("************************************************************");
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#setSkipPaths(java.lang.String)
   */
  @Override
  public void setSkipPaths(final String val) {
    String[] paths = val.split(":");

    skipPaths.clear();

    for (String path:paths) {
      skipPaths.add(path);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexerMBean#getSkipPaths()
   */
  @Override
  public String getSkipPaths() {
    String delim = "";
    StringBuilder sb = new StringBuilder();

    for (String s: skipPaths) {
      sb.append(delim);
      sb.append(s);

      delim = ":";
    }

    return sb.toString();
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
}
