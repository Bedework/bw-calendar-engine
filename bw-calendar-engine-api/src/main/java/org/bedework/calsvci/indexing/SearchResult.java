package org.bedework.calsvci.indexing;

import java.io.Serializable;
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
   * @return Total number found. 0 means none found,
   *                                -1 means indeterminate
   */
  long getFound();

  /**
   * @return for a paged request the next record index
   */
  int getPageStart();

  /**
   *
   * @return for a paged request the max number of hits
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
}
