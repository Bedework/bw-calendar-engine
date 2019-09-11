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

import org.bedework.util.jmx.ConfBase;

/**
 * @author douglm
 *
 */
public class CardDavInfoConf extends ConfBase<CardDavInfoImpl>
        implements CardDavInfoConfMBean {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  /**
   * @param name of service
   */
  public CardDavInfoConf(final String name) {
    super(getServiceName(name));

    setConfigName(name);

    setConfigPname(confuriPname);

    //TzServerUtil.setTzConfigHolder(this);
  }

  /**
   * @param name of service
   * @return service name for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=carddavInfo,Type=" + name;
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
  public void setUrl(final String val) {
    getConfig().setUrl(val);
  }

  @Override
  public String getUrl() {
    return getConfig().getUrl();
  }

  @Override
  public void setCutypeMapping(final String val) {
    getConfig().setCutypeMapping(val);
  }

  @Override
  public String getCutypeMapping() {
    return getConfig().getCutypeMapping();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(CardDavInfoImpl.class);
  }
}
