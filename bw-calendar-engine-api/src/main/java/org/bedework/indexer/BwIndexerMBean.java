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

import java.util.List;

/**
 * @author douglm
 *
 */
public interface BwIndexerMBean {
  /** Account we run under
   *
   * @param val
   */
  public void setAccount(String val);

  /**
   * @return String account we use
   */
  public String getAccount();

  /**
   * @param val thread limit
   */
  public void setMaxPublicThreads(final int val);

  /**
   * @return thread limit
   */
  public int getMaxPublicThreads();

  /**
   * @param val thread limit
   */
  public void setMaxUserThreads(final int val);

  /**
   * @return thread limit
   */
  public int getMaxUserThreads();

  /** True if we do public
   *
   * @param val
   */
  public void setIndexPublic(final boolean val);

  /**
   * @return true if we do public
   */
  public boolean getIndexPublic();

  /** True if we do users
   *
   * @param val
   */
  public void setIndexUsers(final boolean val);

  /**
   * @return true if we do users
   */
  public boolean getIndexUsers();

  /** True if we just discard messages.
   *
   * @param val
   */
  public void setDiscardMessages(final boolean val);

  /**
   * @return true if we just discard messages
   */
  public boolean getDiscardMessages();

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

  /** Paths to skip - ":" separated
   *
   * @param val
   */
  public void setSkipPaths(String val);

  /**
   * @return Paths to skip - ":" separated
   */
  public String getSkipPaths();

  /** Get the current status of the reindexing process
   *
   * @return messages as a list
   */
  public List<String> rebuildStatus();

  /** Name apparently must be the same as the name attribute in the
   * jboss service definition
   *
   * @return Name
   */
  public String getName();

  /** Crawl the data and create indexes - listener should have been stopped.
   *
   * @return message
   */
  public String rebuildIndex();

  /** Lifecycle
   *
   */
  public void start();

  /** Lifecycle
   *
   */
  public void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  public boolean isStarted();

}
