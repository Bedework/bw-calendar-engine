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
package org.bedework.calsvc.scheduling.hosts;

import org.bedework.util.config.ConfigurationStore;
import org.bedework.util.jmx.ConfBase;

import java.util.Arrays;
import java.util.List;

/**
 * @author douglm
 *
 */
public class BwHost extends ConfBase<HostInfo> implements BwHostMBean {
  /**
   * @param configStore the store
   * @param serviceName name
   * @param info host info
   */
  public BwHost(final ConfigurationStore configStore,
                final String serviceName,
                final HostInfo info) {
    super(serviceName);
    cfg = info;
    setConfigName(info.getHostname());
    setStore(configStore);
  }

  @Override
  public String loadConfig() {
    return null;
  }

  /** Get the hostname for the current entry.
   *
   * @return hostname
   */
  @Override
  public String getHostname() {
    if (getConfig() == null) {
      return "No current entry";
    }

    return getConfig().getHostname();
  }

  @Override
  public void setPort(final int val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setPort(val);
  }

  /** Get the port for the current entry.
   *
   * @return port
   */
  @Override
  public int getPort() {
    if (getConfig() == null) {
      return -1;
    }

    return getConfig().getPort();
  }

  /** Set the secure flag for the current entry.
   *
   * @param val secureflag
   */
  @Override
  public void setSecure(final boolean val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setSecure(val);
  }

  /** Get the secure flag for the current entry.
   *
   * @return secure flag
   */
  @Override
  public boolean getSecure() {
    if (getConfig() == null) {
      return false;
    }

    return getConfig().getSecure();
  }

  /** Set the iSchedule url for the current entry.
   *
   * @param val ischedule url
   */
  @Override
  public void setIScheduleUrl(final String val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setIScheduleUrl(val);
  }

  /** Get the iSchedule url for the current entry.
   *
   * @return iSchedule url
   */
  @Override
  public String getIScheduleUrl() {
    if (getConfig() == null) {
      return "No current entry";
    }

    return getConfig().getIScheduleUrl();
  }

  @Override
  public void addDkimPublicKey(final String selector,
                               final String val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().addDkimPublicKey(selector, val);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public String getDkimPublicKey(final String selector) {
    if (getConfig() == null) {
      return "No current entry";
    }

    return getConfig().getDkimPublicKey(selector);
  }

  @Override
  public void removeDkimPublicKey(final String selector) {
    if (getConfig() == null) {
      return;
    }

    getConfig().removeDkimPublicKey(selector);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public void setDkimPublicKey(final String selector,
                               final String val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setDkimPublicKey(selector, val);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public List<String> getDkimPublicKeys() {
    if (getConfig() == null) {
      final String[] msg = {"No current entry"};
      return Arrays.asList(msg);
    }

    return getConfig().getDkimPublicKeys();
  }

  @Override
  public void setIScheduleUsePublicKey(final boolean val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setIScheduleUsePublicKey(val);
  }

  @Override
  public boolean getIScheduleUsePublicKey() {
    if (getConfig() == null) {
      return false;
    }

    return getConfig().getIScheduleUsePublicKey();
  }

  /** Set the iSchedule principal for the current entry.
   *
   * @param val ischedule principal
   */
  @Override
  public void setISchedulePrincipal(final String val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setISchedulePrincipal(val);
  }

  /** Get the iSchedule principal for the current entry.
   *
   * @return iSchedule principal
   */
  @Override
  public String getISchedulePrincipal() {
    if (getConfig() == null) {
      return "No current entry";
    }

    return getConfig().getISchedulePrincipal();
  }

  /** Set the iSchedule pw for the current entry.
   *
   * @param val ischedule pw
   */
  @Override
  public void setISchedulePw(final String val) {
    if (getConfig() == null) {
      return;
    }

    getConfig().setIScheduleCredentials(val);
  }

  /** Get the iSchedule pw for the current entry.
   *
   * @return iSchedule pw
   */
  @Override
  public String getISchedulePw() {
    if (getConfig() == null) {
      return "No current entry";
    }

    return getConfig().getIScheduleCredentials();
  }

  /* ================================= Operations =========================== */

  @Override
  public String display() {
    return getConfig().toString();
  }
}
