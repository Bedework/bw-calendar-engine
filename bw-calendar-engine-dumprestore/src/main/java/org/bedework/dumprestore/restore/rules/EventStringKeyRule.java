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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

/** Expect string on stack top. Either uid or recurrenceId for an event
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 1.0
 */
public class EventStringKeyRule extends EntityFieldRule {
  EventStringKeyRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Throwable {
    try {
      /* Top should now be an event object */

      EventInfo ei = (EventInfo)getTop(EventInfo.class, name);
      BwEventAnnotation ann = null;

      BwEvent e = ei.getEvent();
      if (e instanceof BwEventProxy) {
        ann = ((BwEventProxy)e).getRef();

        if (ann.getOverride()) {
          // Overrides have everything set already
          return;
        }

        String match = getDigester().getMatch();

        if (match.contains("/target/")) {
          e = ann.getTarget();
        } else if (match.contains("/master/")) {
          e = ann.getMaster();
        } else {
          e = ann;
        }
      }

      if (name.equals("uid")) {
        e.setUid(stringFld());
      } else if (name.equals("recurrenceId")) {
        e.setRecurrenceId(stringFld());
      } else {
        unknownTag(name);
      }
    } catch (Throwable t) {
      handleException(t);
    }
  }
}
