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

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.sysevents.events.SysEventBase;

import java.io.Serializable;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** This is the service interface to the calendar suite. This will be
 * used by web applications and web services as well as other applications
 * which wish to integrate calendar actions into their interface.
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
public abstract class CalSvcI implements AutoCloseable, Serializable {
  /** init may throw an exception with this message if read/write is needed
   */
  public static String upgradeToReadWriteMessage =
          "Upgrade to read/write";

  /** (Re)initialise the object for a particular use.
   *
   * @param pars        Defines the global parameters for the object
   * @throws RuntimeException on fatal errors
   */
  public abstract void init(CalSvcIPars pars);

  /** Return properties about the system that depend on authentication state.
   *
   * @return AuthProperties object - never null.
   */
  public abstract AuthProperties getAuthProperties();

  /** Return properties about the system that depend on authentication state.
   *
   * @param auth - true for auth proiperties false for unauth
   * @return AuthProperties object - never null.
   */
  public abstract AuthProperties getAuthProperties(boolean auth);

  /** Return properties about the system.
   *
   * @return SystemProperties object - never null.
   */
  public abstract SystemProperties getSystemProperties();

  /** Return notification properties.
   *
   * @return NotificationProperties object - never null.
   */
  public abstract NotificationProperties getNotificationProperties();

  /** Return properties for indexing.
   *
   * @return IndexProperties object - never null.
   */
  public abstract IndexProperties getIndexProperties();

  /** Set the calendar suite we are running as. Must be running as an
   * unauthenticated user.
   *
   * @param name unique name for the suite
   * @throws CalFacadeException
   */
  public abstract void setCalSuite(String name) throws CalFacadeException;

  /** This structure is set up early during init. Provides information about the
   * current principal.
   *
   * @return lots of info
   */
  public abstract PrincipalInfo getPrincipalInfo();

  /**
   * @return boolean true if super user
   */
  public abstract boolean getSuperUser();

  /** Returns a public key for the given domain and service - either or both of
   * which may be null.
   *
   * <p>This allows us to have different keys for communication with different
   * domains and for different services. At its simplest, both are ignored and a
   * single key (pair) is used to secure all communications.
   *
   * <p>This is used, for example, by iSchedule for DKIM verification.
   *
   * <p>In keeping with the DKIM approach, <ul>
   * <li>if there are no keys an empty object is returned.</li>
   * <li>To refuse keys for a domain/service return null.</li>
   *
   *
   * <p>
   * @param domain e.g. example.org
   * @param service name
   * @return key, empty key object or null.
   * @throws CalFacadeException
   */
  public abstract byte[] getPublicKey(String domain,
                                      String service) throws CalFacadeException;

  /** Get the current stats
   *
   * @return BwStats object
   */
  public abstract BwStats getStats();

  /** Enable/disable db statistics
   *
   * @param enable       boolean true to turn on db statistics collection
   * @throws CalFacadeException if not admin
   */
  public abstract void setDbStatsEnabled(boolean enable) throws CalFacadeException;

  /**
   *
   * @return boolean true if statistics collection enabled
   */
  public abstract boolean getDbStatsEnabled();

  /** Dump db statistics
   *
   * @throws CalFacadeException if not admin
   */
  public abstract void dumpDbStats() throws CalFacadeException;

  /** Get db statistics
   *
   * @return Collection of BwStats.StatsEntry objects
   * @throws CalFacadeException if not admin
   */
  public abstract Collection<StatsEntry> getDbStats() throws CalFacadeException;

  /** Log the current stats
   *
   * @throws CalFacadeException if not admin
   */
  public abstract void logStats() throws CalFacadeException;

  /**
   *
   * @return info for this interface
   */
  public abstract IfInfo getIfInfo();

  /**
   *
   * @return list of info about open interfaces
   */
  public abstract List<IfInfo> getActiveIfInfos();

  /** Kill an errant interface.
   *
   * @param ifInfo IfInfo for process
   */
  public abstract void kill(IfInfo ifInfo);

  /**
   *
   * @param val a hopefully informative message
   */
  public abstract void setState(String val);

  /** Send a notification event
   *
   * @param ev - system event
   */
  public abstract void postNotification(SysEventBase ev);

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
   * @throws CalFacadeException
   */
  public abstract void flushAll() throws CalFacadeException;

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
   * @throws CalFacadeException
   */
  public abstract void open() throws CalFacadeException;

  /**
   * @return boolean true if open
   */
  public abstract boolean isOpen();

  /**
   * @return boolean true if open and rolled back
   * @throws CalFacadeException
   */
  public abstract boolean isRolledback() throws CalFacadeException;

  /** Call on the way out after handling a request..
   *
   * @throws CalFacadeException
   */
  public abstract void close() throws CalFacadeException;

  /** Start a (possibly long-running) transaction. In the web environment
   * this might do nothing. The endTransaction method should in some way
   * check version numbers to detect concurrent updates and fail with an
   * exception.
   *
   * @throws CalFacadeException
   */
  public abstract void beginTransaction() throws CalFacadeException;

