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

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.util.ChangeTable;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Interface for handling bedework event objects.
 *
 * @author Mike Douglass
 *
 */
public interface EventsI extends Serializable {
  /** Return one or more events for the current user using the calendar, guid
   * and the recurrence id as a key.
   *
   * <p>For non-recurring events, in normal calendar collections, one and only
   * one event should be returned.
   *
   * <p>For non-recurring events, in special calendar collections, more than
   * one event might be returned if the guid uniqueness requirement is relaxed,
   * for example, in the inbox.
   *
   * For recurring events, the 'master' event defining the rules together
   * with any exceptions should be returned.
   *
   * @param   colPath   String collection path
   * @param   guid      String guid for the event
   * @param   recurrenceId String recurrence id or null
   * @param recurRetrieval How recurring event is returned.
   * @return  Collection of EventInfo objects representing event(s).
   * @throws CalFacadeException on error
   */
  Collection<EventInfo> getByUid(String colPath,
                                 String guid,
                                 String recurrenceId,
                                 RecurringRetrievalMode recurRetrieval)
        throws CalFacadeException;

  /** Get events given the calendar and String name. Return null for not
   * found. There should be only one event or none. For recurring, the
   * overrides and possibly the instances will be attached.
   * 
   * NOTE: this does not provide alias filtering. 
   *
   * @param  colPath   String collection path fully resolved to target
   * @param name       String possible name
   * @return EventInfo or null
   * @throws CalFacadeException on error
   */
  EventInfo get(String colPath,
                       String name) throws CalFacadeException;

  /** Get events given the calendar and String name. Return null for not
   * found. There should be only one event or none. For recurring, the
   * overrides and possibly the instances will be attached.
   *
   * NOTE: this does not provide alias filtering. 
   *
   * @param  colPath   String collection path
   * @param name       String possible name
   * @param recurrenceId non-null for single instance
   * @return EventInfo or null
   * @throws CalFacadeException on error
   */
  EventInfo get(String colPath,
                       String name,
                       String recurrenceId)
          throws CalFacadeException;

  /** Get events given the calendar and String name. Return null for not
   * found. There should be only one event or none. For recurring, the
   * overrides and possibly the instances will be attached.
   *
   * This does provide alias filtering. 
   *
   * @param col   Collection - possibly a filtered alias
   * @param name  String name
   * @param recurrenceId non-null for single instance
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @return EventInfo or null
   * @throws CalFacadeException on error
   */
  EventInfo get(BwCalendar col,
                       String name,
                       String recurrenceId,
                       List<String> retrieveList)
          throws CalFacadeException;

  /** Return the events for the current user within the given date and time
   * range. If retrieveList is supplied only those fields (and a few required
   * fields) will be returned.
   *
   * @param cal          BwCalendar object - non-null means limit to given calendar
   *                     null is limit to current user
   * @param filter       BwFilter object restricting search or null.
   * @param startDate    BwDateTime start - may be null
   * @param endDate      BwDateTime end - may be null.
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @param recurRetrieval How recurring event is returned.
   * @return Collection  populated event value objects
   * @throws CalFacadeException on error
   */
  Collection<EventInfo> getEvents(BwCalendar cal,
                                         FilterBase filter,
                                         BwDateTime startDate,
                                         BwDateTime endDate,
                                         List<BwIcalPropertyInfoEntry> retrieveList,
                                         RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException;

  /** Delete an event.
   *
   * @param ei                 BwEvent object to be deleted
   * @param sendSchedulingMessage   Send a declined or cancel scheduling message
   * @return true if event deleted
   * @throws CalFacadeException on error
   */
  boolean delete(EventInfo ei,
                 boolean sendSchedulingMessage) throws CalFacadeException;

  /** Add an event and ensure its location and contact exist. The calendar path
   * must be set in the event.
   *
   * <p>For public events some calendar implementors choose to allow the
   * dynamic creation of locations and contacts. For each of those, if we have
   * an id, then the object represents a preexisting database item.
   *
   * <p>Otherwise the client has provided information which will be used to
   * locate an already existing location or contact. Failing that we use the
   * information to create a new entry.
   *
   * <p>For user clients, we generally assume no contact and the location is
   * optional. However, both conditions are enforced at the application level.
   *
   * <p>On return the event object will have been updated. In addition the
   * location and contact may have been updated.
   *
   * <p>If this is a scheduling event and noInvites is set to false then
   * invitations wil be sent out to the attendees.
   *
   * <p>The event to be added may be a reference to another event. In this case
   * a number of fields should have been copied from that event. Other fields
   * will come from the target.
   *
   * @param ei           EventInfo object to be added
   * @param noInvites    True for don't send invitations.
   * @param scheduling   True if this is to be added to an inbox - affects required
   *                     access.
   * @param autoCreateCollection - true if we should add a missing collection
   * @param rollbackOnError true to roll back if we get an error
   * @return UpdateResult Counts of changes.
   * @throws CalFacadeException on error
   */
  UpdateResult add(EventInfo ei,
                          boolean noInvites,
                          boolean scheduling,
                          boolean autoCreateCollection,
                          boolean rollbackOnError) throws CalFacadeException;

  /** Update an event. Any changeset should be embedded in the event info object.
   *
   * @param ei           EventInfo object to be added
   * @param noInvites    True for don't send invitations.
   * @return UpdateResult Counts of changes.
   * @throws CalFacadeException on error
   */
  UpdateResult update(final EventInfo ei,
                             final boolean noInvites) throws CalFacadeException;

  /** Update an event in response to an attendee. Exactly as normal update if
   * fromAtt is null. Otherwise no status update is sent to the given attendee
   *
   * <p>  Any changeset should be embedded in the event info object.
   *
   * @param ei           EventInfo object to be added
   * @param noInvites    True for don't send invitations.
   * @param fromAttUri   attendee responding
   * @return UpdateResult Counts of changes.
   * @throws CalFacadeException on error
   */
  UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             String fromAttUri) throws CalFacadeException;

