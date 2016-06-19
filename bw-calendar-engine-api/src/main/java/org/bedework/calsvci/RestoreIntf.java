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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;

/** Interface which defines the database functions needed to restore the
 * calendar database. The methods need to be called in the order defined
 * below.
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public interface RestoreIntf {
  /** Caller needs to see the messages.
   */
  public interface RestoreLogger {
    /**
     * @param msg
     */
    void info(String msg);

    /**
     * @param msg
     */
    void warn(String msg);

    /**
     * @param msg
     */
    void error(String msg);

    /**
     * @param msg
     * @param t
     */
    void error(final String msg, final Throwable t);

    /**
     * @param msg
     */
    void debug(String msg);

    /**
     * @return true for debug on
     */
    boolean isDebugEnabled();
  }

  /**
   * @param val
   */
  void setLogger(RestoreLogger val);

  /** Allow transactions to span many updates. May not work well.
   *
   * @param val
   */
  void setBatchSize(int val);

  /** Call to end a transaction - even if batched
   *
   * @throws Throwable
   */
  void endTransactionNow() throws Throwable;

  /** Call to end a transaction - may be ignored if batching
   *
   * @throws Throwable
   */
  void endTransaction() throws Throwable;

  /** Check for an empty system first
   *
   * @throws Throwable
   */
  void checkEmptySystem() throws Throwable;

  /** Restore system pars
   *
   * @param o
   * @throws Throwable
   */
  void restoreSyspars(BwSystem o) throws Throwable;

  /** Restore principal
   *
   * @param o
   * @throws Throwable
   */
  void restorePrincipal(BwPrincipal o) throws Throwable;

  /** Restore an admin group - though not the user entries nor
   * the authuser entries.
   *
   * @param o   Object to restore
   * @throws Throwable
   */
  void restoreAdminGroup(BwAdminGroup o) throws Throwable;

  /**
   * @param o
   * @param pr
   * @throws Throwable
   */
  void addAdminGroupMember(BwAdminGroup o, BwPrincipal pr) throws Throwable;

  /** Get an admin group given it's name.
   *
   * @param name     String name of the group
   * @return BwAdminGroup
   * @throws Throwable
   */
  BwAdminGroup getAdminGroup(String name) throws Throwable;

  /** Restore an auth user and preferences
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreAuthUser(BwAuthUser o) throws Throwable;

  /** Restore an event and associated entries
   *
   * @param ei   Object to restore with id set
   * @throws Throwable
   */
  void restoreEvent(EventInfo ei) throws Throwable;

  /** Get an event
   *
   * @param owner - the current user we are acting as - eg, for an annotation and
   *           fetching the master event this will be the annotation owner.
   * @param colPath
   * @param recurrenceId
   * @param uid
   * @return BwEvent
   * @throws Throwable
   */
  BwEvent getEvent(BwPrincipal owner,
                   String colPath,
                   String recurrenceId,
                   String uid) throws Throwable;

  /** Restore category
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreCategory(BwCategory o) throws Throwable;

  /** Restore calendar suite
   *
   * @param o   Object to restore
   * @throws Throwable
   */
  void restoreCalSuite(BwCalSuite o) throws Throwable;

  /** Restore location
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreLocation(BwLocation o) throws Throwable;

  /** Restore contact
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreContact(BwContact o) throws Throwable;

  /** Restore filter
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreFilter(BwFilterDef o) throws Throwable;

  /** Restore resource
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreResource(BwResource o) throws Throwable;

  /** Restore user prefs
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   */
  void restoreUserPrefs(BwPreferences o) throws Throwable;

  /* * Restore alarm - not needed - restored as part of event
   *
   * @param o   Object to restore with id set
   * @throws Throwable
   * /
  void restoreAlarm(BwAlarm o) throws Throwable;
  */

  /**
   * @param path
   * @return BwCalendar
   * @throws Throwable
   */
  BwCalendar getCalendar(String path) throws Throwable;

  /**
   * @param uid
   * @return BwCategory
   * @throws Throwable
   */
  BwCategory getCategory(String uid) throws Throwable;

  /**
   * @param uid
   * @return BwContact
   * @throws Throwable
   */
  BwContact getContact(String uid) throws Throwable;

  /**
   * @param uid
   * @return BwLocation
   * @throws Throwable
   */
  BwLocation getLocation(String uid) throws Throwable;

  /**
   * @param href
   * @return BwPrincipal
   * @throws CalFacadeException
   */
  BwPrincipal getPrincipal(String href) throws CalFacadeException;

  /** Save a single root calendar - no parent is set in the entity
   *
   * @param val
   * @throws Throwable
   */
  void saveRootCalendar(BwCalendar val) throws Throwable;

  /** Restore a single calendar - parent is set in the entity
   *
   * @param val
   * @throws Throwable
   */
  void addCalendar(BwCalendar val) throws Throwable;

  /** */
  public enum FixAliasResult {
    /** No action was required */
    ok,

    /** No access to target collection */
    noAccess,

    /** Wrong access to target collection */
    wrongAccess,

    /** No such target collection */
    notFound,

    /** Part of or points to a circular chain */
    circular,

    /** Broken chain */
    broken,

    /** reshared */
    reshared,

    /** failed */
    failed
  }

  /** Restore sharing for the given principal href
   *
   * @param col - the target collection
   * @param shareeHref - the sharee
   * @return indication of how it went
   * @throws CalFacadeException
   */
  FixAliasResult fixSharee(BwCalendar col,
                           String shareeHref) throws CalFacadeException;
}

