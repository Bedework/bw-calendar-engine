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

import edu.rpi.cmt.config.ConfigurationStore;
import edu.rpi.cmt.config.ConfigurationType;
import edu.rpi.cmt.jmx.ConfBase;

import java.util.Arrays;
import java.util.List;

/**
 * @author douglm
 *
 */
public class BwHost extends ConfBase implements BwHostMBean {
  private HostInfo info;

  /**
   * @param configStore
   * @param serviceName
   * @param info
   */
  public BwHost(final ConfigurationStore configStore,
                final String serviceName,
                final HostInfo info) {
    super(serviceName);
    this.info = info;
    setConfigName(info.getHostname());
    setStore(configStore);
  }

  @Override
  public ConfigurationType getConfigObject() {
    return info.getConfig();
  }

  /**
   * @return the host info
   */
  public HostInfo getInfo() {
    return info;
  }

  /** Get the hostname for the current entry.
   *
   * @return hostname
   */
  @Override
  public String getHostname() {
    if (info == null) {
      return "No current entry";
    }

    return info.getHostname();
  }

  @Override
  public void setPort(final int val) {
    if (info == null) {
      return;
    }

    info.setPort(val);
  }

  /** Get the port for the current entry.
   *
   * @return port
   */
  @Override
  public int getPort() {
    if (info == null) {
      return -1;
    }

    return info.getPort();
  }

  /** Set the secure flag for the current entry.
   *
   * @param val
   */
  @Override
  public void setSecure(final boolean val) {
    if (info == null) {
      return;
    }

    info.setSecure(val);
  }

  /** Get the secure flag for the current entry.
   *
   * @return secure flag
   */
  @Override
  public boolean getSecure() {
    if (info == null) {
      return false;
    }

    return info.getSecure();
  }

  /** Set the iSchedule url for the current entry.
   *
   * @param val
   */
  @Override
  public void setIScheduleUrl(final String val) {
    if (info == null) {
      return;
    }

    info.setIScheduleUrl(val);
  }

  /** Get the iSchedule url for the current entry.
   *
   * @return iSchedule url
   */
  @Override
  public String getIScheduleUrl() {
    if (info == null) {
      return "No current entry";
    }

    return info.getIScheduleUrl();
  }

  @Override
  public void addDkimPublicKey(final String selector,
                               final String val) {
    if (info == null) {
      return;
    }

    info.addDkimPublicKey(selector, val);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public String getDkimPublicKey(final String selector) {
    if (info == null) {
      return "No current entry";
    }

    return info.getDkimPublicKey(selector);
  }

  @Override
  public void removeDkimPublicKey(final String selector) {
    if (info == null) {
      return;
    }

    info.removeDkimPublicKey(selector);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public void setDkimPublicKey(final String selector,
                               final String val) {
    if (info == null) {
      return;
    }

    info.setDkimPublicKey(selector, val);
    BwHosts.refreshStoredKeys();
  }

  @Override
  public List<String> getDkimPublicKeys() {
    if (info == null) {
      String[] msg = {"No current entry"};
      return Arrays.asList(msg);
    }

    return info.getDkimPublicKeys();
  }

  @Override
  public void setIScheduleUsePublicKey(final boolean val) {
    if (info == null) {
      return;
    }

    info.setIScheduleUsePublicKey(val);
  }

  @Override
  public boolean getIScheduleUsePublicKey() {
    if (info == null) {
      return false;
    }

    return info.getIScheduleUsePublicKey();
  }

  /** Set the iSchedule principal for the current entry.
   *
   * @param val
   */
  @Override
  public void setISchedulePrincipal(final String val) {
    if (info == null) {
      return;
    }

    info.setISchedulePrincipal(val);
  }

  /** Get the iSchedule principal for the current entry.
   *
   * @return iSchedule principal
   */
  @Override
  public String getISchedulePrincipal() {
    if (info == null) {
      return "No current entry";
    }

    return info.getISchedulePrincipal();
  }

  /** Set the iSchedule pw for the current entry.
   *
   * @param val
   */
  @Override
  public void setISchedulePw(final String val) {
    if (info == null) {
      return;
    }

    info.setIScheduleCredentials(val);
  }

  /** Get the iSchedule pw for the current entry.
   *
   * @return iSchedule pw
   */
  @Override
  public String getISchedulePw() {
    if (info == null) {
      return "No current entry";
    }

    return info.getIScheduleCredentials();
  }

  /* ================================= Operations =========================== */

  @Override
  public String display() {
    return info.toString();
  }
}
