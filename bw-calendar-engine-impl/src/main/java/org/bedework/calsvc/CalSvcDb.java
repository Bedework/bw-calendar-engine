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

import org.bedework.access.CurrentAccess;
import org.bedework.calcorei.Calintf;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.UsersI;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.UUID;

/** This acts as an interface to the database for more client oriented
 * bedework objects. CalIntf is a more general calendar specific interface.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class CalSvcDb implements Logged, Serializable {
  private CalSvc svci;

  private CalSvcIPars pars;

  /**
   * @param svci
   */
  public CalSvcDb(final CalSvc svci) {
    setSvc(svci);
  }

  /** Call at svci open
   *
   */
  public void open() {
  }

  /** Call at svci close
   *
   */
  public void close() {
  }

  /**
   *
   * @return an encoded value for use as a unique uuid.
   */
  public static String getEncodedUuid() {
    return Base64.getEncoder()
                 .encodeToString(UUID.randomUUID()
                                     .toString()
                                     .getBytes());
  }

  /* ===================================================
   *                   Protected methods.
   * ======================================================= */

  protected Timestamp getCurrentTimestamp() {
    return getSvc().getCurrentTimestamp();
  }

  protected BwPrincipal caladdrToPrincipal(final String href) {
    return getSvc().getDirectories().caladdrToPrincipal(href);
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  /* Get current parameters
   */
  protected CalSvcIPars getPars() {
    return pars;
  }

  /* See if current authorised user has super user access.
   */
  protected boolean isSuper() {
    return pars.getPublicAdmin() && svci.getSuperUser();
  }

  /* See if current authorised is a guest.
   */
  protected boolean isGuest() {
    return pars.isGuest();
  }

  /* See if in public admin mode
   */
  protected boolean isPublicAdmin() {
    return pars.getPublicAdmin();
  }

  protected BwPrincipal getPrincipal() {
    return svci.getPrincipal();
  }

  protected String getPrincipalHref() {
    return svci.getPrincipal().getPrincipalRef();
  }

  public BwIndexer getIndexer(final String docType) {
    return svci.getIndexer(docType);
  }

  protected BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return svci.getUsersHandler().getPrincipal(href);
  }

  /**
   * @param svci service interface
   */
  public void setSvc(final CalSvcI svci) {
    this.svci = (CalSvc)svci;

    if (svci != null) {
      pars = this.svci.getPars();
    }
  }

  protected CalSvc getSvc() {
    return svci;
  }

  protected CalendarsI getCols() {
    return svci.getCalendarsHandler();
  }

  protected NotificationsI getNotes() {
    return svci.getNotificationsHandler();
  }

  protected ResourcesI getRess() {
    return svci.getResourcesHandler();
  }

  protected BwPreferences getPrefs() {
    return svci.getPrefsHandler().get();
  }

  protected BwPreferences getPrefs(final BwPrincipal principal) {
    return svci.getPrefsHandler().get(principal);
  }

  protected UsersI getUsers() {
    return svci.getUsersHandler();
  }

  protected Calintf getCal() {
    try {
      return svci.getCal();
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  protected Calintf getCal(final BwCalendar cal) {
    return svci.getCal(cal);
  }

  protected BwPrincipal getPublicUser() {
    return getSvc().getUsersHandler().getPublicUser();
  }

  protected CurrentAccess checkAccess(
          final BwShareableDbentity<?> ent, final int desiredAccess,
          final boolean returnResult) throws CalFacadeException {
    return svci.checkAccess(ent, desiredAccess, returnResult);
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

