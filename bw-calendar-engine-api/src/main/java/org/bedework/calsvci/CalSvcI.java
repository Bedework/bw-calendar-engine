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
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.sysevents.events.SysEventBase;

import java.io.Serializable;
import java.sql.Blob;
import java.util.Collection;
import java.util.Iterator;

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
public interface CalSvcI
        extends CalSvcIRo, AutoCloseable, Serializable {
  /** Return notification properties.
   *
   * @return NotificationProperties object - never null.
   */
  NotificationProperties getNotificationProperties();

  /**
   * @return boolean true if super user
   */
  boolean getSuperUser();

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
   * @throws CalFacadeException on fatal error
   */
  byte[] getPublicKey(String domain,
                      String service) throws CalFacadeException;

  /** Enable/disable db statistics
   *
   * @param enable       boolean true to turn on db statistics collection
   * @throws CalFacadeException if not admin
   */
  void setDbStatsEnabled(boolean enable) throws CalFacadeException;

  /**
   *
   * @return boolean true if statistics collection enabled
   */
  boolean getDbStatsEnabled();

  /** Dump db statistics
   *
   * @throws CalFacadeException if not admin
   */
  void dumpDbStats() throws CalFacadeException;

  /** Get db statistics
   *
   * @return Collection of BwStats.StatsEntry objects
   * @throws CalFacadeException if not admin
   */
  Collection<StatsEntry> getDbStats() throws CalFacadeException;

  /** Send a notification event
   *
   * @param ev - system event
   */
  void postNotification(SysEventBase ev);

  /**
   * @return a blob
   * @throws CalFacadeException on fatal error
   */
  Blob getBlob(byte[] val) throws CalFacadeException;

  /** Call to reassociate an entity with the current database session
   *
   * @param val to reattach
   * @throws CalFacadeException on fatal error
   */
  void reAttach(BwDbentity<?> val) throws CalFacadeException;

  /** Call to merge an entity with the current database session
   *
   * @param val to merge
   * @return - merged entity
   * @throws CalFacadeException on fatal error
   */
  BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val) throws CalFacadeException;

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  /** Obtain a dump handler
   *
   * @return DumpIntf handler
   * @throws CalFacadeException on fatal error
   */
  DumpIntf getDumpHandler() throws CalFacadeException;

  /** Obtain a restore handler
   *
   * @return RestoreIntf handler
   * @throws CalFacadeException on fatal error
   */
  RestoreIntf getRestoreHandler() throws CalFacadeException;

  /** Obtain an object which handles system parameters
   *
   * @return SysparsI handler
   */
  SysparsI getSysparsHandler();

  /** Get currently configured mailer.
   *
   * @return MailerIntf object of class in config
   */
  MailerIntf getMailer();

  /** Obtain an object which handles admin functions
   *
   * @return AdminI   admin handler
   */
  AdminI getAdminHandler();

  /**
   * @param principal - for given principal
   * @return the indexer
   */
  BwIndexer getIndexer(String principal,
                       String docType);

  /**
   * @param entity may influence choice of indexer
   * @return BwIndexer
   */
  BwIndexer getIndexer(BwOwnedDbentity<?> entity);

  /** .
   *
   * @param principal href
   * @param docType document type
   * @return the indexer
   */
  BwIndexer getIndexerForReindex(String principal,
                                 String docType,
                                 String name);

  /** Obtain an object which handles notifications
   *
   * @return NotificationsI   notifications handler
   */
  NotificationsI getNotificationsHandler();

  /** Get an object which implements scheduling methods.
   *
   * @return SchedulingI object
   */
  SchedulingI getScheduler();

  /** Obtain an object which handles sharing
   *
   * @return SharingI   sharing handler
   */
  SharingI getSharingHandler();

  /** Get an object which interacts with the synch engine.
   *
   * @return SynchI object
   */
  SynchI getSynch();

  /** Obtain an object which handles user objects
   *
   * @return UsersI handler
   */
  UsersI getUsersHandler();

  /** Obtain an object which handles views
   *
   * @return ViewsI handler
   */
  ViewsI getViewsHandler();

  /** Return an object to handle directory information. This will be the default
   * object for the current usage, i.e. admin or user.
   *
   * @return Directories
   */
  Directories getDirectories();

  /** Get a Groups object for non-admin users.
   *
   * @return Groups    implementation.
   */
  Directories getUserDirectories();

  /** Get a Groups object for administrators. This allows the admin client to
   * display or manipulate administrator groups.
   *
   * @return Groups    implementation.
   */
  Directories getAdminDirectories();

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  /** Return the categories maintenance object.
   *
   * @return Categories object
   */
  Categories getCategoriesHandler();

  /** Return the locations maintenance object.
   *
   * @return Locations
   */
  Locations getLocationsHandler();

  /** Return the contacts maintenance object.
   *
   * @return Contacts
   */
  Contacts getContactsHandler();

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
  <T> Iterator<T> getObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @return iterator over all the objects for current principal
   */
  <T> Iterator<T> getPrincipalObjectIterator(Class<T> cl);

  /**
   *
   * @param cl class of objects
   * @return iterator over all the public objects
   */
  <T> Iterator<T> getPublicObjectIterator(Class<T> cl);

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  /** Find the principal with the given href.
   *
   * @param href          String principal hierarchy path
   * @return BwPrincipal  representing the principal or null if not there
   */
  BwPrincipal getPrincipal(String href);

  /**
   * @return System limit or user overrride - bytes.
   */
  long getUserMaxEntitySize();

  /** Fetch the preferences for the given principal href.
   *
   * @param principalHref href
   * @return the preferences for the principal
   * @throws CalFacadeException on fatal error
   */
  BwPreferences getPreferences(String principalHref) throws CalFacadeException;

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  /* XXX These should no be required - there was some issue with earlier hibernate (maybe).
   */

  /** Remove any refs to this object
   *
   * @param val shareable entity
   * @throws CalFacadeException on fatal error
   */
  void removeFromAllPrefs(BwShareableDbentity<?> val) throws CalFacadeException;

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
  BwGroup findGroup(String account,
                    boolean admin) throws CalFacadeException;

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  /** Change the access to the given calendar entity.
   *
   * @param ent      BwShareableDbentity
   * @param aces     Collection of ace
   * @param replaceAll true to replace the entire access list.
   * @throws CalFacadeException on fatal error
   */
  void changeAccess(BwShareableDbentity<?> ent,
                    Collection<Ace> aces,
                    boolean replaceAll) throws CalFacadeException;

  /** Remove any explicit access for the given who to the given calendar entity.
   *
   * @param ent      BwShareableDbentity
   * @param who      AceWho
   * @throws CalFacadeException on fatal error
   */
  void defaultAccess(BwShareableDbentity<?> ent,
                     AceWho who) throws CalFacadeException;

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
   * @throws CalFacadeException on fatal error
   */
  UpdateFromTimeZonesInfo updateFromTimeZones(String colHref,
                                              int limit,
                                              boolean checkOnly,
                                              UpdateFromTimeZonesInfo info
  ) throws CalFacadeException;
}
