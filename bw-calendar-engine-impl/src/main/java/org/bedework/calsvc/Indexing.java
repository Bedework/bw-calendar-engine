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
package org.bedework.calsvc;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.indexing.BwIndexKey;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.BwIndexSearchResultEntry;
import org.bedework.calsvci.IndexingI;

import edu.rpi.cct.misc.indexing.Index;
import edu.rpi.cct.misc.indexing.IndexException;
import edu.rpi.cct.misc.indexing.SearchLimits;
import edu.rpi.sss.util.DateTimeUtil;

import java.util.ArrayList;
import java.util.Collection;

/** This acts as an interface to the database for index searching.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Indexing extends CalSvcDb implements IndexingI {
  transient private BwIndexer publicIndexer;

  transient private BwIndexer userIndexer;

  // XXX - transient might cause intermittent problems
  /* Has to be transient otherwise the session is not serializable.
   * However, we need the indexer object to retrieve the search result.
   *
   * Reimplement with a serializable object which allows us to redo the query
   * if we lose the indexer - or return an opaque search result which includes
   * the indexer.
   */
  transient private BwIndexer searchIndexer;

  Indexing(final CalSvc svci) throws CalFacadeException {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.IndexingI#fromToday()
   */
  @Override
  public SearchLimits fromToday() {
    SearchLimits lim = new SearchLimits();

    lim.fromDate = DateTimeUtil.isoDate(new java.util.Date());

    return lim;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.IndexingI#beforeToday()
   */
  @Override
  public SearchLimits beforeToday() {
    SearchLimits lim = new SearchLimits();

    lim.toDate = DateTimeUtil.isoDate(DateTimeUtil.yesterday());

    return lim;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.IndexingI#search(boolean, java.lang.String, java.lang.String, edu.rpi.cct.misc.indexing.SearchLimits)
   */
  @Override
  public int search(final boolean publick,
                    final String principal,
                    final String query,
                    final SearchLimits limits) throws CalFacadeException {
    searchIndexer = getIndexer(publick, principal);

    return searchIndexer.search(query, limits);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.IndexingI#getSearchResult(int, int)
   */
  @Override
  public Collection<BwIndexSearchResultEntry> getSearchResult(final int start,
                                                              int num,
                                                              final SearchLimits limits)
                                                              throws CalFacadeException {
    Collection<BwIndexSearchResultEntry> res =
      new ArrayList<BwIndexSearchResultEntry>();

    if (searchIndexer == null) {
      return res;
    }

    if (num > 100) {
      num = 100;
    }
    Index.Key[] keys = new Index.Key[num];

    num = searchIndexer.getKeys(start, keys);

    BwDateTime dtStart = null;
    BwDateTime dtEnd = null;

    /*
    if (limits != null) {
      if (limits.fromDate != null) {
        dtStart = BwDateTime.makeBwDateTime(true, limits.fromDate, null);
      }

      if (limits.toDate != null) {
        dtEnd = BwDateTime.makeBwDateTime(true, limits.toDate, null);
      }
    }*/

    for (int i = 0; i < num; i++) {
      BwIndexKey key = (BwIndexKey)keys[i];

      Object sres;
      try {
        sres = key.getRecord(getSvc(), dtStart, dtEnd);
      } catch (IndexException ie) {
        throw new CalFacadeException(ie);
      }

      if (sres == null) {
        // Assume access changed and made it invisible
        continue;
      }

      if (sres instanceof Collection) {
        Collection<?> c = (Collection)sres;

        for (Object ent: c) {
          if (ent instanceof EventInfo) {
            res.add(new BwIndexSearchResultEntry((EventInfo)ent, key.score));
          } else {
            throw new CalFacadeException("org.bedework.index.unexpected.class");
          }
        }
      } else if (sres instanceof BwCalendar) {
        BwCalendar cal = (BwCalendar)sres;

        res.add(new BwIndexSearchResultEntry(cal, key.score));
      } else {
        throw new CalFacadeException("org.bedework.index.unexpected.class");
      }
    }

    return res;
  }

  @Override
  public BwIndexer getIndexer(final boolean publick,
                              String principal) throws CalFacadeException {
    try {
      BwIndexer indexer;

      if (publick) {
        indexer = publicIndexer;
      } else {
        indexer = userIndexer;
      }

      if (principal == null) {
        principal = getPrincipal().getPrincipalRef();
      }

      boolean writeable = !isGuest();

      if (indexer == null) {
        indexer = BwIndexerFactory.getIndexer(publick, principal, writeable,
                                              getSvc().getSysparsHandler().get());
        if (publick) {
          publicIndexer = indexer;
        } else {
          userIndexer = indexer;
        }
      }

      return indexer;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */
}
