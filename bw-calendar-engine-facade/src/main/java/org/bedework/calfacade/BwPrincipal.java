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

import org.bedework.access.AccessException;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.FromXmlCallback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
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
 *   @author Mike Douglass douglm rpi.edu
 *  @version 1.0
 */
@Dump(firstFields = {"account","principalRef"})
@JsonIgnoreProperties({"aclAccount"})
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="@class")
public abstract class BwPrincipal extends BwDbentity<BwPrincipal>
                                  implements AccessPrincipal,
                                  Comparator<BwPrincipal> {
  public final static String principalRoot = "/principals/";

  public final static String groupPrincipalRoot = "/principals/groups/";
  public final static String hostPrincipalRoot = "/principals/hosts/";
  public final static String resourcePrincipalRoot = "/principals/resources/";
  public final static String ticketPrincipalRoot = "/principals/tickets/";
  public final static String userPrincipalRoot = "/principals/users/";
  public final static String venuePrincipalRoot = "/principals/locations/";

  public final static String bwadmingroupPrincipalRoot =
          "/principals/groups/bwadmin/";

  public final static String publicUser = "public-user";

  private final static HashMap<String, Integer> toWho = new HashMap<>();
  private final static HashMap<Integer, String> fromWho = new HashMap<>();
  
  static {
    initWhoMaps(userPrincipalRoot, WhoDefs.whoTypeUser);
    initWhoMaps(groupPrincipalRoot, WhoDefs.whoTypeGroup);
    initWhoMaps(ticketPrincipalRoot, WhoDefs.whoTypeTicket);
    initWhoMaps(resourcePrincipalRoot, WhoDefs.whoTypeResource);
    initWhoMaps(venuePrincipalRoot, WhoDefs.whoTypeVenue);
    initWhoMaps(hostPrincipalRoot, WhoDefs.whoTypeHost);

    initWhoMaps(bwadmingroupPrincipalRoot, WhoDefs.whoTypeGroup);
  }
  
  /* The name by which this principal is identified, unique within
   * its kind
   */
  private String account;  // null for guest

  private String principalRef;  // null for guest

  protected Timestamp created;

  private String description;

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
   * @param whoType - type of principal
   * @return a principal based on type - null if unknown.
   */
  public static BwPrincipal makePrincipal(final int whoType) {
    if (whoType == WhoDefs.whoTypeUser) {
      return makeUserPrincipal();
    }

    if (whoType == WhoDefs.whoTypeGroup) {
      return makeGroupPrincipal();
    }

    if (whoType == WhoDefs.whoTypeVenue) {
      return makeLocationPrincipal();
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

  /**
   * @return a location/venue principal
   */
  public static BwPrincipal makeLocationPrincipal() {
    return new BwLocpr();
  }

  /* ====================================================================
   *                   Principal methods
   * ==================================================================== */

  public static boolean isPrincipal(final String href) {
    if (href == null) {
      return false;
    }

    /* assuming principal root is "principals" we expect something like
     * "/principals/users/jim".
     *
     * Anything with fewer or greater elements is a collection or entity.
     */

    int pos1 = href.indexOf("/", 1);

    if (pos1 < 0) {
      return false;
    }

    if (!href.substring(0, pos1 + 1).equals(principalRoot)) {
      return false;
    }

    int pos2 = href.indexOf("/", pos1 + 1);

    if (pos2 < 0) {
      return false;
    }

    for (String root: toWho.keySet()) {
      if (href.startsWith(root)) {
        return !href.equals(root);
      }
    }

    /*
    int pos3 = val.indexOf("/", pos2 + 1);

    if ((pos3 > 0) && (val.length() > pos3 + 1)) {
      // More than 3 elements
      return false;
    }

    if (!toWho.containsKey(val.substring(0, pos2))) {
      return false;
    }
    */

    /* It's one of our principal hierarchies */

    return false;
  }
  public static BwPrincipal makePrincipal(final String href) throws CalFacadeException {
    try {
      final String uri =
              URLDecoder.decode(new URI(
                      URLEncoder.encode(href, "UTF-8")
              ).getPath(), "UTF-8");

      if (!isPrincipal(uri)) {
        return null;
      }

      int start = -1;

      int end = uri.length();
      if (uri.endsWith("/")) {
        end--;
      }

      for (String prefix: toWho.keySet()) {
        if (!uri.startsWith(prefix)) {
          continue;
        }

        if (uri.equals(prefix)) {
          // Trying to browse user principals?
          return null;
        }

        int whoType = toWho.get(prefix);
        String who;

        if ((whoType == WhoDefs.whoTypeUser) ||
                (whoType == WhoDefs.whoTypeGroup)) {
          /* Strip off the principal prefix for real users.
           */
          who = uri.substring(prefix.length(), end);
        } else {
          who = uri;
        }

        final BwPrincipal p;

        if ((whoType == WhoDefs.whoTypeGroup) &&
                prefix.equals(bwadmingroupPrincipalRoot)) {
          p = new BwAdminGroup();
        } else {
          p = BwPrincipal.makePrincipal(whoType);
        }

        if (p != null) {
          p.setAccount(who);
          p.setPrincipalRef(uri);
          return p;
        }
      }

      throw new CalFacadeException(CalFacadeException.principalNotFound);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  public static String makePrincipalHref(final String id,
                                         final int whoType) throws AccessException {
    if (isPrincipal(id)) {
      return id;
    }

    final String root = fromWho.get(whoType);

    if (root == null) {
      throw new AccessException(CalFacadeException.unknownPrincipalType);
    }

    return Util.buildPath(true, root, "/", id);
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  @Override
  @NoDump
  public abstract int getKind();

  // Keep jackson happy
  public void setKind(final int val) {}
  
  @Override
  public void setUnauthenticated(final boolean val) {
    unauthenticated = val;
  }

  @Override
  @NoDump
  public boolean getUnauthenticated() {
    if (getAccount() == null) {
      return true;
    }

    return unauthenticated;
  }

  @Override
  public void setAccount(final String val) {
    account = val;
  }

  @Override
  public String getAccount() {
    return account;
  }

  @Override
  @NoDump
  public String getAclAccount() {
    return account;
  }

  @Override
  public void setPrincipalRef(final String val) {
    principalRef = val;
  }

  @Override
  public void setDescription(final String val) {
    description = val;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getPrincipalRef() {
    return principalRef;
  }

  /**
   * @param val timestamp
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
   * @param val timestamp
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
   * @param val timestamp
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
   * @param val timestamp
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
   * @param val quota
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
   * @return  String account name without any leading "/"
   */
  @NoDump
  @JsonIgnore
  public String getAccountNoSlash() {
    String res = getAccount();

    if (res.startsWith("/")) {
      res = res.substring(1);
    }

    if (res.endsWith("/")) {
      res = res.substring(0, res.length() - 1);
    }

    return res;
  }

  /**
   * @return  String[] account name split on "/"
   */
  @NoDump
  @JsonIgnore
  public String[] getAccountSplit() {
    final String res = getAccount();

    if (!res.contains("/")) {
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
  @JsonIgnore
  public Collection<BwGroup> getGroups() {
    if (groups == null) {
      groups = new TreeSet<>();
    }
    return groups;
  }

  /**
   * @param val info
   */
  public void setPrincipalInfo(final BwPrincipalInfo val) {
    principalInfo = val;
  }

  /**
   * @return  BwPrincipalInfo principal info
   */
  @JsonIgnore
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

  @Override
  public void setGroupNames(final Collection<String> val) {
    groupNames = val;
  }

  @Override
  @NoDump
  @JsonIgnore
  public Collection<String> getGroupNames() {
    if (groupNames == null) {
      groupNames = new TreeSet<>();
      for (final BwGroup group: getGroups()) {
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
   * @param ts ToString object
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

  private static void initWhoMaps(final String prefix,
                                  final int whoType) {
    toWho.put(prefix, whoType);
    fromWho.put(whoType, prefix);
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
   *                   Restore callback
   * ==================================================================== */

  private static FromXmlCallback fromXmlCb;

  @NoDump
  public static FromXmlCallback getRestoreCallback() {
    if (fromXmlCb == null) {
      fromXmlCb = new FromXmlCallback() {
        protected Timestamp lastAccess;

        /**
         * Last time principal modified something in our
         * system.
         */
        protected Timestamp lastModify;

        @Override
        public Object simpleValue(final Class cl,
                                  final String val) throws Throwable {
          if (cl.getCanonicalName()
                .equals(Timestamp.class.getCanonicalName())) {
            return Timestamp.valueOf(val);
          }

          return null;
        }
      };

      fromXmlCb.addSkips("id",
                         "seq");
    }
    
    return fromXmlCb;
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
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public abstract Object clone();
}
