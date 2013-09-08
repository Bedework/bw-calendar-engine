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

import org.bedework.util.jmx.ConfBaseMBean;
import org.bedework.util.jmx.MBeanInfo;

import java.util.List;

/** Service mbean for ischedule
 *
 * @author douglm
 *
 */
public interface BwHostMBean extends ConfBaseMBean {
  /**
   * @return String current hostname
   */
  String getHostname();

  /** Set the port for the current entry.
   *
   * @param val
   */
  void setPort(int val);

  /** Get the port for the current entry.
   *
   * @return port
   */
  int getPort();

  /** Set the secure flag for the current entry.
   *
   * @param val
   */
  void setSecure(boolean val);

  /** Get the secure flag for the current entry.
   *
   * @return secure flag
   */
  boolean getSecure();

  /** Set the iSchedule url for the current entry.
   *
   * @param val
   */
  void setIScheduleUrl(String val);

  /** Get the iSchedule url for the current entry.
   *
   * @return iSchedule url
   */
  String getIScheduleUrl();

  /** Set the iSchedule principal for the current entry.
   *
   * @param val
   */
  void setISchedulePrincipal(String val);

  /** Get the iSchedule principal for the current entry.
   *
   * @return iSchedule principal
   */
  String getISchedulePrincipal();

  /** Set the iSchedule pw for the current entry.
   *
   * @param val
   */
  void setISchedulePw(String val);

  /** Get the iSchedule pw for the current entry.
   *
   * @return iSchedule pw
   */
  String getISchedulePw();

  /** Add a dkim public key
   *
   * @param selector
   * @param val
   */
  void addDkimPublicKey(@MBeanInfo("selector") final String selector,
                        @MBeanInfo("val") final String val);

  /** Get a dkim public key
   *
   * @param selector
   * @return value or null
   */
  @MBeanInfo("Non-null if we have a specially delivered public key for the given selector which we use for dkim")
  String getDkimPublicKey(@MBeanInfo("selector") final String selector);

  /** Remove a dkim public key
   *
   * @param selector
   */
  void removeDkimPublicKey(@MBeanInfo("selector") final String selector);

  /** Set a dkim public key
   *
   * @param selector
   * @param val
   */
  void setDkimPublicKey(@MBeanInfo("selector") final String selector,
                        @MBeanInfo("val") final String val);

  /**
   *
   * @return String val
   */
  List<String> getDkimPublicKeys();

  /**
   *
   *  @param val    boolean
   */
  void setIScheduleUsePublicKey(final boolean val);

  /** True if we delivered our public key for use for dkim
   *
   * @return String
   */
  @MBeanInfo("True if we delivered our public key for use for dkim")
  boolean getIScheduleUsePublicKey();

  /* ================================= Operations =========================== */

  /**
   * @return host information
   */
  @MBeanInfo("Display the host configuration")
  String display();

  /** (Re)load the configurations
   *
   * @return status
   */
  @Override
  @MBeanInfo("Save the host configuration")
  String saveConfig();
}
