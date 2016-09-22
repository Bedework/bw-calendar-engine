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

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwString;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AlarmFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();

    skippedNames.add("attendees");
    skippedNames.add("descriptions");
    skippedNames.add("summaries");
    skippedNames.add("xproperties");
  }

  AlarmFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Throwable {
    if (skippedNames.contains(name)) {
      return;
    }

    BwString str = null;

    if (top() instanceof BwString) {
      str = (BwString)pop();
    }

    final BwAlarm ent = (BwAlarm)top();

    if (ownedEntityTags(ent, name)) {
      return;
    }

    if (name.equals("trigger")) {
      ent.setTrigger(stringFld());
      return;
    }

    if (name.equals("duration")) {
      ent.setDuration(stringFld());
      return;
    }

    if (name.equals("repeat")) {
      ent.setRepeat(intFld());
      return;
    }

    if (name.equals("attach")) {
      ent.setAttach(stringFld());
      return;
    }

    if (name.equals("expired")) {
      ent.setExpired(booleanFld());
      return;
    }

    /* post 3.5 */

    if (name.equals("alarmType")) {
      ent.setAlarmType(intFld());
      return;
    }

    if (name.equals("triggerStart")) {
      ent.setTriggerStart(booleanFld());
      return;
    }

    if (name.equals("triggerDateTime")) {
      ent.setTriggerDateTime(booleanFld());
      return;
    }

    if (name.equals("triggerTime")) {
      // ignore
      return;
    }

    if (name.equals("repeatCount")) {
      ent.setRepeatCount(intFld());
      return;
    }

    if (name.equals("previousTrigger")) {
      // ignore
      return;
    }

    if (name.equals("byteSize")) {
      ent.setByteSize(intFld());
      return;
    }

    unknownTag(name);
  }
}
