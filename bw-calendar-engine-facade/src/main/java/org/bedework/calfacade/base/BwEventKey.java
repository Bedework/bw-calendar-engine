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
package org.bedework.calfacade.base;

import java.io.Serializable;

/** Key to an event or events.
 *
 * @author douglm
 *
 */
public class BwEventKey implements Serializable {
  private String calPath;

  private String guid;

  private String recurrenceId;

  private String name;

  private Boolean recurring;

  /** Constructor
   *
   * @param calPath
   * @param guid
   * @param recurrenceId
   * @param name
   * @param recurring  iformational
   */
  public BwEventKey(String calPath,
                    String guid, String recurrenceId,
                    String name,
                    Boolean recurring) {
    this.calPath = calPath;
    this.guid = guid;
    this.recurrenceId = recurrenceId;
    this.name = name;
    this.recurring = recurring;
  }

  /** Set the event's calendar path
   *
   * @param val    String event's name
   */
  public void setCalPath(String val) {
    calPath = val;
  }

  /** Get the event's calendar path.
   *
   * @return String   event's name
   */
  public String getCalPath() {
    return calPath;
  }

  /** Set the event's guid
   *
   * @param val    String event's guid
   */
  public void setGuid(String val) {
    guid = val;
  }

  /** Get the event's guid
   *
   * @return String   event's guid
   */
  public String getGuid() {
    return guid;
  }

  /** Set the event's recurrence id
   *
   *  @param val     recurrence id
   */
  public void setRecurrenceId(String val) {
     recurrenceId = val;
  }

  /** Get the event's recurrence id
   *
   * @return the event's recurrence id
   */
  public String getRecurrenceId() {
    return recurrenceId;
  }

  /** Set the event's name
   *
   * @param val    String event's name
   */
  public void setName(String val) {
    name = val;
  }

  /** Get the event's name.
   *
   * @return String   event's name
   */
  public String getName() {
    return name;
  }

  /**
   * @param val
   */
  public void setRecurring(Boolean val) {
    recurring = val;
  }

  /**
   * @return Boolean true if a recurring event
   */
  public Boolean getRecurring() {
    return recurring;
  }

  /**
   * @return String
   */
  public String toStringSegment() {
    StringBuffer sb = new StringBuffer();

    sb.append("calPath=");
    sb.append(getCalPath());
    sb.append("guid=");
    sb.append(getGuid());
    sb.append("recurrenceId=");
    sb.append(getRecurrenceId());
    sb.append("name=");
    sb.append(getName());

    return sb.toString();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("BwEventKey{");
    sb.append(toStringSegment());
    sb.append("}");

    return sb.toString();
  }
}
