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
package org.bedework.indexer.crawler;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.indexer.IndexStats.StatType;

import java.util.Collection;
import java.util.List;

/** This implementation crawls the user subtree indexing user entries.
 *
 * @author douglm
 *
 */
public class UserProcessor extends Crawler {
  /** This is the constructor for handling the root of the user tree.
   *
   * Run a thread which reads the children of the root directory. Each child
   * is a home directory. We read a batch of these at a time and then start
   * up maxThreads processes to handle the user data.
   *
   * When we've finished the entire tree we stop.
   *
   * @param status
   * @param name
   * @param adminAccount
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @param tgroup
   * @param entityThreads - number of threads this process can use
   * @throws CalFacadeException
   */
  public UserProcessor(final CrawlStatus status,
                       final String name,
                       final String adminAccount,
                       final long batchDelay,
                       final long entityDelay,
                       final List<String> skipPaths,
                       final String indexRootPath,
                       final ThreadGroup tgroup,
                       final int entityThreads) throws CalFacadeException {
    super(status,
          name, adminAccount, false, null, batchDelay, entityDelay,
          skipPaths, indexRootPath,
          tgroup, entityThreads);
  }

  @Override
  public ProcessorBase getProcessorObject(final int index) throws CalFacadeException {
    ProcessorBase p = new UserProcessor(name + " thread " + index,
                                        adminAccount,
                                        principal,
                                        batchDelay,
                                        entityDelay,
                                        getSkipPaths(), indexRootPath);

    p.setStatus(getStatus());
    p.setThreadPool(getThreadPool());

    return p;
  }

  /** Constructor for an entity thread processor. These handle the entities
   * found within a collection.
   *
   * @param name
   * @param adminAccount
   * @param principal - the principal we are processing or null.
   * @param batchDelay
   * @param entityDelay
   * @param skipPaths - paths to skip
   * @param indexRootPath - where we build the index
   * @throws CalFacadeException
   */
  private UserProcessor(final String name,
                        final String adminAccount,
                        final String principal,
                        final long batchDelay,
                        final long entityDelay,
                        final List<String> skipPaths,
                        final String indexRootPath) throws CalFacadeException {
    super(name, adminAccount, false,
          principal, batchDelay, entityDelay, skipPaths, indexRootPath);
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.crawler.Processor#process(java.lang.String)
   */
  @Override
  public void process(final String rootPath) throws CalFacadeException {
    if (principal != null) {
      indexDir(rootPath);
      return;
    }

    int batchIndex = 0;

    for (;;) {
      Collection<String> homes = getChildCollections(rootPath, batchIndex);

      if (homes == null) {
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

      for (String home: homes) {
        status.currentStatus = "User: Processing home " + home;

        String[] elements = home.split("/");

        String homeName = elements[elements.length - 1];

        if ("principals".equals(homeName)) {
          processPrincipals();
        } else {
          String principal = "/principals/users/" + homeName;
          ProcessorBase p = new UserProcessor(name + " " + principal,
                                              adminAccount,
                                              principal,
                                              batchDelay,
                                              entityDelay,
                                              getSkipPaths(), indexRootPath);

          p.setStatus(getStatus());
          p.setThreadPool(getThreadPool());

          getStatus().stats.inc(StatType.users);

          p.process(home);
        }
      }

      batchIndex += homes.size();
    }
  }

  void processPrincipals() {

  }

  void indexDir(final String rootPath) throws CalFacadeException {
    if (debug) {
      debugMsg("Index the directory " + principal);
    }

    indexCollection(rootPath);
  }
}
