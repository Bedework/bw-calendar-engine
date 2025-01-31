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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.hibernate.daoimpl.AccessDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.AlarmsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.CalSuitesDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.CalendarsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.CoreEventPropertiesDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.EventsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.FilterDefsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.IteratorsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.PrincipalsAndPrefsDAOImpl;
import org.bedework.calcore.hibernate.daoimpl.ResourcesDAOImpl;
import org.bedework.calcore.rw.common.CalintfCommonImpl;
import org.bedework.calcore.rw.common.CoreAccess;
import org.bedework.calcore.rw.common.CoreAlarms;
import org.bedework.calcore.rw.common.CoreCalSuites;
import org.bedework.calcore.rw.common.CoreCalendars;
import org.bedework.calcore.rw.common.CoreDumpRestore;
import org.bedework.calcore.rw.common.CoreEventProperties;
import org.bedework.calcore.rw.common.CoreEvents;
import org.bedework.calcore.rw.common.CoreFilterDefs;
import org.bedework.calcore.rw.common.CorePrincipalsAndPrefs;
import org.bedework.calcore.rw.common.CoreResources;
import org.bedework.calcore.rw.common.dao.DAOBase;
import org.bedework.calcorei.CalintfInfo;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.database.db.DbSession;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.stat.Statistics;

import java.io.StringReader;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.lang.String.format;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeEvent;

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
public class CalintfImpl extends CalintfCommonImpl {
  private static final BwStats stats = new BwStats();

  private static final CalintfInfo info = new CalintfInfo(
       true,     // handlesLocations
       true,     // handlesContacts
       true      // handlesCategories
     );

  private final IfInfo ifInfo = new IfInfo();

  private CoreAccess access;

  private CoreAlarms alarms;

  private CoreCalendars calendars;

  private CoreCalSuites calSuites;

  private CoreDumpRestore dumpRestore;

  private CoreEvents events;

  private CoreEventPropertiesI<BwCategory> categoriesHandler;

  private CoreEventPropertiesI<BwLocation> locationsHandler;

  private CoreEventPropertiesI<BwContact> contactsHandler;

  private CoreFilterDefs filterDefs;

  private CorePrincipalsAndPrefs principalsAndPrefs;

  private CoreResources resources;

  private final Map<String, DAOBase> daos = new HashMap<>();

  /* Prevent updates.
   */
  //sprivate boolean readOnly;

  /** Current database session - exists only across one user interaction
   */
  private DbSession sess;

  private Timestamp curTimestamp;

  /** We make this static for this implementation so that there is only one
   * SessionFactory per server for the calendar.
   *
   * <p>static fields used this way may be illegal in the j2ee specification
   * though we might get away with it here as the session factory only
   * contains parsed mappings for the calendar configuration. This should
   * be the same for any machine in a cluster so it might work OK.
   *
   * <p>It might be better to find some other approach for the j2ee world.
   */
  private static SessionFactory sessionFactory;
  private static Statistics dbStats;
  
  private final static Object syncher = new Object();

  protected static class IndexEntry {
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
      sess = new HibSessionImpl();
      sess.init(getSessionFactory());
      if (debug()) {
        debug(format("New database session (class %s) for %s",
                     sess.getClass(), getTraceId()));
      }
    }

    if (access == null) {
      final var dao = new AccessDAOImpl(sess);
      registerDao(dao);
      access = new CoreAccess(dao, this, ac, sessionless);
    }

    if (alarms == null) {
      final var dao = new AlarmsDAOImpl(sess);
      registerDao(dao);
      alarms = new CoreAlarms(dao, this, ac, sessionless);
    }

    if (calSuites == null) {
      final var dao = new CalSuitesDAOImpl(sess);
      registerDao(dao);
      calSuites = new CoreCalSuites(dao, this, ac, sessionless);
    }

    if (dumpRestore == null) {
      final var dao = new IteratorsDAOImpl(sess);
      registerDao(dao);
      dumpRestore = new CoreDumpRestore(dao, this, ac, sessionless);
    }

