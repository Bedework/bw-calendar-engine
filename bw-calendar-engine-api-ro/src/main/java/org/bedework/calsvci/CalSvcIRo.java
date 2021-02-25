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
package org.bedework.calsvci;

import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.UserAuth;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

/** This is the read-only interface to the calendar system.
 *
 * This will be used by web applications and web services
 * as well as other applications which wish to integrate calendar
 * actions into their interface.
 *
 * <p>This is a high level interface which carries out commonly used
 * calendar operations which may involve a number of interactions with the
 * underlying database implementation.
 *
 * <p>Within the bedework system events and todos are treated identically. An
 * entity type field defines which is what and allows filtering based on type
 * of entity. Explicit calls to fetch an event always return the addressed
 * object which may be an event or a todo.
 *
 * <p>Calls for collections of event objects may include filters to specify
 * which type of entity is desired.
 *
 * @author Mike Douglass       douglm rpi.edu
 */
public interface CalSvcIRo extends AutoCloseable, Serializable {
  /** init may throw an exception with this message if read/write is needed
   */
  String upgradeToReadWriteMessage =
          "Upgrade to read/write";

  /** (Re)initialise the object for a particular use.
   *
   * @param pars        Defines the global parameters for the object
   * @throws RuntimeException on fatal errors
   */
  void init(CalSvcIPars pars);

  /** Return properties about the system that depend on authentication state.
   *
   * @return AuthProperties object - never null.
   */
  AuthProperties getAuthProperties();

  /** Return properties about the system.
   *
   * @return SystemProperties object - never null.
   */
  SystemProperties getSystemProperties();

  /** Set the calendar suite we are running as. Must be running as an
   * unauthenticated user.
   *
   * @param name unique name for the suite
   * @throws CalFacadeException on fatal error
   */
  void setCalSuite(String name) throws CalFacadeException;

  /** This structure is set up early during init. Provides information about the
   * current principal.
   *
   * @return lots of info
   */
  PrincipalInfo getPrincipalInfo();

  /** Get the current stats
   *
   * @return BwStats object
   */
  BwStats getStats();

  /** Log the current stats
   *
   * @throws CalFacadeException if not admin
   */
  void logStats() throws CalFacadeException;

  /**
   *
   * @return list of info about open interfaces
   */
  List<IfInfo> getActiveIfInfos();

  /** Kill an errant interface.
   *
   * @param ifInfo IfInfo for process
   */
  void kill(IfInfo ifInfo);

  /**
   *
   * @param val a hopefully informative message
   */
  void setState(String val);

  /** Flush any backend data we may be hanging on to ready for a new
   * sequence of interactions. This is intended to help with web based
   * applications, especially those which follow the action/render url
   * pattern used in portlets.
   *
   * <p>A flushAll can discard a back end session allowing open to get a
   * fresh one. close() can then be either a no-op or something like a
   * hibernate disconnect.
   *
   * <p>This method should be called before calling open (or after calling
   * close).
   *
   * @throws CalFacadeException on fatal error
   */
  void flushAll() throws CalFacadeException;

  /** Signal the start of a sequence of operations. These overlap transactions
   * in that there may be 0 to many transactions started and ended within an
   * open/close call and many open/close calls within a transaction.
   *
   * <p>The open close methods are mainly associated with web style requests
   * and open will usually be called early in the incoming request handling
   * and close will always be called on the way out allowing the interface an
   * opportunity to reacquire (on open) and release (on close) any resources
   * such as connections.
   *
   * @throws CalFacadeException on fatal error
   */
  void open() throws CalFacadeException;

  /**
   * @return boolean true if open
   */
  boolean isOpen();

  /**
   * @return boolean true if open and rolled back
   * @throws CalFacadeException on fatal error
   */
  boolean isRolledback() throws CalFacadeException;

  /** Call on the way out after handling a request..
   *
   * @throws CalFacadeException on fatal error
   */
  void close() throws CalFacadeException;

  /** Start a (possibly long-running) transaction. In the web environment
   * this might do nothing. The endTransaction method should in some way
   * check version numbers to detect concurrent updates and fail with an
   * exception.
   *
   * @throws CalFacadeException on fatal error
   */
  void beginTransaction() throws CalFacadeException;

  /** End a (possibly long-running) transaction. In the web environment
   * this should in some way check version numbers to detect concurrent updates
   * and fail with an exception.
   *
   * @throws CalFacadeException on fatal error
   */
  void endTransaction() throws CalFacadeException;

  /** Call if there has been an error during an update process.
   *
   */
  void rollbackTransaction();

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   */
  Timestamp getCurrentTimestamp();

  /**
   * @return IcalCallback for ical
   */
  IcalCallback getIcalCallback();

  IcalCallback getIcalCallback(Boolean timezonesByReference);

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  /** Obtain a filter parser
   *
   * @return SimpleFilterParser handler
   */
  SimpleFilterParser getFilterParser();

  /** Obtain an object which handles user preferences
   *
   * @return PreferencesI   preferences handler
   */
  PreferencesI getPrefsHandler();

  /** Obtain an object which handles events
   *
   * @return EventsI   events handler
   */
  EventsI getEventsHandler();

  /** Obtain an object which handles filters
   *
   * @return FiltersI   filters handler
   */
  FiltersI getFiltersHandler();

  /** Obtain an object which handles calendars
   *
   * @return CalendarsI   calendars handler
   */
  CalendarsI getCalendarsHandler();

  /** Obtain an object which handles calendar suites
   *
   * @return CalSuitesI handler
   */
  CalSuitesI getCalSuitesHandler();

  /**
   * @param publick true for public index
   * @return the indexer
   */
  BwIndexer getIndexer(boolean publick,
                       String docType);

  /** Obtain an object which handles resources
   *
   * @return ResourcesI   resources handler
   */
  ResourcesI getResourcesHandler();

  /** Obtain an object which handles views
   *
   * @return ViewsI handler
   */
  ViewsI getViewsHandler();

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  /** Returns an object representing the current principal.
   *
   * @return BwPrincipal       representing the current principal
   */
  BwPrincipal getPrincipal();

  /** Get an initialised UserAuth object for the current user.
   *
   * @return UserAuth    implementation.
   * @throws CalFacadeException on fatal error
   */
  UserAuth getUserAuth() throws CalFacadeException;

  /** Check the access for the given entity. Returns the current access
   * or optionally throws a no access exception.
   *
   * @param ent the entity
   * @param desiredAccess access we want
   * @param returnResult true to return a result even if no access
   * @return CurrentAccess never null on return
   * @throws CalFacadeException if returnResult false and no access
   */
  CurrentAccess checkAccess(BwShareableDbentity<?> ent,
                            int desiredAccess,
                            boolean returnResult)
          throws CalFacadeException;

  /* ====================================================================
   *                   Synch Reports
   * ==================================================================== */

  /**
   * @param path to collection
   * @param token from previous report or null
   * @param limit - negative for no limit on result set size
   * @param recurse true for effectively sync-level infinite
   * @return report
   * @throws CalFacadeException on fatal error
   */
  SynchReport getSynchReport(String path,
                             String token,
                             int limit,
                             boolean recurse) throws CalFacadeException;
}
