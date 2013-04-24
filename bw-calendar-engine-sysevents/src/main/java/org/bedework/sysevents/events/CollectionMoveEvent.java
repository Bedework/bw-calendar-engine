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
 * <li>calPath defines the collection</li>
 *
 * @author Mike Douglass
 */
public class CollectionMoveEvent extends NamedEvent {
  private static final long serialVersionUID = 1L;

  private String oldColPath;

  private String colPath;

  /** Constructor
   *
   * @param code
   * @param name
   * @param oldColPath old parent
   * @param colPath new parent
   *
   * @deprecated
   */
  @Deprecated
  public CollectionMoveEvent(final SysCode code,
                             final String name,
                             final String oldColPath,
                             final String colPath) {
    super(code, name);
    this.oldColPath = oldColPath;
    this.colPath = colPath;
  }

  /**
   * @return old collection path
   */
  public String getOldColPath() {
    return oldColPath;
  }

  /**
   * @return collection path
   */
  public String getColPath() {
    return colPath;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("CollectionChangeEventProperties{");

    super.toStringSegment(sb);
    sb.append(", \n");

    sb.append("oldColPath=");
    sb.append(getOldColPath());
    sb.append("colPath=");
    sb.append(getColPath());

    sb.append("}");

    return sb.toString();
  }
}
