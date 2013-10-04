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

/** Signal a change to an entity
 * @author douglm
 *
 */
public class EntityDeletedEvent extends OwnedHrefEvent implements NotificationEvent {
  private static final long serialVersionUID = 1L;

  private String notification;

  private String targetPrincipalHref;

  private boolean publick;
  private String type;
  private String uid;
  private String rid;

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param publick
   * @param type of entity
   * @param uid
   * @param rid
   * @param notification
   * @param targetPrincipalHref
   */
  public EntityDeletedEvent(final SysCode code,
                            final String authPrincipalHref,
                            final String ownerHref,
                            final String href,
                            final boolean shared,
                            final boolean publick,
                            final String type,
                            final String uid,
                            final String rid,
                            final String notification,
                            final String targetPrincipalHref) {
    super(code, authPrincipalHref, ownerHref, href, shared);

    this.notification = notification;
    this.targetPrincipalHref = targetPrincipalHref;
    this.publick = publick;
    this.type = type;
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

  /** Get the publick flag
   *
   * @return boolean
   */
  public boolean getPublick() {
    return publick;
  }

  /** Get the type of entity
   *
   * @return String   type
   */
  public String getType() {
    return type;
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

  /** Add our stuff to the ToString builder
   *
   * @param ts    for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("notification", notification);
    ts.append("targetPrincipalHref", getTargetPrincipalHref());
    ts.append("publick", getPublick());
    ts.append("uid", getUid());
    if (getRecurrenceId() != null) {
      ts.append("rid", getRecurrenceId());
    }
  }
}
