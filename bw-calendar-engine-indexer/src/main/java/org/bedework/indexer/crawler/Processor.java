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

/** This class represents something which crawls a part of the bedework storage.
 * A user process will crawl /user and a public crawler will crawl /public.
 *
 * <p>Subclasses of each may crawl other subtrees as we develop them.
 *
 * <p>Processors maintain a list of entries to be processed which they replenish
 * by getting a new batch of paths from the system.
 *
 * <p>The processor will either query the notifications queue directly to
 * determine if it needs to accelerate processing of a subtree or it will be
 * handed an entry or a list.
 *
 * @author douglm
 *
 */
public interface Processor {
  /** Start crawling
   *
   * @param rootPath  sub-tree root path
   * @throws CalFacadeException
   */
  public void start(String rootPath) throws CalFacadeException;

  /** Wait for any processes to stop.
   *
   * @throws CalFacadeException
   */
  public void join() throws CalFacadeException;

  /** Stop processing entries. A call to start will start from the beginning. A
   * call to restart will restart with the last entry being processed. A call to
   * restartNext will drop the last entry and restart.
   *
   * @throws CalFacadeException
   */
  public void stop() throws CalFacadeException;

  /** Start from where we left off
   *
   * @throws CalFacadeException
   */
  public void restart() throws CalFacadeException;

  /** Start with the next entry on the list
   * @throws CalFacadeException
   */
  public void restartNext() throws CalFacadeException;

  /** Do whatever this processor is supposed to do. usually called from a
   * Thread object.
   *
   * @param rootPath  sub-tree root path
   * @throws CalFacadeException
   */
  public void process(String rootPath) throws CalFacadeException;

  /**
   * @return path we are currently processing.
   * @throws CalFacadeException
   */
  public String getCurrentPath() throws CalFacadeException;
}
