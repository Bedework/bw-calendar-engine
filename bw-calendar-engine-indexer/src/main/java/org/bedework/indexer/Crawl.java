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
import org.bedework.calsvc.indexing.BwIndexLuceneDefs;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.CalSvcI;

import edu.rpi.sss.util.DateTimeUtil;

import java.io.File;
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
  private String indexBuildLocation;

  private List<CrawlStatus> statuses = new ArrayList<CrawlStatus>();

  protected long batchDelay;
  protected long entityDelay;

  private List<String> skipPaths;

  private boolean doPublic;
  private boolean doUser;

  private boolean useSolr;

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

      useSolr = sys.getUseSolr();
    } finally {
      close(svci);
    }

    if (indexBuildLocationPrefix == null) {
      indexBuildLocation = getIndexRoot();
    } else {
      indexBuildLocation = getPath(indexBuildLocationPrefix,
                                   getIndexRoot());
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

    /* We can skip public or user only if we are using solr for indexing.
     */
    if ((!doPublic || !doUser) && !useSolr) {
      setStatus(status, "Partial indexing is only valid when using solr");

      return;
    }

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

  private Indexes newIndexes(final CrawlStatus cr) throws CalFacadeException {
    Indexes idxs = new Indexes();

    if (doUser) {
      /* First set up a new directory - see if new exists - if so delete */
      idxs.userIndex = getPath(indexBuildLocation,
                               BwIndexLuceneDefs.newIndexname);

      boolean newExisted = exists(idxs.userIndex);

      setStatus(cr, "Build directories at " + idxs.userIndex);

      if (newExisted) {
        remove(idxs.userIndex);
      }

      create(idxs.userIndex);
    }

    if (!useSolr) {
      idxs.publicIndex = idxs.userIndex;
      return idxs;
    }

    if (doPublic) {
      // Switch public indexes.

      String defName = sys.getSolrDefaultCore();

      idxs.publicIndex = defName + "-" + DateTimeUtil.isoDateTime();

      setStatus(cr, "Switch solr core to " + idxs.publicIndex);

      BwIndexer idx = BwIndexerFactory.getIndexer(true, adminAccount, true, sys,
                                                  idxs.publicIndex,
                                                  sys.getSolrCoreAdmin());

      idx.newIndex(idxs.publicIndex);
    }

    return idxs;
  }

  private void endIndexing(final CrawlStatus cr,
                           final Indexes idxs) throws CalFacadeException {
    /* If it's lucene both public and user are the same.
     */

    if (doUser) {
      /* Rename directories */
      String oldPath = getPath(indexBuildLocation,
                               BwIndexLuceneDefs.oldIndexname);

      boolean oldExisted = exists(oldPath);

      if (oldExisted) {
        remove(oldPath);
      }

      String currentPath = getPath(indexBuildLocation,
                                   BwIndexLuceneDefs.currentIndexname);

      boolean currentExisted = exists(currentPath);

      if (currentExisted) {
        setStatus(cr, "About to rename directories from " + currentPath +
                    "  to " + oldPath);

        if (!rename(currentPath, oldPath)) {
          setStatus(cr, "Unable to rename current index at " + currentPath +
                     "  to " + oldPath);

          return;
        }
      }

      setStatus(cr, "About to rename directories from " + idxs.userIndex +
                  "  to " + currentPath);

      if (!rename(idxs.userIndex, currentPath)) {
        outErr(cr, "Unable to rename new index at " + idxs.userIndex +
                   "  to " + currentPath +
                   " attempting to rename old back to current");
        if (!rename(oldPath, currentPath)) {
          /* Now  we have no index at all. */
          outErr(cr, "Unable to restore current index to " + currentPath +
                     "  from " + oldPath);
        }

        return;
      }
    }

    if (doPublic && useSolr) {
      BwIndexer idx = BwIndexerFactory.getIndexer(true, adminAccount, true, sys,
                                                  idxs.publicIndex,
                                                  sys.getSolrCoreAdmin());

      idx.swapIndex(idxs.publicIndex, sys.getSolrDefaultCore());
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

  private boolean exists(final String path) throws CalFacadeException {
    try {
      return new File(path).exists();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private boolean remove(final String path) throws CalFacadeException {
    /* Rename rather than delete */
    try {
      String renameTo = path + "-" + DateTimeUtil.isoDateTime();
      return new File(path).renameTo(new File(renameTo));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private boolean create(final String path) throws CalFacadeException {
    try {
      return new File(path).exists();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private boolean rename(final String oldPath,
                         final String newPath) throws CalFacadeException {
    try {
      return new File(oldPath).renameTo(new File(newPath));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private String getPath(final String prefix, final String suffix) {
    StringBuilder sb = new StringBuilder(prefix);

    if (!prefix.endsWith("/")) {
      sb.append("/");
    }

    sb.append(suffix);

    return sb.toString();
  }
}
