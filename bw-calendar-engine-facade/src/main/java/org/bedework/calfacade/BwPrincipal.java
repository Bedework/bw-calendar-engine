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

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.util.CalFacadeUtil;

import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.cmt.access.WhoDefs;
import edu.rpi.sss.util.ToString;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Comparator;
import java.util.TreeSet;

/** Value object to represent a calendar principal. Principals may be users,
 * groups or other special obects. Principals may own objects within the
 * system or simply identify a client to the system.
 *
 * <p>We need to address a problem that might occur with groups. If we choose
 * to allow a group all the facilities of a single user (subscriptions,
 * ownership etc) then we need to be careful with names and their uniqueness.
 *
 * <p>That is to say, the name will probably not be unique, for example, I might
 * have the id douglm and be a member of the group douglm.
 *
 * <p>Allowing groups all the rights of a user gives us the current functionality
 * of administrative groups as well as the functions we need for departmental
 * sites.
 *
 *   @author Mike Douglass douglm@rpi.edu
 *  @version 1.0
 */
@Dump(firstFields = {"account","principalRef"})
public abstract class BwPrincipal extends BwDbentity<BwPrincipal>
                                  implements AccessPrincipal,
                                  Comparator<BwPrincipal> {
  /** The name by which this principal is identified, unique within
   * its kind
   */
  private String account;  // null for guest

  private String principalRef;  // null for guest

  protected Timestamp created;

  /** Last time we saw this principal appear in our system.
   */
  protected Timestamp logon;

  /** Last time principal did something in our system.
   */
  protected Timestamp lastAccess;

  /** Last time principal modified something in our system.
   */
  protected Timestamp lastModify;

  private long quota;

  /** Default access to category entries
   */
  protected String categoryAccess;

  /** Default access to contact entries
   */
  protected String contactAccess;

  /** Default access to location entries
   */
  protected String locationAccess;

  /* ...................................................................
              Non-db fields
     .................................................................... */

  protected boolean unauthenticated;

  protected BwPrincipalInfo principalInfo;

  /* ...................................................................
              Non-db fields
     .................................................................... */

  /* groups of which this user is a member */
  protected Collection<BwGroup> groups;

  // Derived from the groups.
  protected Collection<String> groupNames;

  /* ====================================================================
   *                   Constructors
   * ==================================================================== */

  /** Create a guest BwPrincipal
   */
  public BwPrincipal() {
  }

  /* ====================================================================
   *                   Factories
   * ==================================================================== */

  /**
   * @param whoType
   * @return a principal based on type - null if unknown.
   */
  public static BwPrincipal makePrincipal(final int whoType) {
    if (whoType == WhoDefs.whoTypeUser) {
      return makeUserPrincipal();
    }

    if (whoType == WhoDefs.whoTypeGroup) {
      return makeGroupPrincipal();
    }

    return null;
  }

  /**
   * @return a user principal
   */
  public static BwPrincipal makeUserPrincipal() {
    return new BwUser();
  }

  /**
   * @return a group principal
   */
  public static BwPrincipal makeGroupPrincipal() {
    return new BwGroup();
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /**
   * @return int kind
   */
  @Override
  @NoDump
  public abstract int getKind();

  /** Set the unauthenticated state.
   *
   * @param val
   */
  @Override
  public void setUnauthenticated(final boolean val) {
    unauthenticated = val;
  }

  /**
   * @return  boolean authenticated state
   */
  @Override
  @NoDump
  public boolean getUnauthenticated() {
    if (getAccount() == null) {
      return true;
    }

    return unauthenticated;
  }

  /**
   * @param val
   */
  @Override
  public void setAccount(final String val) {
    account = val;
  }

  /**
   * @return  String account name
   */
  @Override
  public String getAccount() {
    return account;
  }

  @Override
  public void setPrincipalRef(final String val) {
    principalRef = val;
  }

  /**
   * @return  String principal reference
   */
  @Override
  public String getPrincipalRef() {
    return principalRef;
  }

  /**
   * @param val
   */
  public void setCreated(final Timestamp val) {
    created = val;
  }

  /**
   * @return Timestamp created
   */
  public Timestamp getCreated() {
    return created;
  }

  /**
   * @param val
   */
  public void setLogon(final Timestamp val) {
    logon = val;
  }

  /**
   * @return Timetstamp last logon
   */
  public Timestamp getLogon() {
    return logon;
  }

  /**
   * @param val
   */
  public void setLastAccess(final Timestamp val) {
    lastAccess = val;
  }

  /**
   * @return Timestamp last access
   */
  public Timestamp getLastAccess() {
    return lastAccess;
  }

  /**
   * @param val
   */
  public void setLastModify(final Timestamp val) {
    lastModify = val;
  }

  /**
   * @return Timestamp last mod
   */
  public Timestamp getLastModify() {
    return lastModify;
  }

  /** Quota for this user. This will have to be an estimate I imagine.
   *
   * @param val
   */
  public void setQuota(final long val) {
    quota = val;
  }

  /**
   * @return long
   */
  public long getQuota() {
    return quota;
  }

  /**
   * @param val The categoryAccess to set.
   */
  public void setCategoryAccess(final String val) {
    categoryAccess = val;
  }

  /**
   * @return Returns the categoryAccess.
   */
  public String getCategoryAccess() {
    return categoryAccess;
  }

  /**
   * @param val The locationAccess to set.
   */
  public void setLocationAccess(final String val) {
    locationAccess = val;
  }

  /**
   * @return Returns the locationAccess.
   */
  public String getLocationAccess() {
    return locationAccess;
  }

  /**
   * @param val The contactAccess to set.
   */
  public void setContactAccess(final String val) {
    contactAccess = val;
  }

  /**
   * @return Returns the contactAccess.
   */
  public String getContactAccess() {
    return contactAccess;
  }

  /* ====================================================================
   *                   Non-db methods
   * ==================================================================== */

  /**
   * @return  String[] account name split on "/"
   */
  @NoDump
  public String[] getAccountSplit() {
    String res = getAccount();

    if (res.indexOf("/") < 0) {
      return new String[]{res};
    }

    return res.split("/");
  }

  /** Set of groups of which principal is a member. These are not just those
   * of which the principal is a direct member but also those it is a member of
   * by virtue of membership of other groups. For example <br/>
   * If the principal is a member of groupA and groupA is a member of groupB
   * the groupB should appear in the list.
   *
   * @param val        Collection of BwPrincipal
   */
  public void setGroups(final Collection<BwGroup> val) {
    groupNames = null;
    groups = val;
  }

  /** Get the groups of which principal is a member.
   *
   * @return Collection    of BwGroup
   */
  @NoDump
  public Collection<BwGroup> getGroups() {
    if (groups == null) {
      groups = new TreeSet<BwGroup>();
    }
    return groups;
  }

  /**
   * @param val
   */
  public void setPrincipalInfo(final BwPrincipalInfo val) {
    principalInfo = val;
  }

  /**
   * @return  BwPrincipalInfo principal info
   */
  public BwPrincipalInfo getPrincipalInfo() {
    return principalInfo;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /**
   * @param val BwPrincipal
   */
  public void addGroup(final BwGroup val) {
    getGroups().add(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cmt.access.AccessPrincipal#setGroupNames(java.util.Collection)
   */
  @Override
  public void setGroupNames(final Collection<String> val) {
    groupNames = val;
  }

  /* (non-Javadoc)
   * @see edu.rpi.cmt.access.AccessPrincipal#getGroupNames()
   */
  @Override
  @NoDump
  public Collection<String> getGroupNames() {
    if (groupNames == null) {
      groupNames = new TreeSet<String>();
      for (BwGroup group: getGroups()) {
        groupNames.add(group.getPrincipalRef());
      }
    }
    return groupNames;
  }

  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);
    ts.append("account", getAccount())
      .append("pref", getPrincipalRef())
      .append("created", getCreated())
      .newLine()
      .append("logon", getLogon())
      .append("lastAccess", getLastAccess())
      .append("lastModify", getLastModify())
      .append("kind", getKind());
    ts.append("quota", getQuota());
  }

  /** Add a principal to the ToString object
   *
   * @param ts
   * @param name  tag
   * @param val   BwPrincipal
   */
  public static void toStringSegment(final ToString ts,
                                     final String name,
                                     final BwPrincipal val) {
    if (val == null) {
      ts.append(name, "**NULL**");
    } else {
      ts.append(name, "(" + val.getId() + ", " + val.getPrincipalRef() + ")");
    }
  }

  /* ====================================================================
   *                   Copying methods
   * ==================================================================== */

  /** Copy this to val
   *
   * @param val BwPrincipal target
   */
  public void copyTo(final BwPrincipal val) {
    val.setAccount(getAccount());
    val.setPrincipalRef(getPrincipalRef());
    val.setId(getId());
    val.setSeq(getSeq());
    val.setCreated(getCreated());
    val.setLogon(getLogon());
    val.setLastAccess(getLastAccess());
    val.setLastModify(getLastModify());
    val.setCategoryAccess(getCategoryAccess());
    val.setLocationAccess(getLocationAccess());
    val.setContactAccess(getContactAccess());

    val.setGroups(getGroups());  // XXX this should be cloned
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwPrincipal o) {
    return compare(this, o);
  }

  @Override
  public int compare(final BwPrincipal p1, final BwPrincipal p2) {
    if (p1.getKind() < p2.getKind()) {
      return -1;
    }

    if (p1.getKind() > p2.getKind()) {
      return 1;
    }

    return CalFacadeUtil.compareStrings(p1.getAccount(), p2.getAccount());
  }

  @Override
  public int hashCode() {
    int hc = 7 * (getKind() + 1);

    if (account != null) {
      hc = account.hashCode();
    }

    return hc;
  }

  @Override
  public boolean equals(final Object o) {
    return compareTo((BwPrincipal)o) == 0;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public abstract Object clone();
}
