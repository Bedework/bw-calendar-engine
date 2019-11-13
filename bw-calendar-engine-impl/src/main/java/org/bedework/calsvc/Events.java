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

import org.bedework.access.AccessPrincipal;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.UpdateEventResult;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDuration;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.EventListEntry;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.filter.RetrieveList;
import org.bedework.calfacade.filter.SfpTokenizer;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.calfacade.requests.GetInstancesRequest;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.InstancesResponse;
import org.bedework.calfacade.responses.Response;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.EventProperties;
import org.bedework.calsvci.EventProperties.EnsureEntityExistsResult;
import org.bedework.calsvci.EventsI;
import org.bedework.icalendar.IcalTranslator;
import org.bedework.icalendar.IcalUtil;
import org.bedework.icalendar.Icalendar;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.icalendar.RecurUtil.Recurrence;
import org.bedework.sysevents.events.EntityFetchEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VVoter;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Voter;

import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import static org.bedework.calcorei.CoreCalendarsI.GetSpecialCalendarResult;
import static org.bedework.calfacade.responses.Response.Status.failed;
import static org.bedework.calfacade.responses.Response.Status.limitExceeded;
import static org.bedework.calfacade.responses.Response.Status.noAccess;
import static org.bedework.calsvci.EventsI.SetEntityCategoriesResult.success;

