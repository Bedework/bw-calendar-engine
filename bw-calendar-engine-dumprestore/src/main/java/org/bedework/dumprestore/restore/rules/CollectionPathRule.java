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
import org.bedework.calfacade.BwCalendar;
import org.bedework.dumprestore.restore.RestoreGlobals;
import org.bedework.util.misc.Util;

/** Build an OwnerUidKey then retrieve and store the object..
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 1.0
 */
public class CollectionPathRule extends StringKeyRule {
  CollectionPathRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void pushEntity(final String val) throws Exception {
    BwCalendar ent;
    String path = Util.buildPath(true, val);
    try {
      ent = globals.rintf.getCalendar(path);
    } catch (Throwable t) {
      throw new Exception(t);
    }

    if (ent == null) {
      throw new Exception("Missing collection with path " + path +
                          " for " + top());
    }

    if (top() instanceof BwAuthUser) {
      ((BwAuthUser)top()).getPrefs().getCalendarPrefs().add(ent);
    } else {
      push(ent);
    }
  }
}
