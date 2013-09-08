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

import org.bedework.calfacade.BwEvent;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.Comparator;

/** allows us to store EventInfo objects in a set using only the
 * uid and recurrence id as the ordering factors. We need this so we can
 * locate an override even if the date has been changed.
 *
 * @author Mike Douglass       douglm @ rpi.edu
 */
class EventOverride
      implements Comparable<EventOverride>, Comparator<EventOverride>, Serializable {
  private EventInfo ei;

  EventOverride(final EventInfo ei) {
    this.ei = ei;
  }

  EventInfo getEventInfo() {
    return ei;
  }

  BwEvent getEvent() {
    return ei.getEvent();
  }

  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  public int compareTo(final EventOverride that) {
    return compare(this, that);
  }

  /* (non-Javadoc)
   * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
   */
  public int compare(final EventOverride o1, final EventOverride o2) {
    if (o1 == o2) {
      return 0;
    }

    BwEvent ev1 = o1.getEvent();
    BwEvent ev2 = o2.getEvent();

    int res = ev1.getUid().compareTo(ev2.getUid());

    if (res != 0) {
      return res;
    }

    return Util.compareStrings(ev1.getRecurrenceId(),
                               ev2.getRecurrenceId());
  }

  @Override
  public int hashCode() {
    return getEvent().hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof EventOverride)) {
      return false;
    }

    return compareTo((EventOverride)obj) == 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("Override{eventid=");

    if (getEvent() == null) {
      sb.append("UNKNOWN");
    } else {
      sb.append(getEvent().getId());
    }

    sb.append(", recurrenceId=");
    sb.append(getEvent().getRecurrenceId());
    sb.append("}");

    return sb.toString();
  }
}

