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

import java.util.List;

/** Run the dump/restore and/or the schema initialization as an mbean
 *
 * @author douglm
 */
public interface BwDumpRestoreMBean {
  /* ========================================================================
   * Attributes
   * ======================================================================== */

  /** Name apparently must be the same as the name attribute in the
   * jboss service definition
   *
   * @return Name
   */
  String getName();

  /** Account we run under
   *
   * @param val
   */
  void setAccount(String val);

  /**
   * @return String account we use
   */
  String getAccount();

  /**
   * @return String application namee
   */
  String getAppname();

  /** Statement delimiter
   *
   * @param val
   */
  void setDelimiter(String val);

  /**
   * @return Statement delimiter
   */
  String getDelimiter();

  /** Export to database?
   *
   * @param val
   */
  void setExport(boolean val);

  /**
   * @return true for export
   */
  boolean getExport();

  /** Output file name - full path
   *
   * @param val
   */
  void setSchemaOutFile(String val);

  /**
   * @return Output file name - full path
   */
  String getSchemaOutFile();

  /** XML data input file name - full path. Used for data restore
   *
   * @param val
   */
  void setDataIn(String val);

  /**
   * @return XML data input file name - full path
   */
  String getDataIn();

  /** XML data output directory name - full path. Used for data restore
   *
   * @param val
   */
  void setDataOut(String val);

  /**
   * @return XML data output directory name - full path
   */
  String getDataOut();

  /** XML data output file prefix - for data dump
   *
   * @param val
   */
  void setDataOutPrefix(String val);

  /**
   * @return XML data output file prefix - for data dump
   */
  String getDataOutPrefix();

  /**
   * @param val
   */
  void setTimezonesUri(String val);

  /**
   * @return uri for tz server
   */
  String getTimezonesUri();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /** Return true if the schema appears to be valid
   *
   * @return true if it looks ok to us
   */
  boolean testSchemaValid();

  /** Create or dump new schema. If export and drop set will try to drop tables.
   * Export and create will create a schema in the db and export, drop, create
   * will drop tables, and try to create  anew schema.
   *
   * The export, create and drop flags will all be reset to false after this,
   * whatever the result. This avoids accidental damage to the db.
   *
   * @return Completion message
   */
  String schema();

  /** Starts a restore of the data from the DataIn path. Will not restore if
   * there appears to be any data already in the db.
   *
   * @return Completion message
   */
  String restoreData();

  /** Returns status of the restore.
   *
   * @return Completion messages and stats
   */
  List<String> restoreStatus();

  /** Scan the system data looking for external subscriptions. When complete
   * the data can be used by checkExternalSubs
   *
   * @return Completion messages and stats
   */
  String fetchExternalSubs();

  /** Check external subscriptions discovered during the dump or restore. This will
   * restore the subscription if necessary.
   *
   * @return Completion message
   */
  String checkExternalSubs();

  /** Returns status of the external subscriptions check.
   *
   * @return Completion messages and stats
   */
  List<String> checkSubsStatus();

  /** Starts a dump of the data to a file in the DataOut directory.
   *
   * @return Completion message
   */
  String dumpData();

  /** Returns status of the dump.
   *
   * @return Completion messages and stats
   */
  List<String> dumpStatus();

  /** Try to drop all the tables. May get errors for a partial db or for an updated
   * db.
   *
   * @return Completion message
   */
  String dropTables();

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  /** Lifecycle
   *
   */
  void create();

  /** Lifecycle
   *
   */
  void start();

  /** Lifecycle
   *
   */
  void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  boolean isStarted();

  /** Lifecycle
   *
   */
  void destroy();
}
