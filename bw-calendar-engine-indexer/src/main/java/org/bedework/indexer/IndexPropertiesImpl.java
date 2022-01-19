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
import java.util.Collections;
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

  private String indexerToken;

  private String indexerUser;

  private String indexerPw;

  private String clusterName;

  private String nodeName;

  private String keyStore;

  private String keyStorePw;

  private String indexerConfig;

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
  public void setIndexerToken(final String val) {
    indexerToken = val;
  }

  @Override
  public String getIndexerToken() {
    return indexerToken;
  }

  @Override
  public void setIndexerUser(final String val) {
    indexerUser = val;
  }

  @Override
  public String getIndexerUser() {
    return indexerUser;
  }

  @Override
  public void setIndexerPw(final String val) {
    indexerPw = val;
  }

  @Override
  public String getIndexerPw() {
    return indexerPw;
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
  public void setKeyStore(final String val) {
    keyStore = val;
  }

  @Override
  public String getKeyStore() {
    return keyStore;
  }

  @Override
  public void setKeyStorePw(final String val) {
    keyStorePw = val;
  }

  @Override
  public String getKeyStorePw() {
    return keyStorePw;
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
    skipPaths.clear();

    Collections.addAll(skipPaths, val.split(":"));
  }

  @Override
  @ConfInfo(dontSave = true)
  public String getSkipPaths() {
    String delim = "";
    final StringBuilder sb = new StringBuilder();

    for (final String s: skipPaths) {
      sb.append(delim);
      sb.append(s);

      delim = ":";
    }

    return sb.toString();
  }

  @Override
  public void setSkipPathsList(final List<String> val) {
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
    final ToString ts = new ToString(this);

    ts.append("indexerURL", getIndexerURL());
    ts.append("indexerConfig", getIndexerConfig());

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
    final IndexPropertiesImpl clone = new IndexPropertiesImpl();

    clone.setName(getName());

    clone.setIndexerURL(getIndexerURL());
    clone.setIndexerConfig(getIndexerConfig());

    clone.setKeyStore(getKeyStore());

    clone.setAccount(getAccount());
    clone.setMaxEntityThreads(getMaxEntityThreads());
    clone.setMaxPrincipalThreads(getMaxPrincipalThreads());
    clone.setIndexPublic(getIndexPublic());
    clone.setIndexUsers(getIndexUsers());
    clone.setDiscardMessages(getDiscardMessages());
    clone.setSkipPaths(getSkipPaths());

    return clone;
  }
}
