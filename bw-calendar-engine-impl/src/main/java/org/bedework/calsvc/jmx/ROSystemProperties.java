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

/**
 * @author douglm
 *
 */
public final class ROSystemProperties implements SystemProperties {
  /* Wrapper to make System properties read only. */

  private SystemProperties cfg;

  private SystemProperties getConfig() {
    return cfg;
  }

  /**
   * @param cfg
   */
  ROSystemProperties(final SystemProperties cfg) {
    this.cfg = cfg;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setTzid(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setTzid(val);
  }

  @Override
  public String getTzid() {
    return getConfig().getTzid();
  }

  @Override
  public void setTzServeruri(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setTzServeruri(val);
  }

  @Override
  public String getTzServeruri() {
    return getConfig().getTzServeruri();
  }

  @Override
  public void setSystemid(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setSystemid(val);
  }

  @Override
  public String getSystemid() {
    return getConfig().getSystemid();
  }

  @Override
  public void setDefaultChangesNotifications(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultChangesNotifications(val);
  }

  @Override
  public boolean getDefaultChangesNotifications() {
    return getConfig().getDefaultChangesNotifications();
  }

  @Override
  public void setRootUsers(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setRootUsers(val);
  }

  @Override
  public String getRootUsers() {
    return getConfig().getRootUsers();
  }

  @Override
  public void setDefaultUserViewName(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultUserViewName(val);
  }

  @Override
  public String getDefaultUserViewName() {
    return getConfig().getDefaultUserViewName();
  }

  @Override
  public void setDefaultUserHour24(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultUserHour24(val);
  }

  @Override
  public boolean getDefaultUserHour24() {
    return getConfig().getDefaultUserHour24();
  }

  @Override
  public void setMaxPublicDescriptionLength(final int val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxPublicDescriptionLength(val);
  }

  @Override
  public int getMaxPublicDescriptionLength() {
    return getConfig().getMaxPublicDescriptionLength();
  }

  @Override
  public void setMaxUserDescriptionLength(final int val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxUserDescriptionLength(val);
  }

  @Override
  public int getMaxUserDescriptionLength() {
    return getConfig().getMaxUserDescriptionLength();
  }

  @Override
  public void setMaxUserEntitySize(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxUserEntitySize(val);
  }

  @Override
  public Integer getMaxUserEntitySize() {
    return getConfig().getMaxUserEntitySize();
  }

  @Override
  public void setDefaultUserQuota(final long val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultUserQuota(val);
  }

  @Override
  public long getDefaultUserQuota() {
    return getConfig().getDefaultUserQuota();
  }

  @Override
  public void setMaxInstances(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxInstances(val);
  }

  @Override
  public Integer getMaxInstances() {
    return getConfig().getMaxInstances();
  }

  @Override
  public void setMaxAttendeesPerInstance(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxAttendeesPerInstance(val);
  }

  @Override
  public Integer getMaxAttendeesPerInstance() {
    return getConfig().getMaxAttendeesPerInstance();
  }

  @Override
  public void setMinDateTime(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setMinDateTime(val);
  }

  @Override
  public String getMinDateTime() {
    return getConfig().getMinDateTime();
  }

  @Override
  public void setMaxDateTime(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxDateTime(val);
  }

  @Override
  public String getMaxDateTime() {
    return getConfig().getMaxDateTime();
  }

  @Override
  public void setDefaultFBPeriod(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultFBPeriod(val);
  }

  @Override
  public Integer getDefaultFBPeriod() {
    return getConfig().getDefaultFBPeriod();
  }

  @Override
  public void setMaxFBPeriod(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxFBPeriod(val);
  }

  @Override
  public Integer getMaxFBPeriod() {
    return getConfig().getMaxFBPeriod();
  }

  @Override
  public void setDefaultWebCalPeriod(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultWebCalPeriod(val);
  }

  @Override
  public Integer getDefaultWebCalPeriod() {
    return getConfig().getDefaultWebCalPeriod();
  }

  @Override
  public void setMaxWebCalPeriod(final Integer val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxWebCalPeriod(val);
  }

  @Override
  public Integer getMaxWebCalPeriod() {
    return getConfig().getMaxWebCalPeriod();
  }

  @Override
  public void setAdminContact(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setAdminContact(val);
  }

  @Override
  public String getAdminContact() {
    return getConfig().getAdminContact();
  }

  @Override
  public void setIscheduleURI(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setIscheduleURI(val);
  }

  @Override
  public String getIscheduleURI() {
    return getConfig().getIscheduleURI();
  }

  @Override
  public void setFburlServiceURI(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setFburlServiceURI(val);
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
    throw new RuntimeException("Immutable"); // getConfig().setWebcalServiceURI(val);
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
    throw new RuntimeException("Immutable"); // getConfig().setCalSoapWsURI(val);
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
    throw new RuntimeException("Immutable"); // getConfig().setCalSoapWsWSDLURI(val);
  }

  @Override
  public String getCalSoapWsWSDLURI() {
    return getConfig().getCalSoapWsWSDLURI();
  }

  @Override
  public void setTimezonesByReference(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setTimezonesByReference(val);
  }

  @Override
  public boolean getTimezonesByReference() {
    return getConfig().getTimezonesByReference();
  }

  @Override
  public void setDirectoryBrowsingDisallowed(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setDirectoryBrowsingDisallowed(val);
  }

  @Override
  public boolean getDirectoryBrowsingDisallowed() {
    return getConfig().getDirectoryBrowsingDisallowed();
  }

  @Override
  public void setMaxYears(final int val) {
    throw new RuntimeException("Immutable"); // getConfig().setMaxYears(val);
  }

  @Override
  public int getMaxYears() {
    return getConfig().getMaxYears();
  }

  @Override
  public void setUserauthClass(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setUserauthClass(val);
  }

  @Override
  public String getUserauthClass() {
    return getConfig().getUserauthClass();
  }

  @Override
  public void setMailerClass(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setMailerClass(val);
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
    throw new RuntimeException("Immutable"); // getConfig().setAdmingroupsClass(val);
  }

  @Override
  public String getAdmingroupsClass() {
    return getConfig().getAdmingroupsClass();
  }

  @Override
  public void setUsergroupsClass(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setUsergroupsClass(val);
  }

  @Override
  public String getUsergroupsClass() {
    return getConfig().getUsergroupsClass();
  }

  @Override
  public void setUseSolr(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setUseSolr(val);
  }

  @Override
  public boolean getUseSolr() {
    return getConfig().getUseSolr();
  }

  @Override
  public void setSolrURL(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setSolrURL(val);
  }

  @Override
  public String getSolrURL() {
    return getConfig().getSolrURL();
  }

  @Override
  public void setSolrCoreAdmin(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setSolrCoreAdmin(val);
  }

  @Override
  public String getSolrCoreAdmin() {
    return getConfig().getSolrCoreAdmin();
  }

  @Override
  public void setSolrDefaultCore(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setSolrDefaultCore(val);
  }

  @Override
  public String getSolrDefaultCore() {
    return getConfig().getSolrDefaultCore();
  }

  @Override
  public void setLocaleList(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setLocaleList(val);
  }

  @Override
  public String getLocaleList() {
    return getConfig().getLocaleList();
  }

  @Override
  public void setEventregAdminToken(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setEventregAdminToken(val);
  }

  @Override
  public String getEventregAdminToken() {
    return getConfig().getEventregAdminToken();
  }

  @Override
  public void setEventregUrl(final String val) {
    throw new RuntimeException("Immutable"); // getConfig().setEventregUrl(val);
  }

  @Override
  public String getEventregUrl() {
    return getConfig().getEventregUrl();
  }

  @Override
  public SystemProperties cloneIt() {
    return this;
  }
}
