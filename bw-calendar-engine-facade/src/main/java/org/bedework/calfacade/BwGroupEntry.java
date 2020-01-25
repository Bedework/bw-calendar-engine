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

package org.bedework.calfacade;

import org.bedework.util.misc.ToString;

import java.io.Serializable;

/** A table allowing us to retrieve group members which
 * may themselves be groups.
 *
 *   @author Mike Douglass douglm@bedework.edu
 *  @version 1.0
 */
public class BwGroupEntry implements Serializable {
  /* group is a reserved word in (h)sql */
  private BwGroup grp;

  private int groupId;  // Generated

  private int memberId = CalFacadeDefs.unsavedItemKey;

  private boolean memberIsGroup;

  private BwPrincipal member;

  /** Constructor
   *
   */
  public BwGroupEntry() {
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /** Set the group
   *
   * @param val    BwGroup
   */
  public void setGrp(final BwGroup val) {
    grp = val;
  }

  /** Get the group
   *
   * @return BwGroup    the group
   */
  public BwGroup getGrp() {
    return grp;
  }

  /** Set the grp id
   *
   * @param val    int grp id
   */
  public void setGroupId(final int val) {
    groupId = val;
  }

  /** Get the grp id
   *
   * @return int    the grp id
   */
  public int getGroupId() {
    return groupId;
  }

  /** Set the member id
   *
   * @param val    int member id
   */
  public void setMemberId(final int val) {
    memberId = val;
  }

  /** Get the member id
   *
   * @return int    the member id
   */
  public int getMemberId() {
    return memberId;
  }

  /** Set the member is grp flag
   *
   * @param val    boolean member is grp flag
   */
  public void setMemberIsGroup(final boolean val) {
    memberIsGroup = val;
  }

  /** Get the member is grp flag
   *
   * @return boolean    the member is grp flag
   */
  public boolean getMemberIsGroup() {
    return memberIsGroup;
  }

  /** Set the member
   *
   * @param val    BwPrincipal member
   */
  public void setMember(final BwPrincipal val) {
    member = val;
    memberId = val.getId();
    memberIsGroup = (val instanceof BwGroup);
  }

  /** Get the member
   *
   * @return BwPrincipal    the member
   */
  public BwPrincipal getMember() {
    return member;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  /** Compare this and an object
   *
   * @param  o    object to compare.
   * @return int -1, 0, 1
   */
  public int compareTo(final Object o) {
    if (o == this) {
      return 0;
    }

    if (o == null) {
      return -1;
    }

    if (!(o instanceof BwGroupEntry)) {
      return -1;
    }

    BwGroupEntry that = (BwGroupEntry)o;

    int res = getGrp().compareTo(that.getGrp());
    if(res != 0) {
      return res;
    }

    res = Integer.compare(getMemberId(), that.getMemberId());
    if(res != 0) {
      return res;
    }

    return Boolean.compare(getMemberIsGroup(), that.getMemberIsGroup());
  }

  @Override
  public int hashCode() {
    int hc;

    if (getMemberIsGroup()) {
      hc = 1;
    } else {
      hc = 2;
    }

    return hc * getGrp().hashCode() * getMemberId();
  }

  @Override
  public boolean equals(final Object obj) {
    return compareTo(obj) == 0;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    BwPrincipal.toStringSegment(ts, "grp=", getGrp());
    ts.append("memberId", getMemberId());
    ts.append("memberIsGroup", getMemberIsGroup());

    return ts.toString();
  }
}
