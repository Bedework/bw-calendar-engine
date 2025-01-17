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
package org.bedework.calsvc;

import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.Directories;

import java.util.Collection;

/** Class used by groups implementations for calls into CalSvci
 *
 */
public class GroupsCallBack extends Directories.CallBack {
  CalSvc svci;

  GroupsCallBack(final CalSvc svci) {
    this.svci = svci;
  }

  @Override
  public String getSysid() {
    return svci.getSystemProperties().getSystemid();
  }

  @Override
  public BwPrincipal<?> getCurrentUser() {
    return svci.getPrincipal();
  }

  @Override
  public BwGroup<?> findGroup(final String account,
                              final boolean admin) {
    try {
      return svci.getCal().findGroup(account, admin);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(
          final BwGroup<?> group,
          final boolean admin) {
    try {
      return svci.getCal().findGroupParents(group, admin);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public void updateGroup(final BwGroup<?> group,
                          final boolean admin) {
    try {
      svci.getCal().updateGroup(group, admin);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public void removeGroup(final BwGroup<?> group,
                          final boolean admin) {
    svci.getCal().removeGroup(group, admin);
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val,
                        final boolean admin) {
    svci.getCal().addMember(group, val, admin);
  }

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val,
                           final boolean admin) {
    svci.getCal().removeMember(group, val, admin);
  }

  @Override
  public Collection<BwPrincipal<?>> getMembers(
          final BwGroup<?> group,
          final boolean admin) {
    return svci.getCal().getMembers(group, admin);
  }

  @Override
  public Collection<BwGroup<?>> getAll(final boolean admin) {
    return svci.getCal().getAllGroups(admin);
  }

  @Override
  public Collection<BwGroup<?>> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    return svci.getCal().getGroups(val, admin);
  }
}