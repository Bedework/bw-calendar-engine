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
  public String getName();

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
   * @return String application namee
   */
  public String getAppname();

  /** Statement delimiter
   *
   * @param val
   */
  public void setDelimiter(String val);

  /**
   * @return Statement delimiter
   */
  public String getDelimiter();

  /** Export to database?
   *
   * @param val
   */
  public void setExport(boolean val);

  /**
   * @return true for export
   */
  public boolean getExport();

  /** Output file name - full path
   *
   * @param val
   */
  public void setSchemaOutFile(String val);

  /**
   * @return Output file name - full path
   */
  public String getSchemaOutFile();

  /** XML data input file name - full path. Used for data restore
   *
   * @param val
   */
  public void setDataIn(String val);

  /**
   * @return XML data input file name - full path
   */
  public String getDataIn();

  /** XML data output directory name - full path. Used for data restore
   *
   * @param val
   */
  public void setDataOut(String val);

  /**
   * @return XML data output directory name - full path
   */
  public String getDataOut();

  /** XML data output file prefix - for data dump
   *
   * @param val
   */
  public void setDataOutPrefix(String val);

  /**
   * @return XML data output file prefix - for data dump
   */
  public String getDataOutPrefix();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /** Return true if the schema appears to be valid
   *
   * @return true if it looks ok to us
   */
  public boolean testSchemaValid();

  /** Create or dump new schema. If export and drop set will try to drop tables.
   * Export and create will create a schema in the db and export, drop, create
   * will drop tables, and try to create  anew schema.
   *
   * The export, create and drop flags will all be reset to false after this,
   * whatever the result. This avoids accidental damage to the db.
   *
   * @return Completion message
   */
  public String schema();

  /** Starts a restore of the data from the DataIn path. Will not restore if
   * there appears to be any data already in the db.
   *
   * @return Completion message
   */
  public String restoreData();

  /** Returns status of the restore.
   *
   * @return Completion messages and stats
   */
  public List<String> restoreStatus();

  /** Scan the system data looking for external subscriptions. When complete
   * the data can be used by checkExternalSubs
   *
   * @return Completion messages and stats
   */
  public String fetchExternalSubs();

  /** Check external subscriptions discovered during the dump or restore. This will
   * restore the subscription if necessary.
   *
   * @return Completion message
   */
  public String checkExternalSubs();

  /** Returns status of the external subscriptions check.
   *
   * @return Completion messages and stats
   */
  public List<String> checkSubsStatus();

  /** Starts a dump of the data to a file in the DataOut directory.
   *
   * @return Completion message
   */
  public String dumpData();

  /** Returns status of the dump.
   *
   * @return Completion messages and stats
   */
  public List<String> dumpStatus();

  /** Try to drop all the tables. May get errors for a partial db or for an updated
   * db.
   *
   * @return Completion message
   */
  public String dropTables();

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  /** Lifecycle
   *
   */
  public void create();

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

  /** Lifecycle
   *
   */
  public void destroy();
}
