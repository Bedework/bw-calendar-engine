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
package org.bedework.calcore.es;

import org.bedework.access.Acl.CurrentAccess;
import org.bedework.calcore.AccessUtil;
import org.bedework.calcore.hibernate.EventQueryBuilder;
import org.bedework.calcore.hibernate.EventQueryBuilder.EventsQueryResult;
import org.bedework.calcore.hibernate.Filters;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI;
import org.bedework.calcorei.HibSession;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCalendar.CollectionInfo;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwRecurrenceInstance;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeDupNameException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.Position;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.sysevents.events.StatsEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.TimeZone;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
public class CoreEvents extends CalintfHelperEs implements CoreEventsI {
  /** Constructor
   *
   * @param chcb
   * @param cb
   * @param access
   * @param currentMode
   * @param sessionless
   */
  public CoreEvents(final CalintfHelperEsCb chcb,
                    final Callback cb,
                    final AccessUtil access,
                    final int currentMode,
                    final boolean sessionless) {
    super(chcb);
    super.init(cb, access, currentMode, sessionless);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#startTransaction()
   */
  @Override
  public void startTransaction() throws CalFacadeException {
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#endTransaction()
   */
  @Override
  public void endTransaction() throws CalFacadeException {
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#getEvent(java.lang.String, java.lang.String, java.lang.String, boolean, org.bedework.calfacade.RecurringRetrievalMode)
   */
  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String uid,
                                            final String rid,
                                            final boolean scheduling,
                                            final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    /* If we have a recurrence id we want an expanded form of the instance.
     *
     * If we don't then recurRetrieval may provide a date range and
     * indicates if the events should be expanded or returned as
     * master + overrides.
     */

    FilterBase fb;
    boolean expanded = false;

    if ((rid != null) ||
            ((recurRetrieval != null) &&
                     (recurRetrieval.mode == Rmode.expanded))) {
      fb = entityTypes();
      expanded = true;
    } else {
      fb = masterOverrideTypes();
    }

    if (colPath != null) {
      fb = FilterBase.addAndChild(fb,
              new ObjectFilter<>(PropertyInfoIndex.COLPATH, colPath));
    }

    fb = FilterBase.addAndChild(fb,
                                new ObjectFilter<>(PropertyInfoIndex.UID, uid));

    if (rid != null) {
      fb = FilterBase.addAndChild(fb,
                                  new ObjectFilter<>(
                                          PropertyInfoIndex.RECURRENCE_ID,
                                          rid));
    }

    return doSearch(expanded,
                    fb,
                    privRead,
                    recurRetrieval);
  }

  private FilterBase entityTypes() {
    OrFilter or = new OrFilter();

    or.addChild(new ObjectFilter<String>(PropertyInfoIndex.DOCTYPE,
                                         IcalDefs.entityTypeNames[IcalDefs.entityTypeEvent]));
    or.addChild(new ObjectFilter<String>(PropertyInfoIndex.DOCTYPE,
                                         IcalDefs.entityTypeNames[IcalDefs.entityTypeTodo]));

    return or;
  }

  private FilterBase masterOverrideTypes() {
    OrFilter or = new OrFilter();

    for (String t: BwIndexer.masterOverrideEventTypes) {
      or.addChild(new ObjectFilter<String>(PropertyInfoIndex.DOCTYPE,
                                           t));
    }

    return or;
  }

  private boolean inOverrideDocTypes(String docType) {
    for (String s: BwIndexer.overrideDocTypes){
      if (s.equals(docType)) {
        return true;
      }
    }

    return false;
  }

  private Collection<CoreEventInfo> doSearch(final boolean expanded,
                                             final FilterBase filter,
                                             final int desiredAccess,
                                             final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    TreeSet<CoreEventInfo> evs = new TreeSet<>();

    String start = null;
    String end = null;

    if (recurRetrieval != null) {
      if (recurRetrieval.start != null) {
        start = recurRetrieval.start.getDate();
      }
      if (recurRetrieval.end != null) {
        end = recurRetrieval.end.getDate();
      }
    }

    SearchResult sr = getIndexer().search(null,
                                          filter,
                                          null,   // sort
                                          start,
                                          end,
                                          -1,
                                          getAccessChecker());

    List<SearchResultEntry> sres = getIndexer().getSearchResult(sr,
                                                                Position.current);

    if (Util.isEmpty(sres)) {
      return evs;
    }

    Map<String, CoreEventInfo> masters = null;
    Map<String, Collection<CoreEventInfo>> overrides = null;

    if (!expanded) {
      masters = new HashMap<>();
      overrides = new HashMap<>();
    }

    /* TODO this needs to handle VAvailability and availables.
       See what the old get by name did
     */

    for (SearchResultEntry sre: sres) {
      if (!(sre.getEntity() instanceof EventInfo)) {
        continue;
      }

      EventInfo ei = (EventInfo)sre.getEntity();

      CoreEventInfo cei = postGetEvent(ei.getEvent(),
                                       desiredAccess,
                                       returnResultAlways,
                                       null);

      if (cei == null) {
        continue;
      }

      if (expanded) {
        evs.add(cei);
        continue;
      }

      String path = ei.getEvent().getColPath();

      if (inOverrideDocTypes(sre.getDocType())) {
        Collection<CoreEventInfo> ov = overrides.get(path);

        if (ov == null) {
          ov = new ArrayList<>();
          overrides.put(path, ov);
        }

        ov.add(cei);
        continue;
      }

      masters.put(path, cei);
      evs.add(cei);
    }

    for (String cp: masters.keySet()){
      CoreEventInfo cei = masters.get(cp);

      cei.setOverrides(overrides.get(cp));
    }

    return evs;
  }

  @Override
  public Collection<CoreEventInfo> getEvents(final Collection<BwCalendar> calendars,
                                             final FilterBase filter,
                                             final BwDateTime startDate,
                                             final BwDateTime endDate,
                                             final List<String> retrieveList,
                                             RecurringRetrievalMode recurRetrieval,
                                             final boolean freeBusy) throws CalFacadeException {
    /* Ensure dates are limited explicitly or implicitly */
    recurRetrieval = defaultRecurringRetrieval(recurRetrieval,
                                               startDate, endDate);

    if (debug) {
      trace("getEvents for start=" + startDate + " end=" + endDate);
    }

    Collection<String> colPaths = null;

    if (calendars != null) {
      colPaths = new ArrayList<>();
      for (BwCalendar c: calendars) {
        colPaths.add(c.getPath());

        if (debug) {
          trace("   calendar:" + c.getPath());
        }
      }
    }

    List<BwIcalPropertyInfoEntry> retrieveListFields = null;

    if (retrieveList != null) {
      // Convert property names to field names
      retrieveListFields = new ArrayList<>(retrieveList.size() +
                                                   BwIcalPropertyInfo.requiredPindexes.size());

      for (String pname: retrieveList) {
        PropertyInfoIndex pi;

        try {
          pi = PropertyInfoIndex.valueOf(pname);
        } catch (Throwable t) {
          //warn("Bad property " + pname);
          retrieveListFields = null;
          break;
        }

        BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

        if ((ipie == null) || (ipie.getMultiValued())) {
          // At this stage it seems better to be inefficient
          //warn("Bad property " + pname);
          retrieveListFields = null;
          break;
        }

        retrieveListFields.add(ipie);
      }

      if (retrieveListFields != null) {
        for (PropertyInfoIndex pi: BwIcalPropertyInfo.requiredPindexes) {
          retrieveListFields.add(BwIcalPropertyInfo.getPinfo(pi));
        }
      }
    }

    int desiredAccess = privRead;
    if (freeBusy) {
      // DORECUR - freebusy events must have enough info for expansion
      desiredAccess = privReadFreeBusy;
    }

    EventsQueryResult eqr = new EventsQueryResult();
//    eqr.flt = new Filters(getCalintfCb(), filter);
    eqr.flt = new Filters(filter);
    eqr.colPaths = colPaths;

    eventsQuery(eqr, startDate, endDate,
                retrieveListFields,
                freeBusy,
                null, // master
                null, // masters
                null, // uids
                getEvents);

    Collection<CoreEventInfo> ceis = postGetEvents(eqr.es, desiredAccess,
                                                   returnResultAlways,
                                                   null);

    /* Now get the annotations - these are not overrides */
    eventsQuery(eqr, startDate, endDate,
                retrieveListFields,
                freeBusy,
                null, // master
                null, // masters
                null, // uids
                getAnnotations);

    if (!eqr.es.isEmpty()) {
      ceis.addAll(postGetEvents(eqr.es, desiredAccess, returnResultAlways,
                                eqr.flt));
    }

    /* Won't need with ES
    ceis = getRecurrences(eqr, ceis,
                          startDate, endDate,
                          retrieveListFields, recurRetrieval, desiredAccess,
                          freeBusy);
                          */

    return buildVavail(ceis);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.EventsI#getEvent(org.bedework.calfacade.BwCalendar, java.lang.String, org.bedework.calfacade.RecurringRetrievalMode)
   */
  @Override
  @SuppressWarnings("unchecked")
  public CoreEventInfo getEvent(final String colPath,
                                final String name,
                                final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    FilterBase fb;
    boolean expanded = false;

    if ((recurRetrieval != null) &&
            (recurRetrieval.mode == Rmode.expanded)) {
      fb = entityTypes();
      expanded = true;
    } else {
      fb = masterOverrideTypes();
    }

    fb = FilterBase.addAndChild(fb,
                                new ObjectFilter<>(PropertyInfoIndex.COLPATH, colPath));
    fb = FilterBase.addAndChild(fb,
                                new ObjectFilter<>(PropertyInfoIndex.NAME, name));

    Collection<CoreEventInfo> evs = doSearch(expanded,
                                             fb,
                                             privRead,
                                             recurRetrieval);

    if (evs.size() == 0) {
      return null;
    }

    if (evs.size() == 1) {
      return evs.iterator().next();
    }

    throwException(new CalFacadeException(CalFacadeException.duplicateName));
    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#addEvent(org.bedework.calfacade.BwEvent, java.util.Collection, boolean, boolean)
   */
  @Override
  public UpdateEventResult addEvent(final BwEvent val,
                                    final Collection<BwEventProxy> overrides,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) throws CalFacadeException {
    long startTime = System.currentTimeMillis();
    RecuridTable recurids = null;
    HibSession sess = getSess();
    UpdateEventResult uer = new UpdateEventResult();

    uer.addedUpdated = true;

    BwCalendar cal = getEntityCollection(val.getColPath(), privBind,
                                         scheduling, false);
    boolean shared = cal.getShared();

    CollectionInfo collInf = cal.getCollectionInfo();

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

    /** If it's a recurring event see what we can do to optimize searching
     * and retrieval
     */
    if ((val instanceof BwEventAnnotation) || !val.getRecurring()) {
      sess.save(val);

      if (!getForRestore()) {
        notify(SysEvent.SysCode.ENTITY_ADDED, val, shared);
      }

      stat(StatsEvent.createTime, startTime);

      return uer;
    }

    /* Get all the times for this event. - this could be a problem. Need to
       limit the number. Should we do this in chunks, stepping through the
       whole period?
     */

    RecurPeriods rp = RecurUtil.getPeriods(val, getAuthprops().getMaxYears(),
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

      return uer;
    }

    /* We can save the master at this point */
    sess.save(val);

    String stzid = val.getDtstart().getTzid();
    TimeZone stz = null;

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

    boolean dateOnly = val.getDtstart().getDateType();

    /* There appears to be a bug in ical4j in which the first instance gets
     * duplicated. Rather than change that code and run the risk of breaking
     * all recurrences I'll just look for that duplicate.
     */

    String firstRecurrenceId = null;

    for (Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      DateTime edt = p.getEnd();
      if (!dateOnly && (stz != null)) {
        edt.setTimeZone(stz);
      }

      dtval = edt.toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      BwRecurrenceInstance ri = new BwRecurrenceInstance();

      ri.setDtstart(rstart);
      ri.setDtend(rend);
      ri.setRecurrenceId(ri.getDtstart().getDate());
      ri.setMaster(val);

      if (firstRecurrenceId == null) {
        firstRecurrenceId = ri.getRecurrenceId();
      } else if (firstRecurrenceId.equals(ri.getRecurrenceId())) {
        // Skip it
        if (debug) {
          debugMsg("Skipping duplicate recurid " + firstRecurrenceId);
        }

        continue;
      }

      if (recurids != null) {
        /* See if we have a recurrence */
        String rid = ri.getRecurrenceId();
        BwEventProxy ov = recurids.get(rid);

        if (ov != null) {
          if (debug) {
            debugMsg("Add override with recurid " + rid);
          }

          setupDependentEntities(ov);
          addOverride(ov, ri);
          recurids.remove(rid);
        }
      }

      sess.save(ri);
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

    stat(StatsEvent.createTime, startTime);

    return uer;
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#updateEvent(org.bedework.calfacade.BwEvent, java.util.Collection, java.util.Collection, org.bedework.calfacade.util.ChangeTable)
   */
  @Override
  public UpdateEventResult updateEvent(final BwEvent val,
                                       final Collection<BwEventProxy> overrides,
                                       final Collection<BwEventProxy> deletedOverrides) throws CalFacadeException {
    HibSession sess = getSess();
    UpdateEventResult ue = new UpdateEventResult();

    if (!access.checkAccess(val, privWrite, true).getAccessAllowed()) {
      // See if we get write content
      // XXX Is this correct?
      try {
        access.checkAccess(val, privWriteContent, false);
      } catch (CalFacadeException cfe) {
        throwException(cfe);
      }
    }

    BwEventProxy proxy = null;

    if (val instanceof BwEventProxy) {
      proxy = (BwEventProxy)val;
    }

    BwCalendar col = getCollection(val.getColPath());
    boolean shared = col.getShared();

    /* Don't allow name and uid changes for overrides */

    if ((proxy != null) && (proxy.getRef().getOverride())) {
      BwEventAnnotation ann = proxy.getRef();
      BwEvent mstr = ann.getMaster();

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
      CollectionInfo collInf = col.getCollectionInfo();

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
      sess.update(val);

      Collection<BwDbentity<?>> deleted = val.getDeletedEntities();
      if (deleted != null) {
        for (BwDbentity ent: deleted) {
          sess.delete(ent);
        }

        deleted.clear();
      }

      if (val.testRecurring()) {
        /* Check the instances and see if any changes need to be made.
         */
        updateRecurrences(val, ue, overrides, shared);
//      } else {
  //      /* If the event was recurring and now is not remove recurrences */
    //    deleteRecurrences(val, changes);
      }

      // XXX I don't think we want this updateRefs(val);

      if (!val.testRecurring() ||
          (Util.isEmpty(overrides) && Util.isEmpty(deletedOverrides))) {
        notify(SysEvent.SysCode.ENTITY_UPDATED, val, shared);

        return ue;
      }

      if (!Util.isEmpty(overrides)) {
        updateOverrides:
        for (BwEventProxy pxy: overrides) {
          BwEventAnnotation ann = pxy.getRef();

          /* Is this a deleted instance? */

          if (ue.deleted != null) {
            for (BwRecurrenceInstance ri: ue.deleted) {
              if (ri.getRecurrenceId().equals(ann.getRecurrenceId())) {
                continue updateOverrides;
              }
            }
          }

          if (ue.added != null) {
            for (BwRecurrenceInstance ri: ue.added) {
              if (ri.getRecurrenceId().equals(ann.getRecurrenceId())) {
                continue updateOverrides;
              }
            }
          }

          ann.setRecurring(new Boolean(false)); // be safe

          if (ann.getTombstoned() == null) {
            ann.setTombstoned(false); // be safe
          }

          if (!ann.unsaved()) {
            updateProxy(new BwEventProxy(ann));
          } else {
            sess.save(ann);

            /* See if there is an instance for this override
             */
            makeQuery(new String[]{"from ",
                                   BwRecurrenceInstance.class.getName(),
                                   " where master=:master and ",
                                   " recurrenceId=:rid"});

            sess.setEntity("master", val);
            sess.setString("rid", ann.getRecurrenceId());
            BwRecurrenceInstance ri = (BwRecurrenceInstance)sess.getUnique();

            if (ri == null) {
              BwDateTime rid = BwDateTime.fromUTC(ann.getRecurrenceId().length() == 8,
                                                  ann.getRecurrenceId());

              Dur dur = new Dur(val.getDuration());
              BwDateTime end = rid.addDur(dur);

              ri = new BwRecurrenceInstance();

              ri.setDtstart(rid);
              ri.setDtend(end);
              ri.setRecurrenceId(rid.getDate());
              ri.setMaster(val);
              ri.setOverride(ann);

              sess.save(ri);
            } else {
              ri.setOverride(ann);

              sess.update(ri);
            }
          }

          notifyInstanceChange(SysEvent.SysCode.ENTITY_UPDATED, val, shared,
                               ann.getRecurrenceId());
        }
      }

      if (!Util.isEmpty(deletedOverrides)) {
        Collection<String> rids = new ArrayList<String>();
        for (BwEventProxy pxy: deletedOverrides) {
          rids.add(pxy.getRecurrenceId());
        }

        removeInstances(val, rids, ue, deletedOverrides, shared);
      }

      notify(SysEvent.SysCode.ENTITY_UPDATED, val, shared);

      return ue;
    }

    if (!proxy.getChangeFlag()) {
      return ue;
    }

    updateProxy(proxy);
    notify(SysEvent.SysCode.ENTITY_UPDATED, val, shared);

    return ue;
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#deleteEvent(org.bedework.calfacade.BwEvent, boolean, boolean)
   */
  @Override
  public DelEventResult deleteEvent(final BwEvent val,
                                    final boolean scheduling,
                                    final boolean reallyDelete) throws CalFacadeException {
    long startTime = System.currentTimeMillis();
    HibSession sess = getSess();
    DelEventResult der = new DelEventResult(false, 0);
    int desiredAccess;
    boolean shared;

    try {
      BwCalendar col = getEntityCollection(val.getColPath(),
                                           privAny, scheduling, false);
      shared = col.getShared();

      if (!scheduling) {
        desiredAccess = privUnbind;
      } else {
        /* Delete message while tidying up in/outbox.
         * Set desiredAccess to something that works.
         *  */

        CalendarWrapper cw = (CalendarWrapper)col;
        desiredAccess = cw.getLastDesiredAccess();
      }

      access.checkAccess(val, desiredAccess, false);
    } catch (CalFacadeException cfe) {
      sess.rollback();
      throw cfe;
    }

    if (!reallyDelete && val.getTombstoned()) {
      // no-op - just pretend

      der.eventDeleted = true;

      return der;
    }

    if (val.testRecurring() && (val.getRecurrenceId() == null)) {
      // Master event - delete all instances and overrides.
      deleteInstances(val, new UpdateEventResult(), der, shared);

      if (reallyDelete) {
        notify(SysEvent.SysCode.ENTITY_DELETED, val, shared);
        sess.delete(val);
      } else {
        notify(SysEvent.SysCode.ENTITY_TOMBSTONED, val, shared);
        tombstoneEvent(val);
      }

      der.eventDeleted = true;

      stat(StatsEvent.deleteTime, startTime);

      return der;
    }

    if ((val.getRecurrenceId() != null) &&
        (val instanceof BwEventProxy)) {
      /* Deleting a single instance. Delete any overrides, delete the instance
       * and add an exdate to the master.
       */

      BwEventProxy proxy = (BwEventProxy)val;
      BwEventAnnotation ann = proxy.getRef();
      BwEvent master = ann.getMaster();

      /* Fetch the instance so we can delete it */
      makeQuery(new String[]{"from ",
                             BwRecurrenceInstance.class.getName(),
                             " where master=:master and ",
                             " recurrenceId=:rid"});

      sess.setEntity("master", master);
      sess.setString("rid", val.getRecurrenceId());
      BwRecurrenceInstance inst = (BwRecurrenceInstance)sess.getUnique();

      if (inst == null) {
        stat(StatsEvent.deleteTime, startTime);

        return der;
      }

      notify(SysEvent.SysCode.ENTITY_DELETED, val, shared);

      sess.delete(inst);

      if (!ann.unsaved()) {
        //der.alarmsDeleted = deleteAlarms(ann);

        ann.getAttendees().clear();
        sess.delete(ann);
      }

      BwDateTime instDate = inst.getDtstart();

      if (!master.getRdates().remove(instDate)) {
        // Wasn't an rdate event
        master.addExdate(instDate);
      }
      master.updateLastmod();
      sess.update(master);

      der.eventDeleted = true;

      stat(StatsEvent.deleteTime, startTime);

      return der;
    }

    // Single non recurring event.

    BwEvent deletee = val;

    if (val instanceof BwEventProxy) {
      // Deleting an annotation
      deletee = ((BwEventProxy)val).getRef();
    }

    // I think we need something like this -- fixReferringAnnotations(deletee);

    // XXX This could be wrong.
    /* If this is a proxy we should only delete alarmas attached to the
     * proxy - any attached to the underlying event should be left alone.
     */
    //der.alarmsDeleted = deleteAlarms(deletee);

    //sess.delete(sess.merge(deletee));

    if (reallyDelete) {
      notify(SysEvent.SysCode.ENTITY_DELETED, val, shared);
      clearCollection(val.getAttendees());

      sess.delete(deletee);
    } else {
      notify(SysEvent.SysCode.ENTITY_TOMBSTONED, val, shared);

      tombstoneEvent(deletee);
    }

    der.eventDeleted = true;

    stat(StatsEvent.deleteTime, startTime);

    return der;
  }

  @Override
  public void moveEvent(final BwEvent val,
                        final BwCalendar from,
                        final BwCalendar to) throws CalFacadeException {
    deleteTombstoned(to.getPath(), val.getUid());

    val.setColPath(to.getPath());

    BwEvent tombstone = val.cloneTombstone();

    //tombstoneEvent(tombstone);

    HibSession sess = getSess();

    sess.saveOrUpdate(tombstone);

    notifyMove(SysEvent.SysCode.ENTITY_MOVED,
               getHref(tombstone), from.getShared(),
               val, to.getShared());
  }

  /** Remove much of the data associated with the event and then tombstone it.
   *
   * @param val
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  private void tombstoneEvent(final BwEvent val) throws CalFacadeException {
    HibSession sess = getSess();

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

    sess.update(val);
  }

  private void deleteTombstoned(final String colPath,
                                final String uid) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("delete from ");
    sb.append(BwEventObj.class.getName());
    sb.append(" ev where ev.tombstoned = true and ");

    sb.append("ev.colPath = :path and ");
    sb.append("ev.uid = :uid");

    sess.createQuery(sb.toString());

    sess.setString("path", fixPath(colPath));
    sess.setString("uid", uid);

    sess.executeUpdate();
  }

  /** This represents an internal key to an event.
   *
   */
  private static class PrivateInternalEventKey extends InternalEventKey {
    Integer key;

    BwDateTime start;

    BwDateTime end;

    String ownerHref;

    /**
     * @param key
     * @param start
     * @param end
     * @param ownerHref
     */
    @SuppressWarnings("unused")
    public PrivateInternalEventKey(final Integer key,
                                   final BwDateTime start,
                                   final BwDateTime end,
                                   final String ownerHref) {
      this.key = key;
      this.start = start;
      this.end = end;
      this.ownerHref = ownerHref;
    }

    @Override
    public BwDateTime getStart() {
      return start;
    }

    @Override
    public BwDateTime getEnd() {
      return end;
    }

    @Override
    public String getOwnerHref() {
      return ownerHref;
    }

  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#getEventKeysForTzupdate(java.lang.String)
   */
  @Override
  @SuppressWarnings("unchecked")
  public Collection<? extends InternalEventKey> getEventKeysForTzupdate(final String lastmod)
          throws CalFacadeException {
    HibSession sess = getSess();

    if (!cb.getPrincipalInfo().getSuperUser()) {
      sess.rollback();
      throw new CalFacadeAccessException();
    }

    StringBuilder sb = new StringBuilder();

    sb.append("select new org.bedework.calcore.hibernate.CoreEvents$PrivateInternalEventKey(");
    sb.append("ev.id, ev.dtstart, ev.dtend, ev.ownerHref) from ");
    sb.append(BwEventObj.class.getName());
    sb.append(" ev where ev.tombstoned = false and ");

    if (lastmod != null) {
      sb.append("ev.lastmod >= :lastmod and ");
    }

    sb.append("(ev.dtstart.floatFlag=false or ");

    sb.append("ev.dtstart.dateType=false or ");

    sb.append("ev.dtend.floatFlag=false or ");

    sb.append("ev.dtend.dateType=false)");

    sess.createQuery(sb.toString());

    if (lastmod != null) {
      sess.setString("lastmod", lastmod);
    }

    Collection<PrivateInternalEventKey> ids = sess.getList();

    if (debug) {
      trace(" ----------- number ids = " + ids.size());
    }

    return ids;
  }

  /** Get an event given the internal key. Returns null if event no longer
   * exists.
   *
   * @param key
   * @return CoreEventInfo
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  @Override
  public CoreEventInfo getEvent(final InternalEventKey key)
          throws CalFacadeException {
    HibSession sess = getSess();

    if (!cb.getPrincipalInfo().getSuperUser()) {
      sess.rollback();
      throw new CalFacadeAccessException();
    }

    if ((key == null) || !(key instanceof PrivateInternalEventKey)) {
      throwException(CalFacadeException.illegalObjectClass);
    }

    PrivateInternalEventKey ikey = (PrivateInternalEventKey)key;

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwEventObj.class.getName());
    sb.append(" ev where ev.id=:id");

    sess.createQuery(sb.toString());
    sess.setInt("id", ikey.key);

    BwEvent ev = (BwEvent)sess.getUnique();

    if (ev == null) {
      return null;
    }

    CurrentAccess ca = new CurrentAccess(true);

    return new CoreEventInfo(ev, ca);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String token) throws CalFacadeException {
    HibSession sess = getSess();

    if (path == null) {
      sess.rollback();
      throw new CalFacadeBadRequest("Missing path");
    }

    String fpath = fixPath(path);

    BwCalendar col = getCollection(fpath);
    access.checkAccess(col, privAny, false);

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwEvent.class.getName());
    sb.append(" ev where ev.colPath = :path and ");

    if (token != null) {
      sb.append("ev.ctoken is not null and "); // XXX Only because we reused column
      sb.append("ev.ctoken > :token");
    } else {
      // No deleted events for null sync-token
      sb.append("ev.tombstoned = false");
    }

    sess.createQuery(sb.toString());

    sess.setString("path", fpath);

    if (token != null) {
      sess.setString("token", token);
    }

    @SuppressWarnings("unchecked")
    List<BwEvent> evs = sess.getList();

    if (debug) {
      trace(" ----------- number evs = " + evs.size());
    }

    Set<CoreEventInfo> res = new TreeSet<CoreEventInfo>();

    for (BwEvent ev: evs) {
      CurrentAccess ca = new CurrentAccess(true);

      res.add(new CoreEventInfo(ev, ca));
    }

    return res;
  }

  /* ====================================================================
   *                  Admin support
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreEventsI#getChildEntities(java.lang.String, int, int)
   */
  @Override
  @SuppressWarnings("unchecked")
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder("select ev.name from ");
    sb.append(BwEventObj.class.getName());
    sb.append(" ev where ev.colPath=:colPath");
    // No deleted events
    sb.append(" and ev.tombstoned = false");
    sb.append(" order by ev.name");

    sess.createQuery(sb.toString());

    sess.setString("colPath", parentPath);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    List res = sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void getOverrides(final EventsQueryResult eqr,
                            final boolean allOverrides,
                            final Collection<CoreEventInfo> recurringMasters,
                            final HashMap<BwEvent, Collection<CoreEventInfo>> ovMap,
                            final RecurringRetrievalMode recurRetrieval,
                            final boolean freeBusy,
                            final int desiredAccess) throws CalFacadeException {
    Collection<BwEvent> masters = null;

    if (allOverrides) {
      // Limit to supplied masters
      masters = new ArrayList<BwEvent>();
      for (CoreEventInfo cei: recurringMasters) {
        masters.add(cei.getEvent());
      }
    }

    eventsQuery(eqr,
                recurRetrieval.start,
                recurRetrieval.end,
                null, // retrieveListFields
                freeBusy,
                null,
                masters,
                null, // uids
                getOverrides);

    Iterator it = eqr.es.iterator();

    /* Build a map of overrides indexed by their master */
    while (it.hasNext()) {
      BwEventAnnotation override = (BwEventAnnotation)it.next();
      BwEvent mstr = override.getMaster();

      CoreEventInfo mstrCei = postGetEvent(mstr, desiredAccess,
                                         returnResultAlways,
                                         null);
      if (mstrCei == null) {
        continue;
      }

      CoreEventInfo cei = postGetEvent(override, desiredAccess,
                                       returnResultAlways,
                                       eqr.flt);

      if (cei != null) {
        Collection<CoreEventInfo> ovs = ovMap.get(mstr);
        if (ovs == null) {
          ovs = new ArrayList<CoreEventInfo>();
          ovMap.put(mstr, ovs);
        }

        ovs.add(cei);

        if (!allOverrides) {
          recurringMasters.add(mstrCei);
        }
      }
    }
  }

  private void setupDependentEntities(final BwEvent val) throws CalFacadeException {
    // Ensure collections in reasonable state.
    if (val.getAlarms() != null) {
      for (BwAlarm alarm: val.getAlarms()) {
        alarm.setEvent(val);
        alarm.setOwnerHref(getPrincipal().getPrincipalRef());
      }
    }
  }

  /* Called by updateEvent to update a proxied event (annotation) or an
   * override.
   */
  private void updateProxy(final BwEventProxy proxy) throws CalFacadeException {
    HibSession sess = getSess();

    /* if this is a proxy for a recurrence instance of our own event
       then the recurrence instance should point at this override.
       Otherwise we just update the event annotation.
     */
    BwEventAnnotation override = proxy.getRef();
    if (debug) {
      debugMsg("Update override event " + override);
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
      // from the recurrences table
      StringBuilder sb = new StringBuilder();

      sb.append("from ");
      sb.append(BwRecurrenceInstance.class.getName());
      sb.append(" rec ");
      sb.append(" where rec.master=:mstr ");
      sb.append(" and rec.recurrenceId=:rid ");

      sess.createQuery(sb.toString());

      sess.setEntity("mstr", mstr);
      sess.setString("rid", override.getRecurrenceId());

      BwRecurrenceInstance inst = (BwRecurrenceInstance)sess.getUnique();
      if (inst == null) {
        if (debug) {
          debugMsg("Cannot locate instance for " +
                   mstr + "with recurrence id " + override.getRecurrenceId());
        }
        throwException(CalFacadeException.cannotLocateInstance,
                       mstr + "with recurrence id " + override.getRecurrenceId());
      }

      override.setOwnerHref(mstr.getOwnerHref()); // XXX Force owner????
      sess.saveOrUpdate(override);
//      sess.flush();
      if (inst.getOverride() == null) {
        inst.setOverride(override);
        sess.saveOrUpdate(inst);
      }

      /* Update the lastmod on the master event */
      mstr.setDtstamps(getCurrentTimestamp());
      sess.update(mstr);
    } else {
      sess.saveOrUpdate(override);
    }

    proxy.setChangeFlag(false);
  }

  private BwRecurrenceInstance getInstance(final BwEvent master,
                                           final String rid) throws CalFacadeException {
    HibSession sess = getSess();
    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwRecurrenceInstance.class.getName());
    sb.append(" inst ");
    sb.append(" where inst.master=:master ");

    sb.append(" and inst.recurrenceId=:rid ");

    sess.createQuery(sb.toString());

    sess.setEntity("master", master);
    sess.setString("rid", rid);

    return (BwRecurrenceInstance)sess.getUnique();
  }

  /* Get an object which will limit retrieved enties either to the explicitly
   * given date limits or to th edates (if any) given in the call.
   */
  private RecurringRetrievalMode defaultRecurringRetrieval(
        final RecurringRetrievalMode val,
        final BwDateTime start, final BwDateTime end) {
    if ((start == null) && (end == null)) {
      // No change to make
      return val;
    }

    if ((val.start != null) && (val.end != null)) {
      // Fully specified
      return val;
    }

    RecurringRetrievalMode newval = new RecurringRetrievalMode(val.mode,
                                                               val.start,
                                                               val.end);
    if (newval.start == null) {
      newval.start = start;
    }

    if (newval.end == null) {
      newval.end = end;
    }

    return newval;
  }

  /* Return the name of any event which has the same uid
   */
  private String calendarGuidExists(final BwEvent val,
                                    final boolean annotation,
                                    final boolean adding) throws CalFacadeException {
    long startTime = System.currentTimeMillis();
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder("select ev.name from ");

    if (!annotation) {
      sb.append(BwEventObj.class.getName());
    } else {
      sb.append(BwEventAnnotation.class.getName());
    }

    sb.append(" ev where ev.tombstoned = false and ");

    BwEvent testEvent = null;

    if (!adding) {
      if (annotation) {
        if (val instanceof BwEventProxy) {
          BwEventProxy proxy = (BwEventProxy)val;
          BwEventAnnotation ann = proxy.getRef();

          testEvent = ann;
        }
        sb.append("ev.override=false and ");
      } else if (!(val instanceof BwEventProxy)) {
        testEvent = val;
      }
    }

    if (testEvent != null) {
      sb.append("ev<>:event and ");
    }

    sb.append("ev.colPath=:colPath and ");
    sb.append("ev.uid = :uid");

    sess.createQuery(sb.toString());
    /* Change the above to
     *     sess.createNoFlushQuery(sb.toString());
     * and we save about 50% of the cpu for some updates. However we can't do
     * just that. The savings come about in not doing the flush which is
     * expensive - however we need it to ensure we are not getting dup uids.
     *
     * To make this work we would need to accumulate uids for the current
     * transaction in a table and check that as well as the db.
     *
     * It's also the case that a concurrent transaction could add uids and
     * a no-flush call will miss those.
     *
     * We may have to live with it but see if we can't speed up the fush. A lot
     * of the COU ends up in hibernate calling java.lang.Class.getInterfaces
     * which is not meant to be called frequently.
     */


    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    sess.setString("colPath", val.getColPath());
    sess.setString("uid", val.getUid());


    Collection refs = sess.getList();

    String res = null;

    if (refs.size() != 0) {
      res = (String)refs.iterator().next();
    }

    stat(StatsEvent.checkUidTime, startTime);

    return res;
  }

  private boolean calendarNameExists(final BwEvent val,
                                     final boolean annotation,
                                     final boolean adding) throws CalFacadeException {
    long startTime = System.currentTimeMillis();
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder("select count(*) from ");

    if (!annotation) {
      sb.append(BwEventObj.class.getName());
    } else {
      sb.append(BwEventAnnotation.class.getName());
    }

    sb.append(" ev where ev.tombstoned = false and ");

    BwEvent testEvent = null;

    if (!adding) {
      if (annotation) {
        if (val instanceof BwEventProxy) {
          BwEventProxy proxy = (BwEventProxy)val;
          BwEventAnnotation ann = proxy.getRef();

          testEvent = ann;
        }
        sb.append("ev.override=false and ");
      } else if (!(val instanceof BwEventProxy)) {
        testEvent = val;
      }
    }

    if (testEvent != null) {
      sb.append("ev<>:event and ");
    }

    sb.append("ev.colPath=:colPath and ");
    sb.append("ev.name = :name");

    sess.createQuery(sb.toString());
    /* See above note
      sess.createNoFlushQuery(sb.toString());
      */

    if (testEvent != null) {
      sess.setEntity("event", testEvent);
    }

    sess.setString("colPath", val.getColPath());
    sess.setString("name", val.getName());

    Collection refs = sess.getList();

    Object o = refs.iterator().next();

    boolean res;

    /* Apparently some get a Long - others get Integer */
    if (o instanceof Long) {
      Long ct = (Long)o;
      res = ct.longValue() > 0;
    } else {
      Integer ct = (Integer)o;
      res = ct.intValue() > 0;
    }

    stat(StatsEvent.checkNameTime, startTime);

    return res;
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
                           final BwRecurrenceInstance inst) throws CalFacadeException {
    BwEventAnnotation override = proxy.getRef();
    if (override.getOwnerHref() == null) {
      override.setOwnerHref(inst.getMaster().getOwnerHref());
    }
    override.setMaster(inst.getMaster());
    override.setTarget(inst.getMaster());
    override.setOverride(true);
    override.setTombstoned(false);

    getSess().saveOrUpdate(override);
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
                               final UpdateEventResult uc,
                               final DelEventResult der,
                               final boolean shared) throws CalFacadeException {

    HibSession sess = getSess();
    StringBuilder sb = new StringBuilder();

    /* First fire off some delete notifications. */
    sb.append("from ");
    sb.append(BwRecurrenceInstance.class.getName());
    sb.append(" where master=:master");

    sess.createQuery(sb.toString());
    sess.setEntity("master", val);
    Collection current = sess.getList();

    Iterator it = current.iterator();
    while (it.hasNext()) {
      BwRecurrenceInstance ri = (BwRecurrenceInstance)it.next();

      notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED,
                           val, shared,
                           ri.getRecurrenceId());
    }

    /* Delete the instances first as they refer to the overrides */
    sb = new StringBuilder();

    /* SEG:   delete from recurrences recur where */
    sb.append("delete from ");
    sb.append(BwRecurrenceInstance.class.getName());
    sb.append(" where master=:master");

    sess.createQuery(sb.toString());
    sess.setEntity("master", val);
    sess.executeUpdate();

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
  private void updateRecurrences(final BwEvent val,
                                 final UpdateEventResult uc,
                                 final Collection<BwEventProxy> overrides,
                                 final boolean shared) throws CalFacadeException {
    ChangeTable changes = val.getChangeset(currentPrincipal());

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
          addInstances(val, uc, overrides, ent.getRemovedValues(), shared);
        }

        ent = changes.getEntry(PropertyInfoIndex.RDATE);
        if (ent.getAddedValues() != null) {
          // rdates added - add the instances.
          addInstances(val, uc, overrides, ent.getAddedValues(), shared);
        }

        if (ent.getRemovedValues() != null) {
          // rdates removed - remove the instances.
          removeInstances(val, uc, overrides, ent.getRemovedValues(), shared);
        }

        return;
      }
    }

    HibSession sess = getSess();

    Map<String, BwRecurrenceInstance> updated = new HashMap<String, BwRecurrenceInstance>();

    /* Get all the times for this event. - this could be a problem. Need to
       limit the number. Should we do this in chunks, stepping through the
       whole period?
     */

    RecurPeriods rp = RecurUtil.getPeriods(val, getAuthprops().getMaxYears(),
                                           getAuthprops().getMaxInstances());

    if (rp.instances.isEmpty()) {
      // No instances for an alleged recurring event.

      // XXX Mark the master as non-recurring to stop it disappearing
      val.setRecurring(false);
      //throwException(CalFacadeException.noRecurrenceInstances);
    }

    String stzid = val.getDtstart().getTzid();

/*    try {
      val.setLatestDate(Timezones.getUtc(rp.rangeEnd.toString(), stzid));
    } catch (Throwable t) {
      throwException(new CalFacadeException(t));
    } */

    int maxInstances = getAuthprops().getMaxInstances();

    boolean dateOnly = val.getDtstart().getDateType();

    for (Period p: rp.instances) {
      String dtval = p.getStart().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      dtval = p.getEnd().toString();
      if (dateOnly) {
        dtval = dtval.substring(0, 8);
      }

      BwDateTime rend = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

      BwRecurrenceInstance ri = new BwRecurrenceInstance();

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

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwRecurrenceInstance.class.getName());
    sb.append(" where master=:master");

    sess.createQuery(sb.toString());
    sess.setEntity("master", val);
    Collection current = sess.getList();

    Iterator it = current.iterator();
    while (it.hasNext()) {
      BwRecurrenceInstance ri = (BwRecurrenceInstance)it.next();
      BwRecurrenceInstance updri = updated.get(ri.getRecurrenceId());

      if (updri == null) {
        // Not in the new instance set - delete from db
        sess.delete(ri);
        uc.addDeleted(ri);

        notifyInstanceChange(SysEvent.SysCode.ENTITY_DELETED, val, shared,
                             ri.getRecurrenceId());
      } else {
        /* Found instance with same recurrence id. Is the start and end the same
         */
        if (!ri.getDtstart().equals(updri.getDtstart()) ||
            !ri.getDtend().equals(updri.getDtend())) {
          ri.setDtstart(updri.getDtstart());
          ri.setDtend(updri.getDtend());

          sess.update(ri);
          uc.addUpdated(ri);

          notifyInstanceChange(SysEvent.SysCode.ENTITY_UPDATED, val, shared,
                               ri.getRecurrenceId());
        }

        // Remove the entry - we've processed it.
        updated.remove(ri.getRecurrenceId());
      }
    }

    /* updated only contains recurrence ids that don't exist */

    for (BwRecurrenceInstance ri: updated.values()) {
      sess.save(ri);
      uc.addAdded(ri);

      notifyInstanceChange(SysEvent.SysCode.ENTITY_ADDED, val, shared,
                           ri.getRecurrenceId());
    }
  }

  /* Remove instances identified by the Collection of recurrence ids
   */
  private void removeInstances(final BwEvent master,
                               final UpdateEventResult uc,
                               final Collection<BwEventProxy> overrides,
                               final Collection<BwDateTime> rids,
                               final boolean shared) throws CalFacadeException {
    for (BwDateTime dt: rids) {
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
    for (String rid: rids) {
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
      for (BwEventProxy pr: overrides) {
        if (pr.getRecurrenceId() == null) {
          throw new NullPointerException();
        }

        if (pr.getRecurrenceId().equals(rid)) {
          // This one is being deleted
          overrides.remove(pr);
          break;
        }
      }
    }

    BwRecurrenceInstance inst = getInstance(master, rid);
    if (inst != null) {
      getSess().delete(inst);
      uc.addDeleted(inst);
    }
  }

  /* Add instances identified by the Collection of recurrence ids
   */
  private void addInstances(final BwEvent master,
                            final UpdateEventResult uc,
                            final Collection<BwEventProxy> overrides,
                            final Collection rids,
                            final boolean shared) throws CalFacadeException {
    HibSession sess = getSess();
    Dur dur = new Dur(master.getDuration());

    Iterator it = rids.iterator();
    while (it.hasNext()) {
      BwDateTime start = (BwDateTime)it.next();
      BwDateTime end = start.addDur(dur);

      BwRecurrenceInstance ri = new BwRecurrenceInstance();

      ri.setDtstart(start);
      ri.setDtend(end);
      ri.setRecurrenceId(start.getDate());
      ri.setMaster(master);

      if (!Util.isEmpty(overrides)) {
        for (BwEventProxy pxy: overrides) {
          BwEventAnnotation ann = pxy.getRef();

          if (!ann.getRecurrenceId().equals(ri.getRecurrenceId())) {
            continue;
          }

          ann.setRecurring(new Boolean(false)); // be safe

          if (ann.getTombstoned() == null) {
            ann.setTombstoned(false); // be safe
          }

          if (!ann.unsaved()) {
            updateProxy(new BwEventProxy(ann));
          } else {
            sess.save(ann);
          }

          ri.setOverride(ann);
          break;
        }
      }

      sess.save(ri);

      notifyInstanceChange(SysEvent.SysCode.ENTITY_ADDED, master, shared,
                           start.getDate());

      uc.addAdded(ri);
    }
  }

  @SuppressWarnings("unchecked")
  private Collection<BwEventAnnotation> getAnnotations(final BwEvent val,
                                    final boolean overrides) throws CalFacadeException {
    HibSession sess = getSess();
    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwEventAnnotation.class.getName());
//    sb.append(" where target=:target and override=:override");
    sb.append(" where target=:target");

    sess.createQuery(sb.toString());
    sess.setEntity("target", val);
  //  sess.setBool("override", overrides);

    Collection anns = sess.getList();

    if (debug) {
      debugMsg("getAnnotations for event " + val.getId() +
               " overrides=" + overrides +
               " returns " + anns.size());
    }

    return anns;
  }

  private void fixReferringAnnotations(final BwEvent val) throws CalFacadeException {
    /* We may have annotations to annotations so we hunt them all down deleting
     * the leaf entries first.
     */
    HibSession sess = getSess();

    for (BwEventAnnotation ev: getAnnotations(val, false)) {
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
      sess.delete(ev);
    }
  }

  // ENUM
  private static final int getEvents = 0;
  private static final int getAnnotations = 1;
  private static final int getOverrides = 2;
  private static final int getInstances = 3;
  private static final int getInstanceMasters = 4;
  private static final int getAvailables = 5;

  @SuppressWarnings("unchecked")
  private void eventsQuery(final EventsQueryResult eqr,
                           final BwDateTime startDate,
                           final BwDateTime endDate,
                           final List<BwIcalPropertyInfoEntry> retrieveListFields,
                           final boolean freebusy,
                           final BwEvent master,
                           final Collection<BwEvent> masters,
                           final Set<String> uids,
                           final int getWhat) throws CalFacadeException {
    HibSession sess = getSess();

    /* Name of the event in the query */
    final String qevName = "ev";

    /* Name of the entity we test for date terms - might be the instance, might
     * be the event itself.
     */
    String dtentName;

    String qevNameMstr;
    String qevNameSelect = qevName;

    Class cl;
    Class cldt = null;
    boolean testTombstoned = false;

    if (getWhat == getInstances) {
      cl = BwRecurrenceInstance.class;
      qevNameMstr = qevName + ".master";
      dtentName = qevName;
    } else if (getWhat == getInstanceMasters) {
      cl = BwEventObj.class;
      qevNameMstr = qevName;

      cldt = BwRecurrenceInstance.class;
      dtentName = "inst";

      qevNameSelect = qevNameMstr;
    } else if (getWhat == getAnnotations) {
      cl = BwEventAnnotation.class;
      qevNameMstr = qevName;
      dtentName = qevName;
      testTombstoned = true;
    } else if (getWhat == getOverrides) {
      cl = BwEventAnnotation.class;
      qevNameMstr = qevName;
      dtentName = qevName;
      testTombstoned = true;
    } else {
      cl = BwEventObj.class;
      qevNameMstr = qevName;
      dtentName = qevName;
      testTombstoned = true;
    }

    eqr.reset();

    EventQueryBuilder selectClause = new EventQueryBuilder();
    EventQueryBuilder whereClause = new EventQueryBuilder();

    eqr.flt.init(selectClause, whereClause,
                 qevNameMstr, dtentName,
                 (getWhat == getAnnotations) || (getWhat == getOverrides),
                 eqr.suppressFilter);

    eqr.suppressFilter = false;

    /* SEG:   from <class> ev [, <cldt> inst] where */
    selectClause.append("select distinct ");
    selectClause.fields(retrieveListFields, qevNameSelect,
                        getWhat == getAnnotations);
    selectClause.from();
    selectClause.addClass(cl, qevName);

    if (cldt != null) {
      selectClause.append(", ");
      selectClause.addClass(cldt, dtentName);
    }

    eqr.flt.joinPass(retrieveListFields);

    selectClause.append(" where ");

    if (freebusy) {
      whereClause.append(qevNameMstr);
      whereClause.append(".endType <> '");
      whereClause.append(StartEndComponent.endTypeNone);
      whereClause.append("' ");
    }

    if (getWhat == getInstanceMasters) {
      if (freebusy) {
        whereClause.and();
      }

      whereClause.append("(");
      whereClause.append(dtentName);
      whereClause.append(".master = ");
      whereClause.append(qevName);
      whereClause.append(") ");
    }

    if (testTombstoned) {
      whereClause.and();
      whereClause.append(dtentName);
      whereClause.append(".tombstoned=false ");
    }

    /* SEG:   and (<date-ranges>) */
    whereClause.appendDateTerms(dtentName, startDate, endDate, true,
                                (getWhat == getInstances) ||
                                (getWhat == getInstanceMasters));

    if ((getWhat == getEvents) &&
        ((startDate != null) || (endDate != null))) {
      /* Don't retrieve any recurrences master records if we have a date range.
       * We pick these up along with the instances that match the time range.
       */
      whereClause.and();
      whereClause.append(qevName);
      whereClause.append(".recurring = false ");
    }

    boolean doCals = false;

    if (getWhat == getAvailables) {
      whereClause.and();
      whereClause.append("(");
      whereClause.append(qevName);

      whereClause.append(".uid in (:uids)) ");
    }

    if ((getWhat == getInstances) ||
        (getWhat == getOverrides)) {
      if (masters != null) {
        whereClause.and();
        whereClause.append("(");
        whereClause.append(qevName);

        whereClause.append(".master in (:masters)) ");
      } else if (master != null) {
        // We're asking for all instances for this master
        whereClause.and();
        whereClause.append("(");
        whereClause.append(qevName);

        whereClause.append(".master = :master) ");
      } else {
        /* We are being called to pick up the master for instances within the
         * date range.
         */
        doCals = true;
      }

      /* If there is an override, either we already have it or it
       * was overriden somewhere else.
       */
      if (getWhat == getInstances) {
        whereClause.and();
        whereClause.append(qevName);
        whereClause.append(".override is null ");
      }
    } else {
      doCals = true;
    }

    boolean setUser = false;

    eqr.flt.addWhereFilters();

    if (doCals) {
      if (eqr.colPaths == null) {
        // Doesn't count as empty

        if (!eqr.flt.getQueryLimited()) {
          setUser = whereClause.appendPublicOrOwnerTerm(qevNameMstr,
                                                        currentMode,
                                                        cb.getPrincipalInfo().getSuperUser());
        }
      } else if (!eqr.colPaths.isEmpty()) {
        // Doesn't count as empty either

        whereClause.and();
        whereClause.append(" (");

        whereClause.emitCalendarClause(qevNameMstr, eqr.colPaths);
        setUser = false;

        whereClause.append(") ");
      } else {
        // No valid calendars or user to limit search. Disallow
        eqr.es = new TreeSet();
        return;
      }
    }

    if (getWhat == getAnnotations) {
      /* Exclude overrides */

      whereClause.and();
      whereClause.append(" (");
      whereClause.append(qevName);
      whereClause.append(".override = false) ");
    } else if (getWhat == getOverrides) {
      whereClause.and();
      whereClause.append(" (");
      whereClause.append(qevName);
      whereClause.append(".override = true) ");
    }

    /* This ordering may be unnecessary. If so we can remove it and remove
     * dtstart as a required field.
     * /
    whereClause.append(" order by ");
    whereClause.append(qevNameSelect);
    whereClause.append(".dtstart.date");*/

    selectClause.append(whereClause);

    if (debug) {
      trace(selectClause.toString());
    }

    selectClause.createQuery(sess);

    /* XXX Limit result set size - pagination allows something like:
       query.setFirstResult(0);
       query.setMaxResults(10);
       */

    selectClause.setDateTermValues(startDate, endDate);

    if (getWhat == getAvailables) {
      sess.setParameterList("uids", uids);
    } else if ((getWhat == getInstances) ||
               (getWhat == getOverrides)) {
      if (masters != null) {
        sess.setParameterList("masters", masters);
      } else if (master != null) {
        sess.setEntity("master", master);
      }
    }

    if (doCals) {
      selectClause.doCalendarEntities(setUser, getPrincipal(), eqr);
    }

    eqr.flt.parPass(sess);

    //if (debug) {
    //  trace(sess.getQueryString());
    //}

    if (Util.isEmpty(retrieveListFields)) {
      eqr.es = sess.getList();
    } else {
      /* Build an event object from the results. */
      List<Object[]> projRes = sess.getList();

      Collection<BwEvent> evs = new ArrayList<BwEvent>();

      for (Object[] evflds: projRes) {
        evs.add(makeEvent(retrieveListFields, evflds, getWhat));
      }

      eqr.es = evs;
    }

    if (debug) {
      trace("Getting (" + getWhat +
            ") Found " + eqr.es.size() + " entries");
    }
  }

  private BwEvent makeEvent(final List<BwIcalPropertyInfoEntry> retrieveListFields,
                            final Object[] evflds,
                            final int getWhat) throws CalFacadeException {
    BwEvent ev;
    List<BwIcalPropertyInfoEntry> rlf;
    if (getWhat == getAnnotations) {
      ev = new BwEventAnnotation();
      rlf = new ArrayList<>(retrieveListFields.size());
      rlf.addAll(retrieveListFields);
      for (PropertyInfoIndex pi: BwIcalPropertyInfo.requiredAnnotationPindexes) {
        rlf.add(BwIcalPropertyInfo.getPinfo(pi));
      }
    } else {
      ev = new BwEventObj();
      rlf = retrieveListFields;
    }

    /* This could be done with reflection... */

    if (evflds.length != rlf.size()) {
      throw new CalFacadeException("Bad projection result");
    }

    for (int i = 0; i < evflds.length; i++) {
      Object o = evflds[i];

      if (o == null) {
        continue;
      }

      if ((o instanceof Integer) &&
          (((Integer)o) == 0)) {
        continue;
      }

      setEventField(ev, rlf.get(i), o);
    }

    return ev;
  }

  private void setEventField(final BwEvent ev,
                             final BwIcalPropertyInfoEntry ipie,
                             final Object o) throws CalFacadeException {
    try {
      Method m;

      if (ipie.getMultiValued()) {
        m = findAdder(ev, ipie.getAdderName());
      } else {
        m = findSetter(ev, ipie.getDbFieldName());
      }

      Object[] pars = new Object[]{o};

      m.invoke(ev, pars);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private Method findSetter(final Object val, final String name) throws CalFacadeException {
    return findMethod(val, "set", name);
  }

  private Method findAdder(final Object val, final String name) throws CalFacadeException {
    return findMethod(val, "add", name);
  }

  private Method findMethod(final Object val,
                            final String prefix,
                            final String name) throws CalFacadeException {
    String methodName = prefix + name.substring(0, 1).toUpperCase() +
                        name.substring(1);
    Method[] meths = val.getClass().getMethods();
    Method meth = null;

    for (int i = 0; i < meths.length; i++) {
      Method m = meths[i];

      if (m.getName().equals(methodName)) {
        if (meth != null) {
          throw new CalFacadeException("org.bedework.calenv.multiple.setters");
        }
        meth = m;
      }
    }

    if (meth == null) {
      throw new CalFacadeException("org.bedework.unknown.method", name);
    }

    return meth;
  }

  /** Check the master for access and if ok build and return an event
   * proxy.
   *
   * <p>If there is an override we don't need the instance. If there is no
   * override we create an annotation.
   *
   * <p>If checked is non-null will use and update the checked map.
   *
   * @param inst        May be null if we retrieved the override
   * @param override    May be null if we retrieved the instance
   * @param checked
   * @param recurRetrieval
   * @param desiredAccess
   * @param freeBusy
   * @return CoreEventInfo
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  private CoreEventInfo makeProxy(final BwRecurrenceInstance inst,
                                  BwEventAnnotation override,
                                  final CheckMap checked,
                                  final RecurringRetrievalMode recurRetrieval,
                                  final int desiredAccess,
                                  final boolean freeBusy) throws CalFacadeException {
    BwEvent mstr;
    if (inst != null) {
      mstr = inst.getMaster();
    } else {
      mstr = override.getTarget();
    }

    //int res = 0;
    CurrentAccess ca = null;

    if (checked != null) {
      ca = checked.getca(mstr);
      if ((ca != null) && !ca.getAccessAllowed()) {
        // failed
        return null;
      }
    }

    if ((checked == null) || (ca == null)) {
      // untested
      ca = access.checkAccess(mstr, desiredAccess, returnResultAlways);
      if (checked != null) {
        checked.setChecked(mstr, ca);
      }

      if (!ca.getAccessAllowed()) {
        return null;
      }
    }

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

    if (override == null) {
      if (recurRetrieval.mode == Rmode.overrides) {
        // Master and overrides only
        return null;
      }

      /* DORECUR The instance could point to an overrride if the owner of the event
             changed an instance.
       */
      BwEventAnnotation instOverride = inst.getOverride();
      boolean newOverride = true;

      if (instOverride != null) {
//        if (instOverride.getOwner().equals(getUser())) {
          // It's our own override.
          override = instOverride;
          newOverride = false;
  //      } else {
    //      // make a fake one pointing at the owners override
      //    override = new BwEventAnnotation();
        //  override.setTarget(instOverride);
          //override.setMaster(instOverride);
        //}
      } else {
        // make a fake one pointing at the master event
        override = new BwEventAnnotation();

        override.setTarget(mstr);
        override.setMaster(mstr);
      }

      if (newOverride) {
        BwDateTime start = inst.getDtstart();
        BwDateTime end = inst.getDtend();

        override.setDtstart(start);
        override.setDtend(end);
        override.setDuration(BwDateTime.makeDuration(start, end).toString());
        override.setCreatorHref(mstr.getCreatorHref());
        override.setOwnerHref(mstr.getOwnerHref());
        override.setOverride(true);
        override.setName(mstr.getName());
        override.setUid(mstr.getUid());

        override.setRecurrenceId(inst.getRecurrenceId());
      }
    }

    /* At this point we have an event with possible overrides. If this is free
     * busy we need to replace it all with a skeleton event holding only date/time
     * information.
     *
     * We can't do this before I think because we need to allow the user to
     * override the transparency on a particular instance,
     */

    BwEvent proxy = new BwEventProxy(override);

    if (freeBusy) {
      proxy = proxy.makeFreeBusyEvent();
    }

    return new CoreEventInfo(proxy, ca);
  }

  private static class CheckMap extends HashMap<Integer, CurrentAccess> {
    void setChecked(final BwEvent ev, final CurrentAccess ca) {
      put(ev.getId(), ca);
    }

    /* Return null for not found.
     */
    CurrentAccess getca(final BwEvent ev) {
      return get(ev.getId());
    }
  }

  /*
  private int deleteAlarms(BwEvent ev) throws CalFacadeException {
    HibSession sess = getSess();
    sess.namedQuery("deleteEventAlarms");
    sess.setEntity("ev", ev);

    return sess.executeUpdate();
  }*/

  private Collection<CoreEventInfo> postGetEvents(final Collection evs,
                                                  final int desiredAccess,
                                                  final boolean nullForNoAccess,
                                                  final Filters f)
          throws CalFacadeException {
    TreeSet<CoreEventInfo> outevs = new TreeSet<CoreEventInfo>();

    Iterator it = evs.iterator();

    while (it.hasNext()) {
      BwEvent ev = (BwEvent)it.next();

      CoreEventInfo cei = postGetEvent(ev, desiredAccess, nullForNoAccess, f);

      if (cei == null) {
        continue;
      }

      outevs.add(cei);
    }

    return outevs;
  }

  private Collection<CoreEventInfo> buildVavail(final Collection<CoreEventInfo> ceis)
          throws CalFacadeException {
    TreeSet<CoreEventInfo> outevs = new TreeSet<CoreEventInfo>();

    Map<String, CoreEventInfo> vavails = new HashMap<String, CoreEventInfo>();

    List<CoreEventInfo> unclaimed = new ArrayList<CoreEventInfo>();

    for (CoreEventInfo cei: ceis) {
      BwEvent ev = cei.getEvent();

      if (ev.getEntityType() == IcalDefs.entityTypeAvailable) {
        CoreEventInfo vavail = vavails.get(ev.getUid());

        if (vavail != null) {
          vavail.addContainedItem(cei);
        } else {
          unclaimed.add(cei);
        }

        continue;
      }

      if (ev.getEntityType() == IcalDefs.entityTypeVavailability) {
        // Keys are the list of AVAILABLE uids
        for (String auid: ev.getAvailableUids()) {
          vavails.put(auid, cei);
        }
      }

      outevs.add(cei);
    }

    for (CoreEventInfo cei: unclaimed) {
      CoreEventInfo vavail = vavails.get(cei.getEvent().getUid());

      if (vavail != null) {
        vavail.addContainedItem(cei);
        continue;
      }

      /*
         This is an orphaned available object. We should probably retrieve the
         vavailability.
         I guess this could happen if we have a date range query that excludes
         the vavailability?
       */
    }

    return outevs;
  }

  private Collection<CoreEventInfo> makeFreeBusy(final Collection<CoreEventInfo> ceis)
          throws CalFacadeException {
    TreeSet<CoreEventInfo> outevs = new TreeSet<CoreEventInfo>();

    HibSession sess = getSess();

    for (CoreEventInfo cei: ceis) {
      BwEvent ev = cei.getEvent();
      cei.setEvent(ev.makeFreeBusyEvent());
      outevs.add(cei);

      sess.evict(ev);
    }

    return outevs;
  }

  private class RecuridTable extends HashMap<String, BwEventProxy> {
    RecuridTable(final Collection<BwEventProxy> events) {
      for (BwEventProxy ev: events) {
        String rid = ev.getRecurrenceId();
        if (debug) {
          debugMsg("Add override to table with recurid " + rid);
        }

        put(rid, ev);
      }
    }
  }
}