  /** For an event to which we have write access we simply mark it deleted.
   *
   * <p>Otherwise we add an annotation maarking the event as deleted.
   *
   * @param event the event
   * @throws CalFacadeException on error
   */
  void markDeleted(BwEvent event) throws CalFacadeException;

  /** Result from copy or move operations. */
  enum CopyMoveStatus {
    /** */
    ok,

    /** Nothing happened */
    noop,

    /** Destination was created (i.e. didn't exist previously) */
    created,

    /** copy/move would create duplicate uid */
    duplicateUid,

    /** changing uids is illegal */
    changedUid,

    /** destination exists and overwrite not set. */
    destinationExists,
  }
  /** Copy or move the given named entity to the destination calendar and give it
   * the supplied name.
   *
   * @param from      Source named entity
   * @param to        Destination calendar
   * @param name      String name of new entity
   * @param copy      true for copying
   * @param overwrite if destination exists replace it.
   * @param newGuidOK   set a new guid if needed (e.g. copy in same collection)
   * @return CopyMoveStatus
   * @throws CalFacadeException on error
   */
  CopyMoveStatus copyMoveNamed(EventInfo from,
                               BwCalendar to,
                               String name,
                               boolean copy,
                               boolean overwrite,
                               boolean newGuidOK) throws CalFacadeException;

  /** Claim ownership of this event
   *
   * @param ev  event
   * @throws CalFacadeException on error
   */
  void claim(BwEvent ev) throws CalFacadeException;

  /** Add cached or retrieved entities to the events in the list. These are
   * entities such as locations, categories etc.
   *
   * @param events  to have cached entities
   * @throws CalFacadeException
   */
  public void implantEntities(Collection<EventInfo> events) throws CalFacadeException;

  /** Realias the event - set categories according to the set of aliases.
   * 
   * <p>This is a bedework function in which we specify which set of aliases
   * we used to add the event. Aliases are used to filter the data and provide a
   * view for users, e.g category="Films"
   *
   * <p>We need these aliases to provide a way of informing the user what they can
   * subscribe to in order to see the events of interest.
   *
   * <p>We also use them to set and unset categories, allowing event submitters to
   * consider only topic areas and leave it up to system administrators to
   * define which categories get set
   *
   * <p>Each alias is a virtual path. For example "/user/adgrp_Eng/Lectures/Lectures"
   * might be a real path with two components<br/>
   * "/user/adgrp_Eng/Lectures" and<br/>
   * "Lectures"
   *
   * <p>"/user/adgrp_Eng/Lectures" is aliased to "/public/aliases/Lectures" which
   * is a folder containing the alias "/public/aliases/Lectures/Lectures" which
   * is aliased to the single calendar.
   *
   * @param ev  event
   * @return set of categories referenced by the aliases           
   * @throws CalFacadeException on unknown alias
   */
  Set<BwCategory> reAlias(BwEvent ev) throws CalFacadeException;

  class SetEntityCategoriesResult {
    /** rc */
    public static final int success = 0;
    
    public int rcode = -1;

    /** Number of BwCategory created */
    public int numCreated;

    /** Number of BwCategory added */
    public int numAdded;

    /** Number of BwCategory removed */
    public int numRemoved;
  }

  /** Set the entity categories based on multivalued request parameter "categoryKey".
   *
   * <p>We build a list of categories then update the membership of the entity
   * category collection to correspond.
   *
   * @param ent categorised entity to be adjusted
   * @param extraCats Categories to add as a result of other operations
   * @param defCatUids uids of default categories for current user
   * @param allDefCatUids uids of all public default categories
   * @param strCatUids Categories to add from request
   * @param changes a change table
   * @return setEventCategoriesResult
   * @throws CalFacadeException on error
   */
  SetEntityCategoriesResult setEntityCategories(CategorisedEntity ent,
                                                Set<BwCategory> extraCats,
                                                Set<String> defCatUids,
                                                Set<String> allDefCatUids,
                                                Collection<String> strCatUids,
                                                ChangeTable changes) throws CalFacadeException;
}
