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
import org.bedework.sysevents.events.SysEventBase.SysCode;

import java.util.Properties;

/** This is the implementation of a notifications listener.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class SysEventListenerImpl extends SysEventListener {
  private String actionClassName;

  private SysEventActionClass actionObject;

  protected SysCode sysCode;

  protected Properties props;

  /**
   * @param code
   */
  public SysEventListenerImpl(SysCode code) {
    sysCode = code;
  }

  /**
   * @param code
   * @param actionObject
   * @throws NotificationException
   */
  public SysEventListenerImpl(SysCode code,
                              SysEventActionClass actionObject) throws NotificationException {
    sysCode = code;
    setActionObject(actionObject);
  }

  /* (non-Javadoc)
   * @see org.bedework.sysevents.listeners.SysEventListener#setActionClassName(java.lang.String)
   */
  public void setActionClassName(String className) throws NotificationException {
    actionClassName = className;
  }

  /* (non-Javadoc)
   * @see org.bedework.sysevents.listeners.SysEventListener#setActionObject(org.bedework.sysevents.listeners.SysEventActionClass)
   */
  public void setActionObject(SysEventActionClass actionObject) throws NotificationException {
    this.actionObject = actionObject;
    if (actionObject ==  null) {
      actionClassName = null;
    } else {
      setActionClassName(actionObject.getClass().getName());
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.sysevents.listeners.SysEventListener#action(org.bedework.sysevents.events.SysEvent)
   */
  public void action(SysEvent ev) throws NotificationException {
    if (actionObject == null) {
      if (actionClassName == null) {
        throw new NotificationException(NotificationException.noActionClassName);
      }

      Object o = null;
      try {
        o = Class.forName(actionClassName).newInstance();
      } catch (Throwable t) {
      }

      if (o == null) {
        throw new NotificationException(NotificationException.noActionClass,
                                     actionClassName);
      }

      if (!(o instanceof SysEventActionClass)) {
        throw new NotificationException(NotificationException.notActionClass,
                                     actionClassName);
      }

      setActionObject((SysEventActionClass)o);
    }

    actionObject.action(ev);
  }

  /* (non-Javadoc)
   * @see org.bedework.sysevents.listeners.SysEventListener#toXml()
   */
  public String toXml() throws NotificationException {
    // XXXX
    return null;
  }

  /* ====================================================================
   *             Implementations which look for particular objects
   * ==================================================================== */

}
