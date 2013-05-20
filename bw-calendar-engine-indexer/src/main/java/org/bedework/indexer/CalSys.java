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
package org.bedework.indexer;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.EventsI;

import org.apache.log4j.Logger;

import java.util.Collection;

/** An interface to the calendar system for the indexer.
 *
 * @author Mike Douglass
 *
 */
public abstract class CalSys {
  protected String name;

  protected String adminAccount;

  protected String principal;

  protected boolean debug;

  private Logger log;

  private String indexRoot;

  private String userCalendarRoot;

  private String publicCalendarRoot;

  private SystemProperties syspars;

  private int batchSize = 5;

  private CalSvcI svci;

  /* Who the svci object is for */
  private String curAccount;
  private boolean curPublicAdmin;


  /**
   * @param name
   * @param adminAccount
   * @param principal
   * @throws CalFacadeException
   */
  public CalSys(final String name,
                final String adminAccount,
                final String principal) throws CalFacadeException {
    this.name = name;
    this.adminAccount = adminAccount;
    this.principal = principal;
    debug = getLogger().isDebugEnabled();
  }

  /**
   * @param principal
   */
  public void setCurrentPrincipal(final String principal) {
    this.principal = principal;
  }

  /**
   * @param val
   * @throws CalFacadeException
   */
  public abstract void putIndexer(BwIndexer val) throws CalFacadeException;

