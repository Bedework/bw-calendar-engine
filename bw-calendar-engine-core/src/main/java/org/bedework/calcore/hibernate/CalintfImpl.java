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
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.common.CalintfROImpl;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calcorei.HibSession;
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
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAdminGroupEntry;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.util.misc.Util;

import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;

import java.io.StringReader;
import java.sql.Blob;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.bedework.calfacade.indexing.BwIndexer.docTypePreferences;
import static org.bedework.calfacade.indexing.BwIndexer.docTypePrincipal;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResource;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeResourceContent;

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
  public void initPinfo(final PrincipalInfo principalInfo) throws CalFacadeException {
    super.initPinfo(principalInfo);
    
    events = new CoreEvents(sess, this,
                            ac, currentMode, sessionless);

    calendars = new CoreCalendars(sess, this,
                                  ac, currentMode, sessionless);

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
  public void setDbStatsEnabled(final boolean enable) throws CalFacadeException {
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
  public boolean getDbStatsEnabled() throws CalFacadeException {
    return dbStats != null && dbStats.isStatisticsEnabled();

  }

  @Override
  public void dumpDbStats() throws CalFacadeException {
    DbStatistics.dumpStats(dbStats);
  }

  @Override
  public Collection<StatsEntry> getDbStats() throws CalFacadeException {
    return DbStatistics.getStats(dbStats);
  }

  /* ====================================================================
   *                   Misc methods
   * ==================================================================== */

  @Override
  public synchronized void open(final FilterParserFetcher filterParserFetcher,
                                final String logId,
                                final Configurations configs,
                                final boolean webMode,
                                final boolean forRestore,
                                final boolean indexRebuild,
                                final boolean publicAdmin,
                                final boolean publicSubmission,
                                final boolean sessionless,
                                final boolean dontKill) throws CalFacadeException {
    super.open(filterParserFetcher, logId, configs, webMode,
               forRestore, indexRebuild,
               publicAdmin, publicSubmission, sessionless, dontKill);
    authenticated = true;

    if (publicAdmin) {
      currentMode = CalintfDefs.publicAdminMode;
    } else if (publicSubmission) {
      currentMode = CalintfDefs.publicUserMode;
    } else {
      currentMode = CalintfDefs.userMode;
    }

    if ((sess != null) && !webMode) {
      warn("Session is not null. Will close");
      try {
        close();
      } catch (final Throwable t) {
        if (debug) {
          warn("Ignoring the following error");
          error(t);
        }
      }
    }

    if (sess == null) {
      if (debug) {
        debug("New hibernate session for " + getTraceId());
      }
      sess = new HibSessionImpl();
      sess.init(getSessionFactory(), getLogger());
      if (webMode) {
        sess.setFlushMode(FlushMode.MANUAL);
      } else if (debug) {
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
  public synchronized void close() throws CalFacadeException {
    closeIndexers();

    if (killed) {
      return;
    }

    if (!isOpen) {
      if (debug) {
        debug("Close for " + getTraceId() + " closed session");
      }
      return;
    }

    if (debug) {
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
  //        sess.disconnect();
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
  public void beginTransaction() throws CalFacadeException {
    super.beginTransaction();
    sess.beginTransaction();

    curTimestamp = sess.getCurrentTimestamp();

    if (calendars != null) {
      calendars.startTransaction();
    }
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    super.endTransaction();

    try {
      if (calendars != null) {
        calendars.endTransaction();
      }

      if (!sess.rolledback()) {
        sess.commit();
      }

      if (!indexRebuild) {
        getIndexer().markTransaction();
      }
    } catch (final CalFacadeException cfe) {
      if (sess != null) {
        sess.rollback();
      }

      throw cfe;
    } catch (final Throwable t) {
      if (sess != null) {
        sess.rollback();
      }

      throw new CalFacadeException(t);
    } finally {
      synchronized (openIfs) {
        openIfs.remove(objKey);
      }
    }
  }

  @Override
  public void rollbackTransaction() throws CalFacadeException {
    try {
      if (killed) {
        return;
      }

      checkOpen();
      sess.rollback();
    } finally {
      clearNotifications();
      synchronized (openIfs) {
        openIfs.remove(objKey);
      }
    }
  }

  @Override
  public boolean isRolledback() throws CalFacadeException {
    //noinspection SingleStatementInBlock
    if (killed) {
      return true;
    }

    return isOpen && sess.rolledback();
  }

  @Override
  public void flush() throws CalFacadeException {
    if (killed) {
      return;
    }

    if (debug) {
      getLogger().debug("flush for " + getTraceId());
    }
    if (sess.isOpen()) {
      sess.flush();
    }
  }

  @Override
  public void clear() throws CalFacadeException {
    if (killed) {
      return;
    }

    if (debug) {
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
    
    try {
      close();
    } catch (final Throwable t) {
      warn("Exception on close for kill: " + t.getMessage());
    }

    super.kill();
  }

  @Override
  public void reAttach(BwDbentity val) throws CalFacadeException {
    if (val instanceof CalendarWrapper) {
      final CalendarWrapper ccw = (CalendarWrapper)val;
      val = ccw.fetchEntity();
    }
    sess.reAttach(val);
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  @Override
  public void changeAccess(final BwShareableDbentity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    if (ent instanceof BwCalendar) {
      changeAccess((BwCalendar)ent, aces, replaceAll);
      return;
    }
    checkOpen();
    access.changeAccess(ent, aces, replaceAll);
    entityDao.saveOrUpdate(ent);
  }

  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    checkOpen();
    calendars.changeAccess(cal, aces, replaceAll);
  }

  @Override
  public void defaultAccess(final BwShareableDbentity ent,
                            final AceWho who) throws CalFacadeException {
    if (ent instanceof BwCalendar) {
      defaultAccess((BwCalendar)ent, who);
      return;
    }
    checkOpen();
    checkAccess(ent, privWriteAcl, false);
    access.defaultAccess(ent, who);
    entityDao.saveOrUpdate(ent);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) throws CalFacadeException {
    checkOpen();
    calendars.defaultAccess(cal, who);
  }

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime)
          throws CalFacadeException {
    checkOpen();

    return entityDao.getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm)
          throws CalFacadeException {
    checkOpen();

    return entityDao.getEventsByAlarm(alarm);
  }

  /* ====================================================================
   *                       Calendars
   * ==================================================================== */

  @Override
  public void principalChanged() throws CalFacadeException {
    calendars.principalChanged();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) throws CalFacadeException {
    checkOpen();

    return calendars.getSynchInfo(path, token);
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal,
                                             final BwIndexer indexer) throws CalFacadeException {
    checkOpen();

    return calendars.getCalendars(cal, indexer);
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) throws CalFacadeException {
    checkOpen();

    return calendars.resolveAlias(val, resolveSubAlias,
                                  freeBusy, indexer);
  }

  @Override
  public List<BwCalendar> findAlias(final String val) throws CalFacadeException {
    checkOpen();

    return calendars.findAlias(val);
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) throws CalFacadeException{
    checkOpen();

    return calendars.getCalendar(path, desiredAccess, alwaysReturnResult);
  }

  @Override
  public GetSpecialCalendarResult getSpecialCalendar(final BwIndexer indexer,
                                                     final BwPrincipal owner,
                                                     final int calType,
                                                     final boolean create,
                                                     final int access) throws CalFacadeException {
    return calendars.getSpecialCalendar(indexer,
                                        owner, calType, create, access);
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                                final String parentPath) throws CalFacadeException {
    checkOpen();

    return calendars.add(val, parentPath);
  }

  @Override
  public void touchCalendar(final String path) throws CalFacadeException {
    checkOpen();
    calendars.touchCalendar(path);
  }

  @Override
  public void touchCalendar(final BwCalendar col) throws CalFacadeException {
    checkOpen();
    calendars.touchCalendar(col);
  }

  @Override
  public void updateCalendar(final BwCalendar val) throws CalFacadeException {
    checkOpen();
    calendars.updateCalendar(val);
  }

  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) throws CalFacadeException {
    checkOpen();
    calendars.renameCalendar(val, newName);
  }

  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    checkOpen();
    calendars.moveCalendar(val, newParent);
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete)
          throws CalFacadeException {
    checkOpen();

    return calendars.deleteCalendar(val, reallyDelete);
  }

  @Override
  public boolean isEmpty(final BwCalendar val) throws CalFacadeException {
    checkOpen();

    return calendars.isEmpty(val);
  }

  @Override
  public void addNewCalendars(final BwPrincipal user) throws CalFacadeException {
    checkOpen();

    calendars.addNewCalendars(user);
    entityDao.update(user);
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) throws CalFacadeException {
    checkOpen();

    return calendars.getChildCollections(parentPath, start, count);
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String lastmod) throws CalFacadeException {
    return calendars.getSynchCols(path, lastmod);
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) throws CalFacadeException {
    checkOpen();

    return events.getChildEntities(parentPath, start, count);
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> getEvent(final String colPath,
                                            final String guid)
           throws CalFacadeException {
    checkOpen();
    return events.getEvent(colPath, guid);
  }

  @Override
  public UpdateEventResult addEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean rollbackOnError) throws CalFacadeException {
    checkOpen();
    final UpdateEventResult uer = 
            events.addEvent(ei, scheduling,
                            rollbackOnError);

    if (!forRestore) {
      calendars.touchCalendar(ei.getEvent().getColPath());
    }

    return uer;
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) throws CalFacadeException {
    checkOpen();
    final UpdateEventResult ue;

    try {
      calendars.touchCalendar(ei.getEvent().getColPath());

      ue = events.updateEvent(ei);
    } catch (final CalFacadeException cfe) {
      rollbackTransaction();
      throw cfe;
    }

    return ue;
  }

  @Override
  public DelEventResult deleteEvent(final EventInfo ei,
                                    final boolean scheduling,
                                    final boolean reallyDelete) throws CalFacadeException {
    checkOpen();
    final String colPath = ei.getEvent().getColPath();
    try {
      try {
        return events.deleteEvent(ei, scheduling, reallyDelete);
      } catch (final CalFacadeException cfe) {
        rollbackTransaction();
        throw cfe;
      }
    } finally {
      calendars.touchCalendar(colPath);
    }
  }

  @Override
  public void moveEvent(final BwEvent val,
                        final BwCalendar from,
                        final BwCalendar to) throws CalFacadeException {
    checkOpen();
    events.moveEvent(val, from, to);
  }

  @Override
  public Set<CoreEventInfo> getSynchEvents(final String path,
                                           final String lastmod) throws CalFacadeException {
    return events.getSynchEvents(path, lastmod);
  }

  @Override
  public CoreEventInfo getEvent(final String href)
          throws CalFacadeException {
    checkOpen();
    return events.getEvent(href);
  }

  /* ====================================================================
   *                       Restore methods
   * ==================================================================== */

  @Override
  public void saveOrUpdate(final BwUnversionedDbentity val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    return entityDao.merge(val);
  }

  public Blob getBlob(final byte[] val) throws CalFacadeException {
    return entityDao.getBlob(val);
  }
  
  private class ObjectIterator implements Iterator {
    protected final String className;
    protected final String colPath;
    protected final String ownerHref;
    protected final boolean publicAdmin;
    protected List batch;
    protected int index;
    protected boolean done;
    protected int start;
    protected final int batchSize = 100;

    private ObjectIterator(final String className) {
      this(className, null, null, false, 0);
    }

    private ObjectIterator(final String className,
                           final String colPath) {
      this(className, colPath, null, false, 0);
    }

    private ObjectIterator(final String className,
                           final String colPath,
                           final String ownerHref,
                           final boolean publicAdmin,
                           final int start) {
      this.className = className;
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
    public Object next() {
      if (!more()) {
        return null;
      }

      index++;
      return batch.get(index - 1);
    }

    @Override
    public void remove() {
      throw new RuntimeException("Forbidden");
    }

    protected boolean more() {
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
      super(BwEventObj.class.getName(), null, null, false, start);
    }

    @Override
    public Object next() {
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
  public Iterator getObjectIterator(final String className) {
    return new ObjectIterator(className);
  }

  @Override
  public Iterator getPrincipalObjectIterator(final String className) {
    return new ObjectIterator(className, null, getPrincipalRef(), false, 0);
  }

  @Override
  public Iterator getPublicObjectIterator(final String className) {
    return new ObjectIterator(className, null, null, true, 0);
  }

  @Override
  public Iterator getObjectIterator(final String className,
                                    final String colPath) {
    return new ObjectIterator(className, colPath);
  }

  @Override
  public Iterator<String> getEventHrefs(final int start) {
    return new EventHrefIterator(start);
  }

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException {
    return events.getEventAnnotations();
  }

  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) throws CalFacadeException {
    return events.getEventOverrides(ev);
  }

  /* ====================================================================
   *                       filter defs
   * ==================================================================== */

  @Override
  public void save(final BwFilterDef val,
                   final BwPrincipal owner) throws CalFacadeException {
    final BwFilterDef fd = filterDefs.fetch(val.getName(), owner);

    if (fd != null) {
      throw new CalFacadeException(CalFacadeException.duplicateFilter,
                                   val.getName());
    }

    entityDao.save(val);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal owner) throws CalFacadeException {
    return filterDefs.fetch(name, owner);
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal owner) throws CalFacadeException {
    return filterDefs.getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) throws CalFacadeException {
    entityDao.update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal owner) throws CalFacadeException {
    final BwFilterDef fd = filterDefs.fetch(name, owner);

    if (fd == null) {
      throw new CalFacadeException(CalFacadeException.unknownFilter, name);
    }

    entityDao.delete(fd);
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public void addAuthUser(final BwAuthUser val) throws CalFacadeException {
    final BwAuthUser ck = getAuthUser(val.getUserHref());

    if (ck != null) {
      throw new CalFacadeException(CalFacadeException.targetExists);
    }

    entityDao.save(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) throws CalFacadeException {
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
  public void updateAuthUser(final BwAuthUser val) throws CalFacadeException {
    entityDao.update(val);
  }

  @Override
  public List<BwAuthUser> getAll() throws CalFacadeException {
    return principalsAndPrefs.getAllAuthUsers();
  }

  @Override
  public void delete(final BwAuthUser val) throws CalFacadeException {
    entityDao.delete(val);
  }

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return principalsAndPrefs.getPrincipal(href);
  }

  @Override
  public void saveOrUpdate(final BwPrincipal val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(val).indexEntity(val);
  }

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) throws CalFacadeException {
    return principalsAndPrefs.getPrincipalHrefs(start, count);
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) throws CalFacadeException {
    return principalsAndPrefs.getPreferences(principalHref);
  }

  @Override
  public void saveOrUpdate(final BwPreferences val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(val).indexEntity(val);
  }

  @Override
  public void delete(final BwPreferences val) throws CalFacadeException {
    entityDao.delete(val);
    getIndexer().unindexEntity(docTypePreferences, val.getHref());
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */
  
  @Override
  public void removeFromAllPrefs(final BwShareableDbentity val) throws CalFacadeException {
    principalsAndPrefs.removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup findGroup(final String account,
                           final boolean admin) throws CalFacadeException {
    return principalsAndPrefs.findGroup(account, admin);
  }

  @Override
  public Collection<BwGroup> findGroupParents(final BwGroup group,
                                       final boolean admin) throws CalFacadeException {
    return principalsAndPrefs.findGroupParents(group, admin);
 }
 
  @Override
  public void updateGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    principalsAndPrefs.saveOrUpdate(group);
    getIndexer().indexEntity(group);
  }

  @Override
  public void removeGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    principalsAndPrefs.removeGroup(group, admin);
    getIndexer().unindexEntity(docTypePrincipal, group.getHref());
  }

  @Override
  public void addMember(final BwGroup group,
                        final BwPrincipal val,
                        final boolean admin) throws CalFacadeException {
    final BwGroupEntry ent;

    if (admin) {
      ent = new BwAdminGroupEntry();
    } else {
      ent = new BwGroupEntry();
    }

    ent.setGrp(group);
    ent.setMember(val);

    principalsAndPrefs.saveOrUpdate(ent);
    getIndexer().indexEntity(group);
  }

  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val,
                           final boolean admin) throws CalFacadeException {
    principalsAndPrefs.removeMember(group, val, admin);
    getIndexer().indexEntity(group);
  }

  @Override
  public Collection<BwPrincipal> getMembers(final BwGroup group,
                                            final boolean admin) throws CalFacadeException {
    return principalsAndPrefs.getMembers(group, admin);
  }

  @Override
  public Collection<BwGroup> getAllGroups(final boolean admin) throws CalFacadeException {
    return principalsAndPrefs.getAllGroups(admin);
  }

  @Override
  public Collection<BwGroup> getGroups(final BwPrincipal val,
                                       final boolean admin) throws CalFacadeException {
    return principalsAndPrefs.getGroups(val, admin);
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  @Override
  public BwCalSuite get(final BwAdminGroup group) throws CalFacadeException {
    return entityDao.get(group);
  }
  
  @Override
  public BwCalSuite getCalSuite(final String name) throws CalFacadeException {
    return entityDao.getCalSuite(name);
  }

  @Override
  public Collection<BwCalSuite> getAllCalSuites() throws CalFacadeException {
    return entityDao.getAllCalSuites();
  }

  @Override
  public void saveOrUpdate(final BwCalSuite val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(val).indexEntity(val);
  }

  @Override
  public void delete(final BwCalSuite val) throws CalFacadeException {
    entityDao.delete(val);
    BwCalSuitePrincipal csp = BwCalSuitePrincipal.from(val);
    getIndexer().unindexEntity(docTypePrincipal, csp.getHref());
  }

  /* ====================================================================
   *                   Event Properties
   * ==================================================================== */

  @Override
  public void saveOrUpdate(final BwEventProperty val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(val).indexEntity(val);
  }

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwEventProperty> CoreEventPropertiesI<T> getEvPropsHandler(final Class<T> cl)
        throws CalFacadeException {
    if (cl.equals(BwCategory.class)) {
      if (categoriesHandler == null) {
        categoriesHandler =
            new CoreEventProperties<>(sess, this,
                                      ac, currentMode, sessionless,
                                      BwCategory.class.getName());
      }

      return (CoreEventPropertiesI<T>)categoriesHandler;
    }

    if (cl.equals(BwContact.class)) {
      if (contactsHandler == null) {
        contactsHandler =
                new CoreEventProperties<>(sess, this,
                                          ac, currentMode, sessionless,
                                          BwContact.class.getName());
      }

      return (CoreEventPropertiesI<T>)contactsHandler;
    }

    if (cl.equals(BwLocation.class)) {
      if (locationsHandler == null) {
        locationsHandler =
                new CoreEventProperties<>(sess, this,
                                          ac, currentMode, sessionless,
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
                                final int desiredAccess)
          throws CalFacadeException {
    final int pos = href.lastIndexOf("/");
    if (pos <= 0) {
      throw new RuntimeException("Bad href: " + href);
    }

    final String name = href.substring(pos + 1);

    final String colPath = href.substring(0, pos);

    if (debug) {
      debug("Get resource " + colPath + " -> " + name);
    }
    final BwResource res = entityDao.getResource(name, colPath,
                                                 desiredAccess);
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
    entityDao.getResourceContent(val);
  }

  @Override
  public List<BwResource> getResources(final String path,
                                       final boolean forSynch,
                                       final String token,
                                       final int count) throws CalFacadeException {
    return postProcess(entityDao.getAllResources(path,
                                                 forSynch,
                                                 token,
                                                 count));
  }

  @Override
  public void add(final BwResource val) throws CalFacadeException {
    entityDao.save(val);

    getIndexer(val).indexEntity(val);
  }

  @Override
  public void addContent(final BwResource r,
                         final BwResourceContent rc) throws CalFacadeException {
    entityDao.save(rc);
    getIndexer(r).indexEntity(rc);
  }

  @Override
  public void saveOrUpdate(final BwResource val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(val).indexEntity(val);
  }

  @Override
  public void saveOrUpdateContent(final BwResource r,
                                  final BwResourceContent val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
    getIndexer(r).indexEntity(val);
  }

  @Override
  public void delete(final BwResource val) throws CalFacadeException {
    entityDao.delete(val);
    getIndexer(val).unindexEntity(docTypeResource, val.getHref());
  }

  @Override
  public void deleteContent(final BwResource r,
                            final BwResourceContent val) throws CalFacadeException {
    entityDao.delete(val);
    getIndexer(r).unindexEntity(docTypeResourceContent, val.getHref());
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private List<BwResource> postProcess(final Collection<BwResource> ress) throws CalFacadeException {
    final List<BwResource> resChecked = new ArrayList<>();

    for (final BwResource res: ress) {
      if (checkAccess(res, PrivilegeDefs.privRead, true).getAccessAllowed()) {
        resChecked.add(res);
      }
    }

    return resChecked;
  }

  private SessionFactory getSessionFactory() throws CalFacadeException {
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
        final DbConfig dbConf = CoreConfigurations.getConfigs().getDbConfig();
        final Configuration conf = new Configuration();

        final StringBuilder sb = new StringBuilder();

        @SuppressWarnings("unchecked")
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
        throw new CalFacadeException(t);
      }
    }
  }
}
