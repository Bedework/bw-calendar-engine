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

/**
 * @author douglm
 *
 */
public final class ROAuthProperties implements AuthProperties {
  /* Wrapper to make System properties read only. */

  private AuthProperties cfg;

  private AuthProperties getConfig() {
    return cfg;
  }

  /**
   * @param cfg
   */
  ROAuthProperties(final AuthProperties cfg) {
    this.cfg = cfg;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setDefaultChangesNotifications(final boolean val) {
    throw new RuntimeException("Immutable"); // getConfig().setDefaultChangesNotifications(val);
  }

  @Override
  public boolean getDefaultChangesNotifications() {
    return getConfig().getDefaultChangesNotifications();
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
  public AuthProperties cloneIt() {
    return this;
  }
}
