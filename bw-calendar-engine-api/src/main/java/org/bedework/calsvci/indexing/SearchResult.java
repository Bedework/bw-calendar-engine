package org.bedework.calsvci.indexing;

import org.bedework.util.indexing.SearchLimits;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * User: douglm Date: 9/12/13 Time: 4:07 PM
 */
public interface SearchResult extends Serializable {
  /**
   * @return indexer used to get this search.
   */
  BwIndexer getIndexer();

  /**
   * @return Number found. 0 means none found,
   *                                -1 means indeterminate
   */
  long getFound();

  /**
   * @return for a paged request the start index
   */
  long getStart();

  /**
   *
   * @return for a paged request the page size
   */
  int getPageSize();

  /**
   *
   * @return limits applied to original search - possibly null.
   */
  SearchLimits getLimits();

  /**
   * @return names of returned facets - possibly null
   */
  Set<String> getFacetNames();

  /**
   *
   * @return the entries from calling BwIndexer#getSearchResult
   */
  List<SearchResultEntry> getSearchResult();
}
