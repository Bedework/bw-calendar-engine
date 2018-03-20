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

import org.bedework.calfacade.BwResource;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm  rpi.edu
 * @version 1.0
 */
public class ResourceRule extends EntityRule {
  /** Cobstructor
   *
   * @param globals
   */
  public ResourceRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwResource entity = (BwResource)pop();

    try {
      if (entity.getOwnerHref() == null) {
        error("Missing owner for " + entity);
        return;
      }

      if (entity.getContent() == null) {
        error("Missing content for " + entity);
        return;
      }

      globals.counts[globals.resources]++;

      if (globals.rintf != null) {
        globals.rintf.restoreResource(entity);
      }
    } catch (Throwable t) {
      error("Unable to restore admin group " + entity);
      throw new Exception(t);
    }
  }
}

