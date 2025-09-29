/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.rw.common;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.PrincipalsAndPrefsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CorePrincipalsAndPrefsI;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwGroupEntry;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAdminGroupEntry;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;
import java.util.List;

import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;

/**
 * User: mike Date: 2/1/20 Time: 14:49
 */
public class CorePrincipalsAndPrefs
        extends CalintfHelper
        implements CorePrincipalsAndPrefsI {
  private final PrincipalsAndPrefsDAO dao;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CorePrincipalsAndPrefs(final PrincipalsAndPrefsDAO dao,
                                final Calintf intf,
                                final AccessChecker ac,
                                final boolean sessionless) {
    this.dao = dao;
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) {
    final BwAuthUser ck = getAuthUser(val.getUserHref());

    if (ck != null) {
      throw new BedeworkException(CalFacadeErrorCode.targetExists);
    }

    dao.add(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) {
    final BwAuthUser au = dao.getAuthUser(href);

    if (au == null) {
      // Not an authorised user
      return null;
    }

    BwAuthUserPrefs prefs = au.getPrefs();

    if (prefs == null) {
      prefs = BwAuthUserPrefs.makeAuthUserPrefs();
      au.setPrefs(prefs);
    }

    return au;
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) {
    dao.update(val);
  }

  @Override
  public List<BwAuthUser> getAllAuthUsers() {
    return dao.getAllAuthUsers();
  }

  @Override
  public void delete(final BwAuthUser val) {
    dao.delete(val);
  }

  /* =========================================================
   *                       principals + prefs
   * ========================================================= */

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return dao.getPrincipal(href);
  }

  @Override
  public void add(final BwPrincipal<?> val) {
    dao.add(val);
    intf.getIndexer(val.getPrincipalRef(),
                    docTypePrincipal).indexEntity(val);
  }

  @Override
  public void update(final BwPrincipal<?> val) {
    dao.update(val);
    intf.getIndexer(val.getPrincipalRef(),
                    docTypePrincipal).indexEntity(val);
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    return dao.getPrincipalHrefs(start, count);
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    return dao.getPreferences(principalHref);
  }

  @Override
  public void add(final BwPreferences val) {
    dao.add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwPreferences val) {
    dao.update(val);
    indexEntity(val);
  }

  @Override
  public void delete(final BwPreferences val) {
    dao.delete(val);
    intf.getIndexer(docTypePreferences).unindexEntity(val.getHref());
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    dao.removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup<?> findGroup(final String account,
                              final boolean admin) {
    return dao.findGroup(account, admin);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup group,
                                                 final boolean admin) {
    return dao.findGroupParents(group, admin);
  }

  @Override
  public void addGroup(final BwGroup group,
                       final boolean admin) {
    dao.add(group);
    indexEntity(group);
  }

  @Override
  public void updateGroup(final BwGroup group,
                          final boolean admin) {
    dao.update(group);
    indexEntity(group);
  }

  @Override
  public void removeGroup(final BwGroup<?> group,
                          final boolean admin) {
    dao.removeGroup(group, admin);
    intf.getIndexer(docTypePrincipal).unindexEntity(group.getHref());
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val,
                        final boolean admin) {
    final BwGroupEntry ent;

    if (admin) {
      ent = new BwAdminGroupEntry();
    } else {
      ent = new BwGroupEntry();
    }

    ent.setGrp(group);
    ent.setMember(val);

    dao.add(ent);
    indexEntity(group);
  }

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val,
                           final boolean admin) {
    dao.removeMember(group, val, admin);
    indexEntity(group);
  }

  @Override
  public Collection<BwPrincipal<?>> getMembers(final BwGroup<?> group,
                                               final boolean admin) {
    return dao.getMembers(group, admin);
  }

  @Override
  public Collection<BwGroup<?>> getAllGroups(final boolean admin) {
    return dao.getAllGroups(admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups() {
    return dao.getAllGroups(true);
  }

  @Override
  public Collection<BwGroup<?>> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    return dao.getGroups(val, admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups(
          final BwPrincipal<?> val) {
    return dao.getGroups(val, true);
  }
}
