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

import org.bedework.util.misc.ToString;

/** Signal an update to an entity. The changes provided are an XML string which
 * follows the Apple spec for notifications adn may be null.
 *
 * @author douglm
 */
public class EntityUpdateEvent extends OwnedHrefEvent implements NotificationEvent {
  private static final long serialVersionUID = 1L;

  private String notification;

  private String targetPrincipalHref;

  private String uid;
  private String rid;

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href of the entity
   * @param shared
   * @param uid
   * @param rid
   * @param notification
   * @param targetPrincipalHref
   */
  public EntityUpdateEvent(final SysCode code,
                           final String authPrincipalHref,
                           final String ownerHref,
                           final String href,
                           final boolean shared,
                           final String uid,
                           final String rid,
                           final String notification,
                           final String targetPrincipalHref) {
    super(code, authPrincipalHref, ownerHref, href, shared);

    this.notification = notification;
    this.targetPrincipalHref = targetPrincipalHref;
    this.uid = uid;
    this.rid = rid;
  }

  @Override
  public String getNotification() {
    return notification;
  }

  @Override
  public String getTargetPrincipalHref() {
    return targetPrincipalHref;
  }

  /** Get the uid
   *
   * @return String   uid
   */
  public String getUid() {
    return uid;
  }

  /**
   * @return String
   */
  public String getRecurrenceId() {
    return rid;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts    ToString for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("notification", getNotification());
    ts.append("targetPrincipalHref", getTargetPrincipalHref());

    ts.append("uid", getUid());
    if (getRecurrenceId() != null) {
      ts.append("recurrenceId", getRecurrenceId());
    }
  }
}
