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

import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvci.CalSvcI;
import org.bedework.indexer.IndexStats.StatType;

import org.apache.log4j.Logger;

import java.util.Collection;

/** Run to index entities.
 *
 * @author Mike Douglass
 *
 */
public class EntityIndexerThread extends Thread {
  private ThreadPool tpool;
  private IndexStats stats;
  private CalSys sys;

  private Collection<String> childEntNames;
  private String path;

  private boolean running;

  private transient Logger log;

  private boolean debug;

  private int maxErrors = 10;
  private int errors;

  /**
   * @param name
   * @param tpool
   * @param stats
   * @param sys
   * @throws CalFacadeException
   */
  public EntityIndexerThread(final String name,
                             final ThreadPool tpool,
                             final IndexStats stats,
                             final CalSys sys) throws CalFacadeException {
    super(tpool.getThreadGroup(), name);
    this.sys = sys;
    this.tpool = tpool;
    this.stats = stats;
  }

  /**
   * @param principal
   * @param path for collection
   * @param val
   */
  public void setPathNames(//final boolean publick,
                           final String principal,
                           final String path,
                           final Collection<String> val) {
    sys.setCurrentPrincipal(principal);
    this.path = path;
    childEntNames = val;
  }

  /* (non-Javadoc)
   * @see java.lang.Thread#run()
   */
  @Override
  public void run() {
    try {
      RecurringRetrievalMode rrm = new RecurringRetrievalMode(Rmode.overrides);

      setRunning(true);

      CalSvcI svci = null;
      BwIndexer indexer = sys.getIndexer();

      try {
        svci = sys.getSvci();

        for (String name: childEntNames) {
          try {
            if (debug) {
              debugMsg("Indexing collection " + path +
                       " entity " + name);
            }

            stats.inc(StatType.entities);
            EventInfo ent = svci.getEventsHandler().get(path, name, rrm);

            if (ent == null) {
              stats.inc(StatType.unreachableEntities);
              continue;
            }
            indexer.indexEntity(ent);
          } catch (Throwable t) {
            Logger.getLogger(this.getClass()).error(this, t);

            errors++;

            if (errors > maxErrors) {
              error("Too many errors (" + errors + "): terminating");
              break;
            }
          }
        }
      } finally {
        if (svci != null) {
          sys.close();
        }

        sys.putIndexer(indexer);
      }
    } catch (Throwable t) {
      Logger.getLogger(this.getClass()).error(t);
    } finally {
      setRunning(false);
      // Make ourself available again.
      tpool.putProcessor(this);
    }
  }

  private synchronized void setRunning(final boolean val) {
    running = val;

    if (val == false) {
      notify(); // Flag processes waiting for a thread
    }
  }

  /** This is synchronized by the caller
   * @return running
   */
  public boolean getRunning() {
    return running;
  }

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(t);
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
