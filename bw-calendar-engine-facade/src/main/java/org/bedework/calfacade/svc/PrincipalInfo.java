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
package org.bedework.calfacade.svc;

import org.bedework.access.Access.AccessCb;
import org.bedework.access.AccessPrincipal;
import org.bedework.access.PrivilegeSet;
import org.bedework.access.WhoDefs;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.misc.Util;

import java.io.Serializable;

/** Provide information about the current principal and given principals.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class PrincipalInfo implements AccessCb, Serializable {
  protected boolean superUser;

  protected boolean subscriptionsOnly;

  protected BwPrincipal principal;

  /* associated with principal */
  protected String calendarHomePath;

  protected BwPrincipal authPrincipal;

  /* Null allows all accesses according to user - otherwise restricted to this. */
  protected PrivilegeSet maxAllowedPrivs;

  private String userHomePath;

  /**
   * @param principal the principal
   * @param authPrincipal the authenticated principal
   * @param maxAllowedPrivs - used to filter access - e.g. force read only in
   *                   public client
   * @param subscriptionsOnly - true if this user is limited to internal subscriptions 
   */
  public PrincipalInfo(final BwPrincipal principal,
                       final BwPrincipal authPrincipal,
                       final PrivilegeSet maxAllowedPrivs,
                       final boolean subscriptionsOnly) {
    this.principal = principal;
    this.authPrincipal = authPrincipal;
    this.maxAllowedPrivs = maxAllowedPrivs;
    this.subscriptionsOnly = subscriptionsOnly;
  }

  /**
   * @return max privs or null for no max
   */
  public PrivilegeSet getMaximumAllowedPrivs() {
    return maxAllowedPrivs;
  }

  /**
   * @param href principal href
   * @return AccessPrincipal or null for not valid
   * @throws CalFacadeException
   */
  public abstract AccessPrincipal getPrincipal(String href) throws CalFacadeException;

  /**
   * @return the path for home for the current principal, e.g. /user
   * @throws CalFacadeException
   */
  public String getUserHomePath() throws CalFacadeException {
    if (userHomePath == null) {
      userHomePath = "/" + getSyspars().getUserCalendarRoot();
    }

    return userHomePath;
  }

  /**
   * @return the path for calendar home for the current principal, e.g. /user/douglm
   * @throws CalFacadeException
   */
  public String getCalendarHomePath() throws CalFacadeException {
    if (calendarHomePath == null) {
      calendarHomePath = getCalendarHomePath(getPrincipal());
    }

    return calendarHomePath;
  }

  /**
   * @param pr the principal
   * @return the path for calendar home for the given principal, e.g. /user/douglm
   * @throws CalFacadeException
   */
  public String getCalendarHomePath(final BwPrincipal pr) throws CalFacadeException {
    if (pr.getKind() == WhoDefs.whoTypeUser) {
      return Util.buildPath(BasicSystemProperties.colPathEndsWithSlash, 
                            getUserHomePath(), "/", pr.getAccount());
    }

    return Util.buildPath(BasicSystemProperties.colPathEndsWithSlash, 
                          getUserHomePath(), "/", pr.getPrincipalRef());
  }

  /**
   * @return boolean
   */
  public boolean getSuperUser() {
    return superUser;
  }

  public boolean getSubscriptionsOnly() {
    return subscriptionsOnly;
  }

  /**
   * @return current principal
   */
  public BwPrincipal getPrincipal() {
    return principal;
  }

  /**
   * @return principal we authenticated as
   */
  public BwPrincipal getAuthPrincipal() {
    return authPrincipal;
  }

  /**
   * @return system parameters
   * @throws CalFacadeException
   */
  public abstract BasicSystemProperties getSyspars() throws CalFacadeException;
}
