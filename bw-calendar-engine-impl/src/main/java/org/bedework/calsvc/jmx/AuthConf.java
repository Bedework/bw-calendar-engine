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

import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jmx.ConfigHolder;

/**
 * @author douglm
 *
 */
public class AuthConf extends ConfBase<AuthPropertiesImpl>
        implements AuthConfMBean, ConfigHolder<AuthPropertiesImpl> {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  /**
   * @param name
   */
  public AuthConf(final String name) {
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
  public void setDefaultChangesNotifications(final boolean val) {
    getConfig().setDefaultChangesNotifications(val);
  }

  @Override
  public boolean getDefaultChangesNotifications() {
    return getConfig().getDefaultChangesNotifications();
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

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String loadConfig() {
    return loadConfig(AuthPropertiesImpl.class);
  }

  /** Save the configuration.
   *
   */
  @Override
  public void putConfig() {
    saveConfig();
  }

  @Override
  public AuthProperties cloneIt() {
    return null;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
