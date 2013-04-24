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

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;

/** Pool of runnable thread processes.
 *
 * @author douglm
 *
 */
public class ThreadPool {
  protected boolean debug;

  private Logger log;

  private ThreadGroup tgroup;

  private Stack<EntityIndexerThread> idleProcessors = new Stack<EntityIndexerThread>();
  private Collection<EntityIndexerThread> allProcessors = new ArrayList<EntityIndexerThread>();
  private int numWaiting;

  /**
   * @param tgroup
   */
  public ThreadPool(final ThreadGroup tgroup) {
    debug = getLogger().isDebugEnabled();
    this.tgroup = tgroup;
  }

  /**
   * @return ThreadGroup for this pool
   */
  public ThreadGroup getThreadGroup() {
    return tgroup;
  }

  /** Add a processor to be used for handling entities. The supplied
   * processor will be run on a separate thread.
   *
   * @param val
   */
  public void addEntityThreadProcessor(final EntityIndexerThread val) {
    idleProcessors.push(val);
    allProcessors.add(val);
  }

  /**
   * @throws CalFacadeException
   */
  public void waitForProcessors() throws CalFacadeException {
    try {
      synchronized (idleProcessors) {
        while (numWaiting > 0) {
          if (debug) {
            debugMsg("Number waiting is " + numWaiting + ". Waiting till zero");
          }

          idleProcessors.wait();
        }
      }

      for (EntityIndexerThread eit: allProcessors) {
        synchronized(eit) {
          while (eit.getRunning()) {
            if (debug) {
              debugMsg("Waiting for thread termination");
            }

            eit.wait();
          }
        }

        if (debug) {
          debugMsg("All threads terminated");
        }
      }
    } catch (Throwable t) {
      if (debug) {
        debugMsg("Exception waiting for processor: " + t);
      }
      throw new CalFacadeException(t);
    }
  }

  /** Put a processor back in the pool
   *
   * @param val
   */
  protected void putProcessor(final EntityIndexerThread val) {
    if (debug) {
      debugMsg("about to putProcessor: queue length: " + numWaiting);
    }

    synchronized (idleProcessors) {
      if (debug) {
        debugMsg("putProcessor: queue length: " + numWaiting);
      }

      idleProcessors.push(val);

      if (numWaiting > 0) {
        idleProcessors.notify();
      }
    }
  }

  /**
   * @return an available processor
   * @throws CalFacadeException
   */
  public EntityIndexerThread getProcessor() throws CalFacadeException {
    try {
      synchronized (idleProcessors) {
        while (idleProcessors.empty()) {
          numWaiting++;
          if (debug) {
            debugMsg("Waiting for processor. Queue length: " + numWaiting);
          }

          idleProcessors.wait();
          numWaiting--;
        }

        return idleProcessors.pop();
      }
    } catch (Throwable t) {
      if (debug) {
        debugMsg("Exception waiting for processor: " + t);
      }
      throw new CalFacadeException(t);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ThreadPool{");

    sb.append("name=");
    sb.append(tgroup.getName());
    sb.append("}");

    return sb.toString();
  }

  /**
   * @return Logger
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }
}
