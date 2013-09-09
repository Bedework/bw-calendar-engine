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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.dumprestore.restore.PrincipalHref;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.bedework.access.WhoDefs;

import org.xml.sax.Attributes;

/** Retrieve a creator and leave on the stack.
 *
 * @author Mike Douglass   douglm @ bedework.edu
 * @version 1.0
 */
public class CreatorRule extends RestoreRule {
  protected transient String tagName;
  protected transient String fldval;

  CreatorRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void begin(final String ns, final String name, final Attributes att) {
    push(new PrincipalHref());
    globals.inOwnerKey = true;
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    error("CreatorRule called");
    BwPrincipal p = doPrincipal();

    BwShareableDbentity o;

    if (top() instanceof EventInfo) {
      o = ((EventInfo)top()).getEvent();
    } else {
      o = (BwShareableDbentity)top();
    }

    if (o == null) {
      error("Null stack top when setting creator");
      return;
    }

    o.setCreatorHref(p.getPrincipalRef());
    globals.inOwnerKey = false;
  }

  protected BwPrincipal doPrincipal() throws Exception {
    /* Top should be the owner info, underneath is the actual entity -
     * hide the owner under the entity.
     */

    PrincipalHref oi = (PrincipalHref)pop();

    // XXX could be a group?
    if (oi.getKind() == WhoDefs.whoTypeGroup) {
      throw new Exception("Group creator not implemented");
    }

    try {
      oi.setHref(RestoreGlobals.getUserPrincipalRoot() + oi.getAccount());
    } catch (Throwable t) {
      error("Unable to get user principal root", t);
      return null;
    }

    BwPrincipal p = globals.principalsTbl.getUserOwner(oi);

    if (p == null) {
      error("Missing creator " + oi);
      globals.entityError = true;
      p = BwPrincipal.makeUserPrincipal();
    }

    return p;
  }

  @Override
  public void body(final String namespace, final String name, final String text)
      throws Exception {
    /*
    if (globals.debug) {
      trace("Save field " + name);
    }
    */

    tagName = name;
    fldval = text;
  }
}
