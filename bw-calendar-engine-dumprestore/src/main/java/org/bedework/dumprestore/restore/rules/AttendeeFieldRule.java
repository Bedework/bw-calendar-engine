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
package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwAttendee;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AttendeeFieldRule extends EntityFieldRule {
  AttendeeFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Throwable {
    BwAttendee ent = (BwAttendee)top();

    if (name.equals("id") || name.equals("seq")) {  // pre 3.5 - won't work
      return;
    }

    if (name.equals("cn")) {
      ent.setCn(stringFld());
      return;
    }

    if (name.equals("dir")) {
      ent.setDir(stringFld());
      return;
    }

    if (name.equals("member")) {
      ent.setMember(stringFld());
      return;
    }

    if (name.equals("reinvite")) {
      return;
    }

    if (name.equals("rsvp")) {
      ent.setRsvp(booleanFld());
      return;
    }

    if (name.equals("role")) {
      ent.setRole(stringFld());
      return;
    }

    if (name.equals("sequence")) {
      ent.setSequence(intFld());
      return;
    }

    if (name.equals("dtstamp")) {
      ent.setDtstamp(stringFld());
      return;
    }

    if (name.equals("partstat")) {
      ent.setPartstat(stringFld());
      return;
    }

    /* post 3.5 */

    if (name.equals("cuType")) {
      ent.setCuType(stringFld());
      return;
    }

    if (name.equals("delegatedFrom")) {
      ent.setDelegatedFrom(stringFld());
      return;
    }

    if (name.equals("delegatedTo")) {
      ent.setDelegatedTo(stringFld());
      return;
    }

    if (name.equals("language")) {
      ent.setLanguage(stringFld());
      return;
    }

    if (name.equals("scheduleAgent")) {
      ent.setScheduleAgent(intFld());
      return;
    }

    if (name.equals("scheduleStatus")) {
      ent.setScheduleStatus(stringFld());
      return;
    }

    if (name.equals("sentBy")) {
      ent.setSentBy(stringFld());
      return;
    }

    if (name.equals("attendeeUri")) {
      ent.setAttendeeUri(stringFld());
      return;
    }

    if (name.equals("byteSize")) {
      ent.setByteSize(intFld());
      return;
    }

    unknownTag(name);
  }
}

