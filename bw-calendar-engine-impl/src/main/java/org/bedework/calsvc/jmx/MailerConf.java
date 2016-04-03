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

import org.bedework.calfacade.mail.MailConfigPropertiesImpl;
import org.bedework.util.jmx.ConfBase;

/**
 * @author douglm
 *
 */
public class MailerConf extends ConfBase<MailConfigPropertiesImpl>
        implements MailerConfMBean {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  /**
   */
  public MailerConf() {
    super(getServiceName("mailer"));

    setConfigName("mailer");

    setConfigPname(confuriPname);
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
  public void setProtocol(final String val)  {
    getConfig().setProtocol(val);
  }

  @Override
  public String getProtocol()  {
    return getConfig().getProtocol();
  }

  @Override
  public void setServerUri(final String val)  {
    getConfig().setServerUri(val);
  }

  @Override
  public String getServerUri()  {
    return getConfig().getServerUri();
  }

  @Override
  public void setServerPort(final String val)  {
    getConfig().setServerPort(val);
  }

  @Override
  public String getServerPort()  {
    return getConfig().getServerPort();
  }

  @Override
  public void setStarttls(final boolean val) {
    getConfig().setStarttls(val);
  }

  @Override
  public boolean getStarttls() {
    return getConfig().getStarttls();
  }

  @Override
  public void setServerUsername(final String val) {
    getConfig().setServerUsername(val);
  }

  @Override
  public String getServerUsername() {
    return getConfig().getServerUsername();
  }

  @Override
  public void setServerPassword(final String val) {
    getConfig().setServerPassword(val);
  }

  @Override
  public String getServerPassword() {
    return getConfig().getServerPassword();
  }

  @Override
  public void setFrom(final String val)  {
    getConfig().setFrom(val);
  }

  @Override
  public String getFrom()  {
    return getConfig().getFrom();
  }

  @Override
  public void setSubject(final String val)  {
    getConfig().setSubject(val);
  }

  @Override
  public String getSubject()  {
    return getConfig().getSubject();
  }

  @Override
  public void setDisabled(final boolean val)  {
    getConfig().setDisabled(val);
  }

  @Override
  public boolean getDisabled()  {
    return getConfig().getDisabled();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(MailConfigPropertiesImpl.class);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
