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
package org.bedework.calfacade.svc;

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.util.misc.ToString;

/** An object representing a calendar admin group.
 *
 * @author Mike Douglass
 * @version 2.2
 */
@Dump(elementName="adminGroup", keyFields={"account"},
      firstFields = {"account","principalRef"})
public class BwAdminGroup extends BwGroup {
  private String groupOwnerHref;

  private String ownerHref;

  /** Create a new object.
   */
  public BwAdminGroup() {
    super();
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  @Override
  @NoDump
  public String getAclAccount() {
    // TODO - fix this whole mess
    return "bwadmin/" + getAccount();
  }

  /** Set the group owner.
   *
   * @param   val     String group owner.
   */
  public void setGroupOwnerHref(final String val) {
    groupOwnerHref = val;
  }

  /** Return the group owner href.
   *
   * @return String        group owner href
   */
  public String getGroupOwnerHref() {
    return groupOwnerHref;
  }

  /** Set the owner of the groups resources and entities.
   *
   * @param   val     String group event owner.
   */
  public void setOwnerHref(final String val) {
    ownerHref = val;
  }

  /** Return the owner of the groups resources and entities.
   *
   * @return String        group owner
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("description", getDescription());
    ts.append("groupOwner", getGroupOwnerHref());
    ts.append("owner", getOwnerHref());

    return ts.toString();
  }

  @Override
  public Object clone() {
    final BwAdminGroup ag = new BwAdminGroup();
    copyTo(ag);

    ag.setDescription(getDescription());
    ag.setGroupOwnerHref(getGroupOwnerHref());
    ag.setOwnerHref(getOwnerHref());

    return ag;
  }
}
