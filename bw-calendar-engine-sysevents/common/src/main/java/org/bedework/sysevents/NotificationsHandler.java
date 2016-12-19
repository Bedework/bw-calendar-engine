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
import org.bedework.sysevents.listeners.SysEventListener;

/**
 * Handler which may be called to notify the system that something changed.
 * <p>
 * Some implementations may be able to call immediately changes take effect, for
 * example the bedework hibernate implementation can call at the point a
 * calendar is changed. Others may have to poll to determine if something has
 * changed.
 * 
 * @author Mike Douglass
 */
public abstract class NotificationsHandler {
  /**
   * Called to notify container that an event occurred.
   * 
   * @param ev
   * @throws NotificationException
   */
  public abstract void post(SysEventBase ev) throws NotificationException;

  /**
   * Register a listener.
   * 
   * @param l
   * @param persistent
   *          true if this listener is to be stored in the database and
   *          reregistered at each system startup.
   * @throws NotificationException
   */
  public abstract void registerListener(SysEventListener l, boolean persistent)
                                                                               throws NotificationException;

  /**
   * Remove a listener. If persistent it will be deleted from the database.
   * 
   * @param l
   * @throws NotificationException
   */
  public abstract void removeListener(SysEventListener l)
                                                         throws NotificationException;
}
