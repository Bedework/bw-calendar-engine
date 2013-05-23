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
  public Collection<BwGroup> getGroups(final BwPrincipal val) throws CalFacadeException {
    return new TreeSet<BwGroup>(cb.getGroups(val, false));
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#getAllGroups(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public Collection<BwGroup> getAllGroups(final BwPrincipal val) throws CalFacadeException {
    Collection<BwGroup> groups = getGroups(val);
    Collection<BwGroup> allGroups = new TreeSet<BwGroup>(groups);

    for (BwGroup grp: groups) {
      Collection<BwGroup> gg = getAllGroups(grp);
      if (!gg.isEmpty()) {
        allGroups.addAll(gg);
      }
    }

    return allGroups;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#getGroupMaintOK()
   */
  @Override
  public boolean getGroupMaintOK() {
    return true;
  }

  @Override
  public Collection<BwGroup> getAll(final boolean populate) throws CalFacadeException {
    Collection<BwGroup> gs = cb.getAll(false);

    if (!populate) {
      return gs;
    }

    for (BwGroup grp: gs) {
      getMembers(grp);
    }

    return gs;
  }

  @Override
  public void getMembers(final BwGroup group) throws CalFacadeException {
    group.setGroupMembers(cb.getMembers(group, false));
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#addGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void addGroup(final BwGroup group) throws CalFacadeException {
    if (findGroup(group.getAccount()) != null) {
      throw new CalFacadeException(CalFacadeException.duplicateAdminGroup);
    }
    cb.updateGroup(group, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Directories#findGroup(java.lang.String)
   */
  @Override
  public BwGroup findGroup(final String name) throws CalFacadeException {
    return cb.findGroup(name, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#addMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void addMember(final BwGroup group,
                        final BwPrincipal val) throws CalFacadeException {
    BwGroup g = findGroup(group.getAccount());

    if (g == null) {
      throw new CalFacadeException("Group " + group + " does not exist");
    }

    if (!checkPathForSelf(group, val)) {
      throw new CalFacadeException(CalFacadeException.alreadyOnGroupPath);
    }

    g.addGroupMember(val);

    cb.addMember(group, val, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#removeMember(org.bedework.calfacade.BwGroup, org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val) throws CalFacadeException {
    BwGroup g = findGroup(group.getAccount());

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
  public void removeGroup(final BwGroup group) throws CalFacadeException {
    cb.removeGroup(group, false);
 }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.ifs.Groups#updateGroup(org.bedework.calfacade.BwGroup)
   */
  @Override
  public void updateGroup(final BwGroup group) throws CalFacadeException {
    cb.updateGroup(group, false);
  }

  @Override
  public Collection<BwGroup> findGroupParents(final BwGroup group) throws CalFacadeException {
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

  private boolean checkPathForSelf(final BwGroup group,
                                   final BwPrincipal val) throws CalFacadeException {
    if (group.equals(val)) {
      return false;
    }

    /* get all parents of group and try again */

    for (BwGroup g: findGroupParents(group)) {
      if (!checkPathForSelf(g, val)) {
        return false;
      }
    }

    return true;
  }
}

