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

import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexInfo;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.calfacade.indexing.ReindexResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
  private final IndexProperties props;

  private final List<CrawlStatus> statuses = new ArrayList<>();

  private Class entityClass;

  private boolean sameIndex;

  //protected long batchDelay;
  //protected long entityDelay;

  //private final IndexProperties idxProps;
  //private final AuthProperties authProps;
  //private final AuthProperties unauthProps;

  /**
   * @param props - our config
   */
  public Crawl(final IndexProperties props) {
    super("Crawler", props.getAccount(),
          //true,
          null);

    this.props = props;

    setThreadPools(props.getMaxEntityThreads(),
                   props.getMaxPrincipalThreads());

    /*
    try (BwSvc bw = getAdminBw()) {
      final CalSvcI svci = bw.getSvci();

      idxProps = svci.getIndexProperties();
      authProps = svci.getAuthProperties(true);
      unauthProps = svci.getAuthProperties(false);
    }
    */
  }

  public void setEntityClass(final Class entityClass) {
    this.entityClass = entityClass;
  }

  public void sameIndex() {
    this.sameIndex = true;
  }

  /**
   * @throws CalFacadeException
   */
  @SuppressWarnings("ConstantConditions")
  public void crawl() throws CalFacadeException {
    final long start = System.currentTimeMillis();

    final CrawlStatus prstats = new CrawlStatus("Statistics for Principals");
    statuses.add(prstats);

    final CrawlStatus pubstats = new CrawlStatus("Statistics for Public");
    statuses.add(pubstats);

    final CrawlStatus status = new CrawlStatus("Overall status");
    statuses.add(status);
    final String indexName;

    if (sameIndex) {
      indexName = null;
    } else {
      indexName = newIndexes(status);
    }

    /* Now we can reindex into the new directory */

    PrincipalsProcessor prProc = null;
    PublicProcessor pubProc = null;

    if (props.getIndexUsers()) {
      prProc = new PrincipalsProcessor(prstats,
                                       "Principals",
                                       adminAccount, // admin account
                                       2000,   // batchDelay,
                                       100,    // entityDelay,
                                       props.getSkipPathsList(),
                                       indexName,
                                       entityClass);
      prProc.start();
    }

    if (props.getIndexPublic()) {
      pubProc = new PublicProcessor(pubstats,
                                    "Public",
                                    adminAccount, // admin account
                                    2000,   // batchDelay,
                                    100,    // entityDelay,
                                    props.getSkipPathsList(),
                                    indexName,
                                    entityClass);
      pubProc.start();
    }

    if (props.getIndexUsers()) {
      setStatus(status, "Wait for user indexing to complete");
      prProc.join();
      setStatus(status, "User indexing completed");
    }

    if (props.getIndexPublic()) {
      setStatus(status, "Wait for public indexing to complete");
      pubProc.join();
      setStatus(status, "Public indexing completed");
    }

    final long millis = System.currentTimeMillis() - start;
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
   * @return info on indexes maintained by indexer.
   * @throws CalFacadeException
   */
  public Set<IndexInfo> getIndexInfo() throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      final BwIndexer idx = bw.getSvci().getIndexer(adminAccount, null);

      return idx.getIndexInfo();
    }
  }

  /** Purge non-current indexes maintained by server.
   *
   * @return names of indexes removed.
   * @throws CalFacadeException
   */
  public List<String> purgeIndexes() throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      final BwIndexer idx = bw.getSvci().getIndexer(adminAccount,
                                                    null);

      return idx.purgeIndexes();
    }
  }

  public String newIndexes() throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      // Switch indexes.

      if (props.getUserIndexName() == null) {
        throw new CalFacadeException(
                "No index name defined in system properties");
      }

      final BwIndexer idx = bw.getSvci().getIndexer(adminAccount,
                                                    null);

      return idx.newIndex(props.getUserIndexName());
    }
  }

  public ReindexResponse reindex(final String indexName) throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      // Switch indexes.

      if (props.getUserIndexName() == null) {
        throw new CalFacadeException(
                "No index name defined in system properties");
      }

      final BwIndexer idx = bw.getSvci().getIndexer(adminAccount,
                                                    null);

      return idx.reindex(indexName);
    }
  }

  public IndexStatsResponse getIndexStats(final String indexName) throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      return bw.getSvci().getIndexer(adminAccount,
                                     null).getIndexStats(indexName);
    }
  }

  /** Move the production index alias to the given index
   *
   * @param indexName name of index to be aliased
   * @return status code- 0 for OK
   * @throws CalFacadeException on error
   */
  public int setProdAlias(final String indexName) throws CalFacadeException {
    try (BwSvc bw = getAdminBw()) {
      final BwIndexer idx = bw.getSvci().getIndexer(adminAccount,
                                                    indexName);

      return idx.setAlias(indexName, props.getUserIndexName());
    }
  }

  private String newIndexes(final CrawlStatus cr) throws CalFacadeException {
    try {
      final String indexName = newIndexes();

      setStatus(cr, "Switched index to " + indexName);

      return indexName;
    } catch (final Throwable t) {
      outErr(cr, t.getLocalizedMessage());
      throw t;
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
