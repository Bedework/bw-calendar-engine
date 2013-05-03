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
package org.bedework.calfacade.ifs;

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.configs.CalAddrPrefixes;
import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;

/** An interface to handle directory information and groups.
 *
 * <p>Groups may be stored in a site specific manner so the actual
 * implementation used is a build-time configuration option. They may be
 * ldap directory based or implemented by storing in the calendar database.
 *
 * <p>Methods may throw an unimplemented exception if functions are not
 * available.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 3.3.2
 */
public interface Directories extends Serializable {

  /** Class to be implemented by caller and passed during init.
   */
  public static abstract class CallBack implements Serializable {
    /** Get a name uniquely.identifying this system. This should take the form <br/>
     *   name@host
     * <br/>where<ul>
     * <li>name identifies the particular calendar system at the site</li>
     * <li>host part identifies the domain of the site.</li>..
     * </ul>
     *
     * @return String    globally unique system identifier.
     * @throws CalFacadeException
     */
    public abstract String getSysid() throws CalFacadeException;

    /**
     * @return BwUser representing current user
     * @throws CalFacadeException
     */
    public abstract BwPrincipal getCurrentUser() throws CalFacadeException;

    /** Find a group given its account name
     *
     * @param  account           String group name
     * @param admin          true for an admin group
     * @return BwGroup        group object
     * @exception CalFacadeException If there's a problem
     */
    public abstract BwGroup findGroup(final String account,
                                      boolean admin) throws CalFacadeException;

    /**
     * @param group
     * @param admin          true for an admin group
     * @return Collection
     * @throws CalFacadeException
     */
    public abstract Collection<BwGroup> findGroupParents(final BwGroup group,
                                                         boolean admin) throws CalFacadeException;

    /**
     * @param group
     * @param admin          true for an admin group
     * @throws CalFacadeException
     */
    public abstract void updateGroup(final BwGroup group,
                                     boolean admin) throws CalFacadeException;

    /** Delete a group
     *
     * @param  group           BwGroup group object to delete
     * @param admin          true for an admin group
     * @exception CalFacadeException If there's a problem
     */
    public abstract void removeGroup(BwGroup group,
                                     boolean admin) throws CalFacadeException;

    /** Add a member to a group
     *
     * @param group          a group principal
     * @param val             BwPrincipal new member
     * @param admin          true for an admin group
     * @exception CalFacadeException   For invalid usertype values.
     */
    public abstract void addMember(BwGroup group,
                                   BwPrincipal val,
                                   boolean admin) throws CalFacadeException;

    /** Remove a member from a group
     *
     * @param group          a group principal
     * @param val            BwPrincipal member
     * @param admin          true for an admin group
     * @exception CalFacadeException   For invalid usertype values.
     */
    public abstract void removeMember(BwGroup group,
                                      BwPrincipal val,
                                      boolean admin) throws CalFacadeException;

    /** Get the direct members of the given group.
     *
     * @param  group           BwGroup group object to add
     * @param admin          true for an admin group
     * @return list of members
     * @throws CalFacadeException
     */
    public abstract Collection<BwPrincipal> getMembers(BwGroup group,
                                                       boolean admin) throws CalFacadeException;

    /** Return all groups to which this user has some access. Never returns null.
     *
     * @param admin          true for an admin group
     * @return Collection    of BwGroup
     * @throws CalFacadeException
     */
    public abstract Collection<BwGroup> getAll(boolean admin) throws CalFacadeException;

    /** Return all groups of which the given principal is a member. Never returns null.
     *
     * <p>Does not check the returned groups for membership of other groups.
     *
     * @param val            a principal
     * @param admin          true for an admin group
     * @return Collection    of BwGroup
     * @throws CalFacadeException
     */
    public abstract Collection<BwGroup> getGroups(BwPrincipal val,
                                                  boolean admin) throws CalFacadeException;
  }

  /** Provide the callback object
   *
   * @param cb
   * @param caPrefixes for principals etc.
   * @param authCdinfo
   * @param unauthCdinfo
   * @param dirProps
   * @throws CalFacadeException
   */
  void init(CallBack cb,
            CalAddrPrefixes caPrefixes,
            CardDavInfo authCdinfo,
            CardDavInfo unauthCdinfo,
            DirConfigProperties dirProps) throws CalFacadeException;

