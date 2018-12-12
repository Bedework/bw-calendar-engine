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

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;

import java.util.Collection;
import java.util.Map;

/** Run to index entities.
 *
 * @author Mike Douglass
 *
 */
public class EntityProcessor extends Crawler {
  private final Collection<String> entityNames;
  private final String path;

  @SuppressWarnings("FieldCanBeLocal")
  private final int maxErrors = 10;
  private int errors;

  /** Index a bunch of entities given the names.
   * @param status crawler status object
   * @param name to identify process
   * @param adminAccount for admin access
   * @param principal - the principal we are processing or null.
   * @param entityDelay delay in millisecs between entities (unused)
   * @param path for collection
   * @param entityNames paths to index
   * @param indexNames - where we build the index
   * @throws CalFacadeException
   */
  public EntityProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final String principal,
                         final long entityDelay,
                         final String path,
                         final Collection<String> entityNames,
                         final Map<String, String> indexNames) throws CalFacadeException {
    super(status, name, adminAccount,
          principal, 0, entityDelay, null, indexNames);
    this.path = path;
    this.entityNames = entityNames;
  }

  /* (non-Javadoc)
   * @see java.lang.Thread#run()
   */
  @Override
  public void process() throws CalFacadeException {
    try {
      try (BwSvc bw = getBw()) {
        final CalSvcI svci = bw.getSvci();

        final BwIndexer entIndexer = getIndexer(svci,
                                                principal,
                                                BwIndexer.docTypeEvent);

        for (final String name: entityNames) {
          try {
            if (debug()) {
              debug("Indexing collection " + path +
                       " entity " + name);
            }

            status.stats.inc(IndexedType.events);
            final EventInfo ent =
                    svci.getEventsHandler().get(path, name);

            if (ent == null) {
              status.stats.inc(IndexedType.unreachableEntities);
              continue;
            }
            entIndexer.indexEntity(ent);
          } catch (final Throwable t) {
            error(t);

            errors++;

            if (errors > maxErrors) {
              error("Too many errors (" + errors + "): terminating");
              break;
            }
          }
        }
      }
    } catch (final Throwable t) {
      error(t);
    }
  }
}
