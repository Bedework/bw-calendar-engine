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

import java.util.List;
import java.util.Map;

/** This class provides common crawler methods.
 *
 * @author douglm
 *
 */
public abstract class Crawler extends ProcessorBase {
  private CrawlThread thr;

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
   * @param status crawl status object
   * @param name to identify
   * @param adminAccount admin
   * @param principal - the principal we are processing or null.
   * @param batchDelay - delay between batches - milliseconds
   * @param entityDelay - delay between entities - milliseconds
   * @param skipPaths - paths to skip
   * @param indexNames - where we build the index
   */
  public Crawler(final CrawlStatus status,
                 final String name,
                 final String adminAccount,
                 final String principal,
                 final long batchDelay,
                 final long entityDelay,
                 final List<String> skipPaths,
                 final Map<String, String> indexNames) {
    super(name, adminAccount, principal, batchDelay, entityDelay,
          skipPaths, indexNames);

    setStatus(status);
  }

  @Override
  public void start() {
    try {
      thr = new CrawlThread(name, this);
      thr.start();
    } catch (final Throwable t) {
      getStatus().currentStatus = "Start failed with exception " + t.getLocalizedMessage();
      error(t);
    }
  }

  @Override
  public void join() {
    try {
      thr.join();
    } catch (final Throwable t) {
      getStatus().currentStatus = "Join failed with exception " + t.getLocalizedMessage();
      error(t);
    }

    /* Wait for the remaining threads */
    super.join();
  }

  @Override
  public void stop() {

  }

  @Override
  public void restart() {

  }

  @Override
  public void restartNext() {

  }
}
