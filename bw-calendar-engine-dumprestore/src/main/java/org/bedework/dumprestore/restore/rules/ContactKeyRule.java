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

import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.OwnerUidKey;
import org.bedework.dumprestore.restore.RestoreGlobals;

/** Build an OwnerUidKey then retrieve and store the object..
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public class ContactKeyRule extends OwnerUidKeyRule {
  ContactKeyRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void storeEntity(final OwnerUidKey key) throws Exception {
    BwContact ent;
    try {
      ent = globals.rintf.getContact(key.getUid());
    } catch (Throwable t) {
      throw new Exception(t);
    }

    if (ent == null) {
      throw new Exception("Missing contact with key " + key + " for " + top());
    }

    if (top() instanceof EventInfo) {
      BwEvent ev = ((EventInfo)top()).getEvent();

      ev.addContact(ent);
      return;
    }

    throw new Exception("Unhandled class for contacts " + top());
  }
}
