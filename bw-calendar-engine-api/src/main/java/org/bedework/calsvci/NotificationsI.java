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
package org.bedework.calsvci;

import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.List;

import javax.xml.namespace.QName;

/** Interface for handling bedework notifications - including CalDAV user
 * notification collections.
 *
 * @author Mike Douglass
 *
 */
public interface NotificationsI extends Serializable {
  /** Add the given notification to the notification collection for the
   * indicated principal.
   *
   * @param pr - target
   * @param val
   * @return false for unknown CU
   * @throws CalFacadeException
   */
  public boolean send(BwPrincipal pr,
                      NotificationType val) throws CalFacadeException;

  /** Add the given notification to the notification collection for the
   * current principal. Caller should check for notifications enabled if
   * appropriate.
   *
   * @param val
   * @return false for no notification or collection
   * @throws CalFacadeException
   */
  public boolean add(NotificationType val) throws CalFacadeException;

  /** Update the given notification
   *
   * @param val
   * @return false for no notification or collection
   * @throws CalFacadeException
   */
  public boolean update(NotificationType val) throws CalFacadeException;

  /** Find a notification in the notification collection for the
   * current principal with the given name.
   *
   * @param name - of the notification
   * @return null for no notification or the notification with that name
   * @throws CalFacadeException
   */
  public NotificationType find(String name) throws CalFacadeException;

  /** Remove the given notification from the notification collection for the
   * indicated calendar user.
   *
   * @param pr - target
   * @param val
   * @throws CalFacadeException
   */
  public void remove(BwPrincipal pr,
                     NotificationType val) throws CalFacadeException;

  /** Remove the given notification from the notification collection for the
   * current calendar user.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void remove(NotificationType val) throws CalFacadeException;

  /**
   * @return all notifications for this user
   * @throws CalFacadeException
   */
  public List<NotificationType> getAll() throws CalFacadeException;

  /**
   * @param type of notification (null for all)
   * @return matching notifications for this user - never null
   * @throws CalFacadeException
   */
  public List<NotificationType> getMatching(QName type) throws CalFacadeException;

  /**
   * @param pr principal
   * @param type of notification (null for all)
   * @return notifications for the given principal of the given type
   * @throws CalFacadeException
   */
  public List<NotificationType> getMatching(BwPrincipal pr,
                                            QName type) throws CalFacadeException;

  /**
   * @param href principal href
   * @param type of notification (null for all)
   * @return notifications for the given principal of the given type
   * @throws CalFacadeException
   */
  public List<NotificationType> getMatching(String href,
                                            QName type) throws CalFacadeException;
}