  /** End a (possibly long-running) transaction. In the web environment
   * this should in some way check version numbers to detect concurrent updates
   * and fail with an exception.
   *
   * @throws CalFacadeException
   */
  public abstract void endTransaction() throws CalFacadeException;

  /** Call if there has been an error during an update process.
   *
   */
  public abstract void rollbackTransaction();

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   */
  public abstract Timestamp getCurrentTimestamp();

  /**
   * @return a blob
   * @throws CalFacadeException on fatal error
   */
  public abstract Blob getBlob(final byte[] val) throws CalFacadeException;

  /** Call to reassociate an entity with the current database session
   *
   * @param val to reattach
   * @throws CalFacadeException
   */
  public abstract void reAttach(BwDbentity<?> val) throws CalFacadeException;

  /** Call to merge an entity with the current database session
   *
   * @param val to merge
   * @return - merged entity
   * @throws CalFacadeException
   */
  public abstract BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val) throws CalFacadeException;

  /**
   * @return IcalCallback for ical
   */
  public abstract IcalCallback getIcalCallback();

  public abstract IcalCallback getIcalCallback(final Boolean timezonesByReference);

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  /** Obtain a dump handler
   *
   * @return DumpIntf handler
   * @throws CalFacadeException
   */
  public abstract DumpIntf getDumpHandler() throws CalFacadeException;

  /** Obtain a restore handler
   *
   * @return RestoreIntf handler
   * @throws CalFacadeException
   */
  public abstract RestoreIntf getRestoreHandler() throws CalFacadeException;

  /** Obtain a filter parser
   *
   * @return SimpleFilterParser handler
   */
  public abstract SimpleFilterParser getFilterParser();

  /** Obtain an object which handles system parameters
   *
   * @return SysparsI handler
   */
  public abstract SysparsI getSysparsHandler();

  /** Get currently configured mailer.
   *
   * @return MailerIntf object of class in config
   */
  public abstract MailerIntf getMailer();

  /** Obtain an object which handles user preferences
   *
   * @return PreferencesI   preferences handler
   */
  public abstract PreferencesI getPrefsHandler();

  /** Obtain an object which handles admin functions
   *
   * @return AdminI   admin handler
   */
  public abstract AdminI getAdminHandler();

  /** Obtain an object which handles events
   *
   * @return EventsI   events handler
   */
  public abstract EventsI getEventsHandler();

  /** Obtain an object which handles filters
   *
   * @return FiltersI   filters handler
   */
  public abstract FiltersI getFiltersHandler();

  /** Obtain an object which handles calendars
  *
  * @return CalendarsI   calendars handler
  */
  public abstract CalendarsI getCalendarsHandler();

  /** Obtain an object which handles calendar suites
   *
   * @return CalSuitesI handler
   */
  public abstract CalSuitesI getCalSuitesHandler();

  /**
   * @param publick true for public index
   * @return the indexer
   */
  public abstract BwIndexer getIndexer(final boolean publick,
                                       final String docType);

  /**
   * @param principal - for given principal
   * @return the indexer
   */
  public abstract BwIndexer getIndexer(String principal,
                                       String docType);

  /**
   * @param entity may influence choice of indexer
   * @return BwIndexer
   */
  public abstract BwIndexer getIndexer(BwOwnedDbentity<?> entity);

  /** .
   *
   * @param principal href
   * @param docType document type
   * @return the indexer
   */
  public abstract BwIndexer getIndexerForReindex(String principal,
                                                 final String docType,
                                                 final String name);

  /** Obtain an object which handles notifications
   *
   * @return NotificationsI   notifications handler
   */
  public abstract NotificationsI getNotificationsHandler();

  /** Obtain an object which handles resources
   *
   * @return ResourcesI   resources handler
   */
  public abstract ResourcesI getResourcesHandler();

  /** Get an object which implements scheduling methods.
   *
   * @return SchedulingI object
   */
  public abstract SchedulingI getScheduler();

  /** Obtain an object which handles sharing
   *
   * @return SharingI   sharing handler
   */
  public abstract SharingI getSharingHandler();

  /** Get an object which interacts with the synch engine.
   *
   * @return SynchI object
   */
  public abstract SynchI getSynch();

  /** Obtain an object which handles user objects
   *
   * @return UsersI handler
   */
  public abstract UsersI getUsersHandler();

  /** Obtain an object which handles views
   *
   * @return ViewsI handler
   */
  public abstract ViewsI getViewsHandler();

  /** Return an object to handle directory information. This will be the default
   * object for the current usage, i.e. admin or user.
   *
   * @return Directories
   */
  public abstract Directories getDirectories();

  /** Get a Groups object for non-admin users.
   *
   * @return Groups    implementation.
   */
  public abstract Directories getUserDirectories();

  /** Get a Groups object for administrators. This allows the admin client to
   * display or manipulate administrator groups.
   *
   * @return Groups    implementation.
   */
  public abstract Directories getAdminDirectories();

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  /** Return the categories maintenance object.
   *
   * @return Categories object
   */
  public abstract Categories getCategoriesHandler();

  /** Return the locations maintenance object.
   *
   * @return Locations
   */
  public abstract Locations getLocationsHandler();

  /** Return the contacts maintenance object.
   *
   * @return Contacts
   */
  public abstract Contacts getContactsHandler();

  /* ====================================================================
   *                       dump/restore methods
   *
   * These are used to handle the needs of dump/restore. Perhaps we
   * can eliminate the need at some point...
   * ==================================================================== */

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the objects
   */
  public abstract <T> Iterator<T> getObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the objects for current principal
   */
  public abstract <T> Iterator<T> getPrincipalObjectIterator(Class<T> cl);

  /**
   *
   * @param cl class of objects
   * @return iterator over all the public objects
   */
  public abstract <T> Iterator<T> getPublicObjectIterator(Class<T> cl);

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  /** Returns an object representing the current principal.
   *
   * @return BwPrincipal       representing the current principal
   */
  public abstract BwPrincipal getPrincipal();

  /** Find the principal with the given href.
   *
   * @param href          String principal hierarchy path
   * @return BwPrincipal  representing the principal or null if not there
   */
  public abstract BwPrincipal getPrincipal(final String href);

  /** Get an initialised UserAuth object for the current user.
   *
   * @return UserAuth    implementation.
   * @throws CalFacadeException
   */
  public abstract UserAuth getUserAuth() throws CalFacadeException;

  /**
   * @return System limit or user overrride - bytes.
   */
  public abstract long getUserMaxEntitySize();

  /** Fetch the preferences for the given principal href.
   *
   * @param principalHref href
   * @return the preferences for the principal
   * @throws CalFacadeException
   */
  public abstract BwPreferences getPreferences(final String principalHref) throws CalFacadeException;

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  /* XXX These should no be required - there was some issue with earlier hibernate (maybe).
   */

  /** Remove any refs to this object
   *
   * @param val shareable entity
   * @throws CalFacadeException
   */
  public abstract void removeFromAllPrefs(final BwShareableDbentity<?> val) throws CalFacadeException;

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  /* XXX This should really be some sort of directory function - perhaps
   * via carddav
   */

  /** Find a group given its account name
   *
   * @param  account           String group name
   * @param admin          true for an admin group
   * @return BwGroup        group object
   * @exception CalFacadeException If there's a problem
   */
  public abstract BwGroup findGroup(final String account,
                                    boolean admin) throws CalFacadeException;

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  /** Change the access to the given calendar entity.
   *
   * @param ent      BwShareableDbentity
   * @param aces     Collection of ace
   * @param replaceAll true to replace the entire access list.
   * @throws CalFacadeException
   */
  public abstract void changeAccess(BwShareableDbentity<?> ent,
                                    Collection<Ace> aces,
                                    boolean replaceAll) throws CalFacadeException;

  /** Remove any explicit access for the given who to the given calendar entity.
   *
   * @param ent      BwShareableDbentity
   * @param who      AceWho
   * @throws CalFacadeException
   */
  public abstract void defaultAccess(BwShareableDbentity<?> ent,
                                     AceWho who) throws CalFacadeException;

  /** Check the access for the given entity. Returns the current access
   * or optionally throws a no access exception.
   *
   * @param ent the entity
   * @param desiredAccess access we want
   * @param returnResult true to return a result even if no access
   * @return CurrentAccess never null on return
   * @throws CalFacadeException if returnResult false and no access
   */
  public abstract CurrentAccess checkAccess(BwShareableDbentity<?> ent,
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
   * @throws CalFacadeException
   */
  public abstract SynchReport getSynchReport(String path,
                                    String token,
                                    int limit,
                                    boolean recurse) throws CalFacadeException;

  /* ====================================================================
   *                   Timezones
   * ==================================================================== */

  /** Update the system after changes to timezones. This is a lengthy process
   * so the method allows the caller to specify how many updates are to take place
   * before returning.
   *
   * <p>To restart the update, call the method again, giving it the result from
   * the last call as a parameter.
   *
   * <p>If called again after all events have been checked the process will be
   * redone using timestamps to limit the check to events added or updated since
   * the first check. Keep calling until the number of updated events is zero.
   *
   * @param limit   -1 for no limit
   * @param checkOnly  don't update if true.
   * @param info    null on first call, returned object from previous calls.
   * @return UpdateFromTimeZonesInfo staus of the update
   * @throws CalFacadeException
   */
  public abstract UpdateFromTimeZonesInfo updateFromTimeZones(String colHref,
                                                              int limit,
                                                     boolean checkOnly,
                                                     UpdateFromTimeZonesInfo info
                                                     ) throws CalFacadeException;
}
