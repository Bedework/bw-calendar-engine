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

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfROImpl;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwGroupEntry;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAdminGroupEntry;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.util.misc.Util;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;

import java.io.StringReader;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static java.lang.String.format;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;

/** Implementation of CalIntf which uses hibernate as its persistance engine.
 *
 * <p>We assume this interface is accessing public events or private events.
 * In either case it is assumed to be read/write, though is up to the caller
 * to set the appropriate access.
 *
 * <p>Write access to public objects may be restricted to only those owned
 * by the supplied owner. It is up to the caller to determine the setting for
 * the modifyAll flag.
 *
 * <p>The following methods always work within the above context, e.g. 'all'
 * for an object initialised for public access means all public objects of
 * a requested class. For a personal object it means all objects owned by
 * the current user.
 *
 * <p>Currently some classes are only available as public objects. This
 * might change.
 *
 * <p>A public object in readonly mode will deliver all public objects
 * within the given constraints, regardless of ownership, e.g all events for
 * given day or all public categories.
 *
 * <p>A public object in read/write will enforce ownership on display and
 * on update. This might require a client to obtain two or more objects to
 * get the appropriate behaviour. For example, an admin client will only
 * allow update of events owned by the current user but must display all
 * public categories for use.
 *
 * @author Mike Douglass   douglm rpi.edu
 */
@SuppressWarnings("unused")
public class CalintfImpl extends CalintfROImpl {
  private static final BwStats stats = new BwStats();

  private static final CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  private final IfInfo ifInfo = new IfInfo();

  private EntityDAO entityDao;

  private PrincipalsAndPrefsDAO principalsAndPrefs;

  private CoreEvents events;

  private CoreCalendars calendars;

  private CoreResources resources;

  private FilterDefsDAO filterDefs;

  private CoreEventPropertiesI<BwCategory> categoriesHandler;

  private CoreEventPropertiesI<BwLocation> locationsHandler;

  private CoreEventPropertiesI<BwContact> contactsHandler;
  
  private final Map<String, DAOBase> daos = new HashMap<>();

  /* Prevent updates.
   */
  //sprivate boolean readOnly;

  /** Current hibernate session - exists only across one user interaction
   */
  private HibSession sess;

  private Timestamp curTimestamp;

  /** We make this static for this implementation so that there is only one
   * SessionFactory per server for the calendar.
   *
   * <p>static fields used this way are illegal in the j2ee specification
   * though we might get away with it here as the session factory only
   * contains parsed mappings for the calendar configuration. This should
   * be the same for any machine in a cluster so it might work OK.
   *
   * <p>It might be better to find some other approach for the j2ee world.
   */
  private static SessionFactory sessionFactory;
  private static Statistics dbStats;
  
  private final static Object syncher = new Object();

  static class IndexEntry {
    final BwIndexer indexer;
    final BwUnversionedDbentity<?> entity;
    boolean forTouch;

    IndexEntry(final BwIndexer indexer,
               final BwUnversionedDbentity<?> entity,
               final boolean forTouch) {
      this.indexer = indexer;
      this.entity = entity;
      this.forTouch = forTouch;
    }

    public String getKey() {
      return indexer.getDocType() + "-" + entity.getHref();
    }

    public int hashCode() {
      return entity.getHref().hashCode();
    }

    public boolean equals(final Object o) {
      if (!(o instanceof IndexEntry)) {
        return false;
      }

      return entity.getHref().equals(((IndexEntry)o).entity.getHref());
    }
  }

  protected Map<String, IndexEntry> awaitingIndex = new HashMap<>();

  /* ====================================================================
   *                   initialisation
   * ==================================================================== */

  /** Constructor
   *
   */
  @SuppressWarnings("unused")
  public CalintfImpl() {
  }

