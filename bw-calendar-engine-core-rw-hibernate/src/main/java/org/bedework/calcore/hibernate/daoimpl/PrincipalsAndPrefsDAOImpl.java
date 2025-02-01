/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.access.WhoDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.rw.common.dao.PrincipalsAndPrefsDAO;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwGroupEntry;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.database.db.DbSession;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class PrincipalsAndPrefsDAOImpl extends DAOBaseImpl
        implements PrincipalsAndPrefsDAO {
  /**
   * @param sess             the session
   */
  public PrincipalsAndPrefsDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return PrincipalsAndPrefsDAOImpl.class.getName();
  }

  @Override
  public void add(final BwGroupEntry val) {
    getSess().add(val);
  }

  /* =====================================================
   *                       principals + prefs
   * ===================================================== */

  private final static String getPrincipalQuery =
          "select u from BwUser u " +
                  "where u.principalRef = :href";

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    final var sess = getSess();

    if (href == null) {
      return null;
    }

    /* XXX We should cache these as a static map and return detached objects only.
     * Updating the user for logon etc should be a separate method,
     *
     * Also - we are searching the user table at the moment. Make this into a
     * principal table and allow any principal to log on and own entities.
     */

    if (sess == null) {
      warn("Null sesssion");
      throw new NullPointerException("No session");
    }
    sess.createQuery(getPrincipalQuery);

    sess.setString("href", href);

    return (BwPrincipal<?>)sess.getUnique();
  }

  private static final String getPrincipalHrefsQuery =
          "select u.principalRef from BwUser u " +
                  "order by u.principalRef";

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    final var sess = getSess();

    sess.createQuery(getPrincipalHrefsQuery);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    @SuppressWarnings("unchecked")
    final List<String> res = (List<String>)sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  private static final String getOwnerPreferencesQuery =
          "select p from BwPreferences p " +
                  "where p.ownerHref=:ownerHref";

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    final var sess = getSess();

    sess.createQuery(getOwnerPreferencesQuery);
    sess.setString("ownerHref", principalHref);

    return (BwPreferences)sess.getUnique();
  }

  /* ======================================================
   *                       admin groups
   * ====================================================== */

  private static final String getAdminGroupQuery =
          "select ag from BwAdminGroup ag " +
                  "where ag.account = :account";

  private static final String getGroupQuery =
          "select g from BwGroup g " +
                  "where g.account = :account";

  @Override
  public BwGroup<?> findGroup(final String account,
                              final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(getAdminGroupQuery);
    } else {
      sess.createQuery(getGroupQuery);
    }

    sess.setString("account", account);

    return (BwGroup<?>)sess.getUnique();
  }

  private static final String getAdminGroupParentsQuery =
          "select ag from BwAdminGroupEntry age, BwAdminGroup ag " +
                  "where ag.id = age.groupId and " +
                  "age.memberId=:grpid and age.memberIsGroup=true";

  private static final String getGroupParentsQuery =
          "select g from BwGroupEntry ge, BwGroup g " +
                  "where g.id = ge.groupId and " +
                  "ge.memberId=:grpid and ge.memberIsGroup=true";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwGroup<?>> findGroupParents(
          final BwGroup<?> group,
          final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(getAdminGroupParentsQuery);
    } else {
      sess.createQuery(getGroupParentsQuery);
    }

    sess.setInt("grpid", group.getId());

    return (Collection<BwGroup<?>>)sess.getList();
  }

  private static final String removeAllAdminGroupMemberRefsQuery =
          "delete from BwAdminGroupEntry " +
                  "where grp=:gr";
  private static final String removeAllGroupMembersQuery =
          "delete from BwGroupEntry " +
                  "where grp=:gr";

  private static final String removeFromAllAdminGroupsQuery =
          "delete from BwAdminGroupEntry " +
                  "where memberId=:mbrId and memberIsGroup=:isgroup";

  private static final String removeFromAllGroupsQuery =
          "delete from BwGroupEntry " +
                  "where memberId=:mbrId and memberIsGroup=:isgroup";

  @Override
  public void removeGroup(final BwGroup<?> group,
                          final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(removeAllAdminGroupMemberRefsQuery);
    } else {
      sess.createQuery(removeAllGroupMembersQuery);
    }

    sess.setEntity("gr", group);
    sess.executeUpdate();

    // Remove from any groups

    if (admin) {
      sess.createQuery(removeFromAllAdminGroupsQuery);
    } else {
      sess.createQuery(removeFromAllGroupsQuery);
    }

    sess.setInt("mbrId", group.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    */
    sess.setString("isgroup", "T");
    sess.executeUpdate();

    sess.delete(group);
  }

  private static final String findAdminGroupEntryQuery =
          "select age from BwAdminGroupEntry age " +
                  "where age.grp=:grp and age.memberId=:mbrId " +
                  "and age.memberIsGroup=:isgroup";

  private static final String findGroupEntryQuery =
          "select ge from BwGroupEntry " +
                  "where ge.grp=:grp and ge.memberId=:mbrId " +
                  "and ge.memberIsGroup=:isgroup";

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val,
                           final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(findAdminGroupEntryQuery);
    } else {
      sess.createQuery(findGroupEntryQuery);
    }

    sess.setEntity("grp", group);
    sess.setInt("mbrId", val.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    */
    if (val instanceof BwGroup) {
      sess.setString("isgroup", "T");
    } else {
      sess.setString("isgroup", "F");
    }

    final Object ent = sess.getUnique();

    if (ent == null) {
      return;
    }

    sess.delete(ent);
  }

  private static final String getAdminGroupUserMembersQuery =
          "select u from BwAdminGroupEntry age, BwUser u " +
                  "where u.id = age.memberId and " +
                  "age.grp=:gr and age.memberIsGroup=false";

  private static final String getAdminGroupGroupMembersQuery =
          "select ag from BwAdminGroupEntry age, " +
                  "BwAdminGroup ag " +
                  "where ag.id = age.memberId and " +
                  "age.grp=:gr and age.memberIsGroup=true";

  private static final String getGroupUserMembersQuery =
          "select u from BwGroupEntry ge, BwUser u " +
                  "where u.id = ge.memberId and " +
                  "ge.grp=:gr and ge.memberIsGroup=false";

  private static final String getGroupGroupMembersQuery =
          "select g from BwGroupEntry ge, BwGroup g " +
                  "where g.id = ge.memberId and " +
                  "ge.grp=:gr and ge.memberIsGroup=true";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwPrincipal<?>> getMembers(final BwGroup<?> group,
                                               final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(getAdminGroupUserMembersQuery);
    } else {
      sess.createQuery(getGroupUserMembersQuery);
    }

    sess.setEntity("gr", group);

    final Collection<BwPrincipal<?>> ms =
            new TreeSet<>(
                    (Collection<? extends BwPrincipal<?>>)sess.getList());

    if (admin) {
      sess.createQuery(getAdminGroupGroupMembersQuery);
    } else {
      sess.createQuery(getGroupGroupMembersQuery);
    }

    sess.setEntity("gr", group);

    ms.addAll((Collection<? extends BwPrincipal<?>>)sess.getList());

    return ms;
  }

  private static final String getAllAdminGroupsQuery =
          "select ag from BwAdminGroup ag " +
                  "order by ag.account";

  private static final String getAllGroupsQuery =
          "select g from BwGroup g " +
                  "order by g.account";

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwGroup<?>> Collection<T> getAllGroups(
          final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(getAllAdminGroupsQuery);
    } else {
      sess.createQuery(getAllGroupsQuery);
    }

    return (Collection<T>)sess.getList();
  }

  /* Groups principal is a member of */
  private static final String getAdminGroupsQuery =
          "select ag.grp from BwAdminGroupEntry ag " +
                  "where ag.memberId=:entId and ag.memberIsGroup=:isgroup";

  /* Groups principal is a event owner for */
  private static final String getAdminGroupsByEventOwnerQuery =
          "select ag from BwAdminGroup ag " +
                  "where ag.ownerHref=:ownerHref";

  private static final String getGroupsQuery =
          "select g.grp from BwGroupEntry g " +
                  "where g.memberId=:entId and g.memberIsGroup=:isgroup";

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwGroup<?>> Collection<T> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    final var sess = getSess();

    if (admin) {
      sess.createQuery(getAdminGroupsQuery);
    } else {
      sess.createQuery(getGroupsQuery);
    }

    sess.setInt("entId", val.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    */
    sess.setBool("isgroup", (val instanceof BwGroup));
    /*
    if (val.getKind() == WhoDefs.whoTypeGroup) {
      sess.setString("isgroup", "T");
    } else {
      sess.setString("isgroup", "F");
    }*/

    final Set<BwGroup<?>> gs =
            new TreeSet<>(
                    (Collection<? extends BwGroup<?>>)sess.getList());

    if (admin && (val.getKind() == WhoDefs.whoTypeUser)) {
      /* Event owner for group is implicit member of group. */

      sess.createQuery(getAdminGroupsByEventOwnerQuery);
      sess.setString("ownerHref", val.getPrincipalRef());

      gs.addAll((Collection<? extends BwGroup<?>>)sess.getList());
    }

    return (Collection<T>)gs;
  }

  /* ==========================================================
   *                       adminprefs
   * ========================================================== */

  private static final String removeCalendarPrefForAllQuery =
          "delete from BwAuthUserPrefsCalendar " +
                  "where calendarid=:id";

  private static final String removeCategoryPrefForAllQuery =
          "delete from BwAuthUserPrefsCategory " +
                  "where categoryid=:id";

  private static final String removeLocationPrefForAllQuery =
          "delete from BwAuthUserPrefsLocation " +
                  "where locationid=:id";

  private static final String removeContactPrefForAllQuery =
          "delete from BwAuthUserPrefsContact " +
                  "where contactid=:id";

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    final var sess = getSess();

    final String q;

    if (val instanceof BwCategory) {
      q = removeCategoryPrefForAllQuery;
    } else if (val instanceof BwCalendar) {
      q = removeCalendarPrefForAllQuery;
    } else if (val instanceof BwContact) {
      q = removeContactPrefForAllQuery;
    } else if (val instanceof BwLocation) {
      q = removeLocationPrefForAllQuery;
    } else {
      throw new BedeworkException("Can't handle " + val);
    }

    sess.createQuery(q);
    sess.setInt("id", val.getId());

    sess.executeUpdate();
  }

  /* ==========================================================
   *                       auth users
   * ========================================================== */

  private final static String getUserQuery =
          "select au from BwAuthUser au " +
                  "where au.userHref = :userHref";

  @Override
  public BwAuthUser getAuthUser(final String href) {
    final var sess = getSess();
    
    sess.createQuery(getUserQuery);
    sess.setString("userHref", href);

    return (BwAuthUser)sess.getUnique();
  }

  private final static String getAllAuthUsersQuery =
          "select au from BwAuthUser au " +
                  "order by au.userHref";

  @SuppressWarnings("unchecked")
  @Override
  public List<BwAuthUser> getAllAuthUsers() {
    final var sess = getSess();

    sess.createQuery(getAllAuthUsersQuery);

    return (List<BwAuthUser>)sess.getList();
  }
}
