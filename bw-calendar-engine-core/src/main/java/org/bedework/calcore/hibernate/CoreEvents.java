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
package org.bedework.calcore.hibernate;

import org.bedework.access.CurrentAccess;
import org.bedework.calcore.common.CalintfHelper;
import org.bedework.calcore.hibernate.EventQueryBuilder.EventsQueryResult;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwRecurrenceInstance;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeDupNameException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.convert.RecurUtil;
import org.bedework.convert.RecurUtil.RecurPeriods;
import org.bedework.sysevents.events.StatsEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.TimeZone;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState;

/** Class to encapsulate most of what we do with events.
 *
 * <p>There is a lot of complication surrounding recurring events. Expanding
 * events takes time so we expand them and index the instance. To save space
 * we use a table with only the times and recurrence id and references to the
 * master and override.
 *
 * <p>The picture comes out something like below. (See BwEvent for how we
 * handle the bits that make up an overridden recurring instance). In this
 * example the master has categories A and B. One overridden instance (2)
 * has an single category P.
 *
 * <p>The same structure holds for any overridden instance. The annotation
 * object points to or contains the overridden values. For collections there
 * is a flag in the annotation which explicitly flags empty collections.
 *
 * <p>The proxy object returns a value either from the annotation or from the
 * event.
 *
 * <pre>
 *                                                     Event
 * Events  ************************                    Categories
 * Table   | 99 |       |     |   |                    table
 *         ************************                                  ...
 *           ^ ^                                       | 99 | A        |
 *           | |                                       *****************
 *           | +--------------------------+            | 99 | B        |
 *           +------------------+         |            ...
 *                              |         |
 *                              |         |            Event Annotation
 * Annotations  ***********************   |                Categories
 * Table        | 7 |       |  99 |   |   |                table
 *              ***********************   |                          ...
 *                ^                       |            | 7  | P        |
 *                +------+                |            ...
 *                       |   Mstr         |
 *  Instances ***********|********        |
 *  Table     |  1    |  |  |    |------>-+
 *            ***********|********        |
 *            |  2    |  |  |    |------>-+
 *            ********************        |
 *            |  3    |     |    |------>-+
 *            ********************
 * </pre>
 *
 * <p>Another view might help to show some of the reason for complications in
 * fetching events. Most of this complication comes about because there are at
 * least 2 modes of working<ul>
 * <li>Master + overrides only - the caldav unexpanded form</li>
 * <li>Expanded into events - the CalDAV expanded form and the web form</li>
 * </ul>
 * <p>There are some further differences but those are the main forms of fetch.
 * In addition to time ranges, which have some special considerations, we might
 * be filtering on e.g. category.
 *
 * <p>A further complication is that CalDAV REPORT has two time ranges, one
 * applied to the filter, which affects which entities are considered for
 * expansion, and a second time-range, applied to the expand element which
 * allows expansion within a time range, or applied to the limit-recurrence-set
 * element which bounds the number of instances checked for overrides.
 *
 * <p>Below, T0-T1 is the retrieval time range:
 *
 * <pre>
 *       Events            Annotations      Instances     Results
 *      |      |            |      |        |      |
 *      |------|            |      |        |      |
 *      | Mstr |<-------+   |      |        |------|
 *      |------|<----+  |   |      |  +---->|Inst 1|
 *      |      |     |  |   |      |  |     |------|
 *  T0  |......|.....|..|...|......|..|.....|      |
 *      |      |     |  |   |      |  |     |      |
 *      |      |     |  |   |      |  |     |------|
 *      |      |     |  |   |      |  |     |Inst 2|.....> Event A
 *      |      |     |  |   |      |  |     |------|
 *      |      |     |  |   |------|  |     |      |
 *      |      |     |  |   |over- |..|..................> Event B
 *      |      |     |  +---|ride 1|--+     |      |
 *      |      |     |      |------|        |------|
 *      |      |     |      |      |  +---->|Inst 3|
 *      |------|     |      |      |  |     |------|
 *      |Event1|.....|................|..................> Event C
 *      |------|     |      |      |  |     |------|
 *      |      |     |      |      |  |     |Inst 4|.....> Event D
 *      |      |     |      |      |  |     |------|
 *  T1  |......|.....|......|......|..|.....|      |
 *      |      |     |      |      |  |     |      |
 *      |      |     |      |      |  |     |      |
 *      |      |     |      |------|  |     |      |
 *      |      |     |      |over- |  |     |      |
 *      |      |     +------|ride 2|--+     |      |
 *      |      |            |------|        |      |
 *
 * </pre>
 *
 * <p>The resulting events A-D show up because:<ul>
 * <li>
 * A and D because of un-overridden instances.
 * </li>
 * <li>
 * B because of override 1.
 * </li>
 * <li>
 * C because of the non-recurring event 1.
 * </li>
 * </ul>
 *
 * <p>This is the unfiltered, expanded form. If we want the unfiltered, unexpanded
 * form we need to deliver Event 1 + the Master + the overrides.
 *
 * <p>Note that we have 2 other cases to consider, <ul>
 * <li>No instances in date range but an override within T0-T1</li>
 * <li>Instances in date range but an override taking them outside of T0-T1</li>
 * </ul>
 *
 * <p>In all cases we need to deliver the master + overrides.
 *
 * <p>In addition CalDAV allows us to restrict the result to only those overrides
 * which affect the time range either to include or exclude instances.
 *
 * @author Mike Douglass   douglm  - rpi.edu
 */
public class CoreEvents extends CalintfHelper implements CoreEventsI {
  private final CoreEventsDAO dao;
  
  /** Constructor
   *
   * @param sess persistance session
   * @param intf interface
   * @param ac access checker
   * @param guestMode true for a guest
   * @param sessionless if true
   * @throws CalFacadeException on fatal error
   */
  public CoreEvents(final HibSession sess,
                    final CalintfImpl intf,
                    final AccessChecker ac,
                    final boolean guestMode,
                    final boolean sessionless)
          throws CalFacadeException {
    dao = new CoreEventsDAO(sess);
    intf.registerDao(dao);
    super.init(intf, ac, guestMode, sessionless);
  }

