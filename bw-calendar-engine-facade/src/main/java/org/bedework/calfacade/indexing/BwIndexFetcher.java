/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.util.misc.response.GetEntityResponse;

/** Allow one indexer to fetch resources from other indexes
 * User: mike Date: 9/5/18 Time: 09:51
 */
public interface BwIndexFetcher {
  /**
   *
   * @param params for getting indexer
   * @param href of entity
   * @return response containing status and entity
   */
  GetEntityResponse<BwCategory> fetchCategory(BwIndexerParams params,
                                              String href);

  /**
   *
   * @param params for getting indexer
   * @param href of entity
   * @return response containing status and entity
   */
  GetEntityResponse<BwContact> fetchContact(BwIndexerParams params,
                                            String href);

  /**
   *
   * @param params for getting indexer
   * @param href of entity
   * @return response containing status and entity
   */
  GetEntityResponse<BwLocation> fetchLocation(BwIndexerParams params,
                                              String href);
}
