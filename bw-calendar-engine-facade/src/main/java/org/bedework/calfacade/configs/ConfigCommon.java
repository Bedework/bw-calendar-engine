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
package org.bedework.calfacade.configs;

/** Common configuration properties for the clients
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class ConfigCommon extends DbConfig {
  private String logPrefix;

  /** */
  public static final String appTypeWebadmin = "webadmin";
  /** */
  public static final String appTypeWebsubmit = "websubmit";
  /** */
  public static final String appTypeWebpublic = "webpublic";
  /** */
  public static final String appTypeFeeder = "feeder";
  /** */
  public static final String appTypeWebuser = "webuser";

  private String appType;

  private boolean publicAdmin;

  private boolean guestMode;

  /** Constructor
   *
   */
  public ConfigCommon() {
  }

  /**
   * @param val
   */
  public void setLogPrefix(final String val) {
    logPrefix = val;
  }

  /**
   * @return String
   */
  public String getLogPrefix() {
    return logPrefix;
  }

  /**
   * @param val
   */
  public void setAppType(final String val) {
    appType = val;
  }

  /**
   * @return String
   */
  public String getAppType() {
    return appType;
  }

  /** True for a public admin client.
   *
   * @param val
   */
  public void setPublicAdmin(final boolean val) {
    publicAdmin = val;
  }

  /**
   * @return boolean
   */
  public boolean getPublicAdmin() {
    return publicAdmin;
  }

  /** True for a guest mode (non-auth) client.
   *
   * @param val
   */
  public void setGuestMode(final boolean val) {
    guestMode = val;
  }

  /**
   * @return boolean
   */
  public boolean getGuestMode() {
    return guestMode;
  }

  /** Copy this object to val.
   *
   * @param val
   */
  public void copyTo(final ConfigCommon val) {
    super.copyTo(val);
    val.setLogPrefix(getLogPrefix());
    val.setAppType(getAppType());
    val.setPublicAdmin(getPublicAdmin());
    val.setGuestMode(getGuestMode());
  }

  @Override
  public Object clone() {
    ConfigCommon conf = new ConfigCommon();

    copyTo(conf);

    return conf;
  }
}
