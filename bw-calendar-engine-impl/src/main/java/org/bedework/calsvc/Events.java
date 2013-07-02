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

import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.InternalEventKey;
import org.bedework.calcorei.CoreEventsI.UpdateEventResult;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.Categories;
import org.bedework.calsvci.EventProperties;
import org.bedework.calsvci.EventProperties.EnsureEntityExistsResult;
import org.bedework.calsvci.EventsI;
import org.bedework.icalendar.IcalTranslator;
import org.bedework.icalendar.Icalendar;
import org.bedework.sysevents.events.EntityFetchEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;

import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.cmt.access.PrivilegeDefs;
import edu.rpi.cmt.calendar.IcalDefs;
import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;
import edu.rpi.sss.util.Util;
import edu.rpi.sss.util.xml.tagdefs.CaldavTags;
import edu.rpi.sss.util.xml.tagdefs.NamespaceAbbrevs;

import net.fortuna.ical4j.model.Component;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;

/** This acts as an interface to the database for subscriptions.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Events extends CalSvcDb implements EventsI {
  Events(final CalSvc svci) {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#get(org.bedework.calfacade.svc.BwSubscription, org.bedework.calfacade.BwCalendar, java.lang.String, java.lang.String, org.bedework.calfacade.RecurringRetrievalMode)
   */
  @Override
  public Collection<EventInfo> get(final String colPath,
                                   final String guid, final String recurrenceId,
                                   final RecurringRetrievalMode recurRetrieval,
                                   final boolean scheduling)
          throws CalFacadeException {
    Collection<EventInfo> res = postProcess(getCal().getEvent(colPath,
                                                              guid,
                                                              recurrenceId,
                                                              scheduling,
                                                              recurRetrieval));

    int num = 0;

    if (res != null) {
      num = res.size();
    }
    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    return res;
  }

  @Override
  public EventInfo get(final String colPath, final String name,
                       final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    EventInfo res = postProcess(getCal().getEvent(colPath,
                                                  name,
                                                  recurRetrieval));

    int num = 0;

    if (res != null) {
      num = 1;
    }
    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    return res;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#getEvents(org.bedework.calfacade.BwCalendar, org.bedework.caldav.util.filter.Filter, org.bedework.calfacade.BwDateTime, org.bedework.calfacade.BwDateTime, java.util.List, org.bedework.calfacade.RecurringRetrievalMode)
   */
  @Override
  public Collection<EventInfo> getEvents(final BwCalendar cal, final FilterBase filter,
                                         final BwDateTime startDate, final BwDateTime endDate,
                                         final List<String> retrieveList,
                                         final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    Collection<BwCalendar> cals = null;

    if (cal != null) {
      cals = new ArrayList<BwCalendar>();
      cals.add(cal);
    }

    Collection<EventInfo> res =  getMatching(cals, filter, startDate, endDate,
                                             retrieveList,
                                             recurRetrieval, false);

    int num = 0;

    if (res != null) {
      num = res.size();
    }
    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    return res;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#delete(org.bedework.calfacade.svc.EventInfo, boolean)
   */
  @Override
  public boolean delete(final EventInfo ei,
                        final boolean sendSchedulingMessage) throws CalFacadeException {
    return delete(ei, false, sendSchedulingMessage);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#add(org.bedework.calfacade.svc.EventInfo, boolean, boolean, boolean)
   */
  @Override
  public UpdateResult add(final EventInfo ei,
                          final boolean noInvites,
                          final boolean scheduling,
                          final boolean rollbackOnError) throws CalFacadeException {
    try {
      UpdateResult updResult = ei.getUpdResult();
      updResult.adding = true;
      updResult.hasChanged = true;

      BwEvent event = ei.getEvent();

      adjustEntities(ei);

      BwPreferences prefs = getSvc().getPrefsHandler().get();
      if (prefs != null) {
        List<BwCategory> cats = getSvc().getCategoriesHandler().
            get(prefs.getDefaultCategoryUids());

        for (BwCategory cat: cats) {
          event.addCategory(cat);
        }
      }

      assignGuid(event); // Or just validate?

      validate(event);

      Collection<BwEventProxy> overrides = ei.getOverrideProxies();
      BwEventProxy proxy = null;
      BwEvent override = null;

      if (event instanceof BwEventProxy) {
        proxy = (BwEventProxy)event;
        override = proxy.getRef();
        setupSharableEntity(override, getPrincipal().getPrincipalRef());
      } else {
        setupSharableEntity(event, getPrincipal().getPrincipalRef());

        boolean avail = event.getEntityType() == IcalDefs.entityTypeVavailability;

        if (avail && (ei.getNumContainedItems() > 0)) {
          for (EventInfo aei: ei.getContainedItems()) {
            BwEvent av = aei.getEvent();
            av.setParent(event);

            setupSharableEntity(av,
                                getPrincipal().getPrincipalRef());
          }
        }
      }

      updateEntities(updResult, event);

      BwCalendar cal = getCols().get(event.getColPath());
      if (cal == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound);
      }

      BwCalendar undereffedCal = cal;

      if (cal.getAlias()) {
        /* Resolve the alias and put the event in it's proper place */

        //XXX This is probably OK for non-public admin
        boolean setCats = getSvc().getPars().getPublicAdmin();

        if (!setCats) {
          cal = getCols().resolveAlias(cal, true, false);
        } else {
          while (true) {
            Set<BwCategory> cats = cal.getCategories();

            for (BwCategory cat: cats) {
              event.addCategory(cat);
            }

            if (!cal.getAlias()) {
              break;
            }

            cal = getCols().resolveAlias(cal, false, false);
          }
        }

        event.setColPath(cal.getPath());
      }

      if (!cal.getCalendarCollection()) {
        throw new CalFacadeAccessException();
      }

      if (!event.getPublick() && Util.isEmpty(event.getAlarms())) {
        setDefaultAlarms(ei, undereffedCal);
      }

      boolean schedulingObject = false;

      if (cal.getCollectionInfo().scheduling &&
          (event.getOrganizerSchedulingObject() ||
           event.getAttendeeSchedulingObject())) {
        schedulingObject = true;
      }

      event.setDtstamps(getCurrentTimestamp());
      if (schedulingObject) {
        event.updateStag(getCurrentTimestamp());
      }

      /* All Overrides go in same calendar and have same name */

      if (overrides != null) {
        for (BwEventProxy ovei: overrides) {
          setScheduleState(ovei);

          ovei.setDtstamps(getCurrentTimestamp());

          if (cal.getCollectionInfo().scheduling &&
              (ovei.getOrganizerSchedulingObject() ||
               ovei.getAttendeeSchedulingObject())) {
            schedulingObject = true;
          }

          if (schedulingObject) {
            ovei.updateStag(getCurrentTimestamp());
          }

          BwEventAnnotation ann = ovei.getRef();
          ann.setColPath(event.getColPath());
          ann.setName(event.getName());
        }
      }

      UpdateEventResult uer;

      if (proxy != null) {
        uer = getCal().addEvent(override, overrides, scheduling, rollbackOnError);
      } else {
        uer = getCal().addEvent(event, overrides, scheduling, rollbackOnError);
      }

      boolean avail = event.getEntityType() == IcalDefs.entityTypeVavailability;

      if (avail && (ei.getNumContainedItems() > 0)) {
        for (EventInfo oei: ei.getContainedItems()) {
          oei.getEvent().setName(event.getName());
          UpdateEventResult auer = getCal().addEvent(oei.getEvent(), null,
                                                     scheduling, rollbackOnError);
          if (auer.errorCode != null) {
            //?
          }
        }
      }

      updResult.failedOverrides = uer.failedOverrides;

      if (!noInvites) {
        if (event.getAttendeeSchedulingObject()) {
          // Attendee replying?
          updResult.reply = true;
        }

        if (cal.getCollectionInfo().scheduling &&
            schedulingObject) {
          SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

          sched.implicitSchedule(ei,
                                 updResult,
                                 noInvites);

          /* We assume we don't need to update again to set attendee status
           * Trying to do an update results in duplicate key errors.
           *
           * If it turns out the scgedule status is not getting persisted in the
           * calendar entry then we need to find a way to set just that value in
           * already persisted entity.
           */
        }
      }

      return updResult;
    } catch (Throwable t) {
      if (debug) {
        error(t);
      }
      getSvc().rollbackTransaction();
      if (t instanceof CalFacadeException) {
        throw (CalFacadeException)t;
      }

      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#update(org.bedework.calfacade.svc.EventInfo, boolean)
   */
  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites) throws CalFacadeException {
    return update(ei, noInvites, null);
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             final String fromAttUri) throws CalFacadeException {
    try {
      BwEvent event = ei.getEvent();
      event.setDtstamps(getCurrentTimestamp());

      validate(event);
      adjustEntities(ei);

      boolean organizerSchedulingObject = false;
      boolean attendeeSchedulingObject = false;

      BwCalendar cal = getCols().get(event.getColPath());

      if (cal.getCollectionInfo().scheduling) {
        organizerSchedulingObject = event.getOrganizerSchedulingObject();
        attendeeSchedulingObject = event.getAttendeeSchedulingObject();
      }

      boolean schedulingObject = organizerSchedulingObject ||
                                 attendeeSchedulingObject;

      if (event.getSignificantChange() && schedulingObject) {
        event.updateStag(getCurrentTimestamp());
      }

      boolean changed = checkChanges(ei,
                                     organizerSchedulingObject,
                                     attendeeSchedulingObject) ||
                        ei.getOverridesChanged();

      boolean doReschedule = ei.getUpdResult().doReschedule;

      if (ei.getNumOverrides() > 0) {
        for (EventInfo oei: ei.getOverrides()) {
          setScheduleState(oei.getEvent());

          if (cal.getCollectionInfo().scheduling &&
               oei.getEvent().getAttendeeSchedulingObject()) {
            schedulingObject = true;
            attendeeSchedulingObject = true;
            // Shouldn't need to check organizer - it's set in the master even
            // if suppressed.
          }

          if (checkChanges(oei,
                           organizerSchedulingObject,
                           attendeeSchedulingObject)) {
            changed = true;
          }

          doReschedule = doReschedule || oei.getUpdResult().doReschedule;
        }
      }

      if (!changed) {
        return ei.getUpdResult();
      }

      if (doReschedule) {
        getSvc().getScheduler().setupReschedule(ei);
      }

      UpdateResult updResult = ei.getUpdResult();

      updateEntities(updResult, event);

      UpdateEventResult uer = getCal().updateEvent(event,
                                                   ei.getOverrideProxies(),
                                                   ei.getDeletedOverrideProxies(getPrincipalHref()));

      updResult.addedInstances = uer.added;
      updResult.updatedInstances = uer.updated;
      updResult.deletedInstances = uer.deleted;

      updResult.fromAttUri = fromAttUri;

      if (!noInvites) {
        if (schedulingObject &&
                        (organizerSchedulingObject || updResult.reply)) {
          SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

          sched.implicitSchedule(ei,
                                 updResult,
                                 noInvites);

          /* We assume we don't need to update again to set attendee status
           * Trying to do an update results in duplicate key errors.
           *
           * If it turns out the scgedule status is not getting persisted in the
           * calendar entry then we need to find a way to set just that value in
           * already persisted entity.
           */
        }
      }

      return updResult;
    } catch (Throwable t) {
      getSvc().rollbackTransaction();
      if (t instanceof CalFacadeException) {
        throw (CalFacadeException)t;
      }

      throw new CalFacadeException(t);
    }
  }

  @SuppressWarnings("unchecked")
  private boolean checkChanges(final EventInfo ei,
                               final boolean organizerSchedulingObject,
                               final boolean attendeeSchedulingObject) throws CalFacadeException {
    UpdateResult updResult = ei.getUpdResult();

    if (ei.getChangeset(getPrincipalHref()).isEmpty()) {
      // Forced update?
      updResult.hasChanged = true;
      if (attendeeSchedulingObject) {
        // Attendee replying?
        /* XXX We should really check to see if the value changed here -
         */
        updResult.reply = true;
      }

      return true;
    }

    if (debug) {
      ei.getChangeset(getPrincipalHref()).dumpEntries();
    }

    Collection<ChangeTableEntry> ctes = ei.getChangeset(getPrincipalHref()).getEntries();

    for (ChangeTableEntry cte: ctes) {
      if (!cte.getChanged()) {
        continue;
      }

      updResult.hasChanged = true;

      if (!organizerSchedulingObject &&
          cte.getIndex().equals(PropertyInfoIndex.ORGANIZER)) {
        // Never valid
        throw new CalFacadeForbidden(CaldavTags.attendeeAllowed,
                                     "Cannot change organizer");
      }

      if (cte.getIndex().equals(PropertyInfoIndex.ATTENDEE) ||
          cte.getIndex().equals(PropertyInfoIndex.VOTER)) {
        updResult.addedAttendees = cte.getAddedValues();
        updResult.deletedAttendees = cte.getRemovedValues();

        if (attendeeSchedulingObject) {
          // Attendee replying?
          /* XXX We should really check to see if the value changed here -
           */
          updResult.reply = true;
        }
      }

      if (organizerSchedulingObject) {
        BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(cte.getIndex());
        if (pi.getReschedule()) {
          updResult.doReschedule = true;
        }
      }
    }

    return updResult.hasChanged;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#markDeleted(org.bedework.calfacade.BwEvent)
   */
  @Override
  public void markDeleted(final BwEvent event) throws CalFacadeException {
    /* Trash disabled
    if (getCal().checkAccess(event, PrivilegeDefs.privWrite, true).accessAllowed) {
      // Have write access - just set the flag and move it into the owners trash
      event.setDeleted(true);

      GetSpecialCalendarResult gscr = getCal().getSpecialCalendar(getUser(), //event.getOwner(),
                                          BwCalendar.calTypeTrash,
                                          true,
                                          PrivilegeDefs.privWriteContent);
      if (gscr.created) {
        getCal().flush();
      }
      event.setCalendar(gscr.cal);

      if (!event.getOwner().equals(getUser())) {
        // Claim ownership
        event.setOwner(getUser());
      }

      EventInfo ei = new EventInfo(event);

      /* Names have to be unique. Just keep extending the name out till it works. I guess
       * a better approach would be a random suffix.
       * /
      int limit = 100;
      for (int i = 0; i < limit; i++) {
        try {
          update(ei, false, null, null, null);
          break;
        } catch (CalFacadeDupNameException dup) {
          if ((i + 1) == limit) {
            throw dup;
          }
          event.setName("a" + event.getName());
        }
      }
      return;
    }
    */
    // Need to annotate it as deleted

    BwEventProxy proxy = BwEventProxy.makeAnnotation(event, event.getOwnerHref(),
                                                     false);

    // Where does the ref go? Not in the same calendar - we have no access

    BwCalendar cal = getCal().getSpecialCalendar(getPrincipal(),
                                     BwCalendar.calTypeDeleted,
                                     true, PrivilegeDefs.privRead).cal;
    proxy.setOwnerHref(getPrincipal().getPrincipalRef());
    proxy.setDeleted(true);
    proxy.setColPath(cal.getPath());
    add(new EventInfo(proxy), true, false, false);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventsI#findCalendars(java.lang.String, java.lang.String)
   */
  @Override
  public Collection<BwCalendar> findCalendars(final String guid,
                                              final String rid) throws CalFacadeException {
    return getCal().findCalendars(guid, rid);
  }

  @Override
  public CopyMoveStatus copyMoveNamed(final EventInfo fromEi,
                                      final BwCalendar to,
                                      String name,
                                      final boolean copy,
                                      final boolean overwrite,
                                      final boolean newGuidOK) throws CalFacadeException {
    BwEvent ev = fromEi.getEvent();
    String fromPath = ev.getColPath();

    boolean sameCal = fromPath.equals(to.getPath());

    if (name == null) {
      name = ev.getName();
    }

    if (sameCal && name.equals(ev.getName())) {
      // No-op
      return CopyMoveStatus.noop;
    }

    try {
      // Get the target
      RecurringRetrievalMode rrm =
        new RecurringRetrievalMode(Rmode.overrides);

      EventInfo destEi = get(to.getPath(), name, rrm);

      if (destEi != null) {
        if (!overwrite) {
          return CopyMoveStatus.destinationExists;
        }

        if (!destEi.getEvent().getUid().equals(ev.getUid())) {
          // Not allowed to change uid.
          return CopyMoveStatus.changedUid;
        }

        //deleteEvent(destEi.getEvent(), true);
      }

      if (!copy) {
        // Moving the event.

        if (!sameCal) {
          /* Not sure why I was doing a delete+add
          delete(from, false, false); // Delete unreffed

          if (destEi != null) {
            delete(destEi.getEvent(), false, false); // Delete unreffed
          }

          add(to, newEi, true);
          */

          BwCalendar from = getCols().get(fromPath);

          getCal().moveEvent(ev, from, to);

          getCal().touchCalendar(from);
        } else {
          // Just changing name
          ev.setName(name);
        }

        ev.updateStag(getCurrentTimestamp());
        update(fromEi, false, null);
      } else {
        // Copying the event.

        BwEvent newEvent = (BwEvent)ev.clone();
        newEvent.setName(name);

        // WebDAV ACL say's new event must not carry over access
        newEvent.setAccess(null);

        EventInfo newEi = new EventInfo(newEvent);

        if (fromEi.getOverrideProxies() != null) {
          for (BwEventProxy proxy: fromEi.getOverrideProxies()) {
            newEi.addOverride(new EventInfo(proxy.clone(newEvent, newEvent)));
          }
        }

        if (sameCal && newGuidOK) {
          // Assign a new guid
          newEvent.setUid(null);
          assignGuid(newEvent);
        }

        if (destEi != null) {
          delete(destEi, false);
        }

        newEvent.setColPath(to.getPath());
        newEvent.updateStag(getCurrentTimestamp());

        add(newEi, true, false, true);
      }

      if (destEi != null) {
        return CopyMoveStatus.ok;
      }

      return CopyMoveStatus.created;
    } catch (CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.duplicateGuid)) {
        return CopyMoveStatus.duplicateUid;
      }

      throw cfe;
    }
  }

  @Override
  public void claim(final BwEvent ev) throws CalFacadeException {
    ev.setOwnerHref(null);
    ev.setCreatorHref(null);
    setupSharableEntity(ev, getPrincipal().getPrincipalRef());
  }

  /* ====================================================================
   *                   Package private methods
   * ==================================================================== */

  void updateEntities(final UpdateResult updResult,
                      final BwEvent event) throws CalFacadeException {

    BwContact ct = event.getContact();

    if (ct != null) {
      EnsureEntityExistsResult<BwContact> eeers =
        getSvc().getContactsHandler().ensureExists(ct,
                                                   event.getOwnerHref());

      if (eeers.added) {
        updResult.contactsAdded++;
      }

      // XXX only do this if we know it changed
      event.setContact(eeers.entity);
    }

    BwLocation loc = event.getLocation();

    if (loc != null) {
      EnsureEntityExistsResult<BwLocation> eeerl = getSvc().getLocationsHandler().ensureExists(loc,
                                                                        event.getOwnerHref());

      if (eeerl.added) {
        updResult.locationsAdded++;
      }

      // XXX only do this if we know it changed
      event.setLocation(eeerl.entity);
    }
  }

  /** Return all keys or all with a lastmod greater than or equal to that supplied.
   *
   * <p>The lastmod allows us to redo the search after we have updated timezones
   * to find all events added after we made the last call.
   *
   * <p>Note the lastmod has a coarse granularity so it may need to be backed off
   * to ensure all events are covered if doing batches.
   *
   * @param lastmod
   * @return collection of opaque key objects.
   * @throws CalFacadeException
   */
  Collection<? extends InternalEventKey> getEventKeysForTzupdate(final String lastmod)
          throws CalFacadeException {
    return getCal().getEventKeysForTzupdate(lastmod);
  }

  CoreEventInfo getEvent(final InternalEventKey key) throws CalFacadeException {
    return getCal().getEvent(key);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param cals
   * @param filter
   * @param startDate
   * @param endDate
   * @param retrieveList
   * @param recurRetrieval
   * @param freeBusy
   * @return Collection of matching events
   * @throws CalFacadeException
   */
  Collection<EventInfo> getMatching(final Collection<BwCalendar> cals,
                                    final FilterBase filter,
                                    final BwDateTime startDate, final BwDateTime endDate,
                                    final List<String> retrieveList,
                                    final RecurringRetrievalMode recurRetrieval,
                                    final boolean freeBusy) throws CalFacadeException {
    TreeSet<EventInfo> ts = new TreeSet<EventInfo>();

    if ((filter != null) && (filter.equals(BooleanFilter.falseFilter))) {
      return ts;
    }

    Collection<BwCalendar> calSet = null;

    if (cals != null) {
      /* Turn the calendar reference into a set of calendar collections
       */
      calSet = new ArrayList<BwCalendar>();

      for (BwCalendar cal:cals) {
        buildCalendarSet(calSet, cal, freeBusy);
      }
    }

    ts.addAll(postProcess(getCal().getEvents(calSet, filter,
                          startDate, endDate,
                          retrieveList,
                          recurRetrieval, freeBusy)));

    return ts;
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param colPath
   * @param guid
   * @param recurrenceId
   * @param scheduling
   * @param recurRetrieval
   * @return Collection<EventInfo> - collection as there may be more than
   *                one with this uid in the inbox.
   * @throws CalFacadeException
   */
  Collection<EventInfo> get(final String colPath,
                            final String guid, final String recurrenceId,
                            final boolean scheduling,
                            final RecurringRetrievalMode recurRetrieval)
                            throws CalFacadeException {
    return postProcess(getCal().getEvent(colPath, guid, recurrenceId,
                                         scheduling, recurRetrieval));
  }

  Set<EventInfo> getSynchEvents(final String path,
                                final String lastmod) throws CalFacadeException {
    return postProcess(getCal().getSynchEvents(path, lastmod));
  }

  /** Method which allows us to flag it as a scheduling action
  *
   * @param ei
   * @param scheduling - true for the scheduling system deleting in/outbox events
   * @param sendSchedulingMessage
   * @return boolean
   * @throws CalFacadeException
   */
  public boolean delete(final EventInfo ei,
                        final boolean scheduling,
                        final boolean sendSchedulingMessage) throws CalFacadeException {
    return delete(ei, scheduling, sendSchedulingMessage, false);
  }

  boolean delete(final EventInfo ei,
                 final boolean scheduling,
                 final boolean sendSchedulingMessage,
                 final boolean reallyDelete) throws CalFacadeException {
    if (ei == null) {
      return false;
    }

    BwEvent event = ei.getEvent();

    /* Note we don't just return immediately if this is a no-op because of
     * tombstoning. We go through the actions to allow access checks to take place.
     */

    if (!event.getTombstoned()) {
      // Handle some scheduling stuff.

      BwCalendar cal = getCols().get(event.getColPath());

      if (sendSchedulingMessage &&
          event.getSchedulingObject() &&
          (cal.getCollectionInfo().scheduling)) {
        // Should we also only do this if it affects freebusy?

        /* According to CalDAV we're supposed to do this before we delete the
         * event. If it fails we now have no way to record that.
         *
         * However that also requires a way to forcibly delete it so we need to
         * ensure we have that first. (Just don't set sendSchedulingMessage
         */
        try {
          SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();
          if (event.getAttendeeSchedulingObject()) {
            /* Send a declined message to the organizer
             */
            sched.sendReply(ei,
                            IcalDefs.partstatDeclined, null);
          } else if (event.getOrganizerSchedulingObject()) {
            // send a cancel
            UpdateResult uer = new UpdateResult();
            uer.deleting = true;

            sched.implicitSchedule(ei, uer, false);
          }
        } catch (CalFacadeException cfe) {
          if (debug) {
            error(cfe);
          }
        }
      }
    }

    if (!getCal().deleteEvent(event,
                              scheduling,
                              reallyDelete).eventDeleted) {
      getSvc().rollbackTransaction();
      return false;
    }

    if (event.getEntityType() != IcalDefs.entityTypeVavailability) {
      return true;
    }

    for (EventInfo aei: ei.getContainedItems()) {
      if (!getCal().deleteEvent(aei.getEvent(),
                                scheduling,
                                true).eventDeleted) {
        getSvc().rollbackTransaction();
        return false;
      }
    }

    return true;
  }

  @Override
  public void implantEntities(final Collection<EventInfo> events) throws CalFacadeException {
    if (Util.isEmpty(events)) {
      return;
    }

    Categories cats = getSvc().getCategoriesHandler();
    EventProperties<BwLocation> locs = getSvc().getLocationsHandler();

    for (EventInfo ei: events) {
      BwEvent ev = ei.getEvent();

      Set<String> catUids = ev.getCategoryUids();

      if (catUids != null) {
        for (String uid: catUids) {
          BwCategory cat = cats.get(uid);

          if (cat != null) {
            ev.addCategory(cat);
          }
        }
      }

      if (ev.getLocationUid() != null) {
        ev.setLocation(locs.getCached(ev.getLocationUid()));
      }
    }
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  /** Ensure that all references to entites are up to date, for example, ensure
   * that the list of category uids matches the actual list of categories.
   *
   * @param event
   * @throws CalFacadeException
   */
  private void adjustEntities(final EventInfo event) throws CalFacadeException {
    if (event == null) {
      return;
    }

    BwEvent ev = event.getEvent();

    ev.adjustCategories();

    if (ev.getLocation() != null) {
      ev.setLocationUid(ev.getLocation().getUid());
    } else {
      ev.setLocationUid(null);
    }
  }

  private void implantEntities(final EventInfo event) throws CalFacadeException {
    if (event == null) {
      return;
    }

    Categories cats = getSvc().getCategoriesHandler();
    EventProperties<BwLocation> locs = getSvc().getLocationsHandler();

    BwEvent ev = event.getEvent();

    Set<String> catUids = ev.getCategoryUids();

    if (catUids != null) {
      for (String uid: catUids) {
        BwCategory cat = cats.get(uid);

        if (cat != null) {
          ev.addCategory(cat);
        }
      }
    }

    if (ev.getLocationUid() != null) {
      ev.setLocation(locs.getCached(ev.getLocationUid()));
    }
  }

  private void buildCalendarSet(final Collection<BwCalendar> cals,
                                BwCalendar calendar,
                                final boolean freeBusy) throws CalFacadeException {
    if (calendar == null) {
      return;
    }

    int desiredAccess = PrivilegeDefs.privRead;
    if (freeBusy) {
      desiredAccess = PrivilegeDefs.privReadFreeBusy;
    }

    calendar = getCols().get(calendar.getPath());
    if (calendar == null) {
      // No access presumably
      return;
    }

    if (!getSvc().checkAccess(calendar, desiredAccess, true).getAccessAllowed()) {
      return;
    }

    if (calendar.getInternalAlias()) {
      BwCalendar saveColl = calendar;
      getCols().resolveAlias(calendar, true, freeBusy);

      while (calendar.getInternalAlias()) {
        calendar = calendar.getAliasTarget();

        if (calendar == null) {
          // No access presumably
          saveColl.setLastRefreshStatus(String.valueOf(HttpServletResponse.SC_FORBIDDEN) +
          ": Forbidden");
          return;
        }
      }
    }

    if (calendar.getCalendarCollection() ||
        calendar.getExternalSub() ||
        (cals.isEmpty() && calendar.getSpecial())) {
      /* It's a calendar collection - add if not 'special' or we're adding all
       */

      cals.add(calendar);

      return;
    }

    if (calendar.getCalType() != BwCalendar.calTypeFolder) {
      return;
    }

    for (BwCalendar c: getCols().getChildren(calendar)) {
      buildCalendarSet(cals, c, freeBusy);
    }
  }

  private void validate(final BwEvent ev) throws CalFacadeException {
    if (ev.getColPath() == null) {
      throw new CalFacadeException(CalFacadeException.noEventCalendar);
    }

    if (ev.getNoStart() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "noStart");
    }

    if (ev.getDtstart() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "dtstart");
    }

    if (ev.getDtend() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "dtend");
    }

    if (ev.getDuration() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "duration");
    }

    if (ev.getRecurring() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "recurring");
    }

    setScheduleState(ev);
  }

  /* Flag this as an attendee scheduling object or an organizer scheduling object
   */
  private void setScheduleState(final BwEvent ev) throws CalFacadeException {
    ev.setOrganizerSchedulingObject(false);
    ev.setAttendeeSchedulingObject(false);

    if ((ev.getEntityType() != IcalDefs.entityTypeEvent) &&
        (ev.getEntityType() != IcalDefs.entityTypeTodo) &&
        (ev.getEntityType() != IcalDefs.entityTypeVpoll)) {
      // Not a possible scheduling entity
      return;
    }

    BwOrganizer org = ev.getOrganizer();

    Collection<BwAttendee> atts = ev.getAttendees();

    if (Util.isEmpty(atts) || (org == null)) {
      return;
    }

    String curPrincipal = getSvc().getPrincipal().getPrincipalRef();
    AccessPrincipal evPrincipal =
      getSvc().getDirectories().caladdrToPrincipal(org.getOrganizerUri());

    if ((evPrincipal != null) &&
        (evPrincipal.getPrincipalRef().equals(curPrincipal))) {
      ev.setOrganizerSchedulingObject(true);

      if (ev instanceof BwEventProxy) {
        // Only add x-property to master
        return;
      }

      if (CalFacadeDefs.jasigSchedulingAssistant.equals(getPars().getClientId())) {
        ev.addXproperty(new BwXproperty(BwXproperty.bedeworkSchedAssist,
                                        null,
                                        "true"));
      }

      return;
    }

    for (BwAttendee att: atts) {
      /* See if at least one attendee is us */

      evPrincipal = getSvc().getDirectories().caladdrToPrincipal(att.getAttendeeUri());
      if ((evPrincipal != null) &&
          (evPrincipal.getPrincipalRef().equals(curPrincipal))) {
        ev.setAttendeeSchedulingObject(true);

        break;
      }
    }
  }

  private EventInfo postProcess(final CoreEventInfo cei)
          throws CalFacadeException {
    if (cei == null) {
      return null;
    }

    //trace("ev: " + ev);

    /* If the event is an event reference (an alias) implant it in an event
     * proxy and return that object.
     */
    BwEvent ev = cei.getEvent();

    if (ev instanceof BwEventAnnotation) {
      ev = new BwEventProxy((BwEventAnnotation)ev);
    }

    Set<EventInfo> overrides = new TreeSet<EventInfo>();
    if (cei.getOverrides() != null) {
      for (CoreEventInfo ocei: cei.getOverrides()) {
        BwEventProxy op = (BwEventProxy)ocei.getEvent();

        overrides.add(new EventInfo(op));
      }
    }

    EventInfo ei = new EventInfo(ev, overrides);

    /* Reconstruct if any contained items. */
    if (cei.getNumContainedItems() > 0) {
      for (CoreEventInfo ccei: cei.getContainedItems()) {
        BwEvent cv = ccei.getEvent();

        ei.addContainedItem(new EventInfo(cv));
      }
    }

    ei.setCurrentAccess(cei.getCurrentAccess());

    implantEntities(ei);

    return ei;
  }

  private Set<EventInfo> postProcess(final Collection<CoreEventInfo> ceis)
          throws CalFacadeException {
    TreeSet<EventInfo> eis = new TreeSet<EventInfo>();

    for (CoreEventInfo cei: ceis) {
      eis.add(postProcess(cei));
    }

    implantEntities(eis);

    return eis;
  }

  private void setDefaultAlarms(final EventInfo ei,
                                final BwCalendar col) throws CalFacadeException {
    BwEvent event = ei.getEvent();

    boolean isEvent = event.getEntityType() == IcalDefs.entityTypeEvent;
    boolean isTask = event.getEntityType() == IcalDefs.entityTypeTodo;

    if (!isEvent && !isTask) {
      return;
    }

    /* This test was wrong - we need to test the alarm for compatability with
     * the task/event
     */
//    if (isTask && (event.getNoStart())) {
//      return;
//    }

    boolean isDate = event.getDtstart().getDateType();

    String al = getDefaultAlarmDef(col, isEvent, isDate);

    if (al == null) {
      // Get the user home and try that
      al = getDefaultAlarmDef(getCols().getHome(),
                              isEvent, isDate);
    }

    if ((al == null) || (al.length() == 0)) {
      return;
    }

    Set<BwAlarm> alarms = compileAlarms(al);

    if (alarms == null) {
      return;
    }

    for (BwAlarm alarm: alarms) {
      /* XXX At this point we should test to see if this alarm can be added -
       * e.g. we should not add an alarm triggered off start to a task with no
       * start
       */
      alarm.addXproperty(new BwXproperty(BwXproperty.appleDefaultAlarm,
                                         null, "TRUE"));
      event.addAlarm(alarm);

      ei.getChangeset(getPrincipalHref()).addValue(Component.VALARM, alarm);
    }
  }

  private String getDefaultAlarmDef(final BwCalendar col,
                                    final boolean isEvent,
                                    final boolean isDate) {
    if (col == null) {
      return null;
    }

    QName pname;

    if (isEvent) {
      if (isDate) {
        pname = CaldavTags.defaultAlarmVeventDate;
      } else {
        pname = CaldavTags.defaultAlarmVeventDatetime;
      }
    } else {
      if (isDate) {
        pname = CaldavTags.defaultAlarmVtodoDate;
      } else {
        pname = CaldavTags.defaultAlarmVtodoDatetime;
      }
    }

    return col.getProperty(NamespaceAbbrevs.prefixed(pname));
  }

  private static final String ValidateAlarmPrefix =
      "BEGIN:VCALENDAR\n" +
      "VERSION:2.0\n" +
      "PRODID:bedework-validate\n" +
      "BEGIN:VEVENT\n" +
      "DTSTART:20101231T230000\n" +
      "DTEND:20110101T010000\n" +
      "SUMMARY:Just checking\n" +
      "UID:1234\n" +
      "DTSTAMP:20101125T112600\n";

  private static final String ValidateAlarmSuffix =
      "END:VEVENT\n" +
      "END:VCALENDAR\n";

  /** Compile an alarm component
   *
   * @param val
   * @return alarms or null
   * @throws CalFacadeException
   */
  public Set<BwAlarm> compileAlarms(final String val) throws CalFacadeException {
    try {
      StringReader sr = new StringReader(ValidateAlarmPrefix +
                                         val +
                                         ValidateAlarmSuffix);
      IcalTranslator trans = new IcalTranslator(getSvc().getIcalCallback());
      Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.getEventInfo() == null)) {
        if (debug) {
          trace("Not single event");
        }

        return null;
      }

      /* There should be alarms in the Calendar object
       */
      EventInfo ei = ic.getEventInfo();
      BwEvent ev = ei.getEvent();

      Set<BwAlarm> alarms = ev.getAlarms();

      if (Util.isEmpty(alarms)) {
        return null;
      }

      return alarms;
    } catch (CalFacadeException cfe) {
      if (debug) {
        error(cfe);
      }

      return null;
    }
  }

}
