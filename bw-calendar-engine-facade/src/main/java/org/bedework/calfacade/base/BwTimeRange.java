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

import org.bedework.calfacade.BwDateTime;

import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.Property;

import org.apache.log4j.Logger;

/** Timerange element for filters. Either start or end may be absent but
 * not both.
 *
 *   @author Mike Douglass   douglm  rpi.edu
 */
public class BwTimeRange {
  private BwDateTime start;
  private BwDateTime end;

  /** Constructor
   */
  public BwTimeRange() {
  }

  /** Constructor
   *
   * @param start
   * @param end
   */
  public BwTimeRange(BwDateTime start, BwDateTime end) {
    this.start = start;
    this.end = end;
  }

  /**
   * @param val BwDateTime start
   */
  public void setStart(BwDateTime val) {
    start = val;
  }

  /**
   * @return BwDateTime start
   */
  public BwDateTime getStart() {
    return start;
  }

  /**
   * @param val BwDateTime end
   */
  public void setEnd(BwDateTime val) {
    end = val;
  }

  /**
   * @return BwDateTime end
   */
  public BwDateTime getEnd() {
    return end;
  }

  /** merge that into this
   *
   * @param that TimeRange to merge
   */
  public void merge(BwTimeRange that) {
    if (getStart().before(that.getStart())) {
      setStart(that.getStart());
    }

    if (getEnd().after(that.getEnd())) {
      setEnd(that.getEnd());
    }
  }

  /** Test if the given property falls in the timerange
   *
   * @param candidate
   * @return boolean true if in range
   */
  public boolean matches(Property candidate) {
    if (!(candidate instanceof DateProperty)) {
      return false;
    }

    // XXX later
    return true;
  }

  /** Debug
   *
   * @param log
   * @param indent
   */
  public void dump(Logger log, String indent) {
    log.debug(indent + toString());
  }

  protected void toStringSegment(StringBuffer sb) {
    if (start != null) {
      sb.append("start=");
      sb.append(start);
    }

    if (end != null) {
      if (start != null) {
        sb.append(" ");
      }
      sb.append("end=");
      sb.append(end);
    }
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();

    sb.append("<time-range ");
    toStringSegment(sb);
    sb.append("/>");

    return sb.toString();
  }
}

