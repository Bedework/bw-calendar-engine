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
package org.bedework.inoutsched;

import org.bedework.calsvc.scheduling.hosts.BwHosts;

import org.bedework.util.jmx.ConfBase;

/** JMX bean for bedework scheduling
 *
 * @author douglm
 *
 */
public class BwInoutSched extends ConfBase
        implements BwInoutSchedMBean {
  private static BwHosts isched;

  private class ProcessorThread extends Thread {
    private InoutSched sched;

    /**
     * @param name - for the thread
     * @param sched
     */
    public ProcessorThread(final String name, final InoutSched sched) {
      super(name);

      this.sched = sched;
    }

    @Override
    public void run() {
      try {
        sched.run();
      } catch (Throwable t) {
        error(t.getMessage());
      }
    }
  }

  private ProcessorThread inProcessor;
  private ProcessorThread outProcessor;

  private int incomingRetryLimit = 10;

  private int outgoingRetryLimit = 10;

  private Counts counts = new Counts();

  /**
   *
   */
  public BwInoutSched() {
    super("org.bedework.bwengine:service=BwInoutSched");

    isched = new BwHosts();
    register("ischedconf", "ischedconf", isched);
    isched.loadConfigs();
  }

  @Override
  public String loadConfig() {
    return null;
  }

  @Override
  public void setIncomingRetryLimit(final int val) {
    incomingRetryLimit = val;

    if (inProcessor != null) {
      inProcessor.sched.setRetryLimit(val);
    }
  }

  @Override
  public int getIncomingRetryLimit() {
    return incomingRetryLimit;
  }

  @Override
  public void setOutgoingRetryLimit(final int val) {
    outgoingRetryLimit = val;

    if (outProcessor != null) {
      outProcessor.sched.setRetryLimit(val);
    }
  }

  @Override
  public int getOutgoingRetryLimit() {
    return outgoingRetryLimit;
  }

  @Override
  public Counts getCounts() {
    return counts;
  }

  @Override
  public String getName() {
    /* This apparently must be the same as the name attribute in the
     * jboss service definition
     */
    return getServiceName();
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  @Override
  public void create() {
  }

  @Override
  public synchronized void start() {
    if (outProcessor != null) {
      error("Already started");
      return;
    }

    info("************************************************************");
    info(" * Starting " + getName());
    info("************************************************************");

    try {
      inProcessor = new ProcessorThread(getName(),
                                        new InoutSched(counts.inCounts,
                                                       incomingRetryLimit,
                                                       true));
      outProcessor = new ProcessorThread(getName(),
                                         new InoutSched(counts.outCounts,
                                                        outgoingRetryLimit,
                                                        false));
    } catch (Throwable t) {
      error("Error starting scheduler");
      error(t);
    }

    inProcessor.start();
    outProcessor.start();
  }

  @Override
  public synchronized void stop() {
    if (outProcessor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping " + getName());
    info("************************************************************");

    stopProc(inProcessor);
    inProcessor = null;

    stopProc(outProcessor);
    outProcessor = null;

    info("************************************************************");
    info(" * " + getName() + " terminated");
    info("************************************************************");
  }

  @Override
  public boolean isStarted() {
    return (outProcessor != null) && outProcessor.isAlive();
  }

  @Override
  public void destroy() {
    try {
      getManagementContext().stop();
    } catch (Throwable t) {
      error("Failed to stop management context");
      error(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

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
}
