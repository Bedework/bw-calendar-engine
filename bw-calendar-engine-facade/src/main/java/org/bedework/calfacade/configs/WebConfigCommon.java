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

/** Common configuration properties for the web clients
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class WebConfigCommon extends ConfigCommon {
  /* True if we should auto-create contacts. Some sites may wish to control
   * the creation of contacts to enforce consistency in their use. If this
   * is true we create a contact as we create events. If false the contact
   * must already exist.
   */
  private boolean autoCreateContacts = true;

  /* True if we should auto-create locations. Some sites may wish to control
   * the creation of locations to enforce consistency in their use. If this
   * is true we create a location as we create events. If false the location
   * must already exist.
   */
  private boolean autoCreateLocations = true;

  /* True if we should auto-delete contacts. Some sites may wish to control
   * the deletion of contacts to enforce consistency in their use. If this
   * is true we delete a contact when it becomes unused.
   */
  private boolean autoDeleteContacts = true;

  /* True if we should auto-delete locations. Some sites may wish to control
   * the deletion of locations to enforce consistency in their use. If this
   * is true we delete a location when it becomes unused.
   */
  private boolean autoDeleteLocations = true;

  private boolean hour24;

  private int minIncrement;

  private boolean showYearData;

  private String browserResourceRoot;

  private String appRoot;

  private String refreshAction;

  private int refreshInterval;

  private String calSuite;

  private String submissionRoot;

  /** Constructor
   *
   */
  public WebConfigCommon() {
  }

  /** True if we should auto-create contacts. Some sites may wish to control
   * the creation of contacts to enforce consistency in their use. If this
   * is true we create a contact as we create events. If false the contact
   * must already exist.
   *
   * @param val
   */
  public void setAutoCreateContacts(final boolean val) {
    autoCreateContacts = val;
  }

  /**
   * @return boolean
   */
  public boolean getAutoCreateContacts() {
    return autoCreateContacts;
  }

  /** True if we should auto-create locations. Some sites may wish to control
   * the creation of locations to enforce consistency in their use. If this
   * is true we create a location as we create events. If false the location
   * must already exist.
   *
   * @param val
   */
  public void setAutoCreateLocations(final boolean val) {
    autoCreateLocations = val;
  }

  /**
   * @return boolean
   */
  public boolean getAutoCreateLocations() {
    return autoCreateLocations;
  }

  /** True if we should auto-delete contacts. Some sites may wish to control
   * the deletion of contacts to enforce consistency in their use. If this
   * is true we delete a contact when it becomes unused.
   *
   * @param val
   */
  public void setAutoDeleteContacts(final boolean val) {
    autoDeleteContacts = val;
  }

  /**
   * @return boolean
   */
  public boolean getAutoDeleteContacts() {
    return autoDeleteContacts;
  }

  /** True if we should auto-delete locations. Some sites may wish to control
   * the deletion of locations to enforce consistency in their use. If this
   * is true we delete a location when it becomes unused.
   *
   * @param val
   */
  public void setAutoDeleteLocations(final boolean val) {
    autoDeleteLocations = val;
  }

  /**
   * @return boolean
   */
  public boolean getAutoDeleteLocations() {
    return autoDeleteLocations;
  }

  /**
   * @param val
   */
  public void setHour24(final boolean val) {
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
  public void setMinIncrement(final int val) {
    minIncrement = val;
  }

  /**
   * @return int
   */
  public int getMinIncrement() {
    return minIncrement;
  }

  /** True if we show data on year viewws.
   *
   * @param val
   */
  public void setShowYearData(final boolean val) {
    showYearData = val;
  }

  /**
   * @return boolean
   */
  public boolean getShowYearData() {
    return showYearData;
  }

  /** Where the browser finds css and other resources.
   *
   * @param val
   */
  public void setBrowserResourceRoot(final String val) {
    browserResourceRoot = val;
  }

  /**
   * @return String
   */
  public String getBrowserResourceRoot() {
    return browserResourceRoot;
  }

  /** Where the xslt and resources are based.
   *
   * @param val
   */
  public void setAppRoot(final String val) {
    appRoot = val;
  }

  /**
   * @return String
   */
  public String getAppRoot() {
    return appRoot;
  }

  /**
   * @param val
   */
  public void setRefreshAction(final String val) {
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
  public void setRefreshInterval(final int val) {
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
  public void setCalSuite(final String val) {
    calSuite = val;
  }

  /**
   * @return String
   */
  public String getCalSuite() {
    return calSuite;
  }

  /** The root of the calendars used for submission of public events by users.
   *
   * @param val
   */
  public void setSubmissionRoot(final String val) {
    submissionRoot = val;
  }

  /**
   * @return String
   */
  public String getSubmissionRoot() {
    return submissionRoot;
  }

  /** Copy this object to val.
   *
   * @param val
   */
  public void copyTo(final WebConfigCommon val) {
    super.copyTo(val);
    val.setAutoCreateContacts(getAutoCreateContacts());
    val.setAutoCreateLocations(getAutoCreateLocations());
    val.setAutoDeleteContacts(getAutoDeleteContacts());
    val.setAutoDeleteLocations(getAutoDeleteLocations());
    val.setHour24(getHour24());
    val.setMinIncrement(getMinIncrement());
    val.setShowYearData(getShowYearData());
    val.setBrowserResourceRoot(getBrowserResourceRoot());
    val.setAppRoot(getAppRoot());
    val.setRefreshAction(getRefreshAction());
    val.setRefreshInterval(getRefreshInterval());
    val.setCalSuite(getCalSuite());
    val.setSubmissionRoot(getSubmissionRoot());
  }

  @Override
  public Object clone() {
    WebConfigCommon conf = new WebConfigCommon();

    copyTo(conf);

    return conf;
  }
}
