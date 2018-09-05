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
package org.bedework.indexer;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calsvci.CalSvcI;

import java.util.List;
import java.util.Map;

/** This implementation crawls the user subtree indexing user entries.
 *
 * @author douglm
 *
 */
public class PrincipalProcessor extends Crawler {
  BwPrincipal publicUser = null;

  private final Class entityClass;

  /** Constructor for an entity thread processor. These handle the entities
   * found within a collection.
   *
   * @param status of the process
   * @param name for the thread
   * @param adminAccount to use
   * @param principal - the principal we are processing or null.
   * @param batchDelay between batches
   * @param entityDelay betwen entities
   * @param skipPaths - paths to skip
   * @param indexNames - where we build the index
   * @param entityClass - if non-null only index this class. Cannot
   *                    be collection or events classes
   * @throws CalFacadeException on fatal error
   */
  public PrincipalProcessor(final CrawlStatus status,
                            final String name,
                            final String adminAccount,
                            final String principal,
                            final long batchDelay,
                            final long entityDelay,
                            final List<String> skipPaths,
                            final Map<String, String> indexNames,
                            final Class entityClass) throws CalFacadeException {
    super(status, name, adminAccount,
          principal, batchDelay, entityDelay, skipPaths, indexNames);

    this.entityClass = entityClass;
  }

  @Override
  public void process() throws CalFacadeException {
    /* Index the current principal
     */

    try (BwSvc bw = getBw()) {
      final CalSvcI svc = bw.getSvci();

      if (publicUser == null) {
        publicUser = svc.getUsersHandler().getPublicUser();
      }

      if (entityClass == null) {
        indexCollection(svc, svc.getCalendarsHandler().getHomePath());
      }

      /* Skip the public owner here as public entities are already
       * indexed by the public processor
       */

      if (principal.equals(publicUser.getPrincipalRef())) {
        return;
      }

      final BwPrincipal pr =
              svc.getUsersHandler().getPrincipal(principal);

      if (testClass(BwPrincipal.class)) {
        getIndexer(svc, principal,
                   BwIndexer.docTypePrincipal).indexEntity(pr);
        status.stats.inc(IndexedType.principals, 1);
      }

      if (testClass(BwPreferences.class)) {
        getIndexer(svc, principal,
                   BwIndexer.docTypePreferences).indexEntity(svc.getPreferences(pr.getPrincipalRef()));
        status.stats.inc(IndexedType.preferences, 1);
      }

      if (testClass(BwCategory.class)) {
        status.stats.inc(IndexedType.categories,
                         svc.getCategoriesHandler().reindex(getIndexer(svc, principal,
                                                                       BwIndexer.docTypeCategory)));
      }

      if (testClass(BwContact.class)) {
        status.stats.inc(IndexedType.contacts,
                         svc.getContactsHandler().reindex(getIndexer(svc, principal,
                                                                     BwIndexer.docTypeContact)));
      }

      if (testClass(BwLocation.class)) {
        status.stats.inc(IndexedType.locations,
                         svc.getLocationsHandler().reindex(getIndexer(svc, principal,
                                                                      BwIndexer.docTypeLocation)));
      }

      if (testClass(BwResource.class)) {
        status.stats.inc(IndexedType.resources,
                         svc.getResourcesHandler().reindex(getIndexer(svc, principal,
                                                                      BwIndexer.docTypeResource)));
      }

      if (testClass(BwFilterDef.class)) {
        status.stats.inc(IndexedType.filters,
                         svc.getFiltersHandler().reindex(getIndexer(svc, principal,
                                                                    BwIndexer.docTypeResourceContent)));
      }
    }
  }

  private boolean testClass(final Class cl) {
    return entityClass == null || (entityClass.equals(cl));
  }
}
