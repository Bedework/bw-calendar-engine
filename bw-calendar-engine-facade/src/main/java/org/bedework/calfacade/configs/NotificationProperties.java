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

import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.MBeanInfo;

/** These are the properties that relate to outbound notifications.
 * These are handled by a separate component.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "notification-properties")
public interface NotificationProperties {
  /** Notification enabled flag
   *
   * @param val    boolean
   */
  void setOutboundEnabled(final boolean val);

  /** Notification system id - null for no service
   *
   * @return String
   */
  @MBeanInfo("Show if outbound notifications enabled")
  boolean getOutboundEnabled();

  /** Notification system id - null for no service
   *
   * @param val    String
   */
  void setNotifierId(final String val);

  /** Notification system id - null for no service
   *
   * @return String
   */
  @MBeanInfo("Notification system id")
  String getNotifierId();

  /** Notification system token - null for no service
   *
   * @param val    String
   */
  void setNotifierToken(final String val);

  /** Notification system id - null for no service
   *
   * @return String
   */
  @MBeanInfo("Notification system token")
  String getNotifierToken();

  /**
   * @param val path to notifications directory
   */
  void setNotificationDirHref(final String val);

  /**
   * @return path to notifications directory
   */
  @MBeanInfo("Path to notifications directory")
  String getNotificationDirHref();
}