  @Override
  public void initPinfo(final PrincipalInfo principalInfo) {
    super.initPinfo(principalInfo);

    final AuthProperties authProps;

    if (readOnlyMode) {
      authProps = configs.getUnauthenticatedAuthProperties();
    } else {
      authProps = configs.getAuthenticatedAuthProperties();
    }

    events = new CoreEvents(sess, this,
                            ac, authProps, sessionless);

    calendars = new CoreCalendars(sess, this,
                                  ac, readOnlyMode, sessionless);

    resources = new CoreResources(sess, this,
                                  ac, readOnlyMode, sessionless);

    access.setCollectionGetter(calendars);
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

  protected Map<String, DAOBase> getDaos() {
    return daos;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) {
    if (!enable && (dbStats == null)) {
      return;
    }

    if (dbStats == null) {
      dbStats = getSessionFactory().getStatistics();
    }

    dbStats.setStatisticsEnabled(enable);
  }

  public void registerDao(final DAOBase dao) {
    final DAOBase entry = getDaos().get(dao.getName());

    if (entry != null) {
      error("******************************************\n" +
                              "dao: " + dao.getName() + " already registered");
    }
    getDaos().put(dao.getName(), dao);
  }

  @Override
  public boolean getDbStatsEnabled() {
    return dbStats != null && dbStats.isStatisticsEnabled();

  }

  @Override
  public void dumpDbStats() {
    DbStatistics.dumpStats(dbStats);
  }

  @Override
  public Collection<StatsEntry> getDbStats() {
    return DbStatistics.getStats(dbStats);
  }

  /* ====================================================================
   *                   Indexing
   * ==================================================================== */

  public void closeIndexers() {
    try {
      if (!awaitingIndex.isEmpty()) {
        final var vals = awaitingIndex.values();
        final var sz = vals.size();
        var ct = 1;

        for (final IndexEntry ie : vals) {
          try {
            ie.indexer.indexEntity(ie.entity,
                                   ct == sz,
                                   ie.forTouch); // wait
            ct++;
          } catch (final BedeworkException be) {
            if (debug()) {
              error(be);
            }
            throw be;
          }
        }
      }
    } finally {
      awaitingIndex.clear();

      super.closeIndexers();
    }
  }

  @Override
  public void indexEntity(final BwUnversionedDbentity<?> entity) {
    indexEntity(getIndexer(entity), entity, false);
  }

  @Override
  public void indexEntityNow(final BwCalendar entity) {
    indexEntity(getIndexer(entity), entity, true);
  }

  public void indexEntity(final BwIndexer indexer,
                          final BwUnversionedDbentity<?> entity,
                          final boolean forTouch) {
    //indexer.indexEntity(entity, wait);

    final var ie = new IndexEntry(indexer, entity, forTouch);
    final var prevEntry = awaitingIndex.put(ie.getKey(), ie);
    if (forTouch && (prevEntry != null) && !prevEntry.forTouch) {
      ie.forTouch = false;
    }
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
    final long start = System.currentTimeMillis();
    super.open(filterParserFetcher, logId, configs,
               forRestore, indexRebuild,
               publicAdmin, publicAuth, publicSubmission,
               authenticated, sessionless, dontKill);
    if (trace()) {
      trace(format("CalintfImpl.open after super.open() %s",
                   System.currentTimeMillis() - start));
    }

    if (sess != null) {
      warn("Session is not null. Will close");
      close();
    }

    if (sess == null) {
      if (debug()) {
        debug("New hibernate session for " + getTraceId());
      }
      sess = new HibSessionImpl();
      sess.init(getSessionFactory());
      if (debug()) {
        debug("Open session for " + getTraceId());
      }
    }

    if (entityDao == null) {
      entityDao = new EntityDAO(sess);
      registerDao(entityDao);
    }

    if (principalsAndPrefs == null) {
      principalsAndPrefs = new PrincipalsAndPrefsDAO(sess);
      registerDao(principalsAndPrefs);
    }

    if (filterDefs == null) {
      filterDefs = new FilterDefsDAO(sess);
      registerDao(filterDefs);
    }
    
    for (final DAOBase dao: daos.values()) {
      dao.setSess(sess);
    }
  }

  @Override
  public synchronized void close() {
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

    try {
      try {
        if (sess != null) {
          if (sess.rolledback()) {
            sess.close();
            sess = null;
            clearNotifications();
            return;
          }

          if (sess.transactionStarted()) {
            sess.rollback();
            clearNotifications();
          }

          sess.close();
          sess = null;
        }
      } catch (final Throwable t) {
        try {
          if (sess != null) {
            sess.close();
          }
        } catch (final Throwable ignored) {}
        sess = null; // Discard on error
      } finally {
        isOpen = false;
      }

      if (access != null) {
        access.close();
      }
    } finally {
      flushNotifications();
    }
  }

  @Override
  public void beginTransaction() {
    super.beginTransaction();
    sess.beginTransaction();

    curTimestamp = sess.getCurrentTimestamp(BwSystem.class);

    if (calendars != null) {
      calendars.startTransaction();
    }
  }

  @Override
  public void endTransaction() {
    var endedOk = false;

    final long start = System.currentTimeMillis();
    super.endTransaction();
    if (trace()) {
      trace(format("CalintImpl.endTransaction after super.endTransaction() %s",
                   System.currentTimeMillis() - start));
    }

    try {
      if (calendars != null) {
        calendars.endTransaction();
        if (trace()) {
          trace(format("CalintImpl.endTransaction after calendars.endTransaction() %s",
                       System.currentTimeMillis() - start));
        }
      }

      if (!sess.rolledback()) {
        sess.commit();
        if (trace()) {
          trace(format("CalintImpl.endTransaction after sess.commit() %s",
                       System.currentTimeMillis() - start));
        }
      }

      if (!indexRebuild) {
        getIndexer(docTypeEvent).markTransaction();
        if (trace()) {
          trace(format("CalintImpl.endTransaction after indexer.markTransaction() %s",
                       System.currentTimeMillis() - start));
        }
      }

      closeIndexers();

      endedOk = true;
    } catch (final BedeworkException be) {
      if (sess != null) {
        sess.rollback();
      }

      throw be;
    } catch (final Throwable t) {
      if (sess != null) {
        sess.rollback();
      }

      throw new BedeworkException(t);
    } finally {
      if (!endedOk) {
        awaitingIndex.clear();
      }

      synchronized (openIfs) {
        openIfs.remove(objKey);
      }
      if (trace()) {
        trace(format("CalintImpl.endTransaction after openIfs removed %s",
                     System.currentTimeMillis() - start));
      }
    }
  }

  @Override
  public void rollbackTransaction() {
    try {
      awaitingIndex.clear();

      if (killed) {
        return;
      }

      try {
        checkOpen();
      } catch (final Throwable ignored) {
        return;
      }

      try {
        sess.rollback();
      } catch (final Throwable ignored) {
      }
    } finally {
      clearNotifications();
      synchronized (openIfs) {
        openIfs.remove(objKey);
      }
    }
  }

  @Override
  public boolean isRolledback() {
    //noinspection SingleStatementInBlock
    if (killed) {
      return true;
    }

    return isOpen && sess.rolledback();
  }

  @Override
  public void flush() {
    if (killed) {
      return;
    }

    if (debug()) {
      getLogger().debug("flush for " + getTraceId());
    }

    if (sess.isOpen()) {
      sess.flush();
    }
  }

  @Override
  public void clear() {
    if (killed) {
      return;
    }

    if (debug()) {
      getLogger().debug("clear for " + getTraceId());
    }

    if (sess.isOpen()) {
      sess.clear();
    }
  }

  @Override
  public void kill() {
    try {
      rollbackTransaction();
    } catch (final Throwable t) {
      warn("Exception on rollback for kill: " + t.getMessage());
    }
    
    close();

    super.kill();
  }

  /* ==============================================================
   *                   Access
   * ============================================================== */

  @Override
  public void changeAccess(final ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    if (ent instanceof BwCalendar) {
      changeAccess((BwCalendar)ent, aces, replaceAll);
      return;
    }
    checkOpen();
    access.changeAccess(ent, aces, replaceAll);
    entityDao.update((BwUnversionedDbentity<?>)ent);
  }

  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    checkOpen();
    calendars.changeAccess(cal, aces, replaceAll);
  }