  /** Return the name of the configuration properties for the module,
   * e.g "module.user-ldap-group" or "module.dir-config"
   * @return String
   */
  String getConfigName();

  /** Get application visible directory information.
   *
   * @return DirectoryInfo
   * @throws CalFacadeException
   */
  DirectoryInfo getDirectoryInfo() throws CalFacadeException;

  /** Test for a valid principal in the directory. This may have a number of
   * uses. For example, when organizing meetings we may want to send an
   * invitation to a user who has not yet logged on. This allows us to
   * distinguish between a bad account (spam maybe?) and a real account.
   *
   * <p>Sites may wish to override this method to check their directory to see
   * if the principal exists.
   *
   * @param href
   * @return true if it's a valid principal
   * @throws CalFacadeException
   */
  boolean validPrincipal(String href) throws CalFacadeException;

  /** Does the value appear to represent a valid principal?
   *
   * @param val
   * @return true if it's a (possible) principal
   * @throws CalFacadeException
   */
  boolean isPrincipal(String val) throws CalFacadeException;

  /** Return principal for the given href.
   *
   * @param href
   * @return Principal
   * @throws CalFacadeException
   */
  BwPrincipal getPrincipal(String href) throws CalFacadeException;

  /** Needed for the ischedule service
   *
   * @return the default domain for the service.
   * @throws CalFacadeException
   */
  String getDefaultDomain() throws CalFacadeException;

  /** The urls should be principal urls. principalUrl can null for the current user.
   * The result is a collection of principal urls of which the given url is a
   * member, based upon rootUrl. For example, if rootUrl points to the base of
   * the user principal hierarchy, then the rsult should be at least the current
   * user's principal url, remembering that user principals are themselves groups
   * and the user is considered a member of their own group.
   *
   * @param rootUrl - url to base search on.
   * @param principalUrl - url of principal or null for current user
   * @return Collection of urls - always non-null
   * @throws CalFacadeException
   */
  Collection<String>getGroups(String rootUrl,
                                     String principalUrl) throws CalFacadeException;

  /**
   * @param id
   * @param whoType - from WhoDefs
   * @return String principal uri
   * @throws CalFacadeException
   */
  String makePrincipalUri(String id,
                                 int whoType) throws CalFacadeException;

  /** Used by caldav to return the root of the principal hierarchy
   * @return String principal root
   * @throws CalFacadeException
   */
  String getPrincipalRoot() throws CalFacadeException;

  /** Given a uri return a calendar address.
   * This should handle actions such as turning<br/>
   *   auser
   * <br/>into the associated calendar address of <br/>
   *   mailto:auser@ahost.org
   *
   * <p>It should also deal with user@somewhere.org to
   * mailto:user@somewhere.org
   *
   * <p>Note: this method and userToCalAddr should be doing lookups of the
   * enterprise directory (or carddav) to determine the calendar user address.
   * For the moment we do a transform of the account to get a mailto.
   *
   * @param val        uri
   * @return caladdr for this system or null for an invalid uri
   * @throws CalFacadeException  for errors
   */
  abstract String uriToCaladdr(String val) throws CalFacadeException;

  /** Given a user principal return a calendar address.
   *
   * @param val        principal
   * @return caladdr
   * @throws CalFacadeException  for errors
   */
  abstract String principalToCaladdr(BwPrincipal val) throws CalFacadeException;

  /** Given a user account return a calendar address.
   * For example, we might have an account<br/>
   *   auser
   * <br/>with the associated calendar address of <br/>
   *   mailto:auser@ahost.org
   *
   * <p>Note: this method and uriToCalAddr should be doing lookups of the
   * enterprise directory (or carddav) to determine the calendar user address.
   * For the moment we do a transform of the account to get a mailto.
   *
   * @param val        account
   * @return caladdr for this system
   * @throws CalFacadeException  for errors
   */
  abstract String userToCaladdr(String val) throws CalFacadeException;

  /** Given a calendar address return the associated calendar account.
   * For example, we might have a calendar address<br/>
   *   mailto:auser@ahost.org
   * <br/>with the associated account of <br/>
   * auser<br/>
   *
   * <p>We also allow user principals
   *
   * <p>Wherever we need a user account use the converted value. Call
   * userToCaladdr for the inverse.
   *
   * @param caladdr      calendar address
   * @return account or null if not caladdr for this system
   * @throws CalFacadeException  for errors
   */
  abstract BwPrincipal caladdrToPrincipal(String caladdr) throws CalFacadeException;

