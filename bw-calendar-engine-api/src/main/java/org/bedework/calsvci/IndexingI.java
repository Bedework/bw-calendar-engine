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
package org.bedework.calsvci;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexer;

import edu.rpi.cct.misc.indexing.SearchLimits;

import java.io.Serializable;
import java.util.Collection;

/** Interface for handling bedework index searching.
 *
 * @author Mike Douglass
 *
 */
public interface IndexingI extends Serializable {
  /** Convenience method to limit to now onwards.
   *
   * @return SearchLimits
   */
  public SearchLimits fromToday();

  /** Convenience method to limit to before now.
   *
   * @return SearchLimits
   */
  public SearchLimits beforeToday();

  /** Called to search an index. If publick is false use the principal to
   * identify the index.
   *
   * XXX Security implications here. We should probably not return a count for
   * searching another users entries - or we should get an accurate count of
   * entries this user has access to.
   *
   * @param publick
   * @param principal ignored if publick is true
   * @param   query    Query string
   * @param   limits   limits or null
   * @return  int      Number found. 0 means none found,
   *                                -1 means indeterminate
   * @throws CalFacadeException
   */
  public int search(boolean publick,
                    String principal,
                    String query,
                    SearchLimits limits) throws CalFacadeException;

  /** Called to retrieve results after a search of the index.
   *
   * @param start
   * @param num
   * @param   limits   limits or null
   * @return  Collection of BwIndexSearchResultEntry
   * @throws CalFacadeException
   */
  public Collection<BwIndexSearchResultEntry> getSearchResult(int start,
                                                              int num,
                                                              SearchLimits limits)
        throws CalFacadeException;

  /**
   * @param publick
   * @param principal
   * @return the indexer
   * @throws CalFacadeException
   */
  public BwIndexer getIndexer(final boolean publick,
                              String principal) throws CalFacadeException;
}
