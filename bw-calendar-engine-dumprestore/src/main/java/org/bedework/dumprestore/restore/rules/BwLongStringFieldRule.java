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

import org.bedework.calfacade.BwLongString;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class BwLongStringFieldRule extends EntityFieldRule {
  BwLongStringFieldRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Exception {
    BwLongString s = (BwLongString)top();

    if (name.equals("lang")) {
      s.setLang(stringFld());
    } else if (name.equals("value")) {
      s.setValue(stringFld());
    } else if (name.equals("bwlongstring")) { // Needed so we can cope with previous formats
    } else {
      unknownTag(name);
    }
  }
}

