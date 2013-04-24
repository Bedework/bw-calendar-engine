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

import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwString;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class LocationRule extends EntityRule {
  /** Constructor
   *
   * @param globals
   */
  public LocationRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwLocation entity = (BwLocation)pop();
    globals.counts[globals.locations]++;

    if (entity.getUid() == null) {
      // Presume migrating
      entity.initUid();
    }

    fixSharableEntity(entity, "Location");

    if (entity.getAddress() == null) {
      entity.setAddress(new BwString(null, " "));
      warn("Null address for " + entity);
    }

    try {
      if (!globals.onlyUsersMap.check(entity)) {
        return;
      }

      globals.rintf.restoreLocation(entity);
    } catch (Throwable t) {
      warn("Unable to restore " + entity + " with owner " + entity.getOwnerHref());
      throw new Exception(t);
    }
  }
}

