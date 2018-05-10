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
package org.bedework.calfacade.indexing;

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public interface BwIndexer extends Serializable {
  // Types of entity we index
  String docTypeUnknown = "unknown";
  String docTypePrincipal = "principal";
  String docTypeCollection = "collection";
  String docTypeCategory = "category";
  String docTypeLocation = "location";
  String docTypeContact = "contact";
  String docTypeEvent = "event";
  //        IcalDefs.entityTypeNames[IcalDefs.entityTypeEvent];
  String docTypePoll = "vpoll";
  //        IcalDefs.entityTypeNames[IcalDefs.entityTypeVpoll];

  /** */
  public enum IndexedType {
    /** */
    principals(docTypePrincipal),

    /** */
    collections(docTypeCollection),

    /** */
    events(docTypeEvent),

    /** */
    vpoll(docTypePoll),

    /** */
    categories(docTypeCategory),

    /** */
    contacts(docTypeContact),

    /** */
    locations(docTypeLocation),

    /** */
    unreachableEntities(docTypeUnknown);
    
    private final String docType;

    private IndexedType(final String docType) {
      this.docType = docType;
    }

    public String getDocType() {
      return docType;
    }
  }
  
  /* Following used for the id */
  String[] masterDocTypes = {
          "masterEvent",
          null,  // alarm
          "masterTask",
          "masterJournal",
          null,   // freebusy
          null,   // vavail
          "masterAvailable",
          "masterVpoll",   // vpoll
  };

  String[] overrideDocTypes = {
          "overrideEvent",
          null,  // alarm
          "overrideTask",
          "overrideJournal",
          null,   // freebusy
          null,   // vavail
          "overrideAvailable",
          "overrideVpoll",   // vpoll
  };

  /** Used for fetching master + override
   *
   */
  String[] masterOverrideEventTypes = {
          "masterEvent",
          "masterTask",
          "overrideEvent",
          "overrideTask",
          "masterAvailable",
          "overrideAvailable",
  };

  /* Other types are those defined in IcalDefs.entityTypeNames */

  /**
   * @return true if this is a public indexer
   */
  boolean getPublic();

  /** Flag the end of a transaction - updates the updateTracker if any
   * changes were made to the index.
   *
   * @throws CalFacadeException
   */
  void markTransaction() throws CalFacadeException;

  /**
   * @return a token based on the update tracker value.
   * @throws CalFacadeException
   */
  String currentChangeToken() throws CalFacadeException;

  /** Will return a response indicating what happened. An immediate 
   * response with status processing indicates a process is already 
   * running.
   * 
   * @param name of index
   * @return final statistics
   */
  ReindexResponse reindex(String name);

  /**
   *
   * @param indexName of index
   * @return current statistics
   */
  ReindexResponse getReindexStatus(String indexName);

  /**
   *
   * @param indexName of index
   * @return current statistics
   */
  IndexStatsResponse getIndexStats(String indexName);

  enum DeletedState {
    onlyDeleted, // Only deleted entities in result
    noDeleted,   // No deleted entities in result
    includeDeleted // Deleted and non-deleted in result
  }

  /** Called to find entries that match the search string. This string may
   * be a simple sequence of keywords or some sort of query the syntax of
   * which is determined by the underlying implementation.
   *
   * <p>defaultFilterContext is a temporary fix until the client is
   * fully upgraded. This is applied as the context for the search if
   * present and no other context is provided. For example, in the user
   * client the default context includes all the user calendars,
   * not the inbox. If no path is selected we apply the default. If a
   * path IS selected we do not apply the default. This allows, for
   * instance, selection of the inbox.</p>
   *
   * @param query        Query string
   * @param relevance    true for a relevance style query
   * @param filter       parsed filter
   * @param sort  list of fields to sort by - may be null
   * @param defaultFilterContext  - see above
   * @param start - if non-null limit to this and after
   * @param end - if non-null limit to before this
   * @param pageSize - stored in the search result for future calls.
   * @param recurRetrieval How recurring event is returned.
   * @return  SearchResult - never null
   * @throws CalFacadeException
   */
  SearchResult search(String query,
                      boolean relevance,
                      FilterBase filter,
                      List<SortTerm> sort,
                      FilterBase defaultFilterContext,
                      String start,
                      String end,
                      int pageSize,
                      DeletedState deletedState,
                      RecurringRetrievalMode recurRetrieval) throws CalFacadeException;

  enum Position {
    previous,  // Move to previous batch
    current,   // Return the current set
    next       // Move to next batch
  }

  /** Called to retrieve results after a search of the index. Updates
   * the current search result.
   *
   * @param  sres     result of previous search
   * @param pos - specify movement in result set
   * @param desiredAccess  to the entities
   * @throws CalFacadeException
   */
  List<SearchResultEntry> getSearchResult(SearchResult sres,
                                          Position pos,
                                          int desiredAccess) throws CalFacadeException;

  /** Called to retrieve results after a search of the index. Updates
   * the SearchResult object
   *
   * @param  sres     result of previous search
   * @param offset from first record
   * @param num number of entries
   * @param desiredAccess  to the entities
   * @return list of results - possibly empty - never null.
   * @throws CalFacadeException
   */
  List<SearchResultEntry> getSearchResult(SearchResult sres,
                                          int offset,
                                          int num,
                                          int desiredAccess) throws CalFacadeException;
  /** Called to unindex an entity
   *
   * @param   val     an event property
   * @throws CalFacadeException
   */
  void unindexEntity(BwEventProperty val) throws CalFacadeException;

  /** Called to unindex an entity
   *
   * @param   href     the entities href
   * @throws CalFacadeException
   */
  void unindexEntity(String href) throws CalFacadeException;

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

  /** create a new index and start using it.
   *
   * @param name basis for new name - in solr the core
   * @return name of created index.
   * @throws CalFacadeException
   */
  String newIndex(String name) throws CalFacadeException;

  class IndexInfo implements Comparable<IndexInfo>, Serializable {
    private final String indexName;

    private Set<String> aliases;

    /**
     * @param indexName name of the index
     */
    public IndexInfo(final String indexName) {
      this.indexName = indexName;
    }

    /**
     *
     * @return index name
     */
    public String getIndexName() {
      return indexName;
    }

    /**
     * @param val - set of aliases - never null
     */
    public void setAliases(final Set<String> val) {
      aliases = val;
    }

    /**
     * @return - set of aliases - never null
     */
    public Set<String> getAliases() {
      return aliases;
    }

    /**
     * @param val - an alias - never null
     */
    public void addAlias(final String val) {
      if (aliases == null) {
        aliases = new TreeSet<>();
      }

      aliases.add(val);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public int compareTo(final IndexInfo o) {
      return getIndexName().compareTo(o.getIndexName());
    }
  }

  /** Get info on indexes maintained by server
   *
   * @return list of index info.
   * @throws CalFacadeException
   */
  Set<IndexInfo> getIndexInfo() throws CalFacadeException;

  /** Purge non-current indexes maintained by server.
   *
   * @return names of indexes removed.
   * @throws CalFacadeException
   */
  List<String> purgeIndexes() throws CalFacadeException;

  /** Set alias on the given index - usually to make it the production index.
   *
   * @param index   name of index to be aliased
   * @param alias   to point at index
   * @return 0 for OK or HTTP status from indexer
   * @throws CalFacadeException
   */
  int setAlias(String index, String alias) throws CalFacadeException;

  /** Href of event with possible anchor tag for recurrence id.
   *
   * @param href of event
   * @return entity is EventInfo with overrides if present
   * @throws CalFacadeException on fatal error
   */
  GetEntityResponse<EventInfo> fetchEvent(String href) throws CalFacadeException;

  /** Find a category owned by the current user which has a named
   * field which matches the value.
   *
   * @param val - expected full value
   * @param index e.g. UID or CN, VALUE
   * @return null or category object
   * @throws CalFacadeException
   */
  BwCategory fetchCat(String val,
                      PropertyInfoIndex... index) throws CalFacadeException;

  /** Fetch all for the current principal.
   *
   * @return possibly empty list
   * @throws CalFacadeException
   */
  List<BwCategory> fetchAllCats() throws CalFacadeException;

  /** Find a collection which has a named
   * field which matches the value.
   *
   * @param val - expected full value
   * @param index e.g. HREF, UID or CN, VALUE
   * @return null or collection object
   * @throws CalFacadeException
   */
  BwCalendar fetchCol(String val,
                      PropertyInfoIndex... index) throws CalFacadeException;

  /** Fetch children of the collection with the given href.
   *
   * @param href of parent
   * @return possibly empty list of children
   * @throws CalFacadeException
   */
  Collection<BwCalendar> fetchChildren(String href) throws CalFacadeException;

  /** Find a contact owned by the current user which has a named
   * field which matches the value.
   *
   * @param val - expected full value
   * @param index e.g. UID or CN, VALUE
   * @return null or contact object
   * @throws CalFacadeException
   */
  BwContact fetchContact(String val,
                         PropertyInfoIndex... index) throws CalFacadeException;

  /** Fetch all for the current principal.
   *
   * @return possibly empty list
   * @throws CalFacadeException
   */
  List<BwContact> fetchAllContacts() throws CalFacadeException;

  /**
   *
   * @param filter expression
   * @param from start for result
   * @param size max number
   * @return status and locations
   */
  GetEntitiesResponse<BwContact> findContacts(FilterBase filter,
                                              int from,
                                              int size);

  /** Find a location owned by the current user which has a named
   * field which matches the value.
   *
   * @param val - expected full value
   * @param index e.g. UID or CN, VALUE
   * @return null or location object
   * @throws CalFacadeException
   */
  BwLocation fetchLocation(String val,
                           PropertyInfoIndex... index) throws CalFacadeException;

  /** Find a location owned by the current user which has a named
   * key field which matches the value.
   *
   * @param name - of key field
   * @param val - expected full value
   * @return null or location object
   */
  GetEntityResponse<BwLocation> fetchLocationByKey(String name,
                                                   String val);

  /**
   *
   * @param filter expression
   * @param from start for result
   * @param size max number
   * @return status and locations
   */
  GetEntitiesResponse<BwLocation> findLocations(FilterBase filter,
                                                int from,
                                                int size);

  /** Fetch all for the current principal.
   *
   * @return possibly empty list
   * @throws CalFacadeException
   */
  List<BwLocation> fetchAllLocations() throws CalFacadeException;

  /**
   *
   * @param filter expression
   * @param from start for result
   * @param size max number
   * @return status and categories
   */
  GetEntitiesResponse<BwCategory> findCategories(FilterBase filter,
                                                 int from,
                                                 int size);
}
