/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.exc.CalFacadeException;

/**
 * User: mike Date: 7/5/19 Time: 23:27
 */
public class IndexerConsumer {
  final BwIndexer indexer;

  public IndexerConsumer(final BwIndexer indexer) {
    this.indexer = indexer;
  }

  public void consume(final Object o) throws CalFacadeException {
    indexer.indexEntity(0);
  }
}