    if (principalsAndPrefs == null) {
      final var dao = new PrincipalsAndPrefsDAOImpl(sess);
      registerDao(dao);
      principalsAndPrefs = new CorePrincipalsAndPrefs(dao,
                                                      this,
                                                      ac, sessionless);
    }

    if (filterDefs == null) {
      final var dao = new FilterDefsDAOImpl(sess);
      registerDao(dao);
      filterDefs = new CoreFilterDefs(dao, this, ac, sessionless);
    }

    /* Reset the session in the daos.
     */
    for (final var dao: daos.values()) {
      dao.setSess(sess);
    }
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

    final var evDao = new EventsDAOImpl(sess);
    registerDao(evDao);
    events = new CoreEvents(evDao, this,
                            ac, authProps, sessionless);

    final var calDao = new CalendarsDAOImpl(sess);
    registerDao(calDao);
    calendars = new CoreCalendars(calDao,
                                  this,
                                  ac, sessionless);

    final var resDao = new ResourcesDAOImpl(sess);
    registerDao(resDao);
    resources = new CoreResources(resDao, this,
                                  ac, sessionless);

    accessUtil.setCollectionGetter(calendars);
  }

  /* ====================================================================
   *                   abstract methods implementations
   * ==================================================================== */

  @Override
  protected CoreAccess access() {
    return access;
  }

  @Override
  protected CoreAlarms alarms() {
    return alarms;
  }

  @Override
  protected CoreCalendars calendars() {
    return calendars;
  }

  @Override
  protected CoreCalSuites calSuites() {
    return calSuites;
  }

  @Override
  protected CoreDumpRestore dumpRestore() {
    return dumpRestore;
  }

  @Override
  protected CoreEvents events() {
    return events;
  }

  @Override
  protected CoreFilterDefs filterDefs() {
    return filterDefs;
  }

  @Override
  protected CorePrincipalsAndPrefs principalsAndPrefs() {
    return principalsAndPrefs;
  }

  @Override
  protected CoreResources resources() {
    return resources;
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

  private void registerDao(final DAOBase dao) {
    final var entry = getDaos().get(dao.getName());

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

  /* ==========================================================
   *                   Db methods
   * ========================================================== */

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

      if (accessUtil != null) {
        accessUtil.close();
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

  /* ====================================================================
   *                   Event Properties Factories
   * ==================================================================== */

  @SuppressWarnings("unchecked")
  @Override
  public <T extends BwEventProperty<?>> CoreEventPropertiesI<T> getEvPropsHandler(final Class<T> cl) {
    if (cl.equals(BwCategory.class)) {
      if (categoriesHandler == null) {
        final var dao = new CoreEventPropertiesDAOImpl(
                sess,
                BwCategory.class.getName());
        registerDao(dao);

        categoriesHandler =
            new CoreEventProperties<>(dao, this,
                                      ac, sessionless);
      }

      return (CoreEventPropertiesI<T>)categoriesHandler;
    }

    if (cl.equals(BwContact.class)) {
      if (contactsHandler == null) {
        final var dao = new CoreEventPropertiesDAOImpl(
                sess,
                BwContact.class.getName());
        registerDao(dao);

        contactsHandler =
                new CoreEventProperties<>(dao, this,
                                          ac,
                                          sessionless);
      }

      return (CoreEventPropertiesI<T>)contactsHandler;
    }

    if (cl.equals(BwLocation.class)) {
      final var dao = new CoreEventPropertiesDAOImpl(
              sess,
              BwLocation.class.getName());
      registerDao(dao);

      if (locationsHandler == null) {
        locationsHandler =
                new CoreEventProperties<>(dao, this,
                                          ac,
                                          sessionless);
      }

      return (CoreEventPropertiesI<T>)locationsHandler;
    }

    throw new BedeworkException("Should not get here");
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
