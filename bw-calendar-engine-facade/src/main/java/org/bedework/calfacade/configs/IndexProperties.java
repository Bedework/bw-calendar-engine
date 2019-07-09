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
package org.bedework.calfacade.configs;

import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.MBeanInfo;

import java.util.List;

/** These are the properties that the indexer needs to know about.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "index-properties")
public interface IndexProperties {
  /** Set the indexer url
   *
   * @param val
   */
  void setIndexerURL(String val);

  /** Get the indexer url
   *
   * @return url of server
   */
  @MBeanInfo("Non-embedded indexer url")
  String getIndexerURL();

  /** Set the cluster name
   *
   * @param val
   */
  void setClusterName(String val);

  /** Get the cluster name
   *
   * @return name
   */
  @MBeanInfo("cluster name")
  String getClusterName();

  /** Set the node name
   *
   * @param val
   */
  void setNodeName(String val);

  /** Get the node name
   *
   * @return name
   */
  @MBeanInfo("node name")
  String getNodeName();

  /** Set the data directory for embedded
   *
   * @param val
   */
  void setDataDir(String val);

  /** Get the data directory for embedded
   *
   * @return name
   */
  @MBeanInfo("data directory for embedded")
  String getDataDir();

  /** Set the indexer config location
   *
   * @param val
   */
  void setIndexerConfig(String val);

  /** Get the indexer config location
   *
   * @return location
   */
  @MBeanInfo("indexer config location")
  String getIndexerConfig();

  /** Account we run under
   *
   * @param val
   */
  void setAccount(String val);

  /**
   * @return String account we use
   */
  @MBeanInfo("account indexer runs as")
  String getAccount();

  /**
   * @param val thread limit
   */
  void setMaxEntityThreads(final int val);

  /**
   * @return thread limit
   */
  @MBeanInfo("max number threads for entity reindexing")
  int getMaxEntityThreads();

  /**
   * @param val thread limit
   */
  void setMaxPrincipalThreads(final int val);

  /**
   * @return thread limit
   */
  @MBeanInfo("max number threads for principal reindexing")
  int getMaxPrincipalThreads();

  /** True if we do public
   *
   * @param val
   */
  void setIndexPublic(final boolean val);

  /**
   * @return true if we do public
   */
  @MBeanInfo("true if we reindex public information")
  boolean getIndexPublic();

  /** True if we do users
   *
   * @param val
   */
  void setIndexUsers(final boolean val);

  /**
   * @return true if we do users
   */
  @MBeanInfo("true if we reindex user information")
  boolean getIndexUsers();

  /** True if we just discard messages.
   *
   * @param val
   */
  void setDiscardMessages(final boolean val);

  /**
   * @return true if we just discard messages
   */
  @MBeanInfo("true if we discard indexer messages - false disablesindexing")
  boolean getDiscardMessages();

  /** Paths to skip - ":" separated
   *
   * @param val
   */
  void setSkipPaths(String val);

  /**
   * @return Paths to skip - ":" separated
   */
  @MBeanInfo("Paths to skip - \":\" separated")
  String getSkipPaths();

  /** Paths to skip as a list
   *
   * @param val
   */
  void setSkipPathsList(List<String> val);

  /**
   * @return Paths to skip as a list
   */
  @MBeanInfo("Paths to skip - \":\" separated")
  @ConfInfo(dontSave = true)
  List<String> getSkipPathsList();

  /**
   * @return copy of this
   */
  IndexProperties cloneIt();
}
