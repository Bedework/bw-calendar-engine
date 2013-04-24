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
import org.bedework.calfacade.BwString;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class ContactFieldRule extends EntityFieldRule {
  ContactFieldRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Exception {
    if (name.equals("name")) {
      // Expect the value on stack top
      BwString s = (BwString)pop();

      ((BwContact)top()).setName(s);

      return;
    }

    BwContact c = (BwContact)top();

    if (shareableEntityTags(c, name)) {
      return;
    }

    if (name.equals("value")) {             // PRE3.5
    } else if (name.equals("phone")) {
      c.setPhone(stringFld());
    } else if (name.equals("email")) {
      c.setEmail(stringFld());
    } else if (name.equals("link")) {
      c.setLink(stringFld());
    } else if (name.equals("uid")) {
      c.setUid(stringFld());
    } else if (name.equals("byteSize")) {
      c.setByteSize(intFld());
    } else {
      unknownTag(name);
    }
  }
}

