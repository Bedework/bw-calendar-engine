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

import org.bedework.calfacade.BwResource;
import org.bedework.dumprestore.restore.RestoreGlobals;

import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Mike Douglass   douglm bedework.edu
 * @version 1.0
 */
public class ResourceFieldRule extends EntityFieldRule {
  private static Collection<String> skippedNames;

  static {
    skippedNames = new ArrayList<String>();
  }

  ResourceFieldRule(final RestoreGlobals globals) {
    super(globals);
  }

  @Override
  public void field(final String name) throws Exception {
    if (skippedNames.contains(name)) {
      return;
    }

    BwResource rs = (BwResource)top();

    if (shareableContainedEntityTags(rs, name)) {
      return;
    }

    if (name.equals("lastmod")) {
      rs.setLastmod(stringFld());

    } else if (name.equals("sequence")) {
      rs.setSequence(intFld());

    } else if (name.equals("created")) {
      rs.setCreated(stringFld());

    } else if (name.equals("name")) {
      rs.setName(stringFld());

    } else if (name.equals("contentType")) {
      rs.setContentType(stringFld());

    } else if (name.equals("encoding")) {
      rs.setEncoding(stringFld());

    } else if (name.equals("contentLength")) {
      rs.setContentLength(longFld());

    } else if (name.equals("byteSize")) {
      rs.setByteSize(intFld());
    } else {
      unknownTag(name);
    }
  }
}

