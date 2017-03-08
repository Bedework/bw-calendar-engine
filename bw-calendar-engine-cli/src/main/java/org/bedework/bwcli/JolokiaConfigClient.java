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
package org.bedework.bwcli;

import org.bedework.util.jolokia.JolokiaClient;

import java.util.List;

/**
 * User: mike Date: 12/3/15 Time: 00:32
 */
public class JolokiaConfigClient extends JolokiaClient {
  public static final String bwcoreMbean =
          "org.bedework.bwengine.core:service=DbConf";

  public static final String bwdumpRestoreMbean =
          "org.bedework.bwengine:service=dumprestore";

  public static final String cmdutilMbean =
          "org.bedework.bwengine:service=cmdutil";

  public static final String indexMbean =
          "org.bedework.bwengine:service=indexing";

  /**
   *
   * @param url Usually something like "http://localhost:8080/hawtio/jolokia"
   */
  public JolokiaConfigClient(final String url) {
    super(url);
  }

  public void setCmdutilUser(final String account) throws Throwable {
    execute(cmdutilMbean, "exec", "user " + account);
  }

  public List<String> coreSchema() throws Throwable {
    writeVal(bwcoreMbean, "Export", "true");

    execute(bwcoreMbean, "schema");

    waitCompletion(bwcoreMbean);

    return execStringList(bwcoreMbean, "schemaStatus");
  }

  public String listIndexes() throws Throwable {
    return execString(indexMbean, "listIndexes");
  }

  public String purgeIndexes() throws Throwable {
    return execString(indexMbean, "purgeIndexes");
  }

  public String rebuildIndexes() throws Throwable {
    return execString(indexMbean, "rebuildIndex");
  }

  public List<String> rebuildIdxStatus() throws Throwable {
    return execStringList(indexMbean, "rebuildStatus");
  }

  public List<String> restoreCalData(final String path) throws Throwable {
    if (path != null) {
      writeVal(bwdumpRestoreMbean, "DataIn", path);
    }

    writeVal(bwdumpRestoreMbean, "AllowRestore", "true");

    execute(bwdumpRestoreMbean, "restoreData");

    waitCompletion(bwdumpRestoreMbean);

    return execStringList(bwdumpRestoreMbean, "restoreStatus");
  }
}
