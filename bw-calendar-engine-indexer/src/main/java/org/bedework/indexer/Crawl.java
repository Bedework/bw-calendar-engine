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

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.CalSvcI;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** A class to crawl the entire data structure reindexing as it proceeds.
 *
 * <p>The reindexing process creates an index directory under the index root
 * named so that it is distinguishable as a temporary directory. After indexing
 * it renames that directory so that it has the form recognized by the search
 * and update routines.
 *
 * <p>The names take the forms <ul>
 * <li>new - currently being built</li>
 * <li>current - one we are using now for update and search</li>
 * <li>old - previous index before reindex</li>
 * <li>old-yyyyMMddThhmmss - timestamped renamed from old</li>
 * </ul>
 *
 * <p>The search and update engines will try with a path that assumes "current".
 * If the search or update fails they retry with "old". This process should allow
 * reading of the index to proceed without failure while a reindex operation is
 * in process and during the renaming of index directories.
 *
 * @author douglm
 *
 */
public class Crawl extends CalSys {
  private List<CrawlStatus> statuses = new ArrayList<CrawlStatus>();

  protected long batchDelay;
  protected long entityDelay;

  private List<String> skipPaths;

  private boolean doPublic;
  private boolean doUser;

  private SystemProperties sys;

  /**
   * @param adminAccount
   * @param indexBuildLocationPrefix - if non null prefix the system path with this
   * @param skipPaths - paths to skip
   * @param maxEntityThreads
   * @param maxPrincipalThreads
   * @param doPublic
   * @param doUser
   * @throws CalFacadeException
   */
  public Crawl(final String adminAccount,
               final String indexBuildLocationPrefix,
               final List<String> skipPaths,
               final int maxEntityThreads,
               final int maxPrincipalThreads,
               final boolean doPublic,
               final boolean doUser) throws CalFacadeException {
    super("Crawler", adminAccount,
          //true,
          null);

    this.skipPaths = skipPaths;
    this.doPublic = doPublic;
    this.doUser = doUser;

    setThreadPools(maxEntityThreads, maxPrincipalThreads);

    CalSvcI svci = null;
    try {
      svci = getAdminSvci();

      sys = svci.getSystemProperties();
    } finally {
      close(svci);
    }
  }

  static class Indexes {
    String publicIndex;

    String userIndex;
  }

  /**
   * @throws CalFacadeException
   */
  public void crawl() throws CalFacadeException {
    long start = System.currentTimeMillis();

    CrawlStatus prstats = new CrawlStatus("Statistics for Principals");
    statuses.add(prstats);

    CrawlStatus pubstats = new CrawlStatus("Statistics for Public");
    statuses.add(pubstats);

    CrawlStatus status = new CrawlStatus("Overall status");
    statuses.add(status);
    Indexes idxs = newIndexes(status);

    /* Now we can reindex into the new directory */

    PrincipalsProcessor prProc = null;
    PublicProcessor pubProc = null;

    if (doUser) {
      prProc = new PrincipalsProcessor(prstats,
                                       "Principals",
                                       adminAccount, // admin account
                                       2000,   // batchDelay,
                                       100,    // entityDelay,
                                       skipPaths,
                                       idxs.userIndex);
      prProc.start();
    }

    if (doPublic) {
      pubProc = new PublicProcessor(pubstats,
                                    "Public",
                                    adminAccount, // admin account
                                    2000,   // batchDelay,
                                    100,    // entityDelay,
                                    skipPaths,
                                    idxs.publicIndex);
      pubProc.start();
    }

    if (doUser) {
      setStatus(status, "Wait for user indexing to complete");
      prProc.join();
      setStatus(status, "User indexing completed");
    }

    if (doPublic) {
      setStatus(status, "Wait for public indexing to complete");
      pubProc.join();
      setStatus(status, "Public indexing completed");
    }

    endIndexing(status, idxs);

    long millis = System.currentTimeMillis() - start;
    status.infoLines.add("Indexing took " +
      String.format("%d min, %d sec",
                    TimeUnit.MILLISECONDS.toMinutes(millis),
                    TimeUnit.MILLISECONDS.toSeconds(millis) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
              ));
  }

  /**
   * @return status or null
   */
  public List<CrawlStatus> getStatus() {
    return statuses;
  }

  /**
   * @return list of indexes maintained by indexer.
   * @throws CalFacadeException
   */
  public List<String> listIndexes() throws CalFacadeException {
    BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, false, sys,
                                                sys.getSolrPublicCore(),
                                                sys.getSolrCoreAdmin());

    return idx.listIndexes();
  }

  /** Purge non-current indexes maintained by server.
   *
   * @return names of indexes removed.
   * @throws CalFacadeException
   */
  public List<String> purgeIndexes() throws CalFacadeException {
    List<String> preserve = new ArrayList<String>();

    preserve.add(sys.getSolrPublicCore());
    preserve.add(sys.getSolrUserCore());

    BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, false, sys,
                                                sys.getSolrPublicCore(),
                                                sys.getSolrCoreAdmin());

    return idx.purgeIndexes(preserve);
  }

  private Indexes newIndexes(final CrawlStatus cr) throws CalFacadeException {
    Indexes idxs = new Indexes();

    if (doUser) {
      // Switch user indexes.

      if (sys.getSolrUserCore() == null) {
        outErr(cr, "No user index core defined in system properties");
        throw new CalFacadeException("No user index core defined in system properties");
      }

      BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, true, sys,
                                                  idxs.userIndex,
                                                  sys.getSolrCoreAdmin());

      idxs.userIndex = idx.newIndex(sys.getSolrUserCore());

      setStatus(cr, "Switched solr core to " + idxs.userIndex);
    }

    if (doPublic) {
      // Switch public indexes.

      if (sys.getSolrPublicCore() == null) {
        outErr(cr, "No public index core defined in system properties");
        throw new CalFacadeException("No public index core defined in system properties");
      }

      BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, true, sys,
                                                  idxs.publicIndex,
                                                  sys.getSolrCoreAdmin());

      idxs.publicIndex = idx.newIndex(sys.getSolrPublicCore());

      setStatus(cr, "Switched solr core to " + idxs.publicIndex);
    }

    return idxs;
  }

  private void endIndexing(final CrawlStatus cr,
                           final Indexes idxs) throws CalFacadeException {
    /* If it's lucene both public and user are the same.
     */

    if (doUser) {
      BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, true, sys,
                                                  idxs.userIndex,
                                                  sys.getSolrCoreAdmin());

      idx.swapIndex(idxs.userIndex, sys.getSolrUserCore());
    }

    if (doPublic) {
      BwIndexer idx = BwIndexerFactory.getIndexer(adminAccount, true, sys,
                                                  idxs.publicIndex,
                                                  sys.getSolrCoreAdmin());

      idx.swapIndex(idxs.publicIndex, sys.getSolrPublicCore());
    }
  }

  private void setStatus(final CrawlStatus cr, final String msg) {
    cr.currentStatus = msg;
    outInfo(cr, msg);
  }

  private void outInfo(final CrawlStatus cr, final String msg) {
    cr.infoLines.add(msg + "\n");
    info(msg);
  }

  private void outErr(final CrawlStatus cr, final String msg) {
    cr.infoLines.add(msg + "\n");
    error(msg);
  }
}
