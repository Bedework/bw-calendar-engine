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
package org.bedework.sysevents.listeners;

import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;

/** Listener class which logs system events sent via JMS.
 *
 * @author Mike Douglass
 */
public class LogListener extends JmsSysEventListener {
  /**
   */
  public LogListener() {
  }

  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      getLogger().info(ev.toString());
    } catch (final Throwable t) {
      throw new NotificationException(t);
    }
  }

  void listen() throws NotificationException {
    try (final JmsSysEventListener ignored =
                 open(syseventsLogQueueName)) {
      open(syseventsLogQueueName);

      process(false);
    }
  }
}
