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

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.config.ConfigBase;
import edu.rpi.sss.util.ToString;

/** These are the system properties that the server needs to know about, either
 * because it needs to apply these limits or just to report them to clients.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "auth-properties",
          type = "org.bedework.calfacade.configs.AuthProperties")
public class AuthPropertiesImpl extends ConfigBase<AuthPropertiesImpl>
        implements AuthProperties {
  private boolean defaultChangesNotifications;

  private String defaultUserViewName;

  private boolean defaultUserHour24 = true;

  private int maxPublicDescriptionLength;
  private int maxUserDescriptionLength;
  private Integer maxUserEntitySize;
  private long defaultUserQuota;

  private Integer maxInstances;

  private int maxYears;

  private Integer maxAttendeesPerInstance;

  private String minDateTime;

  private String maxDateTime;

  private Integer defaultFBPeriod = 31;

  private Integer maxFBPeriod = 32 * 3;

  private Integer defaultWebCalPeriod = 31;

  private Integer maxWebCalPeriod = 32 * 3;

  private boolean directoryBrowsingDisallowed;

  @Override
  public void setDefaultChangesNotifications(final boolean val) {
    defaultChangesNotifications = val;
  }

  @Override
  public boolean getDefaultChangesNotifications() {
    return defaultChangesNotifications;
  }

  @Override
  public void setDefaultUserViewName(final String val) {
    defaultUserViewName = val;
  }

  @Override
  public String getDefaultUserViewName() {
    return defaultUserViewName;
  }

  @Override
  public void setDefaultUserHour24(final boolean val) {
    defaultUserHour24 = val;
  }

  @Override
  public boolean getDefaultUserHour24() {
    return defaultUserHour24;
  }

  @Override
  public void setMaxPublicDescriptionLength(final int val) {
    maxPublicDescriptionLength = val;
  }

  @Override
  public int getMaxPublicDescriptionLength() {
    return maxPublicDescriptionLength;
  }

  /** Set the max description length for user events
   *
   * @param val    int max
   */
  @Override
  public void setMaxUserDescriptionLength(final int val) {
    maxUserDescriptionLength = val;
  }

  /**
   *
   * @return int
   */
  @Override
  public int getMaxUserDescriptionLength() {
    return maxUserDescriptionLength;
  }

  @Override
  public void setMaxUserEntitySize(final Integer val) {
    maxUserEntitySize = val;
  }

  @Override
  public Integer getMaxUserEntitySize() {
    return maxUserEntitySize;
  }

  @Override
  public void setDefaultUserQuota(final long val) {
    defaultUserQuota = val;
  }

  @Override
  public long getDefaultUserQuota() {
    return defaultUserQuota;
  }

  @Override
  public void setMaxInstances(final Integer val) {
    maxInstances = val;
  }

  @Override
  public Integer getMaxInstances() {
    return maxInstances;
  }

  @Override
  public void setMaxAttendeesPerInstance(final Integer val) {
    maxAttendeesPerInstance = val;
  }

  @Override
  public Integer getMaxAttendeesPerInstance() {
    return maxAttendeesPerInstance;
  }

  /**
   * @param val    minimum date time allowed - null for no limit
   */
  @Override
  public void setMinDateTime(final String val) {
    minDateTime = val;
  }

  /**
   *
   * @return String   minimum date time allowed - null for no limit
   */
  @Override
  public String getMinDateTime() {
    return minDateTime;
  }

  /**
   * @param val    maximum date time allowed - null for no limit
   */
  @Override
  public void setMaxDateTime(final String val) {
    maxDateTime = val;
  }

  /**
   *
   * @return String   maximum date time allowed - null for no limit
   */
  @Override
  public String getMaxDateTime() {
    return maxDateTime;
  }

  /** Set the c if not specified
   *
   * @param val
   */
  @Override
  public void setDefaultFBPeriod(final Integer val) {
    defaultFBPeriod = val;
  }

  /** get the default freebusy fetch period if not specified
   *
   * @return Integer days
   */
  @Override
  public Integer getDefaultFBPeriod() {
    return defaultFBPeriod;
  }

  /** Set the maximum freebusy fetch period
   *
   * @param val
   */
  @Override
  public void setMaxFBPeriod(final Integer val) {
    maxFBPeriod = val;
  }

  /** get the maximum freebusy fetch period
   *
   * @return Integer days
   */
  @Override
  public Integer getMaxFBPeriod() {
    return maxFBPeriod;
  }

  /** Set the default webcal fetch period if not specified
   *
   * @param val
   */
  @Override
  public void setDefaultWebCalPeriod(final Integer val) {
    defaultWebCalPeriod = val;
  }

  /** Get the default webcal fetch period if not specified
   *
   * @return Integer days
   */
  @Override
  public Integer getDefaultWebCalPeriod() {
    return defaultWebCalPeriod;
  }

  /** Set the maximum webcal fetch period
   *
   * @param val
   */
  @Override
  public void setMaxWebCalPeriod(final Integer val) {
    maxWebCalPeriod = val;
  }

  /** Set the maximum webcal fetch period
   *
   * @return Integer days
   */
  @Override
  public Integer getMaxWebCalPeriod() {
    return maxWebCalPeriod;
  }

  @Override
  public void setDirectoryBrowsingDisallowed(final boolean val) {
    directoryBrowsingDisallowed = val;
  }

  @Override
  public boolean getDirectoryBrowsingDisallowed() {
    return directoryBrowsingDisallowed;
  }

  @Override
  public void setMaxYears(final int val) {
    maxYears = val;
  }

  @Override
  public int getMaxYears() {
    return maxYears;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.newLine();
    ts.append("directoryBrowsingDisallowed", getDirectoryBrowsingDisallowed());

    ts.newLine();
    ts.append("name", getName());

    ts.newLine();

    ts.newLine();
    ts.append("defaultUserViewName", getDefaultUserViewName());

    ts.newLine();
    ts.append("maxPublicDescriptionLength", getMaxPublicDescriptionLength());
    ts.append("maxUserDescriptionLength", getMaxUserDescriptionLength());
    ts.append("maxUserEntitySize", getMaxUserEntitySize());
    ts.append("defaultUserQuota", getDefaultUserQuota());

    ts.append("maxInstances", getMaxInstances());
    ts.append("maxYears", getMaxYears());

    return ts.toString();
  }

  @Override
  public AuthProperties cloneIt() {
    AuthPropertiesImpl clone = new AuthPropertiesImpl();

    clone.setName(getName());

    clone.setDefaultUserViewName(getDefaultUserViewName());
    clone.setDefaultUserHour24(getDefaultUserHour24());

    clone.setDefaultChangesNotifications(getDefaultChangesNotifications());

    clone.setMaxPublicDescriptionLength(getMaxPublicDescriptionLength());
    clone.setMaxUserDescriptionLength(getMaxUserDescriptionLength());
    clone.setMaxUserEntitySize(getMaxUserEntitySize());
    clone.setDefaultUserQuota(getDefaultUserQuota());

    clone.setMaxInstances(getMaxInstances());
    clone.setMaxYears(getMaxYears());

    return clone;
  }
}
