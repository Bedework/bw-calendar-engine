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

import org.bedework.util.jmx.ConfBase;

/**
 * @author douglm
 *
 */
public class BwChgNote extends ConfBase
        implements BwChgNoteMBean {
  /* Name of the directory holding the config data */
  private static final String confDirName = "engine";

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

  private ProcessorThread processor;

  private int retryLimit = 10;

  private MesssageCounts counts = new MesssageCounts("Notification processing counts");

  private final static String nm = "ChangeNotifications";

  public BwChgNote() {
    super(getServiceName(nm), confDirName, nm);
  }

  /**
   * @param name
   * @return object name value for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  @Override
  public String loadConfig() {
    return "No config to load";
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

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
  public boolean isStarted() {
    return (processor != null) && processor.isAlive();
  }

  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    info("************************************************************");
    info(" * Starting " + nm);
    info("************************************************************");

    try {
      processor = new ProcessorThread(nm,
                                        new ChgProc(counts,
                                                    retryLimit));
    } catch (Throwable t) {
      error("Error starting notification processor");
      error(t);
    }

    processor.start();
  }

  @Override
  public synchronized void stop() {
    if (processor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping " + nm);
    info("************************************************************");

    processor.interrupt();
    try {
      processor.join();
    } catch (InterruptedException ie) {
    } catch (Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }

    info("************************************************************");
    info(" * " + nm + " terminated");
    info("************************************************************");

    processor = null;
  }
}
