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

import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

/** These are the system properties that the server needs to know about, either
 * because it needs to apply these limits or just to report them to clients.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "notification-properties",
          type = "org.bedework.calfacade.configs.NotificationProperties")
public class NotificationPropertiesImpl
        extends ConfigBase<NotificationPropertiesImpl>
        implements NotificationProperties {
  private boolean outboundEnabled;
  private String notifierId;
  private String notifierToken;
  private String notificationDirHref;

  @Override
  public void setOutboundEnabled(final boolean val) {
    outboundEnabled = val;
  }

  @Override
  public boolean getOutboundEnabled() {
    return outboundEnabled;
  }

  @Override
  public void setNotifierId(final String val) {
    notifierId = val;
  }

  @Override
  public String getNotifierId() {
    return notifierId;
  }

  @Override
  public void setNotifierToken(final String val) {
    notifierToken = val;
  }

  @Override
  public String getNotifierToken() {
    return notifierToken;
  }

  @Override
  public void setNotificationDirHref(final String val) {
    notificationDirHref = val;
  }

  @Override
  public String getNotificationDirHref() {
    return notificationDirHref;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.newLine();
    ts.append("name", getName());
    ts.append("outboundEnabled", getOutboundEnabled());
    ts.append("notifierId", getNotifierId());
    ts.append("notifierToken", getNotifierToken());
    ts.append("notificationDirHref", getNotificationDirHref());

    return ts.toString();
  }

  public NotificationProperties cloneIt() {
    final NotificationPropertiesImpl clone = new NotificationPropertiesImpl();

    clone.setName(getName());
    clone.setNotifierId(getNotifierId());
    clone.setNotifierToken(getNotifierToken());


    return clone;
  }
}
