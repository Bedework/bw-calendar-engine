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
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Set;

/** This is the calendars interface for the  bedework system.
 * Calendars is something of a misnomer - we're really talking collections here.
 * The type property defines which type of collection we are dealing with.
 * The CalDAV spec defines what is allowable, e.g. no collections
 * inside a calendar collection.
 *
 * <p>To allow us to enforce access checks we wrap the object inside a wrapper
 * class which blocks access to the getChildren method. To retrieve the children
 * of a calendar object call the getCalendars(BwCalendar) method. The resulting
 * collection is a set of access checked, wrapped objects. Only accessible
 * children will be returned.
 *
 * @author Mike Douglass
 */
public interface CalendarsI extends Serializable {
  /** Returns the tree of public calendars. The returned objects are those to
   * which the current user has access.
   *
   * @return BwCalendar   root with all children attached
   * @throws CalFacadeException
   */
  BwCalendar getPublicCalendars() throws CalFacadeException;

  /** Returns root path of calendars owned by the current user. For
   * unauthenticated this will be the public calendar root.
   *
   * @return String principal home.
   * @throws CalFacadeException
   */
  String getHomePath() throws CalFacadeException;

  /** Returns root of calendars owned by the current user.
   *
   * <p>For authenticated, personal access this always returns the user
   * entry in the /user calendar tree, e.g. for user smithj it would return
   * an entry /user/smithj
   *
   * @return BwCalendar   user home.
   * @throws CalFacadeException
   */
  BwCalendar getHome() throws CalFacadeException;

  /** Returns root of calendars owned by the given principal.
   *
   * <p>For authenticated, personal access this always returns the user
   * entry in the /user calendar tree, e.g. for user smithj it would return
   * an entry smithj with path /user/smithj
   *
   * @param  principal
   * @param freeBusy      true if this is for freebusy access
   * @return BwCalendar   user home.
   * @throws CalFacadeException
   */
  BwCalendar getHome(BwPrincipal principal,
                     boolean freeBusy) throws CalFacadeException;

  /** A virtual path might be for example "/user/adgrp_Eng/Lectures/Lectures"
   * which has two two components<ul>
   * <li>"/user/adgrp_Eng/Lectures" and</li>
   * <li>"Lectures"</li></ul>
   *
   * <p>
   * "/user/adgrp_Eng/Lectures" is a real path which is an alias to
   * "/public/aliases/Lectures" which is a folder containing the alias
   * "/public/aliases/Lectures/Lectures" which is aliased to the single calendar.
   *
   * @param vpath
   * @return collection of collection objects - null for bad vpath
   * @throws CalFacadeException
   */
  Collection<BwCalendar> decomposeVirtualPath(String vpath) throws CalFacadeException;

  /** Returns children of the given calendar to which the current user has
   * some access.
   *
   * @param  cal          parent calendar
   * @return Collection   of BwCalendar
   * @throws CalFacadeException
   */
  Collection<BwCalendar> getChildren(BwCalendar cal) throws CalFacadeException;

  /** Return a list of calendars in which calendar objects can be
   * placed by the current user.
   *
   * <p>Caldav currently does not allow collections inside collections so that
   * calendar collections are the leaf nodes only.
   *
   * @param includeAliases - true to include aliases - for public admin we don't
   *                    want aliases
   * @return Set   of BwCalendar
   * @throws CalFacadeException
   */
  Set<BwCalendar> getAddContentCollections(boolean includeAliases)
          throws CalFacadeException;

  /** Check to see if a collection is empty. A collection is not empty if it
   * contains other collections or calendar entities.
   *
   * @param val      BwCalendar object to check
   * @return boolean true if the calendar is empty
   * @throws CalFacadeException
   */
  boolean isEmpty(BwCalendar val) throws CalFacadeException;

  /** Get a calendar given the path. If the path is that of a 'special'
   * calendar, for example the deleted calendar, it may not exist if it has
   * not been used.
   *
   * @param  path          String path of calendar
   * @return BwCalendar null for unknown calendar
   * @throws CalFacadeException
   */
  BwCalendar get(String path) throws CalFacadeException;

