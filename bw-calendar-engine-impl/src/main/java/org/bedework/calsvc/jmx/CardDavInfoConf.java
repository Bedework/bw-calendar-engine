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

import edu.rpi.cmt.jmx.ConfBase;

/**
 * @author douglm
 *
 */
public class CardDavInfoConf extends ConfBase<CardDavInfoImpl>
        implements CardDavInfoConfMBean {
  /** Name of the property holding the location of the config data */
  public static final String datauriPname = "org.bedework.bwengine.datauri";

  /**
   * @param name
   */
  public CardDavInfoConf(final String name) {
    super(getServiceName(name));

    setConfigName(name);

    setConfigPname(datauriPname);

    //TzServerUtil.setTzConfigHolder(this);
  }

  /**
   * @param name
   * @return service name for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=carddavInfo:Type=" + name;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setAuth(final boolean val) {
    getConfig().setAuth(val);
  }

  @Override
  public boolean getAuth() {
    return getConfig().getAuth();
  }

  @Override
  public void setHost(final String val) {
    getConfig().setHost(val);
  }

  @Override
  public String getHost() {
    return getConfig().getHost();
  }

  @Override
  public void setPort(final int val) {
    getConfig().setPort(val);
  }

  @Override
  public int getPort() {
    return getConfig().getPort();
  }

  @Override
  public void setContextPath(final String val) {
    getConfig().setContextPath(val);
  }

  @Override
  public String getContextPath() {
    return getConfig().getContextPath();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(CardDavInfoImpl.class);
  }
}
