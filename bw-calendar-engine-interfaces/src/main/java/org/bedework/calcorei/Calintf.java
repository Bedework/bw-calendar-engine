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
package org.bedework.calcorei;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.sysevents.events.SysEventBase;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** This is the low level interface to the calendar database.
 *
 * <p>This interface provides a view of the data as seen by the supplied user
 * id. This may or may not be the actual authenticated user of whatever
 * application is driving it.
 *
 * <p>This is of particular use for public events administration. A given
 * authenticated user may be the member of a number of groups, and this module
 * will be initialised with the id of one of those groups. At some point
 * the authenticated user may choose to switch identities to manage a different
 * group.
 *
 * <p>The UserAuth object returned by getUserAuth usually represents the
 * authenticated user and determines the rights that user has.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public interface Calintf
        extends CoreCalendarsI, CoreEventsI, CoreFilterDefsI,
        CoreResourcesI, CoreUserAuthI {
  interface FilterParserFetcher {
    SimpleFilterParser getFilterParser();
  }

  /** Must be called once we know the principal.
   *
   * @param principalInfo    Required for access evaluation.
   * @throws CalFacadeException on fatal error
   */
  void initPinfo(PrincipalInfo principalInfo) throws CalFacadeException;

  /**
   * 
   * @return info for this interface
   */
  IfInfo getIfInfo();
  
  /**
   *
   * @return boolean true if super user
   */
  boolean getSuperUser();

  /**
   *
   * @return a label
   */
  String getLogId();

  /**
   *
   * @return dontKill flag
   */
  boolean getDontKill();

  /**
   *
   * @return an augmented label
   */
  String getTraceId();

  /** Updated every time state is changed. Not necessarily an
   * indication of idleness - it depends on state being updated,
   *
   * @return UTC time state was last changed.
   */
  String getLastStateTime();

  /**
   *
   * @param val a hopefully informative message
   */
  void setState(String val);

  /**
   *
   * @return a hopefully informative message
   */
  String getState();

  /**
   * @return PrincipalInfo
   */
  PrincipalInfo getPrincipalInfo();

  /**
   * @return true if restoring
   */
  boolean getForRestore();

  /** Get the current system (not db) stats
   *
   * @return BwStats object
   */
  BwStats getStats();

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
   */
  void dumpDbStats();

  /** Get db statistics
   *
   * @return Collection of BwStats.StatsEntry objects
   */
  Collection<StatsEntry> getDbStats();

  /** Get information about this interface
   *
   * @return CalintfInfo
   */
  CalintfInfo getInfo();

  /** Signal the start of a sequence of operations. These overlap transactions
   * in that there may be 0 to many transactions started and ended within an
   * open/close call and many open/close calls within a transaction.
   *
   * <p>During the initial opening of a new object we may not be fully
   * initialised.
   *
   * @param filterParserFetcher for the parsing of filters
   * @param logId for tracing
   * @param configs for configuration info
   * @param webMode  true for long-running multi request conversations.
   * @param forRestore true if this is for a system restore
   * @param indexRebuild  true if we are rebuilding the index.
   * @param publicAdmin boolean true if this is a public events admin app
   * @param publicAuth boolean true if this is authenticated public events app
   * @param publicSubmission true for the submit app
   * @param sessionless true if this is a sessionless client
   * @param authenticated true for an authenticated user
   * @param dontKill true if this is a system process
   * @throws CalFacadeException on error
   */
  void open(FilterParserFetcher filterParserFetcher,
            String logId,
            Configurations configs,
            boolean webMode,
            boolean forRestore,
            boolean indexRebuild,
            boolean publicAdmin,
            boolean publicAuth,
            boolean publicSubmission,
            boolean authenticated,
            boolean sessionless,
            boolean dontKill) throws CalFacadeException;

  /** Call on the way out after handling a request..
   *
   */
  void close();

  /** Start a (possibly long-running) transaction. In the web environment
   * this might do nothing. The endTransaction method should in some way
   * check version numbers to detect concurrent updates and fail with an
   * exception.
   *
   * @throws CalFacadeException on error
   */
  void beginTransaction() throws CalFacadeException;

  /** End a (possibly long-running) transaction. In the web environment
   * this should in some way check version numbers to detect concurrent updates
   * and fail with an exception.
   *
   * @throws CalFacadeException on error - rollback has been called
   */
  void endTransaction() throws CalFacadeException;

  /** Call if there has been an error during an update process.
   *
   */
  void rollbackTransaction();

  /**
   * @return boolean true if open and rolled back
   */
  boolean isRolledback();

  /** Flush queued operations.
   *
   * @throws CalFacadeException
   */
  void flush() throws CalFacadeException;

  /** Clear session - probably need to flush first.
   *
   * @throws CalFacadeException on hibernate error
   */
  void clear() throws CalFacadeException;

  /* * Replace session with a clean one - probably need to flush first.
   *
   * @throws CalFacadeException on hibernate error
   * /
  void replaceSession() throws CalFacadeException;
  */

  /**
   * Get the set of active transactions
   * @return set
   */
  Collection<? extends Calintf> active();

  /** Kill an errant interface.
   *
   */
  void kill();

  /**
   *
   * @return time in millis we started the transaction
   */
  long getStartMillis();

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   */
  Timestamp getCurrentTimestamp();

  /** Call to reassociate an entity with the current database session
   *
   * @param val the entity
   * @throws CalFacadeException
   */
  void reAttach(BwDbentity<?> val) throws CalFacadeException;

  String getCalendarNameFromType(int calType) throws CalFacadeException;

  /**
   * @param docType type of entity
   * @return the appropriate indexer
   */
  BwIndexer getIndexer(String docType);

  /**
   * @param entity to select indexer
   * @return BwIndexer
   */
  BwIndexer getIndexer(Object entity);

  /** Return a public indexer if we're in public mode or one for the given href
   *
   * @param principalHref if we're not public
   * @param docType type of entity
   * @return BwIndexer
   */
  BwIndexer getIndexer(String principalHref, String docType);

  /**
   * @param docType type of entity
   * @return the indexer
   */
  BwIndexer getPublicIndexer(String docType);

  /**
   * @param docType type of entity
   * @return the [public] indexer
   */
  BwIndexer getIndexer(boolean publick, String docType);

  /**
   * @param entity to index
   */
  void indexEntity(BwUnversionedDbentity<?> entity);

  /**
   * @param entity to index
   */
  void indexEntityNow(BwCalendar entity);

  /** Method for reindexing.
   *
   * @param principalHref if we're not public
   * @param docType type of entity
   * @return the indexer
   */
  BwIndexer getIndexerForReindex(String principalHref,
                                 String docType,
                                 String indexName);

  /* ====================================================================
   *                   Notifications
   * ==================================================================== */

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev
   */
  void postNotification(final SysEventBase ev);

  /** Called to flush any queued notifications. Called by the commit
   * process.
   *
   */
  void flushNotifications();

  /** Clear any queued notifications without posting. Called by the commit
   * process.
   */
  void clearNotifications();

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
  void changeAccess(ShareableEntity ent,
                    Collection<Ace> aces,
                    boolean replaceAll) throws CalFacadeException;

  /** Remove any explicit access for the given who to the given calendar entity.
   *
   * @param ent      A shareable entity
   * @param who      AceWho
   * @throws CalFacadeException
   */
  void defaultAccess(ShareableEntity ent,
                     AceWho who) throws CalFacadeException;

  /** Return a Collection of the objects after checking access
   *
   * @param ents          Collection of BwShareableDbentity
   * @param desiredAccess access we want
   * @param alwaysReturn boolean flag behaviour on no access
   * @return Collection   of checked objects
   * @throws CalFacadeException for no access or other failure
   */
  Collection<? extends ShareableEntity>
  checkAccess(Collection<? extends ShareableEntity> ents,
              int desiredAccess,
              boolean alwaysReturn)
          throws CalFacadeException;

  /** Check the access for the given entity. Returns the current access
   * or null or optionally throws a no access exception.
   *
   * @param ent a shareable entity
   * @param desiredAccess access we want
   * @param returnResult true to return rather than throw exception
   * @return CurrentAccess
   * @throws CalFacadeException if returnResult false and no access
   */
  CurrentAccess checkAccess(ShareableEntity ent,
                            int desiredAccess,
                            boolean returnResult)
          throws CalFacadeException;

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  /** Return all unexpired alarms before a given time. If time is 0 all
   * unexpired alarms will be retrieved.
   *
   * <p>Any cancelled alarms will be excluded from the result.
   *
   * <p>Typically the system will call this with a time set into the near future
   * and then queue up alarms that are near to triggering.
   *
   * @param triggerTime
   * @return Collection of unexpired alarms.
   * @throws CalFacadeException
   */
  Collection<BwAlarm> getUnexpiredAlarms(long triggerTime)
          throws CalFacadeException;

  /** Given an alarm return the associated event(s)
   *
   * @param alarm
   * @return an event.
   * @throws CalFacadeException
   */
  Collection<BwEvent> getEventsByAlarm(BwAlarm alarm)
          throws CalFacadeException;

  /* ====================================================================
   *                   Some general helpers
   * ==================================================================== */

  /** Used to fetch a category from the cache - assumes any access
   *
   * @param uid
   * @return BwCategory
   * @throws CalFacadeException on error
   */
  BwCategory getCategory(String uid) throws CalFacadeException;

  /* ====================================================================
   *                   Free busy
   * ==================================================================== */

  /** Get the fee busy for calendars (if cal != null) or for a principal.
   *
   * @param cals
   * @param who
   * @param start
   * @param end
   * @param returnAll
   * @param ignoreTransparency
   * @return  BwFreeBusy object representing the calendar (or principal's)
   *          free/busy
   * @throws CalFacadeException
   */
  BwEvent getFreeBusy(Collection<BwCalendar> cals, BwPrincipal<?> who,
                      BwDateTime start, BwDateTime end,
                      boolean returnAll,
                      boolean ignoreTransparency)
          throws CalFacadeException;

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  Collection<CoreEventInfo> postGetEvents(
          Collection<?> evs,
          int desiredAccess,
          boolean nullForNoAccess)
          throws CalFacadeException;

  /* Post processing of event access has been checked
   */
  CoreEventInfo postGetEvent(BwEvent ev,
                             CurrentAccess ca);

  /* Post processing of event. Return null or throw exception for no access
   */
  CoreEventInfo postGetEvent(BwEvent ev,
                             int desiredAccess,
                             boolean nullForNoAccess) throws CalFacadeException;

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  /**
   * @param val an entity to restore
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwUnversionedDbentity<?> val) throws CalFacadeException;

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  /**
   * @param val principal
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwPrincipal<?> val) throws CalFacadeException;

  /**
   * @param val the event property
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwEventProperty<?> val) throws CalFacadeException;

  /**
   * @param val the preferences
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwPreferences val) throws CalFacadeException;

  /**
   * @param val to save/update/index
   * @throws CalFacadeException on fatal error
   */
  void saveOrUpdate(BwCalSuite val) throws CalFacadeException;

  /**
   * @param val auth user entry to delete
   * @throws CalFacadeException on fatal error
   */
  void delete(BwAuthUser val) throws CalFacadeException;

  /**
   * @param val the preferences
   * @throws CalFacadeException on fatal error
   */
  void delete(BwPreferences val) throws CalFacadeException;

  /**
   * @param val calsuite to delete and unindex
   * @throws CalFacadeException on fatal error
   */
  void delete(BwCalSuite val) throws CalFacadeException;

  /**
   * @param val the entity
   * @return - merged entity
   * @throws CalFacadeException on fatal error
   */
  BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val) throws CalFacadeException;

  /**
   * @return a blob
   */
  Blob getBlob(byte[] val);

  /**
   * @return a blob
   */
  Blob getBlob(InputStream val, long length);

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
   * @param cl Class of objects
   * @return iterator over all the public objects
   */
  <T> Iterator<T> getPublicObjectIterator(Class<T> cl);

  /**
   *
   * @param cl Class of objects
   * @param colPath for objects
   * @return iterator over all the objects with the given col path
   */
  <T> Iterator<T> getObjectIterator(Class<T> cl,
                                    String colPath);

  /** Return an iterator over hrefs for events.
   *
   * @param start first object
   * @return iterator over the objects
   */
  Iterator<String> getEventHrefs(int start);

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  /** Find the principal with the given href.
   *
   * @param href          String principal hierarchy path
   * @return BwPrincipal  representing the principal or null if not there
   * @throws CalFacadeException
   */
  BwPrincipal<?> getPrincipal(String href) throws CalFacadeException;

  /** Get a partial list of principal hrefs.
   *
   * @param start         Position to start
   * @param count         Number we want
   * @return list of hrefs - null for no more
   * @throws CalFacadeException
   */
  List<String> getPrincipalHrefs(int start,
                                 int count) throws CalFacadeException;

  /** Fetch the preferences for the given principal.
   *
   * @param principalHref
   * @return the preferences for the principal
   * @throws CalFacadeException
   */
  BwPreferences getPreferences(String principalHref) throws CalFacadeException;

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  /* XXX These should no be required - there was some issue with earlier hibernate (maybe).
   */

  /** Remove any refs to this object
   *
   * @param val the entity
   * @throws CalFacadeException
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
  BwGroup<?> findGroup(final String account,
                       boolean admin) throws CalFacadeException;

  /**
   * @param group the group
   * @param admin          true for an admin group
   * @return Collection
   * @throws CalFacadeException on error
   */
  Collection<BwGroup<?>> findGroupParents(
          BwGroup<?> group,
          boolean admin) throws CalFacadeException;

  /**
   * @param group
   * @param admin          true for an admin group
   * @throws CalFacadeException
   */
  void updateGroup(BwGroup<?> group,
                   boolean admin) throws CalFacadeException;

  /** Delete a group
   *
   * @param  group           BwGroup group object to delete
   * @param admin          true for an admin group
   * @exception CalFacadeException If there's a problem
   */
  void removeGroup(BwGroup<?> group,
                   boolean admin) throws CalFacadeException;

  /** Add a member to a group
   *
   * @param group          a group principal
   * @param val             BwPrincipal new member
   * @param admin          true for an admin group
   * @exception CalFacadeException   For invalid usertype values.
   */
  void addMember(BwGroup<?> group,
                 BwPrincipal<?> val,
                 boolean admin) throws CalFacadeException;

  /** Remove a member from a group
   *
   * @param group          a group principal
   * @param val            BwPrincipal new member
   * @param admin          true for an admin group
   * @exception CalFacadeException   For invalid usertype values.
   */
  void removeMember(BwGroup<?> group,
                    BwPrincipal<?> val,
                    boolean admin) throws CalFacadeException;

  /** Get the direct members of the given group.
   *
   * @param  group           BwGroup group object to add
   * @param admin          true for an admin group
   * @return list of members
   * @throws CalFacadeException
   */
  Collection<BwPrincipal<?>> getMembers(BwGroup<?> group,
                                        boolean admin) throws CalFacadeException;

  /** Return all groups to which this user has some access. Never returns null.
   *
   * @param admin          true for an admin group
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwGroup<?>> getAllGroups(boolean admin)
          throws CalFacadeException;

  /** Return all admin groups to which this user has some access. Never returns null.
   *
   * @return Collection    of BwAdminGroup
   * @throws CalFacadeException
   */
  Collection<BwAdminGroup> getAdminGroups() throws CalFacadeException;

  /** Return all groups of which the given principal is a member. Never returns null.
   *
   * <p>Does not check the returned groups for membership of other groups.
   *
   * @param val            a principal
   * @param admin          true for an admin group
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwGroup<?>> getGroups(BwPrincipal<?> val,
                                   boolean admin) throws CalFacadeException;

  /** Return all admin groups of which the given principal is a member. Never returns null.
   *
   * <p>Does not check the returned groups for membership of other groups.
   *
   * @param val            a principal
   * @return Collection    of BwGroup
   * @throws CalFacadeException
   */
  Collection<BwAdminGroup> getAdminGroups(
          BwPrincipal<?> val) throws CalFacadeException;

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  /** Get a detached instance of the calendar suite given the 'owning' group
   *
   * @param  group     BwAdminGroup
   * @return BwCalSuite null for unknown calendar suite
   * @throws CalFacadeException
   */
  BwCalSuite get(BwAdminGroup group) throws CalFacadeException;

  /** Get a (live) calendar suite given the name
   *
   * @param  name     String name of calendar suite
   * @return BwCalSuiteWrapper null for unknown calendar suite
   * @throws CalFacadeException
   */
  BwCalSuite getCalSuite(String name) throws CalFacadeException;

  /** Get calendar suites to which this user has access
   *
   * @return Collection     of BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  Collection<BwCalSuite> getAllCalSuites() throws CalFacadeException;

   /* ====================================================================
    *                   Event Properties Factories
    * ==================================================================== */

  /** Return an event properties handler.
   *
   * @param cl the event properties class
   * @return EventProperties
   */
  <T extends BwEventProperty<?>> CoreEventPropertiesI<T>  getEvPropsHandler(Class<T> cl);
}

