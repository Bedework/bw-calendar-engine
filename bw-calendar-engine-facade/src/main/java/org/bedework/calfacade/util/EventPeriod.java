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
package org.bedework.calfacade.util;

import org.bedework.util.misc.ToString;

import net.fortuna.ical4j.model.DateTime;

/**
 * @author douglm
 *
 */

public class EventPeriod implements Comparable<EventPeriod> {
  private DateTime start;
  private DateTime end;
  private int type;  // from BwFreeBusyComponent

  /* Number of busy entries this period - for the free/busy aggregator */
  private int numBusy;

  /* Number of tentative entries this period - for the free/busy aggregator */
  private int numTentative;

  public EventPeriod(DateTime start, DateTime end, int type) {
    this.start = start;
    this.end = end;
    this.type = type;
  }

  /**
   * @return DateTime
   */
  public DateTime getStart() {
    return start;
  }

  /**
   * @return DateTime
   */
  public DateTime getEnd() {
    return end;
  }

  /**
   * @param val int
   */
  public void setType(int val) {
    type = val;
  }

  /**
   * @return int
   */
  public int getType() {
    return type;
  }

  public int compareTo(EventPeriod that) {
    /* Sort by type first */
    if (type < that.type) {
      return -1;
    }

    if (type > that.type) {
      return 1;
    }

    int res = start.compareTo(that.start);
    if (res != 0) {
      return res;
    }

    return end.compareTo(that.end);
  }

  /**
   * @param val number busy periods
   */
  public void setNumBusy(int val) {
    numBusy = val;
  }

  /**
   * @return int
   */
  public int getNumBusy() {
    return numBusy;
  }

  /**
   * @param val number tentative periods
   */
  public void setNumTentative(int val) {
    numTentative = val;
  }

  /**
   * @return int
   */
  public int getNumTentative() {
    return numTentative;
  }

  public boolean equals(Object o) {
    if (o instanceof EventPeriod) {
      return compareTo((EventPeriod)o) == 0;
    }

    return false;
  }

  public int hashCode() {
    return 7 * (type + 1) * (start.hashCode() + 1) * (end.hashCode() + 1);
  }

  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("start", start);
    ts.append("end", end);
    ts.append("type", type);

    return ts.toString();
  }
}
