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

import org.bedework.calfacade.configs.SystemProperties;

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.config.ConfigBase;
import edu.rpi.sss.util.ToString;

/** These are the system properties that the server needs to know about, either
 * because it needs to apply these limits or just to report them to clients.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "system-properties",
          type = "org.bedework.calfacade.configs.SystemProperties")
public class SystemPropertiesImpl extends ConfigBase<SystemPropertiesImpl>
        implements SystemProperties {
  /* Default time zone */
  private String tzid;
  private String tzServeruri;

  /* The system id */
  private String systemid;

  private String rootUsers;

  private String userauthClass;
  private String mailerClass;
  private String admingroupsClass;
  private String usergroupsClass;

  private String adminContact;

  /* ischedule service uri - null for no ischedule service */
  private String ischeduleURI;

  /* Free busy service uri - null for no freebusy service */
  private String fburlServiceURI;

  /* Web calendar service uri - null for no web calendar service */
  private String webcalServiceURI;

  /* CalWS SOAP web service uri - null for no service */
  private String calSoapWsURI;

  /* CalWS SOAP web service WSDL uri - null for no service */
  private String calSoapWsWSDLURI;

  private boolean timezonesByReference;

  private boolean directoryBrowsingDisallowed;

  private String indexerURL;

  private String indexerConfig;

  private String solrCoreAdmin;

  private String publicIndexName;

  private String userIndexName;

  private String localeList;

  private String eventregAdminToken;
  private String eventregUrl;

  private Integer vpollMaxItems;
  private Integer vpollMaxActive;
  private Integer vpollMaxVoters;

  @Override
  public void setTzid(final String val) {
    tzid = val;
  }

  @Override
  public String getTzid() {
    return tzid;
  }

  @Override
  public void setTzServeruri(final String val) {
    tzServeruri = val;
  }

  @Override
  public String getTzServeruri() {
    return tzServeruri;
  }

  @Override
  public void setSystemid(final String val) {
    systemid = val;
  }

  @Override
  public String getSystemid() {
    return systemid;
  }

  @Override
  public void setRootUsers(final String val) {
    rootUsers = val;
  }

  @Override
  public String getRootUsers() {
    return rootUsers;
  }

  /** Set the administrator contact property
   *
   * @param val
   */
  @Override
  public void setAdminContact(final String val) {
    adminContact = val;
  }

  /** Get the administrator contact property
   *
   * @return String
   */
  @Override
  public String getAdminContact() {
    return adminContact;
  }

  /** Set the ischedule service uri - null for no ischedule service
   *
   * @param val    String
   */
  @Override
  public void setIscheduleURI(final String val) {
    ischeduleURI = val;
  }

  /** get the ischedule service uri - null for no ischedule service
   *
   * @return String
   */
  @Override
  public String getIscheduleURI() {
    return ischeduleURI;
  }

  /** Set the Free busy service uri - null for no freebusy service
   *
   * @param val    String
   */
  @Override
  public void setFburlServiceURI(final String val) {
    fburlServiceURI = val;
  }

  /** get the Free busy service uri - null for no freebusy service
   *
   * @return String
   */
  @Override
  public String getFburlServiceURI() {
    return fburlServiceURI;
  }

  /** Set the web calendar service uri - null for no web calendar service
   *
   * @param val    String
   */
  @Override
  public void setWebcalServiceURI(final String val) {
    webcalServiceURI = val;
  }

  /** get the web calendar service uri - null for no web calendar service
   *
   * @return String
   */
  @Override
  public String getWebcalServiceURI() {
    return webcalServiceURI;
  }

  /** Set the calws soap web service uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsURI(final String val) {
    calSoapWsURI = val;
  }

  /** Get the calws soap web service uri - null for no service
   *
   * @return String
   */
  @Override
  public String getCalSoapWsURI() {
    return calSoapWsURI;
  }

  /** Set the calws soap web service WSDL uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsWSDLURI(final String val) {
    calSoapWsWSDLURI = val;
  }

  /** Get the calws soap web service WSDL uri - null for no service
   *
   * @return String
   */
  @Override
  public String getCalSoapWsWSDLURI() {
    return calSoapWsWSDLURI;
  }

  /**
   * @param val boolean true if we are not including the full tz specification..
   */
  @Override
  public void setTimezonesByReference(final boolean val) {
    timezonesByReference = val;
  }

  /**
   * @return true if we are not including the full tz specification
   */
  @Override
  public boolean getTimezonesByReference() {
    return timezonesByReference;
  }

  /** Set the userauth class
   *
   * @param val    String userauth class
   */
  @Override
  public void setUserauthClass(final String val) {
    userauthClass = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getUserauthClass() {
    return userauthClass;
  }

  /** Set the mailer class
   *
   * @param val    String mailer class
   */
  @Override
  public void setMailerClass(final String val) {
    mailerClass = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getMailerClass() {
    return mailerClass;
  }

  /** Set the admingroups class
   *
   * @param val    String admingroups class
   */
  @Override
  public void setAdmingroupsClass(final String val) {
    admingroupsClass = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getAdmingroupsClass() {
    return admingroupsClass;
  }

  /** Set the usergroups class
   *
   * @param val    String usergroups class
   */
  @Override
  public void setUsergroupsClass(final String val) {
    usergroupsClass = val;
  }

  /**
   *
   * @return String
   */
  @Override
  public String getUsergroupsClass() {
    return usergroupsClass;
  }

  @Override
  public void setIndexerURL(final String val) {
    indexerURL = val;
  }

  @Override
  public String getIndexerURL() {
    return indexerURL;
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
  public void setLocaleList(final String val) {
    localeList = val;
  }

  @Override
  public String getLocaleList() {
    return localeList;
  }

  @Override
  public void setEventregAdminToken(final String val) {
    eventregAdminToken = val;
  }

  @Override
  public String getEventregAdminToken() {
    return eventregAdminToken;
  }

  @Override
  public void setEventregUrl(final String val) {
    eventregUrl = val;
  }

  @Override
  public String getEventregUrl() {
    return eventregUrl;
  }

  @Override
  public void setVpollMaxItems(final Integer val) {
    vpollMaxItems = val;
  }

  @Override
  public Integer getVpollMaxItems() {
    return vpollMaxItems;
  }

  @Override
  public void setVpollMaxActive(final Integer val) {
    vpollMaxActive = val;
  }

  @Override
  public Integer getVpollMaxActive() {
    return vpollMaxActive;
  }

  @Override
  public void setVpollMaxVoters(final Integer val) {
    vpollMaxVoters = val;
  }

  @Override
  public Integer getVpollMaxVoters() {
    return vpollMaxVoters;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.newLine();
    ts.append("name", getName());
    ts.append("tzid", getTzid());
    ts.append("tzServeruri", getTzServeruri());
    ts.append("systemid", getSystemid());

    ts.newLine();

    ts.newLine();
    ts.append("userauthClass", getUserauthClass());
    ts.newLine();
    ts.append("mailerClass", getMailerClass());
    ts.newLine();
    ts.append("admingroupsClass", getAdmingroupsClass());
    ts.newLine();
    ts.append("usergroupsClass", getUsergroupsClass());

    ts.newLine();

    ts.append("indexerURL", getIndexerURL());
    ts.append("indexerConfig", getIndexerConfig());
    ts.append("solrCoreAdmin", getSolrCoreAdmin());
    ts.append("publicIndexName", getPublicIndexName());
    ts.append("userIndexName", getUserIndexName());

    ts.newLine();
    ts.append("localeList", getLocaleList());

    ts.newLine();
    ts.append("rootUsers", getRootUsers());

    ts.newLine();
    ts.append("vpollMaxItems", getVpollMaxItems());
    ts.append("vpollMaxActive", getVpollMaxActive());
    ts.append("vpollMaxVoters", getVpollMaxVoters());

    return ts.toString();
  }

  @Override
  public SystemProperties cloneIt() {
    SystemPropertiesImpl clone = new SystemPropertiesImpl();

    clone.setName(getName());
    clone.setTzid(getTzid());
    clone.setSystemid(getSystemid());

    clone.setUserauthClass(getUserauthClass());
    clone.setMailerClass(getMailerClass());
    clone.setAdmingroupsClass(getAdmingroupsClass());
    clone.setUsergroupsClass(getUsergroupsClass());

    clone.setLocaleList(getLocaleList());
    clone.setRootUsers(getRootUsers());

    clone.setIndexerURL(getIndexerURL());
    clone.setIndexerConfig(getIndexerConfig());
    clone.setSolrCoreAdmin(getSolrCoreAdmin());
    clone.setPublicIndexName(getPublicIndexName());
    clone.setUserIndexName(getUserIndexName());

    clone.setLocaleList(getLocaleList());
    clone.setEventregAdminToken(getEventregAdminToken());
    clone.setEventregUrl(getEventregUrl());

    clone.setVpollMaxItems(getVpollMaxItems());
    clone.setVpollMaxActive(getVpollMaxActive());
    clone.setVpollMaxVoters(getVpollMaxVoters());


    return clone;
  }
}
