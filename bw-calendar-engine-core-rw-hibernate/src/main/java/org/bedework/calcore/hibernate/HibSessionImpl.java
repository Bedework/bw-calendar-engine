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
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.base.BwDbentity;
import org.bedework.database.db.InterceptorDbEntity;
import org.bedework.database.jpa.DbSessionImpl;

/** Class to do the actual database interaction.
 *
 * @author Mike Douglass bedework.org
 */
public class HibSessionImpl
        extends DbSessionImpl {

  protected void afterAdd(final Object o) {
    if (!(o instanceof final InterceptorDbEntity ent)) {
      return;
    }

    ent.afterAdd();
    deleteSubs(ent);
  }

  protected void afterUpdate(final Object o) {
    if (!(o instanceof final InterceptorDbEntity ent)) {
      return;
    }

    ent.afterUpdate();
    deleteSubs(ent);
  }

  protected void afterDelete(final Object o) {
    if (!(o instanceof final InterceptorDbEntity ent)) {
      return;
    }

    ent.afterDeletion();
    deleteSubs(ent);
  }

  private void deleteSubs(final Object o) {
    if (!(o instanceof final BwDbentity<?> ent)) {
      return;
    }

    final var subs = ent.getDeletedEntities();
    if (subs == null) {
      return;
    }

    for (final var sub: subs) {
      evict(sub);
      delete(sub);
    }
  }
}