  @Override
  public <T> T throwException(final CalFacadeException cfe)
          throws CalFacadeException {
    dao.rollback();
    throw cfe;
  }

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String uid)
          throws CalFacadeException {
    final TreeSet<CoreEventInfo> ts = new TreeSet<>();
    final int desiredAccess = privRead;

    /*
    if (colPath != null) {
      BwCalendar cal = getEntityCollection(colPath, privRead, scheduling, false);
      desiredAccess = ((CalendarWrapper)cal).getLastDesiredAccess();
    }
    */

    /* This works as follows:
     *
     * First try to retrieve the master event from the events table.
     *
     * If not there try the annotations table. If it's there, it's a reference
     * to an event owned by somebody else. Otherwise we drew a blank.
     *
     * If no recurrence id was specified process any recurrence information for
     * each event retrieved and return.
     *
     * Note that the event we retrieved might be a reference to a recurring
     * instance. In that case it will inherit the recurrence id. We should check
     * for this case and assume we were being asked for that event.
     *
     * If a recurrence id was specified then, for each master event retrieved,
     * we need to retrieve the instance and build a proxy using any appropriate
     * overrides.
     */

    // First look in the events table for the master(s).
    Collection evs = 
            dao.eventQuery(BwEventObj.class, colPath, uid,
                           null, null,
                           null,  // overrides
                           null); //recurRetrieval);

    /* The uid and recurrence id is a unique key for calendar collections
     * other than some special ones, Inbox and Outbox.
     *
     * These we treat specially as they also cannot be annotated etc so we
     * just return what we find.
     */

    if (Util.isEmpty(evs)) {
      /* Look for an annotation to that event by the current user.
       */
      evs = dao.eventQuery(BwEventAnnotation.class, 
                           colPath, uid, /*null*/
                           null, null,
                           false,  // overrides
                           null); //recurRetrieval);
    }

    if (Util.isEmpty(evs)) {
      return ts;
    }

    final Collection<CoreEventInfo> ceis =
            intf.postGetEvents(evs, desiredAccess,
                               returnResultAlways,
                               null);

    if (ceis.isEmpty()) {
      return ceis;
    }

    /* If the recurrence id is null, do recurrences for each retrieved event,
     * otherwise just retrieve the instance.
     */

    final EventsQueryResult eqr = new EventsQueryResult();
    eqr.flt = new Filters(intf, null);
    eqr.addColPath(colPath);

    for (final CoreEventInfo cei: ceis) {
      final BwEvent master = cei.getEvent();

      if (master.getEntityType() == IcalDefs.entityTypeVavailability) {
        for (final String auid : master.getAvailableUids()) {
          final Collection<CoreEventInfo> aceis = getEvent(colPath, auid);
          if (aceis.size() != 1) {
            throwException(CalFacadeException.badResponse);
          }

          cei.addContainedItem(aceis.iterator().next());
        }
        ts.add(cei);
      } else if (!master.testRecurring()) {
        ts.add(cei);
      } else {
        doRecurrence(cei, null);

        ts.add(cei);
      }
    }

    return ts;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CoreEventInfo getEvent(final String href)
          throws CalFacadeException {
    final PathAndName pn = new PathAndName(href);
    final List<BwEvent> evs = dao.getEventsByName(pn.colPath, pn.name);

    /* If this is availability we should have one vavailability and a number of
     * available. Otherwise just a single event.
     */

    BwEvent ev = null;
    List<BwEvent> avails = null;

    if (evs.size() == 1) {
      ev = evs.get(0);
    } else {
      for (final BwEvent lev: evs) {
        final int etype = lev.getEntityType();

        if (etype == IcalDefs.entityTypeAvailable) {
          if (avails == null) {
            avails = new ArrayList<>();
          }

          avails.add(lev);
        } else if (etype == IcalDefs.entityTypeVavailability) {
          if (ev != null) {
            throwException(new CalFacadeException(CalFacadeException.duplicateName));
            return null;
          }

          ev = lev;
        } else {
          throwException(new CalFacadeException(CalFacadeException.duplicateName));
          return null;
        }
      }
    }

    if (ev == null) {
      // Try annotation

      ev = dao.getEventsAnnotationName(pn.colPath, pn.name);
    }

    if (ev == null) {
      return null;
    }

    final CoreEventInfo cei = intf.postGetEvent(ev,
                                                privRead,
                                                returnResultAlways,
                                                null);

    if (cei != null)  {
      // Access was not denied

      if (avails != null) {
        for (final BwEvent aev: avails) {
          final CoreEventInfo acei = 
                  intf.postGetEvent(aev,
                                    privRead,
                                    returnResultAlways, null);
          if (acei == null) {
            continue;
          }

          if (aev.testRecurring()) {
            doRecurrence(acei, RecurringRetrievalMode.overrides);
          }

          cei.addContainedItem(acei);
        }
      } else {
        ev = cei.getEvent();
        if (ev.testRecurring()) {
          doRecurrence(cei, RecurringRetrievalMode.overrides);
        }
      }
    }

    return cei;
  }

  @Override
  public Collection<CoreEventInfo> getEvents(final Collection<BwCalendar> calendars,
                                             final FilterBase filter,
                                             final BwDateTime startDate,
                                             final BwDateTime endDate,
                                             final List<BwIcalPropertyInfoEntry> retrieveList,
                                             final DeletedState delState,
                                             RecurringRetrievalMode recurRetrieval,
                                             final boolean freeBusy) throws CalFacadeException {
    throw new CalFacadeException("Implemented in the interface class");
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) throws CalFacadeException {
    final BwEvent val = ei.getEvent();
    final Collection<BwEventProxy> overrides = ei.getOverrideProxies();
    final long startTime = System.currentTimeMillis();
    RecuridTable recurids = null;
    final UpdateEventResult uer = new UpdateEventResult();

    uer.addedUpdated = true;

    final BwCalendar cal = getEntityCollection(val.getColPath(), privBind,
                                               scheduling, false);

    /* Indicate if we want sharing notifications of changes */
    final boolean shared = cal.getPublick() || cal.getShared();

    final CollectionInfo collInf = cal.getCollectionInfo();

    if (!Util.isEmpty(overrides)) {
      if (!val.testRecurring()) {
        throwException(CalFacadeException.overridesForNonRecurring);
      }

      recurids = new RecuridTable(overrides);
    }

    if (val.getUid() == null) {
      throwException(CalFacadeException.noEventGuid);
    }

    if (val.getName() == null) {
      throwException(CalFacadeException.noEventName);
    }

    /* The guid must not exist in the same calendar. We assign a guid if
     * one wasn't assigned already. However, the event may have come with a guid
     * (caldav, import, etc) so we need to check here.
     *
     * It also ensures our guid allocation is working OK
     */
    if (collInf.uniqueKey) {
      String name = calendarGuidExists(val, false, true);
      if (name == null) {
        name = calendarGuidExists(val, true, true);
      }

      if (name != null) {
        throwException(CalFacadeException.duplicateGuid, name);
      }
    }

    /* Similarly for event names which must be unique within a collection.
     * Note that a duplicate name is essentially overwriting an event with a
     * new uid - also disallowed.
     */
    if ((val.getEntityType() != IcalDefs.entityTypeAvailable) &&
        (calendarNameExists(val, false, true) ||
          calendarNameExists(val, true, true))) {
      throwException(CalFacadeException.duplicateName, val.getName());
    }

    setupDependentEntities(val);

    /* Remove any tombstoned event in the collection with same uid */
    deleteTombstoned(val.getColPath(), val.getUid());

    /* If it's a recurring event see what we can do to optimize searching
     * and retrieval
     */
    if ((val instanceof BwEventAnnotation) || !val.getRecurring()) {
      dao.save(val);

      if (!getForRestore()) {
        notify(SysEvent.SysCode.ENTITY_ADDED, val, shared);
      }

      stat(StatsEvent.createTime, startTime);

      indexEntity(ei);

      return uer;
    }

    /* Get all the times for this event. - this could be a problem. Need to
       limit the number. Should we do this in chunks, stepping through the
       whole period?
     */

    final RecurPeriods rp = 
            RecurUtil.getPeriods(val, getAuthprops().getMaxYears(),
                                 getAuthprops().getMaxInstances());

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.
      if (rollbackOnError) {
        throwException(CalFacadeException.noRecurrenceInstances,
                       val.getUid());
      }

      uer.addedUpdated = false;
      uer.errorCode = CalFacadeException.noRecurrenceInstances;

      stat(StatsEvent.createTime, startTime);

      indexEntity(ei);

      return uer;
    }

    /* We can save the master at this point */
    dao.save(val);

    final String stzid = val.getDtstart().getTzid();
    final TimeZone stz = null;

/*    try {
      if (stzid != null) {
        stz = Timezones.getTz(stzid);
      }
      val.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(),
                                         stzid));
    } catch (Throwable t) {
      throwException(new CalFacadeException(t));
    } */

    int maxInstances = getAuthprops().getMaxInstances();

    final boolean dateOnly = val.getDtstart().getDateType();

    /* There appears to be a bug in ical4j in which the first instance gets
     * duplicated. Rather than change that code and run the risk of breaking
     * all recurrences I'll just look for that duplicate.
     */

    String firstRecurrenceId = null;

    for (final Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      final DateTime edt = p.getEnd();
      if (!dateOnly && (stz != null)) {
        edt.setTimeZone(stz);
      }

      dtval = edt.toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      final BwRecurrenceInstance ri = new BwRecurrenceInstance();

      ri.setDtstart(rstart);
      ri.setDtend(rend);
      ri.setRecurrenceId(ri.getDtstart().getDate());
      ri.setMaster(val);

      if (firstRecurrenceId == null) {
        firstRecurrenceId = ri.getRecurrenceId();
      } else if (firstRecurrenceId.equals(ri.getRecurrenceId())) {
        // Skip it
        if (debug()) {
          debug("Skipping duplicate recurid " + firstRecurrenceId);
        }

        continue;
      }

      if (recurids != null) {
        /* See if we have a recurrence */
        final String rid = ri.getRecurrenceId();
        final BwEventProxy ov = recurids.get(rid);

        if (ov != null) {
          if (debug()) {
            debug("Add override with recurid " + rid);
          }

          setupDependentEntities(ov);
          addOverride(ov, val, ri);
          dao.save(ri);
          recurids.remove(rid);
        }
      }

//      dao.save(ri);
      maxInstances--;
      if (maxInstances == 0) {
        // That's all you're getting from me
        break;
      }
    }

    if ((recurids != null) && (recurids.size() != 0)) {
      /* We removed all the valid overrides - we are left with those
       * with recurrence ids that don't match.
       */
      if (rollbackOnError) {
        throwException(CalFacadeException.invalidOverride);
      }

      uer.failedOverrides = recurids.values();
    }

//    sess.saveOrUpdate(val);

    if (!getForRestore()) {
      notify(SysEvent.SysCode.ENTITY_ADDED, val, shared);
    }

    indexEntity(ei);

    stat(StatsEvent.createTime, startTime);

    return uer;
  }

  @Override
  public void reindex(final EventInfo ei) {
    throw new RuntimeException("Should be handled by interface");
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) throws CalFacadeException {
    final BwEvent val = ei.getEvent();
    final Collection<BwEventProxy> overrides = ei.getOverrideProxies();
    final Collection<BwEventProxy> deletedOverrides =
            ei.getDeletedOverrideProxies(intf.getPrincipalInfo().getPrincipal().getPrincipalRef());
    final UpdateEventResult ue = new UpdateEventResult();

    if (!ac.checkAccess(val, privWrite, true).getAccessAllowed()) {
      // See if we get write content
      // XXX Is this correct?
      try {
        ac.checkAccess(val, privWriteContent, false);
      } catch (final CalFacadeException cfe) {
        throwException(cfe);
      }
    }

    BwEventProxy proxy = null;

    if (val instanceof BwEventProxy) {
      proxy = (BwEventProxy)val;
    }

    final BwCalendar col = getCollection(val.getColPath());
    final boolean shared = col.getPublick() || col.getShared();

    /* Don't allow name and uid changes for overrides */

    if ((proxy != null) && (proxy.getRef().getOverride())) {
      final BwEventAnnotation ann = proxy.getRef();
      final BwEvent mstr = ann.getMaster();

      if (!proxy.getUid().equals(mstr.getUid())) {
        throwException("org.bedework.cannot.overrideuid");
      }

      if (!proxy.getName().equals(mstr.getName())) {
        throwException("org.bedework.cannot.overridename");
      }
    } else {
      /* The guid must not exist in the same calendar. We assign a guid if
       * one wasn't assigned already. However, the event may have come with a guid
       * (caldav, import, etc) so we need to check here.
       *
       * It also ensures our guid allocation is working OK
       */
      final CollectionInfo collInf = col.getCollectionInfo();

      if (collInf.uniqueKey) {
        String name = calendarGuidExists(val, false, false);
        if (name == null) {
          name = calendarGuidExists(val, true, false);
        }

        if (name != null) {
          throwException(CalFacadeException.duplicateGuid, name);
        }
      }

      /* Similarly for event names which must be unique within a collection
       */
      if (calendarNameExists(val, false, false) ||
          calendarNameExists(val, true, false)) {
        throwException(new CalFacadeDupNameException(val.getName()));
      }
    }

    if (!(val instanceof BwEventProxy)) {
      dao.update(val);

      final Collection<BwDbentity<?>> deleted = val.getDeletedEntities();
      if (deleted != null) {
        for (final BwDbentity ent: deleted) {
          dao.delete(ent);
        }

        deleted.clear();
      }

      if (val.testRecurring()) {
        /* Check the instances and see if any changes need to be made.
         */

        if (!Util.isEmpty(overrides)) {
          for (final BwEventProxy pxy: overrides) {
            final BwEventAnnotation ann = pxy.getRef();
            boolean updated = false;

            if ((ann.getRecurring() != null) &&
                    ann.getRecurring()) {
              ann.setRecurring(false); // be safe
              updated = true;
            }

            if (ann.getTombstoned() == null) {
              ann.setTombstoned(false); // be safe
              updated = true;
            }

            if (ann.unsaved()) {
              dao.save(ann);
            } else if (updated) {
              updateProxy(new BwEventProxy(ann));
            }
          }
        }

        updateRecurrences(ei, ue, overrides, shared);
      }

      // XXX I don't think we want this updateRefs(val);

      if (!val.testRecurring() ||
          (Util.isEmpty(overrides) && Util.isEmpty(deletedOverrides))) {
        notify(SysEvent.SysCode.ENTITY_UPDATED, val, shared);

        indexEntity(ei);

        return ue;
      }

      if (!Util.isEmpty(overrides)) {
        updateOverrides:
        for (final BwEventProxy pxy: overrides) {
          final BwEventAnnotation ann = pxy.getRef();

          /* Is this a deleted instance? */

          if (ue.deleted != null) {
            for (final String rid: ue.deleted) {
              if (rid.equals(ann.getRecurrenceId())) {
                continue updateOverrides;
              }
            }
          }

          if (ue.added != null) {
            for (final String rid: ue.added) {
              if (rid.equals(ann.getRecurrenceId())) {
                continue updateOverrides;
              }
            }
          }

          ann.setRecurring(false); // be safe

          if (ann.getTombstoned() == null) {
            ann.setTombstoned(false); // be safe
          }

          if (!ann.unsaved()) {
            updateProxy(new BwEventProxy(ann));
          } else {
            dao.save(ann);

            /* See if there is an instance for this override
             */
            BwRecurrenceInstance ri =
                    dao.getInstance(val, ann.getRecurrenceId());

            if (ri == null) {
              final BwDateTime rid = 
                      BwDateTime.fromUTC(ann.getRecurrenceId().length() == 8,
                                         ann.getRecurrenceId());

              final Dur dur = new Dur(val.getDuration());
              final BwDateTime end = rid.addDur(dur);

              ri = new BwRecurrenceInstance();

              ri.setDtstart(rid);
              ri.setDtend(end);
              ri.setRecurrenceId(rid.getDate());
              ri.setMaster(val);
              ri.setOverride(ann);

              dao.save(ri);
            } else {
              ri.setOverride(ann);

              dao.update(ri);
            }
          }

          notifyInstanceChange(SysEvent.SysCode.ENTITY_UPDATED, val, shared,
                               ann.getRecurrenceId());
        }
      }

      if (!Util.isEmpty(deletedOverrides)) {
        final Collection<String> rids = new ArrayList<>();
        for (final BwEventProxy pxy: deletedOverrides) {
          rids.add(pxy.getRecurrenceId());
        }

        removeInstances(val, rids, ue, deletedOverrides, shared);
      }
    } else {
      if (proxy.getChangeFlag()) {
        updateProxy(proxy);
      }
    }

    notify(SysEvent.SysCode.ENTITY_UPDATED, val, shared);

    if (proxy != null) {
      if (ei.getRetrievedEvent() == null) {
        warn("No retrieved event for indexer");
      } else {
        final EventInfo rei = ei.getRetrievedEvent();
        rei.addOverride(ei);
        indexEntity(rei);
      }
    } else {
      indexEntity(ei);
    }

    return ue;
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) throws CalFacadeException {
    final DelEventResult der = new DelEventResult(false, 0);
    BwEvent ev = ei.getEvent();

    final boolean isMaster = ev.testRecurring() && (ev.getRecurrenceId() == null);
    final boolean isInstance = (ev.getRecurrenceId() != null) &&
            (ev instanceof BwEventProxy);

    if (!isInstance && ev.unsaved()) {
      final CoreEventInfo cei = getEvent(ev.getHref());

      if (cei == null) {
        return der;
      }

      ev = cei.getEvent();
    }

    final long startTime = System.currentTimeMillis();
    final int desiredAccess;
    final boolean shared;

    try {
      final BwCalendar col = getEntityCollection(ev.getColPath(),
                                                 privAny, scheduling, false);
      shared = col.getPublick() || col.getShared();

      if (!scheduling) {
        desiredAccess = privUnbind;
      } else {
        /* Delete message while tidying up in/outbox.
         * Set desiredAccess to something that works.
         *  */

        final CalendarWrapper cw = (CalendarWrapper)col;
        desiredAccess = cw.getLastDesiredAccess();
      }

      ac.checkAccess(ev, desiredAccess, false);
    } catch (final CalFacadeException cfe) {
      dao.rollback();
      throw cfe;
    }

    if (!reallyDelete && ev.getTombstoned()) {
      // no-op - just pretend

      der.eventDeleted = true;

      return der;
    }

    if (isMaster) {
      // Master event - delete all instances and overrides.
      deleteInstances(ev, shared);

      notifyDelete(reallyDelete, ev, shared);

      if (reallyDelete) {
        dao.delete(ev);
        unindexEntity(ei);
      } else {
        tombstoneEvent(ev);
        indexEntity(ei);
      }

      der.eventDeleted = true;

      stat(StatsEvent.deleteTime, startTime);

      return der;
    }

    if (isInstance) {
      /* Deleting a single instance. Delete any overrides, delete the instance
       * and add an exdate to the master.
       */

      final BwEventProxy proxy = (BwEventProxy)ev;
      final BwEventAnnotation ann = proxy.getRef();
      BwEvent master = ann.getMaster();

      if (master.unsaved()) {
        final CoreEventInfo cei = getEvent(master.getHref());

        if (cei == null) {
          return der;
        }

        master = cei.getEvent();
      }

      /* Fetch the instance so we can delete it */
      final BwRecurrenceInstance inst =
              dao.getInstance(master,
                              ev.getRecurrenceId());

      if (inst == null) {
        stat(StatsEvent.deleteTime, startTime);

        return der;
      }

      notifyDelete(true, ev, shared);

      dao.delete(inst);

      if (!ann.unsaved()) {
        //der.alarmsDeleted = deleteAlarms(ann);

        ann.getAttendees().clear();
        dao.delete(ann);
      }

      final BwDateTime instDate = inst.getDtstart();

      if (!master.getRdates().remove(instDate)) {
        // Wasn't an rdate event
        master.addExdate(instDate);
      }
      master.updateLastmod();
      dao.update(master);

      der.eventDeleted = true;

      stat(StatsEvent.deleteTime, startTime);

      indexEntity(ei.getRetrievedEvent());

      return der;
    }

    // Single non recurring event.

    BwEvent deletee = ev;

    if (ev instanceof BwEventProxy) {
      // Deleting an annotation
      deletee = ((BwEventProxy)ev).getRef();
    }

    // I think we need something like this -- fixReferringAnnotations(deletee);

    // XXX This could be wrong.
    /* If this is a proxy we should only delete alarmas attached to the
     * proxy - any attached to the underlying event should be left alone.
     */
    //der.alarmsDeleted = deleteAlarms(deletee);

    //sess.delete(sess.merge(deletee));

    notifyDelete(reallyDelete, ev, shared);
    if (reallyDelete) {
      clearCollection(ev.getAttendees());

      dao.delete(deletee);
      unindexEntity(ei);
    } else {
      tombstoneEvent(deletee);
      indexEntity(ei);
    }

    der.eventDeleted = true;

    stat(StatsEvent.deleteTime, startTime);

    return der;
  }

  private void unindexEntity(final EventInfo ei) throws CalFacadeException {
    final BwEvent ev = ei.getEvent();

    if (ev.getRecurrenceId() != null) {
      // Cannot index single instance
      warn("Tried to unindex a recurrence instance");
      return;
    }

    getIndexer(ev).unindexEntity(ev.getHref());
  }

  @Override
  public void moveEvent(final BwEvent val,
                        final BwCalendar from,
                        final BwCalendar to) throws CalFacadeException {
    deleteTombstoned(to.getPath(), val.getUid());

    final BwEvent tombstone = val.cloneTombstone();
    tombstone.setDtstamps(getCurrentTimestamp());

    //tombstoneEvent(tombstone);

    dao.save(tombstone);
    final EventInfo old = new EventInfo(tombstone);
    indexEntity(old);

    val.setColPath(to.getPath());
    // Don't save just yet - updates get triggered
    // TODO - this is asking for trouble if it fails

    notifyMove(SysEvent.SysCode.ENTITY_MOVED,
               tombstone.getHref(),
               from.getShared(),
               val,
               to.getShared());
  }

  /** Remove much of the data associated with the event and then tombstone it.
   *
   * @param val the event
   * @throws CalFacadeException on error
   */
  private void tombstoneEvent(final BwEvent val) throws CalFacadeException {
    tombstoneEntity(val);

    /* - do the bits not done by tombstoneEntity
     */

    /* dtstart/dtend/duration and associated stuff we leave alone */

    val.setLocation(null);

    /* name - leave this */

    clearCollection(val.getRecipients());

    clearCollection(val.getRequestStatuses());

    clearCollection(val.getXproperties());

    clearCollection(val.getFreeBusyPeriods()); // freebusy only

    clearCollection(val.getAvailableUids()); // vavailability only

    val.setDtstamps(getCurrentTimestamp());
    val.setTombstoned(true);

    dao.update(val);
  }

  private void deleteTombstoned(final String colPath,
                                final String uid) throws CalFacadeException {
    dao.deleteTombstonedEvent(fixPath(colPath), uid);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String token) throws CalFacadeException {
    if (path == null) {
      dao.rollback();
      throw new CalFacadeBadRequest("Missing path");
    }

    final String fpath = fixPath(path);

    final BwCalendar col = getCollection(fpath);
    ac.checkAccess(col, privAny, false);

    @SuppressWarnings("unchecked")
    final List<BwEvent> evs = dao.getSynchEventObjects(fpath, token);

    if (debug()) {
      debug(" ----------- number evs = " + evs.size());
    }

    final Set<CoreEventInfo> res = new TreeSet<>();

    for (final BwEvent ev: evs) {
      final CurrentAccess ca = new CurrentAccess(true);

      res.add(new CoreEventInfo(ev, ca));
    }

    return res;
  }

  /* ====================================================================
   *                  Admin support
   * ==================================================================== */

  @Override
  @SuppressWarnings("unchecked")
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) throws CalFacadeException {
    final Collection<String> res = dao.getChildrenEntities(parentPath, 
                                                           start, 
                                                           count);

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException {
    return dao.getEventAnnotations();
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) throws CalFacadeException {
    return dao.getEventOverrides(ev);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void setupDependentEntities(final BwEvent val) throws CalFacadeException {
    // Ensure collections in reasonable state.
    if (val.getAlarms() != null) {
      for (final BwAlarm alarm: val.getAlarms()) {
        alarm.setEvent(val);
        alarm.setOwnerHref(getPrincipal().getPrincipalRef());
      }
    }
  }

  /* Called by updateEvent to update a proxied event (annotation) or an
   * override.
   */
  private void updateProxy(final BwEventProxy proxy) throws CalFacadeException {
    /* if this is a proxy for a recurrence instance of our own event
       then the recurrence instance should point at this override.
       Otherwise we just update the event annotation.
     */
    final BwEventAnnotation override = proxy.getRef();
    if (debug()) {
      debug("Update override event " + override);
    }

    BwEvent mstr = override.getTarget();

    while (mstr instanceof BwEventAnnotation) {
      /* XXX The master may itself be an annotated event. We should really
         stop when we get to that point
       */
      /*
      BwEventProxy tempProxy = new BwEventProxy(mstr);
      if (some-condition-holds) {
        break;
      }
      */
      mstr = ((BwEventAnnotation)mstr).getTarget();
    }

//    if (mstr.getOwner().equals(getUser()) &&
    if (mstr.testRecurring()) {
      // A recurring event - retrieve the instance

      final BwRecurrenceInstance inst =
              dao.getInstance(mstr, 
                              override.getRecurrenceId());
      if (inst == null) {
        if (debug()) {
          debug("Cannot locate instance for " +
                   mstr + "with recurrence id " + override.getRecurrenceId());
        }
        throwException(CalFacadeException.cannotLocateInstance,
                       mstr + "with recurrence id " + override.getRecurrenceId());
        return; // satisfy intellij
      }

      override.setOwnerHref(mstr.getOwnerHref()); // XXX Force owner????
      dao.saveOrUpdate(override);
//      sess.flush();
      if (inst.getOverride() == null) {
        inst.setOverride(override);
        dao.save(inst);
      }

      /* Update the lastmod on the master event */
      mstr.setDtstamps(getCurrentTimestamp());
      dao.update(mstr);
    } else {
      dao.saveOrUpdate(override);
    }

    proxy.setChangeFlag(false);
  }

  /* Retrieves the overides for a recurring event and if required,
   * retrieves the instances.
   *
   * The overrides we retrieve are optionally limited by date.
   *
   * The CalDAV spec requires that we retrieve all overrides which fall within
   * the given date range AND all instances in that date range including
   * overriden instances that WOULD have fallen in that range had they not been
   * overriden.
   *
   * Thus we need to search both overrides and instances - unless no date range
   * is given in which case all overrides will appear along with the instances.
   *
   * If the calendars parameter is non-null, as it usually will be for a call
   * from getEvents, we limit the result to instances that appear within that
   * set of calendars. This handles the case of an overriden instance moved to a
   * different calendar, for example the trash.
   */
  @SuppressWarnings("unchecked")
  private void doRecurrence(final CoreEventInfo cei,
                            final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    final BwEvent master = cei.getEvent();
    final Set<String> overrides = new HashSet<>();
    final CurrentAccess ca = cei.getCurrentAccess();

    // Always fetch all overrides
    final Collection<BwEventAnnotation> ovs =
            dao.eventQuery(BwEventAnnotation.class, null, null, null, master,
                           true,  // overrides
                           null); //recurRetrieval);

    if (ovs != null) {
      for (final BwEventAnnotation override: ovs) {
        final CoreEventInfo ocei = makeOverrideProxy(override, ca);

        cei.addOverride(ocei);

        overrides.add(ocei.getEvent().getRecurrenceId());
      }
    }

    /* If we are asking for full expansion generate all the instances (within
     * the given date range if supplied)
     */

    if ((recurRetrieval == null) ||
        (recurRetrieval.mode != Rmode.expanded)) {
      return;
    }

    /* Create a list of all instance date/times before overrides. */

    final int maxYears;
    final int maxInstances;

    maxYears = getAuthprops().getMaxYears();
    maxInstances = getAuthprops().getMaxInstances();

    final RecurPeriods rp = RecurUtil.getPeriods(master, maxYears, maxInstances);

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.
      return;
      //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
    }

    final String stzid = master.getDtstart().getTzid();

    final boolean dateOnly = master.getDtstart().getDateType();

    /* Emit all instances that aren't overridden. */

    final TreeSet<CoreEventInfo> ceis = new TreeSet<>();

    for (final Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      if (overrides.contains(rstart.getDate())) {
        // Overrides built separately - skip this instance.
        continue;
      }

      final String recurrenceId = rstart.getDate();

      dtval = p.getEnd().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      final BwRecurrenceInstance inst = new BwRecurrenceInstance(rstart,
                                                                 rend,
                                                                 recurrenceId,
                                                                 master,
                                                                 null);

      final CoreEventInfo instcei = makeInstanceProxy(inst, ca);
      if (instcei != null) {
        //if (debug()) {
        //  debug("Ev: " + proxy);
        //}
        ceis.add(instcei);
      }
    }

    cei.setInstances(ceis);
  }

  /* XXX This needs more work, OK until we allow modification of annotations - which
   * could happen anyway through caldav or by synch.
   *
   * If the master changes then either we change the referencing annotations or
   * we let the user know it's changed. At the moment we have no notification
   * mechanism.
   * /
  private void updateRefs(BwEvent val) throws CalFacadeException {
    HibSession sess = getSess();
    Iterator it = getAnnotations(val).iterator();

    while (it.hasNext()) {
      BwEventAnnotation ann = (BwEventAnnotation)it.next();
      boolean changed = false;

      if (!val.getDtstart().equals(ann.getDtstart())) {
        ann.setDtstart(val.getDtstart());
        changed = true;
      }

      if (!val.getDtend().equals(ann.getDtend())) {
        ann.setDtend(val.getDtend());
        changed = true;
      }

      if (!val.getDuration().equals(ann.getDuration())) {
        ann.setDuration(val.getDuration());
        changed = true;
      }

      if (val.getEndType() != ann.getEndType()) {
        ann.setEndType(val.getEndType());
        changed = true;
      }

      if (changed) {
        sess.update(ann);
      }
    }
  }
  */

  /* Called when adding an event with overrides
   */
  private void addOverride(final BwEventProxy proxy,
                           final BwEvent master,
                           final BwRecurrenceInstance inst) throws CalFacadeException {
    final BwEventAnnotation override = proxy.getRef();
    if (override.getOwnerHref() == null) {
      override.setOwnerHref(master.getOwnerHref());
    }
    override.setMaster(master);
    override.setTarget(master);
    override.setOverride(true);
    override.setTombstoned(false);

    dao.saveOrUpdate(override);
    inst.setOverride(override);
  }

  /* Delete any recurrences.
   * /
  private void deleteRecurrences(BwEvent val,
                                 UpdateChanges uc,
                                 ChangeTable changes) throws CalFacadeException {
    if (changes != null) {
      if (!changes.recurrenceChanged()) {
        return;
      }
    }

    clearCollection(val.getRrules());
    clearCollection(val.getExrules());
    clearCollection(val.getRdates());
    clearCollection(val.getExdates());

    deleteInstances(val, uc, new DelEventResult(false, 0));
  }

  private void clearCollection(Collection c) {
    if (c == null) {
      return;
    }

    c.clear();
  }
*/
  private void deleteInstances(final BwEvent val,
                               final boolean shared) throws CalFacadeException {
    // First some notifications

    //noinspection unchecked
    final List<BwRecurrenceInstance> current = dao.getInstances(val);

    for (final BwRecurrenceInstance ri: current) {
      notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED,
                           val, shared,
                           ri.getRecurrenceId());
    }

    dao.deleteInstances(val);

    fixReferringAnnotations(val);
  }

  /* XXX This is a bit brute force but it will do for the moment. We have to
   * turn a set of rules into a set of changes. If we'd preserved the rules
   * prior to this I guess we could figure out the differences without querying
   * the db.
   *
   * For the moment create a whole set of instances and then query the db to see if
   * they match.
   */
  @SuppressWarnings("unchecked")
  private void updateRecurrences(final EventInfo ei,
                                 final UpdateEventResult uc,
                                 final Collection<BwEventProxy> overrides,
                                 final boolean shared) throws CalFacadeException {
    final BwEvent val = ei.getEvent();
    final ChangeTable changes = val.getChangeset(currentPrincipal());

    if (!changes.isEmpty()) {
      if (!changes.recurrenceChanged()) {
        return;
      }

      if (!changes.recurrenceRulesChanged()) {
        // We can handle exdate and rdate changes.
        ChangeTableEntry ent = changes.getEntry(PropertyInfoIndex.EXDATE);
        if (ent.getAddedValues() != null) {
          // exdates added - remove the instances.
          removeInstances(val, uc, overrides, ent.getAddedValues(), shared);
        }

        if (ent.getRemovedValues() != null) {
          // exdates removed - add the instances.
//          addInstances(val, uc, overrides, ent.getRemovedValues(), shared);
        }

        ent = changes.getEntry(PropertyInfoIndex.RDATE);
        if (ent.getAddedValues() != null) {
          // rdates added - add the instances.
//          addInstances(val, uc, overrides, ent.getAddedValues(), shared);
        }

        if (ent.getRemovedValues() != null) {
          // rdates removed - remove the instances.
          removeInstances(val, uc, overrides, ent.getRemovedValues(), shared);
        }

        return;
      }
    }

    final Map<String, BwRecurrenceInstance> updated = new HashMap<>();

    /* Get all the times for this event. - this could be a problem. Need to
       limit the number. Should we do this in chunks, stepping through the
       whole period?
     */

    final RecurPeriods rp = 
            RecurUtil.getPeriods(val, 
                                 getAuthprops().getMaxYears(),
                                 getAuthprops().getMaxInstances());

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.

      // XXX Mark the master as non-recurring to stop it disappearing
      val.setRecurring(false);
      //throwException(CalFacadeException.noRecurrenceInstances);
    }

    final String stzid = val.getDtstart().getTzid();

