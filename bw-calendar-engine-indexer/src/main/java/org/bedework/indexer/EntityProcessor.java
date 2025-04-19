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

import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcI;

import java.util.Collection;
import java.util.Map;

import static java.lang.String.format;

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

  private int indexStart;

  /** Index a bunch of entities given the names.
   * @param status crawler status object
   * @param name to identify process
   * @param adminAccount for admin access
   * @param principal - the principal we are processing or null.
   * @param entityDelay delay in millisecs between entities (unused)
   * @param path for collection
   * @param entityNames paths to index
   * @param indexNames - where we build the index
   * @param indexStart where we are in the process.
   */
  public EntityProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final String principal,
                         final long entityDelay,
                         final String path,
                         final Collection<String> entityNames,
                         final Map<String, String> indexNames,
                         final int indexStart) {
    super(status, name, adminAccount,
          principal, 0, entityDelay, null, indexNames);
    this.path = path;
    this.entityNames = entityNames;
    this.indexStart = indexStart;
  }

  @Override
  public void process() {
    try (final BwSvc bw = getBw()) {
      final CalSvcI svci = bw.getSvci();

      final BwIndexer entIndexer = getIndexer(svci,
                                              principal,
                                              BwIndexer.docTypeEvent);

      final var evs = svci.getEventsHandler();
      final var cols = svci.getCollectionsHandler();
      var index = indexStart;

      for (final String name: entityNames) {
        try {
          info(format("Indexing entity(%d) %s/%s",
                      index, path, name));
          index++;

          status.stats.inc(IndexedType.events);
          final EventInfo ent = evs.get(path, name);

          if (ent == null) {
            status.stats.inc(IndexedType.unreachableEntities);
            info(format("      unreachable entity(%d) %s/%s",
                        index, path, name));
            continue;
          }

          final var ev = ent.getEvent();
          if (ev.getTombstoned()) {
            final var token = ev.getCtoken();
            if (!cols.getSyncTokenIsValid(token, path)) {
              status.skippedTombstonedEvents++;
              info(format("      skipped tombstoned entity(%d) %s/%s",
                          index, path, name));
              continue;
            }
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
    } catch (final Throwable t) {
      error(t);
    }
  }
}
