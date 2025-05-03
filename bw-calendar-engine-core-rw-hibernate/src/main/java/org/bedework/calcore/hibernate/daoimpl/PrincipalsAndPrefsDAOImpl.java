/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.access.WhoDefs;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.rw.common.dao.PrincipalsAndPrefsDAO;
import org.bedework.calfacade.BwCollection;
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
    if (href == null) {
      return null;
    }

    /* XXX We should cache these as a static map and return detached objects only.
     * Updating the user for logon etc should be a separate method,
     *
     * Also - we are searching the user table at the moment. Make this into a
     * principal table and allow any principal to log on and own entities.
     */

    return (BwPrincipal<?>)createQuery(getPrincipalQuery)
            .setString("href", href)
            .getUnique();
  }

  private static final String getPrincipalHrefsQuery =
          "select u.principalRef from BwUser u " +
                  "order by u.principalRef";

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    @SuppressWarnings("unchecked")
    final var res = (List<String>)createQuery(getPrincipalHrefsQuery)
            .setFirstResult(start)
            .setMaxResults(count)
            .getList();

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
    return (BwPreferences)createQuery(getOwnerPreferencesQuery)
            .setString("ownerHref", principalHref)
            .getUnique();
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
    final String q;

    if (admin) {
      q = getAdminGroupQuery;
    } else {
      q = getGroupQuery;
    }

    return (BwGroup<?>)createQuery(q)
            .setString("account", account)
            .getUnique();
  }

  private static final String getAdminGroupParentsQuery =
          "select ag from BwAdminGroupEntry age, BwAdminGroup ag " +
                  "where ag.id = age.groupId and " +
                  "age.memberId=:grpid and age.memberIsGroup=true";

  private static final String getGroupParentsQuery =
          "select g from BwGroupEntry ge, BwGroup g " +
                  "where g.id = ge.groupId and " +
                  "ge.memberId=:grpid and ge.memberIsGroup=true";

  @Override
  public Collection<BwGroup<?>> findGroupParents(
          final BwGroup<?> group,
          final boolean admin) {
    final String q;

    if (admin) {
      q = getAdminGroupParentsQuery;
    } else {
      q = getGroupParentsQuery;
    }

    //noinspection unchecked
    return (Collection<BwGroup<?>>)createQuery(q)
            .setInt("grpid", group.getId())
            .getList();
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
    String q;

    if (admin) {
      q = removeAllAdminGroupMemberRefsQuery;
    } else {
      q = removeAllGroupMembersQuery;
    }

    createQuery(q)
            .setEntity("gr", group)
            .executeUpdate();

    // Remove from any groups

    if (admin) {
      q = removeFromAllAdminGroupsQuery;
    } else {
      q = removeFromAllGroupsQuery;
    }

    createQuery(q)
            .setInt("mbrId", group.getId())
            .setBool("isgroup", true)
            .executeUpdate();

    getSess().delete(group);
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
    final String q;

    if (admin) {
      q = findAdminGroupEntryQuery;
    } else {
      q = findGroupEntryQuery;
    }

    final Object ent = createQuery(q)
            .setEntity("grp", group)
            .setInt("mbrId", val.getId())
            .setBool("isgroup", val instanceof BwGroup)
            .getUnique();

    if (ent == null) {
      return;
    }

    getSess().delete(ent);
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

  @Override
  public Collection<BwPrincipal<?>> getMembers(final BwGroup<?> group,
                                               final boolean admin) {
    String q;

    if (admin) {
      q = getAdminGroupUserMembersQuery;
    } else {
      q = getGroupUserMembersQuery;
    }

    //noinspection unchecked
    final Collection<BwPrincipal<?>> ms =
            new TreeSet<>(
                    (Collection<? extends BwPrincipal<?>>)    createQuery(q)
                            .setEntity("gr", group)
                            .getList());

    if (admin) {
      q = getAdminGroupGroupMembersQuery;
    } else {
      q = getGroupGroupMembersQuery;
    }

    //noinspection unchecked
    ms.addAll((Collection<? extends BwPrincipal<?>>)createQuery(q)
            .setEntity("gr", group)
            .getList());

    return ms;
  }

  private static final String getAllAdminGroupsQuery =
          "select ag from BwAdminGroup ag " +
                  "order by ag.account";

  private static final String getAllGroupsQuery =
          "select g from BwGroup g " +
                  "order by g.account";

  @Override
  public <T extends BwGroup<?>> Collection<T> getAllGroups(
          final boolean admin) {
    final String q;

    if (admin) {
      q = getAllAdminGroupsQuery;
    } else {
      q = getAllGroupsQuery;
    }

    //noinspection unchecked
    return (Collection<T>)createQuery(q).getList();
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

  @Override
  public <T extends BwGroup<?>> Collection<T> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    final String q;
    if (admin) {
      q = getAdminGroupsQuery;
    } else {
      q = getGroupsQuery;
    }

    //noinspection unchecked
    final Set<BwGroup<?>> gs =
            new TreeSet<>(
                    (Collection<? extends BwGroup<?>>)createQuery(q)
                            .setInt("entId", val.getId())
                            .setBool("isgroup",
                                     val instanceof BwGroup)
                            .getList());

    if (admin && (val.getKind() == WhoDefs.whoTypeUser)) {
      /* Event owner for group is implicit member of group. */

      //noinspection unchecked
      gs.addAll((Collection<? extends BwGroup<?>>)
                        createQuery(getAdminGroupsByEventOwnerQuery)
              .setString("ownerHref", val.getPrincipalRef())
              .getList());
    }

    //noinspection unchecked
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
    final String q = switch (val) {
      case final BwCategory bwCategory -> removeCategoryPrefForAllQuery;
      case final BwCollection bwCollection -> removeCalendarPrefForAllQuery;
      case final BwContact bwContact -> removeContactPrefForAllQuery;
      case final BwLocation bwLocation -> removeLocationPrefForAllQuery;
      case null, default ->
              throw new BedeworkException("Can't handle " + val);
    };

    createQuery(q)
            .setInt("id", val.getId())
            .executeUpdate();
  }

  /* ==========================================================
   *                       auth users
   * ========================================================== */

  private final static String getUserQuery =
          "select au from BwAuthUser au " +
                  "where au.userHref = :userHref";

  @Override
  public BwAuthUser getAuthUser(final String href) {
    return (BwAuthUser)createQuery(getUserQuery)
            .setString("userHref", href)
            .getUnique();
  }

  private final static String getAllAuthUsersQuery =
          "select au from BwAuthUser au " +
                  "order by au.userHref";

  @Override
  public List<BwAuthUser> getAllAuthUsers() {
    //noinspection unchecked
    return (List<BwAuthUser>)createQuery(getAllAuthUsersQuery)
            .getList();
  }
}