/*    try {
      val.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(), stzid));
    } catch (Throwable t) {
      throwException(new CalFacadeException(t));
    } */

    int maxInstances = getAuthprops().getMaxInstances();

    final boolean dateOnly = val.getDtstart().getDateType();

    for (final Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      dtval = p.getEnd().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      final BwRecurrenceInstance ri = new BwRecurrenceInstance();

      ri.setDtstart(rstart);
      ri.setDtend(rend);
      ri.setRecurrenceId(ri.getDtstart().getDate());
      ri.setMaster(val);

      updated.put(ri.getRecurrenceId(), ri);
      maxInstances--;
      if (maxInstances == 0) {
        // That's all you're getting from me
        break;
      }
    }

    final List<BwRecurrenceInstance> current = dao.getInstances(val);

    for (final BwRecurrenceInstance ri: current) {
      final String rid = ri.getRecurrenceId();
      final BwRecurrenceInstance updri = updated.get(rid);

      if (updri == null) {
        // Not in the new instance set - delete from db
        ei.removeOverride(rid);
        uc.addDeleted(rid);
        dao.delete(ri);

        notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED, val, shared,
                             rid);
        continue;
      }
        
      /* Found instance with same recurrence id. Is the start and end the same
         */
      if (!ri.getDtstart().equals(updri.getDtstart()) ||
              !ri.getDtend().equals(updri.getDtend())) {
        ri.setDtstart(updri.getDtstart());
        ri.setDtend(updri.getDtend());

        dao.update(ri);
        uc.addUpdated(rid);

        notifyInstanceChange(SysEvent.SysCode.ENTITY_UPDATED, val, shared,
                             rid);
      }

      // Remove the entry - we've processed it.
      updated.remove(rid);
    }

    /* updated only contains recurrence ids that don't exist */

    for (final String rid: updated.keySet()) {
      //dao.save(ri);
      uc.addAdded(rid);

      notifyInstanceChange(SysEvent.SysCode.ENTITY_ADDED, val, shared,
                           rid);
    }
  }

  /* Remove instances identified by the Collection of recurrence ids
   */
  private void removeInstances(final BwEvent master,
                               final UpdateEventResult uc,
                               final Collection<BwEventProxy> overrides,
                               final Collection<BwDateTime> rids,
                               final boolean shared) throws CalFacadeException {
    for (final BwDateTime dt: rids) {
      removeInstance(master, uc, overrides, dt.getDate(), shared);
    }
  }

  /* Remove instances identified by the Collection of recurrence ids
   */
  private void removeInstances(final BwEvent master,
                               final Collection<String> rids,
                               final UpdateEventResult uc,
                               final Collection<BwEventProxy> overrides,
                               final boolean shared) throws CalFacadeException {
    for (final String rid: rids) {
      removeInstance(master, uc, overrides, rid, shared);
    }
  }

  /* Remove instances identified by the Collection of recurrence ids
   */
  private void removeInstance(final BwEvent master,
                              final UpdateEventResult uc,
                              final Collection<BwEventProxy> overrides,
                              final String rid,
                              final boolean shared) throws CalFacadeException {
    notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED, master, shared, rid);

    if (overrides != null) {
      BwEventProxy prToRemove = null;
      for (final BwEventProxy pr: overrides) {
        if (pr.getRecurrenceId() == null) {
          throw new NullPointerException();
        }

        if (pr.getRecurrenceId().equals(rid)) {
          // This one is being deleted
          prToRemove = pr;
          break;
        }
      }

      if (prToRemove != null) {
        overrides.remove(prToRemove);
      }
    }

    final BwRecurrenceInstance inst = dao.getInstance(master, rid);
    if (inst != null) {
      dao.delete(inst);
      uc.addDeleted(rid);
    }
  }

  /* Add instances identified by the Collection of recurrence ids
   *  /
  private void addInstances(final BwEvent master,
                            final UpdateEventResult uc,
                            final Collection<BwEventProxy> overrides,
                            final Collection rids,
                            final boolean shared) throws CalFacadeException {
    final Dur dur = new Dur(master.getDuration());

    for (final Object rid : rids) {
      final BwDateTime start = (BwDateTime)rid;
      final BwDateTime end = start.addDur(dur);

      final BwRecurrenceInstance ri = new BwRecurrenceInstance();

      ri.setDtstart(start);
      ri.setDtend(end);
      ri.setRecurrenceId(start.getDate());
      ri.setMaster(master);

      if (!Util.isEmpty(overrides)) {
        for (final BwEventProxy pxy: overrides) {
          final BwEventAnnotation ann = pxy.getRef();

          if (!ann.getRecurrenceId().equals(ri.getRecurrenceId())) {
            continue;
          }

          ann.setRecurring(false); // be safe

          if (ann.getTombstoned() == null) {
            ann.setTombstoned(false); // be safe
          }

          if (!ann.unsaved()) {
            updateProxy(new BwEventProxy(ann));
          } else {
            dao.save(ann);
          }

          ri.setOverride(ann);
          break;
        }
      }

      dao.save(ri);

      notifyInstanceChange(SysEvent.SysCode.ENTITY_ADDED, master,
                           shared,
                           start.getDate());

      uc.addAdded(ri);
    }
  }
   */

  private void fixReferringAnnotations(final BwEvent val) throws CalFacadeException {
    /* We may have annotations to annotations so we hunt them all down deleting
     * the leaf entries first.
     */

    for (final BwEventAnnotation ev: dao.getAnnotations(val, false)) {
      /* The recursive call is intended to allow annotations to annotatiuons.
       * Unfortunately this in't going to work as the reference in the
       * annotation class is always to the master event. We need an extra column
       * which allows chaining to an annotation
       */
      // XXX fix this fixReferringAnnotations(ev);

      // Force a fetch of the attendees - we need to look at them later
      ev.getAttendees();

      //if (ev.getAttendees() != null) {
      //  ev.getAttendees().clear();
      //}
      dao.delete(ev);
    }
  }


  private CoreEventInfo makeOverrideProxy(final BwEventAnnotation override,
                                          final CurrentAccess ca) throws CalFacadeException {
    return new CoreEventInfo(new BwEventProxy(override), ca);
  }

  /** The master has been checked for access and we now build and
   * return an event proxy for an instance which has no override.
   *
   * @param inst        the instance
   * @param ca          Checked access from master
   * @return CoreEventInfo
   * @throws CalFacadeException on error
   */
  private CoreEventInfo makeInstanceProxy(final BwRecurrenceInstance inst,
                                          final CurrentAccess ca) throws CalFacadeException {
    final BwEvent mstr = inst.getMaster();

    /*
    if (recurRetrieval.mode == Rmode.masterOnly) {
      // Master only and we've just seen it for the first time
      // Note we will not do this for freebusy. We need all recurrences.

      /* XXX I think this was wrong. Why make an override?
       * /
      // make a fake one pointing at the owners override
      override = new BwEventAnnotation();
      override.setTarget(mstr);
      override.setMaster(mstr);

      BwDateTime start = mstr.getDtstart();
      BwDateTime end = mstr.getDtend();

      override.setDtstart(start);
      override.setDtend(end);
      override.setDuration(BwDateTime.makeDuration(start, end).toString());
      override.setCreatorHref(mstr.getCreatorHref());
      override.setOwnerHref(getUser().getPrincipalRef());

      return new CoreEventInfo(new BwEventProxy(override), ca);
    }
    */

    /* success so now we build a proxy with the event and any override.
     */

    final BwEventAnnotation override = new BwEventAnnotation();

    override.setTarget(mstr);
    override.setMaster(mstr);

    final BwDateTime start = inst.getDtstart();
    final BwDateTime end = inst.getDtend();
    override.setRecurrenceId(inst.getRecurrenceId());

    override.setDtstart(start);
    override.setDtend(end);
    override.setDuration(BwDateTime.makeDuration(start, end).toString());
    override.setCreatorHref(mstr.getCreatorHref());
    override.setOwnerHref(mstr.getOwnerHref());
    override.setOverride(true);
    override.setName(mstr.getName());
    override.setUid(mstr.getUid());

    /* At this point we have an event with possible overrides. If this is free
     * busy we need to replace it all with a skeleton event holding only date/time
     * information.
     *
     * We can't do this before I think because we need to allow the user to
     * override the transparency on a particular instance,
     */

    final BwEvent proxy = new BwEventProxy(override);

    return new CoreEventInfo(proxy, ca);
  }

  private boolean calendarNameExists(final BwEvent val,
                                     final boolean annotation,
                                     final boolean adding) throws CalFacadeException {
    final long startTime = System.currentTimeMillis();
    try {
      return dao.calendarNameExists(val, annotation, adding);
    } finally {
      stat(StatsEvent.checkNameTime, startTime);
    }
  }

  private String calendarGuidExists(final BwEvent val,
                                     final boolean annotation,
                                     final boolean adding) throws CalFacadeException {
    final long startTime = System.currentTimeMillis();
    try {
      return dao.calendarGuidExists(val, annotation, adding);
    } finally {
      stat(StatsEvent.checkUidTime, startTime);
    }
  }

  private class RecuridTable extends HashMap<String, BwEventProxy> {
    RecuridTable(final Collection<BwEventProxy> events) {
      for (final BwEventProxy ev: events) {
        final String rid = ev.getRecurrenceId();
        if (debug()) {
          debug("Add override to table with recurid " + rid);
        }

        put(rid, ev);
      }
    }
  }
}
