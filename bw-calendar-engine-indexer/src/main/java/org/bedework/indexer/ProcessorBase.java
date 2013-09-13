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
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.indexing.BwIndexer;
import org.bedework.indexer.IndexStats.StatType;
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

  private String currentPath;

  private List<String> skipPaths;

  private List<String> unprefixedSkipPaths;

  private List<String> prefixedSkipPaths;

  protected CrawlStatus status;

  protected String indexRootPath;

  /**
   * @param name
   * @param adminAccount
   * @param principal
   * @param batchDelay - delay between batches - milliseconds
   * @param entityDelay - delay between entities - milliseconds
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public ProcessorBase(final String name,
                       final String adminAccount,
                       final String principal,
                       final long batchDelay,
                       final long entityDelay,
                       final List<String> skipPaths,
                       final String indexRootPath) throws CalFacadeException {
    super(name, adminAccount, principal);

    this.batchDelay = batchDelay;
    this.entityDelay = entityDelay;
    this.indexRootPath = indexRootPath;
    this.skipPaths = skipPaths;

    if (skipPaths != null) {
      unprefixedSkipPaths = new ArrayList<String>();
      prefixedSkipPaths = new ArrayList<String>();

      for (String sp: skipPaths) {
        String fixed = Util.buildPath(true, sp);

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

  @Override
  public String getCurrentPath() throws CalFacadeException {
    return currentPath;
  }

  protected boolean skipThis(final String path) {
    if (unprefixedSkipPaths == null) {
      return false;
    }

    String fixed = Util.buildPath(true, path);

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
      debugMsg("indexCollection(" + path + ")");
    }

    status.currentStatus = "indexCollection(" + path + ")";

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

      BwIndexer indexer =getSvci().getIndexer(principal,
                                              indexRootPath);

      indexer.indexEntity(col);
//      close();

      Refs refs = null;

      for (;;) {
        refs = getChildCollections(path, refs);

        if (refs == null) {
          break;
        }

        for (String cpath: refs.refs) {
          indexCollection(cpath);
        }
      }

      if (!col.getCollectionInfo().onlyCalEntities ||
          !col.getCollectionInfo().indexable) {
        return;
      }

      refs = null;
      svci = getSvci();

      for (;;) {
        refs = getChildEntities(path, refs);

        if (refs == null) {
          break;
        }

        EntityProcessor ep = new EntityProcessor(status,
                                                 name + ":Entity",
                                                 adminAccount,
                                                 principal,
                                                 entityDelay,
                                                 path,
                                                 refs.refs,
                                                 indexRootPath);

        IndexerThread eit = getEntityThread(ep);

        eit.start();
      }
    } catch (Throwable t) {
      error(t);
//    } finally {
  //    close();
    }
  }
}
