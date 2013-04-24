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

/** Configuration properties for the free busy aggregator clients
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class FBAggConfig extends DbConfig {
  private boolean hour24;

  private int defaultGranularity;

  private int minGranularity;

  private int maxGranularity;

  private int maxDateRange;

  private String logPrefix;

  private String appRoot;

  private String portalPlatform;

  private String refreshAction;

  private int refreshInterval;

  private String calSuite;

  /** Constructor
   *
   */
  public FBAggConfig() {
  }

  /**
   * @param val
   */
  public void setHour24(boolean val) {
    hour24 = val;
  }

  /**
   * @return bool
   */
  public boolean getHour24() {
    return hour24;
  }

  /**
   * @param val
   */
  public void setDefaultGranularity(int val) {
    defaultGranularity = val;
  }

  /**
   * @return int
   */
  public int getDefaultGranularity() {
    return defaultGranularity;
  }

  /**
   * @param val
   */
  public void setMinGranularity(int val) {
    minGranularity = val;
  }

  /**
   * @return int
   */
  public int getMinGranularity() {
    return minGranularity;
  }

  /**
   * @param val
   */
  public void setMaxGranularity(int val) {
    maxGranularity = val;
  }

  /**
   * @return int
   */
  public int getMaxGranularity() {
    return maxGranularity;
  }

  /**
   * @param val
   */
  public void setMaxDateRange(int val) {
    maxDateRange = val;
  }

  /**
   * @return int
   */
  public int getMaxDateRange() {
    return maxDateRange;
  }

  /**
   * @param val
   */
  public void setLogPrefix(String val) {
    logPrefix = val;
  }

  /**
   * @return String
   */
  public String getLogPrefix() {
    return logPrefix;
  }

  /** Where the xslt and resources are based.
   *
   * @param val
   */
  public void setAppRoot(String val) {
    appRoot = val;
  }

  /**
   * @return String
   */
  public String getAppRoot() {
    return appRoot;
  }

  /** Define the name of the portal platform.
   *
   * @param val
   */
  public void setPortalPlatform(String val) {
    portalPlatform = val;
  }

  /**
   * @return String
   */
  public String getPortalPlatform() {
    return portalPlatform;
  }

  /**
   * @param val
   */
  public void setRefreshAction(String val) {
    refreshAction = val;
  }

  /**
   * @return String
   */
  public String getRefreshAction() {
    return refreshAction;
  }

  /**
   * @param val
   */
  public void setRefreshInterval(int val) {
    refreshInterval = val;
  }

  /**
   * @return int
   */
  public int getRefreshInterval() {
    return refreshInterval;
  }

  /**
   * @param val
   */
  public void setCalSuite(String val) {
    calSuite = val;
  }

  /**
   * @return String
   */
  public String getCalSuite() {
    return calSuite;
  }

  /** Copy this object to val.
   *
   * @param val
   */
  public void copyTo(FBAggConfig val) {
    super.copyTo(val);
    val.setHour24(getHour24());
    val.setDefaultGranularity(getDefaultGranularity());
    val.setMinGranularity(getMinGranularity());
    val.setMaxGranularity(getMaxGranularity());
    val.setMaxDateRange(getMaxDateRange());
    val.setLogPrefix(getLogPrefix());
    val.setRefreshAction(getRefreshAction());
    val.setRefreshInterval(getRefreshInterval());
    val.setCalSuite(getCalSuite());
  }

  public Object clone() {
    FBAggConfig conf = new FBAggConfig();

    copyTo(conf);

    return conf;
  }
}

