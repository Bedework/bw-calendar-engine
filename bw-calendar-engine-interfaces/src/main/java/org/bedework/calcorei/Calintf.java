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

import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.events.SysEventBase;

import java.sql.Timestamp;
import java.util.Collection;

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
        extends CoreAccessI, CoreAlarmsI, CoreCalendarsI,
        CoreCalSuitesI, CoreDumpRestoreI, CoreEventsI,
        CoreFilterDefsI, CorePrincipalsAndPrefsI, CoreResourcesI {
  interface FilterParserFetcher {
    SimpleFilterParser getFilterParser();
  }

  /** Signal the start of a sequence of operations. These overlap transactions
   * in that there may be 0 to many transactions started and ended within an
   * open/close call and many open/close calls within a transaction.
   *
   * <p>This will be called directly after creating the object and
   * <p>During the initial opening of a new object we may not be fully
   * initialised.
   *
   * @param filterParserFetcher for the parsing of filters
   * @param logId for tracing
   * @param configs for configuration info
   * @param forRestore true if this is for a system restore
   * @param indexRebuild  true if we are rebuilding the index.
   * @param publicAdmin boolean true if this is a public events admin app
   * @param publicAuth boolean true if this is authenticated public events app
   * @param publicSubmission true for the submit app
   * @param sessionless true if this is a sessionless client
   * @param authenticated true for an authenticated user
   * @param dontKill true if this is a system process
   */
  void open(FilterParserFetcher filterParserFetcher,
            String logId,
            Configurations configs,
            boolean forRestore,
            boolean indexRebuild,
            boolean publicAdmin,
            boolean publicAuth,
            boolean publicSubmission,
            boolean authenticated,
            boolean sessionless,
            boolean dontKill);

  /** Must be called once we know the principal.
   *
   * @param principalInfo    Required for access evaluation.
   */
  void initPinfo(PrincipalInfo principalInfo);

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
   */
  void setDbStatsEnabled(boolean enable);

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

  /** Call on the way out after handling a request.
   *
   */
  void close();

  /** Start a (possibly long-running) transaction. In the web environment
   * this might do nothing. The endTransaction method should in some way
   * check version numbers to detect concurrent updates and fail with an
   * exception.
   *
   */
  void beginTransaction();

  /** End a (possibly long-running) transaction. In the web environment
   * this should in some way check version numbers to detect concurrent updates
   * and fail with an exception.
   *
   */
  void endTransaction();

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
   */
  void flush();

  /** Clear session - probably need to flush first.
   *
   */
  void clear();

  /* * Replace session with a clean one - probably need to flush first.
   *
   * /
  void replaceSession();
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

  String getCalendarNameFromType(int calType);

  CalendarWrapper wrap(BwCalendar val);

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
   * @param ev system event
   */
  void postNotification(SysEventBase ev);

  /** Called to flush any queued notifications. Called by the commit
   * process.
   *
   */
  void flushNotifications();

  /** Clear any queued notifications without posting. Called by the commit
   * process.
   */
  void clearNotifications();

  /**
   * @return href for current principal
   */
  String getPrincipalRef();

  /* ==============================================================
   *                   Access
   * ============================================================== */

  /** Return a Collection of the objects after checking access
   *
   * @param ents          Collection of BwShareableDbentity
   * @param desiredAccess access we want
   * @param alwaysReturn boolean flag behaviour on no access
   * @return Collection   of checked objects
   */
  Collection<? extends ShareableEntity>
  checkAccess(Collection<? extends ShareableEntity> ents,
              int desiredAccess,
              boolean alwaysReturn);

  /** Check the access for the given entity. Returns the current access
   * or null or optionally throws a no access exception.
   *
   * @param ent a shareable entity
   * @param desiredAccess access we want
   * @param returnResult true to return rather than throw exception
   * @return CurrentAccess
   */
  CurrentAccess checkAccess(ShareableEntity ent,
                            int desiredAccess,
                            boolean returnResult);

  /* ==========================================================
   *                   Some general helpers
   * ========================================================== */

  /** Used to fetch a category from the cache - assumes any access
   *
   * @param uid of category
   * @return BwCategory
   */
  BwCategory getCategory(String uid);

  /* ===========================================================
   *                   Free busy
   * =========================================================== */

  /** Get the fee busy for calendars (if cal != null) or for a principal.
   *
   * @param cals calendar set
   * @param who the principal
   * @param start of period
   * @param end of period
   * @param returnAll
   * @param ignoreTransparency include transparent
   * @return  BwFreeBusy object representing the calendar (or principal's)
   *          free/busy
   */
  BwEvent getFreeBusy(Collection<BwCalendar> cals,
                      BwPrincipal<?> who,
                      BwDateTime start, BwDateTime end,
                      boolean returnAll,
                      boolean ignoreTransparency);

  /* ===========================================================
   *                   Events
   * ========================================================== */

  Collection<CoreEventInfo> postGetEvents(
          Collection<?> evs,
          int desiredAccess,
          boolean nullForNoAccess);

  /* Post-processing of event access has been checked
   */
  CoreEventInfo postGetEvent(BwEvent ev,
                             CurrentAccess ca);

  /* Post-processing of event. Return null or throw exception for no access
   */
  CoreEventInfo postGetEvent(BwEvent ev,
                             int desiredAccess,
                             boolean nullForNoAccess);

  /* ==========================================================
   *                       General db methods
   * ========================================================== */

  /**
   * @param val the entity
   * @return - merged entity
   */
  BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val);

   /* ============================================================
    *                   Event Properties Factories
    * ============================================================ */

  /** Return an event properties handler.
   *
   * @param cl the event properties class
   * @return EventProperties
   */
  <T extends BwEventProperty<?>> CoreEventPropertiesI<T>  getEvPropsHandler(Class<T> cl);
}

