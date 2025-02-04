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
   */
  void start();

  /** Wait for any processes to stop.
   *
   * @throws RuntimeException on fatal error
   */
  void join();

  /** Stop processing entries. A call to start will start from the beginning. A
   * call to restart will restart with the last entry being processed. A call to
   * restartNext will drop the last entry and restart.
   *
   */
  void stop();

  /** Start from where we left off
   *
   */
  void restart();

  /** Start with the next entry on the list
   */
  void restartNext();

  /** Do whatever this processor is supposed to do. usually called from a
   * Thread object.
   *
   */
  void process();
}
