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

import org.bedework.calfacade.BwAttachment;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class AttachmentFieldRule extends EntityFieldRule {
  AttachmentFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Throwable {
    BwAttachment ent = (BwAttachment)top();

    if (name.equals("id")) {  // pre 3.5 - won't work
      return;
    }

    if (name.equals("fmtType")) {
      ent.setFmtType(stringFld());
      return;
    }

    if (name.equals("valueType")) {
      ent.setValueType(stringFld());
      return;
    }

    if (name.equals("encoding")) {
      ent.setEncoding(stringFld());
      return;
    }

    if (name.equals("uri")) {
      ent.setUri(stringFld());
      return;
    }

    if (name.equals("value")) {
      ent.setValue(stringFld());
      return;
    }

    if (name.equals("byteSize")) {
      ent.setByteSize(intFld());
      return;
    }

    unknownTag(name);
  }
}

