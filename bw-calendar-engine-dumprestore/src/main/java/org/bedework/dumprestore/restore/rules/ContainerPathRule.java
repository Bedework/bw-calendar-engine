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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public class ContainerPathRule extends EntityFieldRule {
  ContainerPathRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Throwable{

    if (name.equals("path")) {
      /* If the top is an override skip this - container is set already. */
      BwEventAnnotation ann = null;
      BwEvent e = null;

      if (top() instanceof EventInfo) {
        EventInfo ei = (EventInfo)getTop(EventInfo.class, name);

        e = ei.getEvent();
        if (e instanceof BwEventProxy) {
          ann = ((BwEventProxy)e).getRef();

          if (ann.getOverride()) {
            // Overrides have everything set already
            return;
          }
        }
      }

      BwCalendar cal = globals.rintf.getCalendar(stringFld());
      if (cal == null) {
        error("No calendar for path " + stringFld());
      }

      if (top() instanceof BwShareableContainedDbentity) {
        BwShareableContainedDbentity scde = (BwShareableContainedDbentity)top();
        scde.setColPath(stringFld());
      } else if (top() instanceof EventInfo) {
        if (ann != null) {
          /* Could be target or master */
          String match = getDigester().getMatch();

          if (match.contains("/target/")) {
            ann.getTarget().setColPath(stringFld());
          } else if (match.contains("/master/")) {
            ann.getMaster().setColPath(stringFld());
          } else {
            ann.setColPath(stringFld());
          }
        }
        e.setColPath(stringFld());
      } else {
        handleException(new Exception("Unexpected stack top "));
      }
    } else {
      unknownTag(name);
    }
  }
}
