package org.bedework.calcore.rw.common.dao;

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwGroupEntry;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;

import java.util.Collection;
import java.util.List;

public interface PrincipalsAndPrefsDAO extends DAOBase {
  void add(BwGroupEntry val);

  BwPrincipal<?> getPrincipal(String href);

  List<String> getPrincipalHrefs(int start,
                                 int count);

  BwPreferences getPreferences(String principalHref);

  BwGroup<?> findGroup(String account,
                       boolean admin);

  /**
   * @param group the group
   * @param admin true for an admin group
   * @return Collection
   */
  Collection<BwGroup<?>> findGroupParents(BwGroup<?> group,
                                          boolean admin);

  /**
   * Delete a group
   *
   * @param group BwGroup group object to delete
   */
  void removeGroup(BwGroup<?> group,
                   boolean admin);

  void removeMember(BwGroup<?> group,
                    BwPrincipal<?> val,
                    boolean admin);

  Collection<BwPrincipal<?>> getMembers(BwGroup<?> group,
                                        boolean admin);

  <T extends BwGroup<?>> Collection<T> getAllGroups(boolean admin);

  <T extends BwGroup<?>> Collection<T> getGroups(
          BwPrincipal<?> val,
          boolean admin);

  void removeFromAllPrefs(BwShareableDbentity<?> val);

  BwAuthUser getAuthUser(String href);

  List<BwAuthUser> getAllAuthUsers();
}
