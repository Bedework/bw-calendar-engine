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
package org.bedework.calsvc.indexing;

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;

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

  /** Factory method to get current indexer
   *
   * @param publick
   * @param principal - who we are searching for
   * @param writeable true if the caller can update the index
   * @param syspars
   * @return indexer
   * @throws CalFacadeException
   */
  public static BwIndexer getIndexer(final boolean publick,
                                     final String principal,
                                     final boolean writeable,
                                     final SystemProperties syspars) throws CalFacadeException {
    try {
      if (publick) {
          return new BwIndexSolrImpl(true,
                                     principal,
                                     syspars.getSolrURL(), writeable,
                                     syspars.getMaxYears(),
                                     syspars.getMaxInstances(),
                                     syspars.getSolrPublicCore(),
                                     null); // No admin
      }

      return new BwIndexSolrImpl(false,
                                 principal,
                                 syspars.getSolrURL(),
                                 writeable,
                                 syspars.getMaxYears(),
                                 syspars.getMaxInstances(),
                                 syspars.getSolrUserCore(),
                                 null); // No admin
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Factory method allowing us to specify the system root. This should only
   * be called from the crawler which will be indexing into an alternative
   * index.
   *
   * @param principal
   * @param writeable true if the caller can update the index
   * @param syspars
   * @param indexRoot
   * @return indexer
   * @param adminPath  - path for administration of cores
   * @throws CalFacadeException
   */
  public static BwIndexer getIndexer(final String principal,
                                     final boolean writeable,
                                     final SystemProperties syspars,
                                     final String indexRoot,
                                     final String adminPath) throws CalFacadeException {
    try {
      return new BwIndexSolrImpl(true,
                                 principal,
                                 syspars.getSolrURL(), writeable,
                                 syspars.getMaxYears(),
                                 syspars.getMaxInstances(),
                                 indexRoot,
                                 adminPath);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
