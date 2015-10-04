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
package org.bedework.dumprestore;

import org.bedework.calfacade.configs.DumpRestoreProperties;
import org.bedework.util.jmx.ConfBaseMBean;
import org.bedework.util.jmx.MBeanInfo;

import java.util.List;

/** Run the dump/restore and/or the schema initialization as an mbean
 *
 * @author douglm
 */
public interface BwDumpRestoreMBean extends ConfBaseMBean,
        DumpRestoreProperties {
  /**
   * @param val true to enable restores
   */
  void setAllowRestore(boolean val);

  /**
   * @return  true to enable restores
   */
  @MBeanInfo("Set true to enable restores")
  boolean getAllowRestore();

  /**
   * @param val true to fix aliases only
   */
  void setFixAliases(boolean val);

  /**
   * @return  true to list aliases only
   */
  @MBeanInfo("Set true to fix aliases - false to list only")
  boolean getFixAliases();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /** Starts a restore of the data from the DataIn path. Will not restore if
   * there appears to be any data already in the db.
   *
   * @return Completion message
   */
  @MBeanInfo("Restores the data from the DataIn path")
  String restoreData();

  /** Returns status of the restore.
   *
   * @return Completion messages and stats
   */
  @MBeanInfo("Show state of current restore")
  List<String> restoreStatus();

  /** Load alias and external subscription info from a file.
   *
   * @param path  to file.
   * @return Completion messages and stats
   */
  @MBeanInfo("Load alias and external subscription info from a file")
  String loadAliasInfo(@MBeanInfo("Path to file")String path);

  /** Scan the system data looking for external subscriptions. When complete
   * the data can be used by checkExternalSubs
   *
   * @return Completion messages and stats
   */
  @MBeanInfo("Fetch external subscriptions for a check")
  String fetchExternalSubs();

  /** Check external subscriptions discovered during the dump or restore. This will
   * restore the subscription if necessary.
   *
   * @return Completion message
   */
  @MBeanInfo("check external subscriptions")
  String checkExternalSubs();

  /** Returns status of the external subscriptions check.
   *
   * @return Completion messages and stats
   */
  @MBeanInfo("Show status of external subscriptions check")
  List<String> checkSubsStatus();

  /** Check aliases discovered during the dump or restore. This will
   * attempt to fix sharing of fixAliases is true.
   *
   * @return Completion message
   */
  @MBeanInfo("check aliases")
  String checkAliases();

  /** Returns status of the alias check.
   *
   * @return Completion messages and stats
   */
  @MBeanInfo("Show status of alias check")
  List<String> checkAliasStatus();

  /** Starts a dump of the data to a file in the DataOut directory.
   *
   * @return Completion message
   */
  @MBeanInfo("Dumps the data to a file in the DataOut directory")
  String dumpData();

  /** Returns status of the dump.
   *
   * @return Completion messages and stats
   */
  @MBeanInfo("Show status of current data dump")
  List<String> dumpStatus();

  /** Delete all traces of a user.
   *
   * @param account of user.
   * @return Completion messages and stats
   */
  @MBeanInfo("Delete all traces of a user - WARNING this is unrecoverable")
  String deleteUser(@MBeanInfo("account")String account);
}
