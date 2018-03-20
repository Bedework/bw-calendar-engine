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

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwString;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class CategoryFieldRule extends EntityFieldRule {
  CategoryFieldRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Exception {
    BwString s = null;

    if (top() instanceof BwString) {
      s = (BwString)pop();
    }

    BwCategory cat = (BwCategory)top();

    if (shareableEntityTags(cat, name)) {
      return;
    }

    if (name.equals("word")) {
      cat.setWord(s);
    } else if (name.equals("description")) {
      cat.setDescription(s);
    } else if (name.equals("uid")) {
      cat.setUid(stringFld());
    } else if (name.equals("byteSize")) {
      cat.setByteSize(intFld());
    } else {
      unknownTag(name);
    }
  }
}
