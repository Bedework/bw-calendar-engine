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

import org.bedework.calfacade.configs.CalAddrPrefixes;
import org.bedework.util.config.ConfigurationStore;
import org.bedework.util.jmx.ConfBase;

/**
 * @author douglm
 *
 * @param <X>
 */
public class DirConf<X extends DirConfigPropertiesImpl> extends ConfBase<X>
    implements DirConfMBean {
  /**
   * @param configStore so we can load and store
   * @param serviceName of the service
   * @param cfg - the configuration
   * @param cfgName its name
   */
  public void init(final ConfigurationStore configStore,
                   final String serviceName,
                   final X cfg,
                   final String cfgName) {
    setServiceName(serviceName);
    setStore(configStore);
    setConfigName(cfgName);

    this.cfg = cfg;
  }

  @Override
  public String loadConfig() {
    return null;
  }

  /* ========================================================================
   * Conf properties
   * ======================================================================== */

  @Override
  public void setMbeanClassName(final String val) {
    getConfig().setMbeanClassName(val);
  }

  @Override
  public String getMbeanClassName() {
    if (getConfig().getMbeanClassName() == null) {
      return this.getClass().getCanonicalName();
    }

    return getConfig().getMbeanClassName();
  }

  @Override
  public void setDomains(final String val)  {
    getConfig().setDomains(val);
  }

  @Override
  public String getDomains()  {
    return getConfig().getDomains();
  }

  @Override
  public void setDefaultDomain(final String val)  {
    getConfig().setDefaultDomain(val);
  }

  @Override
  public String getDefaultDomain()  {
    return getConfig().getDefaultDomain();
  }

  @Override
  public void setCalAddrPrefixes(final CalAddrPrefixes val) {
    getConfig().setCalAddrPrefixes(val);
  }

  @Override
  public CalAddrPrefixes getCalAddrPrefixes() {
    return getConfig().getCalAddrPrefixes();
  }
}
