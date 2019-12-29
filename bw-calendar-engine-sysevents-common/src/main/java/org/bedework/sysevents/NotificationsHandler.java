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
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

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
public abstract class NotificationsHandler implements Logged {
  /**
   * Called to notify container that an event occurred.
   * 
   * @param ev system event
   * @throws RuntimeException on fatal error
   */
  public abstract void post(SysEventBase ev);

  /**
   * Register a listener.
   * 
   * @param l the listener
   * @param persistent
   *          true if this listener is to be stored in the database and
   *          reregistered at each system startup.
   * @throws RuntimeException on fatal error
   */
  public abstract void registerListener(SysEventListener l,
                                        boolean persistent);

  /**
   * Remove a listener. If persistent it will be deleted from the database.
   * 
   * @param l the listener
   * @throws RuntimeException on fatal error
   */
  public abstract void removeListener(SysEventListener l);

  /**
   * Does its best to close.
   *
   */
  public abstract void close();

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
