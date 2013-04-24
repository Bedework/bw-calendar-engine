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
package org.bedework.sysevents;

import org.bedework.sysevents.events.SysEventBase;

/**
 * Return a single instance of a notifications handler.
 * 
 * @author Mike Douglass
 */
public class NotificationsHandlerFactory {
  private static volatile NotificationsHandler handler;

  private static volatile Object synchit = new Object();

  /**
   * Return a handler for the system event
   * 
   * @return NotificationsHandler
   * @throws NotificationException
   */
  private static NotificationsHandler getHandler() throws NotificationException {
    if (handler != null) {
      return handler;
    }

    synchronized (synchit) {
      handler = new JmsNotificationsHandlerImpl();
    }

    return handler;
  }

  /**
   * Called to notify container that an event occurred. In general this should
   * not be called directly as consumers may receive the messages immediately,
   * perhaps before the referenced data has been written.
   * 
   * @param ev
   * @throws NotificationException
   */
  public static void post(final SysEventBase ev) throws NotificationException {
    getHandler().post(ev);
  }
}
