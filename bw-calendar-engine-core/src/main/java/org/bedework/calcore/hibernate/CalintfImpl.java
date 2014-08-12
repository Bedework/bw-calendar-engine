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

import org.bedework.calcore.AccessUtil;
import org.bedework.calcore.CalintfBase;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calcorei.CoreEventsI;
import org.bedework.calcorei.CoreFilterDefsI;
import org.bedework.calcorei.CoreUserAuthI;
import org.bedework.calcorei.HibSession;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAuthUser;
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
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAdminGroupEntry;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefsCalendar;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefsContact;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefsLocation;
import org.bedework.calfacade.util.Granulator.EventPeriod;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;

import java.io.StringReader;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
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
  private static BwStats stats = new BwStats();

  private static CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  /** For evaluating access control
   */
  private AccessUtil access;

  private CoreEventsI events;

  private CoreCalendars calendars;

  private CoreFilterDefsI filterDefs;

  private CoreUserAuthI userauth;

  private CoreEventPropertiesI<BwCategory> categoriesHandler;

  private CoreEventPropertiesI<BwLocation> locationsHandler;

  private CoreEventPropertiesI<BwContact> contactsHandler;

  CalintfHelperCallback cb;
  CalintfHelperHib.CalintfHelperHibCb chcb;

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

    try {
      access = new AccessUtil();
      access.init(principalInfo);
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    cb = new CalintfHelperCallback(this);
    chcb = new CalintfHelperHibCb(this);

    events = new CoreEvents(chcb, cb,
                            access, currentMode, sessionless);

    calendars = new CoreCalendars(chcb, cb,
                                  access, currentMode, sessionless);

    filterDefs = new FilterDefs(chcb, cb,
                                access, currentMode, sessionless);

    userauth = new CoreUserAuthImpl(chcb, cb,
                                    access, currentMode, sessionless);

    access.setCollectionGetter(calendars);
  }

  private static class CalintfHelperHibCb implements CalintfHelperHib.CalintfHelperHibCb {
    protected CalintfImpl intf;

    CalintfHelperHibCb(final CalintfImpl intf) {
      this.intf = intf;
    }

    @Override
    public HibSession getSess() throws CalFacadeException {
      return intf.sess;
    }
  }

  private static class CalintfHelperCallback implements CalintfHelperHib.Callback {
    private CalintfImpl intf;

    CalintfHelperCallback(final CalintfImpl intf) {
      this.intf = intf;
    }

    @Override
    public void rollback() throws CalFacadeException {
      intf.rollbackTransaction();
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
      } catch (Throwable t) {
        if (t instanceof CalFacadeException) {
          throw (CalFacadeException)t;
        }

        throw new CalFacadeException(t);
      }
    }

    @Override
    public BwCategory getCategory(String uid) throws CalFacadeException {
      try {
        return intf.getEvPropsHandler(BwCategory.class).get(uid);
      } catch (Throwable t) {
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
      } catch (Throwable t) {
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
    public BwIndexer getIndexer() throws CalFacadeException {
      if ((intf.currentMode == CalintfDefs.guestMode) ||
              (intf.currentMode == CalintfDefs.publicUserMode) ||
              (intf.currentMode == CalintfDefs.publicAdminMode)) {
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
      } finally {
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
  }

  @Override
  public synchronized void close() throws CalFacadeException {
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
      } catch (Throwable t) {
        try {
          sess.close();
        } catch (Throwable t1) {}
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

    sess.beginTransaction();

    if (events != null) {
      ((CalintfHelperHib)events).startTransaction();
    }

    if (calendars != null) {
      ((CalintfHelperHib)calendars).startTransaction();
    }

    curTimestamp = sess.getCurrentTimestamp();
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    try {
      checkOpen();

      if (debug) {
        debug("End transaction for " + getTraceId());
      }

      if (!sess.rolledback()) {
        sess.commit();
      }

      if (events != null) {
        ((CalintfHelperHib)events).endTransaction();
      }

      if (calendars != null) {
        calendars.endTransaction();
      }

      if (!indexRebuild) {
        cb.getIndexer().markTransaction();
      }
    } catch (final CalFacadeException cfe) {
      sess.rollback();
      throw cfe;
    } catch (final Throwable t) {
      sess.rollback();
      throw new CalFacadeException(t);
//    } finally {
//      flushNotifications();
    }
  }

  @Override
  public void rollbackTransaction() throws CalFacadeException {
    try {
      checkOpen();
      sess.rollback();
    } finally {
      clearNotifications();
    }
  }

  @Override
  public boolean isRolledback() throws CalFacadeException {
    if (!isOpen) {
      return false;
    }

    return sess.rolledback();
  }

  @Override
  public void flush() throws CalFacadeException {
    if (debug) {
      getLogger().debug("flush for " + getTraceId());
    }
    if (sess.isOpen()) {
      sess.flush();
    }
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
      CalendarWrapper ccw = (CalendarWrapper)val;
      val = ccw.fetchEntity();
    }
    sess.reAttach(val);
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calcorei.Calintf#changeAccess(org.bedework.calfacade.base.BwShareableDbentity, java.util.Collection, boolean)
   */
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
    sess.saveOrUpdate(ent);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#changeAccess(org.bedework.calfacade.BwCalendar, java.util.Collection)
   */
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
    sess.saveOrUpdate(ent);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) throws CalFacadeException {
    checkOpen();
    calendars.defaultAccess(cal, who);
  }

  @Override
  public Collection<? extends BwShareableDbentity<? extends Object>>
                  checkAccess(final Collection<? extends BwShareableDbentity<? extends Object>> ents,
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

  private static final String getUnexpiredAlarmsQuery =
    "from " + BwAlarm.class.getName() + " as al " +
      "where al.expired = false";

  /* Return all unexpired alarms before the given time */
  private static final String getUnexpiredAlarmsTimeQuery =
    "from " + BwAlarm.class.getName() + " as al " +
      "where al.expired = false and " +
      "al.triggerTime <= :tt";

  @Override
  @SuppressWarnings("unchecked")
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime)
          throws CalFacadeException {
    checkOpen();

    if (triggerTime == 0) {
      sess.createQuery(getUnexpiredAlarmsQuery);
    } else {
      sess.createQuery(getUnexpiredAlarmsTimeQuery);
      sess.setString("tt", String.valueOf(triggerTime));
    }

    return sess.getList();
  }

  private static final String eventByAlarmQuery =
      "select count(*) from " + BwEventObj.class.getName() + " as ev " +
      "where ev.tombstoned=false and :alarm in alarms";

  @Override
  @SuppressWarnings("unchecked")
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm)
          throws CalFacadeException {
    checkOpen();

    sess.createQuery(eventByAlarmQuery);
    sess.setInt("alarmId", alarm.getId());

    return sess.getList();
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#getCalendars(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar cal) throws CalFacadeException {
    checkOpen();

    return calendars.getCalendars(cal);
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy) throws CalFacadeException {
    checkOpen();

    return calendars.resolveAlias(val, resolveSubAlias, freeBusy);
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#addNewCalendars(org.bedework.calfacade.BwUser)
   */
  @Override
  public void addNewCalendars(final BwPrincipal user) throws CalFacadeException {
    checkOpen();

    calendars.addNewCalendars(user);
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

    Collection<CoreEventInfo> events = getFreeBusyEntities(cals, start, end, ignoreTransparency);
    BwEvent fb = new BwEventObj();

    fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);
    fb.setOwnerHref(who.getPrincipalRef());
    fb.setDtstart(start);
    fb.setDtend(end);
    //assignGuid(fb);

    try {
      TreeSet<EventPeriod> eventPeriods = new TreeSet<EventPeriod>();

      for (CoreEventInfo ei: events) {
        BwEvent ev = ei.getEvent();

        // Ignore if times were specified and this event is outside the times

        BwDateTime estart = ev.getDtstart();
        BwDateTime eend = ev.getDtend();

        /* Don't report out of the requested period */

        String dstart;
        String dend;

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

        DateTime psdt = new DateTime(dstart);
        DateTime pedt = new DateTime(dend);

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

      for (EventPeriod ep: eventPeriods) {
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
    } catch (Throwable t) {
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
    UpdateEventResult uer = events.addEvent(ei, scheduling,
                                            rollbackOnError);

    if (!forRestore) {
      calendars.touchCalendar(ei.getEvent().getColPath());
    }

    return uer;
  }

  @Override
  public UpdateEventResult updateEvent(final EventInfo ei) throws CalFacadeException {
    checkOpen();
    UpdateEventResult ue = null;

    try {
      calendars.touchCalendar(ei.getEvent().getColPath());

      ue = events.updateEvent(ei);
    } catch (CalFacadeException cfe) {
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
    String colPath = ei.getEvent().getColPath();
    try {
      try {
        return events.deleteEvent(ei, scheduling, reallyDelete);
      } catch (CalFacadeException cfe) {
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

  /* (non-Javadoc)
   * @see org.bedework.calcorei.EventsI#getEventKeys()
   */
  @Override
  public Collection<? extends InternalEventKey> getEventKeysForTzupdate(final String lastmod)
          throws CalFacadeException {
    return events.getEventKeysForTzupdate(lastmod);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.EventsI#getEvent(org.bedework.calcorei.EventsI.InternalEventKey)
   */
  @Override
  public CoreEventInfo getEvent(final InternalEventKey key)
          throws CalFacadeException {
    // Only for super user?

    if (!getSuperUser()) {
      throw new CalFacadeAccessException();
    }

    return events.getEvent(key);
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
  public void saveOrUpdate(final BwUnversionedDbentity val) throws CalFacadeException {
    sess.saveOrUpdate(val);
  }

  @Override
  public void delete(final BwUnversionedDbentity val) throws CalFacadeException {
    sess.delete(val);
  }

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    return (BwUnversionedDbentity)sess.merge(val);
  }

  @Override
  public Collection getObjectCollection(final String className) throws CalFacadeException {
    sess.createQuery("from " + className);

    return sess.getList();
  }

  private static String getEventAnnotationsQuery =
      "from " + BwEventAnnotation.class.getName() +
      " where recurrenceId=null";

  @Override
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException {
    sess.createQuery(getEventAnnotationsQuery);

    @SuppressWarnings("unchecked")
    Collection<BwEventAnnotation> anns = sess.getList();

    return anns.iterator();
  }

  private static String getEventOverridesQuery =
      "from " + BwEventAnnotation.class.getName() +
      " where recurrenceId<>null " +
       " and target=:target";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwEventAnnotation> getEventOverrides(final BwEvent ev) throws CalFacadeException {
    sess.createQuery(getEventOverridesQuery);
    sess.setEntity("target", ev);

    return sess.getList();
  }

  /* ====================================================================
   *                       filter defs
   * ==================================================================== */

  @Override
  public void save(final BwFilterDef val,
                   final BwPrincipal owner) throws CalFacadeException {
    filterDefs.save(val, owner);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal owner) throws CalFacadeException {
    return filterDefs.getFilterDef(name, owner);
  }

  @Override
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal owner) throws CalFacadeException {
    return filterDefs.getAllFilterDefs(owner);
  }

  @Override
  public void update(final BwFilterDef val) throws CalFacadeException {
    filterDefs.update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal owner) throws CalFacadeException {
    filterDefs.deleteFilterDef(name, owner);
  }

  /* ====================================================================
   *                       user auth
   * ==================================================================== */

  @Override
  public BwAuthUser getAuthUser(final String href) throws CalFacadeException {
    return userauth.getAuthUser(href);
  }

  @Override
  public List<BwAuthUser> getAll() throws CalFacadeException {
    return userauth.getAll();
  }

  /* ====================================================================
   *                       principals + prefs
   * ==================================================================== */

  private static String getPrincipalQuery =
    "from " + BwUser.class.getName() +
      " as u where u.principalRef = :href";

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    if (href == null) {
      return null;
    }

    /* XXX We should cache these as a static map and return detached objects only.
     * Updating the user for logon etc should be a separate method,
     *
     * Also - we are searching the user table at the moment. Make this into a
     * principal table and allow any principal to log on and own entities.
     */

    sess.createQuery(getPrincipalQuery);

    sess.setString("href", href);

    return (BwPrincipal)sess.getUnique();
  }

  private static String getPrincipalHrefsQuery =
      "select u.principalRef from " + BwUser.class.getName() +
        " u order by u.principalRef";

  @Override
  public List<String> getPrincipalHrefs(final int start,
                                        final int count) throws CalFacadeException {
    sess.createQuery(getPrincipalHrefsQuery);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    @SuppressWarnings("unchecked")
    List<String> res = sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  private static final String getOwnerPreferencesQuery =
      "from " + BwPreferences.class.getName() + " p " +
        "where p.ownerHref=:ownerHref";

  @Override
  public BwPreferences getPreferences(final String principalHref) throws CalFacadeException {
    sess.createQuery(getOwnerPreferencesQuery);
    sess.setString("ownerHref", principalHref);
    sess.cacheableQuery();

    return (BwPreferences)sess.getUnique();
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  private static final String removeCalendarPrefForAllQuery =
      "delete from " + BwAuthUserPrefsCalendar.class.getName() +
      " where calendarid=:id";

  //private static final String removeCategoryPrefForAllQuery =
    //  "delete from " + BwAuthUserPrefsCategory.class.getName() +
      //" where uid=:uid";

  private static final String getCategoryPrefForAllQuery =
      "from " + BwAuthUserPrefs.class.getName() +
      " where (:uid in categoryPrefs.preferred)";

  private static final String removeLocationPrefForAllQuery =
      "delete from " + BwAuthUserPrefsLocation.class.getName() +
      " where locationid=:id";

  private static final String removeContactPrefForAllQuery =
      "delete from " + BwAuthUserPrefsContact.class.getName() +
      " where contactid=:id";

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity val) throws CalFacadeException {
    String q;

    if (val instanceof BwCategory) {
      /* Try to do this without cheating. We can't do this deletion in nosql anyway
       */
      String uid = ((BwCategory)val).getUid();
      q = getCategoryPrefForAllQuery;

      sess.createQuery(q);
      sess.setString("uid", uid);

      @SuppressWarnings("unchecked")
      List<BwAuthUserPrefs> prefs = sess.getList();

      if (!Util.isEmpty(prefs)) {
        for (BwAuthUserPrefs pref: prefs) {
          pref.getCategoryPrefs().remove(uid);
          sess.update(pref);
        }
      }
    } else {
      if (val instanceof BwCalendar) {
        q = removeCalendarPrefForAllQuery;
      } else if (val instanceof BwContact) {
        q = removeContactPrefForAllQuery;
      } else if (val instanceof BwLocation) {
        q = removeLocationPrefForAllQuery;
      } else {
        throw new CalFacadeException("Can't handle " + val);
      }

      sess.createQuery(q);
      sess.setInt("id", val.getId());

      sess.executeUpdate();
    }
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  private static final String getAdminGroupQuery =
      "from " + BwAdminGroup.class.getName() + " ag " +
          "where ag.account = :account";

  private static final String getGroupQuery =
      "from " + BwGroup.class.getName() + " g " +
          "where g.account = :account";

  @Override
  public BwGroup findGroup(final String account,
                           final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(getAdminGroupQuery);
    } else {
      sess.createQuery(getGroupQuery);
    }

    sess.setString("account", account);

    return (BwGroup)sess.getUnique();
  }

  private static final String getAdminGroupParentsQuery =
      "select ag from " +
          "org.bedework.calfacade.svc.BwAdminGroupEntry age, " +
          "org.bedework.calfacade.svc.BwAdminGroup ag " +
          "where ag.id = age.groupId and " +
          "age.memberId=:grpid and age.memberIsGroup=true";

  private static final String getGroupParentsQuery =
      "select g from " +
          "org.bedework.calfacade.BwGroupEntry ge, " +
          "org.bedework.calfacade.BwGroup g " +
          "where g.id = ge.groupId and " +
          "ge.memberId=:grpid and ge.memberIsGroup=true";

  /**
   * @param group
   * @param admin          true for an admin group
   * @return Collection
   * @throws CalFacadeException
   */
  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwGroup> findGroupParents(final BwGroup group,
                                       final boolean admin) throws CalFacadeException {

    if (admin) {
      sess.createQuery(getAdminGroupParentsQuery);
    } else {
      sess.createQuery(getGroupParentsQuery);
    }

    sess.setInt("grpid", group.getId());

    return sess.getList();
 }
  /**
   * @param group
   * @param admin          true for an admin group
   * @throws CalFacadeException
   */
  @Override
  public void updateGroup(final BwGroup group,
                          final boolean admin) throws CalFacadeException {
    sess.saveOrUpdate(group);
  }

  private static final String removeAllAdminGroupMemberRefsQuery =
      "delete from " +
          "org.bedework.calfacade.svc.BwAdminGroupEntry " +
          "where grp=:gr";

  private static final String removeAllGroupMembersQuery =
      "delete from " +
          "org.bedework.calfacade.BwGroupEntry " +
          "where grp=:gr";

  private static final String removeFromAllAdminGroupsQuery =
      "delete from " +
          "org.bedework.calfacade.svc.BwAdminGroupEntry " +
          "where memberId=:mbrId and memberIsGroup=:isgroup";

  private static final String removeFromAllGroupsQuery =
      "delete from " +
          "org.bedework.calfacade.BwGroupEntry " +
          "where memberId=:mbrId and memberIsGroup=:isgroup";

  /** Delete a group
   *
   * @param  group           BwGroup group object to delete
   * @exception CalFacadeException If there's a problem
   */
  @Override
  public  void removeGroup(final BwGroup group,
                           final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(removeAllAdminGroupMemberRefsQuery);
    } else {
      sess.createQuery(removeAllGroupMembersQuery);
    }

    sess.setEntity("gr", group);
    sess.executeUpdate();

    // Remove from any groups

    if (admin) {
      sess.createQuery(removeFromAllAdminGroupsQuery);
    } else {
      sess.createQuery(removeFromAllGroupsQuery);
    }

    sess.setInt("mbrId", group.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    */
    sess.setString("isgroup", "T");
    sess.executeUpdate();

    sess.delete(group);
  }

  @Override
  public void addMember(final BwGroup group,
                        final BwPrincipal val,
                        final boolean admin) throws CalFacadeException {
    BwGroupEntry ent;

    if (admin) {
      ent = new BwAdminGroupEntry();
    } else {
      ent = new BwGroupEntry();
    }

    ent.setGrp(group);
    ent.setMember(val);

    sess.saveOrUpdate(ent);
  }

  private static final String findAdminGroupEntryQuery =
      "from org.bedework.calfacade.svc.BwAdminGroupEntry " +
          "where grp=:grp and memberId=:mbrId and memberIsGroup=:isgroup";

  private static final String findGroupEntryQuery =
      "from org.bedework.calfacade.BwGroupEntry " +
          "where grp=:grp and memberId=:mbrId and memberIsGroup=:isgroup";

  @Override
  public void removeMember(final BwGroup group,
                           final BwPrincipal val,
                           final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(findAdminGroupEntryQuery);
    } else {
      sess.createQuery(findGroupEntryQuery);
    }

    sess.setEntity("grp", group);
    sess.setInt("mbrId", val.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    */
    if (val instanceof BwGroup) {
      sess.setString("isgroup", "T");
    } else {
      sess.setString("isgroup", "F");
    }

    Object ent = sess.getUnique();

    if (ent == null) {
      return;
    }

    sess.delete(ent);
  }

  private static final String getAdminGroupUserMembersQuery =
      "select u from " +
          "org.bedework.calfacade.svc.BwAdminGroupEntry age, " +
          "org.bedework.calfacade.BwUser u " +
          "where u.id = age.memberId and " +
          "age.grp=:gr and age.memberIsGroup=false";

  private static final String getAdminGroupGroupMembersQuery =
      "select ag from " +
          "org.bedework.calfacade.svc.BwAdminGroupEntry age, " +
          "org.bedework.calfacade.svc.BwAdminGroup ag " +
          "where ag.id = age.memberId and " +
          "age.grp=:gr and age.memberIsGroup=true";

  private static final String getGroupUserMembersQuery =
      "select u from " +
          "org.bedework.calfacade.BwGroupEntry ge, " +
          "org.bedework.calfacade.BwUser u " +
          "where u.id = ge.memberId and " +
             "ge.grp=:gr and ge.memberIsGroup=false";

  private static final String getGroupGroupMembersQuery =
      "select g from " +
          "org.bedework.calfacade.BwGroupEntry ge, " +
          "org.bedework.calfacade.BwGroup g " +
          "where g.id = ge.memberId and " +
          "ge.grp=:gr and ge.memberIsGroup=true";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwPrincipal> getMembers(final BwGroup group,
                                            final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(getAdminGroupUserMembersQuery);
    } else {
      sess.createQuery(getGroupUserMembersQuery);
    }

    sess.setEntity("gr", group);

    Collection<BwPrincipal> ms = new TreeSet<BwPrincipal>();
    ms.addAll(sess.getList());

    if (admin) {
      sess.createQuery(getAdminGroupGroupMembersQuery);
    } else {
      sess.createQuery(getGroupGroupMembersQuery);
    }

    sess.setEntity("gr", group);

    ms.addAll(sess.getList());

    return ms;
  }

  private static final String getAllAdminGroupsQuery =
      "from " + BwAdminGroup.class.getName() + " ag " +
        "order by ag.account";

  private static final String getAllGroupsQuery =
      "from " + BwGroup.class.getName() + " g " +
        "order by g.account";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwGroup> getAllGroups(final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(getAllAdminGroupsQuery);
    } else {
      sess.createQuery(getAllGroupsQuery);
    }

    return sess.getList();
  }

  /* Groups principal is a member of */
  private static final String getAdminGroupsQuery =
      "select ag.grp from org.bedework.calfacade.svc.BwAdminGroupEntry ag " +
        "where ag.memberId=:entId and ag.memberIsGroup=:isgroup";

  /* Groups principal is a event owner for */
  private static final String getAdminGroupsByEventOwnerQuery =
      "from org.bedework.calfacade.svc.BwAdminGroup ag " +
        "where ag.ownerHref=:ownerHref";

  private static final String getGroupsQuery =
      "select g.grp from org.bedework.calfacade.BwGroupEntry g " +
        "where g.memberId=:entId and g.memberIsGroup=:isgroup";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwGroup> getGroups(final BwPrincipal val,
                                       final boolean admin) throws CalFacadeException {
    if (admin) {
      sess.createQuery(getAdminGroupsQuery);
    } else {
      sess.createQuery(getGroupsQuery);
    }

    sess.setInt("entId", val.getId());

    /* This is what I want to do but it inserts 'true' or 'false'
    sess.setBool("isgroup", (val instanceof BwGroup));
    */
    if (val.getKind() == WhoDefs.whoTypeGroup) {
      sess.setString("isgroup", "T");
    } else {
      sess.setString("isgroup", "F");
    }

    Set<BwGroup> gs = new TreeSet<BwGroup>(sess.getList());

    if (admin && (val.getKind() == WhoDefs.whoTypeUser)) {
      /* Event owner for group is implicit member of group. */

      sess.createQuery(getAdminGroupsByEventOwnerQuery);
      sess.setString("ownerHref", val.getPrincipalRef());

      gs.addAll(sess.getList());
    }

    return gs;
  }

  /* ====================================================================
   *                       calendar suites
   * ==================================================================== */

  private static final String getCalSuiteByGroupQuery =
    "from org.bedework.calfacade.svc.BwCalSuite cal " +
      "where cal.group=:group";

  @Override
  public BwCalSuite get(final BwAdminGroup group) throws CalFacadeException {
    sess.createQuery(getCalSuiteByGroupQuery);

    sess.setEntity("group", group);
    sess.cacheableQuery();

    BwCalSuite cs = (BwCalSuite)sess.getUnique();

    if (cs != null){
      sess.evict(cs);
    }

    return cs;
  }

  private static final String getCalSuiteQuery =
    "from org.bedework.calfacade.svc.BwCalSuite cal " +
      "where cal.name=:name";

  @Override
  public BwCalSuite getCalSuite(final String name) throws CalFacadeException {
    sess.createQuery(getCalSuiteQuery);

    sess.setString("name", name);
    sess.cacheableQuery();

    return (BwCalSuite)sess.getUnique();
  }

  private static final String getAllCalSuitesQuery =
      "from " + BwCalSuite.class.getName();

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwCalSuite> getAllCalSuites() throws CalFacadeException {
    sess.createQuery(getAllCalSuitesQuery);

    sess.cacheableQuery();

    return sess.getList();
  }

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  /** Return an event properties handler.
   *
   * @return EventProperties
   * @throws CalFacadeException
   */
  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwEventProperty> CoreEventPropertiesI<T>  getEvPropsHandler(final Class<T> cl)
        throws CalFacadeException {
    if (cl.equals(BwCategory.class)) {
      if (categoriesHandler == null) {
        categoriesHandler =
            new CoreEventProperties<BwCategory>(chcb, cb,
                access, currentMode, sessionless,
                      BwCategory.class.getName());
      }

      return (CoreEventPropertiesI<T>)categoriesHandler;
    }

    if (cl.equals(BwContact.class)) {
      if (contactsHandler == null) {
        contactsHandler =
            new CoreEventProperties<BwContact>(chcb, cb,
                access, currentMode, sessionless,
                BwContact.class.getName());
      }

      return (CoreEventPropertiesI<T>)contactsHandler;
    }

    if (cl.equals(BwLocation.class)) {
      if (locationsHandler == null) {
        locationsHandler =
            new CoreEventProperties<BwLocation>(chcb, cb,
                access, currentMode, sessionless,
                BwLocation.class.getName());
      }

      return (CoreEventPropertiesI<T>)locationsHandler;
    }

    throw new RuntimeException("Should not get here");
  }

  /* ====================================================================
   *                       resources
   * ==================================================================== */

  private static final String getResourceQuery =
      "from " + BwResource.class.getName() +
          " where name=:name and colPath=:path" +
          " and (encoding is null or encoding <> :tsenc)";

  @Override
  public BwResource getResource(final String name,
                                final BwCalendar coll,
                                final int desiredAccess) throws CalFacadeException {
    sess.createQuery(getResourceQuery);

    sess.setString("name", name);
    sess.setString("path", coll.getPath());
    sess.setString("tsenc", BwResource.tombstoned);
    sess.cacheableQuery();

    BwResource res = (BwResource)sess.getUnique();
    if (res == null) {
      return res;
    }

    CurrentAccess ca = checkAccess(res, desiredAccess, true);

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return res;
  }

  private static final String getResourceContentQuery =
      "from " + BwResourceContent.class.getName() +
      " as rc where rc.colPath=:path and rc.name=:name";

  @Override
  public void getResourceContent(final BwResource val) throws CalFacadeException {
    sess.createQuery(getResourceContentQuery);
    sess.setString("path", val.getColPath());
    sess.setString("name", val.getName());
    sess.cacheableQuery();

    BwResourceContent rc = (BwResourceContent)sess.getUnique();
    if (rc == null) {
      throw new CalFacadeException(CalFacadeException.missingResourceContent);
    }

    val.setContent(rc);
  }

  private static final String getAllResourcesQuery =
      "from " + BwResource.class.getName() +
      " as r where r.colPath=:path" +
      // No deleted collections for null sync-token or not sync
      " and (r.encoding is null or r.encoding <> :tsenc)";

  private static final String getAllResourcesSynchQuery =
      "from " + BwResource.class.getName() +
      " as r where r.colPath=:path" +
      // Include deleted resources after the token.
      " and (r.lastmod>:lastmod" +
      " or (r.lastmod=:lastmod and r.sequence>:seq))";

  @SuppressWarnings("unchecked")
  @Override
  public List<BwResource> getAllResources(final String path,
                                          final boolean forSynch,
                                          final String token) throws CalFacadeException {
    if (forSynch && (token != null)) {
      sess.createQuery(getAllResourcesSynchQuery);
    } else {
      sess.createQuery(getAllResourcesQuery);
    }

    sess.setString("path", path);

    if (forSynch && (token != null)) {
      sess.setString("lastmod", token.substring(0, 16));
      sess.setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsenc", BwResource.tombstoned);
    }

    sess.cacheableQuery();

    return postProcess(sess.getList());
  }

  /* ====================================================================
   *                       system parameters
   * ==================================================================== */

  private static final String getSystemParsQuery =
      "from " + BwSystem.class.getName() + " as sys " +
      "where sys.name = :name";

  @Override
  public BwSystem getSyspars(final String name) throws CalFacadeException {
    sess.createQuery(getSystemParsQuery);

    sess.setString("name", name);

    return (BwSystem)sess.getUnique();
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private List<BwResource> postProcess(final Collection<BwResource> ress) throws CalFacadeException {
    List<BwResource> resChecked = new ArrayList<BwResource>();

    for (BwResource res: ress) {
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

    synchronized (this) {
      if (sessionFactory != null) {
        return sessionFactory;
      }

      /** Get a new hibernate session factory. This is configured from an
       * application resource hibernate.cfg.xml together with some run time values
       */
      try {
        DbConfig dbConf = CoreConfigurations.getConfigs().getDbConfig();
        Configuration conf = new Configuration();

        StringBuilder sb = new StringBuilder();

        @SuppressWarnings("unchecked")
        List<String> ps = dbConf.getHibernateProperties();

        for (String p: ps) {
          sb.append(p);
          sb.append("\n");
        }

        Properties hprops = new Properties();
        hprops.load(new StringReader(sb.toString()));

        conf.addProperties(hprops).configure();

        sessionFactory = conf.buildSessionFactory();

        return sessionFactory;
      } catch (Throwable t) {
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
    FilterBase filter = new OrFilter();
    try {
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "event",
                                                            false));
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "freeAndBusy",
                                                            false));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    RecurringRetrievalMode rrm = new RecurringRetrievalMode(
                        Rmode.expanded, start, end);
    Collection<CoreEventInfo> evs = getEvents(cals, filter, start, end,
                                              null, rrm, true);

    // Filter out transparent and cancelled events

    Collection<CoreEventInfo> events = new TreeSet<CoreEventInfo>();

    for (CoreEventInfo cei: evs) {
      BwEvent ev = cei.getEvent();

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
