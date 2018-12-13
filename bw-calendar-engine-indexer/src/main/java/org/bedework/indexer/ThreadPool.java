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

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.Collection;

/** Pool of runnable thread processes.
 *
 * @author douglm
 *
 */
public class ThreadPool implements Logged {
  private String name;

  private int maxThreads;

  private ThreadGroup tgroup;

  private Collection<IndexerThread> running = new ArrayList<IndexerThread>();
  private int numWaiting;

  private int totalThreads;

  /**
   * @param name
   * @param maxThreads
   */
  public ThreadPool(final String name,
                    final int maxThreads) {
    tgroup = new ThreadGroup(name);

    this.name = name;
    this.maxThreads = maxThreads;
  }

  /**
   * @return ThreadGroup for this pool
   */
  public ThreadGroup getThreadGroup() {
    return tgroup;
  }

  /** Flag the completion of a processor.
   *
   * @param val
   */
  public void completed(final IndexerThread val) {
    synchronized (running) {
      if (!running.contains(val)) {
        error("Thread " + val.getName() + " was not in running set.");
      } else {
        running.remove(val);
      }

      running.notify();
    }
  }

  /**
   * @throws CalFacadeException
   */
  public void waitForProcessors() throws CalFacadeException {
    try {
      synchronized (running) {
        while (numWaiting > 0) {
          if (debug()) {
            debug("Number waiting is " + numWaiting + ". Waiting till zero");
          }

          running.wait();
        }
      }

      synchronized (running) {
        while (running.size() > 0) {
          if (debug()) {
            debug("Number running is " + running.size() + ". Waiting till zero");
          }

          running.wait();
        }
      }

      if (debug()) {
        debug("All threads terminated");
      }
    } catch (Throwable t) {
      if (debug()) {
        debug("Exception waiting for processor: " + t);
      }
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param proc
   * @return a thread
   * @throws CalFacadeException
   */
  public IndexerThread getThread(final Processor proc) throws CalFacadeException {
    try {
      synchronized (running) {
        while (running.size() >= maxThreads) {
          numWaiting++;
          if (debug()) {
            debug("Waiting for thread. Queue length: " + numWaiting);
          }

          running.wait();
          numWaiting--;
        }

        IndexerThread it = new IndexerThread(name + "-" + totalThreads, this,  proc);
        running.add(it);
        totalThreads++;

        return it;
      }
    } catch (Throwable t) {
      if (debug()) {
        debug("Exception waiting for processor: " + t);
      }
      throw new CalFacadeException(t);
    }
  }

  /**
   *
   */
  public void checkThreads() {
    int active = tgroup.activeCount();

    if (active == 0) {
      return;
    }

    error("Still " + active + " active " + name + " threads");

    Thread[] activeThreads = new Thread[active];

    int ret = tgroup.enumerate(activeThreads);

    for (int i = 0; i < ret; i++) {
      error("Thread " + activeThreads[i].getName() +
            " is still active");
    }
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("name", name);
    ts.append("running", running.size());
    ts.append("waiting", numWaiting);
    ts.append("total", totalThreads);

    return ts.toString();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
