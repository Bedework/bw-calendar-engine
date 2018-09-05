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
package org.bedework.calcore.common.indexing;

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.indexing.BwIndexFetcher;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.util.AccessChecker;

/** Create an instance of an indexer for bedework.
 *
 * @author douglm
 *
 */
public class BwIndexerFactory {
  /* No instantiation
   */
  private BwIndexerFactory() {
  }

  /** Factory method to get indexer
   *
   * @param configs to configure indexer
   * @param docType type of entity
   * @param currentMode - guest, user,publicAdmin
   * @param accessCheck  - required - lets us check access
   * @return indexer
   */
  public static BwIndexer getPublicIndexer(final Configurations configs,
                                           final String docType,
                                           final int currentMode,
                                           final AccessChecker accessCheck,
                                           final BwIndexFetcher indexFetcher) {
    return new BwIndexEsImpl(configs,
                             docType,
                             true,
                             null,    // principal
                             false,   // super user
                             currentMode,
                             accessCheck,
                             indexFetcher,
                             null); // Not reindexing
  }

  /** Factory method to get current indexer
   *
   * @param configs to configure indexer
   * @param docType type of entity
   * @param principal - who we are searching for
   * @param superUser - true if the principal is a superuser.
   * @param currentMode - guest, user,publicAdmin
   * @param accessCheck  - required - lets us check access
   * @return indexer
   */
  public static BwIndexer getIndexer(final Configurations configs,
                                     final String docType,
                                     final BwPrincipal principal,
                                     final boolean superUser,
                                     final int currentMode,
                                     final AccessChecker accessCheck,
                                     final BwIndexFetcher indexFetcher) {
    return new BwIndexEsImpl(configs,
                             docType,
                             false,
                             principal,
                             superUser,
                             currentMode,
                             accessCheck,
                             indexFetcher,
                             null); // Not reindexing
  }

  /** Factory method allowing us to specify the system root. This should only
   * be called from the crawler which will be indexing into an alternative
   * index.
   *
   * @param configs to configure indexer
   * @param docType type of entity
   * @param principal - who we are searching for
   * @param currentMode - guest, user,publicAdmin
   * @param accessCheck  - required - lets us check access
   * @param indexRoot
   * @return indexer
   */
  public static BwIndexer getIndexerForReindex(final Configurations configs,
                                               final String docType,
                                               final BwPrincipal principal,
                                               final int currentMode,
                                               final AccessChecker accessCheck,
                                               final BwIndexFetcher indexFetcher,
                                               final String indexRoot) {
    return new BwIndexEsImpl(configs,
                             docType,
                             true,
                             principal,
                             false,
                             currentMode,
                             accessCheck,
                             indexFetcher,
                             indexRoot); // Explicit name
  }
}
