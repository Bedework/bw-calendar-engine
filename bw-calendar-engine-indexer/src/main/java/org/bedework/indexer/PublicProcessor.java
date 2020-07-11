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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calsvci.CalSvcI;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeFilter;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResource;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResourceContent;

/** This implementation crawls the public subtree indexing entries. This is harder
 * to multithread as we don't generally have the hierarchy to subdivide it.
 *
 * <p>There are subtrees we should not go down. We need a configuration which
 * tells us the paths to avoid.
 *
 * @author douglm
 *
 */
public class PublicProcessor extends Crawler {
  private final String docType;

  /** Run a thread which reads the children of the root directory. Each child
   * is a home directory. We read a batch of these at a time and then start
   * up maxThreads processes to handle the user data.
   *
   * When we've finished the entire tree we stop.
   *
   * if principal is non-null then we are processing a user/group etc
   * so we just descend through the collections indexing stuff.
   *
   * @param status to keep track of progress
   * @param name of processor
   * @param adminAccount the account to use
   * @param batchDelay millis
   * @param entityDelay millis
   * @param skipPaths - paths to skip
   * @param indexNames - where we build the index
   * @param docType - if non-null only index this type. Cannot
   *                    be collection or events types
   */
  public PublicProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final long batchDelay,
                         final long entityDelay,
                         final List<String> skipPaths,
                         final Map<String, String> indexNames,
                         final String docType) {
    super(status, name, adminAccount, null, batchDelay, entityDelay,
          skipPaths,
          indexNames);

    this.docType = docType;
  }

  @Override
  public void process() {
    try (final BwSvc bw = getBw()) {
      final CalSvcI svc = bw.getSvci();

      /* First index the public collection(s) */
      if (docType == null) {
        indexCollection(svc,
                        BasicSystemProperties.publicCalendarRootPath);
      }

      final BwPrincipal pr = svc.getUsersHandler().getPublicUser();

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

      if (testType(docTypePrincipal)) {
        final Iterator<BwAdminGroup> it = getAdminGroups(svc);
        while (it.hasNext()) {
          getIndexer(svc, principal,
                     docTypePrincipal).indexEntity(it.next());
          status.stats.inc(IndexedType.principals, 1);
        }
      }

      if (testType(docTypePrincipal)) {
        final Iterator<BwCalSuite> csit = svc.getDumpHandler()
                                             .getCalSuites();
        while (csit.hasNext()) {
          getIndexer(svc, principal,
                     docTypePrincipal).indexEntity(BwCalSuitePrincipal.from(csit.next()));
          status.stats.inc(IndexedType.principals, 1);
        }
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
        int[] res = svc.getResourcesHandler().reindex(
                getIndexer(svc, principal,
                           docTypeResource),
                getIndexer(svc, principal,
                           docTypeResourceContent),
                getIndexer(svc, principal,
                           docTypeCollection));
        status.stats.inc(IndexedType.resources, res[0]);
        status.stats.inc(IndexedType.resourceContents, res[1]);
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

  public Iterator<BwAdminGroup> getAdminGroups(final CalSvcI svc) throws CalFacadeException {
    return svc.getDumpHandler().getAdminGroups();
  }

  private boolean testType(final String type) {
    return docType == null || (docType.equals(type));
  }
}
