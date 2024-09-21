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
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.UpdateEventResult;
import org.bedework.caldav.util.filter.BooleanFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.Attendee;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwParticipant;
import org.bedework.calfacade.BwParticipants;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.EventListEntry;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.configs.AuthProperties;
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
import org.bedework.calfacade.responses.InstancesResponse;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EnsureEntityExistsResult;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calfacade.svc.RealiasResult;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.EventProperties;
import org.bedework.calsvci.EventsI;
import org.bedework.convert.IcalTranslator;
import org.bedework.convert.Icalendar;
import org.bedework.convert.RecurUtil;
import org.bedework.convert.RecurUtil.RecurPeriods;
import org.bedework.convert.RecurUtil.Recurrence;
import org.bedework.sysevents.events.EntityFetchEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.Response;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.property.ParticipantType;

import java.io.StringReader;
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
import static org.bedework.calsvci.EventsI.SetEntityCategoriesResult.success;
import static org.bedework.util.misc.response.Response.Status.failed;
import static org.bedework.util.misc.response.Response.Status.forbidden;
import static org.bedework.util.misc.response.Response.Status.limitExceeded;
import static org.bedework.util.misc.response.Response.Status.noAccess;
import static org.bedework.util.misc.response.Response.fromResponse;
import static org.bedework.util.misc.response.Response.notOk;

