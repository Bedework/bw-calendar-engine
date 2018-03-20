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

import org.bedework.dumprestore.restore.OwnerUidKey;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.xml.sax.Attributes;

/** PRE3.5 Build an OwnerUidKey then retrieve and store the object..
 *
 * @author Mike Douglass   douglm @ rpi.edu
 * @version 1.0
 */
public abstract class OwnerUidKeyRule extends RestoreRule {
  OwnerUidKeyRule(RestoreGlobals globals) {
    super(globals);
  }

  public void begin(String ns, String name, Attributes att) {
    push(new OwnerUidKey());
  }

  public void end(String ns, String name) throws Exception {
    /* Top should be the OwnerUidKey, underneath is the actual entity
     */

    storeEntity((OwnerUidKey)pop());
  }

  /**
   * @param key
   * @throws Exception
   */
  public abstract void storeEntity(OwnerUidKey key) throws Exception;
}
