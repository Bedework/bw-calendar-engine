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

import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvci.CalSvcI;
import org.bedework.indexer.IndexStats.StatType;

import org.apache.log4j.Logger;

import java.util.Collection;

/** Run to index entities.
 *
 * @author Mike Douglass
 *
 */
public class EntityProcessor extends Crawler {
  private Collection<String> entityNames;
  private String path;

  private int maxErrors = 10;
  private int errors;

  /** Index a bunch of entities given the names.
   * @param status
   * @param name
   * @param adminAccount
   * @param principal - the principal we are processing or null.
   * @param entityDelay delay in millisecs between entities (unused)
   * @param path for collection
   * @param entityNames
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  public EntityProcessor(final CrawlStatus status,
                         final String name,
                         final String adminAccount,
                         final String principal,
                         final long entityDelay,
                         final String path,
                         final Collection<String> entityNames,
                         final String indexRootPath) throws CalFacadeException {
    super(status, name, adminAccount,
          principal, 0, entityDelay, null, indexRootPath);
    this.path = path;
    this.entityNames = entityNames;
  }

  /* (non-Javadoc)
   * @see java.lang.Thread#run()
   */
  @Override
  public void process() throws CalFacadeException {
    try {
      RecurringRetrievalMode rrm = new RecurringRetrievalMode(Rmode.overrides);

      CalSvcI svci = null;
      BwIndexer indexer = BwIndexerFactory.getIndexer(principal,
                                                      true, getSyspars(),
                                                      indexRootPath,
                                                      null);

      try {
        svci = getSvci();

        for (String name: entityNames) {
          try {
            if (debug) {
              debugMsg("Indexing collection " + path +
                       " entity " + name);
            }

            status.stats.inc(StatType.entities);
            EventInfo ent = svci.getEventsHandler().get(path, name, rrm);

            if (ent == null) {
              status.stats.inc(StatType.unreachableEntities);
              continue;
            }
            indexer.indexEntity(ent);
          } catch (Throwable t) {
            Logger.getLogger(this.getClass()).error(this, t);

            errors++;

            if (errors > maxErrors) {
              error("Too many errors (" + errors + "): terminating");
              break;
            }
          }
        }
      } finally {
        if (svci != null) {
          close();
        }
      }
    } catch (Throwable t) {
      Logger.getLogger(this.getClass()).error(t);
    }
  }
}
