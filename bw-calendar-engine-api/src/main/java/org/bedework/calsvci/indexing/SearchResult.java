package org.bedework.calsvci.indexing;

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
   * @return for a paged request the page index
   */
  long getPageNum();

  /**
   *
   * @return for a paged request the page size
   */
  int getPageSize();

  /**
   *
   * @return start date limit applied to original search - possibly null.
   */
  String getStart();

  /**
   *
   * @return end date limit applied to original search - possibly null.
   */
  String getEnd();

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
