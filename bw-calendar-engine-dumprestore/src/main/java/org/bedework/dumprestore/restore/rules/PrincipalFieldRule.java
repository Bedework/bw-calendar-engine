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

import org.bedework.dumprestore.restore.PrincipalHref;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.bedework.access.WhoDefs;

/**
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public class PrincipalFieldRule extends EntityFieldRule {
  PrincipalFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    PrincipalHref oi = (PrincipalHref)top();

    try {
      if (name.equals("user")) {
        oi.setKind(WhoDefs.whoTypeUser);
        oi.prefix = RestoreGlobals.getUserPrincipalRoot();
      } else if (name.equals("group")) {
        oi.setKind(WhoDefs.whoTypeGroup);
        oi.prefix = RestoreGlobals.getGroupPrincipalRoot();
      } else if (name.equals("adminGroup")) {
        oi.setKind(WhoDefs.whoTypeGroup);
        oi.prefix = RestoreGlobals.getBwadmingroupPrincipalRoot();
      } else if (name.equals("account")) {
        oi.setAccount(stringFld());
      } else {
        unknownTag(name);
      }
    } catch (Throwable t) {
      throw new Exception(t);
    }
  }

  @Override
  public void field(final String name) throws Exception {
    // Not used
  }
}
