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
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/** These are the system properties that the server needs to know about, either
 * because it needs to apply these limits or just to report them to clients.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "index-properties",
          type = "org.bedework.calfacade.configs.IndexProperties")
public class IndexPropertiesImpl
        extends ConfigBase<IndexPropertiesImpl>
        implements IndexProperties {
  private String indexerURL;

  private boolean embeddedIndexer;

  private boolean httpEnabled;

  private String clusterName;

  private String nodeName;

  private String dataDir;

  private String indexerConfig;

  private String solrCoreAdmin;

  private String publicIndexName;

  private String userIndexName;

  private String account;

  private boolean discardMessages;

  private int maxEntityThreads;

  private int maxPrincipalThreads;

  private boolean indexPublic;

  private boolean indexUser;

  private List<String> skipPaths = new ArrayList<>();

  @Override
  public void setIndexerURL(final String val) {
    indexerURL = val;
  }

  @Override
  public String getIndexerURL() {
    return indexerURL;
  }

  @Override
  public void setEmbeddedIndexer(final boolean val) {
    embeddedIndexer = val;
  }

  @Override
  public boolean getEmbeddedIndexer() {
    return embeddedIndexer;
  }

  @Override
  public void setHttpEnabled(final boolean val) {
    httpEnabled = val;
  }

  @Override
  public boolean getHttpEnabled() {
    return httpEnabled;
  }

  @Override
  public void setClusterName(final String val) {
    clusterName = val;
  }

  @Override
  public String getClusterName() {
    return clusterName;
  }

  @Override
  public void setNodeName(final String val) {
    nodeName = val;
  }

  @Override
  public String getNodeName() {
    return nodeName;
  }

  @Override
  public void setDataDir(final String val) {
    dataDir = val;
  }

  @Override
  public String getDataDir() {
    return dataDir;
  }

  @Override
  public void setIndexerConfig(final String val) {
    indexerConfig = val;
  }

  @Override
  public String getIndexerConfig() {
    return indexerConfig;
  }

  @Override
  public void setSolrCoreAdmin(final String val) {
    solrCoreAdmin = val;
  }

  @Override
  public String getSolrCoreAdmin() {
    return solrCoreAdmin;
  }

  @Override
  public void setPublicIndexName(final String val) {
    publicIndexName = val;
  }

  @Override
  public String getPublicIndexName() {
    return publicIndexName;
  }

  @Override
  public void setUserIndexName(final String val) {
    userIndexName = val;
  }

  @Override
  public String getUserIndexName() {
    return userIndexName;
  }

  @Override
  public void setAccount(final String val) {
    account = val;
  }

  @Override
  public String getAccount() {
    return account;
  }

  @Override
  public void setMaxEntityThreads(final int val) {
    maxEntityThreads = val;
  }

  @Override
  public int getMaxEntityThreads() {
    return maxEntityThreads;
  }

  @Override
  public void setMaxPrincipalThreads(final int val) {
    maxPrincipalThreads = val;
  }

  @Override
  public int getMaxPrincipalThreads() {
    return maxPrincipalThreads;
  }

  @Override
  public void setIndexPublic(final boolean val) {
    indexPublic = val;
  }

  @Override
  public boolean getIndexPublic() {
    return indexPublic;
  }

  @Override
  public void setIndexUsers(final boolean val) {
    indexUser = val;
  }

  @Override
  public boolean getIndexUsers() {
    return indexUser;
  }

  @Override
  public void setDiscardMessages(final boolean val) {
    discardMessages = val;
  }

  @Override
  public boolean getDiscardMessages() {
    return discardMessages;
  }

  @Override
  public void setSkipPaths(final String val) {
    String[] paths = val.split(":");

    skipPaths.clear();

    for (String path:paths) {
      skipPaths.add(path);
    }
  }

  @Override
  @ConfInfo(dontSave = true)
  public String getSkipPaths() {
    String delim = "";
    StringBuilder sb = new StringBuilder();

    for (String s: skipPaths) {
      sb.append(delim);
      sb.append(s);

      delim = ":";
    }

    return sb.toString();
  }

  @Override
  public void setSkipPathsList(List<String> val) {
    skipPaths = val;
  }

  @Override
  public List<String> getSkipPathsList() {
    return skipPaths;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("indexerURL", getIndexerURL());
    ts.append("embeddedIndexer", getEmbeddedIndexer());
    ts.append("indexerConfig", getIndexerConfig());
    ts.append("solrCoreAdmin", getSolrCoreAdmin());
    ts.append("publicIndexName", getPublicIndexName());
    ts.append("userIndexName", getUserIndexName());

    ts.newLine();
    ts.append("account", getAccount());
    ts.append("maxEntityThreads", getMaxEntityThreads());
    ts.append("maxPrincipalThreads", getMaxPrincipalThreads());
    ts.append("indexPublic", getIndexPublic());
    ts.append("indexUsers", getIndexUsers());
    ts.append("discardMessages", getDiscardMessages());

    ts.append("skipPaths", getSkipPaths());

    return ts.toString();
  }

  @Override
  public IndexProperties cloneIt() {
    IndexPropertiesImpl clone = new IndexPropertiesImpl();

    clone.setName(getName());

    clone.setIndexerURL(getIndexerURL());
    clone.setEmbeddedIndexer(getEmbeddedIndexer());
    clone.setIndexerConfig(getIndexerConfig());
    clone.setSolrCoreAdmin(getSolrCoreAdmin());
    clone.setPublicIndexName(getPublicIndexName());
    clone.setUserIndexName(getUserIndexName());

    clone.setAccount(getAccount());
    clone.setMaxEntityThreads(getMaxEntityThreads());
    clone.setMaxPrincipalThreads(getMaxPrincipalThreads());
    clone.setIndexPublic(getIndexPublic());
    clone.setIndexUsers(getIndexUsers());
    clone.setDiscardMessages(getDiscardMessages());
    clone.setSkipPathsList(getSkipPathsList());

    return clone;
  }
}
