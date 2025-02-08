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
package org.bedework.calcore.ro;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkBadRequest;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.response.GetEntitiesResponse;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.base.response.Response;
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
import org.bedework.calfacade.CollectionAliases;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.filter.BwCollectionFilter;
import org.bedework.calfacade.filter.SortTerm;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.calfacade.indexing.SearchResult;
import org.bedework.calfacade.indexing.SearchResultEntry;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.EventPeriod;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.database.db.StatsEntry;
import org.bedework.sysevents.events.EntityEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.base.response.Response.Status.noAccess;
import static org.bedework.base.response.Response.Status.notFound;
import static org.bedework.base.response.Response.Status.ok;
import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;

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
  public void initPinfo(final PrincipalInfo principalInfo) {
    super.initPinfo(principalInfo);

    accessUtil.setCollectionGetter(this);
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
  public BwStats getStats() {
    return stats;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean getDbStatsEnabled() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void dumpDbStats() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<StatsEntry> getDbStats() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public CalintfInfo getInfo() {
    return info;
  }

  /* ====================================================================
   *                   Indexing
   * ==================================================================== */

  @Override
  public void indexEntity(final BwUnversionedDbentity<?> entity) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void indexEntityNow(final BwCalendar entity) {
    throw new RuntimeException("Read only version");
  }

  public void indexEntity(final BwIndexer indexer,
                          final BwUnversionedDbentity<?> entity,
                          final boolean forTouch) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                   Misc methods
   * ==================================================================== */

  @Override
  public void open(final FilterParserFetcher filterParserFetcher,
                   final String logId,
                   final Configurations configs,
                   final boolean forRestore,
                   final boolean indexRebuild,
                   final boolean publicAdmin,
                   final boolean publicAuth,
                   final boolean publicSubmission,
                   final boolean authenticated,
                   final boolean sessionless,
                   final boolean dontKill) {
    if (isOpen) {
      throw new BedeworkException("Already open");
    }

    this.filterParserFetcher = filterParserFetcher;
    this.logId = logId;
    isOpen = true;
    this.configs = configs;
    this.forRestore = forRestore;
    this.indexRebuild = indexRebuild;
    this.sessionless = sessionless;
    this.dontKill = dontKill;

    if (accessUtil != null) {
      accessUtil.open();
    }

    ac = new CIAccessChecker();

    publicMode = true;

    if (publicAdmin) {
      currentMode = CalintfDefs.publicAdminMode;
    } else if (publicAuth) {
      currentMode = CalintfDefs.publicAuthMode;
    } else if (publicSubmission) {
      currentMode = CalintfDefs.publicUserMode;
    } else if (authenticated) {
      currentMode = CalintfDefs.userMode;
      publicMode = false;
    } else {
      currentMode = CalintfDefs.guestMode;
    }

  }

  @Override
  public void close() {
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
  public void beginTransaction() {
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

    curTimestamp = new Timestamp(System.currentTimeMillis());
  }

  @Override
  public void endTransaction() {
    try {
      if (killed) {
        return;
      }
      
      checkOpen();

      if (debug()) {
        debug("End transaction for " + getTraceId());
      }
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
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
  public boolean isRolledback() {
    return true;
  }

  @Override
  public void flush() {
  }

  @Override
  public void clear() {
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
  public synchronized Timestamp getCurrentTimestamp() {
    final var oldTime = curTimestamp.getTime();
    final var oldNanos = curTimestamp.getNanos();

    while (true) {
      curTimestamp.setTime(System.currentTimeMillis());
      if (curTimestamp.getTime() > oldTime) {
        break;
      }

      if (curTimestamp.getNanos() > oldNanos) {
        break;
      }

      if (debug()) {
        debug("retry get timestamp");
      }

      try {
        Thread.sleep(1);
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return curTimestamp;
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  @Override
  public void changeAccess(final ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    throw new BedeworkException("Read only version");
  }

  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    throw new BedeworkException("Read only version");
  }

  @Override
  public void defaultAccess(final ShareableEntity ent,
                            final AceWho who) {
    throw new BedeworkException("Read only version");
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) {
    throw new BedeworkException("Read only version");
  }

  @Override
  public Collection<? extends ShareableEntity>
                  checkAccess(final Collection<? extends ShareableEntity> ents,
                                         final int desiredAccess,
                                         final boolean alwaysReturn) {
    return accessUtil.checkAccess(ents, desiredAccess, alwaysReturn);
  }

  @Override
  public CurrentAccess checkAccess(final ShareableEntity ent,
                                   final int desiredAccess,
                                   final boolean returnResult) {
    return accessUtil.checkAccess(ent, desiredAccess, returnResult);
  }

  public BwCalendar checkAccess(final CalendarWrapper col,
                         final int desiredAccess,
                         final boolean alwaysReturnResult) {
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
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    checkOpen();
    throw new RuntimeException("Read only version");

    //return entityDao.getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    checkOpen();
    throw new RuntimeException("Read only version");

    //return entityDao.getEventsByAlarm(alarm);
  }

  /* ====================================================================
   *                       Calendars
   * ==================================================================== */

  @Override
  public void principalChanged() {
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    checkOpen();

    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal,
                                             final BwIndexer indexer) {
    checkOpen();

    return getColIndexer(indexer).fetchChildren(cal.getHref());
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) {
    checkOpen();

    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    // Create list of paths so we can detect a loop
    final CollectionAliasesImpl cai = new CollectionAliasesImpl(val);

    return resolveAlias(val, resolveSubAlias, freeBusy,
                        cai, getColIndexer(indexer));
  }

  @Override
  public GetEntityResponse<CollectionAliases> getAliasInfo(
          final BwCalendar val) {
    final GetEntityResponse<CollectionAliases> res =
            new GetEntityResponse<>();
    final CollectionAliasesImpl cai = new CollectionAliasesImpl(val);

    try {
      resolveAlias(val, true, false,
                   cai, getColIndexer());
    } catch (final BedeworkException be) {
      return Response.error(res, be);
    }

    res.setEntity(cai);
    return res;
  }

  @Override
  public List<BwCalendar> findAlias(final String val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) {
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, desiredAccess, alwaysReturnResult);
  }

  @Override
  public BwCalendar getCollectionNoCheck(final String path) {
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, -1, true);
  }

  public BwCalendar getCollection(final String path) {
    checkOpen();

    return getCollectionIdx(getColIndexer(),
                            path, privAny, true);
  }

  @Override
  public BwCalendar getCollection(final String path,
                                  final int desiredAccess,
                                  final boolean alwaysReturnResult) {
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
                                     final boolean alwaysReturnResult) {
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

      throw new BedeworkAccessException();
    }

    throw new BedeworkException(ger.getMessage());
  }
  
  @Override
  public GetSpecialCalendarResult getSpecialCalendar(
          final BwIndexer indexer,
          final BwPrincipal<?> owner,
          final int calType,
          final boolean create,
          final int access) {
    final String name = getCalendarNameFromType(calType);
    if (name == null) {
      // Not supported
      return null;
    }

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

    final var thePath = Util.buildPath(colPathEndsWithSlash,
                                       pathTo, "/", name);
    gscr.cal = getCollectionIdx(indexer,
                                thePath,
                                access, false);

    if ((gscr.cal != null) || !create) {
      return gscr;
    }

    // Return a fake one
    gscr.cal = new BwCalendar();
    gscr.cal.setName(name);
    gscr.cal.setPath(thePath);
    gscr.cal.setColPath(pathTo);
    gscr.cal.setCreatorHref(owner.getPrincipalRef());
    gscr.cal.setOwnerHref(owner.getPrincipalRef());
    gscr.cal.setCalType(calType);

    return gscr;
//    throw new RuntimeException("Read only version");
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                        final String parentPath) {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void touchCalendar(final String path) {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void touchCalendar(final BwCalendar col) {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateCalendar(final BwCalendar val) {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) {
    checkOpen();
    throw new RuntimeException("Read only version");
  }

  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public boolean isEmpty(final BwCalendar val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addNewCalendars(final BwPrincipal<?> user) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) {
    // Only for reindex
    throw new RuntimeException("Read only version");
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String token) {
    final Collection<BwCalendar> cols;

    final Collection<BwCalendar> icols =
            getColIndexer().fetchChildren(path, false);
    cols = new ArrayList<>();

    for (final BwCalendar col: icols) {
      if (token != null) {
        final String lastmod = token.substring(0, 16);
        final int seq = Integer.parseInt(token.substring(17), 16);

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
                              final String token) {
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
  public String getSyncToken(final String path) {
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
                                             final int count) {
    // Only for updates
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                   Free busy
   * ==================================================================== */

  @Override
  public BwEvent getFreeBusy(final Collection<BwCalendar> cals,
                             final BwPrincipal<?> who,
                             final BwDateTime start, final BwDateTime end,
                             final boolean returnAll,
                             final boolean ignoreTransparency) {
    if (who.getKind() != WhoDefs.whoTypeUser) {
      throw new BedeworkException("Unsupported: non user principal for free-busy");
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
      throw new BedeworkException(t);
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
          final boolean freeBusy) {
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

      if (!(o instanceof final EventInfo ei)) {
        continue;
      }

      final BwEvent ev = ei.getEvent();

      final CoreEventInfo cei = postGetEvent(ev, ei.getCurrentAccess());

      if (cei == null) {
        continue;
      }

      ceis.add(cei);
    }

    return buildVavail(ceis);
  }

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String guid) {
    final List<CoreEventInfo> res = new ArrayList<>(1);

    checkOpen();
    final GetEntitiesResponse<EventInfo> ger =
            getEvIndexer().fetchEvent(colPath, guid);

    if (ger.getStatus() == notFound) {
      return res;
    }

    if (ger.getStatus() != Response.Status.ok) {
      throw new BedeworkException("Unable to retrieve event: " +
                                           ger.getStatus());
    }

    for (final var ei: ger.getEntities()) {
      res.add(postGetEvent(ei));
    }

    return res;
  }

  /* Post processing of event from indexer. Access already checked
   */
  protected CoreEventInfo postGetEvent(final EventInfo ei)  {
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
                                    final boolean rollbackOnError) {
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
  public UpdateEventResult updateEvent(final EventInfo ei) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void moveEvent(final EventInfo ei,
                        final BwCalendar from,
                        final BwCalendar to) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String token) {
    if (path == null) {
      throw new BedeworkBadRequest("Missing path");
    }

    final String fpath = fixPath(path);

    final BwCalendar col = getCollection(fpath);
    ac.checkAccess(col, privAny, false);
    String lastmod = null;
    int seq = 0;

    if (token != null) {
      lastmod = token.substring(0, 16);
      seq = Integer.parseInt(token.substring(17), 16);
    }

    final List<EventInfo> eis =
            getIndexer(BwIndexer.docTypeEvent).fetchEvents(path,
                                                           lastmod,
                                                           seq,
                                                           -1);


    final Set<CoreEventInfo> res = new TreeSet<>();

    for (final EventInfo ei: eis) {
      final CurrentAccess ca = new CurrentAccess(true);

      res.add(new CoreEventInfo(ei.getEvent(), ca));
    }

    return res;
  }

  @Override
  public CoreEventInfo getEvent(final String href) {
    checkOpen();
    final GetEntityResponse<EventInfo> ger =
            getEvIndexer().fetchEvent(href);

    if (ger.getStatus() == notFound) {
      return null;
    }

    if (ger.getStatus() != Response.Status.ok) {
      throw new BedeworkException("Unable to retrieve event: " + ger.getStatus());
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
  public void addRestoredEntity(final BwUnversionedDbentity<?> val) {
    throw new BedeworkException("Read only version");
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public BwUnversionedDbentity<?> merge(final BwUnversionedDbentity<?> val) {
    throw new BedeworkException("Read only version");
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
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       filter defs
   * ==================================================================== */

  @Override
  public void add(final BwFilterDef val,
                  final BwPrincipal<?> owner) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal<?> owner) {
    return getIndexer(owner.getPrincipalRef(),
                      BwIndexer.docTypeFilter).fetchFilter(
            makeHref(owner,
                     BasicSystemProperties.userCalendarRoot,
                     BasicSystemProperties.bedeworkResourceDirectory,
                     "filters", name));
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(
          final BwPrincipal<?> owner) {
    return getIndexer(owner.getPrincipalRef(),
                      BwIndexer.docTypeFilter).fetchFilters(null, -1);
  }

  @Override
  public void update(final BwFilterDef val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal<?> owner) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwAuthUser getAuthUser(final String href) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public List<BwAuthUser> getAllAuthUsers() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwAuthUser val) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return getIndexer(docTypePrincipal).fetchPrincipal(href);
  }

  @Override
  public void add(final BwPrincipal<?> val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void update(final BwPrincipal<?> val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    return getIndexer(BwIndexer.docTypePreferences).fetchPreferences(principalHref);
  }

  @Override
  public void add(final BwPreferences val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void update(final BwPreferences val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwPreferences val) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */
  
  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup<?> findGroup(final String account,
                              final boolean admin) {
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

    return (BwGroup<?>)getIndexer(docTypePrincipal).fetchPrincipal(href);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup<?> group,
                                       final boolean admin) {
    // Only admin?
    throw new RuntimeException("Read only version");
 }
 
  @Override
  public void addGroup(final BwGroup<?> group,
                       final boolean admin) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateGroup(final BwGroup<?> group,
                          final boolean admin) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void removeGroup(final BwGroup<?> group,
                          final boolean admin) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addMember(final BwGroup<?> group,
                        final BwPrincipal<?> val,
                        final boolean admin) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void removeMember(final BwGroup<?> group,
                           final BwPrincipal<?> val,
                           final boolean admin) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public Collection<BwPrincipal<?>> getMembers(
          final BwGroup<?> group,
          final boolean admin) {
    final List<BwPrincipal<?>> members = new ArrayList<>();
    final BwIndexer idx = getIndexer(docTypePrincipal);

    for (final String href: group.getMemberHrefs()) {
      final var pr = idx.fetchPrincipal(href);

      if (pr != null) {
        members.add(pr);
      }
    }
    return members;
  }

  @Override
  public Collection<BwGroup<?>> getAllGroups(final boolean admin) {
    final GetEntitiesResponse<BwGroup<?>> resp =
            getIndexer(docTypePrincipal).fetchGroups(admin);

    if (!resp.isOk()) {
      throw new BedeworkException(resp.getException());
    }

    if (resp.getEntities() == null) {
      return Collections.emptyList();
    }
    return resp.getEntities();
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups() {
    final GetEntitiesResponse<BwAdminGroup> resp =
            getIndexer(docTypePrincipal).fetchAdminGroups();

    if (!resp.isOk()) {
      throw new BedeworkException(resp.getException());
    }

    if (resp.getEntities() == null) {
      return Collections.emptyList();
    }
    return resp.getEntities();
  }

  @Override
  public Collection<BwGroup<?>> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    final GetEntitiesResponse<BwGroup<?>> resp =
            getIndexer(docTypePrincipal).fetchGroups(admin,
                                                     val.getHref());

    if (!resp.isOk()) {
      throw new BedeworkException(resp.getException());
    }

    if (resp.getEntities() == null) {
      return Collections.emptyList();
    }
    return resp.getEntities();
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups(
          final BwPrincipal<?> val) {
    final GetEntitiesResponse<BwAdminGroup> resp =
            getIndexer(docTypePrincipal).fetchAdminGroups(val.getHref());

    if (!resp.isOk()) {
      throw new BedeworkException(resp.getException());
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
  public BwCalSuite get(final BwAdminGroup group) {
    throw new RuntimeException("Read only version");
  }
  
  @Override
  public BwCalSuite getCalSuite(final String name) {
    final String href = Util.buildPath(true, BwPrincipal.calsuitePrincipalRoot,
                                       "/", name);

    return (BwCalSuite)getIndexer(
            docTypePrincipal).fetchPrincipal(href);
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void add(final BwCalSuite val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void update(final BwCalSuite val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwCalSuite val) {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                       Event properties
   * ==================================================================== */

  @Override
  public BwCategory getCategory(final String uid) {
    try {
      return getIndexer(BwIndexer.docTypeCategory)
              .fetchCat(uid, PropertyInfoIndex.UID);
    } catch (final Throwable t) {
      if (t instanceof BedeworkException) {
        throw (BedeworkException)t;
      }

      throw new BedeworkException(t);
    }
  }

  @Override
  public <T extends BwEventProperty<?>> CoreEventPropertiesI<T> getEvPropsHandler(
          final Class<T> cl) {
    throw new BedeworkException("Read only version");
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  @Override
  public GetEntityResponse<BwResource> fetchResource(final String href,
                                                     final int desiredAccess) {
    final GetEntityResponse<BwResource> resp = new GetEntityResponse<>();

    try {
      final BwResource res =
              getIndexer(BwIndexer.docTypeResource)
                      .fetchResource(href);
      if (res == null) {
        return Response.notFound(resp);
      }

      final CurrentAccess ca = checkAccess(res, desiredAccess, true);

      if (!ca.getAccessAllowed()) {
        return Response.notOk(resp, Response.Status.forbidden);
      }

      resp.setEntity(res);
      return resp;
    } catch (final BedeworkException be) {
      return Response.error(resp, be);
    }
  }

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess) {
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
  public void getResourceContent(final BwResource val) {
    val.setContent(getIndexer(BwIndexer.docTypeResourceContent).fetchResourceContent(val.getHref()));
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) {
    String lastmod = null;
    int seq = 0;

    if (forSynch && (token != null)) {
      lastmod = token.substring(0, 16);
      seq = Integer.parseInt(token.substring(17), 16);

    }

    return getIndexer(BwIndexer.docTypeResource).fetchResources(path, lastmod, seq, count);
  }

  @Override
  public void add(final BwResource val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void update(final BwResource val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void updateContent(final BwResource r,
                            final BwResourceContent val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteResource(final String href) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void delete(final BwResource val) {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) {
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

  private Collection<CoreEventInfo> buildVavail(
          final Collection<CoreEventInfo> ceis) {
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

  /** Recursively follow chain of aliases
   *
   * @param val to resolve
   * @param resolveSubAlias true to continue down chain
   * @param freeBusy true for a freebusy call (changes access)
   * @param cai for information on chain of aliases
   * @param indexer we use this one
   * @return targeted collection
   */
  private BwCalendar resolveAlias(final BwCalendar val,
                                  final boolean resolveSubAlias,
                                  final boolean freeBusy,
                                  final CollectionAliasesImpl cai,
                                  final BwIndexer indexer) {
    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    final BwCalendar c = val.getAliasTarget();
    if (c != null) {
      // Already fetched target

      if (!cai.addCollection(c) || !resolveSubAlias) {
        return c;
      }

      final BwCalendar res = resolveAlias(c, true,
                                          freeBusy, cai,
                                          indexer);
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

    BwCalendar col;

    try {
      col = getCollectionIdx(indexer,
                             val.getInternalAliasPath(),
                             desiredAccess, false);
    } catch (final BedeworkAccessException ignored) {
      col = null;
    }

    if (col == null) {
      /* Assume deleted - flag in the subscription if it's ours or a temp.
       */
      if (val.unsaved() ||
              val.getOwnerHref().equals(getPrincipal().getPrincipalRef())) {
        val.setDisabled(true);
      }

      cai.markBad(val);

      return null;
    }

    if (!cai.addCollection(col)) {
      return null;
    }

    val.setAliasTarget(col);

    if (!resolveSubAlias) {
      col.setAliasOrigin(val);
      return col;
    }

    final BwCalendar res = resolveAlias(col, true,
                                        freeBusy,
                                        cai, indexer);
    res.setAliasOrigin(val);

    return res;
  }

  private List<BwResource> postProcess(final Collection<BwResource> ress) {
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
                                         final boolean ignoreTransparency) {
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
      throw new BedeworkException(t);
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
