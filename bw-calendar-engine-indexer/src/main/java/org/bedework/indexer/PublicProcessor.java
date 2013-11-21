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
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.util.misc.Util;

import java.util.List;

/** This implementation crawls the public subtree indexing entries. This is harder
 * to multithread as we don't generally have the hierarchy to subdivide it.
 *
 * <p>There are subtrees we should not go down. We need a configuration which
 * tells us the paths to avoid.
 *
 * @author douglm
 *
 */
public class PublicProcessor extends Crawler {
  /** Run a thread which reads the children of the root directory. Each child
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
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public PublicProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final long batchDelay,
                         final long entityDelay,
                         final List<String> skipPaths,
                         final String indexRootPath) throws CalFacadeException {
    super(status, name, adminAccount, null, batchDelay, entityDelay,
          skipPaths,
          indexRootPath);
  }

  @Override
  public void process() throws CalFacadeException {
    try {
      /* First index the public collection(s) */
      indexCollection(Util.buildPath(false, "/",
                                     getPublicCalendarRoot()));

      BwIndexer indexer =getSvci().getIndexer(principal,
                                              indexRootPath);

      status.stats.inc(IndexStats.StatType.categories,
                       getSvci().getCategoriesHandler().reindex(indexer));
    } finally {
      close();
    }
  }
}
