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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.List;

/** Provide a number of useful common methods for processors.
 *
 * @author douglm
 *
 */
public abstract class ProcessorBase extends CalSys implements Processor {
  protected long batchDelay;
  protected long entityDelay;

  private final List<String> skipPaths;

  private List<String> unprefixedSkipPaths;

  private List<String> prefixedSkipPaths;

  protected CrawlStatus status;

  protected String indexRootPath;

  /**
   * @param name to identify
   * @param adminAccount admin
   * @param principal principal
   * @param batchDelay - delay between batches - milliseconds
   * @param entityDelay - delay between entities - milliseconds
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   */
  public ProcessorBase(final String name,
                       final String adminAccount,
                       final String principal,
                       final long batchDelay,
                       final long entityDelay,
                       final List<String> skipPaths,
                       final String indexRootPath) {
    super(name, adminAccount, principal);

    this.batchDelay = batchDelay;
    this.entityDelay = entityDelay;
    this.indexRootPath = indexRootPath;
    this.skipPaths = skipPaths;

    if (skipPaths != null) {
      unprefixedSkipPaths = new ArrayList<>();
      prefixedSkipPaths = new ArrayList<>();

      for (final String sp: skipPaths) {
        final String fixed = Util.buildPath(true, sp);

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
   * @param val crawl status object
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

  protected boolean skipThis(final String path) {
    if (unprefixedSkipPaths == null) {
      return false;
    }

    final String fixed = Util.buildPath(true, path);

    if (unprefixedSkipPaths.contains(fixed)) {
      return true;
    }

    for (final String psp: prefixedSkipPaths) {
      if (fixed.endsWith(psp)) {
        return true;
      }
    }

    return false;
  }

  protected void indexCollection(final CalSvcI svci,
                                 final String path) throws CalFacadeException {
    if (skipThis(path)) {
      if (debug) {
        debug("Skipping " + path);
      }
      return;
    }

    if (debug) {
      debug("indexCollection(" + path + ")");
    }

    status.currentStatus = "indexCollection(" + path + ")";

    status.stats.inc(IndexedType.collections);

    try {
      BwCalendar col = null;

      try {
        col = svci.getCalendarsHandler().get(path);
      } catch (final CalFacadeAccessException cfe) {
        error("No access to " + path);
      }

      if ((col == null) || !hasAccess(col)) {
        if (debug) {
          debug("path " + path + " not found");
        }

        return;
      }

      final BwIndexer indexer = svci.getIndexer(principal,
                                                indexRootPath);

      indexer.indexEntity(col);
//      close();

      final CollectionInfo ci = col.getCollectionInfo();
      if (!ci.childrenAllowed) {
        return;
      }
      
      Refs refs = null;

      for (;;) {
        refs = getChildCollections(path, refs);

        if (refs == null) {
          break;
        }

        for (final String cpath: refs.refs) {
          indexCollection(svci, cpath);
        }
      }

      if (!ci.onlyCalEntities ||
          !ci.indexable) {
        return;
      }

      refs = null;

      for (;;) {
        refs = getChildEntities(path, refs);

        if (refs == null) {
          break;
        }

        final EntityProcessor ep = new EntityProcessor(status,
                                                       name + ":Entity",
                                                       adminAccount,
                                                       principal,
                                                       entityDelay,
                                                       path,
                                                       refs.refs,
                                                       indexRootPath);

        final IndexerThread eit = getEntityThread(ep);

        eit.start();
      }
    } catch (final Throwable t) {
      error(t);
    }
  }
}
