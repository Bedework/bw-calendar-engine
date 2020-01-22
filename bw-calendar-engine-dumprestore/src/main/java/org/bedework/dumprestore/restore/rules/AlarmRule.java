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
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.Counters;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AlarmRule extends EntityRule {
  /** Constructor
   *
   * @param globals for restore
   */
  public AlarmRule(RestoreGlobals globals) {
    super(globals);
  }

  public void end(String ns, String name) throws Exception {
    BwAlarm entity = (BwAlarm)pop();
    globals.counts[Counters.valarms]++;

    if (!(top() instanceof EventInfo)) {
      warn("Expected an event info object: found " + top());
      return;
    }

    BwEvent ev = ((EventInfo)top()).getEvent();

    if (entity.getOwnerHref() == null) {
      entity.setOwnerHref(ev.getOwnerHref());
    }

    ev.addAlarm(entity);
  }
}