/** This class handles fetching and updates of events.
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
          {
    final Collection<EventInfo> res =
            postProcess(getCal().getEvent(colPath,
                                          guid));

    final int num = res.size();

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
      throw new RuntimeException("cannot return rid for multiple events");
    }

    final Collection<EventInfo> eis = new ArrayList<>();

    eis.add(makeInstance(res.iterator().next(),
                         recurrenceId));

    return eis;
  }

  private Collection<EventInfo> processExpanded(final Collection<EventInfo> events,
                                                final RecurringRetrievalMode recurRetrieval) {
    final Collection<EventInfo> res = new ArrayList<>();

    for (final EventInfo ei: events) {
      final BwEvent ev = ei.getEvent();

      if (!ev.getRecurring()) {
        res.add(ei);
        continue;
      }

      final var ca = ei.getCurrentAccess();

      final var authPars = getSvc().getAuthProperties();

      final Collection<Recurrence> instances =
              RecurUtil.getRecurrences(ei,
                                       authPars.getMaxYears(),
                                       authPars.getMaxInstances(),
                                       recurRetrieval.getStartDate(),
                                       recurRetrieval.getEndDate());

      if (instances.isEmpty()) {
        return res;
      }

      for (final Recurrence instance: instances) {
        final EventInfo oei;

        if (instance.override != null) {
          oei = instance.override;
        } else {
          oei = EventInfo.makeProxy(ev,
                                    instance.start,
                                    instance.end,
                                    instance.recurrenceId,
                                    true);  // Call it an override
        }

        oei.setCurrentAccess(ca);
        oei.setRetrievedEvent(ei);

        res.add(oei);
      }
    }

    return res;
  }

  private EventInfo makeInstance(final EventInfo ei,
                                 final String recurrenceId) {
    final BwEvent ev = ei.getEvent();

    if (!ev.getRecurring()) {
      return ei;
    }

    final Recurrence instance = findInstance(ei, recurrenceId);
    if (instance == null) {
      return null;
    }

    final EventInfo oei;

    if (instance.override != null) {
      oei = instance.override;
    } else {
      oei = EventInfo.makeProxy(ev,
                                instance.start,
                                instance.end,
                                recurrenceId,
                                true);  // Call it an override
    }

    oei.setCurrentAccess(ei.getCurrentAccess());
    oei.setRetrievedEvent(ei);

    return oei;
  }

  private Recurrence findInstance(final EventInfo ei,
                                  final String rid) {
    final AuthProperties props =
            getSvc().getAuthProperties();
    final int maxYears = props.getMaxYears();
    final int maxInstances = props.getMaxInstances();

    final Collection<Recurrence> instances =
            RecurUtil.getRecurrences(ei, maxYears, maxInstances,
                                     null, null);

    if (instances.isEmpty()) {
      // No instances for an alleged recurring event.
      return null;
    }

    // See if the recurrenceid is in the instances.
    for (final Recurrence rec: instances) {
      if (rid.equals(rec.recurrenceId)) {
        return rec;
      }
    }

    return null;
  }

  @Override
  public EventInfo get(final String colPath,
                       final String name) {
    return get(colPath, name, null);
  }

  @Override
  public EventInfo get(final String colPath,
                       final String name,
                       final String recurrenceId)
          {
    String href = Util.buildPath(false,
                                 colPath, "/",
                                 name);
    if (recurrenceId != null) {
      href += "#" + recurrenceId;
    }
    final EventInfo res = postProcess(getCal().getEvent(href));

    int num = 0;

    if (res != null) {
      num = 1;
    }
    getSvc().postNotification(
            new EntityFetchEvent(SysCode.ENTITY_FETCHED, num));

    return res;
/*    if (res == null) {
      return null;
    }

    if (recurrenceId == null) {
      return res;
    }

    return makeInstance(res, recurrenceId);*/
  }

  @Override
  public EventInfo get(final BwCalendar col,
                       final String name,
                       final String recurrenceId,
                       final List<String> retrieveList)
          {
    if ((col == null) || (name == null)) {
      throw new RuntimeException(CalFacadeException.badRequest);
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
        throw new RuntimeException("Failed to parse " +
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
      if (evs.isEmpty()) {
        return null;
      }

      if (evs.size() == 1) {
        return evs.iterator().next();
      }

      throw new RuntimeException("Multiple results");
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
  public Collection<EventInfo> getEvents(
          final BwCalendar cal, final FilterBase filter,
          final BwDateTime startDate, final BwDateTime endDate,
          final List<BwIcalPropertyInfoEntry> retrieveList,
          final DeletedState delState,
          final RecurringRetrievalMode recurRetrieval)
          {
    Collection<BwCalendar> cals = null;

    if (cal != null) {
      cals = Collections.singleton(cal);
    }

    final Collection<EventInfo> res =
            getMatching(cals, filter, startDate, endDate,
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
  public Response delete(final EventInfo ei,
                         final boolean sendSchedulingMessage) {
    return delete(ei, false, sendSchedulingMessage);
  }

  @Override
  public Response delete(final EventInfo ei,
                         final boolean scheduling,
                         final boolean sendSchedulingMessage) {
    return delete(ei, scheduling, sendSchedulingMessage, false);
  }

  @Override
  public UpdateResult add(final EventInfo ei,
                          final boolean noInvites,
                          final boolean schedulingInbox,
                          final boolean autoCreateCollection,
                          final boolean rollbackOnError) {
    final UpdateResult updResult = ei.getUpdResult();

    try {
      if (getSvc().getPrincipalInfo().getSubscriptionsOnly()) {
        return notOk(updResult, noAccess,
                              "User has read only access");
      }
      
      updResult.adding = true;
      updResult.hasChanged = true;

      final BwEvent event = ei.getEvent();

      adjustEntities(ei);

      final BwPreferences prefs = getPrefs();
      if (prefs != null) {
        final GetEntitiesResponse<BwCategory> resp =
                getSvc().getCategoriesHandler().
                getByUids(prefs.getDefaultCategoryUids());

        if (resp.isOk()) {
          for (final BwCategory cat : resp.getEntities()) {
            event.addCategory(cat);
          }
        } else {
          return fromResponse(updResult, resp);
        }
      }

      final RealiasResult raResp = reAlias(event);
      if (!raResp.isOk()) {
        return fromResponse(updResult, raResp);
      }

      event.assignGuid(getSvc().getSystemProperties()
                               .getSystemid()); // Or just validate?

      if (!updateEntities(updResult, event)) {
        return updResult;
      }

      BwCalendar cal = validate(event, true, schedulingInbox, 
                                autoCreateCollection);
      if (cal == null) {
        throw new CalFacadeException("No calendar for event");
      }

      if (!cal.isSupportedComponent(event.getEntityType())) {
        return notOk(updResult, noAccess,
                     "Invalid component type for this collection");
      }

      final BwEventProxy proxy;
      final BwEvent override;

      if (event instanceof BwEventProxy) {
        proxy = (BwEventProxy)event;
        override = proxy.getRef();
        getSvc().setupSharableEntity(override, getPrincipal().getPrincipalRef());
      } else {
        getSvc().setupSharableEntity(event, getPrincipal().getPrincipalRef());

        if (ei.getNumContainedItems() > 0) {
          for (final EventInfo aei: ei.getContainedItems()) {
            final BwEvent av = aei.getEvent();
            av.setParent(event);

            getSvc().setupSharableEntity(av,
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
        return notOk(updResult, noAccess);
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
      final var parts = event.getParticipants();
      final var atts = parts.getAttendees();

      if ((maxAttendees != null) &&
              !Util.isEmpty(atts) &&
              (atts.size() > maxAttendees)) {
        return notOk(updResult, limitExceeded,
                              CalFacadeException.schedulingTooManyAttendees);
      }

      final var currentTimestamp = getCurrentTimestamp();

      event.setDtstamps(currentTimestamp);
      if (schedulingObject) {
        event.updateStag(currentTimestamp);
      }

      /* All Overrides go in same calendar and have same name */

      final Collection<BwEventProxy> overrides = ei.getOverrideProxies();
      if (overrides != null) {
        for (final BwEventProxy ovev: overrides) {
          setScheduleState(ovev, true, schedulingInbox);

          final var ovParts = ovev.getParticipants();
          final var ovAtts = ovParts.getAttendees();

          if ((maxAttendees != null) &&
                  !Util.isEmpty(ovAtts) &&
                  (ovAtts.size() > maxAttendees)) {
            return notOk(updResult, limitExceeded,
                                  CalFacadeException.schedulingTooManyAttendees);
          }

          ovev.setDtstamps(currentTimestamp);

          if (cal.getCollectionInfo().scheduling &&
              (ovev.getOrganizerSchedulingObject() ||
               ovev.getAttendeeSchedulingObject())) {
            schedulingObject = true;
          }

          if (ovev.getOrganizerSchedulingObject()) {
            // Set RSVP on all attendees with PARTSTAT = NEEDS_ACTION
            for (final Attendee att: ovAtts) {
              if (att.getParticipationStatus().equals(IcalDefs.partstatValNeedsAction)) {
                att.setExpectReply(true);
              }
            }
          }

          if (schedulingObject) {
            ovev.updateStag(currentTimestamp);
          }

          final BwEventAnnotation ann = ovev.getRef();
          ann.setColPath(event.getColPath());
          ann.setName(event.getName());
        }
      }

      if (event.getOrganizerSchedulingObject()) {
        // Set RSVP on all attendees with PARTSTAT = NEEDS_ACTION
        for (final Attendee att: atts) {
          if (att.getParticipationStatus().equals(IcalDefs.partstatValNeedsAction)) {
            att.setExpectReply(true);
          }
        }
      }

      final UpdateEventResult uer = getCal().addEvent(ei,
                                                schedulingInbox,
                                                rollbackOnError);

      if (uer.errorCode != null) {
        return notOk(updResult, failed,
                              "Status " + uer.errorCode + " from addEvent");
      }

      if (ei.getNumContainedItems() > 0) {
        for (final EventInfo oei: ei.getContainedItems()) {
          oei.getEvent().setName(event.getName());
          final UpdateEventResult auer =
                  getCal().addEvent(oei,
                                    schedulingInbox, rollbackOnError);
          if (auer.errorCode != null) {
            return notOk(updResult, failed,
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

          if (debug()) {
            debug("schedule event");
          }

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
                             final boolean noInvites) {
    return update(ei, noInvites, null, false, true,
                  false); // autocreate
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             final String fromAttUri,
                             final boolean autoCreateCollection) {
    return update(ei, noInvites, fromAttUri, false, false,
                  autoCreateCollection);
  }

  @Override
  public UpdateResult update(final EventInfo ei,
                             final boolean noInvites,
                             final String fromAttUri,
                             final boolean alwaysWrite,
                             final boolean clientUpdate,
                             final boolean autoCreateCollection) {
    final UpdateResult updResult = ei.getUpdResult();

    try {
      final BwEvent event = ei.getEvent();

      if (!updateEntities(updResult, event)) {
        return updResult;
      }

      final BwCalendar cal = validate(event, false, false,
                                      autoCreateCollection);
      if (cal == null) {
        throw new CalFacadeException("No calendar for event");
      }

      adjustEntities(ei);

      final RealiasResult raResp = reAlias(event);
      if (!raResp.isOk()) {
        return fromResponse(updResult, raResp);
      }

      boolean organizerSchedulingObject = false;
      boolean attendeeSchedulingObject = false;

      if (cal.getCollectionInfo().scheduling) {
        organizerSchedulingObject = event.getOrganizerSchedulingObject();
        attendeeSchedulingObject = event.getAttendeeSchedulingObject();
      }

      boolean schedulingObject = organizerSchedulingObject ||
                                 attendeeSchedulingObject;

      if (schedulingObject &&
              clientUpdate &&
              !event.getSignificantChange()) {
        /* We need to figure out if this is a significant change that
           requires we notify others.
         */
        final ChangeTable ct = event.getChangeset(getPrincipalHref());

        if (ct != null) {
          event.setSignificantChange(ct.getSignificantChange());
        }
      }

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

      updResult.deletedInstances = uer.deleted;

      updResult.fromAttUri = fromAttUri;

      if (noInvites || !schedulingObject || !event.getSignificantChange()) {
        if (debug() && !noInvites && schedulingObject) {
          debug("Skipping a scheduling object with insignificant changes?");
        }
        return updResult;
      }

      if (organizerSchedulingObject) {
        // Set RSVP on all attendees with PARTSTAT = NEEDS_ACTION
        for (final Attendee att: event.getParticipants()
                                      .getAttendees()) {
          if (att.getParticipationStatus().equals(
                  IcalDefs.partstatValNeedsAction)) {
            att.setExpectReply(true);
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
           * If it turns out the schedule status is not getting persisted in the
           * calendar entry then we need to find a way to set just that value in
           * already persisted entity.
           */
      }

      /* This stuff would be skipped by th eabove test.
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
                               final boolean attendeeSchedulingObject) {
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
  public void markDeleted(final BwEvent event) {
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

    final BwEventProxy proxy =
            BwEventProxy.makeAnnotation(event, event.getOwnerHref(),
                                        false);

    // Where does the ref go? Not in the same calendar - we have no access

    final BwCalendar cal = getCal()
            .getSpecialCalendar(null, getPrincipal(),
                                BwCalendar.calTypeDeleted,
                                true, PrivilegeDefs.privRead).cal;
    proxy.setOwnerHref(getPrincipal().getPrincipalRef());
    proxy.setDeleted(true);
    proxy.setColPath(cal.getPath());
    add(new EventInfo(proxy), true, false, false, false);
  }

  @Override
  public Response copyMoveNamed(final EventInfo fromEi,
                                final BwCalendar to,
                                String name,
                                final boolean copy,
                                final boolean overwrite,
                                final boolean newGuidOK) {
    final Response resp = new Response();
    final BwEvent ev = fromEi.getEvent();
    final String fromPath = ev.getColPath();

    final boolean sameCal = fromPath.equals(to.getPath());

    if (name == null) {
      name = ev.getName();
    }

    if (sameCal && name.equals(ev.getName())) {
      // No-op
      return resp;
    }

    try {
      // Get the target
      final EventInfo destEi = get(to.getPath(), name);

      if (destEi != null) {
        if (!overwrite) {
          return Response.notOk(resp, forbidden,
                                "destination exists: " + name);
        }

        if (!destEi.getEvent().getUid().equals(ev.getUid())) {
          // Not allowed to change uid.
          return Response.notOk(resp, forbidden, "Cannot change uid");
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

          getCal().moveEvent(fromEi, from, to);

          getCal().touchCalendar(from);
        } else {
          // Just changing name
          ev.setName(name);
        }

        ev.updateStag(getCurrentTimestamp());
        update(fromEi, false, null,
               false); // autocreate
      } else {
        // Copying the event.

        final BwEvent newEvent = (BwEvent)ev.clone();
        newEvent.setName(name);

        // WebDAV ACL say's new event must not carry over access
        newEvent.setAccess(null);

        final EventInfo newEi = new EventInfo(newEvent);

        if (fromEi.getOverrideProxies() != null) {
          for (final BwEventProxy proxy: fromEi.getOverrideProxies()) {
            newEi.addOverride(new EventInfo(proxy.clone(newEvent, newEvent)));
          }
        }

        if (sameCal && newGuidOK) {
          // Assign a new guid
          newEvent.setUid(null);
          newEvent.assignGuid(getSvc().getSystemProperties()
                                      .getSystemid());
        }

        if (destEi != null) {
          delete(destEi, false);
        }

        newEvent.setColPath(to.getPath());
        newEvent.updateStag(getCurrentTimestamp());

        add(newEi, true, false, false, true);
      }

      if (destEi != null) {
        return resp;
      }

      resp.setMessage("created");
      return resp;
    } catch (final CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.duplicateGuid)) {
        return Response.notOk(resp, forbidden, "duplicate uid");
      }

      return Response.error(resp, cfe);
    }
  }

  @Override
  public void claim(final BwEvent ev) {
    ev.setOwnerHref(null);
    ev.setCreatorHref(null);
    getSvc().setupSharableEntity(ev,
                                 getPrincipal().getPrincipalRef());
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

      final var authPars = getSvc().getAuthProperties();
      final RecurPeriods rp =
              RecurUtil.getPeriods(ev,
                                   authPars.getMaxYears(),
                                   authPars.getMaxInstances(),
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
          {
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

  @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
            fromResponse(updResult, eeer);
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
        fromResponse(updResult, eeers);
        return false;
      }

      if (eeers.isAdded()) {
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
        fromResponse(updResult, eeerl);
        return false;
      }

      if (eeerl.isAdded()) {
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
  Collection<EventInfo> getMatching(
          final Collection<BwCalendar> cals,
          final FilterBase filter,
          final BwDateTime startDate, final BwDateTime endDate,
          final List<BwIcalPropertyInfoEntry> retrieveList,
          final DeletedState delState,
          final RecurringRetrievalMode recurRetrieval,
          final boolean freeBusy) {
    final TreeSet<EventInfo> ts = new TreeSet<>();

    if ((filter != null) && (filter.equals(BooleanFilter.falseFilter))) {
      return ts;
    }

    Collection<BwCalendar> calSet = null;

    if (cals != null) {
      /* Turn the calendar reference into a set of calendar collections
       */
      calSet = new ArrayList<>();

      for (final BwCalendar cal:cals) {
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
                    final String entityName) {
    // This should do a cheap test of access - not retrieve the entire event
    return getSvc().getEventsHandler().get(col,
                                           entityName,
                                           null,
                                           null) != null;
  }
  
  Set<EventInfo> getSynchEvents(final String path,
                                final String lastmod) {
    return postProcess(getCal().getSynchEvents(path, lastmod));
  }

  Response delete(final EventInfo ei,
                  final boolean scheduling,
                  final boolean sendSchedulingMessage,
                  final boolean reallyDelete) {
    final Response resp = new Response();

    if (ei == null) {
      return Response.invalid(resp, "Null event");
    }

    final BwEvent event = ei.getEvent();

    /* Note we don't just return immediately if this is a no-op because of
     * tombstoning. We go through the actions to allow access checks to take place.
     */

    if (!event.getTombstoned()) {
      // Handle some scheduling stuff.

      final BwCalendar cal;
      try {
        cal = getCols().get(event.getColPath());
      } catch (final CalFacadeException cfe) {
        return Response.error(resp, cfe);
      }

      boolean schedulingObject = false;
      boolean organizerSchedulingObject = false;

      setScheduleState(event, false, false);
      if (cal.getCollectionInfo().scheduling &&
              (event.getOrganizerSchedulingObject() ||
                       event.getAttendeeSchedulingObject())) {
        schedulingObject = true;
        organizerSchedulingObject =
                event.getOrganizerSchedulingObject();
      }

      if (!schedulingObject && event.isRecurringEntity() &&
              !Util.isEmpty(ei.getOverrideProxies())) {
        for (final BwEventProxy ove: ei.getOverrideProxies()) {
          setScheduleState(ove, false, false);

          if (cal.getCollectionInfo().scheduling &&
                  (ove.getOrganizerSchedulingObject() ||
                           ove.getAttendeeSchedulingObject())) {
            schedulingObject = true;
            organizerSchedulingObject =
                    ove.getOrganizerSchedulingObject();
            break;
          }
        }
      }

      if (sendSchedulingMessage &&
          schedulingObject) {
        // Should we also only do this if it affects freebusy?

        /* According to CalDAV we're supposed to do this before we delete the
         * event. If it fails we now have no way to record that.
         *
         * However that also requires a way to forcibly delete it so we need to
         * ensure we have that first. (Just don't set sendSchedulingMessage
         */
        try {
          final SchedulingIntf sched = (SchedulingIntf)getSvc().getScheduler();
          if (!organizerSchedulingObject) {
            /* Send a declined message to the organizer
             */
            sched.sendReply(ei,
                            IcalDefs.partstatDeclined, null);
          } else {
            // send a cancel
            final UpdateResult uer = ei.getUpdResult();
            uer.deleting = true;

            event.setSequence(event.getSequence() + 1);
            sched.implicitSchedule(ei, false);
          }
        } catch (final CalFacadeException cfe) {
          if (debug()) {
            error(cfe);
          }
        }
      }
    }

    try {
      if (!getCal().deleteEvent(ei,
                                scheduling,
                                reallyDelete).eventDeleted) {
        getSvc().rollbackTransaction();
        // Assume not found?
        return Response.notFound(resp);
      }

      if (event.getEntityType() != IcalDefs.entityTypeVavailability) {
        return resp;
      }

      for (final EventInfo aei: ei.getContainedItems()) {
        if (!getCal().deleteEvent(aei,
                                  scheduling,
                                  true).eventDeleted) {
          getSvc().rollbackTransaction();
          // Assume not found?
          return Response.notFound(resp);
        }
      }

      return resp;
    } catch (final CalFacadeException cfe) {
      return Response.error(resp, cfe);
    }
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  /** Ensure that all referenced are the persistent versions.
   *
   * @param event in question
   */
  private void adjustEntities(final EventInfo event) {
  }

  private void buildCalendarSet(final Collection<BwCalendar> cals,
                                BwCalendar calendar,
                                final boolean freeBusy) {
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
      final BwCalendar saveColl = calendar;
      getCols().resolveAlias(calendar, true, freeBusy);

      while (calendar.getInternalAlias()) {
        calendar = calendar.getAliasTarget();

        if (calendar == null) {
          // No access presumably
          saveColl.setLastRefreshStatus(HttpServletResponse.SC_FORBIDDEN +
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

    for (final BwCalendar c: getCols().getChildren(calendar)) {
      buildCalendarSet(cals, c, freeBusy);
    }
  }

  private BwCalendar validate(final BwEvent ev,
                              final boolean adding,
                              final boolean schedulingInbox,
                              final boolean autoCreateCollection) {
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

    if ((ev.getEntityType() != IcalDefs.entityTypeFreeAndBusy) &&
            (ev.getSummary() == null)) {
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
      final int calType = switch (entityType) {
        case Component.VEVENT -> BwCalendar.calTypeCalendarCollection;
        case Component.VTODO -> BwCalendar.calTypeTasks;
        case Component.VPOLL -> BwCalendar.calTypePoll;
        default -> throw new CalFacadeException(
                CalFacadeException.noEventCalendar);
      };

      final GetSpecialCalendarResult gscr =
              getCal().getSpecialCalendar(null, getPrincipal(), calType,
                                          true,
                                          PrivilegeDefs.privAny);

      if (gscr.cal == null) {
        throw new CalFacadeException(CalFacadeException.noEventCalendar);
      }

      col = gscr.cal;
    }

    final Preferences prefs;

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
    final String s1 = val.replaceAll("(\\r\\n|\\r)", "\n");

    boolean changed = !s1.equals(val);

    // Get rid of all controls except cr and LF
    final String s2 =
            s1.replaceAll("[\u0000-\t\u000B-\u000C\u000E-\u0019\u007F]+",
                          "");

    if (!s2.equals(s1)) {
      changed = true;
    }

    if (!changed) {
      return null;
    }

    // Put the escaped form back again
    //return s2.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
    return s2;
  }

  /* Flag this as an attendee scheduling object or an organizer scheduling object
   */
  private void setScheduleState(final BwEvent ev,
                                final boolean adding,
                                final boolean schedulingInbox) {
    ev.setOrganizerSchedulingObject(false);
    ev.setAttendeeSchedulingObject(false);

    if ((ev.getEntityType() != IcalDefs.entityTypeEvent) &&
        (ev.getEntityType() != IcalDefs.entityTypeTodo) &&
        (ev.getEntityType() != IcalDefs.entityTypeVpoll)) {
      // Not a possible scheduling entity
      return;
    }

    final BwOrganizer org = ev.getOrganizer();

    final var parts = ev.getParticipants();
    final Set<Attendee> atts = parts.getAttendees();

    if (Util.isEmpty(atts) || (org == null)) {
      return;
    }

    final String curPrincipal = getSvc().getPrincipal().getPrincipalRef();
    final Directories dirs = getSvc().getDirectories();
    final var curCalAddr = dirs.principalToCaladdr(getPrincipal());

    /* Check organizer property to see if it is us.
     */
    AccessPrincipal evPrincipal =
      dirs.caladdrToPrincipal(org.getOrganizerUri());

    final var weAreOrganizer = (evPrincipal != null) &&
        (evPrincipal.getPrincipalRef().equals(curPrincipal));

    if (!weAreOrganizer) {
      /* Check the attendees and see if there is one for us.
          If so this is an attendee-scheduling-object.
       */
      if (parts.findAttendee(curCalAddr) != null) {
        ev.setAttendeeSchedulingObject(true);
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

    /* If this is a vpoll we need the voters as we are going to
           have to remove the group voter entry and clone it for the
           attendees we add.

           I think this will work for any poll mode - if not we may
           have to rethink this approach.
         */
    Map<String, BwParticipant> voters = null;
    final var vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;
    final BwParticipants parts;

    if (vpoll) {
      voters = ev.getParticipants().getVoters();
      parts = ev.getParticipants();
    } else {
      parts = null;
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

      final BwParticipant groupVoter;

      if (vpoll) {
        groupVoter = voters.get(att.getAttendeeUri());

        if (groupVoter == null) {
          if (debug()) {
            warn("No voter found for " + att.getAttendeeUri());
          }
          continue;
        }

        // Participant may have more than one role.
        final var types = groupVoter.getParticipantTypes();
        if (types.size() == 1) {
          // just delete
          parts.removeParticipant(groupVoter);
        } else {
          groupVoter.removeParticipantType(ParticipantType.VALUE_VOTER);
        }
      }

      ev.removeAttendee(att); // Remove the group

      chg.changed(PropertyInfoIndex.ATTENDEE, att, null);

      for (final BwPrincipalInfo mbrPi: pi.getMembers()) {
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
          parts.makeParticipant(mbrAtt);
        }
      }
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

  private EventInfo postProcess(final CoreEventInfo cei) {
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
      for (final CoreEventInfo ccei: cei.getContainedItems()) {
        final BwEvent cv = ccei.getEvent();

        ei.addContainedItem(new EventInfo(cv));
      }
    }

    ei.setCurrentAccess(cei.getCurrentAccess());

    return ei;
  }

  private Set<EventInfo> postProcess(final Collection<CoreEventInfo> ceis) {
    final TreeSet<EventInfo> eis = new TreeSet<>();

    for (final CoreEventInfo cei: ceis) {
      eis.add(postProcess(cei));
    }

    return eis;
  }

  private void setDefaultAlarms(final EventInfo ei,
                                final BwCalendar col) {
    final BwEvent event = ei.getEvent();

    final boolean isEvent = event.getEntityType() == IcalDefs.entityTypeEvent;
    final boolean isTask = event.getEntityType() == IcalDefs.entityTypeTodo;

    if (!isEvent && !isTask) {
      return;
    }

    /* This test was wrong - we need to test the alarm for compatability with
     * the task/event
     */
//    if (isTask && (event.getNoStart())) {
//      return;
//    }

    final boolean isDate = event.getDtstart().getDateType();

    String al = getDefaultAlarmDef(col, isEvent, isDate);

    if (al == null) {
      // Get the user home and try that
      al = getDefaultAlarmDef(getCols().getHome(),
                              isEvent, isDate);
    }

    if ((al == null) || (al.isEmpty())) {
      return;
    }

    final Set<BwAlarm> alarms = compileAlarms(al);

    if (alarms == null) {
      return;
    }

    for (final BwAlarm alarm: alarms) {
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

    final QName pname;

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
          """
                  BEGIN:VCALENDAR
                  VERSION:2.0
                  PRODID:bedework-validate
                  BEGIN:VEVENT
                  DTSTART:20101231T230000
                  DTEND:20110101T010000
                  SUMMARY:Just checking
                  UID:1234
                  DTSTAMP:20101125T112600
                  """;

  private static final String ValidateAlarmSuffix =
          """
                  END:VEVENT
                  END:VCALENDAR
                  """;

  /** Compile an alarm component
   *
   * @param val VALARM as a string
   * @return alarms or null
   */
  public Set<BwAlarm> compileAlarms(final String val) {
    try {
      final StringReader sr = new StringReader(ValidateAlarmPrefix +
                                         val +
                                         ValidateAlarmSuffix);
      final IcalTranslator trans = new IcalTranslator(getSvc().getIcalCallback());
      final Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.getEventInfo() == null)) {
        if (debug()) {
          debug("Not single event");
        }

        return null;
      }

      /* There should be alarms in the Calendar object
       */
      final EventInfo ei = ic.getEventInfo();
      final BwEvent ev = ei.getEvent();

      final Set<BwAlarm> alarms = ev.getAlarms();

      if (Util.isEmpty(alarms)) {
        return null;
      }

      return alarms;
    } catch (final CalFacadeException cfe) {
      if (debug()) {
        error(cfe);
      }

      return null;
    }
  }

}
