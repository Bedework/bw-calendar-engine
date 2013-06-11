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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.CalSvcI;
import org.bedework.indexer.CalSys;
import org.bedework.indexer.EntityIndexerThread;
import org.bedework.indexer.IndexStats.StatType;
import org.bedework.indexer.ThreadPool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Provide a number of useful common methods for processors.
 *
 * @author douglm
 *
 */
public abstract class ProcessorBase extends CalSys implements Processor {
  protected long batchDelay;
  protected long entityDelay;

  private boolean publick;

  private String currentPath;

  private List<String> skipPaths;

  private List<String> unprefixedSkipPaths;

  private List<String> prefixedSkipPaths;

  protected CrawlStatus status;

  private ThreadPool tpool;

  protected String indexRootPath;

  /**
   * @param name
   * @param adminAccount
   * @param publick
   * @param principal
   * @param batchDelay - delay between batches - milliseconds
   * @param entityDelay - delay between entities - milliseconds
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public ProcessorBase(final String name,
                       final String adminAccount,
                       final boolean publick,
                       final String principal,
                       final long batchDelay,
                       final long entityDelay,
                       final List<String> skipPaths,
                       final String indexRootPath) throws CalFacadeException {
    super(name, adminAccount, principal);

    this.publick = publick;
    this.batchDelay = batchDelay;
    this.entityDelay = entityDelay;
    this.indexRootPath = indexRootPath;
    this.skipPaths = skipPaths;

    if (skipPaths != null) {
      unprefixedSkipPaths = new ArrayList<String>();
      prefixedSkipPaths = new ArrayList<String>();

      for (String sp: skipPaths) {
        String fixed = fixColPath(sp);

        if (fixed.startsWith("*")) {
          prefixedSkipPaths.add(fixed.substring(1));
        } else {
          unprefixedSkipPaths.add(fixed);
        }
      }
    }
  }

  /**
   * @return supplied list of skip paths
   */
  public List<String> getSkipPaths() {
    return skipPaths;
  }

  /**
   * @param val
   */
  public void setStatus(final CrawlStatus val) {
    status = val;
  }

  /**
   * @return CrawlStatus
   */
  public CrawlStatus getStatus() {
    return status;
  }

  /**
   * @param val
   */
  public void setThreadPool(final ThreadPool val) {
    tpool = val;
  }

  /**
   * @return ThreadPool
   */
  public ThreadPool getThreadPool() {
    return tpool;
  }

  @Override
  public String getCurrentPath() throws CalFacadeException {
    return currentPath;
  }

  protected boolean skipThis(final String path) {
    if (unprefixedSkipPaths == null) {
      return false;
    }

    String fixed = fixColPath(path);

    if (unprefixedSkipPaths.contains(fixed)) {
      return true;
    }

    for (String psp: prefixedSkipPaths) {
      if (fixed.endsWith(psp)) {
        return true;
      }
    }

    return false;
  }

  protected void indexCollection(final String path) throws CalFacadeException {
    if (skipThis(path)) {
      if (debug) {
        debugMsg("Skipping " + path);
      }
      return;
    }

    if (debug) {
      debugMsg("indexCollections(" + path + ")");
    }
    status.currentStatus = "indexCollections(" + path + ")";

    status.stats.inc(StatType.collections);

    CalSvcI svci = null;

    try {
      svci = getSvci();

      BwCalendar col = null;

      try {
        col = svci.getCalendarsHandler().get(path);
      } catch (CalFacadeAccessException cfe) {
        error("No access to " + path);
      }

      if ((col == null) || !hasAccess(col)) {
        if (debug) {
          debugMsg("path " + path + " not found");
        }

        return;
      }

      BwIndexer indexer = getIndexer();

      indexer.indexEntity(col);
      close();

      int batchIndex = 0;

      for (;;) {
        Collection<String> childCols = getChildCollections(path, batchIndex);

        if (childCols == null) {
          break;
        }

        for (String cpath: childCols) {
          indexCollection(cpath);
        }

        batchIndex += childCols.size();
      }

      if (!col.getCollectionInfo().onlyCalEntities ||
          !col.getCollectionInfo().indexable) {
        return;
      }

      batchIndex = 0;

      svci = getSvci();

      for (;;) {
        Collection<String> childEnts = getChildEntities(path, batchIndex);

        if (childEnts == null) {
          break;
        }

        EntityIndexerThread eit = tpool.getProcessor();

        eit.setPathNames(//publick,
                         principal, path, childEnts);

        /* This disables the multi-thread processing. To enable it we need to
         * call start(). However, even single-threaded we seem to be stressing
         * the system currently.
         */
        eit.run();

        batchIndex += childEnts.size();
      }
    } catch (Throwable t) {
      error(t);
    } finally {
      close();
    }
  }

  @Override
  public BwIndexer getIndexer() throws CalFacadeException {
    if (!publick && (principal == null)) {
      /* Not down to the user collections yet */
      if (debug) {
        debugMsg("No indexer");
      }
      return null;
    }

    return BwIndexerFactory.getIndexer(publick, principal,
                                       true, getSyspars(),
                                       indexRootPath,
                                       null);
  }

  @Override
  public void putIndexer(final BwIndexer val) throws CalFacadeException {
  }

  protected String fixColPath(final String val) {
    if (val.endsWith("/")) {
      return val;
    }

    return val + "/";
  }
}