  /** Ensure we have something that looks like a valid calendar user address.
   * Could be a mailto: or a principal
   *
   * @param val String potential calendar user address
   * @return String valid or null invalid.
   * @throws CalFacadeException
   */
  abstract String normalizeCua(String val) throws CalFacadeException;

  /** Return some sort of directory information for the given principal.
   *
   * @param p                principal for which we want info
   * @return BwPrincipalInfo directory information.
   * @throws CalFacadeException
   */
  BwPrincipalInfo getDirInfo(BwPrincipal p) throws CalFacadeException;

  /** Uses the values in pinfo to update the supplied preferences. This may be a
   * site specific operation. It allows bedework to use directory information
   * to alter the behavior of principals. For example, we can define resources
   * in a directory and the auto-respond behavior will be turned on in bedework
   * scheduling.
   *
   * @param prefs
   * @param pinfo
   * @return boolean true if preferences updated
   * @throws CalFacadeException
   */
  boolean mergePreferences(BwPreferences prefs,
                                  BwPrincipalInfo pinfo) throws CalFacadeException;

  /** Return all groups of which the given principal is a member. Never returns null.
   *
   * <p>Does not check the returned groups for membership of other groups.
   *
   * @param val            a principal
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwGroup> getGroups(BwPrincipal val) throws CalFacadeException;

  /** Return all groups of which the given principal is a member. Never returns null.
   *
   * <p>This does check the groups for membership of other groups so the
   * returned collection gives the groups of which the principal is
   * directly or indirectly a member.
   *
   * @param val            a principal
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwGroup> getAllGroups(BwPrincipal val) throws CalFacadeException;

  /** Show whether entries can be modified with this
   * class. Some sites may use other mechanisms.
   *
   * @return boolean    true if group maintenance is implemented.
   */
  boolean getGroupMaintOK();

  /** Return all groups to which this user has some access. Never returns null.
   *
   * @param  populate      boolean populate with members
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwGroup> getAll(boolean populate) throws CalFacadeException;

  /** Populate the group with a (possibly empty) Collection of members. Does not
   * populate groups which are members.
   *
   * @param  group           BwGroup group object to add
   * @throws CalFacadeException
   */
  void getMembers(BwGroup group) throws CalFacadeException;

  /* ====================================================================
   *  The following are available if group maintenance is on.
   * ==================================================================== */

  /** Add a group
   *
   * @param  group           BwGroup group object to add
   * @exception CalFacadeException If there's a problem
   */
  void addGroup(BwGroup group) throws CalFacadeException;

  /** Find a group given its name
   *
   * @param  name           String group name
   * @return BwGroup        group object
   * @exception CalFacadeException If there's a problem
   */
  BwGroup findGroup(String name) throws CalFacadeException;

  /** Add a principal to a group
   *
   * @param group          a group principal
   * @param val            BwPrincipal new member
   * @exception CalFacadeException   For invalid usertype values.
   */
  void addMember(BwGroup group, BwPrincipal val) throws CalFacadeException;

  /** Remove a member from a group
   *
   * @param group          a group principal
   * @param val            BwPrincipal new member
   * @exception CalFacadeException   For invalid usertype values.
   */
  void removeMember(BwGroup group, BwPrincipal val) throws CalFacadeException;

  /** Delete a group
   *
   * @param  group           BwGroup group object to delete
   * @exception CalFacadeException If there's a problem
   */
  void removeGroup(BwGroup group) throws CalFacadeException;

  /** update a group. This may have no meaning in some directories.
   *
   * @param  group           BwGroup group object to update
   * @exception CalFacadeException If there's a problem
   */
  void updateGroup(BwGroup group) throws CalFacadeException;

  /**
   * @param group
   * @return Collection
   * @throws CalFacadeException
   */
  Collection<BwGroup> findGroupParents(BwGroup group) throws CalFacadeException;

  /**
   * @return String used to prefix administrative group names to distinguish
   *         them from user group names.
   */
  String getAdminGroupsIdPrefix();
}
