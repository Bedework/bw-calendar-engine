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
import org.bedework.calsvci.CalSvcI;

import java.util.List;

/** This implementation crawls the user subtree indexing user entries.
 *
 * @author douglm
 *
 */
public class PrincipalProcessor extends Crawler {
  /** Constructor for an entity thread processor. These handle the entities
   * found within a collection.
   *
   * @param status
   * @param name
   * @param adminAccount
   * @param principal - the principal we are processing or null.
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public PrincipalProcessor(final CrawlStatus status,
                            final String name,
                            final String adminAccount,
                            final String principal,
                            final long batchDelay,
                            final long entityDelay,
                            final List<String> skipPaths,
                            final String indexRootPath) throws CalFacadeException {
    super(status, name, adminAccount,
          principal, batchDelay, entityDelay, skipPaths, indexRootPath);
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.crawler.Processor#process(java.lang.String)
   */
  @Override
  public void process() throws CalFacadeException {
    /* Index the current principal
     */

    CalSvcI svc = null;

    try {
      svc = getSvci();

      indexCollection(svc.getCalendarsHandler().getHomePath());

      status.stats.inc(IndexStats.StatType.categories,
                       svc.getCategoriesHandler().reindex());
    } finally {
      close();
    }
  }
}
