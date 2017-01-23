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
import org.bedework.access.WhoDefs;
import org.bedework.calcore.CalintfBase;
import org.bedework.calcore.CalintfHelper;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calcorei.HibSession;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwGroupEntry;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAdminGroupEntry;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.util.Granulator.EventPeriod;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
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
import java.util.TreeSet;

/** Implementation of CalIntf which uses hibernate as its persistance engine.
 *
 * <p>We assume this interface is accessing public events or private events.
 * In either case it may be read-only or read/write. It is up to the caller
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
public class CalintfImpl extends CalintfBase implements PrivilegeDefs {
  private static final BwStats stats = new BwStats();

  private static final CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  protected static final Map<String, CalintfBase> openIfs = new HashMap<>();

  private EntityDAO entityDao;

  private PrincipalsAndPrefsDAO principalsAndPrefs;

  private CoreEvents events;

  private CoreCalendars calendars;

  private FilterDefsDAO filterDefs;

  private CoreEventPropertiesI<BwCategory> categoriesHandler;

  private CoreEventPropertiesI<BwLocation> locationsHandler;

  private CoreEventPropertiesI<BwContact> contactsHandler;
  
  private final Map<String, DAOBase> daos = new HashMap<>();

  CalintfHelperCallback cb;

  private boolean killed;
  
  /** Prevent updates.
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
  public CalintfImpl() {
  }

  @Override
  public void init(final String logId,
                   final Configurations configs,
                   final PrincipalInfo principalInfo,
                   final String url,
                   final boolean publicAdmin,
                   final boolean publicSubmission,
                   final boolean sessionless) throws CalFacadeException {
    super.init(logId, configs, principalInfo, url,
               publicAdmin, publicSubmission, sessionless);
    
    events = new CoreEvents(sess, cb,
                            ac, currentMode, sessionless);

    calendars = new CoreCalendars(sess, cb,
                                  ac, currentMode, sessionless);

    access.setCollectionGetter(calendars);
  }

  private static class CalintfHelperCallback implements CalintfHelper.Callback {
    private final CalintfImpl intf;

    CalintfHelperCallback(final CalintfImpl intf) {
      this.intf = intf;
    }

    @Override
    public void registerDao(final DAOBase dao) {
      intf.daos.put(dao.getClass().getName(), dao);
    }

    @Override
    public BasicSystemProperties getSyspars() throws CalFacadeException {
      return intf.getSyspars();
    }

    @Override
    public PrincipalInfo getPrincipalInfo() throws CalFacadeException {
      return intf.getPrincipalInfo();
    }

    @Override
    public Timestamp getCurrentTimestamp() throws CalFacadeException {
      return intf.getCurrentTimestamp();
    }

    @Override
    public BwStats getStats() throws CalFacadeException {
      return intf.getStats();
    }

    @Override
    public BwCalendar getCollection(final String path) throws CalFacadeException {
      try {
        return intf.calendars.getCalendar(path, PrivilegeDefs.privAny, true);
      } catch (final Throwable t) {
        if (t instanceof CalFacadeException) {
          throw (CalFacadeException)t;
        }

        throw new CalFacadeException(t);
      }
    }

    @Override
    public BwCategory getCategory(final String uid) throws CalFacadeException {
      try {
        return intf.getEvPropsHandler(BwCategory.class).get(uid);
      } catch (final Throwable t) {
        if (t instanceof CalFacadeException) {
          throw (CalFacadeException)t;
        }

        throw new CalFacadeException(t);
      }
    }

    @Override
    public BwCalendar getCollection(final String path,
                                    final int desiredAccess,
                                    final boolean alwaysReturn) throws CalFacadeException {
      try {
        return intf.calendars.getCalendar(path, desiredAccess, alwaysReturn);
      } catch (final Throwable t) {
        if (t instanceof CalFacadeException) {
          throw (CalFacadeException)t;
        }

        throw new CalFacadeException(t);
      }
    }

    @Override
    public void postNotification(final SysEvent ev) throws CalFacadeException {
      intf.postNotification(ev);
    }

    @Override
    public boolean getForRestore() {
      return intf.forRestore;
    }

    @Override
    public BwIndexer getIndexer(final BwOwnedDbentity entity) throws CalFacadeException {
      if ((intf.currentMode == CalintfDefs.guestMode) ||
              (intf.currentMode == CalintfDefs.publicUserMode) ||
              (intf.currentMode == CalintfDefs.publicAdminMode)) {
        return intf.getPublicIndexer();
      }

      if ((entity != null) && entity.getPublick()) {
        return intf.getPublicIndexer();
      }

      return intf.getIndexer(intf.getPrincipal());
    }
  }

  @Override
  public boolean getSuperUser() {
    return principalInfo.getSuperUser();
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
    if (!enable && (dbStats == null)) {
      return;
    }

    if (dbStats == null) {
      dbStats = getSessionFactory().getStatistics();
    }

    dbStats.setStatisticsEnabled(enable);
  }

  @Override
  public boolean getDbStatsEnabled() throws CalFacadeException {
    if (dbStats == null) {
      return false;
    }

    return dbStats.isStatisticsEnabled();
  }

  @Override
  public void dumpDbStats() throws CalFacadeException {
    DbStatistics.dumpStats(dbStats);
  }

  @Override
  public Collection<StatsEntry> getDbStats() throws CalFacadeException {
    return DbStatistics.getStats(dbStats);
  }

  @Override
  public CalintfInfo getInfo() throws CalFacadeException {
    return info;
  }

  /* ====================================================================
   *                   Misc methods
   * ==================================================================== */

  @Override
  public synchronized void open(final boolean webMode,
                                final boolean forRestore,
                                final boolean indexRebuild) throws CalFacadeException {
    if (isOpen) {
      throw new CalFacadeException("Already open");
    }

    isOpen = true;
    this.forRestore = forRestore;
    this.indexRebuild = indexRebuild;

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

    if (access != null) {
      access.open();
    }

    cb = new CalintfHelperCallback(this);
    
    if (entityDao == null) {
      entityDao = new EntityDAO(sess);
      cb.registerDao(entityDao);
    }

    if (principalsAndPrefs == null) {
      principalsAndPrefs = new PrincipalsAndPrefsDAO(sess);
      cb.registerDao(principalsAndPrefs);
    }

    if (filterDefs == null) {
      filterDefs = new FilterDefsDAO(sess);
      cb.registerDao(filterDefs);
    }
    
    for (final DAOBase dao: daos.values()) {
      dao.setSess(sess);
    }
  }

  @Override
  public synchronized void close() throws CalFacadeException {
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
    checkOpen();

    if (debug) {
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

    sess.beginTransaction();

    if (calendars != null) {
      calendars.startTransaction();
    }

    curTimestamp = sess.getCurrentTimestamp();
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    try {
      if (killed) {
        return;
      }
      
      checkOpen();

      if (debug) {
        debug("End transaction for " + getTraceId());
      }

      if (!sess.rolledback()) {
        sess.commit();
      }

      if (calendars != null) {
        calendars.endTransaction();
      }

      if (!indexRebuild) {
        cb.getIndexer(null).markTransaction();
      }
    } catch (final CalFacadeException cfe) {
      sess.rollback();
      throw cfe;
    } catch (final Throwable t) {
      sess.rollback();
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
    if (killed) {
      return true;
    }

    if (!isOpen) {
      return false;
    }

    return sess.rolledback();
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
  public Collection<? extends Calintf> active() throws CalFacadeException {
    return openIfs.values();
  }

  @Override
  public void kill() throws CalFacadeException {
    try {
      rollbackTransaction();
    } catch (final Throwable ignored) {
    }
    
    try {
      close();
    } catch (final Throwable ignored) {
    }
    
    killed = true;
  }

  @Override
  public long getStartMillis() throws CalFacadeException {
    return objMillis;
  }

  @Override
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    return curTimestamp;
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.Calintf#reAttach(org.bedework.calfacade.base.BwDbentity)
   */
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

  @Override
  public Collection<? extends BwShareableDbentity<?>>
                  checkAccess(final Collection<? extends BwShareableDbentity<?>> ents,
                                         final int desiredAccess,
                                         final boolean alwaysReturn)
                                         throws CalFacadeException {
    return access.checkAccess(ents, desiredAccess, alwaysReturn);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.Calintf#checkAccess(org.bedework.calfacade.base.BwShareableDbentity, int, boolean)
   */
  @Override
  public CurrentAccess checkAccess(final BwShareableDbentity ent, final int desiredAccess,
                                   final boolean returnResult) throws CalFacadeException {
    return access.checkAccess(ent, desiredAccess, returnResult);
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#getSynchInfo(java.lang.String, java.lang.String)
   */
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
  public BwCalendar getCollectionIdx(final BwIndexer indexer,
                                     final String path,
                                     final int desiredAccess,
                                     final boolean alwaysReturnResult) throws CalFacadeException {
    checkOpen();

    return calendars.getCollectionIdx(indexer, 
                                      path, desiredAccess, alwaysReturnResult);
  }
  
  @Override
  public GetSpecialCalendarResult getSpecialCalendar(final BwPrincipal owner,
                                       final int calType,
                                       final boolean create,
                                       final int access) throws CalFacadeException {
    return calendars.getSpecialCalendar(owner, calType, create, access);
  }

  @Override
  public BwCalendar add(final BwCalendar val,
                                final String parentPath) throws CalFacadeException {
    checkOpen();

    return calendars.add(val, parentPath);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#touchCalendar(java.lang.String)
   */
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#renameCalendar(org.bedework.calfacade.BwCalendar, java.lang.String)
   */
  @Override
  public void renameCalendar(final BwCalendar val,
                             final String newName) throws CalFacadeException {
    checkOpen();
    calendars.renameCalendar(val, newName);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#moveCalendar(org.bedework.calfacade.BwCalendar, org.bedework.calfacade.BwCalendar)
   */
  @Override
  public void moveCalendar(final BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    checkOpen();
    calendars.moveCalendar(val, newParent);
  }

  @Override
  public boolean deleteCalendar(final BwCalendar val,
                                final boolean reallyDelete) throws CalFacadeException {
    checkOpen();

    return calendars.deleteCalendar(val, reallyDelete);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#isEmpty(org.bedework.calfacade.BwCalendar)
   */
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#getChildren(java.lang.String, int, int)
   */
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
  public boolean testSynchCol(final BwCalendar col,
                              final String lastmod)
          throws CalFacadeException {
    return calendars.testSynchCol(col, lastmod);
  }

  @Override
  public String getSyncToken(final String path) throws CalFacadeException {
    return calendars.getSyncToken(path);
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) throws CalFacadeException {
    checkOpen();

    return events.getChildEntities(parentPath, start, count);
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
        if (debug) {
          trace(ep.toString());
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
      if (debug) {
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
  public Collection<CoreEventInfo> getEvents(final Collection <BwCalendar> calendars,
                                             final FilterBase filter,
                                             final BwDateTime startDate, final BwDateTime endDate,
                                             final List<BwIcalPropertyInfoEntry> retrieveList,
                                             final RecurringRetrievalMode recurRetrieval,
                                             final boolean freeBusy) throws CalFacadeException {
    return events.getEvents(calendars, filter,
                            startDate, endDate, retrieveList, recurRetrieval,
                            freeBusy);
  }

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
  public CoreEventInfo getEvent(final String colPath, final String val,
                                final RecurringRetrievalMode recurRetrieval)
          throws CalFacadeException {
    checkOpen();
    return events.getEvent(colPath, val, recurRetrieval);
  }

  /* ====================================================================
   *                       General db methods
   * ==================================================================== */

  @Override
  public void add(final BwUnversionedDbentity val) throws CalFacadeException {
    entityDao.save(val);
  }

  @Override
  public void saveOrUpdate(final BwUnversionedDbentity val) throws CalFacadeException {
    entityDao.saveOrUpdate(val);
  }

  @Override
  public void delete(final BwUnversionedDbentity val) throws CalFacadeException {
    entityDao.delete(val);
  }

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    return entityDao.merge(val);
  }

  public Blob getBlob(final byte[] val) throws CalFacadeException {
    return entityDao.getBlob(val);
  }
  
  private class ObjectIterator implements Iterator {
    private final String className;
    private List batch;
    private int index;
    private boolean done;
    private int start;
    private final int batchSize = 100;

    private ObjectIterator(final String className) {
      this.className = className;
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

    private boolean more() {
      if (done) {
        return false;
      }

      if ((batch == null) || (index == batch.size())) {
        nextBatch();
      }

      return !done;
    }

    private void nextBatch() {
      try {
        sess.createQuery("from " + className);

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
  public Iterator getObjectIterator(final String className) throws CalFacadeException {
    return new ObjectIterator(className);
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

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return principalsAndPrefs.getPrincipal(href);
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
  }

  @Override
  public void removeGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    principalsAndPrefs.removeGroup(group, admin);
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
  }

  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val,
                           final boolean admin) throws CalFacadeException {
    principalsAndPrefs.removeMember(group, val, admin);
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
            new CoreEventProperties<>(sess, cb,
                                      ac, currentMode, sessionless,
                                      BwCategory.class.getName());
      }

      return (CoreEventPropertiesI<T>)categoriesHandler;
    }

    if (cl.equals(BwContact.class)) {
      if (contactsHandler == null) {
        contactsHandler =
                new CoreEventProperties<>(sess, cb,
                                          ac, currentMode, sessionless,
                                          BwContact.class.getName());
      }

      return (CoreEventPropertiesI<T>)contactsHandler;
    }

    if (cl.equals(BwLocation.class)) {
      if (locationsHandler == null) {
        locationsHandler =
                new CoreEventProperties<>(sess, cb,
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
  public BwResource getResource(final String name,
                                final BwCalendar coll,
                                final int desiredAccess) throws CalFacadeException {
    final BwResource res = entityDao.getResource(name, coll, 
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
  public List<BwResource> getAllResources(final String path,
                                          final boolean forSynch,
                                          final String token) throws CalFacadeException {
    return postProcess(entityDao.getAllResources(path, forSynch, token));
  }

  @Override
  public BwSystem getSyspars(final String name) throws CalFacadeException {
    return entityDao.getSyspars(name);
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
                                                    null, rrm, true);

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
