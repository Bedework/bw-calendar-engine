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

/** Run to index entities.
 *
 * @author Mike Douglass
 *
 */
public class IndexerThread extends Thread implements Logged {
  private ThreadPool tpool;
  private Processor proc;

  /**
   * @param name
   * @param tpool
   * @param proc
   * @throws CalFacadeException
   */
  public IndexerThread(final String name,
                       final ThreadPool tpool,
                       final Processor proc) throws CalFacadeException {
    super(tpool.getThreadGroup(), name);
    this.proc = proc;
    this.tpool = tpool;
  }

  @Override
  public void run() {
    try {
      proc.process();
    } catch (Throwable t) {
      error(t);
    } finally {
      tpool.completed(this);
    }
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
