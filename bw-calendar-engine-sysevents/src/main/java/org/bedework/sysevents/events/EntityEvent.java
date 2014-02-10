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

/** Signal an event concerning an entity
 * @author douglm
 *
 */
public class EntityEvent extends OwnedHrefEvent {
  private static final long serialVersionUID = 1L;

  private String rid;

  /**
   * @param code the system event code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param rid
   */
  public EntityEvent(final SysCode code,
                     final String authPrincipalHref,
                     final String ownerHref,
                     final String href,
                     final String rid) {
    super(code, authPrincipalHref, ownerHref, href, false);

    this.rid = rid;
  }

  /**
   * @return String
   */
  public String getRecurrenceId() {
    return rid;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("recurrenceId", getRecurrenceId());
  }
}
