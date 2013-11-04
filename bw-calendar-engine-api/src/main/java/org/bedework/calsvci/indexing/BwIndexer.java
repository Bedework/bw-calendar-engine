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
package org.bedework.calsvci.indexing;

import org.bedework.access.Acl;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.indexing.Index;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * @author douglm
 *
 */
public interface BwIndexer extends Serializable {
  // Types of entity we index
  static final String docTypeUnknown = "unknown";
  static final String docTypeCollection = "collection";
  static final String docTypeCategory = "category";

  /* Other types are those defined in IcalDefs.entityTypeNames */

  interface AccessChecker extends Serializable {
    /** Check the access for the given entity. Returns the current access
     * or null or optionally throws a no access exception.
     *
     * @param ent
     * @param desiredAccess
     * @param returnResult
     * @return CurrentAccess
     * @throws CalFacadeException if returnResult false and no access
     */
    Acl.CurrentAccess checkAccess(BwShareableDbentity ent,
                                  int desiredAccess,
                                  boolean returnResult)
            throws CalFacadeException;
  }

  /** Called to find entries that match the search string. This string may
   * be a simple sequence of keywords or some sort of query the syntax of
   * which is determined by the underlying implementation.
   *
   * @param query        Query string
   * @param filter       parsed filter
   * @param start - if non-null limit to this and after
   * @param end - if non-null limit to before this
   * @param pageSize - stored in the search result for future calls.
   * @param accessCheck  - required - lets us check access
   * @return  SearchResult - never null
   * @throws CalFacadeException
   */
  SearchResult search(String query,
                      FilterBase filter,
                      String start,
                      String end,
                      int pageSize,
                      AccessChecker accessCheck) throws CalFacadeException;

  /** Called to retrieve results after a search of the index. Updates
   * the current search result.
   *
   * @param  sres     result of previous search
   * @param forward - true if we are moving forwards
   * @throws CalFacadeException
   */
  List<SearchResultEntry> getSearchResult(SearchResult sres,
                                          boolean forward) throws CalFacadeException;

  /** Called to retrieve results after a search of the index. Updates
   * the SearchResult object
   *
   * @param  sres     result of previous search
   * @param offset from first record
   * @param num
   * @return list of results - possibly empty - never null.
   * @throws CalFacadeException
   */
  List<SearchResultEntry> getSearchResult(SearchResult sres,
                                          int offset,
                                          int num) throws CalFacadeException;

  /** Called to retrieve record keys from the result.
   *
   * @param   sres     result of previous search
   * @param   n        Starting index
   * @param   keys     Array for the record keys
   * @return  int      Actual number of records
   * @throws CalFacadeException
   */
  long getKeys(SearchResult sres,
               long n, Index.Key[] keys) throws CalFacadeException;

  /** Called to unindex a record
   *
   * @param   rec      The record to unindex
   * @throws CalFacadeException
   */
  void unindexEntity(Object rec) throws CalFacadeException;

  /** Called to index a record
   *
   * @param rec an indexable object
   * @throws CalFacadeException
   */
  void indexEntity(Object rec) throws CalFacadeException;

  /** Set to > 1 to enable batching
   *
   * @param val batch size
   */
  void setBatchSize(int val);

  /** Called at the end of a batch of updates.
   *
   * @throws CalFacadeException
   */
  void endBwBatch() throws CalFacadeException;

  /** Flush any batched entities.
   * @throws CalFacadeException
   */
  void flush() throws CalFacadeException;

  /** Indicate if we should try to clean locks. (lucene)
   *
   * @param val true/false
   */
  void setCleanLocks(boolean val);

  /** create a new index and start using it.
   *
   * @param name basis for new name - in solr the core
   * @return name of created index.
   * @throws CalFacadeException
   */
  String newIndex(String name) throws CalFacadeException;

  /** List all indexes maintained by server
   *
   * @return names of indexes.
   * @throws CalFacadeException
   */
  List<String> listIndexes() throws CalFacadeException;

  /** Purge non-current indexes maintained by server.
   *
   * @param preserve - list of indexes to preserve
   * @return names of indexes removed.
   * @throws CalFacadeException
   */
  List<String> purgeIndexes(List<String> preserve) throws CalFacadeException;

  /** create a new index and start using it.
   *
   * @param index   swap this with the other
   * @param other   new index
   * @return 0 for OK or HTTP status from indexer
   * @throws CalFacadeException
   */
  int swapIndex(String index, String other) throws CalFacadeException;

  /** If true the fetchEvents method can be used to retrieve events that are
   * adequate for display at least. They are rebuilt from th indexed data and
   * will be incomplete. In particular there is no access control.
   *
   * <p>This approach may be fine for public events and cheaper than retrieval
   * from the core system.
   *
   * @return true if this interface will support the fetching of events
   * @throws CalFacadeException
   */
  boolean isFetchEnabled() throws CalFacadeException;

  /** Find a category owned by the current user which has a named
   * field which matches the value.
   *
   * @param field e.g. "uid", "word"
   * @param val - expected full value
   * @return null or detached category object
   * @throws CalFacadeException
   */
  BwCategory fetchCat(String field, String val) throws CalFacadeException;

  /**
   *
   * @return possibly empty list
   * @throws CalFacadeException
   */
  List<BwCategory> fetchAllCats() throws CalFacadeException;
}
