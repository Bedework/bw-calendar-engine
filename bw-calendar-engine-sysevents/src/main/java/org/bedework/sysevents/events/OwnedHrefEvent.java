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
public class OwnedHrefEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  private String authPrincipalHref;

  private String ownerHref;

  private String href;

  private boolean shared;

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param shared
   */
  public OwnedHrefEvent(final SysCode code,
                         final String authPrincipalHref,
                         final String ownerHref,
                         final String href,
                         final boolean shared) {
    super(code);

    this.authPrincipalHref = authPrincipalHref;
    this.ownerHref = ownerHref;

    this.href = href;
    this.shared = shared;
  }

  /** Currently authenticated principal
   *
   * @return String
   */
  public String getAuthPrincipalHref() {
    return authPrincipalHref;
  }

  /**
   * @return String
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /** Get the href
   *
   * @return String   href
   */
  public String getHref() {
    return href;
  }

  /** Get the shared flag. True if the entity is in a shared collection or if the
   * modified collection is shared
   *
   * @return boolean shared
   */
  public boolean getShared() {
    return shared;
  }

  /** Add our stuff to the ToString object
  *
  * @param ts    ToString for result
  */
 @Override
 public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("authPrincipalHref", getAuthPrincipalHref());
    ts.append("ownerHref", getOwnerHref());
    ts.append("href", getHref());
    ts.append("shared", getShared());
  }
}