  /** Get a special calendar (e.g. Trash) for the current user. If it does not
   * exist and is supported by the target system it will be created.
   *
   * @param  calType   int special calendar type.
   * @param  create    true if we should create it if non-existent.
   * @return BwCalendar null for unknown calendar
   * @throws CalFacadeException
   */
  BwCalendar getSpecial(int calType,
                        boolean create) throws CalFacadeException;

  /** set the default calendar for the current user.
   *
   * @param  val    BwCalendar
   * @throws CalFacadeException
   */
  void setPreferred(BwCalendar  val) throws CalFacadeException;

  /** Get the default calendar for the current user.
   *
   * @return BwCalendar null for unknown calendar
   * @throws CalFacadeException
   */
  BwCalendar getPreferred() throws CalFacadeException;

  /** Add a calendar object
   *
   * <p>The new calendar object will be added to the db. If the indicated parent
   * is null it will be added as a root level calendar.
   *
   * <p>Certain restrictions apply, mostly because of interoperability issues.
   * A calendar cannot be added to another calendar which already contains
   * entities, e.g. events etc.
   *
   * <p>Names cannot contain certain characters - (complete this)
   *
   * <p>Name must be unique at this level, i.e. all paths must be unique
   *
   * @param  val     BwCalendar new object
   * @param  parentPath  String path to parent.
   * @return BwCalendar object as added. Parameter val MUST be discarded
   * @throws CalFacadeException
   */
  BwCalendar add(BwCalendar val,
                 String parentPath) throws CalFacadeException;

  /** Change the name (path segment) of a calendar object.
   *
   * @param  val         BwCalendar object
   * @param  newName     String name
   * @throws CalFacadeException
   */
  public void rename(BwCalendar val,
                     String newName) throws CalFacadeException;

  /** Move a calendar object from one parent to another
   *
   * @param  val         BwCalendar object
   * @param  newParent   BwCalendar potential parent
   * @throws CalFacadeException
   */
  public void move(BwCalendar val,
                   BwCalendar newParent) throws CalFacadeException;

  /** Update a calendar object
   *
   * @param  val     BwCalendar object
   * @throws CalFacadeException
   */
  public void update(BwCalendar val) throws CalFacadeException;

  /** Delete a calendar. Also remove it from the current user preferences (if any).
   *
   * @param val      BwCalendar calendar
   * @param emptyIt  true to delete contents
   * @return boolean  true if it was deleted.
   *                  false if it didn't exist
   * @throws CalFacadeException for in use or marked as default calendar
   */
  boolean delete(BwCalendar val,
                 boolean emptyIt) throws CalFacadeException;

  /** Return true if cal != null and it represents a (local) user root
   *
   * @param cal
   * @return boolean
   * @throws CalFacadeException
   */
  public boolean isUserRoot(BwCalendar cal) throws CalFacadeException;

  /** Attempt to get calendar referenced by the alias. For an internal alias
   * the result will also be set in the aliasTarget property of the parameter.
   *
   * @param val
   * @param resolveSubAlias - if true and the alias points to an alias, resolve
   *                  down to a non-alias.
   * @param freeBusy
   * @return BwCalendar
   * @throws CalFacadeException
   */
  public BwCalendar resolveAlias(BwCalendar val,
                                 boolean resolveSubAlias,
                                 boolean freeBusy) throws CalFacadeException;

  /** */
  public enum CheckSubscriptionResult {
    /** No action was required */
    ok,

    /** No such collection */
    notFound,

    /** Not external subscription */
    notExternal,

    /** resubscribed */
    resubscribed,

    /** Synch service is unavailable */
    noSynchService,

    /** failed */
    failed;
  }

  /** Check the subscription if this is an external subscription. Will contact
   * the synch server and check the validity. If there is no subscription
   * onthe synch server will attempt to resubscribe.
   *
   * @param path
   * @return result of call
   * @throws CalFacadeException
   */
  public CheckSubscriptionResult checkSubscription(String path) throws CalFacadeException;

  /** Return the value to be used as the sync-token property for the given path.
   * This is effectively the max sync-token of the collection and any child
   * collections.
   *
   * @param path
   * @return a sync-token
   * @throws CalFacadeException
   */
  public String getSyncToken(String path) throws CalFacadeException;
}
