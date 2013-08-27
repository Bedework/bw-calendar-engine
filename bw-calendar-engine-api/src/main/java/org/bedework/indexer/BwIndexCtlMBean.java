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

import org.bedework.calfacade.configs.IndexProperties;

import edu.rpi.cmt.jmx.ConfBaseMBean;
import edu.rpi.cmt.jmx.MBeanInfo;

import java.util.List;

/**
 * @author douglm
 *
 */
public interface BwIndexCtlMBean extends ConfBaseMBean, IndexProperties {
  /**
   * @return number of messages processed
   */
  public long getMessageCount();

  /**
   * @return count processed
   */
  public long getCollectionsUpdated();

  /**
   * @return count processed
   */
  public long getCollectionsDeleted();

  /**
   * @return count processed
   */
  public long getEntitiesUpdated();

  /**
   * @return count processed
   */
  public long getEntitiesDeleted();

  /** Get the current status of the reindexing process
   *
   * @return messages as a list
   */
  public List<String> rebuildStatus();

  /** Crawl the data and create indexes - listener should have been stopped.
   *
   * @return message
   */
  public String rebuildIndex();

  /**
   * @return list of indexes maintained by indexer.
   */
  public String listIndexes();

  /* *
   * @return list of purged indexes.
   * @throws Throwable
   * /
  public String purgeIndexes(); NOT YET */

  /** Start the indexer
   *
   */
  public void start();

  /** Stop the indexer
   *
   */
  public void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  public boolean isStarted();

  /** (Re)load the configuration
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configuration")
  String loadConfig();
}
