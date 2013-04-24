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

/** A calendar (collection) change event. The fields define what changed together
 * with the syscode.<ul>
 * <li>colPath defines the collection</li>
 *
 * @author Mike Douglass
 */
public class CollectionDeletionEvent extends CollectionChangeEvent {
  private static final long serialVersionUID = 1L;

  private boolean publick;
  private String ownerHref;

  /** Constructor
   *
   * @param code
   * @param publick
   * @param ownerHref
   * @param colPath path for deleted collection
   *
   * @deprecated
   */
  @Deprecated
  public CollectionDeletionEvent(final SysCode code,
                                 final boolean publick,
                                 final String ownerHref,
                                 final String colPath) {
    super(code, colPath);

    this.publick = publick;
    this.ownerHref = ownerHref;
  }

  /** Get the publick flag
   *
   * @return boolean
   */
  public boolean getPublick() {
    return publick;
  }

  /**
   * @return String
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    sb.append(", publick=");
    sb.append(getPublick());

    sb.append(", ownerHref=");
    sb.append(getOwnerHref());
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("CollectionDeletionEvent{");

    toStringSegment(sb);

    sb.append("}");

    return sb.toString();
  }
}
