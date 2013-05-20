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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.PrincipalInfo;

import edu.rpi.cmt.access.AccessException;
import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.cmt.access.PrivilegeSet;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * @author douglm
 *
 */
final class SvciPrincipalInfo extends PrincipalInfo {
  private CalSvc svci;

  private static class StackedState {
    BwPrincipal principal;
    boolean superUser;
    String calendarHomePath;
    PrivilegeSet maxAllowedPrivs;
  }

  private Deque<SvciPrincipalInfo.StackedState> stack = new ArrayDeque<SvciPrincipalInfo.StackedState>();

  SvciPrincipalInfo(final CalSvc svci,
                    final BwPrincipal principal,
                    final BwPrincipal authPrincipal,
                    final PrivilegeSet maxAllowedPrivs) {
    super(principal, authPrincipal, maxAllowedPrivs);
    this.svci = svci;
  }

  void setSuperUser(final boolean val) {
    superUser = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.util.AccessUtilI.CallBack#getPrincipal(java.lang.String)
   */
  @Override
  public AccessPrincipal getPrincipal(final String href) throws CalFacadeException {
    return svci.getUsersHandler().getPrincipal(href);
  }

  @Override
  public SystemProperties getSyspars() throws CalFacadeException {
    return svci.getSystemProperties();
  }

  void setPrincipal(final BwPrincipal principal) {
    this.principal = principal;
    calendarHomePath = null;
  }

  void pushPrincipal(final BwPrincipal principal) {
    SvciPrincipalInfo.StackedState ss = new StackedState();
    ss.principal = this.principal;
    ss.superUser = superUser;
    ss.calendarHomePath = calendarHomePath;
    ss.maxAllowedPrivs = maxAllowedPrivs;

    stack.push(ss);

    setPrincipal(principal);
    superUser = false;
    maxAllowedPrivs = null;
  }

  void popPrincipal() throws CalFacadeException {
    SvciPrincipalInfo.StackedState ss = stack.pop();

    if (ss == null) {
      throw new CalFacadeException("Nothing to pop");
    }

    setPrincipal(ss.principal);
    calendarHomePath = ss.calendarHomePath;
    superUser = ss.superUser;
    maxAllowedPrivs = ss.maxAllowedPrivs;
  }

  /* (non-Javadoc)
   * @see edu.rpi.cmt.access.Access.AccessCb#makeHref(java.lang.String, int)
   */
  @Override
  public String makeHref(final String id, final int whoType) throws AccessException {
    try {
      return svci.getDirectories().makePrincipalUri(id, whoType);
    } catch (Throwable t) {
      throw new AccessException(t);
    }
  }
}