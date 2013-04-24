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

/** Signal an event concerning an entity
 * @author douglm
 *
 */
public class EntityEvent extends NamedEvent {
  private static final long serialVersionUID = 1L;

  private String ownerHref;

  private String uid;
  private String rid;
  private String colPath;

  /**
   * @param code
   * @param ownerHref
   * @param name
   * @param uid
   * @param rid
   * @param colPath path to parent
   */
  public EntityEvent(final SysCode code,
                           final String ownerHref,
                           final String name,
                           final String uid,
                           final String rid,
                           final String colPath) {
    super(code, name);

    this.ownerHref = ownerHref;

    this.uid = uid;
    this.rid = rid;
    this.colPath = colPath;
  }

  /**
   * @return String
   */
  public String getOwnerHref() {
    return ownerHref;
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

  /** Get the collection path
   *
   * @return String   path
   */
  public String getColPath() {
    return colPath;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    sb.append(", ownerHref=");
    sb.append(getOwnerHref());

    sb.append(", uid=");
    sb.append(getUid());

    if (getRecurrenceId() != null) {
      sb.append(", recurrenceId=");
      sb.append(getRecurrenceId());
    }
    sb.append(", colPath=");
    sb.append(getColPath());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("EntityChangeEvent{");

    toStringSegment(sb);


    sb.append("}");

    return sb.toString();
  }
}
