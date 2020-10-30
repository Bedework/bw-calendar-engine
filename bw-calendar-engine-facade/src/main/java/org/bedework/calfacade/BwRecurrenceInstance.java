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

package org.bedework.calfacade;

import org.bedework.util.misc.ToString;

import java.io.Serializable;
import java.util.Comparator;

/**
 * Class to represent an instance of a recurrence. An instance is represented by
 * the date and time of its dtStart and dtEnd together with a recurrence id. We
 * maintain a reference to the master event and a possibly null reference to
 * an overiding event object which contains changes, (annotations etc) to that
 * instance.
 *
 * @author Mike Douglass douglm@bedework.edu
 * @version 1.0
 */
public class BwRecurrenceInstance
        implements Comparable<BwRecurrenceInstance>,
              Comparator<BwRecurrenceInstance>, Serializable {
  /* db version number */
  private int seq;

  /** Start date, either from calculated recurrence or explicitly set for this
   * instance
   */
  private BwDateTime dtstart;

  /** End date, either from calculated recurrence or explicitly set for this
   * instance
   */
  private BwDateTime dtend;

  /** Value of the recurrence id.
   */
  private String recurrenceId;

  /** Reference to the master event.
   */
  private BwEvent master;

  /** An overriding instance
   */
  private BwEventAnnotation override;

  /** Constructor
   *
   */
  public BwRecurrenceInstance() {
  }

  /** Set the seq for this entity
   *
   * @param val    int seq
   */
  public void setSeq(final int val) {
    seq = val;
  }

  /** Get the entity seq
   *
   * @return int    the entity seq
   */
  public int getSeq() {
    return seq;
  }

  /** Set the event's start
   *
   *  @param  val   Event's start
   */
  public void setDtstart(final BwDateTime val) {
    dtstart = val;
  }

  /** Get the event's start
   *
   *  @return The event's start
   */
  public BwDateTime getDtstart() {
    return dtstart;
  }

  /** Set the event's end
   *
   *  @param  val   Event's end
   */
  public void setDtend(final BwDateTime val) {
    dtend = val;
  }

  /** Get the event's end
   *
   *  @return The event's end
   */
  public BwDateTime getDtend() {
    return dtend;
  }

  /** Set the event's recurrence id
   *
   *  @param val     recurrence id
   */
  public void setRecurrenceId(final String val) {
     recurrenceId = val;
  }

  /** Get the event's recurrence id
   *
   * @return the event's recurrence id
   */
  public String getRecurrenceId() {
    return recurrenceId;
  }

  /**
   * @return Returns the master.
   */
  public BwEvent getMaster() {
    return master;
  }

  /**
   * @param val The master to set.
   */
  public void setMaster(final BwEvent val) {
    master = val;
  }

  /**
   * @return Returns the override.
   */
  public BwEventAnnotation getOverride() {
    return override;
  }

  /**
   * @param val The override to set.
   */
  public void setOverride(final BwEventAnnotation val) {
    override = val;
  }

  /* ====================================================================
   *                   Object methods
   *  =================================================================== */

  public int compare(final BwRecurrenceInstance inst1,
                     final BwRecurrenceInstance inst2) {
    /* Note we only check master + recurrenceid. The recurrence id is the key
     * to the entry. This does not take account of modifications to the object.
     */

    final int res = inst1.getMaster().compareTo(inst2.getMaster());
    if (res != 0) {
      return res;
    }

    return inst1.getRecurrenceId().compareTo(inst2.getRecurrenceId());
  }

  public int compareTo(final BwRecurrenceInstance o2) {
    return compare(this, o2);
  }

  public int hashCode() {
    return getRecurrenceId().hashCode();
  }

  /* We always use the compareTo method
   */
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BwRecurrenceInstance)) {
      return false;
    }

    return compareTo((BwRecurrenceInstance)obj) == 0;
  }

  public String toString() {
    final var ts = new ToString(this);

    ts.append("dtStart", getDtstart());
    ts.append("dtEnd", getDtend());
    ts.append("recurrenceId", getRecurrenceId());
    ts.append("master", getMaster().getId());

    if (getOverride() != null) {
      ts.append(getOverride());
    }

    return ts.toString();
  }
}
