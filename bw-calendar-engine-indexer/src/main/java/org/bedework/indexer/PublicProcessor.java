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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.misc.Util;

import java.util.Iterator;
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
   * @param status to keep track of progress
   * @param name of processor
   * @param adminAccount the account to use
   * @param batchDelay millis
   * @param entityDelay millis
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   */
  public PublicProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final long batchDelay,
                         final long entityDelay,
                         final List<String> skipPaths,
                         final String indexRootPath) {
    super(status, name, adminAccount, null, batchDelay, entityDelay,
          skipPaths,
          indexRootPath);
  }

  @Override
  public void process() throws CalFacadeException {
    try (BwSvc bw = getBw()) {
      final CalSvcI svc = bw.getSvci();

      /* First index the public collection(s) */
      indexCollection(svc, Util.buildPath(false, "/", getPublicCalendarRoot()));

      final BwIndexer indexer = svc.getIndexer(principal,
                                               indexRootPath);

      final BwPrincipal pr = svc.getUsersHandler().getPublicUser();
      indexer.indexEntity(pr);
      status.stats.inc(IndexedType.principals, 1);
      indexer.indexEntity(svc.getPreferences(pr.getPrincipalRef()));
      status.stats.inc(IndexedType.preferences, 1);

      final Iterator<BwAdminGroup> it = getAdminGroups(svc);
      while (it.hasNext()) {
        indexer.indexEntity(it.next());
        status.stats.inc(IndexedType.principals, 1);
      }

      final Iterator<BwCalSuite> csit = svc.getDumpHandler().getCalSuites();
      while (csit.hasNext()) {
        indexer.indexEntity(BwCalSuitePrincipal.from(csit.next()));
        status.stats.inc(IndexedType.principals, 1);
      }

      status.stats.inc(IndexedType.categories,
                       svc.getCategoriesHandler().reindex(indexer));

      status.stats.inc(IndexedType.contacts,
                       svc.getContactsHandler().reindex(indexer));

      status.stats.inc(IndexedType.locations,
                       svc.getLocationsHandler().reindex(indexer));
    }
  }

  public Iterator<BwAdminGroup> getAdminGroups(final CalSvcI svc) throws CalFacadeException {
    return svc.getDumpHandler().getAdminGroups();
  }
}
