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

import org.bedework.dumprestore.restore.RestoreGlobals;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class DumpPropertiesRule extends EntityFieldRule {
  /** Constructor
   *
   * @param globals
   */
  public DumpPropertiesRule(RestoreGlobals globals) {
    super(globals);
  }

  public void field(String name) throws Exception {
    if (name.equals("majorVersion")) {
      globals.bedeworkMajorVersion = intFld();
      return;
    }

    if (name.equals("minorVersion")) {
      globals.bedeworkMinorVersion = intFld();
      return;
    }

    if (name.equals("updateVersion")) {
      globals.bedeworkUpdateVersion = intFld();
      return;
    }

    if (name.equals("patchLevel")) {
      globals.bedeworkPatchLevel = stringFld();
      return;
    }

    if (name.equals("dumpDate")) {
      globals.dumpDate = stringFld();
      return;
    }

    if (name.equals("version")) {
      globals.bedeworkVersion = stringFld();
      return;
    }

    error("Unknown version tag " + name);
  }
}
