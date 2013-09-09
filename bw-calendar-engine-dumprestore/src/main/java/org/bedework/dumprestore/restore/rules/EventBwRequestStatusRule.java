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
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public class EventBwRequestStatusRule extends EntityRule {
  /** Constructor
   *
   * @param globals
   */
  public EventBwRequestStatusRule(RestoreGlobals globals) {
    super(globals);
  }

  public void end(String ns, String name) throws Exception {
    BwRequestStatus entity = (BwRequestStatus)pop();
    EventInfo ei = (EventInfo)top();

    BwEvent e = ei.getEvent();
    if (e instanceof BwEventProxy) {
      e = ((BwEventProxy)e).getRef();
    }

    e.addRequestStatus(entity);
  }
}