  /**
   * @return an indexer
   * @throws CalFacadeException
   */
  public abstract BwIndexer getIndexer() throws CalFacadeException;

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @return svci object
   * @throws CalFacadeException
   */
  public CalSvcI getSvci() throws CalFacadeException {
    if ((svci != null) && svci.isOpen()) {
      // We shouldn't need to check if it's the same account.
      return svci;
    }

    String account = adminAccount;
    boolean publicAdmin = true;
    String userPrincipalPrefix = "/principals/users/";

    if (principal != null) {
      if (principal.startsWith(userPrincipalPrefix)) {
        account = principal.substring(userPrincipalPrefix.length());
      }

      publicAdmin = false;
    }

    if ((svci == null) ||
        !account.equals(curAccount) ||
        (publicAdmin != curPublicAdmin)) {
      curAccount = account;
      curPublicAdmin = publicAdmin;

      CalSvcIPars pars = CalSvcIPars.getServicePars(account,
                                                    publicAdmin,
                                                    true);   // Allow super user
      svci = new CalSvcFactoryDefault().getSvc(pars);
    }

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   * @return svci object
   * @throws CalFacadeException
   */
  public CalSvcI getAdminSvci() throws CalFacadeException {
    CalSvcIPars pars = CalSvcIPars.getServicePars(adminAccount,
                                                  true,   // publicAdmin,
                                                  true);   // Allow super user
    CalSvcI svci = new CalSvcFactoryDefault().getSvc(pars);

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   * @throws CalFacadeException
   */
  public void close() throws CalFacadeException {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    close(svci);
  }

  /**
   * @param svci
   * @throws CalFacadeException
   */
  public void close(final CalSvcI svci) throws CalFacadeException {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();
    } catch (Throwable t) {
      try {
        svci.close();
      } catch (Throwable t1) {
      }
    }

    try {
      svci.close();
    } catch (Throwable t) {
    }
  }

  protected String getIndexRoot() throws CalFacadeException {
    if (indexRoot == null) {
      indexRoot = getSyspars().getIndexRoot();
    }

    return indexRoot;
  }

  protected String getUserCalendarRoot() throws CalFacadeException {
    if (userCalendarRoot == null) {
      userCalendarRoot = getSyspars().getUserCalendarRoot();
    }

    return userCalendarRoot;
  }

  protected String getPublicCalendarRoot() throws CalFacadeException {
    if (publicCalendarRoot == null) {
      publicCalendarRoot = getSyspars().getPublicCalendarRoot();
    }

    return publicCalendarRoot;
  }

  protected SystemProperties getSyspars() throws CalFacadeException {
    CalSvcI svci = null;
    if (syspars == null) {
      try {
        svci = getAdminSvci();
        syspars = svci.getSystemProperties();
      } finally {
        if (svci != null) {
          try {
            close(svci);
          } finally {
          }
        }
      }
    }

    return syspars;
  }

  protected boolean hasAccess(final BwCalendar col) throws CalFacadeException {
    // XXX This should do a real access check so we can index subscriptions.

    if (col.getPublick()) {
      return true;
    }

    //if (publick) {
    //  return false;
    //}

    if (principal == null) {
      // We aren't handling a principal yet.
      return true;
    }

    return col.getOwnerHref().equals(principal);
  }

  protected boolean hasAccess(final BwEvent ent) throws CalFacadeException {
    // XXX This should do a real access check so we can index subscriptions.

    if (ent.getPublick()) {
      return true;
    }

    //if (publick) {
    //  return false;
    //}

    return ent.getOwnerHref().equals(principal);
  }

  protected BwCalendar getCollection(final String path) throws CalFacadeException {
    BwCalendar col = svci.getCalendarsHandler().get(path);

    if ((col == null) || !hasAccess(col)) {
      if (debug) {
        if (col == null) {
          debugMsg("No collection");
        } else {
          debugMsg("No access to " + path + " for " + showPrincipal());
        }
      }
      throw new CalFacadeAccessException();
    }

    return col;
  }

  private String showPrincipal() {
    if (principal != null) {
      return "principal=" + principal;
    }

    return "account=" + adminAccount;
  }

  /** Get the next batch of child collection paths.
   *
   * @param path
   * @param batchIndex  >= 0
   * @return next batch of child collection paths.
   * @throws CalFacadeException
   */
  protected Collection<String> getChildCollections(final String path,
                                                   int batchIndex) throws CalFacadeException {
    if (debug) {
      debugMsg("getChildCollections(" + path + ")");
    }

    CalSvcI svci = getAdminSvci();

    try {
      BwCalendar col = svci.getCalendarsHandler().get(path);

      if ((col == null) || !hasAccess(col)) {
        if (debug) {
          if (col == null) {
            debugMsg("No collection");
          } else {
            debugMsg("No access");
          }
        }
        throw new CalFacadeAccessException();
      }

      Collection<String> paths = svci.getAdminHandler().getChildCollections(path,
                                                                    batchIndex,
                                                                    batchSize);

      if (debug) {
        if (paths == null) {
          debugMsg("getChildCollections(" + path + ") found none");
        } else {
          debugMsg("getChildCollections(" + path + ") found " + paths.size());
        }
      }

      if (paths == null) {
        return null;
      }

      batchIndex += paths.size();

      return paths;
    } finally {
      close(svci);
    }
  }

  /** Get the next batch of child entity names.
   *
   * @param path
   * @param batchIndex >= 0
   * @return next batch of child entity names.
   * @throws CalFacadeException
   */
  protected Collection<String> getChildEntities(final String path,
                                                int batchIndex) throws CalFacadeException {
    if (debug) {
      debugMsg("getChildEntities(" + path + ")");
    }

    CalSvcI svci = getAdminSvci();

    try {
      BwCalendar col = svci.getCalendarsHandler().get(path);

      if ((col == null) || !hasAccess(col)) {
        throw new CalFacadeAccessException();
      }

      Collection<String> names = svci.getAdminHandler().getChildEntities(path,
                                                                    batchIndex,
                                                                    batchSize);

      if (debug) {
        if (names == null) {
          debugMsg("getChildEntities(" + path + ") found none");
        } else {
          debugMsg("getChildEntities(" + path + ") found " + names.size());
        }
      }

      if (names == null) {
        return null;
      }

      batchIndex += names.size();

      return names;
    } finally {
      close(svci);
    }
  }

  protected Collection<EventInfo> getEvent(final String colPath,
                                           final String uid,
                                           final String rid) throws CalFacadeException {
    EventsI evhandler = svci.getEventsHandler();

    Collection<EventInfo> evis = evhandler.get(colPath, uid, rid,
                                               new RecurringRetrievalMode(Rmode.overrides),
                                               false);

    return evis;
  }

  /**
   * @return Logger
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void info(final String msg) {
    getLogger().info(msg);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }
}
