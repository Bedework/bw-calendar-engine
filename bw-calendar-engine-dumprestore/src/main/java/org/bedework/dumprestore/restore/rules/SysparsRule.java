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

import org.bedework.calfacade.BwSystem;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class SysparsRule extends EntityRule {
  /** Constructor
   *
   * @param globals
   */
  public SysparsRule(final RestoreGlobals globals) {
    super(globals);
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.rules.EntityRule#begin(java.lang.String, java.lang.String, org.xml.sax.Attributes)
   */
  @Override
  public void begin(final String ns, final String name, final Attributes att) throws Exception {
    super.begin(ns, name, att);

    /* Use the global object. */
    pop();
    push(new BwSystem());
  }

  @Override
  public void end(final String ns, final String name) throws Exception {
    BwSystem ent = (BwSystem)pop();

    try {
      globals.rintf.restoreSyspars(ent);
    } catch (Throwable t) {
      throw new Exception(t);
    }
  }
}
