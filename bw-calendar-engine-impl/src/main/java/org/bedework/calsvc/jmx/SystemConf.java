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
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;

import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jmx.ConfigHolder;

/**
 * @author douglm
 *
 */
public class SystemConf extends ConfBase<SystemPropertiesImpl>
        implements SystemConfMBean, ConfigHolder<SystemPropertiesImpl> {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  private CalSvcI svci;

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
  public void setRootUsers(final String val) {
    getConfig().setRootUsers(val);
  }

  @Override
  public String getRootUsers() {
    return getConfig().getRootUsers();
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

  @Override
  public void setDbStatsEnabled(final boolean enable) {
    try {
      getSvci();

      if (svci != null) {
        svci.setDbStatsEnabled(enable);
      }
    } catch (Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }
  }

  @Override
  public boolean getDbStatsEnabled() {
    try {
      getSvci();

      if (svci == null) {
        return false;
      }

      return svci.getDbStatsEnabled();
    } catch (Throwable t) {
      error(t);
      return false;
    } finally {
      closeSvci();
    }
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public BwStats getStats()  {
    try {
      getSvci();

      if (svci == null) {
        return null;
      }

      return svci.getStats();
    } catch (Throwable t) {
      error(t);
      return null;
    } finally {
      closeSvci();
    }
  }

  @Override
  public void dumpDbStats() {
    try {
      getSvci();

      if (svci != null) {
        svci.dumpDbStats();
      }
    } catch (Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }
  }

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

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @return svci object
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  private CalSvcI getSvci() throws CalFacadeException {
    if (getConfig() == null) {
      return null;
    }

    if ((svci != null) && svci.isOpen()) {
      return svci;
    }

    boolean publicAdmin = false;

    /* Extract a root user */

    if (getRootUsers() == null) {
      return null;
    }

    String rootUsers[] = getRootUsers().split(",");

    if ((rootUsers.length == 0) || (rootUsers[0] == null)) {
      return null;
    }

    CalSvcIPars pars = CalSvcIPars.getServicePars(rootUsers[0],
                                                  true,   // publicAdmin
                                                  true);   // Allow super user
    svci = new CalSvcFactoryDefault().getSvc(pars);

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   */
  private void closeSvci() {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();
    } catch (Throwable t) {
      try {
        svci.close();
      } catch (Throwable t1) {
      }
    }

    try {
      svci.close();
    } catch (Throwable t) {
    }
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
