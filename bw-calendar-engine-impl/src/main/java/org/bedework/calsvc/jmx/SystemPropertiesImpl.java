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
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

import java.util.List;

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

  private String featureFlags;

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

  private String localeList;

  private String eventregAdminToken;
  private String eventregUrl;

  private String cacheUrlPrefix;

  private int autoKillMinutes;

  private boolean suggestionEnabled;
  private boolean workflowEnabled;
  private String workflowRoot;

  private Integer vpollMaxItems;
  private Integer vpollMaxActive;
  private Integer vpollMaxVoters;

  private List<String> syseventsProperties;

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

  @Override
  public void setFeatureFlags(final String val) {
    featureFlags = val;
  }

  @Override
  public String getFeatureFlags() {
    return featureFlags;
  }

  @Override
  public void setAdminContact(final String val) {
    adminContact = val;
  }

  @Override
  public String getAdminContact() {
    return adminContact;
  }

  @Override
  public void setIscheduleURI(final String val) {
    ischeduleURI = val;
  }

  @Override
  public String getIscheduleURI() {
    return ischeduleURI;
  }

  @Override
  public void setFburlServiceURI(final String val) {
    fburlServiceURI = val;
  }

  @Override
  public String getFburlServiceURI() {
    return fburlServiceURI;
  }

  @Override
  public void setWebcalServiceURI(final String val) {
    webcalServiceURI = val;
  }

  @Override
  public String getWebcalServiceURI() {
    return webcalServiceURI;
  }

  @Override
  public void setCalSoapWsURI(final String val) {
    calSoapWsURI = val;
  }

  @Override
  public String getCalSoapWsURI() {
    return calSoapWsURI;
  }

  @Override
  public void setCalSoapWsWSDLURI(final String val) {
    calSoapWsWSDLURI = val;
  }

  @Override
  public String getCalSoapWsWSDLURI() {
    return calSoapWsWSDLURI;
  }

  @Override
  public void setTimezonesByReference(final boolean val) {
    timezonesByReference = val;
  }

  @Override
  public boolean getTimezonesByReference() {
    return timezonesByReference;
  }

  @Override
  public void setUserauthClass(final String val) {
    userauthClass = val;
  }

  @Override
  public String getUserauthClass() {
    return userauthClass;
  }

  @Override
  public void setMailerClass(final String val) {
    mailerClass = val;
  }

  @Override
  public String getMailerClass() {
    return mailerClass;
  }

  @Override
  public void setAdmingroupsClass(final String val) {
    admingroupsClass = val;
  }

  @Override
  public String getAdmingroupsClass() {
    return admingroupsClass;
  }

  @Override
  public void setUsergroupsClass(final String val) {
    usergroupsClass = val;
  }

  @Override
  public String getUsergroupsClass() {
    return usergroupsClass;
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
  public void setCacheUrlPrefix(final String val) {
    cacheUrlPrefix = val;
  }

  @Override
  public String getCacheUrlPrefix() {
    return cacheUrlPrefix;
  }

  @Override
  public void setAutoKillMinutes(final int val) {
    autoKillMinutes = val;
  }

  @Override
  public void setSuggestionEnabled(final boolean val) {
    suggestionEnabled = val;
  }

  @Override
  public boolean getSuggestionEnabled() {
    return suggestionEnabled;
  }

  @Override
  public void setWorkflowEnabled(final boolean val) {
    workflowEnabled = val;
  }

  @Override
  public boolean getWorkflowEnabled() {
    return workflowEnabled;
  }

  @Override
  public void setWorkflowRoot(final String val) {
    workflowRoot = val;
  }

  @Override
  public String getWorkflowRoot() {
    return workflowRoot;
  }

  @Override
  public int getAutoKillMinutes() {
    return autoKillMinutes;
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
    final ToString ts = new ToString(this);

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
    ts.append("localeList", getLocaleList());

    ts.newLine();
    ts.append("rootUsers", getRootUsers());
    ts.append("autoKillMinutes", getAutoKillMinutes());

    ts.append("workflowEnabled", getWorkflowEnabled());

    ts.newLine();
    ts.append("vpollMaxItems", getVpollMaxItems());
    ts.append("vpollMaxActive", getVpollMaxActive());
    ts.append("vpollMaxVoters", getVpollMaxVoters());

    ts.append("syseventsProperties", getSyseventsProperties());

    return ts.toString();
  }

  @Override
  public SystemProperties cloneIt() {
    final SystemPropertiesImpl clone = new SystemPropertiesImpl();

    clone.setName(getName());
    clone.setTzid(getTzid());
    clone.setSystemid(getSystemid());

    clone.setUserauthClass(getUserauthClass());
    clone.setMailerClass(getMailerClass());
    clone.setAdmingroupsClass(getAdmingroupsClass());
    clone.setUsergroupsClass(getUsergroupsClass());

    clone.setLocaleList(getLocaleList());
    clone.setRootUsers(getRootUsers());

    clone.setLocaleList(getLocaleList());
    clone.setEventregAdminToken(getEventregAdminToken());
    clone.setEventregUrl(getEventregUrl());

    clone.setVpollMaxItems(getVpollMaxItems());
    clone.setVpollMaxActive(getVpollMaxActive());
    clone.setVpollMaxVoters(getVpollMaxVoters());

    clone.setSyseventsProperties(getSyseventsProperties());

    return clone;
  }

  @Override
  public void setSyseventsProperties(final List<String> val) {
    syseventsProperties = val;
  }

  @Override
  @ConfInfo(collectionElementName = "syseventsProperty" ,
            elementType = "java.lang.String")
  public List<String> getSyseventsProperties() {
    return syseventsProperties;
  }

  @Override
  public void addSyseventsProperty(final String name,
                                   final String val) {
    setSyseventsProperties(addListProperty(getSyseventsProperties(),
                                           name, val));
  }

  @Override
  public String getSyseventsProperty(final String name) {
    return getProperty(getSyseventsProperties(), name);
  }

  @Override
  public void removeSyseventsProperty(final String name) {
    removeProperty(getSyseventsProperties(), name);
  }

  @Override
  public void setSyseventsProperty(final String name,
                                   final String val) {
    setSyseventsProperties(setListProperty(getSyseventsProperties(),
                                           name, val));
  }
}
