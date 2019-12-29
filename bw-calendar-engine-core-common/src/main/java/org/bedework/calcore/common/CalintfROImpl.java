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
package org.bedework.calcore.common;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.responses.Response;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.Granulator.EventPeriod;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.events.EntityEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;

import java.sql.Blob;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;
import static org.bedework.calfacade.responses.Response.Status.noAccess;
import static org.bedework.calfacade.responses.Response.Status.notFound;
import static org.bedework.calfacade.responses.Response.Status.ok;

/** Implementation of CalIntf for read-only clients which only interacts with
 * the search engine.
 *
 * <p>We assume this interface is accessing public events or private events.
 *
 * <p>A public object in readonly mode will deliver all public objects
 * within the given constraints, regardless of ownership, e.g all events for
 * given day or all public categories.
 *
 * @author Mike Douglass
 */
@SuppressWarnings("unused")
public class CalintfROImpl extends CalintfBase
        implements AccessUtil.CollectionGetter, PrivilegeDefs {
  private static final BwStats stats = new BwStats();

  private static final CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  private final IfInfo ifInfo = new IfInfo();

  protected static final Map<String, CalintfBase> openIfs = new HashMap<>();

  private CoreEventPropertiesI<BwCategory> categoriesHandler;

  private CoreEventPropertiesI<BwLocation> locationsHandler;

  private CoreEventPropertiesI<BwContact> contactsHandler;

  private Timestamp curTimestamp;

  private final static Object syncher = new Object();

  /* ====================================================================
   *                   initialisation
   * ==================================================================== */

  /** Constructor
   *
   */
  @SuppressWarnings("unused")
  public CalintfROImpl() {
  }

  @Override
  public void initPinfo(final PrincipalInfo principalInfo) throws CalFacadeException {
    super.initPinfo(principalInfo);

    access.setCollectionGetter(this);
  }

  public IfInfo getIfInfo() {
    final long now = System.currentTimeMillis();
    
    ifInfo.setLogid(getLogId());
    ifInfo.setId(getTraceId());
    ifInfo.setDontKill(getDontKill());
    ifInfo.setLastStateTime(getLastStateTime());
    ifInfo.setState(getState());
    ifInfo.setSeconds((now - getStartMillis()) / 1000);
    
    return ifInfo;
  }

  @Override
  public BwStats getStats() throws CalFacadeException {
    if (stats == null) {
      return null;
    }

    return stats;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean getDbStatsEnabled() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void dumpDbStats() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<StatsEntry> getDbStats() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public CalintfInfo getInfo() throws CalFacadeException {
    return info;
  }

  /* ====================================================================
   *                   Misc methods
   * ==================================================================== */

  @Override
  public void open(final FilterParserFetcher filterParserFetcher,
                   final String logId,
                   final Configurations configs,
                   final boolean webMode,
                   final boolean forRestore,
                   final boolean indexRebuild,
                   final boolean publicAdmin,
                   final boolean publicAuth,
                   final boolean publicSubmission,
                   final boolean sessionless,
                   final boolean dontKill) throws CalFacadeException {
    if (isOpen) {
      throw new CalFacadeException("Already open");
    }

    this.filterParserFetcher = filterParserFetcher;
    this.logId = logId;
    isOpen = true;
    this.configs = configs;
    this.forRestore = forRestore;
    this.indexRebuild = indexRebuild;
    this.sessionless = sessionless;
    this.dontKill = dontKill;

    if (access != null) {
      access.open();
    }

    ac = new CIAccessChecker();

    if (publicAuth) {
      currentMode = CalintfDefs.publicAuthMode;
    } else {
      currentMode = CalintfDefs.guestMode;
      readOnlyMode = true;
    }
  }

  @Override
  public void close() throws CalFacadeException {
    closeIndexers();

    if (killed) {
      return;
    }

    if (!isOpen) {
      if (debug()) {
        debug("Close for " + getTraceId() + " closed session");
      }
      return;
    }

    if (debug()) {
      debug("Close for " + getTraceId());
    }
    isOpen = false;
  }

  @Override
  public void beginTransaction() throws CalFacadeException {
    checkOpen();

    if (debug()) {
      debug("Begin transaction for " + getTraceId());
    }

    synchronized (openIfs) {
      /* Add a count to the end of the millisecs timestamp to
         make the key unique.
       */
      long objCount = 0;

      while (true) {
        objKey = objTimestamp.toString() + ":" + objCount;

        if (!openIfs.containsKey(objKey)) {
          openIfs.put(objKey, this);
          break;
        }

        objCount++;
      }
    }

    curTimestamp = new Timestamp(new Date().getTime());
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    try {
      if (killed) {
        return;
      }
      
      checkOpen();

      if (debug()) {
        debug("End transaction for " + getTraceId());
      }
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      synchronized (openIfs) {
        openIfs.remove(objKey);
      }
    }
  }

  @Override
  public void rollbackTransaction() {
  }

  @Override
  public boolean isRolledback() throws CalFacadeException {
    return true;
  }

  @Override
  public void flush() throws CalFacadeException {
  }

  @Override
  public void clear() throws CalFacadeException {
  }

  @Override
  public Collection<? extends Calintf> active() {
    return openIfs.values();
  }

  @Override
  public void kill() {
    super.kill();
  }

  @Override
  public long getStartMillis() {
    return objMillis;
  }

  @Override
  public Timestamp getCurrentTimestamp() {
    // Inc nanos to guarantee different
    int nanos = curTimestamp.getNanos() + 1;
    if (nanos > 999999999) {
      nanos = 0;
    }

    curTimestamp.setNanos(nanos);

    return curTimestamp;
  }

  @Override
  public void reAttach(BwDbentity val) throws CalFacadeException {
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  @Override
  public void changeAccess(final BwShareableDbentity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void defaultAccess(final BwShareableDbentity ent,
                            final AceWho who) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<? extends BwShareableDbentity<?>>
                  checkAccess(final Collection<? extends BwShareableDbentity<?>> ents,
                                         final int desiredAccess,
                                         final boolean alwaysReturn)
                                         throws CalFacadeException {
    return access.checkAccess(ents, desiredAccess, alwaysReturn);
  }

  @Override
  public CurrentAccess checkAccess(final BwShareableDbentity ent,
                                   final int desiredAccess,
                                   final boolean returnResult)
          throws CalFacadeException {
    return access.checkAccess(ent, desiredAccess, returnResult);
  }

  public BwCalendar checkAccess(final CalendarWrapper col,
                         final int desiredAccess,
                         final boolean alwaysReturnResult)
          throws CalFacadeException {
    if (col == null) {
      return null;
    }

    final boolean noAccessNeeded = desiredAccess == privNone;

    final CurrentAccess ca =
            ac.checkAccess(col, desiredAccess,
                           alwaysReturnResult || noAccessNeeded);

    if (!noAccessNeeded && !ca.getAccessAllowed()) {
      return null;
    }

    return col;
  }

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime)
          throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");

    //return entityDao.getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm)
          throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");

    //return entityDao.getEventsByAlarm(alarm);
  }

  /* ====================================================================
   *                       Calendars
   * ==================================================================== */

  @Override
  public void principalChanged() throws CalFacadeException {
    colCache.clear();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) throws CalFacadeException {
    checkOpen();

    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal,
                                             final BwIndexer indexer) throws CalFacadeException {
    checkOpen();

    return getColIndexer(indexer).fetchChildren(cal.getHref());
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) throws CalFacadeException {
    checkOpen();

    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    // Create list of paths so we can detect a loop
    final ArrayList<String> pathElements = new ArrayList<>();
    pathElements.add(val.getPath());

    return resolveAlias(val, resolveSubAlias, freeBusy,
                        pathElements, getColIndexer(indexer));
  }

  @Override
  public List<BwCalendar> findAlias(final String val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) throws CalFacadeException{
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, desiredAccess, alwaysReturnResult);
  }

  @Override
  public BwCalendar getCollectionNoCheck(final String path) throws CalFacadeException {
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, -1, true);
  }

  public BwCalendar getCollection(final String path) throws CalFacadeException {
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, privAny, true);
  }

  @Override
  public BwCalendar getCollection(final String path,
                                  final int desiredAccess,
                                  final boolean alwaysReturnResult) throws CalFacadeException {
    return getCollectionIdx(getColIndexer(), path, desiredAccess, alwaysReturnResult);
  }

  private final static BwCalendar rootCol;

  static {
    rootCol = new BwCalendar();
    rootCol.setPath("/");
    rootCol.setPublick(false);

    rootCol.setOwnerHref(BwPrincipal.publicUserHref);
    rootCol.setCreatorHref(BwPrincipal.publicUserHref);
    rootCol.setAccess(Access.getDefaultPublicAccess());
  }

  @Override
  public BwCalendar getCollectionIdx(final BwIndexer indexer,
                                     final String path,
                                     final int desiredAccess,
                                     final boolean alwaysReturnResult) throws CalFacadeException {
    checkOpen();

    if ("/".equals(path)) {
      return rootCol;
    }

    final GetEntityResponse<BwCalendar> ger =
            getColIndexer(indexer).fetchCol(path, desiredAccess,
                                         PropertyInfoIndex.HREF);

    if (ger.getStatus() == ok) {
      return ger.getEntity();
    }

    if (ger.getStatus() == notFound) {
      return null;
    }

    if (ger.getStatus() == noAccess) {
      if (alwaysReturnResult) {
        return null;
      }

      throw new CalFacadeAccessException();
    }

    throw new CalFacadeException(ger.getMessage());
  }
  
  @Override
  public GetSpecialCalendarResult getSpecialCalendar(final BwIndexer indexer,
                                                     final BwPrincipal owner,
                                       final int calType,
                                       final boolean create,
                                       final int access) throws CalFacadeException {
    final String name = getCalendarNameFromType(calType);
    if (name == null) {
      // Not supported
      return null;
    }

    final List<String> entityTypes = BwCalendar.entityTypes.get(calType);

    final GetSpecialCalendarResult gscr = new GetSpecialCalendarResult();

    if (getPrincipalInfo() == null) {
      warn("No principal info - no special calendar");
      gscr.noUserHome = true;
      return gscr;
    }

    final String pathTo = getPrincipalInfo().getCalendarHomePath(owner);

    if (getCollection(pathTo) == null) {
      gscr.noUserHome = true;
      return gscr;
    }

    gscr.cal = getCollectionIdx(indexer,
                                Util.buildPath(colPathEndsWithSlash,
                                               pathTo, "/", name),
                                access, false);

    if ((gscr.cal != null) || !create) {
      return gscr;
    }

    throw new RuntimeException("Read only version");
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                        final String parentPath) throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void touchCalendar(final String path) throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void touchCalendar(final BwCalendar col) throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateCalendar(final BwCalendar val) throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) throws CalFacadeException {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean isEmpty(final BwCalendar val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addNewCalendars(final BwPrincipal user) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) throws CalFacadeException {
    // Only for reindex
    throw new RuntimeException("Read only version");
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String token) throws CalFacadeException {
    final Collection<BwCalendar> cols;

    final Collection<BwCalendar> icols =
            getColIndexer().fetchChildren(path, false);
    cols = new ArrayList<>();

    for (final BwCalendar col: icols) {
      if (token != null) {
        final String lastmod = token.substring(0, 16);
        final Integer seq = Integer.parseInt(token.substring(17), 16);

        if (col.getLastmod().getTimestamp().compareTo(lastmod) < 0) {
          continue;
        }

        if ((col.getLastmod().getTimestamp().compareTo(lastmod) == 0) &&
                (col.getLastmod().getSequence() <= seq)) {
          continue;
        }
      }

      if ((col.getCalType() != 7) && (col.getCalType() != 8)) {
        continue;
      }

      cols.add(col);
    }

    final Set<BwCalendar> res = new TreeSet<>();

    for (final BwCalendar col: cols) {
      final BwCalendar wcol = wrap(col);
      final CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      res.add(wcol);
    }

    return res;
  }

  @Override
  public boolean testSynchCol(final BwCalendar col,
                              final String token)
          throws CalFacadeException {
    if (token == null) {
      return true;
    }

    final String lastmod = token.substring(0, 16);
    final int seq = Integer.parseInt(token.substring(17), 16);

    final BwCollectionLastmod cl = col.getLastmod();
    final int cmp = cl.getTimestamp().compareTo(lastmod);
    if (cmp > 0) {
      return true;
    }

    if (cmp < 0) {
      return false;
    }

    return cl.getSequence() > seq;
  }

  @Override
  public String getSyncToken(final String path) throws CalFacadeException {
    final BwCalendar thisCol = getCalendar(path, privAny, false);

    if (thisCol == null) {
      return null;
    }

    /* Because we don't have a trailing "/" on the paths the path
       prefix may pull in more than we want. We have to check the path on
       return.

       For example - if path is /a/x - we might get /a/x/y but we might
       also get /a/xxx/y
     */

    final String fpath = fixPath(path); // Removes "/"
    final String fpathSlash = fpath + "/";

    @SuppressWarnings("unchecked")
    final Collection<BwCalendar> cols = getColIndexer().fetchChildrenDeep(fpath);

    String token = thisCol.getLastmod().getTagValue();

    for (final BwCalendar col: cols) {
      final String colPath = col.getPath();

      if (!colPath.equals(fpath) && !colPath.startsWith(fpathSlash)) {
        continue;
      }

      final BwCalendar wcol = wrap(col);
      final CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      final String t = col.getLastmod().getTagValue();

      if (t.compareTo(token) > 0) {
        token = t;
      }
    }

    return token;
  }

  protected String fixPath(final String path) {
    if (path.length() <= 1) {
      return path;
    }

    if (path.endsWith("/")) {
      return path.substring(0, path.length() - 1);
    }

    return path;
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) throws CalFacadeException {
    // Only for updates
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                   Free busy
   * ==================================================================== */

  @Override
  public BwEvent getFreeBusy(final Collection<BwCalendar> cals, final BwPrincipal who,
                             final BwDateTime start, final BwDateTime end,
                             final boolean returnAll,
                             final boolean ignoreTransparency)
          throws CalFacadeException {
    if (who.getKind() != WhoDefs.whoTypeUser) {
      throw new CalFacadeException("Unsupported: non user principal for free-busy");
    }

    final Collection<CoreEventInfo> events = 
            getFreeBusyEntities(cals, start, end, ignoreTransparency);
    final BwEvent fb = new BwEventObj();

    fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);
    fb.setOwnerHref(who.getPrincipalRef());
    fb.setDtstart(start);
    fb.setDtend(end);
    //assignGuid(fb);

    try {
      final TreeSet<EventPeriod> eventPeriods = new TreeSet<>();

      for (final CoreEventInfo ei: events) {
        final BwEvent ev = ei.getEvent();

        // Ignore if times were specified and this event is outside the times

        final BwDateTime estart = ev.getDtstart();
        final BwDateTime eend = ev.getDtend();

        /* Don't report out of the requested period */

        final String dstart;
        final String dend;

        if (estart.before(start)) {
          dstart = start.getDtval();
        } else {
          dstart = estart.getDtval();
        }

        if (eend.after(end)) {
          dend = end.getDtval();
        } else {
          dend = eend.getDtval();
        }

        final DateTime psdt = new DateTime(dstart);
        final DateTime pedt = new DateTime(dend);

        psdt.setUtc(true);
        pedt.setUtc(true);

        int type = BwFreeBusyComponent.typeBusy;

        if (BwEvent.statusTentative.equals(ev.getStatus())) {
          type = BwFreeBusyComponent.typeBusyTentative;
       }

        eventPeriods.add(new EventPeriod(psdt, pedt, type));
      }

      /* iterate through the sorted periods combining them where they are
       adjacent or overlap */

      Period p = null;

      /* For the moment just build a single BwFreeBusyComponent
       */
      BwFreeBusyComponent fbc = null;
      int lastType = 0;

      for (final EventPeriod ep: eventPeriods) {
        if (debug()) {
          debug(ep.toString());
        }

        if (p == null) {
          p = new Period(ep.getStart(), ep.getEnd());
          lastType = ep.getType();
        } else if ((lastType != ep.getType()) || ep.getStart().after(p.getEnd())) {
          // Non adjacent periods
          if (fbc == null) {
            fbc = new BwFreeBusyComponent();
            fbc.setType(lastType);
            fb.addFreeBusyPeriod(fbc);
          }
          fbc.addPeriod(p.getStart(), p.getEnd());

          if (lastType != ep.getType()) {
            fbc = null;
          }

          p = new Period(ep.getStart(), ep.getEnd());
          lastType = ep.getType();
        } else if (ep.getEnd().after(p.getEnd())) {
          // Extend the current period
          p = new Period(p.getStart(), ep.getEnd());
        } // else it falls within the existing period
      }

      if (p != null) {
        if ((fbc == null) || (lastType != fbc.getType())) {
          fbc = new BwFreeBusyComponent();
          fbc.setType(lastType);
          fb.addFreeBusyPeriod(fbc);
        }
        fbc.addPeriod(p.getStart(), p.getEnd());
      }
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new CalFacadeException(t);
    }

    return fb;
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> getEvents(
          final Collection <BwCalendar> calendars,
          final FilterBase filter,
          final BwDateTime startDate,
          final BwDateTime endDate,
          final List<BwIcalPropertyInfoEntry> retrieveList,
          final DeletedState delState,
          final RecurringRetrievalMode rrm,
          final boolean freeBusy) throws CalFacadeException {
    /* Ensure dates are limited explicitly or implicitly */
    final RecurringRetrievalMode recurRetrieval =
            defaultRecurringRetrieval(rrm,
                                      startDate, endDate);

    if (debug()) {
      debug("getEvents for start=" + startDate + " end=" + endDate);
    }

    FilterBase fltr = filter;

    if (!Util.isEmpty(calendars)) {
      FilterBase colfltr = null;
      for (final BwCalendar c: calendars) {
        colfltr = FilterBase.addOrChild(colfltr,
                                        new BwCollectionFilter(null, c));
      }
      fltr = FilterBase.addAndChild(fltr, colfltr);
    }

    int desiredAccess = privRead;
    if (freeBusy) {
      // DORECUR - freebusy events must have enough info for expansion
      desiredAccess = privReadFreeBusy;
    }

    final List<PropertyInfoIndex> properties = new ArrayList<>(2);

    properties.add(PropertyInfoIndex.DTSTART);
    properties.add(PropertyInfoIndex.UTC);

    final List<SortTerm> sort = new ArrayList<>(1);
    sort.add(new SortTerm(properties, true));

    String start = null;
    String end = null;

    if (startDate != null) {
      start = startDate.getDate();
    }

    if (endDate != null) {
      end = endDate.getDate();
    }

    final SearchResult sr =
            getEvIndexer().search(null,   // query
                                    false,
                                    fltr,
                                    sort,
                                    null,  // defaultFilterContext
                                    start,
                                    end,
                                    -1,
                                    delState,
                                    recurRetrieval);

    final List<SearchResultEntry> sres =
            sr.getIndexer().getSearchResult(sr, 0, -1, desiredAccess);
    final TreeSet<CoreEventInfo> ceis = new TreeSet<>();

    for (final SearchResultEntry sre: sres) {
      final Object o = sre.getEntity();

      if (!(o instanceof EventInfo)) {
        continue;
      }

      final EventInfo ei = (EventInfo)o;
      final BwEvent ev = ei.getEvent();

      final CoreEventInfo cei = postGetEvent(ev, null, ei.getCurrentAccess());

      if (cei == null) {
        continue;
      }

      ceis.add(cei);
    }

    return buildVavail(ceis);
  }

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String guid)
           throws CalFacadeException {
    final List<CoreEventInfo> res = new ArrayList<>(1);

    checkOpen();
    final GetEntityResponse<EventInfo> ger =
            getEvIndexer().fetchEvent(colPath, guid);

    if (ger.getStatus() == Response.Status.notFound) {
      return res;
    }

    if (ger.getStatus() != Response.Status.ok) {
      throw new CalFacadeException("Unable to retrieve event: " + ger.getStatus());
    }

    final EventInfo ei = ger.getEntity();

    if (ei == null) {
      return res;
    }

    res.add(postGetEvent(ei));

    return res;
  }

  /* Post processing of event from indexer. Access already checked
   */
  protected CoreEventInfo postGetEvent(final EventInfo ei) throws CalFacadeException {
    if (ei == null) {
      return null;
    }

    final CoreEventInfo cei = new CoreEventInfo(ei.getEvent(),
                                                ei.getCurrentAccess());

    if (!Util.isEmpty(ei.getOverrides())) {
      for (final EventInfo oei: ei.getOverrides()) {
        cei.addOverride(new CoreEventInfo(oei.getEvent(),
                                          oei.getCurrentAccess()));
      }
    }

    if (!Util.isEmpty(ei.getContainedItems())) {
      for (final EventInfo ciei: ei.getContainedItems()) {
        cei.addContainedItem(new CoreEventInfo(ciei.getEvent(),
                                               ciei.getCurrentAccess()));
      }
    }

    return cei;
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void reindex(final EventInfo ei) {
    try {
      final BwEvent ev = ei.getEvent();

      postNotification(
              new EntityEvent(SysEventBase.SysCode.REINDEX_EVENT,
                              ev.getOwnerHref(),
                              ev.getOwnerHref(),
                              ev.getHref(),
                              null));
    } catch (final Throwable t) {
      error(t);
    }
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void moveEvent(final BwEvent val,
                        final BwCalendar from,
                        final BwCalendar to) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String lastmod) throws CalFacadeException {
    throw new RuntimeException("Read only version");

    // TODO - this needs to work return events.getSynchEvents(path, lastmod);
  }

  @Override
  public CoreEventInfo getEvent(final String href)
          throws CalFacadeException {
    checkOpen();
    checkOpen();
    final GetEntityResponse<EventInfo> ger =
            getEvIndexer().fetchEvent(href);

    if (ger.getStatus() == Response.Status.notFound) {
      return null;
    }

    if (ger.getStatus() != Response.Status.ok) {
      throw new CalFacadeException("Unable to retrieve event: " + ger.getStatus());
    }

    final EventInfo ei = ger.getEntity();

    if (ei == null) {
      return null;
    }

    return postGetEvent(ei);
  }

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  @Override
  public void saveOrUpdate(final BwUnversionedDbentity val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public void saveOrUpdate(final BwEventProperty val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  public Blob getBlob(final byte[] val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(final Class<T> cl) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl,
                                           final String colPath) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       filter defs
   * ==================================================================== */

  @Override
  public void save(final BwFilterDef val,
                   final BwPrincipal owner) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal owner) throws CalFacadeException {
    return getIndexer(owner.getPrincipalRef(),
                      BwIndexer.docTypeFilter).fetchFilter(
            makeHref(owner,
                     getSyspars().getUserCalendarRoot(),
                     getSyspars().getBedeworkResourceDirectory(),
                     "filters", name));
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal owner) throws CalFacadeException {
    return getIndexer(owner.getPrincipalRef(),
                      BwIndexer.docTypeFilter).fetchFilters(null, -1);
  }

  @Override
  public void update(final BwFilterDef val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal owner) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwAuthUser getAuthUser(final String href) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public List<BwAuthUser> getAll() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwAuthUser val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return getIndexer(docTypePrincipal).fetchPrincipal(href);
  }

  @Override
  public void saveOrUpdate(final BwPrincipal val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) throws CalFacadeException {
    return getIndexer(BwIndexer.docTypePreferences).fetchPreferences(principalHref);
  }

  @Override
  public void saveOrUpdate(final BwPreferences val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwPreferences val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */
  
  @Override
  public void removeFromAllPrefs(final BwShareableDbentity val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup findGroup(final String account,
                           final boolean admin) throws CalFacadeException {
    final String href;

    if (admin) {
      href = Util.buildPath(true,
                            BwPrincipal.bwadmingroupPrincipalRoot, "/",
                            account);
    } else {
      href = Util.buildPath(true,
                            BwPrincipal.groupPrincipalRoot, "/",
                            account);
    }

    return (BwGroup)getIndexer(docTypePrincipal).fetchPrincipal(href);
  }

  @Override
  public Collection<BwGroup> findGroupParents(final BwGroup group,
                                       final boolean admin) throws CalFacadeException {
    // Only admin?
    throw new RuntimeException("Read only version");
 }
 
  @Override
  public void updateGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void removeGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addMember(final BwGroup group,
                        final BwPrincipal val,
                        final boolean admin) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val,
                           final boolean admin) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwPrincipal> getMembers(final BwGroup group,
                                            final boolean admin) throws CalFacadeException {
    final List<BwPrincipal> members = new ArrayList<>();
    final BwIndexer idx = getIndexer(docTypePrincipal);

    for (final String href: group.getMemberHrefs()) {
      final BwPrincipal pr = idx.fetchPrincipal(href);

      if (pr != null) {
        members.add(pr);
      }
    }
    return members;
  }

  @Override
  public Collection<BwGroup> getAllGroups(final boolean admin) throws CalFacadeException {
    GetEntitiesResponse<BwGroup> resp =
            getIndexer(docTypePrincipal).fetchGroups(admin);

    if (!resp.isOk()) {
      throw new CalFacadeException(resp.getException());
    }

    if (resp.getEntities() == null) {
      return Collections.emptyList();
    }
    return resp.getEntities();
  }

  @Override
  public Collection<BwGroup> getGroups(final BwPrincipal val,
                                       final boolean admin) throws CalFacadeException {
    GetEntitiesResponse<BwGroup> resp =
            getIndexer(docTypePrincipal).fetchGroups(admin,
                                                     val.getHref());

    if (!resp.isOk()) {
      throw new CalFacadeException(resp.getException());
    }

    if (resp.getEntities() == null) {
      return Collections.emptyList();
    }
    return resp.getEntities();
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  @Override
  public BwCalSuite get(final BwAdminGroup group) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }
  
  @Override
  public BwCalSuite getCalSuite(final String name) throws CalFacadeException {
    final String href = Util.buildPath(true, BwPrincipal.calsuitePrincipalRoot,
                                       "/", name);

    final BwCalSuitePrincipal cspr =
            (BwCalSuitePrincipal)getIndexer(
                    docTypePrincipal).fetchPrincipal(href);
    if (cspr == null) {
      return null;
    }

    return cspr.getFrom();
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void saveOrUpdate(final BwCalSuite val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwCalSuite val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       Event properties
   * ==================================================================== */

  @Override
  public BwCategory getCategory(final String uid) throws CalFacadeException {
    try {
      return getIndexer(BwIndexer.docTypeCategory)
              .fetchCat(uid, PropertyInfoIndex.UID);
    } catch (final Throwable t) {
      if (t instanceof CalFacadeException) {
        throw (CalFacadeException)t;
      }

      throw new CalFacadeException(t);
    }
  }

  @Override
  public <T extends BwEventProperty> CoreEventPropertiesI<T> getEvPropsHandler(
          final Class<T> cl) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess) throws CalFacadeException {
    final BwResource res = getIndexer(BwIndexer.docTypeResource).fetchResource(href);
    if (res == null) {
      return null;
    }

    final CurrentAccess ca = checkAccess(res, desiredAccess, true);

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return res;
  }

  @Override
  public void getResourceContent(final BwResource val) throws CalFacadeException {
    val.setContent(getIndexer(BwIndexer.docTypeResourceContent).fetchResourceContent(val.getHref()));
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) throws CalFacadeException {
    String lastmod = null;
    int seq = 0;

    if (forSynch && (token != null)) {
      lastmod = token.substring(0, 16);
      seq = Integer.parseInt(token.substring(17), 16);

    }

    return getIndexer(BwIndexer.docTypeResource).fetchResources(path, lastmod, seq, count);
  }

  @Override
  public void add(final BwResource val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void saveOrUpdate(final BwResource val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void saveOrUpdateContent(final BwResource r,
                                  final BwResourceContent val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwResource val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

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

    final RecurringRetrievalMode newval =
            new RecurringRetrievalMode(val.mode,
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

  private Collection<CoreEventInfo> buildVavail(final Collection<CoreEventInfo> ceis)
          throws CalFacadeException {
    final TreeSet<CoreEventInfo> outevs = new TreeSet<>();

    final Map<String, CoreEventInfo> vavails = new HashMap<>();

    final List<CoreEventInfo> unclaimed = new ArrayList<>();

    for (final CoreEventInfo cei: ceis) {
      final BwEvent ev = cei.getEvent();

      if (ev.getEntityType() == IcalDefs.entityTypeAvailable) {
        final CoreEventInfo vavail = vavails.get(ev.getUid());

        if (vavail != null) {
          vavail.addContainedItem(cei);
        } else {
          unclaimed.add(cei);
        }

        continue;
      }

      if (ev.getEntityType() == IcalDefs.entityTypeVavailability) {
        // Keys are the list of AVAILABLE uids
        for (final String auid: ev.getAvailableUids()) {
          vavails.put(auid, cei);
        }
      }

      outevs.add(cei);
    }

    for (final CoreEventInfo cei: unclaimed) {
      final CoreEventInfo vavail = vavails.get(cei.getEvent().getUid());

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

  private BwCalendar resolveAlias(final BwCalendar val,
                                  final boolean resolveSubAlias,
                                  final boolean freeBusy,
                                  final ArrayList<String> pathElements,
                                  final BwIndexer indexer) throws CalFacadeException {
    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    final BwCalendar c = val.getAliasTarget();
    if (c != null) {
      if (!resolveSubAlias) {
        return c;
      }

      final BwCalendar res = resolveAlias(c, true, freeBusy, pathElements, indexer);
      res.setAliasOrigin(val);

      return res;
    }

    if (val.getDisabled()) {
      return null;
    }

    int desiredAccess = privRead;
    if (freeBusy) {
      desiredAccess = privReadFreeBusy;
    }

    final String path = val.getInternalAliasPath();

    if (pathElements.contains(path)) {
      val.setDisabled(true);
      return null;
    }

    pathElements.add(path);

    //if (debug()) {
    //  debug("Search for calendar \"" + path + "\"");
    //}

    BwCalendar col;

    try {
      col = getCollectionIdx(indexer, path, desiredAccess, false);
    } catch (final CalFacadeAccessException cfae) {
      col = null;
    }

    if (col == null) {
      /* Assume deleted - flag in the subscription if it's ours or a temp.
       */
      if ((val.getId() == CalFacadeDefs.unsavedItemKey) ||
              val.getOwnerHref().equals(getPrincipal().getPrincipalRef())) {
        val.setDisabled(true);
      }

      return null;
    }

    val.setAliasTarget(col);

    if (!resolveSubAlias) {
      col.setAliasOrigin(val);
      return col;
    }

    final BwCalendar res = resolveAlias(col, true, freeBusy, pathElements, indexer);
    res.setAliasOrigin(val);

    return res;
  }

  private List<BwResource> postProcess(final Collection<BwResource> ress) throws CalFacadeException {
    final List<BwResource> resChecked = new ArrayList<>();

    for (final BwResource res: ress) {
      if (checkAccess(res, PrivilegeDefs.privRead, true).getAccessAllowed()) {
        resChecked.add(res);
      }
    }

    return resChecked;
  }

  private Collection<CoreEventInfo> getFreeBusyEntities(final Collection <BwCalendar> cals,
                                         final BwDateTime start, final BwDateTime end,
                                         final boolean ignoreTransparency)
          throws CalFacadeException {
    /* Only events and freebusy for freebusy reports. */
    final FilterBase filter = new OrFilter();
    try {
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "event",
                                                            false));
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "freeAndBusy",
                                                            false));
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    final RecurringRetrievalMode rrm = new RecurringRetrievalMode(
                        Rmode.expanded, start, end);
    final Collection<CoreEventInfo> evs = getEvents(cals, 
                                                    filter, 
                                                    start, 
                                                    end,
                                                    null,
                                                    DeletedState.noDeleted,
                                                    rrm, true);

    // Filter out transparent and cancelled events

    final Collection<CoreEventInfo> events = new TreeSet<>();

    for (final CoreEventInfo cei: evs) {
      final BwEvent ev = cei.getEvent();

      if (!ignoreTransparency &&
          IcalDefs.transparencyTransparent.equals(ev.getPeruserTransparency(this.getPrincipal().getPrincipalRef()))) {
        // Ignore this one.
        continue;
      }

      if (ev.getSuppressed() ||
          BwEvent.statusCancelled.equals(ev.getStatus())) {
        // Ignore this one.
        continue;
      }

      events.add(cei);
    }

    return events;
  }
}
