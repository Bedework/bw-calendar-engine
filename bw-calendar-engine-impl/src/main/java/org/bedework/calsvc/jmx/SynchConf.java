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
public class SynchConf extends ConfBase<SynchConfigImpl>
        implements SynchConfMBean {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  /**
   */
  public SynchConf() {
    super(getServiceName("synch"));

    setConfigName("synch");

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
  public void setWsdlUri(final String val) {
    getConfig().setWsdlUri(val);
  }

  @Override
  public String getWsdlUri() {
    return getConfig().getWsdlUri();
  }

  @Override
  public void setManagerUri(final String val) {
    getConfig().setManagerUri(val);
  }

  @Override
  public String getManagerUri() {
    return getConfig().getManagerUri();
  }

  @Override
  public void setConnectorId(final String val) {
    getConfig().setConnectorId(val);
  }

  @Override
  public String getConnectorId() {
    return getConfig().getConnectorId();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(SynchConfigImpl.class);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
