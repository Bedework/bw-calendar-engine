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

import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class CalSuiteFieldRule extends EntityFieldRule {
  CalSuiteFieldRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Exception {
    BwCalSuite ent = (BwCalSuite)top();

    if (shareableEntityTags(ent, name)) {
      return;
    }

    if (name.equals("name")) {
      ent.setName(stringFld());
    } else if (name.equals("group")) {
      // Ignore
    } else if (name.equals("account")) {
      ent.setGroup(adminGroupFld());
    } else if (name.equals("rootCollectionPath")) {
      ent.setRootCollectionPath(stringFld());
    } else if (name.equals("submissionsRootPath")) {
    } else if (name.equals("fields1")) {
      ent.setFields1(stringFld());
    } else if (name.equals("fields2")) {
      ent.setFields2(stringFld());
    } else if (name.equals("path")) {           // Intermediate 3.5?
      ent.setRootCollection(calendarFld());
      if (ent.getRootCollection() != null) {
        ent.setRootCollectionPath(ent.getRootCollection().getPath());
      }
    } else if (name.equals("byteSize")) {
      ent.setByteSize(intFld());
    } else {
      unknownTag(name);
    }
  }
}
