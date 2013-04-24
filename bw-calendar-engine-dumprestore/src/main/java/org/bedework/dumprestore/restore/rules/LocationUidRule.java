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

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

/** Retrieve a location with the stacked uid and push it.
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class LocationUidRule extends StringKeyRule {
  LocationUidRule(RestoreGlobals globals) {
    super(globals);
  }

  public void pushEntity(String val) throws Exception {
    BwLocation ent;
    try {
      ent = globals.rintf.getLocation(val);
    } catch (Throwable t) {
      throw new Exception(t);
    }

    if (ent == null) {
      throw new Exception("Missing location with uid " + val + " for " + top());
    }

    if (top() instanceof BwAuthUser) {
      ((BwAuthUser)top()).getPrefs().getLocationPrefs().add(ent);
    } else if (top() instanceof EventInfo) {
      ((EventInfo)top()).getEvent().setLocation(ent);
    } else {
      handleException(new Exception("Unexpected stack top "));
    }
  }
}
