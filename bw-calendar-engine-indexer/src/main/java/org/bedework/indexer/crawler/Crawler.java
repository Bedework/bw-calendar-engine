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
package org.bedework.indexer.crawler;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.indexer.EntityIndexerThread;
import org.bedework.indexer.ThreadPool;

import java.util.List;

/** This class provides common crawler methods.
 *
 * @author douglm
 *
 */
public abstract class Crawler extends ProcessorBase {
  private CrawlThread thr;

  private ThreadGroup tgroup;

  /** if principal is null then we are at the root.
   *
   * Run a thread which reads the children of the root directory. Each child
   * is a home directory. We read a batch of these at a time and then start
   * up maxThreads processes to handle the user data.
   *
   * When we've finished the entire tree we stop.
   *
   * if principal is non-null then we are processing a user/group etc
   * so we just descend through the collections indexing stuff.
   *
   * @param status
   * @param name
   * @param adminAccount
   * @param publick
   * @param principal - the principal we are processing or null.
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @param tgroup
   * @param entityThreads - number of threads this process can use
   * @throws CalFacadeException
   */
  public Crawler(final CrawlStatus status,
                 final String name,
                 final String adminAccount,
                 final boolean publick,
                 final String principal,
                 final long batchDelay,
                 final long entityDelay,
                 final List<String> skipPaths,
                 final String indexRootPath,
                 final ThreadGroup tgroup,
                 final int entityThreads) throws CalFacadeException {
    super(name, adminAccount, publick, principal, batchDelay, entityDelay,
          skipPaths, indexRootPath);

    setStatus(status);
    this.tgroup = tgroup;

    ThreadPool tpool = new ThreadPool(tgroup);
    setThreadPool(tpool);

    for (int i = 0; i < entityThreads; i++) {
      String n = name + " thread " + i;

      ProcessorBase p = getProcessorObject(i);
      tpool.addEntityThreadProcessor(new EntityIndexerThread(n, tpool,
                                                             status.stats,
                                                             p));
    }
  }

  /**
   * @param index - identifiy the object
   * @return ProcessorBase
   * @throws CalFacadeException
   */
  public abstract ProcessorBase getProcessorObject(int index) throws CalFacadeException;

  /** Constructor for an entity thread processor. These handle the entities
   * found within a collection.
   *
   * @param name
   * @param adminAccount
   * @param publick
   * @param principal - the principal we are processing or null.
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public Crawler(final String name,
                 final String adminAccount,
                 final boolean publick,
                 final String principal,
                 final long batchDelay,
                 final long entityDelay,
                 final List<String> skipPaths,
                 final String indexRootPath) throws CalFacadeException {
    super(name, adminAccount, publick,
          principal, batchDelay, entityDelay, skipPaths, indexRootPath);
  }

  @Override
  public void start(final String rootPath) {
    try {
      thr = new CrawlThread(name, tgroup, this, rootPath);
      thr.run();
    } catch (Throwable t) {
      getStatus().currentStatus = "Failed with exception " + t.getLocalizedMessage();
      error(t);
    }
  }

  @Override
  public void join() throws CalFacadeException {
    if (thr == null) {
      return;
    }

    try {
      getThreadPool().waitForProcessors();

      if (debug) {
        debugMsg(name + ": Wait for termination");
      }
      thr.join();
      if (debug) {
        debugMsg(name +": Termination");
      }
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public void stop() throws CalFacadeException {

  }

  @Override
  public void restart() throws CalFacadeException {

  }

  @Override
  public void restartNext() throws CalFacadeException {

  }
}
