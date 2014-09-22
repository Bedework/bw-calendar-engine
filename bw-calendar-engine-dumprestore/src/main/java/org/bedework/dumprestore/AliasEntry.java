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
package org.bedework.dumprestore;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/** Information about an aliased path
 *
 * @author Mike Douglass
 * @version 1.0
 */
@Dump(elementName="aliasEntry", keyFields={"targetPath"})
public class AliasEntry extends DumpEntity<AliasEntry> {
  private String targetPath;

  private List<AliasInfo> aliases = new ArrayList<>();

  /** Required for restore
   *
   */
  public AliasEntry() {
  }

  /**
   * @param targetPath what it points at
   */
  public AliasEntry(final String targetPath) {
    this.targetPath = targetPath;
  }

  public void setTargetPath(final String val) {
    targetPath = val;
  }

  /**
   * @return internal alias target
   */
  public String getTargetPath() {
    return targetPath;
  }

  /**
   * @param val the aliases
   */
  public void setAliases(final List<AliasInfo> val) {
    aliases = val;
  }

  /**
   * @return the aliases
   */
  public List<AliasInfo> getAliases() {
    return aliases;
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("targetPath", targetPath);
    ts.append("aliases", aliases);

    return ts.toString();
  }
}
