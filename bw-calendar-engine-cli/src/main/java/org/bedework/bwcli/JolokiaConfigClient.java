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

import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jolokia.JolokiaClient;

import java.util.List;

import static org.bedework.calfacade.configs.Configurations.cmdutilMbean;
import static org.bedework.calfacade.configs.Configurations.dumpRestoreMbean;
import static org.bedework.calfacade.configs.Configurations.indexMbean;
import static org.bedework.calfacade.configs.Configurations.systemMbean;

/**
 * User: mike Date: 12/3/15 Time: 00:32
 */
public class JolokiaConfigClient extends JolokiaClient {
  public final static String syncEngineMbean =
          "org.bedework.synch:service=SynchConf";
  /**
   *
   * @param url Usually something like "http://localhost:8080/hawtio/jolokia"
   */
  public JolokiaConfigClient(final String url,
                             final String id, 
                             final String pw) {
    super(url, id, pw);
  }

  public String setCmdutilUser(final String account) throws Throwable {
    return execCmdutilCmd("user " + account);
  }

  public String execCmdutilCmd(final String cmd) throws Throwable {
    return execString(cmdutilMbean, "exec", cmd);
  }

  public List<String> coreSchema() throws Throwable {
    writeVal(dumpRestoreMbean, "Export", "true");

    execute(dumpRestoreMbean, "schema");

    waitCompletion(dumpRestoreMbean);

    return execStringList(dumpRestoreMbean, "schemaStatus");
  }

  public String listIndexes() throws Throwable {
    return execString(indexMbean, "listIndexes");
  }

  public String purgeIndexes() throws Throwable {
    return execString(indexMbean, "purgeIndexes");
  }

  public String newIndexes() throws Throwable {
    return execString(indexMbean, "newIndexes");
  }

  public List<String> rebuildIndexes() throws Throwable {
    execute(indexMbean, "rebuildIndex");

    String status;
    do {
      status = waitCompletion(indexMbean);
      multiLine(execStringList(indexMbean, "rebuildStatus"));
    } while (status.equals(ConfBase.statusTimedout));

    return execStringList(indexMbean, "rebuildStatus");
  }

  public Object indexStats(final String indexName) throws Throwable {
    return exec(indexMbean, "indexStats", indexName);
  }

  public String reindex(final String indexName) throws Throwable {
    return execString(indexMbean, "reindex", indexName);
  }

  public List<String> rebuildIdxStatus() throws Throwable {
    return execStringList(indexMbean, "rebuildStatus");
  }

  public String makeIdxProd(final String indexName) throws Throwable {
    return execString(indexMbean, "setProdAlias", indexName);
  }

  public List<String> restoreCalData(final String path) throws Throwable {
    if (path != null) {
      writeVal(dumpRestoreMbean, "DataIn", path);
    }

    writeVal(dumpRestoreMbean, "AllowRestore", "true");

    execute(dumpRestoreMbean, "restoreData");

    waitCompletion(dumpRestoreMbean);

    return execStringList(dumpRestoreMbean, "restoreStatus");
  }
  
  /* System properties */

  public void setSystemTzid(final String val) throws Throwable {
    writeVal(systemMbean, "Tzid", val);
  }
  
  public String getSystemTzid() throws Throwable {
    return readString(systemMbean, "Tzid");
  }

  public void setRootUsers(final String val) throws Throwable {
    writeVal(systemMbean, "RootUsers", val);
  }

  public String getRootUsers() throws Throwable {
    return readString(systemMbean, "RootUsers");
  }

  public void setAutoKillMinutes(final Integer val) throws Throwable {
    writeVal(systemMbean, "AutoKillMinutes", val);
  }

  public Integer getAutoKillMinutes() throws Throwable {
    String s = readString(systemMbean, "AutoKillMinutes");
    return new Integer(s);
  }

  /* ----------- sync engine ----------------- */

  public String getSyncAttr(final String attrName) throws Throwable {
    return readString(syncEngineMbean, attrName);
  }

  public void setSyncAttr(final String attrName,
                          final String val) throws Throwable {
    writeVal(syncEngineMbean, attrName, val);
  }

  public List<String> syncSchema() throws Throwable {
    writeVal(syncEngineMbean, "Export", "true");

    execute(syncEngineMbean, "schema");

    waitCompletion(syncEngineMbean);

    return execStringList(syncEngineMbean, "schemaStatus");
  }

  public void syncStart() throws Throwable {
    execute(syncEngineMbean, "start");
  }

  public void syncStop() throws Throwable {
    execute(syncEngineMbean, "stop");
  }

  public void setSyncPrivKeys(final String val) throws Throwable {
    writeVal(syncEngineMbean, "PrivKeys", val);
  }
}
