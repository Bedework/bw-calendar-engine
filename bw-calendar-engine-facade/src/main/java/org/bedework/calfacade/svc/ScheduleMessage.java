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
package org.bedework.calfacade.svc;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

/** Messages relating to automatic and implicit scheduling. Each message refers
 * to an event placed in the inbox or outbox.
 *
 * <p>Inbox messages are inbound and the result of some scheduling activity
 * initiated by another user.
 *
 * <p>Outbox messages are message to be sent to a remote address, either through
 * iSchedule or some other process like iMip.
 *
 * @author Mike Douglass douglm  bedework.edu
 */
@Dump(elementName="autoSchedule", keyFields={"timestamp", "sequence"})
public class ScheduleMessage extends BwDbentity<ScheduleMessage> {
  /** UTC datetime */
  private String timestamp;

  /** Ensure uniqueness - lastmod only down to second.
   */
  private int sequence;

  /** UTC datetime */
  private String lastProcessed;

  private boolean inBox;

  private String principalHref;

  private String eventName;

  private String rid;

  /** Constructor
   *
   */
  public ScheduleMessage() {
    super();
    updateTimestamp();
  }

  /** Constructor
   *
   * @param inBox
   * @param principalHref
   * @param eventName
   * @param rid
   */
  public ScheduleMessage(final boolean inBox,
                         final String principalHref,
                         final String eventName,
                         final String rid) {
    super();

    this.inBox = inBox;
    this.principalHref = principalHref;
    this.eventName = eventName;
    this.rid = rid;
    updateTimestamp();
    setLastProcessed(getTimestamp());
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /**
   * @param val
   */
  public void setTimestamp(final String val) {
    timestamp = val;
  }

  /**
   * @return String lastmod
   */
  public String getTimestamp() {
    return timestamp;
  }

  /** Set the sequence
   *
   * @param val    sequence number
   */
  public void setSequence(final int val) {
    sequence = val;
  }

  /** Get the sequence
   *
   * @return int    the sequence
   */
  public int getSequence() {
    return sequence;
  }

  /**
   * @param val
   */
  public void setLastProcessed(final String val) {
    lastProcessed = val;
  }

  /**
   * @return String last processed
   */
  public String getLastProcessed() {
    return lastProcessed;
  }

  /**
   * @param val    inBox flag
   */
  public void setInBox(final boolean val) {
    inBox = val;
  }

  /**
   * @return true for inbox event
   */
  public boolean getInBox() {
    return inBox;
  }

  /**
   * @param val
   */
  public void setPrincipalHref(final String val) {
    principalHref = val;
  }

  /**
   * @return  String principal reference
   */
  public String getPrincipalHref() {
    return principalHref;
  }

  /** Set the name
   *
   * @param val    String name
   */
  public void setEventName(final String val) {
    eventName = val;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getEventName() {
    return eventName;
  }

  /** Set the rid
   *
   * @param val    String rid
   */
  public void setRid(final String val) {
    rid = val;
  }

  /** Get the rid
   *
   * @return String   rid
   */
  public String getRid() {
    return rid;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Update last mod fields
   */
  public void updateTimestamp() {
    setTimestamp(Util.icalUTCTimestamp());
    setSequence(getSequence() + 1);
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  /** Comapre this and another object
   *
   * @param  that    object to compare.
   * @return int -1, 0, 1
   */
  @Override
  public int compareTo(final ScheduleMessage that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return 1;
    }

    int res = getTimestamp().compareTo(that.getTimestamp());

    if (res != 0) {
      return res;
    }

    if (getSequence() < that.getSequence()) {
      return -1;
    }

    if (getSequence() > that.getSequence()) {
      return 1;
    }

    return 0;
  }

  @Override
  public int hashCode() {
    return getTimestamp().hashCode() + getSequence();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    ts.newLine();
    ts.append("timestamp", getTimestamp());
    ts.append("sequence", getSequence());
    ts.append("lastProcessed", getLastProcessed());

    ts.newLine();
    ts.append("inBox", getInBox());
    ts.append("  principalHref", getPrincipalHref());
    ts.append("eventName", getEventName());
    ts.append("rid", getRid());

    return ts.toString();
  }
}
