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

/** Signal the approval status of an event.
 *
 * @author douglm
 */
public class EntityApprovalNeededEvent extends OwnedHrefEvent {
  private static final long serialVersionUID = 1L;

  private final String rid;

  private final String comment;

  private final String calsuiteHref;

  /**
   * @param code the system event code
   * @param authPrincipalHref authenticated principal
   * @param ownerHref principal href of the owner (the group)
   * @param href of the entity
   * @param rid recurrence id
   * @param comment a message
   * @param calsuiteHref href of calsuite group
   */
  public EntityApprovalNeededEvent(final SysCode code,
                                   final String authPrincipalHref,
                                   final String ownerHref,
                                   final String href,
                                   final String rid,
                                   final String comment,
                                   final String calsuiteHref) {
    super(code, authPrincipalHref, ownerHref, href, false);

    this.rid = rid;
    this.comment = comment;
    this.calsuiteHref = calsuiteHref;
  }

  /**
   * @return String
   */
  public String getRecurrenceId() {
    return rid;
  }

  /**
   * @return String
   */
  public String getComment() {
    return comment;
  }

  public String getCalsuiteHref() {
    return calsuiteHref;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts    ToString for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    if (getRecurrenceId() != null) {
      ts.append("recurrenceId", getRecurrenceId());
    }
    ts.append("comment", getComment());
    ts.append("calsuiteHref", getCalsuiteHref());
  }
}
