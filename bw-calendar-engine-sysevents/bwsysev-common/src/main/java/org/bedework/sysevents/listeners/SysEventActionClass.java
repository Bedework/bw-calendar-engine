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

import org.apache.log4j.Logger;

import java.io.Serializable;

/** Listener action class
*
* @author Mike Douglass
*/
public abstract class SysEventActionClass implements Serializable {
  private transient Logger log;

  /** Called whenever a matching event occurs.
   *
   * @param ev
   * @throws NotificationException
   */
  public abstract void action(SysEvent ev) throws NotificationException;

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void trace(String msg) {
    getLogger().debug(msg);
  }

  /* Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}
