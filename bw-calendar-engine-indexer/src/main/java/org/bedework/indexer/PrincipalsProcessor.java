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

import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calsvci.CalSvcI;
import org.bedework.util.misc.Util;

import java.util.List;
import java.util.Map;

/** This implementation crawls the user subtree indexing user entries.
 *
 * @author douglm
 *
 */
public class PrincipalsProcessor extends Crawler {
  private final String docType;

  /**
   * This is the constructor for handling the non-public principal
   * indexing.
   * <p>
   * Run a thread which reads the children of the root directory. Each
   * child is a home directory. We read a batch of these at a time and
   * then start up maxThreads processes to handle the user data.
   * <p>
   * When we've finished the entire tree we stop.
   *
   * @param status       of the process
   * @param name         for the thread
   * @param adminAccount to use
   * @param batchDelay   between batches
   * @param entityDelay  betwen entities
   * @param skipPaths    - paths to skip
   * @param indexNames   - where we build the index
   * @param docType      - if non-null only index this type. Cannot be
   *                     collection or events types
   */
  public PrincipalsProcessor(final CrawlStatus status,
                             final String name,
                             final String adminAccount,
                             final long batchDelay,
                             final long entityDelay,
                             final List<String> skipPaths,
                             final Map<String, String> indexNames,
                             final String docType) {
    super(status,
          name, adminAccount, null, batchDelay, entityDelay,
          skipPaths, indexNames);

    this.docType = docType;
  }

  @Override
  public void process() {
    /* We get batched lists of user principals and then start up indexer threads
     * to handle each principal
     */

    Refs refs = null;

    /* First index the user collection root */
    try (final BwSvc bw = getBw()) {
      final CalSvcI svc = bw.getSvci();

      if (docType == null) {
        indexCollection(svc, Util.buildPath(
                BasicSystemProperties.colPathEndsWithSlash,
                "/",
                BasicSystemProperties.userCalendarRoot));
      }
    } catch (final Throwable t) {
      error(t);
      return;
    }

    try {
      for (; ; ) {
        refs = getPrincipalHrefs(refs);

        if (refs == null) {
          if (debug()) {
            debug("Principals: No more");
          }

          break;
        }

        /* Here I should start up maxthread processors then as each one terminates
         * start up a new one until the batch is done
         */

        /* At the moment we can get a weird mix of names under /user.
         * If it's a normal account we get something like /user/fred so that home
         * would be "fred".
         *
         * We also get something like /user/principals/groups/mygroup so if we
         * get "principals" as the home then we need to start a principal processor
         * to handle those. Note we may have /user/principals/locations etc.
         *
         * We also need to check the options to determine if "principals" really is
         * the value to look for.
         */

        for (final String href : refs.refs) {
          if (debug()) {
            debug("Principals: Processing principal " + href);
          }

          getStatus().currentStatus = "Principals: Processing principal " + href;

          final ProcessorBase p =
                  new PrincipalProcessor(status,
                                         name + " " + principal,
                                         adminAccount,
                                         href,
                                         batchDelay,
                                         entityDelay,
                                         getSkipPaths(),
                                         indexNames,
                                         docType);

          /* This call should hang waiting for an available process */
          final IndexerThread it = getPrincipalThread(p);

          if (debug()) {
            debug("Principals: Got thread for " + href);
          }

          it.start();
        }
      }
    } catch (final Throwable t) {
      error(t);
    }
  }
}
