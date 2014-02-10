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

import org.bedework.util.misc.ToString;

import java.util.List;

/** Signal update of a scheduling entity. These are not yet in an inbox or outbox.
 *
 * @author douglm
 *
 */
public class ScheduleUpdateEvent extends EntityEvent {
  private static final long serialVersionUID = 1L;

  /** */
  public enum ChangeType {
    /** Attendee partstat change only - result of reply */
    partStatOnly,

    /** Attendee changed their partstat */
    attendeeChange,

    /** Significant organizer change */
    organizerUpdate,

    /** Just been added */
    newMeeting
  }

  private ChangeType change;

  /**
   * @param code
   * @param authPrincipalHref
   * @param ownerHref
   * @param href
   * @param rid
   * @param change
   */
  public ScheduleUpdateEvent(final SysCode code,
                             final String authPrincipalHref,
                             final String ownerHref,
                             final String href,
                             final String rid,
                             final ChangeType change) {
    super(code, authPrincipalHref, ownerHref, href, rid);

    this.change = change;
  }

  @Override
  public List<Attribute> getMessageAttributes() {
    List<Attribute> attrs = super.getMessageAttributes();

    attrs.add(new Attribute("scheduleEvent", "true"));

    return attrs;
  }

  /**
   * @return type of change that occurred
   */
  public ChangeType getChange() {
    return change;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("change", getChange());
  }
}
