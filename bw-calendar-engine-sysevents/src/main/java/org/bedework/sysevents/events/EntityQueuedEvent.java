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

/** Signal queuing of an entity in an inbox or outbox
 *
 * @author douglm
 *
 */
public class EntityQueuedEvent extends NamedEvent {
  private static final long serialVersionUID = 1L;

  private String ownerHref;
  private String rid;
  private boolean inBox;

  /**
   * @param code
   * @param ownerHref
   * @param name
   * @param rid
   * @param inBox
   */
  public EntityQueuedEvent(final SysCode code,
                           final String ownerHref,
                           final String name,
                           final String rid,
                           final boolean inBox) {
    super(code, name);

    this.ownerHref = ownerHref;
    this.rid = rid;
    this.inBox = inBox;
  }

  /**
   *
   * @return String    owner href of the entity
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /**
   * @return String
   */
  public String getRecurrenceId() {
    return rid;
  }

  /** Get the inbox flag
   *
   * @return String   inbox flag
   */
  public boolean getInBox() {
    return inBox;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("EntityQueuedEvent{");

    super.toStringSegment(sb);

    try {
      sb.append(",\n ownerHref=");
      sb.append(getOwnerHref());

      if (getRecurrenceId() != null) {
        sb.append(", recurrenceId=");
        sb.append(getRecurrenceId());
      }
      sb.append(", inBox=");
      sb.append(getInBox());
    } catch (Throwable t) {
      sb.append(t);
    }

    sb.append("}");

    return sb.toString();
  }
}
