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
package org.bedework.sysevents.events.publicAdmin;

import org.bedework.sysevents.events.OwnedHrefEvent;
import org.bedework.util.misc.ToString;

/** Signal an update to an entity. The changes provided are an XML string which
 * follows the Apple spec for notifications adn may be null.
 *
 * @author douglm
 */
public class EntitySuggestedResponseEvent extends OwnedHrefEvent {
  private static final long serialVersionUID = 1L;

  private final String targetPrincipalHref;

  private final String rid;

  private final boolean accepted;

  /**
   * @param code the system event code
   * @param authPrincipalHref authenticated principal
   * @param ownerHref principal href of the owner
   * @param href of the entity
   * @param rid recurrence id
   * @param targetPrincipalHref who it's suggested to - who is responding
   * @param accepted - false for rejected
   */
  public EntitySuggestedResponseEvent(final SysCode code,
                                      final String authPrincipalHref,
                                      final String ownerHref,
                                      final String href,
                                      final String rid,
                                      final String targetPrincipalHref,
                                      final boolean accepted) {
    super(code, authPrincipalHref, ownerHref, href, false);

    this.targetPrincipalHref = targetPrincipalHref;
    this.rid = rid;
    this.accepted = accepted;
  }

  public String getTargetPrincipalHref() {
    return targetPrincipalHref;
  }

  /**
   * @return recurrence id
   */
  public String getRecurrenceId() {
    return rid;
  }

  /**
   * @return true for accepted
   */
  public boolean getAccepted() {
    return accepted;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts    ToString for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("targetPrincipalHref", getTargetPrincipalHref());

    if (getRecurrenceId() != null) {
      ts.append("recurrenceId", getRecurrenceId());
    }
    ts.append("accepted", getAccepted());
  }
}
