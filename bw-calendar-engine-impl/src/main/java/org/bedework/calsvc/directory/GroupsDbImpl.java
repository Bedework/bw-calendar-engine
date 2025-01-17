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

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Collection;
import java.util.TreeSet;

/** An implementation of Directories which stores groups in the calendar
 * database. It is assumed a production system will use the ldap implementation
 * or something like it.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 1.0
 */
public class GroupsDbImpl extends AbstractDirImpl {
  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#getGroups(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public Collection<BwGroup<?>> getGroups(final BwPrincipal<?> val) {
    return new TreeSet<>(cb.getGroups(val, false));
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#getAllGroups(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public Collection<BwGroup<?>>  getAllGroups(final BwPrincipal<?> val) {
    final var groups = getGroups(val);
    final var allGroups = new TreeSet<>(groups);

    for (final var grp: groups) {
      final var gg = getAllGroups(grp);
      if (!gg.isEmpty()) {
        allGroups.addAll(gg);
      }
    }

    return allGroups;
  }

  @Override
  public boolean getGroupMaintOK() {
    return true;
  }

  @Override
  public Collection<BwGroup<?>> getAll(final boolean populate) {
    final var gs = cb.getAll(false);

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
    group.setGroupMembers(cb.getMembers(group, false));
  }

  @Override
  public void addGroup(final BwGroup<?> group) {
    if (findGroup(group.getAccount()) != null) {
      throw new CalFacadeException(CalFacadeErrorCode.duplicateAdminGroup);
    }
    cb.updateGroup(group, false);
  }

  @Override
  public BwGroup<?> findGroup(final String name) {
    return cb.findGroup(name, false);
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val) {
    final var g = findGroup(group.getAccount());

    if (g == null) {
      throw new CalFacadeException("Group " + group + " does not exist");
    }

    if (!checkPathForSelf(group, val)) {
      throw new CalFacadeException(CalFacadeErrorCode.alreadyOnGroupPath);
    }

    g.addGroupMember(val);

    cb.addMember(group, val, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#removeMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val) {
    final var g = findGroup(group.getAccount());

    if (g == null) {
      throw new CalFacadeException("Group " + group + " does not exist");
    }

    g.removeGroupMember(val);

    cb.removeMember(group, val, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#removeGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void removeGroup(final BwGroup<?> group) {
    cb.removeGroup(group, false);
 }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#updateGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void updateGroup(final BwGroup<?> group) {
    cb.updateGroup(group, false);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup<?> group) {
    return cb.findGroupParents(group, false);
  }

  /* ====================================================================
   *  Abstract methods.
   * ==================================================================== */

  @Override
  public String getConfigName() {
    return "dir-config";
  }

  /* ====================================================================
   *  Private methods.
   * ==================================================================== */

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

