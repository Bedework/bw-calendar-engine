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


import org.bedework.util.misc.ToString;

/** Information about an alias
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class AliasInfo {
  private final String path;

  private boolean publick;

  private String owner;

  private boolean external;

  private final String targetPath;

  private boolean indirect;

  private boolean broken;

  private boolean noAccess;

  private boolean noInvite;

  /**
   * @param path of the alias
   * @param targetPath what it points at
   * @param publick true for public
   * @param owner the owner
   */
  public AliasInfo(final String path,
                   final String targetPath,
                   final boolean publick,
                   final String owner) {
    this.path = path;
    this.targetPath = targetPath;
  }

  /**
   * @param path of the subscription
   * @param publick true for public
   * @param owner the owner
   */
  public static AliasInfo getExternalSubInfo(final String path,
                                             final boolean publick,
                                             final String owner) {
    final AliasInfo ai = new AliasInfo(path, null, publick, owner);
    ai.external = true;

    return ai;
  }

  public String getPath() {
    return path;
  }

  /**
   * @return true for a public alias
   */
  public boolean getPublick() {
    return publick;
  }

  /**
   * @return owner principal href
   */
  public String getOwner() {
    return owner;
  }

  /**
   * @return true for an external subscription
   */
  public boolean getExternal() {
    return external;
  }

  /**
   * @return internal alias target
   */
  public String getTargetPath() {
    return targetPath;
  }

  /**
   * @param indirect true if this alias refers to an alias
   */
  public void setIndirect(final boolean indirect) {
    this.indirect = indirect;
  }

  public boolean getIndirect() {
    return indirect;
  }

  /**
   * @param broken true for no target
   */
  public void setBroken(final boolean broken) {
    this.broken = broken;
  }

  public boolean getBroken() {
    return broken;
  }

  /**
   * @param noAccess true if sharee has no access
   */
  public void setNoAccess(final boolean noAccess) {
    this.noAccess = noAccess;
  }

  public boolean getNoAccess() {
    return noAccess;
  }

  /**
   * @param noInvite true if there is no invite status
   */
  public void setNoInvite(final boolean noInvite) {
    this.noInvite = noInvite;
  }

  public boolean getNoInvite() {
    return noInvite;
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("path", path);
    ts.append("targetPath", targetPath);
    ts.append("external", external);
    ts.append("publick", publick);
    ts.append("owner", owner);
    ts.append("broken", broken);
    ts.append("noAccess", noAccess);
    ts.append("noInvite", noInvite);

    return ts.toString();
  }
}