/** This acts as an interface to the database for subscriptions.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Events extends CalSvcDb implements EventsI {
  Events(final CalSvc svci) {
    super(svci);
  }

  @Override
  public Collection<EventInfo> getByUid(final String colPath,
                                        final String guid,
                                        final String recurrenceId,
                                        final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    Collection<EventInfo> res = postProcess(getCal().getEvent(colPath,
                                                              guid));

    int num = 0;

    if (res != null) {
      num = res.size();
    }

    if (num == 0) {
      return res;
    }

    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    if ((recurrenceId == null) &&
            ((recurRetrieval == null) ||
            (recurRetrieval.mode != Rmode.expanded))) {
      return res;
    }

    /* For an expansion replace the result with a set of expansions
     */
    if (recurrenceId == null) {
      return processExpanded(res, recurRetrieval);
    }

    if (num > 1) {
      throw new CalFacadeException("cannot return rid for multiple events");
    }

    final Collection<EventInfo> eis = new ArrayList<>();

    final EventInfo ei = makeInstance(res.iterator().next(), recurrenceId);

    if (ei != null) {
      eis.add(ei);
    }
    return eis;
  }

  private Collection<EventInfo> processExpanded(final Collection<EventInfo> events,
                                                final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    Collection<EventInfo> res = new ArrayList<>();

    for (EventInfo ei: events) {
      BwEvent ev = ei.getEvent();

      if (!ev.getRecurring()) {
        res.add(ei);
        continue;
      }

      CurrentAccess ca = ei.getCurrentAccess();
      Set<EventInfo> oveis = ei.getOverrides();

      if (!Util.isEmpty(oveis)) {
        for (EventInfo oei: oveis) {
          if (oei.getEvent().inDateTimeRange(recurRetrieval.start.getDate(),
                                             recurRetrieval.end.getDate())) {
            oei.setRetrievedEvent(ei);
            res.add(oei);
          }
        }
      }

      /* Generate non-overridden instances. */
      final Collection<Recurrence> instances =
              RecurUtil.getRecurrences(ei,
                                       getAuthpars().getMaxYears(),
                                       getAuthpars().getMaxInstances(),
                                       recurRetrieval.getStartDate(),
                                       recurRetrieval.getEndDate());

      if (instances == null) {
        return res;
      }

      for (final Recurrence rec: instances) {
        if (rec.override != null) {
          continue;
        }

        final BwEventAnnotation ann = new BwEventAnnotation();

        ann.setDtstart(rec.start);
        ann.setDtend(rec.end);
        ann.setRecurrenceId(rec.recurrenceId);
        ann.setOwnerHref(ev.getOwnerHref());
        ann.setOverride(true);  // Call it an override
        ann.setTombstoned(false);
        ann.setName(ev.getName());
        ann.setUid(ev.getUid());
        ann.setTarget(ev);
        ann.setMaster(ev);
        final BwEvent proxy = new BwEventProxy(ann);
        final EventInfo oei = new EventInfo(proxy);
        oei.setCurrentAccess(ei.getCurrentAccess());
        oei.setRetrievedEvent(ei);

        res.add(oei);
      }
    }

    return res;
  }

  private EventInfo makeInstance(final EventInfo ei,
                                 final String recurrenceId)
          throws CalFacadeException {
    final BwEvent ev = ei.getEvent();

    if (!ev.getRecurring()) {
      return ei;
    }

    /* See if it's in the overrides */

    if (!Util.isEmpty(ei.getOverrides())) {
      for (final EventInfo oei: ei.getOverrides()) {
        if (oei.getEvent().getRecurrenceId().equals(recurrenceId)) {
          oei.setRetrievedEvent(ei);
          oei.setCurrentAccess(ei.getCurrentAccess());
          return oei;
        }
      }
    }

    /* Not in the overrides - generate an instance */
    final BwDateTime rstart;
    final boolean dateOnly = ev.getDtstart().getDateType();

    if (dateOnly) {
      rstart = BwDateTime.makeBwDateTime(true,
                                         recurrenceId.substring(0, 8),
                                         null);
    } else {
      final String stzid = ev.getDtstart().getTzid();

      DateTime dt = null;
      try {
        dt = new DateTime(recurrenceId);
      } catch (final ParseException pe) {
        throw new CalFacadeException(pe);
      }
      final DtStart ds = ev.getDtstart().makeDtStart();
      dt.setTimeZone(ds.getTimeZone());

      rstart = BwDateTime.makeBwDateTime(dt);
    }

    final BwDateTime rend = rstart.addDuration(
            BwDuration.makeDuration(ev.getDuration()));

    final BwEventAnnotation ann = new BwEventAnnotation();

    ann.setDtstart(rstart);
    ann.setDtend(rend);
    ann.setRecurrenceId(recurrenceId);
    ann.setOwnerHref(ev.getOwnerHref());
    ann.setOverride(true);  // Call it an override
    ann.setTombstoned(false);
    ann.setName(ev.getName());
    ann.setUid(ev.getUid());
    ann.setTarget(ev);
    ann.setMaster(ev);
    BwEvent proxy = new BwEventProxy(ann);
    EventInfo oei = new EventInfo(proxy);
    oei.setCurrentAccess(ei.getCurrentAccess());

    oei.setRetrievedEvent(ei);
    return oei;
  }

  @Override
  public EventInfo get(final String colPath,
                       final String name) throws CalFacadeException {
    return get(colPath, name, null);
  }

  @Override
  public EventInfo get(final String colPath,
                       final String name,
                       final String recurrenceId)
          throws CalFacadeException {
    final EventInfo res =
            postProcess(getCal().getEvent(Util.buildPath(false,
                                                         colPath, "/",
                                          name)));

    int num = 0;

    if (res != null) {
      num = 1;
    }
    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    if (res == null) {
      return null;
    }

    if (recurrenceId == null) {
      return res;
    }

    return makeInstance(res, recurrenceId);
  }

  @Override
  public EventInfo get(final BwCalendar col,
                       final String name,
                       final String recurrenceId,
                       final List<String> retrieveList)
          throws CalFacadeException {
    if ((col == null) || (name == null)) {
      throw new CalFacadeException(CalFacadeException.badRequest);
    }


    if (col.getInternalAlias()) {
      final String expr = 
              "(vpath='" +
                      SfpTokenizer.escapeQuotes(col.getPath()) + 
                      "') and (name='" +
                      SfpTokenizer.escapeQuotes(name) + 
                      "')";

      final SimpleFilterParser sfp = getSvc().getFilterParser();
      final ParseResult pr = sfp.parse(expr, true, null);
      if (!pr.ok) {
        throw new CalFacadeException("Failed to parse " +
                                           expr + ": message was " + 
                                             pr.message);
      }

      final Collection<EventInfo> evs =
              getEvents(null, pr.filter,
                        null,  // start
                        null,  // end
                        RetrieveList.getRetrieveList(retrieveList),
                        DeletedState.noDeleted,
                        RecurringRetrievalMode.overrides);
      if (evs.size() == 0) {
        return null;
      }

      if (evs.size() == 1) {
        return evs.iterator().next();
      }

      throw new CalFacadeException("Multiple results");
    }

    String path = col.getPath();

      /* If the collection is an event list collection we need to change the
       * path part to be the path of the actual event
       */

    if (col.getCalType() == BwCalendar.calTypeEventList) {
        /* Find the event in the list using the name */
      final SortedSet<EventListEntry> eles =
              col.getEventList();

      findHref: {
        for (final EventListEntry ele: eles) {
          if (ele.getName().equals(name)) {
            path = ele.getPath();
            break findHref;
          }
        }

        return null; // Not in list
      } // findHref
    }

    return get(path, name, null);
  }

  @Override
  public Collection<EventInfo> getEvents(final BwCalendar cal, final FilterBase filter,
                                         final BwDateTime startDate, final BwDateTime endDate,
                                         final List<BwIcalPropertyInfoEntry> retrieveList,
                                         final DeletedState delState,
                                         final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    Collection<BwCalendar> cals = null;

    if (cal != null) {
      cals = Collections.singleton(cal);
    }

    Collection<EventInfo> res =  getMatching(cals, filter, startDate, endDate,
                                             retrieveList,
                                             delState,
                                             recurRetrieval, false);

    int num = 0;

    if (res != null) {
      num = res.size();
    }
    getSvc().postNotification(new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    return res;
  }

  @Override
  public boolean delete(final EventInfo ei,
                        final boolean sendSchedulingMessage) throws CalFacadeException {
    return delete(ei, false, sendSchedulingMessage);
  }

  @Override
  public UpdateResult add(final EventInfo ei,
                          final boolean noInvites,
                          final boolean schedulingInbox,
                          final boolean autoCreateCollection,
                          final boolean rollbackOnError) {
    final UpdateResult updResult = ei.getUpdResult();

    try {
      if (getPrincipalInfo().getSubscriptionsOnly()) {
        return Response.notOk(updResult, noAccess,
                              "User has read only access");
      }
      
      updResult.adding = true;
      updResult.hasChanged = true;

      final BwEvent event = ei.getEvent();

      adjustEntities(ei);

      final BwPreferences prefs = getSvc().getPrefsHandler().get();
      if (prefs != null) {
        final GetEntitiesResponse<BwCategory> resp =
                getSvc().getCategoriesHandler().
                getByUids(prefs.getDefaultCategoryUids());

        if (resp.isOk()) {
          for (final BwCategory cat : resp.getEntities()) {
            event.addCategory(cat);
          }
        } else {
          return Response.fromResponse(updResult, resp);
        }
      }

      final RealiasResult raResp = reAlias(event);
      if (!raResp.isOk()) {
        return Response.fromResponse(updResult, raResp);
      }

      assignGuid(event); // Or just validate?

      if (!updateEntities(updResult, event)) {
        return updResult;
      }

      BwCalendar cal = validate(event, true, schedulingInbox, 
                                autoCreateCollection);

      BwEventProxy proxy = null;
      BwEvent override = null;

      if (event instanceof BwEventProxy) {
        proxy = (BwEventProxy)event;
        override = proxy.getRef();
        setupSharableEntity(override, getPrincipal().getPrincipalRef());
      } else {
        setupSharableEntity(event, getPrincipal().getPrincipalRef());

        if (ei.getNumContainedItems() > 0) {
          for (final EventInfo aei: ei.getContainedItems()) {
            final BwEvent av = aei.getEvent();
            av.setParent(event);

            setupSharableEntity(av,
                                getPrincipal().getPrincipalRef());
          }
        }
      }

      final BwCalendar undereffedCal = cal;

      if (cal.getInternalAlias()) {
        /* Resolve the alias and put the event in it's proper place */

        //XXX This is probably OK for non-public admin
        final boolean setCats = getSvc().getPars().getPublicAdmin();

        if (!setCats) {
          cal = getCols().resolveAlias(cal, true, false);
        } else {
          while (true) {
            final Set<BwCategory> cats = cal.getCategories();

            for (final BwCategory cat: cats) {
              event.addCategory(cat);
            }

            if (!cal.getInternalAlias()) {
              break;
            }

            cal = getCols().resolveAlias(cal, false, false);
          }
        }

        event.setColPath(cal.getPath());
      }

      if (!cal.getCalendarCollection()) {
        return Response.notOk(updResult, noAccess, null);
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

      final Integer maxAttendees =
              getSvc().getAuthProperties().getMaxAttendeesPerInstance();

      if ((maxAttendees != null) &&
              !Util.isEmpty(event.getAttendees()) &&
              (event.getAttendees().size() > maxAttendees)) {
        return Response.notOk(updResult, limitExceeded,
                              CalFacadeException.schedulingTooManyAttendees);
      }

      var currentTimestamp = getCurrentTimestamp();

      event.setDtstamps(currentTimestamp);
      if (schedulingObject) {
        event.updateStag(currentTimestamp);
      }

      /* All Overrides go in same calendar and have same name */

      final Collection<BwEventProxy> overrides = ei.getOverrideProxies();
      if (overrides != null) {
        for (final BwEventProxy ovei: overrides) {
          setScheduleState(ovei, true, schedulingInbox);

          if ((maxAttendees != null) &&
                  !Util.isEmpty(ovei.getAttendees()) &&
                  (ovei.getAttendees().size() > maxAttendees)) {
            return Response.notOk(updResult, limitExceeded,
                                  CalFacadeException.schedulingTooManyAttendees);
          }

          ovei.setDtstamps(currentTimestamp);

          if (cal.getCollectionInfo().scheduling &&
              (ovei.getOrganizerSchedulingObject() ||
               ovei.getAttendeeSchedulingObject())) {
            schedulingObject = true;
          }

          if (schedulingObject) {
            ovei.updateStag(currentTimestamp);
          }

          final BwEventAnnotation ann = ovei.getRef();
          ann.setColPath(event.getColPath());
          ann.setName(event.getName());
        }
      }

      if (event.getOrganizerSchedulingObject()) {
        // Set RSVP on all attendees with PARTSTAT = NEEDS_ACTION
        for (final BwAttendee att: event.getAttendees()) {
          if (att.getPartstat().equals(IcalDefs.partstatValNeedsAction)) {
            att.setRsvp(true);
          }
        }
      }

      UpdateEventResult uer = getCal().addEvent(ei,
                                                schedulingInbox,
                                                rollbackOnError);

      if (ei.getNumContainedItems() > 0) {
        for (final EventInfo oei: ei.getContainedItems()) {
          oei.getEvent().setName(event.getName());
          final UpdateEventResult auer =
                  getCal().addEvent(oei,
                                    schedulingInbox, rollbackOnError);
          if (auer.errorCode != null) {
            return Response.notOk(updResult, failed,
                                  "Status " + auer.errorCode + " from addEvent");
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
          final SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

          sched.implicitSchedule(ei,
                                 false /*noInvites*/);

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
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      getSvc().rollbackTransaction();
      reindex(ei);

      return Response.error(updResult, t);
    }
  }

  @Override
  public void reindex(final EventInfo ei) {
    try {
      getCal().reindex(ei);
    } catch(final Throwable t) {
      error(t);
    }
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites) throws CalFacadeException {
    return update(ei, noInvites, null, false);
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             final String fromAttUri) throws CalFacadeException {
    return update(ei, noInvites, fromAttUri, false);
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             final String fromAttUri,
                             final boolean alwaysWrite) {
    final UpdateResult updResult = ei.getUpdResult();

    try {
      final BwEvent event = ei.getEvent();

      if (!updateEntities(updResult, event)) {
        return updResult;
      }

      final BwCalendar cal = validate(event, false, false, false);
      adjustEntities(ei);

      final RealiasResult raResp = reAlias(event);
      if (!raResp.isOk()) {
        return Response.fromResponse(updResult, raResp);
      }

      boolean organizerSchedulingObject = false;
      boolean attendeeSchedulingObject = false;

      if (cal.getCollectionInfo().scheduling) {
        organizerSchedulingObject = event.getOrganizerSchedulingObject();
        attendeeSchedulingObject = event.getAttendeeSchedulingObject();
      }

      boolean schedulingObject = organizerSchedulingObject ||
                                 attendeeSchedulingObject;

      if (event.getSignificantChange() && schedulingObject) {
        event.updateStag(getCurrentTimestamp());
      }

      boolean changed = alwaysWrite ||
              checkChanges(ei,
                           organizerSchedulingObject,
                           attendeeSchedulingObject) ||
              ei.getOverridesChanged();
      boolean sequenceChange = ei.getUpdResult().sequenceChange;

      /* TODO - this is wrong.
         At the very least we should only reschedule the override that changed.
         However adding an override looks like a change for all the fields
         copied in. There should only be a change if the value is different
       */
      boolean doReschedule = ei.getUpdResult().doReschedule;

      if (ei.getNumOverrides() > 0) {
        for (final EventInfo oei: ei.getOverrides()) {
          setScheduleState(oei.getEvent(), false, false);

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
            if (oei.getUpdResult().sequenceChange) {
              sequenceChange = true;
            }
          }

          if (schedulingObject) {
            oei.getEvent().updateStag(getCurrentTimestamp());
          }

          doReschedule = doReschedule || oei.getUpdResult().doReschedule;
        }
      }

      if (!changed) {
        if (debug()) {
          debug("No changes to event: returning");
        }
        return ei.getUpdResult();
      }

      event.setDtstamps(getCurrentTimestamp());
      /* TODO - fix this */
//      if (doReschedule) {
  //      getSvc().getScheduler().setupReschedule(ei);
    //  }
      
      if (organizerSchedulingObject && sequenceChange) {
        event.setSequence(event.getSequence() + 1);
      }

      final UpdateEventResult uer = getCal().updateEvent(ei);

      updResult.addedInstances = uer.added;
      updResult.updatedInstances = uer.updated;
      updResult.deletedInstances = uer.deleted;

      updResult.fromAttUri = fromAttUri;

      if (!noInvites && schedulingObject) {
        if (organizerSchedulingObject) {
          // Set RSVP on all attendees with PARTSTAT = NEEDS_ACTION
          for (final BwAttendee att: event.getAttendees()) {
            if (att.getPartstat().equals(IcalDefs.partstatValNeedsAction)) {
              att.setRsvp(true);
            }
          }
        }

        boolean sendit = organizerSchedulingObject || updResult.reply;

        if (!sendit) {
          if (!Util.isEmpty(ei.getOverrides())) {
            for (final EventInfo oei: ei.getOverrides()) {
              if (oei.getUpdResult().reply) {
                sendit = true;
                break;
              }
            }
          }
        }

        if (sendit) {
          final SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();

          sched.implicitSchedule(ei,
                                 false /*noInvites */);

          /* We assume we don't need to update again to set attendee status
           * Trying to do an update results in duplicate key errors.
           *
           * If it turns out the scgedule status is not getting persisted in the
           * calendar entry then we need to find a way to set just that value in
           * already persisted entity.
           */
        }
      }

      /*
      final boolean vpoll = event.getEntityType() == IcalDefs.entityTypeVpoll;

      if (vpoll && (updResult.pollWinner != null)) {
        // Add the winner and send it out
        final Map<Integer, Component> comps =
                IcalUtil.parseVpollCandidates(event);

        final Component comp = comps.get(updResult.pollWinner);

        if (comp != null) {
          final IcalTranslator trans =
                  new IcalTranslator(getSvc().getIcalCallback());
          final String colPath = getSvc().getCalendarsHandler().getPreferred(
                  comp.getName());
          final BwCalendar col = getSvc().getCalendarsHandler().get(colPath);
          final Icalendar ical = trans.fromComp(col, comp, true, true);

          add(ical.getEventInfo(), false, false, true, true);
        }
      } */

      return updResult;
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      getSvc().rollbackTransaction();
      reindex(ei);

      return Response.error(updResult, t);
    }
  }

  @SuppressWarnings("unchecked")
  private boolean checkChanges(final EventInfo ei,
                               final boolean organizerSchedulingObject,
                               final boolean attendeeSchedulingObject) throws CalFacadeException {
    final UpdateResult updResult = ei.getUpdResult();

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

    if (debug()) {
      ei.getChangeset(getPrincipalHref()).dumpEntries();
    }

    final ChangeTable ct = ei.getChangeset(getPrincipalHref());
    final Collection<ChangeTableEntry> ctes = ct.getEntries();

    updResult.sequenceChange = ct.getSequenceChangeNeeded();

    for (final ChangeTableEntry cte: ctes) {
      if (!cte.getChanged()) {
        continue;
      }

      updResult.hasChanged = true;
      final PropertyInfoIndex pi = cte.getIndex();

      if (!organizerSchedulingObject &&
          pi.equals(PropertyInfoIndex.ORGANIZER)) {
        final BwOrganizer oldOrg = (BwOrganizer)cte.getOldVal();
        final BwOrganizer newOrg = (BwOrganizer)cte.getNewVal();
        
        if ((oldOrg == null) ||
                (newOrg == null) ||
                !oldOrg.getOrganizerUri().equals(newOrg.getOrganizerUri())) {
          // Never valid
          throw new CalFacadeForbidden(CaldavTags.attendeeAllowed,
                                       "Cannot change organizer");
        }
      }

      if (pi.equals(PropertyInfoIndex.ATTENDEE) ||
              pi.equals(PropertyInfoIndex.VOTER)) {
        updResult.addedAttendees = cte.getAddedValues();
        updResult.deletedAttendees = cte.getRemovedValues();

        if (attendeeSchedulingObject) {
          // Attendee replying?
          /* XXX We should really check to see if the value changed here -
           */
          updResult.reply = true;
        } else {
          if (!Util.isEmpty(updResult.deletedAttendees)) {
            // Bump sequence as we are sending out cancels
            updResult.sequenceChange = true;
          }
        }
      }

      if (pi.equals(PropertyInfoIndex.POLL_WINNER)) {
        if (!attendeeSchedulingObject) {
          // Attendee replying?
          /* XXX We should really check to see if the value changed here -
           */
          updResult.pollWinner = ei.getEvent().getPollWinner();
        }
      }

      if (pi.equals(PropertyInfoIndex.POLL_ITEM)) {
        if (attendeeSchedulingObject) {
          // Attendee replying?
          /* XXX We should really check to see if the value changed here -
           */
          updResult.reply = true;
        }
      }

      if (organizerSchedulingObject) {
        final BwIcalPropertyInfoEntry pie =
                BwIcalPropertyInfo.getPinfo(cte.getIndex());
        if (pie.getReschedule()) {
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

    BwCalendar cal = getCal().getSpecialCalendar(null, getPrincipal(),
                                     BwCalendar.calTypeDeleted,
                                     true, PrivilegeDefs.privRead).cal;
    proxy.setOwnerHref(getPrincipal().getPrincipalRef());
    proxy.setDeleted(true);
    proxy.setColPath(cal.getPath());
    add(new EventInfo(proxy), true, false, false, false);
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
      final EventInfo destEi = get(to.getPath(), name);

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

          final BwCalendar from = getCols().get(fromPath);

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

        final BwEvent newEvent = (BwEvent)ev.clone();
        newEvent.setName(name);

        // WebDAV ACL say's new event must not carry over access
        newEvent.setAccess(null);

        final EventInfo newEi = new EventInfo(newEvent);

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

        add(newEi, true, false, false, true);
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

  @Override
  public RealiasResult reAlias(final BwEvent ev) {
    /* The set of categories referenced by the aliases and their parents */
    final RealiasResult resp = new RealiasResult(new TreeSet<>());

    final Collection<BwXproperty> aliases = ev.getXproperties(BwXproperty.bedeworkAlias);

    if (!Util.isEmpty(aliases)) {
      for (final BwXproperty alias : aliases) {
        doCats(resp, alias.getValue(), ev);
      }
    }

    doCats(resp, ev.getColPath(), ev);

    return resp;
  }

  @Override
  public InstancesResponse getInstances(final GetInstancesRequest req) {
    final InstancesResponse resp = new InstancesResponse();

    resp.setId(req.getId());

    if (!req.validate(resp)) {
      return resp;
    }

    // Use a BwEvent to build the instance set

    final BwEvent ev = new BwEventObj();

    try {
      final BwDateTime st = req.getStartDt();
      ev.setDtstart(st);
      ev.setDtend(req.getEndDt());
      ev.addRrule(req.getRrule());

      if (!Util.isEmpty(req.getExdates())) {
        for (final String dt: req.getExdates()) {
          ev.addExdate(BwDateTime.makeBwDateTime(st.getDateType(),
                                                 dt,
                                                 st.getTzid()));
        }
      }

      if (!Util.isEmpty(req.getRdates())) {
        for (final String dt: req.getRdates()) {
          ev.addRdate(BwDateTime.makeBwDateTime(st.getDateType(),
                                                 dt,
                                                 st.getTzid()));
        }
      }

      final RecurPeriods rp =
              RecurUtil.getPeriods(ev,
                                   getAuthpars().getMaxYears(),
                                   getAuthpars().getMaxInstances(),
                                   req.getBegin(),
                                   req.getEnd());

      resp.setInstances(rp.instances);

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  private void doCats(final RealiasResult resp,
                      final String colHref,
                      final BwEvent ev) {
    try {
      final Set<BwCategory> cats = getCols()
              .getCategorySet(colHref);

      if (Util.isEmpty(cats)) {
        return;
      }

      resp.getCats().addAll(cats);

      for (final BwCategory cat : cats) {
        if (ev.addCategory(cat)) {
          final ChangeTable changes = ev.getChangeset(getPrincipalHref());

          final ChangeTableEntry cte = changes
                  .getEntry(PropertyInfoIndex.CATEGORIES);


          cte.addAddedValue(cat);
        }
      }
    } catch (final Throwable t) {
      Response.error(new RealiasResult(null),
                     t.getMessage());
    }
  }

  @Override
  public SetEntityCategoriesResult setEntityCategories(final CategorisedEntity ent,
                                                       final Set<BwCategory> extraCats,
                                                       final Set<String> defCatUids,
                                                       final Set<String> allDefCatUids,
                                                       final Collection<String> strCatUids,
                                                       final ChangeTable changes) 
          throws CalFacadeException {
    // XXX We should use the change table code for this.
    final SetEntityCategoriesResult secr = new SetEntityCategoriesResult();

    /* categories already set in event */
    final Set<BwCategory> entcats = ent.getCategories();
    final Map<String, BwCategory> entcatMap = new HashMap<>();
    if (!Util.isEmpty(entcats)) {
      for (final BwCategory entcat: entcats) {
        entcatMap.put(entcat.getUid(), entcat);
      }
    }

    /* Remove all categories if we don't supply any
     */

    if (Util.isEmpty(strCatUids) &&
            Util.isEmpty(extraCats) &&
            Util.isEmpty(defCatUids) &&
            Util.isEmpty(allDefCatUids)) {
      if (!Util.isEmpty(entcats)) {
        if (changes != null) {
          final ChangeTableEntry cte = changes.getEntry(PropertyInfoIndex.CATEGORIES);
          cte.setRemovedValues(new ArrayList<>(entcats));
        }

        secr.numRemoved = entcats.size();
        entcats.clear();
      }
      secr.rcode = success;
      return secr;
    }

    final Set<BwCategory> cats = new TreeSet<>();

    if (extraCats != null) {
      cats.addAll(extraCats);
    }

    if (!Util.isEmpty(defCatUids)) {
      for (final String uid: defCatUids) {
        final BwCategory cat = getSvc().getCategoriesHandler().getPersistent(uid);

        if (cat != null) {
          cats.add(cat);
        }
      }
    }

    if (!Util.isEmpty(allDefCatUids) &&
            (entcats != null)) {
      for (final String catUid: allDefCatUids) {
        /* If it's in the event add it to the list we're building then move on
         * to the next requested category.
         */
        final BwCategory entcat = entcatMap.get(catUid);
        if (entcat != null) {
          cats.add(entcat);
        }
      }
    }

    if (!Util.isEmpty(strCatUids)) {
      buildList:
      for (final String catUid: strCatUids) {
        /* If it's in the event add it to the list we're building then move on
         * to the next requested category.
         */
        final BwCategory entcat = entcatMap.get(catUid);
        if (entcat != null) {
          cats.add(entcat);
          continue buildList;
        }

        final BwCategory cat = getSvc().getCategoriesHandler().getPersistent(catUid);

        if (cat != null) {
          cats.add(cat);
        }
      }
    }
    
    /* cats now contains category objects corresponding to the parameters
     *
     * Now we need to add or remove any in the event but not in our list.
     */

    /* First make a list to remove - to avoid concurrent update
     * problems with the iterator
     */

    final ArrayList<BwCategory> toRemove = new ArrayList<>();

    if (entcats != null) {
      for (final BwCategory evcat: entcats) {
        if (cats.contains(evcat)) {
          cats.remove(evcat);
          continue;
        }

        toRemove.add(evcat);
      }
    }

    for (final BwCategory cat: cats) {
      ent.addCategory(cat);
      secr.numAdded++;
    }

    for (final BwCategory cat: toRemove) {
      if (entcats.remove(cat)) {
        secr.numRemoved++;
      }
    }

    if ((changes != null)  &&
            (secr.numAdded > 0) && (secr.numRemoved > 0)) {
      final ChangeTableEntry cte = changes.getEntry(PropertyInfoIndex.CATEGORIES);
      cte.setRemovedValues(toRemove);
      cte.setAddedValues(cats);
    }

    secr.rcode = success;

    return secr;
  }

  /* ====================================================================
   *                   Package private methods
   * ==================================================================== */

  boolean updateEntities(final UpdateResult updResult,
                         final BwEvent event) {
    final EventProperties<BwCategory> cathdlr = getSvc().getCategoriesHandler();

    final Set<BwCategory> cats = event.getCategories();
    final Set<BwCategory> removeCats = new TreeSet<>();
    final Set<BwCategory> addCats = new TreeSet<>();

    if (cats != null) {
      for (final BwCategory cat : cats) {
        if (cat.unsaved()) {
          final EnsureEntityExistsResult<BwCategory> eeer =
                  cathdlr.ensureExists(cat, event.getOwnerHref());

          removeCats.add(cat);
          if (!eeer.isOk()) {
            Response.fromResponse(updResult, eeer);
            return false;
          }

          addCats.add(eeer.getEntity());
        }
      }

      for (final BwCategory cat : removeCats) {
        event.removeCategory(cat);
      }

      for (final BwCategory cat : addCats) {
        event.addCategory(cat);
      }
    }

    final BwContact ct = event.getContact();

    if (ct != null) {
      final EnsureEntityExistsResult<BwContact> eeers =
        getSvc().getContactsHandler().ensureExists(ct,
                                                   ct.getOwnerHref());

      if (!eeers.isOk()) {
        Response.fromResponse(updResult, eeers);
        return false;
      }

      if (eeers.added) {
        updResult.contactsAdded++;
      }

      // XXX only do this if we know it changed
      event.setContact(eeers.getEntity());
    }

    final BwLocation loc = event.getLocation();

    if (loc != null) {
      final EnsureEntityExistsResult<BwLocation> eeerl =
              getSvc().getLocationsHandler().ensureExists(loc,
                                                          loc.getOwnerHref());

      if (!eeerl.isOk()) {
        Response.fromResponse(updResult, eeerl);
        return false;
      }

      if (eeerl.added) {
        updResult.locationsAdded++;
      }

      // XXX only do this if we know it changed
      event.setLocation(eeerl.getEntity());
    }

    return true;
  }

  /** Method which allows us to flag this as a scheduling action
   * Return the events for the current user within the given date and time
   * range. If retrieveList is supplied only those fields (and a few required
   * fields) will be returned.
   *
   * @param cals         BwCalendar objects - non-null means limit to
   *                     given calendar set
   *                     null is limit to current user
   * @param filter       BwFilter object restricting search or null.
   * @param startDate    BwDateTime start - may be null
   * @param endDate      BwDateTime end - may be null.
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @param recurRetrieval How recurring event is returned.
   * @param freeBusy      true for a freebusy request
   * @return Collection  populated matching event value objects
   * @throws CalFacadeException on fatal error
   */
  Collection<EventInfo> getMatching(final Collection<BwCalendar> cals,
                                    final FilterBase filter,
                                    final BwDateTime startDate, final BwDateTime endDate,
                                    final List<BwIcalPropertyInfoEntry> retrieveList,
                                    final DeletedState delState,
                                    final RecurringRetrievalMode recurRetrieval,
                                    final boolean freeBusy) throws CalFacadeException {
    TreeSet<EventInfo> ts = new TreeSet<>();

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
                          delState,
                          recurRetrieval, freeBusy)));

    return ts;
  }
  
  boolean isVisible(final BwCalendar col,
                    final String entityName) throws CalFacadeException {
    // This should do a cheap test of access - not retrieve the entire event
    return getEvent(col, entityName, null) != null;
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
            UpdateResult uer = ei.getUpdResult();
            uer.deleting = true;

            event.setSequence(event.getSequence() + 1);
            sched.implicitSchedule(ei, false);
          }
        } catch (CalFacadeException cfe) {
          if (debug()) {
            error(cfe);
          }
        }
      }
    }

    if (!getCal().deleteEvent(ei,
                              scheduling,
                              reallyDelete).eventDeleted) {
      getSvc().rollbackTransaction();
      return false;
    }

    if (event.getEntityType() != IcalDefs.entityTypeVavailability) {
      return true;
    }

    for (EventInfo aei: ei.getContainedItems()) {
      if (!getCal().deleteEvent(aei,
                                scheduling,
                                true).eventDeleted) {
        getSvc().rollbackTransaction();
        return false;
      }
    }

    return true;
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  /** Ensure that all referenced are the persistent versions.
   *
   * @param event
   * @throws CalFacadeException
   */
  private void adjustEntities(final EventInfo event) throws CalFacadeException {
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

  private BwCalendar validate(final BwEvent ev,
                              final boolean adding,
                              final boolean schedulingInbox,
                              final boolean autoCreateCollection) throws CalFacadeException {
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

    if (ev.getSummary() == null) {
      throw new CalFacadeException(CalFacadeException.missingEventProperty,
                                   "summary");
    }

    String checkedString = checkString(ev.getSummary());
    if (checkedString != null) {
      ev.setSummary(checkedString);
    }

    checkedString = checkString(ev.getDescription());
    if (checkedString != null) {
      ev.setDescription(checkedString);
    }

    setScheduleState(ev, adding, schedulingInbox);

    BwCalendar col = getCols().get(ev.getColPath());
    
    if (col == null) {
      if (!autoCreateCollection) {
        throw new CalFacadeException(
                CalFacadeException.collectionNotFound);
      }

      // TODO - need a configurable default display name

      // TODO - this all needs a rework

      final String entityType = IcalDefs.entityTypeIcalNames[ev
              .getEntityType()];
      final int calType;

      switch (entityType) {
        case Component.VEVENT:
          calType = BwCalendar.calTypeCalendarCollection;
          break;
        case Component.VTODO:
          calType = BwCalendar.calTypeTasks;
          break;
        case Component.VPOLL:
          calType = BwCalendar.calTypePoll;
          break;
        default:
          return null;
      }

      final GetSpecialCalendarResult gscr =
              getCal().getSpecialCalendar(null, getPrincipal(), calType,
                                          true,
                                          PrivilegeDefs.privAny);

      col = gscr.cal;
    }

    Preferences prefs = null;

    if (getPars().getPublicAdmin() && !getPars().getService()) {
      prefs = (Preferences)getSvc().getPrefsHandler();

      prefs.updateAdminPrefs(false,
                             col,
                             ev.getCategories(),
                             ev.getLocation(),
                             ev.getContact());
    }
    
    return col;
  }

  /* Check for bad characters - not complete
   */
  private String checkString(final String val) {
    if (val == null) {
      return null;
    }

    // Normalize the line endings
    final String s1 = val.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");

    boolean changed = !s1.equals(val);

    // Get rid of all controls except cr and LF
    final String s2 =
            s1.replaceAll("[\u0000-\u0009\u000B-\u000C\u000E-\u0019\u007F]+",
                          "");

    if (!s2.equals(s1)) {
      changed = true;
    }

    if (!changed) {
      return null;
    }

    // Put the escaped form back again
    return s2.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
  }

  /* Flag this as an attendee scheduling object or an organizer scheduling object
   */
  private void setScheduleState(final BwEvent ev,
                                final boolean adding,
                                final boolean schedulingInbox) throws CalFacadeException {
    ev.setOrganizerSchedulingObject(false);
    ev.setAttendeeSchedulingObject(false);

    if ((ev.getEntityType() != IcalDefs.entityTypeEvent) &&
        (ev.getEntityType() != IcalDefs.entityTypeTodo) &&
        (ev.getEntityType() != IcalDefs.entityTypeVpoll)) {
      // Not a possible scheduling entity
      return;
    }

    final BwOrganizer org = ev.getOrganizer();

    final Set<BwAttendee> atts = ev.getAttendees();

    if (Util.isEmpty(atts) || (org == null)) {
      return;
    }

    final String curPrincipal = getSvc().getPrincipal().getPrincipalRef();
    final Directories dirs = getSvc().getDirectories();

    /* Check organizer property to see if it is us.
     */
    AccessPrincipal evPrincipal =
      dirs.caladdrToPrincipal(org.getOrganizerUri());

    var weAreOrganizer = (evPrincipal != null) &&
        (evPrincipal.getPrincipalRef().equals(curPrincipal));

    if (!weAreOrganizer) {
      /* Go through the attendees and see if at least one is us.
          If so this is an attendee-scheduling-object.
       */
      for (final BwAttendee att: atts) {
        evPrincipal = getSvc().getDirectories().caladdrToPrincipal(att.getAttendeeUri());
        if ((evPrincipal != null) &&
                (evPrincipal.getPrincipalRef().equals(curPrincipal))) {
          ev.setAttendeeSchedulingObject(true);

          break;
        }
      }

      return;
    }

    // We are the organizer for this entity */

    ev.setOrganizerSchedulingObject(true);

    /* If we are expanding groups do so here */

    final ChangeTable chg = ev.getChangeset(getPrincipalHref());
    final Set<BwAttendee> groups = new TreeSet<>();

    if (!schedulingInbox) {
      final ChangeTableEntry cte = chg
              .getEntry(PropertyInfoIndex.ATTENDEE);

      checkAttendees:
      for (final BwAttendee att : atts) {
        if (CuType.GROUP.getValue().equals(att.getCuType())) {
          groups.add(att);
        }

        final AccessPrincipal attPrincipal =
                getSvc().getDirectories().
                        caladdrToPrincipal(att.getAttendeeUri());
        if ((attPrincipal != null) &&
                (attPrincipal.getPrincipalRef()
                             .equals(curPrincipal))) {
          // It's us
          continue checkAttendees;
        }

        if (att.getPartstat()
               .equals(IcalDefs.partstatValNeedsAction)) {
          continue checkAttendees;
        }

        if (adding) {
          // Can't add an event with attendees set to accepted
          att.setPartstat(IcalDefs.partstatValNeedsAction);
          continue checkAttendees;
        }

        // Not adding event. Did we add attendee?
        if ((cte != null) &&
                !Util.isEmpty(cte.getAddedValues())) {
          for (final Object o : cte.getAddedValues()) {
            final BwAttendee chgAtt = (BwAttendee)o;

            if (chgAtt.getCn().equals(att.getCn())) {
              att.setPartstat(IcalDefs.partstatValNeedsAction);
              continue checkAttendees;
            }
          }
        }
      } // checkAttendees
    }

    try {
      /* If this is a vpoll we need the vvoters as we are going to
           have to remove the group vvoter entry and clone it for the
           attendees we add.

           I think this will work for any poll mode - if not we may
           have to rethink this approach.
         */
      Map<String, VVoter> voters = null;
      final var vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

      if (vpoll) {
        voters = IcalUtil.parseVpollVvoters(ev);
        ev.clearVvoters(); // We'll add them all back
      }

      for (final BwAttendee att : groups) {
        /* If the group is in one of our domains we can try to expand it.
           * We should leave it if it's an external id.
           */

        final Holder<Boolean> trunc = new Holder<>();
        final List<BwPrincipalInfo> groupPis =
                dirs.find(att.getAttendeeUri(),
                          att.getCuType(),
                          true,  // expand
                          trunc);

        if ((groupPis == null) || (groupPis.size() != 1)) {
          continue;
        }

        final BwPrincipalInfo pi = groupPis.get(0);

        if (pi.getMembers() == null) {
          continue;
        }

        VVoter groupVvoter = null;
        Voter groupVoter = null;
        PropertyList pl = null;

        if (vpoll) {
          groupVvoter = voters.get(att.getAttendeeUri());

          if (groupVvoter == null) {
            if (debug()) {
              warn("No vvoter found for " + att.getAttendeeUri());
            }
            continue;
          }

          voters.remove(att.getAttendeeUri());
          groupVoter = groupVvoter.getVoter();
          pl = groupVvoter.getProperties();
        }

        ev.removeAttendee(att); // Remove the group

        chg.changed(PropertyInfoIndex.ATTENDEE, att, null);

        for (final BwPrincipalInfo mbrPi : pi.getMembers()) {
          if (mbrPi.getCaladruri() == null) {
            continue;
          }

          final BwAttendee mbrAtt = new BwAttendee();

          mbrAtt.setType(att.getType());
          mbrAtt.setAttendeeUri(mbrPi.getCaladruri());
          mbrAtt.setCn(mbrPi.getEmail());
          mbrAtt.setCuType(mbrPi.getKind());
          mbrAtt.setMember(att.getAttendeeUri());

          ev.addAttendee(mbrAtt);
          chg.addValue(PropertyInfoIndex.ATTENDEE, mbrAtt);

          if (vpoll) {
            pl.remove(groupVoter);

            groupVoter = IcalUtil.setVoter(mbrAtt);

            pl.add(groupVoter);

            ev.addVvoter(groupVvoter.toString());
          }
        }
      }

      if (vpoll) {
        // Add back any remaining vvoters
        for (VVoter vv: voters.values()) {
          ev.addVvoter(vv.toString());
        }
      }
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    if (ev instanceof BwEventProxy) {
      // Only add x-property to master
      return;
    }

    if (CalFacadeDefs.jasigSchedulingAssistant.equals(getPars().getClientId())) {
      ev.addXproperty(new BwXproperty(BwXproperty.bedeworkSchedAssist,
                                      null,
                                      "true"));
    }
  }

  private EventInfo postProcess(final CoreEventInfo cei)
          throws CalFacadeException {
    if (cei == null) {
      return null;
    }

    //debug("ev: " + ev);

    /* If the event is an event reference (an alias) implant it in an event
     * proxy and return that object.
     */
    BwEvent ev = cei.getEvent();

    if (ev instanceof BwEventAnnotation) {
      ev = new BwEventProxy((BwEventAnnotation)ev);
    }

    final Set<EventInfo> overrides = new TreeSet<>();
    if (cei.getOverrides() != null) {
      for (final CoreEventInfo ocei: cei.getOverrides()) {
        final BwEventProxy op = (BwEventProxy)ocei.getEvent();

        overrides.add(new EventInfo(op));
      }
    }

    final EventInfo ei = new EventInfo(ev, overrides);

    /* Reconstruct if any contained items. */
    if (cei.getNumContainedItems() > 0) {
      for (CoreEventInfo ccei: cei.getContainedItems()) {
        BwEvent cv = ccei.getEvent();

        ei.addContainedItem(new EventInfo(cv));
      }
    }

    ei.setCurrentAccess(cei.getCurrentAccess());

    return ei;
  }

  private Set<EventInfo> postProcess(final Collection<CoreEventInfo> ceis)
          throws CalFacadeException {
    TreeSet<EventInfo> eis = new TreeSet<>();

    for (CoreEventInfo cei: ceis) {
      eis.add(postProcess(cei));
    }

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

      ei.getChangeset(getPrincipalHref()).addValue(PropertyInfoIndex.VALARM, alarm);
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
        if (debug()) {
          debug("Not single event");
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
      if (debug()) {
        error(cfe);
      }

      return null;
    }
  }

}
