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
package org.bedework.chgnote;

import org.apache.geronimo.gbean.GBeanInfo;
import org.apache.geronimo.gbean.GBeanInfoBuilder;
import org.apache.geronimo.gbean.GBeanLifecycle;
import org.apache.log4j.Logger;

/**
 * @author douglm
 *
 */
public class BwChgNote
        implements BwChgNoteMBean, GBeanLifecycle {

  /** Geronimo gbean info
   */
  public static final GBeanInfo GBEAN_INFO;
  static {
    GBeanInfoBuilder infoB =
        GBeanInfoBuilder.createStatic("BwChgNote", BwChgNote.class);

    GBEAN_INFO = infoB.getBeanInfo();
  }

  private class ProcessorThread extends Thread {
    private ChgProc cp;

    /**
     * @param name - for the thread
     * @param cp
     */
    public ProcessorThread(final String name,
                           final ChgProc cp) {
      super(name);

      this.cp = cp;
    }

    @Override
    public void run() {
      try {
        cp.run();
      } catch (Throwable t) {
        error(t.getMessage());
      }
    }
  }

  private transient Logger log;

  private ProcessorThread processor;

  private int retryLimit = 10;

  private MesssageCounts counts = new MesssageCounts("Notification processing counts");

  @Override
  public void setRetryLimit(final int val) {
    retryLimit = val;

    if (processor != null) {
//      processor.sched.setRetryLimit(val);
    }
  }

  @Override
  public int getRetryLimit() {
    return retryLimit;
  }

  @Override
  public MesssageCounts getCounts() {
    return counts;
  }

  @Override
  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return "org.bedework:service=BwChgNote";
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

    info("************************************************************");
    info(" * Starting " + getName());
    info("************************************************************");

    try {
      processor = new ProcessorThread(getName(),
                                        new ChgProc(counts,
                                                    retryLimit));
    } catch (Throwable t) {
      error("Error starting notification processor");
      error(t);
    }

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
    info(" * Stopping " + getName());
    info("************************************************************");

    stopProc(processor);

    info("************************************************************");
    info(" * " + getName() + " terminated");
    info("************************************************************");

    processor = null;
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

  private void stopProc(final ProcessorThread p) {
    if (p == null) {
      return;
    }

    p.interrupt();
    try {
      p.join();
    } catch (InterruptedException ie) {
    } catch (Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  protected void info(final String msg) {
    getLogger().info(msg);
  }
}
