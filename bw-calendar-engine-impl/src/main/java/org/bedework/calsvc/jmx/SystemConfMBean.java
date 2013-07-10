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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.configs.SystemProperties;

import edu.rpi.cmt.jmx.ConfBaseMBean;
import edu.rpi.cmt.jmx.MBeanInfo;

/** Run the timezones service
 *
 * @author douglm
 */
public interface SystemConfMBean extends ConfBaseMBean, SystemProperties {
  /* ========================================================================
   * Attributes
   * ======================================================================== */
  /** Enable/disable db statistics
   *
   * @param enable       boolean true to turn on db statistics collection
   */
  void setDbStatsEnabled(@MBeanInfo("true for enable.")
                         boolean enable);

  /**
   *
   * @return boolean true if statistics collection enabled
   */
  @MBeanInfo("Enable/disable db stats collection.")
  boolean getDbStatsEnabled();

  /**
   * @return SystemProperties object
   */
  @MBeanInfo("Get the configuration object.")
  SystemProperties getConfig();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /** Get the current stats
   *
   * @return BwStats object
   */
  @MBeanInfo("Display the stats.")
  BwStats getStats();

  /** Dump db statistics
   *
   */
  @MBeanInfo("Dump the stats in the log.")
  void dumpDbStats();

  /* * Get the current stats
   *
   * @return List of Stat
   * /
  @MBeanInfo("Provide some statistics.")
  List<Stat> getStats();*/

  /** (Re)load the configuration
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configuration")
  String loadConfig();
}
