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

import org.bedework.calfacade.configs.SystemConfig;
import org.bedework.calfacade.configs.SystemRoots;
import org.bedework.calfacade.exc.CalFacadeException;

/** Provides access to some of the basic configuration for the system.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class SystemConfigImpl extends SystemConfig {
  private final static class ReadOnlySystemRoots extends SystemRoots {
    private ReadOnlySystemRoots(final String principalRoot,
                                final String userPrincipalRoot,
                                final String groupPrincipalRoot,
                                final String bwadmingroupPrincipalRoot,
                                final String resourcePrincipalRoot,
                                final String venuePrincipalRoot,
                                final String ticketPrincipalRoot,
                                final String hostPrincipalRoot) {
      super.setPrincipalRoot(principalRoot);
      super.setUserPrincipalRoot(userPrincipalRoot);
      super.setGroupPrincipalRoot(groupPrincipalRoot);
      super.setBwadmingroupPrincipalRoot(bwadmingroupPrincipalRoot);
      super.setResourcePrincipalRoot(resourcePrincipalRoot);
      super.setVenuePrincipalRoot(venuePrincipalRoot);
      super.setTicketPrincipalRoot(ticketPrincipalRoot);
      super.setHostPrincipalRoot(hostPrincipalRoot);
    }

    @Override
    public final void setPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setUserPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setGroupPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setBwadmingroupPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setResourcePrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setVenuePrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setTicketPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }

    @Override
    public final void setHostPrincipalRoot(final String val) {
      throw new RuntimeException("Immutable");
    }
  }

  private static final SystemRoots sysRoots;

  static {
    sysRoots = new ReadOnlySystemRoots(
      "/principals/",
      "/principals/users/",
      "/principals/groups/",
      "/principals/groups/bwadmin/",
      "/principals/resources/",
      "/principals/locations/",
      "/principals/tickets/",
      "/principals/hosts/");
  }

  /**
   * @return a read only set of system roots.
   * @throws CalFacadeException
   */
  @Override
  public SystemRoots getSystemRoots() throws CalFacadeException {
    return sysRoots;
  }
}
