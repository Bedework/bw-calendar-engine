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

import net.fortuna.ical4j.model.DateTime;

/**
 * @author douglm
 *
 */

public class EventPeriod implements Comparable {
  DateTime start;
  DateTime end;
  int type;  // from BwFreeBusyComponent

  EventPeriod(DateTime start, DateTime end, int type) {
    this.start = start;
    this.end = end;
    this.type = type;
  }

  public int compareTo(Object o) {
    if (!(o instanceof EventPeriod)) {
      return -1;
    }

    EventPeriod that = (EventPeriod)o;

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

  public boolean equals(Object o) {
    return compareTo(o) == 0;
  }

  public int hashCode() {
    return 7 * (type + 1) * (start.hashCode() + 1) * (end.hashCode() + 1);
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("EventPeriod{start=");

    sb.append(start);
    sb.append(", end=");
    sb.append(end);
    sb.append(", type=");
    sb.append(type);
    sb.append("}");

    return sb.toString();
  }
}
