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
package org.bedework.calsvc.directory;

import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.BedeworkUnimplementedException;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.DirectoryInfo;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.AdminGroups;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.TreeSet;

/** An implementation of AdminGroups which stores the groups in the calendar
 * database.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 1.0
 */
public class AdminGroupsDbImpl extends AbstractDirImpl implements AdminGroups {
  /* ====================================================================
   *  Abstract methods.
   * ==================================================================== */

  @Override
  public String getConfigName() {
    /* Use the same config as the default groups - we're only after principal info
     */
    return "dir-config";
  }

  /* ===================================================================
   *  The following should not change the state of the current users
   *  group.
   *  =================================================================== */

  private static DirectoryInfo dirInfo;

  @Override
  public BwPrincipal getPrincipal(final String href) {
    if (dirInfo == null) {
      dirInfo = getDirectoryInfo();
    }

    if (href == null) {
      return null;
    }

    if (!href.startsWith(dirInfo.getBwadmingroupPrincipalRoot())) {
      return super.getPrincipal(href);
    }

    final String account = Util.buildPath(false,
                                          href.substring(dirInfo.getBwadmingroupPrincipalRoot().length()));

    return findGroup(account);
  }

  @Override
  public boolean validPrincipal(final String account) {
    // XXX Not sure how we might use this for admin users.
    return true;
  }

  @Override
  public BwPrincipalInfo getDirInfo(final BwPrincipal p) {
    /* Was never previously called - getUserInfo is not defined as a query
    HibSession sess = getSess();

    sess.namedQuery("getUserInfo");
    sess.setString("userHref", p.getPrincipalRef());

    return (BwPrincipalInfo)sess.getUnique(); */
    return null;
  }

  @Override
  public Collection<BwGroup> getGroups(final BwPrincipal val) {
    return new TreeSet<>(cb.getGroups(val, true));
  }

  @Override
  public Collection<BwGroup> getAllGroups(final BwPrincipal val) {
    final Collection<BwGroup> groups = getGroups(val);
    final Collection<BwGroup> allGroups = new TreeSet<>(groups);

    for (final BwGroup adgrp: groups) {
//      BwGroup grp = new BwGroup(adgrp.getAccount());

      final Collection<BwGroup> gg = getAllGroups(adgrp);
      if (!gg.isEmpty()) {
        allGroups.addAll(gg);
      }
    }

    return allGroups;
  }

  /** Show whether user entries can be modified with this
   * class. Some sites may use other mechanisms.
   *
   * @return boolean    true if group maintenance is implemented.
   */
  @Override
  public boolean getGroupMaintOK() {
    return true;
  }

  @Override
  public Collection<BwGroup<?>> getAll(final boolean populate) {
    final Collection<BwGroup<?>> gs = cb.getAll(true);

    if (!populate) {
      return gs;
    }

    for (final var grp: gs) {
      getMembers(grp);
    }

    return gs;
  }

  @Override
  public void getMembers(final BwGroup<?> group) {
    group.setGroupMembers(cb.getMembers(group, true));
  }

  @Override
  public String getAdminGroupsIdPrefix() {
    return "agrp_";
  }

  /* ====================================================================
   *  The following are available if group maintenance is on.
   * ==================================================================== */

  @Override
  public void addGroup(final BwGroup<?> group) {
    if (findGroup(group.getAccount()) != null) {
      throw new BedeworkException(CalFacadeErrorCode.duplicateAdminGroup);
    }
    cb.addGroup(group, true);
  }

  /** Find a group given its name
   *
   * @param  name             String group name
   * @return AdminGroupVO   group object
   * @exception RuntimeException If there's a problem
   */
  @Override
  public BwGroup<?> findGroup(final String name) {
    return cb.findGroup(name, true);
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val) {
    final BwGroup<?> ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new BedeworkException(CalFacadeErrorCode.groupNotFound,
                                   group.getAccount());
    }

    /*
    if (val instanceof BwUser) {
      ensureAuthUserExists((BwUser)val);
    } else {
      val = findGroup(val.getAccount());
    }
    */

    /* val must not already be present on any paths to the root.
     * We'll assume the possibility of more than one parent.
     */

    if (!checkPathForSelf(group, val)) {
      throw new BedeworkException(CalFacadeErrorCode.alreadyOnGroupPath);
    }

    ag.addGroupMember(val);

    cb.addMember(ag, val, true);
  }

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val) {
    final var ag = findGroup(group.getAccount());

    if (ag == null) {
      throw new BedeworkException(CalFacadeErrorCode.groupNotFound,
                                   group.getAccount());
    }

    ag.removeGroupMember(val);

    cb.removeMember(group, val, true);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.AdminGroups#removeGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void removeGroup(final BwGroup<?> group) {
    cb.removeGroup(group, true);
  }

  @Override
  public void updateGroup(final BwGroup<?> group) {
    cb.updateGroup(group, true);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup<?> group) {
    return cb.findGroupParents(group, true);
  }

  @Override
  public Collection<String>getGroups(final String rootUrl,
                                     final String principalUrl) {
    // Not needed for admin
    throw new BedeworkUnimplementedException();
  }

  private boolean checkPathForSelf(final BwGroup<?> group,
                                   final BwPrincipal<?> val) {
    if (group.equals(val)) {
      return false;
    }

    /* get all parents of group and try again */


    for (final var g: findGroupParents(group)) {
      if (!checkPathForSelf(g, val)) {
        return false;
      }
    }

    return true;
  }
}
