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
package org.bedework.sysevents.events;

/** An event which carries a notification message as an XML string. This message
 * may be targeted at a specific user by setting the targetPrincipalHref.
 *
 * @author douglm
 *
 */
public interface NotificationEvent {
  /** Get the notification text
   *
   * @return XML String or null
   */
  String getNotification();

  /** Get any targeted principal
   *
   * @return principal href or null
   */
  String getTargetPrincipalHref();
}
