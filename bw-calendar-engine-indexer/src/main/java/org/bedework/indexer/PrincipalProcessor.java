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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.base.response.GetEntityResponse;

import java.util.List;
import java.util.Map;

import static org.bedework.access.PrivilegeDefs.privAny;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeFilter;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResource;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResourceContent;
import static org.bedework.base.response.Response.Status.ok;

/** This implementation crawls the user subtree indexing user entries.
 *
 * @author douglm
 *
 */
public class PrincipalProcessor extends Crawler {
  BwPrincipal publicUser = null;

  private final String docType;

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
   * @param docType - if non-null only index this type. Cannot
   *                    be collection or events types
   */
  public PrincipalProcessor(final CrawlStatus status,
                            final String name,
                            final String adminAccount,
                            final String principal,
                            final long batchDelay,
                            final long entityDelay,
                            final List<String> skipPaths,
                            final Map<String, String> indexNames,
                            final String docType) {
    super(status, name, adminAccount,
          principal, batchDelay, entityDelay, skipPaths, indexNames);

    this.docType = docType;
  }

  @Override
  public void process() {
    /* Index the current principal
     */

    try (BwSvc bw = getBw()) {
      final CalSvcI svc = bw.getSvci();

      if (publicUser == null) {
        publicUser = svc.getUsersHandler().getPublicUser();
      }

      if (docType == null) {
        final String homePath = svc.getCalendarsHandler().getHomePath();
        final GetEntityResponse<BwCalendar> ger =
                getIndexer(svc,
                           principal,
                           BwIndexer.docTypeCollection)
                        .fetchCol(homePath,
                                  privAny,
                                  PropertyIndex.PropertyInfoIndex.HREF);
        if (ger.getStatus() != ok) {
          indexCollection(svc, homePath);
        }
      }

      /* Skip the public owner here as public entities are already
       * indexed by the public processor
       */

      if (principal.equals(publicUser.getPrincipalRef())) {
        return;
      }

      final BwPrincipal pr =
              svc.getUsersHandler().getPrincipal(principal);

      if (testType(docTypePrincipal)) {
        getIndexer(svc, principal,
                   docTypePrincipal).indexEntity(pr);
        status.stats.inc(IndexedType.principals, 1);
      }

      if (testType(docTypePreferences)) {
        getIndexer(svc, principal,
                   docTypePreferences).indexEntity(svc.getPreferences(pr.getPrincipalRef()));
        status.stats.inc(IndexedType.preferences, 1);
      }

      if (testType(docTypeCategory)) {
        status.stats.inc(IndexedType.categories,
                         svc.getCategoriesHandler().reindex(getIndexer(svc, principal,
                                                                       docTypeCategory)));
      }

      if (testType(docTypeContact)) {
        status.stats.inc(IndexedType.contacts,
                         svc.getContactsHandler().reindex(getIndexer(svc, principal,
                                                                     docTypeContact)));
      }

      if (testType(docTypeLocation)) {
        status.stats.inc(IndexedType.locations,
                         svc.getLocationsHandler().reindex(getIndexer(svc, principal,
                                                                      docTypeLocation)));
      }

      if (testType(docTypeResource)) {
        var res = svc.getResourcesHandler().reindex(
                getIndexer(svc, principal,
                           docTypeResource),
                getIndexer(svc, principal,
                           docTypeResourceContent),
                getIndexer(svc, principal,
                           docTypeCollection));
        status.stats.inc(IndexedType.resources, res.resources);
        status.stats.inc(IndexedType.resourceContents, res.resourceContents);
        status.skippedTombstonedResources +=
                res.skippedTombstonedResources;
      }

      if (testType(docTypeFilter)) {
        status.stats.inc(IndexedType.filters,
                         svc.getFiltersHandler().reindex(getIndexer(svc, principal,
                                                                    docTypeFilter)));
      }
    } catch (final Throwable t) {
      error(t);
    }
  }

  private boolean testType(final String type) {
    return docType == null || (docType.equals(type));
  }
}
