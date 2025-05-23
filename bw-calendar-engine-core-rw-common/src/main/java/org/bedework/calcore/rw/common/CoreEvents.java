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
package org.bedework.calcore.rw.common;

import org.bedework.access.CurrentAccess;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkDupNameException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.rw.common.dao.EventsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calfacade.wrappers.CollectionWrapper;
import org.bedework.convert.RecurUtil;
import org.bedework.convert.RecurUtil.RecurPeriods;
import org.bedework.sysevents.events.StatsEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.Period;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import static org.bedework.calfacade.util.CalFacadeUtil.getHrefRecurrenceId;

/** Class to encapsulate most of what we do with events.
 *
 * <p>Note that the following is only partially correct now. The database
 * handling of recurrences is getting much more simplified with opensearch
 * handling the issues with instance indexing.
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
  private final EventsDAO dao;

  private final AuthProperties authProps;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param authProps - authorisation info
   * @param sessionless if true
   */
  public CoreEvents(final EventsDAO dao,
                    final Calintf intf,
                    final AccessChecker ac,
                    final AuthProperties authProps,
                    final boolean sessionless) {
    this.dao = dao;
    super.init(intf, ac, sessionless);
    this.authProps = authProps;
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String uid) {
    final TreeSet<CoreEventInfo> ts = new TreeSet<>();

    /*
    if (colPath != null) {
      BwCollection cal = getEntityCollection(colPath, privRead, scheduling, false);
      desiredAccess = ((CollectionWrapper)cal).getLastDesiredAccess();
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
    Collection<? extends BwEvent> evs =
            dao.eventQuery(BwEventObj.class, colPath, uid,
                           null,
                           null);  // overrides

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
                           null,
                           false);  // overrides
    }

    if (Util.isEmpty(evs)) {
      return ts;
    }

    final Collection<CoreEventInfo> ceis =
            intf.postGetEvents(evs, privRead,
                               returnResultAlways);

    if (ceis.isEmpty()) {
      return ceis;
    }

    /* If the recurrence id is null, do overrides for each retrieved event,
     */

    for (final CoreEventInfo cei: ceis) {
      final BwEvent master = cei.getEvent();

      if (master.getEntityType() == IcalDefs.entityTypeVavailability) {
        for (final String auid : master.getAvailableUids()) {
          final Collection<CoreEventInfo> aceis = getEvent(colPath, auid);
          if (aceis.size() != 1) {
            throwException(CalFacadeErrorCode.badResponse);
          }

          cei.addContainedItem(aceis.iterator().next());
        }
        ts.add(cei);
      } else if (!master.testRecurring()) {
        ts.add(cei);
      } else {
        getOverrides(cei);

        ts.add(cei);
      }
    }

    return ts;
  }

  /**
   * User: mike Date: 6/13/18 Time: 23:06
   */
  public static class PathAndName {
    final String colPath;
    final String name;

    public PathAndName(final String href) {
      final int pos = href.lastIndexOf("/");
      if (pos < 0) {
        throw new RuntimeException("Bad href: " + href);
      }

      name = href.substring(pos + 1);

      colPath = href.substring(0, pos);
    }
  }

  @Override
  public CoreEventInfo getEvent(final String href) {
    final var hr = getHrefRecurrenceId(href);

    final var pn = new PathAndName(hr.hrefNorid);
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
            throwException(new BedeworkException(CalFacadeErrorCode.duplicateName));
            return null;
          }

          ev = lev;
        } else {
          throwException(new BedeworkException(CalFacadeErrorCode.duplicateName));
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
                                                returnResultAlways);

    if (cei != null)  {
      // Access was not denied

      if (avails != null) {
        for (final BwEvent aev: avails) {
          final CoreEventInfo acei = 
                  intf.postGetEvent(aev,
                                    privRead,
                                    returnResultAlways);
          if (acei == null) {
            continue;
          }

          if (aev.testRecurring()) {
            getOverrides(acei);
          }

          cei.addContainedItem(acei);
        }
      } else {
        ev = cei.getEvent();
        if (ev.testRecurring()) {
          getOverrides(cei);
          if (hr.recurrenceId != null) {
            // Single instance
            return getInstance(cei, hr.recurrenceId);
          }
        }
      }
    }

    return cei;
  }

  private CoreEventInfo getInstance(final CoreEventInfo cei,
                                    final String recurrenceId) {
    final BwEvent ev = cei.getEvent();
    final String href = ev.getHref() + "#" + recurrenceId;

    final GetEntityResponse<EventInfo> ires =
            getIndexer(ev).fetchEvent(href);

    if (!ires.isOk()) {
      return null;
    }

    final EventInfo ei = ires.getEntity();
    return new CoreEventInfo(ei.getEvent(),
                             ei.getCurrentAccess());
  }

  @Override
  public Collection<CoreEventInfo> getEvents(
          final Collection<BwCollection> collections,
          final FilterBase filter,
          final BwDateTime startDate,
          final BwDateTime endDate,
          final List<BwIcalPropertyInfoEntry> retrieveList,
          final DeletedState delState,
          final RecurringRetrievalMode recurRetrieval,
          final boolean freeBusy) {
    throw new BedeworkException("Implemented in the interface class");
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) {
    final BwEvent val = ei.getEvent();
    final Collection<BwEventProxy> overrides = ei.getOverrideProxies();
    final long startTime = System.currentTimeMillis();
    RecuridTable recurids = null;
    final UpdateEventResult uer = new UpdateEventResult();

    final BwCollection cal = getEntityCollection(val.getColPath(), privBind,
                                                 scheduling, false);

    if (cal == null) {
      uer.errorCode = CalFacadeErrorCode.noEventCalendar;
      return uer;
    }

    uer.addedUpdated = true;

    /* Indicate if we want sharing notifications of changes */
    final boolean shared = cal.getPublick() || cal.getShared();

    final CollectionInfo collInf = cal.getCollectionInfo();

    if (!Util.isEmpty(overrides)) {
      if (!val.isRecurringEntity()) {
        throwException(CalFacadeErrorCode.overridesForNonRecurring);
      }

      recurids = new RecuridTable(overrides);
    }

    if (val.getUid() == null) {
      throwException(CalFacadeErrorCode.noEventGuid);
    }

    if (val.getName() == null) {
      throwException(CalFacadeErrorCode.noEventName);
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
        throwException(CalFacadeErrorCode.duplicateGuid, name);
      }
    }

    /* Similarly for event names which must be unique within a collection.
     * Note that a duplicate name is essentially overwriting an event with a
     * new uid - also disallowed.
     */
    if ((val.getEntityType() != IcalDefs.entityTypeAvailable) &&
        (collectionNameExists(val, false, true) ||
          collectionNameExists(val, true, true))) {
      throwException(CalFacadeErrorCode.duplicateName, val.getName());
    }

    setupDependentEntities(val);

    /* Remove any tombstoned event in the collection with same uid */
    deleteTombstoned(val.getColPath(), val.getUid());

    /* If it's a recurring event see what we can do to optimize searching
     * and retrieval
     */
    if ((val instanceof BwEventAnnotation) || !val.getRecurring()) {
      dao.add(val);

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
            RecurUtil.getPeriods(val, authProps.getMaxYears(),
                                 authProps.getMaxInstances());

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.
      if (rollbackOnError) {
        throwException(CalFacadeErrorCode.noRecurrenceInstances,
                       val.getUid());
      }

      uer.addedUpdated = false;
      uer.errorCode = CalFacadeErrorCode.noRecurrenceInstances;

      stat(StatsEvent.createTime, startTime);

      indexEntity(ei);

      return uer;
    }

    /* We can save the master at this point */
    dao.add(val);

    final String stzid = val.getDtstart().getTzid();

    int maxInstances = authProps.getMaxInstances();

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

      final String rid = rstart.getDate();

      if (firstRecurrenceId == null) {
        firstRecurrenceId = rid;
      } else if (firstRecurrenceId.equals(rid)) {
        // Skip it
        if (debug()) {
          debug("Skipping duplicate recurid " + firstRecurrenceId);
        }

        continue;
      }

      if (recurids != null) {
        /* See if we have a recurrence */
        final BwEventProxy ov = recurids.get(rid);

        if (ov != null) {
          if (debug()) {
            debug("Add override with recurid " + rid);
          }

          setupDependentEntities(ov);
          addOverride(ov, val);

          recurids.remove(rid);
        }
      }

      maxInstances--;
      if (maxInstances == 0) {
        // That's all you're getting from me
        break;
      }
    }

    if ((recurids != null) && (!recurids.isEmpty())) {
      /* We removed all the valid overrides - we are left with those
       * with recurrence ids that don't match.
       */
      if (rollbackOnError) {
        throwException(CalFacadeErrorCode.invalidOverride);
      }

      uer.failedOverrides = recurids.values();
    }

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
  public UpdateEventResult updateEvent(final EventInfo ei) {
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
      } catch (final BedeworkException be) {
        throwException(be);
      }
    }

    BwEventProxy proxy = null;

    if (val instanceof BwEventProxy) {
      proxy = (BwEventProxy)val;
    }

    final BwCollection col = getCollection(val.getColPath());
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
          throwException(CalFacadeErrorCode.duplicateGuid, name);
        }
      }

      /* Similarly for event names which must be unique within a collection
       */
      if (collectionNameExists(val, false, false) ||
          collectionNameExists(val, true, false)) {
        throwException(new BedeworkDupNameException(val.getName()));
      }
    }

    if (proxy == null) {
      dao.update(val);

      final Collection<BwDbentity<?>> deleted = val.getDeletedEntities();
      if (deleted != null) {
        for (final BwDbentity<?> ent: deleted) {
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
              dao.add(ann);
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
            dao.add(ann);
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
                                    final boolean reallyDelete) {
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
      final var col = getEntityCollection(ev.getColPath(),
                                          privAny, scheduling, false);
      shared = col.getPublick() || col.getShared();

      if (!scheduling) {
        desiredAccess = privUnbind;
      } else {
        /* Delete message while tidying up in/outbox.
         * Set desiredAccess to something that works.
         *  */

        final CollectionWrapper cw = (CollectionWrapper)col;
        desiredAccess = cw.getLastDesiredAccess();
      }

      ac.checkAccess(ev, desiredAccess, false);
    } catch (final BedeworkException be) {
      dao.rollback();
      throw be;
    }

    if (!reallyDelete && ev.getTombstoned()) {
      // no-op - just pretend

      der.eventDeleted = true;

      return der;
    }

    if (isMaster) {
      // Master event - delete all overrides.
      fixReferringAnnotations(ev, shared);

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

      notifyDelete(true, ev, shared);

      if (!ann.unsaved()) {
        //der.alarmsDeleted = deleteAlarms(ann);

        ann.getAttendees().clear();
        dao.delete(ann);
      }

      final BwDateTime masterStart = master.getDtstart();
      final String startTzid;
      if (masterStart == null) {
        startTzid = null;
      } else {
        startTzid = masterStart.getTzid();
      }

      final BwDateTime instDate =
              BwDateTime.fromUTC(ev.getRecurrenceId().length() == 8,
                                 ev.getRecurrenceId(),
                                 startTzid);

      if (!master.getRdates().remove(instDate)) {
        // Wasn't an rdate event
        master.addExdate(instDate);
      }
      master.setDtstamps(intf.getCurrentTimestamp());
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

  private void unindexEntity(final EventInfo ei) {
    final BwEvent ev = ei.getEvent();

    if (ev.getRecurrenceId() != null) {
      // Cannot index single instance
      warn("Tried to unindex a recurrence instance");
      return;
    }

    getIndexer(ev).unindexEntity(ev.getHref());
  }

  @Override
  public void moveEvent(final EventInfo ei,
                        final BwCollection from,
                        final BwCollection to) {
    final var ev = ei.getEvent();

    if (ev.getRecurrenceId() != null) {
      throw new BedeworkException("Cannot move an instance");
    }

    deleteTombstoned(to.getPath(), ev.getUid());
    final var href = ev.getHref();

    // Tombstoning effectively deletes the old entity.
    // No tombstone for pending inbox

    if (from.getCalType() != BwCollection.calTypePendingInbox) {
      final BwEvent tombstone = ev.cloneTombstone();
      tombstone.setDtstamps(intf.getCurrentTimestamp());

      //tombstoneEvent(tombstone);

      dao.add(tombstone);
      final EventInfo old = new EventInfo(tombstone);
      indexEntity(old);
//    } else {
//      // Just delete it
//      final var toDelete = new EventInfo(val);
//      deleteEvent(toDelete, true, true);
//      unindexEntity(toDelete);
    }

    ev.setColPath(to.getPath());
    // Don't save just yet - updates get triggered
    // TODO - this is asking for trouble if it fails

    if (ev.testRecurring()) {
      // TODO I think this is wrong in that it may pull in annotations
      // that are not overrides.
      for (final BwEventAnnotation aev:
              dao.getAnnotations(ev)) {
        aev.setColPath(to.getPath());
        notifyInstanceChange(SysEvent.SysCode.ENTITY_MOVED,
                             ev, from.getShared(),
                             aev.getRecurrenceId());
      }
    }

    notifyMove(SysEvent.SysCode.ENTITY_MOVED,
               href,
               from.getShared(),
               ev,
               to.getShared());
  }

  /** Remove much of the data associated with the event and then tombstone it.
   *
   * @param val the event
   */
  private void tombstoneEvent(final BwEvent val) {
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

    val.setDtstamps(intf.getCurrentTimestamp());
    val.setTombstoned(true);

    dao.update(val);
  }

  private void deleteTombstoned(final String colPath,
                                final String uid) {
    dao.deleteTombstonedEvent(fixPath(colPath), uid);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String token) {
    if (path == null) {
      dao.rollback();
      throw new BedeworkException("Missing path");
    }

    final String fpath = fixPath(path);

    final BwCollection col = getCollection(fpath);
    ac.checkAccess(col, privAny, false);

    @SuppressWarnings("unchecked")
    final List<BwEvent> evs =
            (List<BwEvent>)dao.getSynchEventObjects(fpath, token);

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
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) {
    final Collection<String> res = dao.getChildrenEntities(parentPath, 
                                                           start, 
                                                           count);

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    return dao.getEventAnnotations();
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) {
    return dao.getEventOverrides(ev);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void setupDependentEntities(final BwEvent val) {
    // Ensure collections in reasonable state.
    if (val.getAlarms() != null) {
      for (final BwAlarm alarm: val.getAlarms()) {
        alarm.setOwnerHref(getPrincipal().getPrincipalRef());
      }
    }
  }

  /* Called by updateEvent to update a proxied event (annotation) or an
   * override.
   */
  private void updateProxy(final BwEventProxy proxy) {
    /* if this is a proxy for a recurrence instance of our own event
       then the recurrence instance should point at this override.
       Otherwise we just update the event annotation.
     */
    final var override = proxy.getRef();
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
      override.setOwnerHref(mstr.getOwnerHref()); // XXX Force owner????
      dao.update(override);

      /* Update the lastmod on the master event */
      mstr.setDtstamps(intf.getCurrentTimestamp());
      dao.update(mstr);
    } else {
      dao.update(override);
    }

    proxy.setChangeFlag(false);
  }

  /* Retrieves the overrides for a recurring event.
   */
  private void getOverrides(final CoreEventInfo cei) {
    final BwEvent master = cei.getEvent();
    final CurrentAccess ca = cei.getCurrentAccess();

    final Collection<BwEventAnnotation> ovs =
            dao.eventQuery(BwEventAnnotation.class,
                           null,
                           null,
                           master,
                           true);  // overrides

    if (ovs != null) {
      for (final BwEventAnnotation override: ovs) {
        final CoreEventInfo ocei = makeOverrideProxy(override, ca);

        cei.addOverride(ocei);
      }
    }
  }

  /* Called when adding an event with overrides
   */
  private void addOverride(final BwEventProxy proxy,
                           final BwEvent master) {
    final BwEventAnnotation override = proxy.getRef();
    if (override.getOwnerHref() == null) {
      override.setOwnerHref(master.getOwnerHref());
    }
    override.setMaster(master);
    override.setTarget(master);
    override.setOverride(true);
    override.setTombstoned(false);

    dao.update(override);
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
                                 final boolean shared) {
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

        ent = changes.getEntry(PropertyInfoIndex.RDATE);
        if (ent.getRemovedValues() != null) {
          // rdates removed - remove the instances.
          removeInstances(val, uc, overrides, ent.getRemovedValues(), shared);
        }

        return;
      }
    }

    final Set<String> updated = new TreeSet<>();

    /* Get all the times for this event. - this could be a problem. Need to
       limit the number. Should we do this in chunks, stepping through the
       whole period?
     */

    final RecurPeriods rp = 
            RecurUtil.getPeriods(val, 
                                 authProps.getMaxYears(),
                                 authProps.getMaxInstances());

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.

      // XXX Mark the master as non-recurring to stop it disappearing
      val.setRecurring(false);
      //throwException(CalFacadeErrorCode.noRecurrenceInstances);
    }

    final String stzid = val.getDtstart().getTzid();

/*    try {
      val.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(), stzid));
    } catch (Throwable t) {
      throwException(new BedeworkException(t));
    } */

    int maxInstances = authProps.getMaxInstances();

    final boolean dateOnly = val.getDtstart().getDateType();

    for (final Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      final BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      final var instid = rstart.getDate();

      updated.add(instid);
      maxInstances--;
      if (maxInstances == 0) {
        // That's all you're getting from me
        break;
      }
    }

    if (!Util.isEmpty(overrides)) {
      for (final BwEventProxy pxy: overrides) {
        final BwEventAnnotation ann = pxy.getRef();
        final String rid = ann.getRecurrenceId();

        if (!updated.contains(rid)) {
          // Not in the new instance set - delete from db
          ei.removeOverride(rid);
          uc.addDeleted(rid);

          notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED, val,
                               shared,
                               rid);
          continue;
        }
        
        // Remove the entry - we've processed it.
        updated.remove(rid);
      }
    }

    /* updated only contains recurrence ids that don't exist */

    for (final String rid: updated) {
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
                               final boolean shared) {
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
                               final boolean shared) {
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
                              final boolean shared) {
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

    uc.addDeleted(rid);
  }

  private BwCollection getEntityCollection(final String path,
                                           final int nonSchedAccess,
                                           final boolean scheduling,
                                           final boolean alwaysReturn) {
    final int desiredAccess;

    if (!scheduling) {
      desiredAccess = nonSchedAccess;
    } else {
      desiredAccess = privAny;
    }

    final BwCollection cal =
            intf.getCollection(path, desiredAccess,
                               alwaysReturn | scheduling);
    if (cal == null) {
      return null;
    }

    if (!cal.getCalendarCollection()) {
      throwException(new BedeworkAccessException());
    }

    if (!scheduling) {
      return cal;
    }

    CurrentAccess ca;
    final AccessUtilI access = ac.getAccessUtil();

    if ((cal.getCalType() == BwCollection.calTypeInbox) ||
            (cal.getCalType() == BwCollection.calTypePendingInbox)) {
      ca = access.checkAccess(cal, privScheduleDeliver,
                              true); //alwaysReturn
      if (!ca.getAccessAllowed()) {
        // try old style
        ca = access.checkAccess(cal, privScheduleRequest,
                                true); //alwaysReturn
      }
    } else if (cal.getCalType() == BwCollection.calTypeOutbox) {
      ca = access.checkAccess(cal, privScheduleSend, true);
      if (!ca.getAccessAllowed()) {
        // try old style
        ca = access.checkAccess(cal, privScheduleReply,
                                true); //alwaysReturn
      }
    } else {
      throw new BedeworkAccessException();
    }

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return cal;
  }

  private void fixReferringAnnotations(final BwEvent val,
                                       final boolean shared) {
    /* We may have annotations to annotations so we hunt them all down deleting
     * the leaf entries first.
     */

    for (final var ev: dao.getAnnotations(val)) {
      /* The recursive call is intended to allow annotations to annotatiuons.
       * Unfortunately this in't going to work as the reference in the
       * annotation class is always to the master event. We need an extra column
       * which allows chaining to an annotation
       */
      // XXX fix this fixReferringAnnotations(ev);

      // Force a fetch of the attendees - we need to look at them later
      //noinspection ResultOfMethodCallIgnored
      ev.getAttendees();

      //if (ev.getAttendees() != null) {
      //  ev.getAttendees().clear();
      //}
      dao.delete(ev);
      notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED,
                           val, shared,
                           ev.getRecurrenceId());
    }
  }


  private CoreEventInfo makeOverrideProxy(final BwEventAnnotation override,
                                          final CurrentAccess ca) {
    return new CoreEventInfo(new BwEventProxy(override), ca);
  }

  private boolean collectionNameExists(final BwEvent val,
                                       final boolean annotation,
                                       final boolean adding) {
    final long startTime = System.currentTimeMillis();
    try {
      return dao.collectionNameExists(val, annotation, adding);
    } finally {
      stat(StatsEvent.checkNameTime, startTime);
    }
  }

  private String calendarGuidExists(final BwEvent val,
                                     final boolean annotation,
                                     final boolean adding) {
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
