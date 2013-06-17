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

import edu.rpi.cmt.jmx.ConfBase;
import edu.rpi.cmt.jmx.ConfigHolder;

/**
 * @author douglm
 *
 */
public class SystemConf extends ConfBase<SystemPropertiesImpl>
        implements SystemConfMBean, ConfigHolder<SystemPropertiesImpl> {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  /**
   * @param name
   */
  public SystemConf(final String name) {
    super(getServiceName(name));

    setConfigName(name);

    setConfigPname(confuriPname);

    //TzServerUtil.setTzConfigHolder(this);
  }

  /**
   * @param name
   * @return service name for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setTzid(final String val) {
    getConfig().setTzid(val);
  }

  @Override
  public String getTzid() {
    return getConfig().getTzid();
  }

  @Override
  public void setTzServeruri(final String val) {
    getConfig().setTzServeruri(val);
  }

  @Override
  public String getTzServeruri() {
    return getConfig().getTzServeruri();
  }

  @Override
  public void setSystemid(final String val) {
    getConfig().setSystemid(val);
  }

  @Override
  public String getSystemid() {
    return getConfig().getSystemid();
  }

  @Override
  public void setDefaultChangesNotifications(final boolean val) {
    getConfig().setDefaultChangesNotifications(val);
  }

  @Override
  public boolean getDefaultChangesNotifications() {
    return getConfig().getDefaultChangesNotifications();
  }

  @Override
  public void setRootUsers(final String val) {
    getConfig().setRootUsers(val);
  }

  @Override
  public String getRootUsers() {
    return getConfig().getRootUsers();
  }

  @Override
  public void setDefaultUserViewName(final String val) {
    getConfig().setDefaultUserViewName(val);
  }

  @Override
  public String getDefaultUserViewName() {
    return getConfig().getDefaultUserViewName();
  }

  @Override
  public void setDefaultUserHour24(final boolean val) {
    getConfig().setDefaultUserHour24(val);
  }

  @Override
  public boolean getDefaultUserHour24() {
    return getConfig().getDefaultUserHour24();
  }

  @Override
  public void setMaxPublicDescriptionLength(final int val) {
    getConfig().setMaxPublicDescriptionLength(val);
  }

  @Override
  public int getMaxPublicDescriptionLength() {
    return getConfig().getMaxPublicDescriptionLength();
  }

  @Override
  public void setMaxUserDescriptionLength(final int val) {
    getConfig().setMaxUserDescriptionLength(val);
  }

  @Override
  public int getMaxUserDescriptionLength() {
    return getConfig().getMaxUserDescriptionLength();
  }

  @Override
  public void setMaxUserEntitySize(final Integer val) {
    getConfig().setMaxUserEntitySize(val);
  }

  @Override
  public Integer getMaxUserEntitySize() {
    return getConfig().getMaxUserEntitySize();
  }

  @Override
  public void setDefaultUserQuota(final long val) {
    getConfig().setDefaultUserQuota(val);
  }

  @Override
  public long getDefaultUserQuota() {
    return getConfig().getDefaultUserQuota();
  }

  @Override
  public void setMaxInstances(final Integer val) {
    getConfig().setMaxInstances(val);
  }

  @Override
  public Integer getMaxInstances() {
    return getConfig().getMaxInstances();
  }

  @Override
  public void setMaxAttendeesPerInstance(final Integer val) {
    getConfig().setMaxAttendeesPerInstance(val);
  }

  @Override
  public Integer getMaxAttendeesPerInstance() {
    return getConfig().getMaxAttendeesPerInstance();
  }

  @Override
  public void setMinDateTime(final String val) {
    getConfig().setMinDateTime(val);
  }

  @Override
  public String getMinDateTime() {
    return getConfig().getMinDateTime();
  }

  @Override
  public void setMaxDateTime(final String val) {
    getConfig().setMaxDateTime(val);
  }

  @Override
  public String getMaxDateTime() {
    return getConfig().getMaxDateTime();
  }

  @Override
  public void setDefaultFBPeriod(final Integer val) {
    getConfig().setDefaultFBPeriod(val);
  }

  @Override
  public Integer getDefaultFBPeriod() {
    return getConfig().getDefaultFBPeriod();
  }

  @Override
  public void setMaxFBPeriod(final Integer val) {
    getConfig().setMaxFBPeriod(val);
  }

  @Override
  public Integer getMaxFBPeriod() {
    return getConfig().getMaxFBPeriod();
  }

  @Override
  public void setDefaultWebCalPeriod(final Integer val) {
    getConfig().setDefaultWebCalPeriod(val);
  }

  @Override
  public Integer getDefaultWebCalPeriod() {
    return getConfig().getDefaultWebCalPeriod();
  }

  @Override
  public void setMaxWebCalPeriod(final Integer val) {
    getConfig().setMaxWebCalPeriod(val);
  }

  @Override
  public Integer getMaxWebCalPeriod() {
    return getConfig().getMaxWebCalPeriod();
  }

  @Override
  public void setAdminContact(final String val) {
    getConfig().setAdminContact(val);
  }

  @Override
  public String getAdminContact() {
    return getConfig().getAdminContact();
  }

  @Override
  public void setIscheduleURI(final String val) {
    getConfig().setIscheduleURI(val);
  }

  @Override
  public String getIscheduleURI() {
    return getConfig().getIscheduleURI();
  }

  @Override
  public void setFburlServiceURI(final String val) {
    getConfig().setFburlServiceURI(val);
  }

  @Override
  public String getFburlServiceURI() {
    return getConfig().getFburlServiceURI();
  }

  /** Set the web calendar service uri - null for no web calendar service
   *
   * @param val    String
   */
  @Override
  public void setWebcalServiceURI(final String val) {
    getConfig().setWebcalServiceURI(val);
  }

  /** get the web calendar service uri - null for no web calendar service
   *
   * @return String
   */
  @Override
  public String getWebcalServiceURI() {
    return getConfig().getWebcalServiceURI();
  }

  /** Set the calws soap web service uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsURI(final String val) {
    getConfig().setCalSoapWsURI(val);
  }

  /** Get the calws soap web service uri - null for no service
   *
   * @return String
   */
  @Override
  public String getCalSoapWsURI() {
    return getConfig().getCalSoapWsURI();
  }

  /** Set the calws soap web service WSDL uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsWSDLURI(final String val) {
    getConfig().setCalSoapWsWSDLURI(val);
  }

  @Override
  public String getCalSoapWsWSDLURI() {
    return getConfig().getCalSoapWsWSDLURI();
  }

  @Override
  public void setTimezonesByReference(final boolean val) {
    getConfig().setTimezonesByReference(val);
  }

  @Override
  public boolean getTimezonesByReference() {
    return getConfig().getTimezonesByReference();
  }

  @Override
  public void setDirectoryBrowsingDisallowed(final boolean val) {
    getConfig().setDirectoryBrowsingDisallowed(val);
  }

  @Override
  public boolean getDirectoryBrowsingDisallowed() {
    return getConfig().getDirectoryBrowsingDisallowed();
  }

  @Override
  public void setMaxYears(final int val) {
    getConfig().setMaxYears(val);
  }

  @Override
  public int getMaxYears() {
    return getConfig().getMaxYears();
  }

  @Override
  public void setUserauthClass(final String val) {
    getConfig().setUserauthClass(val);
  }

  @Override
  public String getUserauthClass() {
    return getConfig().getUserauthClass();
  }

  @Override
  public void setMailerClass(final String val) {
    getConfig().setMailerClass(val);
  }

  /**
   *
   * @return String
   */
  @Override
  public String getMailerClass() {
    return getConfig().getMailerClass();
  }

  /** Set the admingroups class
   *
   * @param val    String admingroups class
   */
  @Override
  public void setAdmingroupsClass(final String val) {
    getConfig().setAdmingroupsClass(val);
  }

  @Override
  public String getAdmingroupsClass() {
    return getConfig().getAdmingroupsClass();
  }

  @Override
  public void setUsergroupsClass(final String val) {
    getConfig().setUsergroupsClass(val);
  }

  @Override
  public String getUsergroupsClass() {
    return getConfig().getUsergroupsClass();
  }

  @Override
  public void setSolrURL(final String val) {
    getConfig().setSolrURL(val);
  }

  @Override
  public String getSolrURL() {
    return getConfig().getSolrURL();
  }

  @Override
  public void setSolrCoreAdmin(final String val) {
    getConfig().setSolrCoreAdmin(val);
  }

  @Override
  public String getSolrCoreAdmin() {
    return getConfig().getSolrCoreAdmin();
  }

  @Override
  public void setSolrPublicCore(final String val) {
    getConfig().setSolrPublicCore(val);
  }

  @Override
  public String getSolrPublicCore() {
    return getConfig().getSolrPublicCore();
  }

  @Override
  public void setSolrUserCore(final String val) {
    getConfig().setSolrUserCore(val);
  }

  @Override
  public String getSolrUserCore() {
    return getConfig().getSolrUserCore();
  }

  @Override
  public void setLocaleList(final String val) {
    getConfig().setLocaleList(val);
  }

  @Override
  public String getLocaleList() {
    return getConfig().getLocaleList();
  }

  @Override
  public void setEventregAdminToken(final String val) {
    getConfig().setEventregAdminToken(val);
  }

  @Override
  public String getEventregAdminToken() {
    return getConfig().getEventregAdminToken();
  }

  @Override
  public void setEventregUrl(final String val) {
    getConfig().setEventregUrl(val);
  }

  @Override
  public String getEventregUrl() {
    return getConfig().getEventregUrl();
  }

  @Override
  public void setVpollMaxItems(final Integer val) {
    getConfig().setVpollMaxItems(val);
  }

  @Override
  public Integer getVpollMaxItems() {
    return getConfig().getVpollMaxItems();
  }

  @Override
  public void setVpollMaxActive(final Integer val) {
    getConfig().setVpollMaxActive(val);
  }

  @Override
  public Integer getVpollMaxActive() {
    return getConfig().getVpollMaxActive();
  }

  @Override
  public void setVpollMaxVoters(final Integer val) {
    getConfig().setVpollMaxVoters(val);
  }

  @Override
  public Integer getVpollMaxVoters() {
    return getConfig().getVpollMaxVoters();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(SystemPropertiesImpl.class);
  }

  /** Save the configuration.
   *
   */
  @Override
  public void putConfig() {
    saveConfig();
  }

  @Override
  public SystemProperties cloneIt() {
    return null;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
