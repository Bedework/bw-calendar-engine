package org.bedework.calcorei;

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;

import java.util.Collection;
import java.util.List;

/**
 * User: mike Date: 2/1/20 Time: 14:46
 */
public interface CorePrincialsAndPrefsI {
  /* ======================================================
   *                       User Auth
   * ====================================================== */

  /**
   * @param val auth user object
   */
  void addAuthUser(BwAuthUser val);

  /**
   * @param href - principal href for the entry
   * @return auth user with preferences or null
   */
  BwAuthUser getAuthUser(String href);

  /**
   * @param val auth user object
   */
  void updateAuthUser(BwAuthUser val);

  /**
   * @return list of all auth user entries
   */
  List<BwAuthUser> getAll();

  /* ======================================================
   *                       principals + prefs
   * ====================================================== */

  /** Find the principal with the given href.
   *
   * @param href          String principal hierarchy path
   * @return BwPrincipal  representing the principal or null if not there
   */
  BwPrincipal<?> getPrincipal(String href);

  /** Get a partial list of principal hrefs.
   *
   * @param start         Position to start
   * @param count         Number we want
   * @return list of hrefs - null for no more
   */
  List<String> getPrincipalHrefs(int start,
                                 int count);

  /** Fetch the preferences for the given principal.
   *
   * @param principalHref identifies principal
   * @return the preferences for the principal
   */
  BwPreferences getPreferences(String principalHref);

  /* =====================================================
   *                       adminprefs
   * ===================================================== */

  /* XXX These should no be required - there was some issue with earlier hibernate (maybe).
   */

  /** Remove any refs to this object
   *
   * @param val the entity
   */
  void removeFromAllPrefs(BwShareableDbentity<?> val);

  /* ====================================================
   *                       groups
   * ==================================================== */

  /* XXX This should really be some sort of directory function - perhaps
   * via carddav
   */

  /** Find a group given its account name
   *
   * @param  account           String group name
   * @param admin          true for an admin group
   * @return BwGroup        group object
   */
  BwGroup<?> findGroup(String account,
                       boolean admin);

  /**
   * @param group the group
   * @param admin          true for an admin group
   * @return Collection
   */
  Collection<BwGroup<?>> findGroupParents(
          BwGroup<?> group,
          boolean admin);

  /**
   * @param group to add
   * @param admin          true for an admin group
   */
  void addGroup(BwGroup<?> group,
                boolean admin);

  /**
   * @param group to update
   * @param admin          true for an admin group
   */
  void updateGroup(BwGroup<?> group,
                   boolean admin);

  /** Delete a group
   *
   * @param  group           BwGroup group object to delete
   * @param admin          true for an admin group
   */
  void removeGroup(BwGroup<?> group,
                   boolean admin);

  /** Add a member to a group
   *
   * @param group          a group principal
   * @param val             BwPrincipal new member
   * @param admin          true for an admin group
   */
  void addMember(BwGroup<?> group,
                 BwPrincipal<?> val,
                 boolean admin);

  /** Remove a member from a group
   *
   * @param group          a group principal
   * @param val            BwPrincipal new member
   * @param admin          true for an admin group
   */
  void removeMember(BwGroup<?> group,
                    BwPrincipal<?> val,
                    boolean admin);

  /** Get the direct members of the given group.
   *
   * @param  group           BwGroup group object to add
   * @param admin          true for an admin group
   * @return list of members
   */
  Collection<BwPrincipal<?>> getMembers(BwGroup<?> group,
                                        boolean admin);

  /** Return all groups to which this user has some access. Never returns null.
   *
   * @param admin          true for an admin group
   * @return Collection    of BwGroup
   */
  Collection<BwGroup<?>> getAllGroups(boolean admin);

  /** Return all admin groups to which this user has some access. Never returns null.
   *
   * @return Collection    of BwAdminGroup
   */
  Collection<BwAdminGroup> getAdminGroups();

  /** Return all groups of which the given principal is a member. Never returns null.
   *
   * <p>Does not check the returned groups for membership of other groups.
   *
   * @param val            a principal
   * @param admin          true for an admin group
   * @return Collection    of BwGroup
   */
  Collection<BwGroup<?>> getGroups(BwPrincipal<?> val,
                                   boolean admin);

  /** Return all admin groups of which the given principal is a member. Never returns null.
   *
   * <p>Does not check the returned groups for membership of other groups.
   *
   * @param val            a principal
   * @return Collection    of BwGroup
   */
  Collection<BwAdminGroup> getAdminGroups(
          BwPrincipal<?> val);
}
