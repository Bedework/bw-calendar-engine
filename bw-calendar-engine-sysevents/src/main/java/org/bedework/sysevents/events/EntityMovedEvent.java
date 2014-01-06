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

/** A calendar (collection) change event. The fields define what changed together
 * with the syscode.<ul>
 * <li>calPath defines the collection</li>
 *
 * @author Mike Douglass
 */
public class EntityMovedEvent extends OwnedHrefEvent {
  private static final long serialVersionUID = 1L;

  private String oldHref;

  private boolean oldShared;

  /** Constructor
   *
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   * @param indexed - true if already indexed
   * @param oldHref
   * @param oldShared
   */
  public EntityMovedEvent(final SysCode code,
                          final String authPrincipalHref,
                          final String ownerHref,
                          final String href,
                          final boolean shared,
                          final boolean indexed,
                          final String oldHref,
                          final boolean oldShared) {
    super(code, authPrincipalHref, ownerHref, href, shared);

    this.oldHref = oldHref;
    this.oldShared = oldShared;

    setIndexed(indexed);
  }

  /**
   * @return old Href
   */
  public String getOldHref() {
    return oldHref;
  }

  /** Get the oldShared flag. True if the entity was in a shared collection
   *
   * @return boolean shared
   */
  public boolean getOldShared() {
    return oldShared;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts for result
   */
  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("oldHref", getOldHref());
    ts.append("oldShared", getOldShared());
  }
}