  @Override
  public void defaultAccess(final ShareableEntity ent,
                            final AceWho who) {
    if (ent instanceof BwCalendar) {
      defaultAccess((BwCalendar)ent, who);
      return;
    }
    checkOpen();
    checkAccess(ent, privWriteAcl, false);
    access.defaultAccess(ent, who);
    entityDao.update((BwUnversionedDbentity<?>)ent);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) {
    checkOpen();
    calendars.defaultAccess(cal, who);
  }

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    checkOpen();

    return entityDao.getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    checkOpen();

    return entityDao.getEventsByAlarm(alarm);
  }

  /* ====================================================================
   *                       Calendars
   * ==================================================================== */

  @Override
  public void principalChanged() {
    calendars.principalChanged();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    checkOpen();

    return calendars.getSynchInfo(path, token);
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal,
                                             final BwIndexer indexer) {
    checkOpen();

    return calendars.getCalendars(cal, indexer);
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) {
    checkOpen();

    return calendars.resolveAlias(val, resolveSubAlias,
                                  freeBusy, indexer);
  }

  @Override
  public List<BwCalendar> findAlias(final String val) {
    checkOpen();

    return calendars.findAlias(val);
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) {
    checkOpen();

    return calendars.getCalendar(path, desiredAccess, alwaysReturnResult);
  }

  @Override
  public GetSpecialCalendarResult getSpecialCalendar(
          final BwIndexer indexer,
          final BwPrincipal<?> owner,
          final int calType,
          final boolean create,
          final int access) {
    return calendars.getSpecialCalendar(indexer,
                                        owner, calType, create, access);
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                                final String parentPath) {
    checkOpen();

    return calendars.add(val, parentPath);
  }

  @Override
  public void touchCalendar(final String path) {
    checkOpen();
    calendars.touchCalendar(path);
  }

  @Override
  public void touchCalendar(final BwCalendar col) {
    checkOpen();
    calendars.touchCalendar(col);
  }

  @Override
  public void updateCalendar(final BwCalendar val) {
    checkOpen();
    calendars.updateCalendar(val);
  }

  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) {
    checkOpen();
    calendars.renameCalendar(val, newName);
  }

  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) {
    checkOpen();
    calendars.moveCalendar(val, newParent);
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete) {
    checkOpen();

    return calendars.deleteCalendar(val, reallyDelete);
  }

  @Override
  public boolean isEmpty(final BwCalendar val) {
    checkOpen();

    return calendars.isEmpty(val);
  }

  @Override
  public void addNewCalendars(final BwPrincipal<?> user) {
    checkOpen();

    calendars.addNewCalendars(user);
    entityDao.update(user);
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) {
    checkOpen();

    return calendars.getChildCollections(parentPath, start, count);
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String lastmod) {
    return calendars.getSynchCols(path, lastmod);
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) {
    checkOpen();

    return events.getChildEntities(parentPath, start, count);
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String guid) {
    checkOpen();
    return events.getEvent(colPath, guid);
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) {
    checkOpen();
    final UpdateEventResult uer = 
            events.addEvent(ei, scheduling,
                            rollbackOnError);

    if (!forRestore && uer.addedUpdated) {
      calendars.touchCalendar(ei.getEvent().getColPath());
    }

    return uer;
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) {
    checkOpen();
    final UpdateEventResult ue;

    try {
      calendars.touchCalendar(ei.getEvent().getColPath());

      ue = events.updateEvent(ei);
    } catch (final BedeworkException be) {
      rollbackTransaction();
      reindex(ei);
      throw be;
    }

    return ue;
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) {
    checkOpen();
    final String colPath = ei.getEvent().getColPath();
    try {
      try {
        return events.deleteEvent(ei, scheduling, reallyDelete);
      } catch (final BedeworkException be) {
        rollbackTransaction();
        reindex(ei);

        throw be;
      }
    } finally {
      calendars.touchCalendar(colPath);
    }
  }

  @Override
  public void moveEvent(final EventInfo ei,
                        final BwCalendar from,
                        final BwCalendar to) {
    checkOpen();
    events.moveEvent(ei, from, to);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String lastmod) {
    return events.getSynchEvents(path, lastmod);
  }

  @Override
  public CoreEventInfo getEvent(final String href) {
    checkOpen();
    return events.getEvent(href);
  }

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  @Override
  public void add(final BwUnversionedDbentity<?> val) {
    entityDao.add(val);
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public BwUnversionedDbentity<?> merge(final BwUnversionedDbentity<?> val) {
    return entityDao.merge(val);
  }

  private class ObjectIterator<T> implements Iterator<T> {
    protected final String className;
    protected final Class<T> cl;
    protected final String colPath;
    protected final String ownerHref;
    protected final boolean publicAdmin;
    protected List<?> batch;
    protected int index;
    protected boolean done;
    protected int start;
    protected final int batchSize = 100;

    private ObjectIterator(final Class<T> cl) {
      this(cl, null, null, false, 0);
    }

    private ObjectIterator(final Class<T> cl,
                           final String colPath) {
      this(cl, colPath, null, false, 0);
    }

    private ObjectIterator(final Class<T> cl,
                           final String colPath,
                           final String ownerHref,
                           final boolean publicAdmin,
                           final int start) {
      this.className = cl.getName();
      this.cl = cl;
      this.colPath = colPath;
      this.ownerHref = ownerHref;
      this.publicAdmin = publicAdmin;
      this.start = start;
    }

    @Override
    public boolean hasNext() {
      return more();
    }

    @Override
    public synchronized T next() {
      if (!more()) {
        return null;
      }

      index++;
      return (T)batch.get(index - 1);
    }

    @Override
    public void remove() {
      throw new RuntimeException("Forbidden");
    }

    protected synchronized boolean more() {
      if (done) {
        return false;
      }

      if ((batch == null) || (index == batch.size())) {
        nextBatch();
      }

      return !done;
    }

    protected void nextBatch() {
      try {
        String query = "from " + className;

        boolean doneWhere = false;

        if (colPath != null) {
          query += " where colPath=:colPath";
          doneWhere = true;
        }

        if ((ownerHref != null) | publicAdmin) {
          if (!doneWhere) {
            query += " where";
            doneWhere = true;
          } else {
            query += " and";
          }
          query += " ownerHref=:ownerHref";
        }

        sess.createQuery(query);

        if (colPath != null) {
          sess.setString("colPath", colPath);
        }

        if (publicAdmin) {
          sess.setString("ownerHref", BwPrincipal.publicUserHref);
        } else if (ownerHref != null) {
          sess.setString("ownerHref", ownerHref);
        }

        sess.setFirstResult(start);
        sess.setMaxResults(batchSize);

        start += batchSize;

        batch = sess.getList();
        index = 0;

        if (Util.isEmpty(batch)) {
          done = true;
        }
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }
  }
  
  private class EventHrefIterator extends ObjectIterator {
    private EventHrefIterator(final int start) {
      super(BwEventObj.class, null, null, false, start);
    }

    @Override
    public synchronized Object next() {
      if (!more()) {
        return null;
      }

      final Object[] pathName = (Object[])batch.get(index);
      index++;
      
      if ((pathName.length != 2) || 
              (!(pathName[0] instanceof String)) ||
              (!(pathName[1] instanceof String))) {
        throw new RuntimeException("Expected 2 strings");
      }
      
      return pathName[0] + "/" + pathName[1];
    }

    @Override
    protected void nextBatch() {
      try {
        sess.createQuery("select colPath, name from " + className + 
                                 " order by dtstart.dtval desc");

        sess.setFirstResult(start);
        sess.setMaxResults(batchSize);

        start += batchSize;

        batch = sess.getList();
        index = 0;

        if (Util.isEmpty(batch)) {
          done = true;
        }
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl) {
    return new ObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    return new ObjectIterator(cl, null, getPrincipalRef(), false, 0);
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(final Class<T> cl) {
    return new ObjectIterator(cl, null, null, true, 0);
  }

  @Override
  public <T> Iterator<T> getObjectIterator(final Class<T> cl,
                                           final String colPath) {
    return new ObjectIterator<T>(cl, colPath);
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    return new EventHrefIterator(start);
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() {
    return events.getEventAnnotations();
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) {
    return events.getEventOverrides(ev);
  }

  /* ====================================================================
   *                       filter defs
   * ==================================================================== */

  @Override
  public void add(final BwFilterDef val,
                  final BwPrincipal<?> owner) {
    final BwFilterDef fd = filterDefs.fetch(val.getName(), owner);

    if (fd != null) {
      throw new BedeworkException(CalFacadeErrorCode.duplicateFilter,
                                   val.getName());
    }

    entityDao.add(val);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal<?> owner) {
    return filterDefs.fetch(name, owner);
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal<?> owner) {
    return filterDefs.getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) {
    entityDao.update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal<?> owner) {
    final BwFilterDef fd = filterDefs.fetch(name, owner);

    if (fd == null) {
      throw new BedeworkException(CalFacadeErrorCode.unknownFilter, name);
    }

    entityDao.delete(fd);
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) {
    final BwAuthUser ck = getAuthUser(val.getUserHref());

    if (ck != null) {
      throw new BedeworkException(CalFacadeErrorCode.targetExists);
    }

    entityDao.add(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) {
    final BwAuthUser au = principalsAndPrefs.getAuthUser(href);

    if (au == null) {
      // Not an authorised user
      return null;
    }

    BwAuthUserPrefs prefs = au.getPrefs();

    if (prefs == null) {
      prefs = BwAuthUserPrefs.makeAuthUserPrefs();
      au.setPrefs(prefs);
    }

    return au;
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) {
    entityDao.update(val);
  }

  @Override
  public List<BwAuthUser> getAll() {
    return principalsAndPrefs.getAllAuthUsers();
  }

  @Override
  public void delete(final BwAuthUser val) {
    entityDao.delete(val);
  }

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return principalsAndPrefs.getPrincipal(href);
  }

  @Override
  public void add(final BwPrincipal<?> val) {
    entityDao.add(val);
    getIndexer(val.getPrincipalRef(),
               docTypePrincipal).indexEntity(val);
  }

  @Override
  public void update(final BwPrincipal<?> val) {
    entityDao.update(val);
    getIndexer(val.getPrincipalRef(),
               docTypePrincipal).indexEntity(val);
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) {
    return principalsAndPrefs.getPrincipalHrefs(start, count);
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    return principalsAndPrefs.getPreferences(principalHref);
  }

  @Override
  public void add(final BwPreferences val) {
    entityDao.add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwPreferences val) {
    entityDao.update(val);
    indexEntity(val);
  }

  @Override
  public void delete(final BwPreferences val) {
    entityDao.delete(val);
    getIndexer(docTypePreferences).unindexEntity(val.getHref());
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */
  
  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    principalsAndPrefs.removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup<?> findGroup(final String account,
                           final boolean admin) {
    return principalsAndPrefs.findGroup(account, admin);
  }

  @Override
  public Collection<BwGroup<?>> findGroupParents(final BwGroup group,
                                       final boolean admin) {
    return principalsAndPrefs.findGroupParents(group, admin);
 }
 
  @Override
  public void addGroup(final BwGroup group,
                       final boolean admin) {
    principalsAndPrefs.add(group);
    indexEntity(group);
  }

  @Override
  public void updateGroup(final BwGroup group,
                          final boolean admin) {
    principalsAndPrefs.update(group);
    indexEntity(group);
  }

  @Override
  public void removeGroup(final BwGroup group,
                          final boolean admin) {
    principalsAndPrefs.removeGroup(group, admin);
    getIndexer(docTypePrincipal).unindexEntity(group.getHref());
  }

  @Override
  public void addMember(final BwGroup group,
                        final BwPrincipal val,
                        final boolean admin) {
    final BwGroupEntry ent;

    if (admin) {
      ent = new BwAdminGroupEntry();
    } else {
      ent = new BwGroupEntry();
    }

    ent.setGrp(group);
    ent.setMember(val);

    principalsAndPrefs.add(ent);
    indexEntity(group);
  }

  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val,
                           final boolean admin) {
    principalsAndPrefs.removeMember(group, val, admin);
    indexEntity(group);
  }

  @Override
  public Collection<BwPrincipal<?>> getMembers(final BwGroup group,
                                               final boolean admin) {
    return principalsAndPrefs.getMembers(group, admin);
  }

  @Override
  public Collection<BwGroup<?>> getAllGroups(final boolean admin) {
    return principalsAndPrefs.getAllGroups(admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups() {
    return principalsAndPrefs.getAllGroups(false);
  }

  @Override
  public Collection<BwGroup<?>> getGroups(
          final BwPrincipal<?> val,
          final boolean admin) {
    return principalsAndPrefs.getGroups(val, admin);
  }

  @Override
  public Collection<BwAdminGroup> getAdminGroups(
          final BwPrincipal<?> val) {
    return principalsAndPrefs.getGroups(val, true);
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  @Override
  public BwCalSuite get(final BwAdminGroup group) {
    return entityDao.get(group);
  }
  
  @Override
  public BwCalSuite getCalSuite(final String name) {
    return entityDao.getCalSuite(name);
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    return entityDao.getAllCalSuites();
  }

  @Override
  public void add(final BwCalSuite val) {
    entityDao.add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwCalSuite val) {
    entityDao.update(val);
    indexEntity(val);
  }

  @Override
  public void delete(final BwCalSuite val) {
    entityDao.delete(val);
    getIndexer(docTypePrincipal).unindexEntity(val.getHref());
  }

  /* ====================================================================
   *                   Event Properties
   * ==================================================================== */

  @Override
  public void add(final BwEventProperty<?> val) {
    entityDao.add(val);
    indexEntity(val);
  }

  @Override
  public void update(final BwEventProperty<?> val) {
    entityDao.update(val);
    indexEntity(val);
  }

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwEventProperty<?>> CoreEventPropertiesI<T> getEvPropsHandler(final Class<T> cl) {
    if (cl.equals(BwCategory.class)) {
      if (categoriesHandler == null) {
        categoriesHandler =
            new CoreEventProperties<>(sess, this,
                                      ac, readOnlyMode, sessionless,
                                      BwCategory.class.getName());
      }

      return (CoreEventPropertiesI<T>)categoriesHandler;
    }

    if (cl.equals(BwContact.class)) {
      if (contactsHandler == null) {
        contactsHandler =
                new CoreEventProperties<>(sess, this,
                                          ac, readOnlyMode, sessionless,
                                          BwContact.class.getName());
      }

      return (CoreEventPropertiesI<T>)contactsHandler;
    }

    if (cl.equals(BwLocation.class)) {
      if (locationsHandler == null) {
        locationsHandler =
                new CoreEventProperties<>(sess, this,
                                          ac, readOnlyMode, sessionless,
                                          BwLocation.class.getName());
      }

      return (CoreEventPropertiesI<T>)locationsHandler;
    }

    throw new RuntimeException("Should not get here");
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  @Override
  public BwResource getResource(final String href,
                                final int desiredAccess) {
    return resources.getResource(href, desiredAccess);
  }

  @Override
  public void getResourceContent(final BwResource val) {
    resources.getResourceContent(val);
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) {
    return resources.getResources(path,
                                  forSynch,
                                  token,
                                  count);
  }

  @Override
  public void add(final BwResource val) {
    resources.add(val);
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) {
    resources.addContent(r, rc);
  }

  @Override
  public void update(final BwResource val) {
    resources.update(val);
  }

  @Override
  public void updateContent(final BwResource r,
                            final BwResourceContent val) {
    resources.updateContent(r, val);
  }

  @Override
  public void deleteResource(final String href) {
    resources.deleteResource(href);
  }

  @Override
  public void delete(final BwResource r) {
    resources.delete(r);
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) {
    resources.deleteContent(r, val);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private SessionFactory getSessionFactory() {
    if (sessionFactory != null) {
      return sessionFactory;
    }

    synchronized (syncher) {
      if (sessionFactory != null) {
        return sessionFactory;
      }

      /* Get a new hibernate session factory. This is configured from an
       * application resource hibernate.cfg.xml together with some run time values
       */
      try {
        final DbConfig<?> dbConf = CoreConfigurations.getConfigs().getDbConfig();
        final Configuration conf = new Configuration();

        final StringBuilder sb = new StringBuilder();

        final List<String> ps = dbConf.getHibernateProperties();

        for (final String p: ps) {
          sb.append(p);
          sb.append("\n");
        }

        final Properties hprops = new Properties();
        hprops.load(new StringReader(sb.toString()));

        conf.addProperties(hprops).configure();

        sessionFactory = conf.buildSessionFactory();

        return sessionFactory;
      } catch (final Throwable t) {
        // Always bad.
        error(t);
        throw new BedeworkException(t);
      }
    }
  }
}
