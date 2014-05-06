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
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.RestoreGlobals;

/** Build an OwnerUidKey then retrieve and store the object..
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class CategoryUidRule extends StringKeyRule {
  CategoryUidRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void pushEntity(final String val) throws Exception {
    final BwCategory ent;
    try {
      ent = globals.rintf.getCategory(val);
    } catch (final Throwable t) {
      throw new Exception(t);
    }

    if (ent == null) {
      error("Missing category with uid " + val + " for " + top());
    } else if (top() instanceof BwAuthUser) {
      ((BwAuthUser)top()).getPrefs().getCategoryPrefs().add(ent);
    } else if (top() instanceof EventInfo) {
      ((EventInfo)top()).getEvent().addCategory(ent);
    } else if (top() instanceof CategorisedEntity) {
      ((CategorisedEntity)top()).addCategory(ent);
    } else {
      warn("Unknown top element type for category " +
           top().getClass().getName());
      push(ent);
    }
  }
}
