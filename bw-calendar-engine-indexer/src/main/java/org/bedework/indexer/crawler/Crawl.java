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

import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexLuceneDefs;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.CalSvcI;
import org.bedework.indexer.CalSys;
import org.bedework.indexer.IndexStats;

import edu.rpi.sss.util.DateTimeUtil;
import edu.rpi.sss.util.Util;

import java.io.File;
import java.util.List;

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

  //private String indexBuildLocationPrefix;

  protected long batchDelay;
  protected long entityDelay;

  private List<String> skipPaths;
  private int maxPublicThreads;
  private int maxUserThreads;

  private ThreadGroup publicThreads = new ThreadGroup("Public threads");

  private ThreadGroup userThreads = new ThreadGroup("User threads");

  private boolean doPublic;
  private boolean doUser;

  private boolean useSolr;

  private BwSystem sys;

  /**
   * @param adminAccount
   * @param indexBuildLocationPrefix - if non null prefix the system path with this
   * @param skipPaths - paths to skip
   * @param maxPublicThreads
   * @param maxUserThreads
   * @throws CalFacadeException
   */
  public Crawl(final String adminAccount,
               final String indexBuildLocationPrefix,
               final List<String> skipPaths,
               final int maxPublicThreads,
               final int maxUserThreads) throws CalFacadeException {
    super("Crawler", adminAccount,
          //true,
          null);

    this.skipPaths = skipPaths;
    this.maxPublicThreads = maxPublicThreads;
    this.maxUserThreads = maxUserThreads;

    doPublic = maxPublicThreads > 0;
    doUser = maxUserThreads > 0;

    CalSvcI svci = null;
    try {
      svci = getAdminSvci();

      sys = svci.getSysparsHandler().get();

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
   * @param userStatus - status of user reindexer
   * @param publicStatus - status of public reindexer
   * @param status - overall status
   * @throws CalFacadeException
   */
  public void crawl(final CrawlStatus userStatus,
                    final CrawlStatus publicStatus,
                    final CrawlStatus status) throws CalFacadeException {
    Indexes idxs = newIndexes(status);

    userStatus.stats = new IndexStats("Statistics for User");
    publicStatus.stats = new IndexStats("Statistics for Public");

    /* Now we can reindex into the new directory */

    /* We can skip public or user only if we are using solr for indexing.
     */
    if ((!doPublic || !doUser) && !useSolr) {
      setStatus(status, "Partial indexing is only valid when using solr");

      return;
    }

    UserProcessor uproc = null;
    PublicProcessor pproc = null;

    if (doUser) {
      uproc = new UserProcessor(userStatus,
                                "User",
                                adminAccount, // admin account
                                2000,   // batchDelay,
                                100,    // entityDelay,
                                skipPaths,
                                idxs.userIndex,
                                userThreads,
                                maxUserThreads);
      uproc.start(Util.buildPath(true, "/", getUserCalendarRoot()));
    }

    if (doPublic) {
      pproc = new PublicProcessor(publicStatus,
                                  "Public",
                                  adminAccount, // admin account
                                  2000,   // batchDelay,
                                  100,    // entityDelay,
                                  skipPaths,
                                  idxs.publicIndex,
                                  publicThreads,
                                  maxPublicThreads);
      pproc.start(Util.buildPath(true, "/", getPublicCalendarRoot()));
    }

    if (doUser) {
      setStatus(status, "Wait for user indexing to complete");
      uproc.join();
      setStatus(status, "User indexing completed");
    }

    if (doPublic) {
      setStatus(status, "Wait for public indexing to complete");
      pproc.join();
      setStatus(status, "Public indexing completed");
    }

    endIndexing(status, idxs);
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

  /**
   * @return public thread group
   */
  public ThreadGroup getPublicThreads() {
    return publicThreads;
  }

  /**
   * @return user thread group
   */
  public ThreadGroup getUserThreads() {
    return userThreads;
  }

  @Override
  public void putIndexer(final BwIndexer val) throws CalFacadeException {
  }

  @Override
  public BwIndexer getIndexer() throws CalFacadeException {
    return null;
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
