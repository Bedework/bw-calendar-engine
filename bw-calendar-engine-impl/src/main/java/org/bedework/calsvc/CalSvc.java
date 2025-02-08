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
package org.bedework.calsvc;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeSet;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.persist.BedeworkConstraintViolationException;
import org.bedework.base.response.GetEntitiesResponse;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.base.response.Response;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfFactory;
import org.bedework.caldav.util.notifications.admin.AdminNoteParsers;
import org.bedework.caldav.util.notifications.eventreg.EventregParsers;
import org.bedework.caldav.util.notifications.suggest.SuggestParsers;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.CacheStats;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.OwnedEntity;
import org.bedework.calfacade.base.ShareableEntity;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.ifs.IfInfo;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.calsvc.scheduling.Scheduling;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvc.scheduling.hosts.BwHosts;
import org.bedework.calsvci.AdminI;
import org.bedework.calsvci.CalSuitesI;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.Categories;
import org.bedework.calsvci.Contacts;
import org.bedework.calsvci.DumpIntf;
import org.bedework.calsvci.EventsI;
import org.bedework.calsvci.FiltersI;
import org.bedework.calsvci.Locations;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.PreferencesI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.RestoreIntf;
import org.bedework.calsvci.SchedulingI;
import org.bedework.calsvci.SharingI;
import org.bedework.calsvci.SynchI;
import org.bedework.calsvci.SynchReport;
import org.bedework.calsvci.SynchReportItem;
import org.bedework.calsvci.SysparsI;
import org.bedework.calsvci.TimeZonesStoreI;
import org.bedework.calsvci.UsersI;
import org.bedework.calsvci.ViewsI;
import org.bedework.database.db.StatsEntry;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.jmx.MBeanUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.security.PwEncryptionIntf;
import org.bedework.util.security.keys.GenKeysMBean;
import org.bedework.util.timezones.Timezones;

import org.apache.james.jdkim.api.JDKIM;

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

import static java.lang.String.format;

/** This is an implementation of the service level interface to the calendar
 * suite.
 *
 * @author Mike Douglass       douglm rpi.edu
 */
public class CalSvc
        implements CalSvcI, Logged, Calintf.FilterParserFetcher {
  //private String systemName;

  private CalSvcIPars pars;

  private static Configurations configs;

  static {
    try {
      configs = CalSvcFactoryDefault.getSystemConfig();
      new SuggestParsers(); // force load
      new EventregParsers(); // force load
      new AdminNoteParsers(); // force load
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  private boolean open;

  //private boolean superUser;

  /** True if this is a session to create a new account. Do not try to create one
   */
  private boolean creating;

  private boolean authenticated;

  /* Information about current user */
  private PrincipalInfo principalInfo;

  /* The account that we are representing
   */
//  private BwUser currentUser;

  /* The account we logged in as - for user access equals currentUser, for admin
   * access currentUser is the group we are managing.
   */
//  private BwUser currentAuthUser;

  /* ....................... Handlers ..................................... */

  //private MailerIntf mailer;

  private PreferencesI prefsHandler;

  private AdminI adminHandler;

  private EventsI eventsHandler;

  private FiltersI filtersHandler;

  private CalendarsI calendarsHandler;

  private SysparsI sysparsHandler;

  private CalSuitesI calSuitesHandler;

  private NotificationsI notificationsHandler;

  private ResourcesI resourcesHandler;

  private SchedulingIntf sched;

  private SharingI sharingHandler;

  private SynchI synch;

  private UsersI usersHandler;

  private ViewsI viewsHandler;

  private Categories categoriesHandler;

  private Locations locationsHandler;

  private Contacts contactsHandler;

  private final Collection<CalSvcDb> handlers = new ArrayList<>();

  /* ....................... ... ..................................... */

  /** Core calendar interface
   */
  private transient Calintf cali;

  private transient PwEncryptionIntf pwEncrypt;

  /** handles timezone info.
   */
  private Timezones timezones;
  private TimeZonesStoreI tzstore;

  /* null if timezones not initialised */
  private static String tzserverUri = null;

  /** The user authorisation object
   */
  private UserAuth userAuth;

  private transient UserAuth.CallBack uacb;

  private transient Directories.CallBack gcb;

  /** The user groups object.
   */
  private Directories userGroups;

  /** The admin groups object.
   */
  private Directories adminGroups;

  private IcalCallback icalcb;

  @Override
  public void init(final CalSvcIPars parsParam) {
    init(parsParam, parsParam.getForRestore());
  }

  private void init(final CalSvcIPars parsParam,
                    final boolean creating) {
    pars = (CalSvcIPars)parsParam.clone();

    this.creating = creating;

    final long start = System.currentTimeMillis();

    fixUsers();

    try {
      if (configs == null) {
        // Try again - failed at static init?
        configs = CalSvcFactoryDefault.getSystemConfig();
      }

      open();

      if (trace()) {
        trace(format("svc after open %s",
                     System.currentTimeMillis() - start));
      }

      if (userGroups != null) {
        userGroups.init(getGroupsCallBack(),
                        configs);
      }

      if (adminGroups != null) {
        adminGroups.init(getGroupsCallBack(),
                         configs);
      }

      final SystemProperties sp = getSystemProperties();

      if (tzserverUri == null) {
        tzserverUri = sp.getTzServeruri();

        if (tzserverUri == null) {
          throw new BedeworkException("No timezones server URI defined");
        }

        Timezones.initTimezones(tzserverUri);

        Timezones.setSystemDefaultTzid(sp.getTzid());
      }

      /* Some checks on parameter validity
       */
      //        BwUser =

      tzstore = new TimeZonesStoreImpl(this);

      /* Nominate our timezone registry */
      System.setProperty(
              "net.fortuna.ical4j.timezone.registry",
              "org.bedework.util.timezones.TimeZoneRegistryFactoryImpl");
      System.setProperty(
              "net.fortuna.ical4j.timezone.cache.impl",
              "net.fortuna.ical4j.util.MapTimeZoneCache");

      if (!creating) {
        final String tzid = getPrefsHandler().get().getDefaultTzid();

        if (tzid != null) {
          Timezones.setThreadDefaultTzid(tzid);
        }

        //        if (pars.getCaldav() && !pars.isGuest()) {
        if (!pars.isGuest()) {
          /* Ensure scheduling resources exist */
//          getCal().getSpecialCalendar(getPrincipal(), BwCalendar.calTypeInbox,
  //                                    true, PrivilegeDefs.privAny);

    //      getCal().getSpecialCalendar(getPrincipal(), BwCalendar.calTypeOutbox,
      //                                true, PrivilegeDefs.privAny);
        }
      }
      if (trace()) {
        trace(format("svc after tzs %s",
                     System.currentTimeMillis() - start));
      }

      if ((pars.getPublicAdmin() || pars.getAllowSuperUser()) &&
          (pars.getAuthUser() != null)) {
        ((SvciPrincipalInfo)principalInfo).setSuperUser(
             getSysparsHandler().isRootUser(principalInfo.getAuthPrincipal()));
      }

      postNotification(
        SysEvent.makePrincipalEvent(
                SysEvent.SysCode.USER_SVCINIT,
                getPrincipal().getPrincipalRef(),
                System.currentTimeMillis() - start));
    } catch (final Throwable t) {
      if ((t instanceof RuntimeException) &&
              t.getMessage().equals(upgradeToReadWriteMessage)) {
        throw (RuntimeException)t;
      }

      t.printStackTrace();

      if (t instanceof RuntimeException) {
        throw (RuntimeException)t;
      }

      throw new RuntimeException(t);
    } finally {
      try {
        close();
      } catch (final Throwable ignored) {}
    }
    if (trace()) {
      trace(format("svc about to exit %s",
                   System.currentTimeMillis() - start));
    }
  }

  @Override
  public AuthProperties getAuthProperties() {
    if (!authenticated) {
      return configs.getUnauthenticatedAuthProperties();
    }

    return configs.getAuthenticatedAuthProperties();
  }

  @Override
  public SystemProperties getSystemProperties() {
    return configs.getSystemProperties();
  }

  @Override
  public JDKIM getJDKIM() {
    return BwHosts.getJDKIM();
  }

  @Override
  public NotificationProperties getNotificationProperties() {
    return configs.getNotificationProps();
  }

  @Override
  public void setCalSuite(final String name) {
    final BwCalSuiteWrapper cs = getCalSuitesHandler().get(name);

    if (cs == null) {
      error("******************************************************");
      error("Unable to fetch calendar suite " + name);
      error("Is the database correctly initialised?");
      error("******************************************************");
      throw new BedeworkException(CalFacadeErrorCode.unknownCalsuite,
                                  name);
    }

    getCalSuitesHandler().set(cs);

    /* This is wrong. The calsuite doesn't always represent the group
       It may be a sub-group.
    final BwPrincipal<?> user = getUsersHandler().getPrincipal(cs.getGroup().getOwnerHref());
    user.setGroups(getDirectories().getAllGroups(user));

    if (!user.equals(principalInfo.getPrincipal())) {
      ((SvciPrincipalInfo)principalInfo).setPrincipal(user);
    }
    */
  }

  @Override
  public PrincipalInfo getPrincipalInfo() {
    return principalInfo;
  }

  @Override
  public boolean getSuperUser() {
    return principalInfo.getSuperUser();
  }

  @Override
  public byte[] getPublicKey(final String domain,
                             final String service) {
    try {
      return getEncrypter().getPublicKey();
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  @Override
  public BwStats getStats() {
    final BwStats stats = getCal().getStats();

    if (timezones != null) {
      final CacheStats cs = stats.getDateCacheStats();

      cs.setHits(timezones.getDateCacheHits());
      cs.setMisses(timezones.getDateCacheMisses());
      cs.setCached(timezones.getDatesCached());
    }

    stats.setAccessStats(Access.getStatistics());

    return stats;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) {
    //if (!pars.getPublicAdmin()) {
    //  throw new BedeworkAccessException();
    //}

    getCal().setDbStatsEnabled(enable);
  }

  @Override
  public boolean getDbStatsEnabled() {
    return getCal().getDbStatsEnabled();
  }

  @Override
  public void dumpDbStats() {
    //if (!pars.getPublicAdmin()) {
    //  throw new BedeworkAccessException();
    //}

    trace(getStats().toString());
    getCal().dumpDbStats();
  }

  @Override
  public Collection<StatsEntry> getDbStats() {
    //if (!pars.getPublicAdmin()) {
    //  throw new BedeworkAccessException();
    //}

    return getCal().getDbStats();
  }

  @Override
  public void logStats() {
    info(getStats().toString());
  }

  @Override
  public List<IfInfo> getActiveIfInfos() {
    final List<IfInfo> ifs = new ArrayList<>();

    for (final Calintf ci: getCal().active()) {
      ifs.add(ci.getIfInfo());
    }

    return ifs;
  }

  @Override
  public void kill(final IfInfo ifInfo) {
    // We could probably use some sort of kill listener to clean up after this

    try {
      for (final Calintf ci: getCal().active()) {
        final IfInfo calIfInfo = ci.getIfInfo();
        
        if (calIfInfo.getId().equals(ifInfo.getId())) {
          warn("Stopping interface with id " + ifInfo.getId());

          ci.kill();
          break;
        }
      }
    } catch (final Throwable t) {
      error(t);
    }
  }

  @Override
  public void setState(final String val) {
    getCal().setState(val);
  }

  @Override
  public void postNotification(final SysEventBase ev) {
    getCal().postNotification(ev);
  }

  @Override
  public void flushAll() {
    getCal().flush();
  }

  @Override
  public void open() {
    //TimeZoneRegistryImpl.setThreadCb(getIcalCallback());

    if (open) {
      return;
    }

    open = true;
    final long start = System.currentTimeMillis();

    getCal().open(this,
                  pars.getLogId(),
                  configs,
                  pars.getForRestore(),
                  pars.getIndexRebuild(),
                  pars.getPublicAdmin(),
                  pars.getPublicAuth(),
                  pars.getPublicSubmission(),
                  authenticated,
                  pars.getSessionsless(),
                  pars.getDontKill());
    if (trace()) {
      trace(format("svc.open after getCal() %s",
                   System.currentTimeMillis() - start));
    }

    for (final CalSvcDb handler: handlers) {
      if (trace()) {
        trace(format("svc.open about to open %s after %s",
                     handler.getClass(),
                     System.currentTimeMillis() - start));
      }
      handler.open();
    }

    if (trace()) {
      trace(format("svc.open after open handlers %s",
                   System.currentTimeMillis() - start));
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isRolledback() {
    return open && getCal().isRolledback();

  }

  @Override
  public void close() {
    open = false;
    getCal().close();

    for (final CalSvcDb handler: handlers) {
      handler.close();
    }
  }

  @Override
  public void beginTransaction() {
    getCal().beginTransaction();
  }

  @Override
  public void endTransaction() {
    getCal().endTransaction();
  }

  @Override
  public void rollbackTransaction() {
    getCal().rollbackTransaction();
  }

  @Override
  public Timestamp getCurrentTimestamp() {
    return getCal().getCurrentTimestamp();
  }

  @Override
  public BwUnversionedDbentity<?> merge(final BwUnversionedDbentity<?> val) {
    if (val instanceof final CalendarWrapper w) {
      w.putEntity((BwCalendar)getCal().merge(w.fetchEntity()));
      return w;
    }

    return getCal().merge(val);
  }

  @Override
  public IcalCallback getIcalCallback() {
    if (icalcb == null) {
      icalcb = new IcalCallbackcb(null);
    }

    return icalcb;
  }

  @Override
  public IcalCallback getIcalCallback(final Boolean timezonesByReference) {
    if (icalcb == null) {
      icalcb = new IcalCallbackcb(timezonesByReference);
    }

    return icalcb;
  }

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  @Override
  public DumpIntf getDumpHandler() {
    return new DumpImpl(this);
  }

  @Override
  public RestoreIntf getRestoreHandler() {
    return new RestoreImpl(this);
  }

  class SvcSimpleFilterParser extends SimpleFilterParser {
    final FlushMap<String, BwCalendar> cols = new FlushMap<>(20, 60 * 1000,
                                                             100);

    @Override
    public BwCalendar getCollection(final String path) {
      BwCalendar col = cols.get(path);
      if (col != null) {
        return col;
      }

      col = getCalendarsHandler().get(path);
      cols.put(path, col);

      return col;
    }

    @Override
    public BwCalendar resolveAlias(final BwCalendar val,
                                   final boolean resolveSubAlias) {
      return getCalendarsHandler().resolveAlias(val, resolveSubAlias, false);
    }

    @Override
    public Collection<BwCalendar> getChildren(final BwCalendar col) {
      final String path = col.getPath();
      BwCalendar cachedCol = cols.get(path);

      if ((cachedCol != null) && (cachedCol.getChildren() != null)) {
        return col.getChildren();
      }

      final Collection<BwCalendar> children =
              getCalendarsHandler().getChildren(col);

      if (cachedCol == null) {
        cachedCol = col;
      }

      cachedCol.setChildren(children);

      cols.put(path, cachedCol);

      return children;
    }

    @Override
    public BwCategory getCategoryByName(final String name) {
      return getCategoriesHandler().find(new BwString(null, name));
    }

    @Override
    public GetEntityResponse<BwCategory> getCategoryByUid(final String uid)  {
      return getCategoriesHandler().getByUid(uid);
    }

    @Override
    public BwView getView(final String path) {
      return getViewsHandler().find(path);
    }

    @Override
    public Collection<BwCalendar> decomposeVirtualPath(final String vpath) {
      return getCalendarsHandler().decomposeVirtualPath(vpath);
    }

    @Override
    public SimpleFilterParser getParser() {
      return new SvcSimpleFilterParser();
    }
  }

  @Override
  public SimpleFilterParser getFilterParser() {
    return new SvcSimpleFilterParser();
  }

  @Override
  public SysparsI getSysparsHandler() {
    if (sysparsHandler == null) {
      sysparsHandler = new Syspars(this);
      handlers.add((CalSvcDb)sysparsHandler);
    }

    return sysparsHandler;
  }

  @Override
  public MailerIntf getMailer() {
    /*
    if (mailer != null) {
      return mailer;
    }*/

    final MailerIntf mailer = (MailerIntf)Util.getObject(
            getClass().getClassLoader(),
            getSystemProperties().getMailerClass(),
            MailerIntf.class);
    mailer.init(configs.getMailConfigProperties());

    return mailer;
  }

  @Override
  public PreferencesI getPrefsHandler() {
    if (prefsHandler == null) {
      prefsHandler = new Preferences(this);
      handlers.add((CalSvcDb)prefsHandler);
    }

    return prefsHandler;
  }

  @Override
  public AdminI getAdminHandler() {
    if (!isPublicAdmin()) {
      throw new RuntimeException("Attempt to get admin handler " +
                                         "when not public admin");
    }

    if (adminHandler == null) {
      adminHandler = new Admin(this);
      handlers.add((CalSvcDb)adminHandler);
    }

    return adminHandler;
  }

  @Override
  public EventsI getEventsHandler() {
    if (eventsHandler == null) {
      eventsHandler = new Events(this);
      handlers.add((CalSvcDb)eventsHandler);
    }

    return eventsHandler;
  }

  @Override
  public FiltersI getFiltersHandler() {
    if (filtersHandler == null) {
      filtersHandler = new Filters(this);
      handlers.add((CalSvcDb)filtersHandler);
    }

    return filtersHandler;
  }

  @Override
  public CalendarsI getCalendarsHandler() {
    if (calendarsHandler == null) {
      calendarsHandler = new Calendars(this);
      handlers.add((CalSvcDb)calendarsHandler);
    }

    return calendarsHandler;
  }

  @Override
  public CalSuitesI getCalSuitesHandler() {
    if (calSuitesHandler == null) {
      calSuitesHandler = new CalSuites(this);
      handlers.add((CalSvcDb)calSuitesHandler);
    }

    return calSuitesHandler;
  }

  public BwIndexer getIndexer(final String docType) {
    return getCal().getIndexer(docType);
  }

  @Override
  public BwIndexer getIndexer(final boolean publick,
                              final String docType) {
    return getCal().getIndexer(publick, docType);
  }

  @Override
  public BwIndexer getIndexer(final BwOwnedDbentity<?> entity) {
    return getCal().getIndexer(entity);
  }

  @Override
  public BwIndexer getIndexer(final String principal,
                              final String docType) {
    final String prHref;

    if (principal == null) {
      prHref = getPrincipal().getPrincipalRef();
    } else {
      prHref = principal;
    }

    return getCal().getIndexer(prHref, docType);
  }

  @Override
  public BwIndexer getIndexerForReindex(final String principal,
                                        final String docType,
                                        final String indexName) {
    final String prHref;

    if (principal == null) {
      prHref = getPrincipal().getPrincipalRef();
    } else {
      prHref = principal;
    }

    return getCal().getIndexerForReindex(prHref, docType, indexName);
  }

  @Override
  public NotificationsI getNotificationsHandler() {
    if (notificationsHandler == null) {
      notificationsHandler = new Notifications(this);
      handlers.add((CalSvcDb)notificationsHandler);
    }

    return notificationsHandler;
  }

  @Override
  public ResourcesI getResourcesHandler() {
    if (resourcesHandler == null) {
      resourcesHandler = new ResourcesImpl(this);
      handlers.add((CalSvcDb)resourcesHandler);
    }

    return resourcesHandler;
  }

  @Override
  public SchedulingI getScheduler() {
    if (sched == null) {
      sched = new Scheduling(this);
      handlers.add((CalSvcDb)sched);
    }

    return sched;
  }

  @Override
  public SharingI getSharingHandler() {
    if (sharingHandler == null) {
      sharingHandler = new Sharing(this);
      handlers.add((CalSvcDb)sharingHandler);
    }

    return sharingHandler;
  }

  @Override
  public SynchI getSynch() {
    if (synch == null) {
      synch = new Synch(this, configs.getSynchConfig());
      handlers.add((CalSvcDb)synch);
    }

    return synch;
  }

  @Override
  public UsersI getUsersHandler()  {
    if (usersHandler == null) {
      usersHandler = new Users(this);
      handlers.add((CalSvcDb)usersHandler);
    }

    return usersHandler;
  }

  @Override
  public ViewsI getViewsHandler() {
    if (viewsHandler == null) {
      viewsHandler = new Views(this);
      handlers.add((CalSvcDb)viewsHandler);
    }

    return viewsHandler;
  }

  @Override
  public Directories getDirectories() {
    if (isPublicAdmin() || isPublicAuth()) {
      return getAdminDirectories();
    }

    return getUserDirectories();
  }

  @Override
  public Directories getUserDirectories() {
    if (userGroups != null) {
      return userGroups;
    }

    try {
      userGroups = (Directories)Util.getObject(
              getClass().getClassLoader(),
              getSystemProperties().getUsergroupsClass(),
              Directories.class);
      userGroups.init(getGroupsCallBack(),
                      configs);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    return userGroups;
  }

  @Override
  public Directories getAdminDirectories() {
    if (adminGroups != null) {
      return adminGroups;
    }

    try {
      adminGroups = (Directories)Util.getObject(
              getClass().getClassLoader(),
              getSystemProperties().getAdmingroupsClass(),
              Directories.class);
      adminGroups.init(getGroupsCallBack(),
                       configs);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    return adminGroups;
  }

  @Override
  public Categories getCategoriesHandler() {
    if (categoriesHandler == null) {
      categoriesHandler = new CategoriesImpl(this);
      categoriesHandler.init(pars.getAdminCanEditAllPublicCategories());
      handlers.add((CalSvcDb)categoriesHandler);
    }

    return categoriesHandler;
  }

  @Override
  public Locations getLocationsHandler() {
    if (locationsHandler == null) {
      locationsHandler = new LocationsImpl(this);
      locationsHandler.init(pars.getAdminCanEditAllPublicLocations());
      handlers.add((CalSvcDb)locationsHandler);
    }

    return locationsHandler;
  }

  @Override
  public Contacts getContactsHandler() {
    if (contactsHandler == null) {
      contactsHandler = new ContactsImpl(this);
      contactsHandler.init(pars.getAdminCanEditAllPublicContacts());
      handlers.add((CalSvcDb)contactsHandler);
    }

    return contactsHandler;
  }

  /* ====================================================================
   *                       dump/restore methods
   *
   * These are used to handle the needs of dump/restore. Perhaps we
   * can eliminate the need at some point...
   * ==================================================================== */

  @Override
  public <T>  Iterator<T> getObjectIterator(final Class<T> cl) {
    return getCal().getObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getPrincipalObjectIterator(final Class<T> cl) {
    return getCal().getPrincipalObjectIterator(cl);
  }

  @Override
  public <T> Iterator<T> getPublicObjectIterator(final Class<T> cl) {
    return getCal().getPublicObjectIterator(cl);
  }

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  @Override
  public BwPrincipal<?> getPrincipal() {
    return principalInfo.getPrincipal();
  }

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    return getCal().getPrincipal(href);
  }

  @Override
  public UserAuth getUserAuth() {
    if (userAuth != null) {
      return userAuth;
    }

    userAuth = (UserAuth)Util.getObject(
            getClass().getClassLoader(),
            getSystemProperties().getUserauthClass(),
            UserAuth.class);

    userAuth.initialise(getUserAuthCallBack());

    return userAuth;
  }

  @Override
  public long getUserMaxEntitySize() {
    final long max = getPrefsHandler().get().getMaxEntitySize();

    if (max != 0) {
      return max;
    }

    return getAuthProperties().getMaxUserEntitySize();
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) {
    return getCal().getPreferences(principalHref);
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity<?> val) {
    getCal().removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup<?> findGroup(final String account,
                              final boolean admin) {
    return getCal().findGroup(account, admin);
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  @Override
  public void changeAccess(ShareableEntity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().changeAccess(ent, aces, replaceAll);

    if (ent instanceof final BwCalendar col) {
      if (col.getCalType() == BwCalendar.calTypeInbox) {
        // Same access as inbox
        final BwCalendar pendingInbox =
                getCalendarsHandler().getSpecial(BwCalendar.calTypePendingInbox,
                                                 true);
        if (pendingInbox == null) {
          warn("Unable to update pending inbox access");
        } else {
          getCal().changeAccess(pendingInbox, aces, replaceAll);
        }
      }

      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                         col,
                                         null,
                                         null,
                                         null);
    } else if (ent instanceof BwEventProperty) {
      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                                        (BwEventProperty<?>)ent);
    }
  }

  @Override
  public void defaultAccess(ShareableEntity ent,
                            final AceWho who) {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().defaultAccess(ent, who);
  }

  @Override
  public CurrentAccess checkAccess(final ShareableEntity ent,
                                   final int desiredAccess,
                                   final boolean returnResult) {
    return getCal().checkAccess(ent, desiredAccess, returnResult);
  }

  @Override
  public SynchReport getSynchReport(final String path,
                                    final String token,
                                    final int limit,
                                    final boolean recurse) {
    final BwCalendar col = getCalendarsHandler().get(path);
    if (col == null) {
      return null;
    }

    Set<SynchReportItem> items = new TreeSet<>();
    String resToken = getSynchItems(col, path, token, items, recurse);
    final var tokenValid = (token == null) || (resToken != null);
    final SynchReport res = new SynchReport(items, resToken, tokenValid);

    if (!tokenValid) {
      return res;
    }

    if ((limit > 0) && (res.size() >= limit)) {
      if (res.size() == limit) {
        return res;
      }

      items = new TreeSet<>();
      resToken = "";

      for (final SynchReportItem item: res.getItems()) {
        if (item.getToken().compareTo(resToken) > 0) {
          resToken = item.getToken();
        }

        items.add(item);

        if (items.size() == limit) {
          break;
        }
      }
    }

    if ((resToken == null) || (resToken.isEmpty())) {
      resToken = Util.icalUTCTimestamp() + "-0000";
    }

    return new SynchReport(items, resToken, true);
  }

  private boolean canSync(final BwCalendar col) {
    //if (col.getCalType() == BwCalendar.calTypeAlias) {
    //  return false;
    //}

    return col.getCalType() != BwCalendar.calTypeExtSub;
  }

  /* ====================================================================
   *                   Timezones
   * ==================================================================== */

  @Override
  public UpdateFromTimeZonesInfo updateFromTimeZones(final String colHref,
                                                     final int limit,
                                                     final boolean checkOnly,
                                                     final UpdateFromTimeZonesInfo info
                                                     ) {
    return tzstore.updateFromTimeZones(colHref, limit, checkOnly, info);
  }

  /* ====================================================================
   *                   Get back end interface
   * ==================================================================== */

  /* This will get a calintf based on the supplied collection object.
   */
  Calintf getCal(final BwCalendar cal) {
    return getCal();
  }

  private static final Map<String, BwPrincipal<?>> authUsers = new HashMap<>();

  private static final Map<String, BwPrincipal<?>> unauthUsers = new HashMap<>();

  /* Currently this gets a local calintf only. Later we need to use a par to
   * get calintf from a table.
   */
  Calintf getCal() {
    if (cali != null) {
      return cali;
    }

    final long start = System.currentTimeMillis();

    try {
      final long beforeGetIntf = System.currentTimeMillis() - start;
      if (trace()) {
        trace(format("getCal: beforeGetIntf=%s", beforeGetIntf));
      }

      String authenticatedUser = pars.getAuthUser();

      authenticated = pars.getForRestore()
              || authenticatedUser != null;

      cali = CalintfFactory
              .getIntf(getClass().getClassLoader(),
                       !authenticated || pars.getReadonly());

      final long afterGetIntf = System.currentTimeMillis() - start;
      if (trace()) {
        trace(format("getCal: afterGetIntf=%s", afterGetIntf));
      }

      cali.open(this,
                pars.getLogId(),
                configs,
                pars.getForRestore(),
                pars.getIndexRebuild(),
                pars.getPublicAdmin(),
                pars.getPublicAuth(),
                pars.getPublicSubmission(),
                authenticated,
                pars.getSessionsless(),
                pars.getDontKill()); // Just for the user interactions

      postNotification(SysEvent.makeTimedEvent(
              "Login: about to obtain calintf",
              beforeGetIntf));
      postNotification(
              SysEvent.makeTimedEvent("Login: calintf obtained",
                                      afterGetIntf));
      postNotification(
              SysEvent.makeTimedEvent("Login: intf opened",
                                      System.currentTimeMillis() - start));
      if (trace()) {
        trace(format("getCal: intf opened=%s",
                     System.currentTimeMillis() - start));
      }

      cali.beginTransaction();

      postNotification(
              SysEvent.makeTimedEvent("Login: transaction started",
                                      System.currentTimeMillis() - start));
      if (trace()) {
        trace(format("getCal: transaction started=%s",
                     System.currentTimeMillis() - start));
      }

      String runAsUser = pars.getUser();

      if (pars.getCalSuite() != null) {
        final BwCalSuite cs = cali.getCalSuite(pars.getCalSuite());

        if (cs == null) {
          error("******************************************************");
          error("Unable to fetch calendar suite " + pars
                  .getCalSuite());
          error("Is the database correctly initialised?");
          error("******************************************************");
          throw new BedeworkException(
                  CalFacadeErrorCode.unknownCalsuite,
                  pars.getCalSuite());
        }

        getCalSuitesHandler().set(new BwCalSuiteWrapper(cs));
        /* For administrative use we use the account of the admin group the user
         * is a direct member of
         *
         * For public clients we use the calendar suite owning group.
         */
        if (!pars.getPublicAdmin() && !pars.getPublicAuth()) {
          runAsUser = cs.getGroup().getOwnerHref();
        }
      }

      postNotification(
              SysEvent.makeTimedEvent("Login: before get dirs",
                                      System.currentTimeMillis() - start));
      if (trace()) {
        trace(format("getCal: before get dirs=%s",
                     System.currentTimeMillis() - start));
      }

      final Directories dir = getDirectories();

      /* Get ourselves a user object */
      if (authenticatedUser != null) {
        final String sv = authenticatedUser;

        if (dir.isPrincipal(authenticatedUser)) {
          authenticatedUser = dir
                  .accountFromPrincipal(authenticatedUser);
        }

        if (authenticatedUser == null) {
          error("Failed with Authenticated user " + sv);
          return null;
        }

        if (authenticatedUser.endsWith("/")) {
          getLogger().warn("Authenticated user " + authenticatedUser +
                                   " ends with \"/\"");
        }
      }

      postNotification(
              SysEvent.makeTimedEvent("Login: before user fetch",
                                      System.currentTimeMillis() - start));
      if (trace()) {
        trace(format("getCal: before user fetch=%s",
                     System.currentTimeMillis() - start));
      }

      final Users users = (Users)getUsersHandler();

      if (runAsUser == null) {
        runAsUser = authenticatedUser;
      }

      BwPrincipal<?> currentPrincipal;
      final BwPrincipal<?> authPrincipal;
      PrivilegeSet maxAllowedPrivs = null;
      boolean subscriptionsOnly = getSystemProperties()
              .getUserSubscriptionsOnly();
      boolean userMapHit = false;
      boolean addingUser = false;
      boolean addingRunAsUser = false;

      if (pars.getForRestore()) {
        currentPrincipal = dir.caladdrToPrincipal(pars.getAuthUser());
        authPrincipal = currentPrincipal;
        subscriptionsOnly = false;
      } else if (authenticatedUser == null) {
        // Unauthenticated use

        currentPrincipal = unauthUsers.get(runAsUser);

        if (currentPrincipal == null) {
          currentPrincipal = users.getUser(runAsUser);
        } else {
          userMapHit = true;
        }
        if (currentPrincipal == null) {
          // XXX Should we set this one up?
          currentPrincipal = BwPrincipal.makeUserPrincipal();
        }

        currentPrincipal.setUnauthenticated(true);

        if (!userMapHit) {
          unauthUsers.put(runAsUser, currentPrincipal);
        }
        authPrincipal = currentPrincipal;
        maxAllowedPrivs = PrivilegeSet.readOnlyPrivileges;
      } else {
        currentPrincipal = unauthUsers.get(authenticatedUser);

        if (currentPrincipal == null) {
          currentPrincipal = users.getUser(authenticatedUser);
        } else {
          userMapHit = true;
        }

        if (currentPrincipal == null) {
          if (pars.getReadonly()) {
            // We need read-write
            throw new RuntimeException(upgradeToReadWriteMessage);
          }

          /* Add the user to the database. Presumably this is first logon
             */
          getLogger().debug("Add new user " + authenticatedUser);

          /*
            currentPrincipal = addUser(authenticatedUser);
            if (currentPrincipal == null) {
              error("Failed to find user after adding: " + authenticatedUser);
            }
            */
          currentPrincipal = getFakeUser(authenticatedUser);
          addingUser = true;
        }
        authPrincipal = currentPrincipal;

        if (authenticatedUser.equals(runAsUser)) {
          audit(format("Authenticated user %s logged on - " +
                               "logid %s - admin %b",
                       authenticatedUser, pars.getLogId(),
                       pars.getPublicAdmin()));
        } else {
          currentPrincipal = unauthUsers.get(runAsUser);

          if (currentPrincipal == null) {
            currentPrincipal = users.getUser(runAsUser);
          } else {
            userMapHit = true;
          }

          if (currentPrincipal == null) {
            //              throw new BedeworkException("User " + runAsUser + " does not exist.");
            /* Add the user to the database. Presumably this is first logon
               */
            debug("Add new run-as-user " + runAsUser);

            //currentPrincipal = addUser(runAsUser);
            currentPrincipal = getFakeUser(runAsUser);
            addingRunAsUser = true;
          }

          audit(format("Authenticated user %s logged on - " +
                               " - running as %s " +
                               "logid %s - admin %b",
                       authenticatedUser, runAsUser,
                       pars.getLogId(),
                       pars.getPublicAdmin()));
        }

        if (!userMapHit && (currentPrincipal != null)) {
          currentPrincipal
                  .setGroups(dir.getAllGroups(currentPrincipal));
          authUsers.put(currentPrincipal.getAccount(),
                        currentPrincipal);
        }

        postNotification(
                SysEvent.makeTimedEvent("Login: after get Groups",
                                        System.currentTimeMillis() - start));
        if (trace()) {
          trace(format("getCal: after get groups=%s",
                       System.currentTimeMillis() - start));
        }

        if (pars.getService()) {
          subscriptionsOnly = false;
        } else {
          final BwPrincipalInfo bwpi = dir
                  .getDirInfo(currentPrincipal);
          currentPrincipal.setPrincipalInfo(bwpi);

          if (pars.getPublicAdmin() || (bwpi != null && bwpi
                  .getHasFullAccess())) {
            subscriptionsOnly = false;
          }

          postNotification(
                  SysEvent.makeTimedEvent("Login: got Dirinfo",
                                          System.currentTimeMillis() - start));
          if (trace()) {
            trace(format("getCal: got dirinfo=%s",
                         System.currentTimeMillis() - start));
          }
        }
      }

      principalInfo = new SvciPrincipalInfo(this,
                                            currentPrincipal,
                                            authPrincipal,
                                            maxAllowedPrivs,
                                            subscriptionsOnly);

      cali.initPinfo(principalInfo);

      if (addingUser) {
        // Do the real work of setting up user
        addUser(authenticatedUser);
      }

      if (addingRunAsUser) {
        // Do the real work of setting up user
        addUser(runAsUser);
      }

      if (!currentPrincipal.getUnauthenticated()) {
        if (pars.getService()) {
          postNotification(
                  SysEvent.makePrincipalEvent(
                          SysEvent.SysCode.SERVICE_USER_LOGIN,
                          currentPrincipal.getPrincipalRef(),
                          System.currentTimeMillis() - start));
        } else if (!creating) {
          users.logon(currentPrincipal);

          postNotification(
                  SysEvent.makePrincipalEvent(
                          SysEvent.SysCode.USER_LOGIN,
                          currentPrincipal.getPrincipalRef(),
                          System.currentTimeMillis() - start));
        }

        if (debug()) {
          final Collection<BwGroup<?>> groups =
                  currentPrincipal.getGroups();
          if (!Util.isEmpty(groups)) {
            for (final var group: groups) {
              debug("Group: " + group.getAccount());
            }
          }
        }
      } else {
        // If we have a runAsUser it's a public client. Pretend we authenticated
// WHY?          currentPrincipal.setUnauthenticated(runAsUser == null);
      }

      if (pars.getPublicAdmin() || pars.isGuest()) {
        if (debug()) {
          debug("PublicAdmin: " + pars.getPublicAdmin() + " user: "
                        + runAsUser);
        }

        /* We may be running as a different user. The preferences we want to see
           * are those of the user we are running as - i.e. the 'run.as' user
           * not those of the authenticated user.
           * /

          BwCalSuiteWrapper suite = getCalSuitesHandler().get();
          BwPrincipal user;

          if (suite != null) {
            // Use this user
            user = users.getPrincipal(suite.getGroup().getOwnerHref());
          } else if (runAsUser == null) {
            // Unauthenticated CalDAV for example?
            user = currentPrincipal;
          } else {
            // No calendar suite set up

            // XXX This is messy
            if (runAsUser.startsWith("/")) {
              user = users.getPrincipal(runAsUser);
            } else {
              user = users.getUser(runAsUser);
            }
          }

          if (!user.equals(principalInfo.getPrincipal())) {
            user.setGroups(getDirectories().getAllGroups(user));
            user.setPrincipalInfo(getDirectories().getDirInfo(user));
            ((SvciPrincipalInfo)principalInfo).setPrincipal(user);
          }

           */
      }
      if (trace()) {
        trace(format("getCal: return %s",
                     System.currentTimeMillis() - start));
      }

      return cali;
      //}
    } catch (final RuntimeException t) {
      if (!t.getMessage().equals(upgradeToReadWriteMessage)) {
        error(t);
      }
      throw t;
    } catch (final Throwable t) {
      error(t);
      throw new RuntimeException(t);
    } finally {
      if (cali != null) {
        try {
          cali.endTransaction();
          if (trace()) {
            trace(format("getCal: after endTransaction %s",
                         System.currentTimeMillis() - start));
          }
        } catch (final Throwable ignored) {}

        cali.close();
        if (trace()) {
          trace(format("getCal: after close %s",
                       System.currentTimeMillis() - start));
        }
      }
    }
  }

  void initPrincipal(final BwPrincipal<?> p) {
    getCal().addNewCalendars(p);
  }

  /** Set the owner and creator on a shareable entity.
   *
   * @param entity shareable entity
   * @param ownerHref - new owner
   */
  void setupSharableEntity(final ShareableEntity entity,
                           final String ownerHref) {
    if (entity.getCreatorHref() == null) {
      entity.setCreatorHref(ownerHref);
    }

    setupOwnedEntity(entity, ownerHref);
  }

  /** Set the owner and publick on an owned entity.
   *
   * @param entity owned entity
   * @param ownerHref - new owner
   */
  void setupOwnedEntity(final OwnedEntity entity,
                        final String ownerHref) {
    entity.setPublick(isPublicAdmin());

    if (entity.getOwnerHref() == null) {
      if (entity.getPublick()) {
        entity.setOwnerHref(getUsersHandler().getPublicUser()
                                             .getPrincipalRef());
      } else {
        entity.setOwnerHref(ownerHref);
      }
    }
  }

  /** Return owner for entities
   *
   * @return BwPrincipal
   */
  BwPrincipal<?> getEntityOwner() {
    if (isPublicAdmin() || pars.isGuest()) {
      return getUsersHandler().getPublicUser();
    }

    return getPrincipal();
  }

  /** Switch to the given principal to allow us to update their stuff - for
   * example - send a notification.
   *
   * @param principal a principal object
   */
  void pushPrincipal(final BwPrincipal<?> principal) {
    BwPrincipal<?> pr = getUsersHandler().getUser(principal.getPrincipalRef());

    if (pr == null) {
      pr = addUser(principal.getPrincipalRef());
    }

    ((SvciPrincipalInfo)principalInfo).pushPrincipal(pr);
    getCal().principalChanged();
  }

  /** Switch to the given principal to allow us to update their stuff - for
   * example - send a notification.
   *
   * @param principalHref a principal href
   */
  void pushPrincipalOrFail(final String principalHref) {
    final BwPrincipal<?> pr =
            getDirectories()
                    .caladdrToPrincipal(principalHref);

    if (pr == null) {
      throw new RuntimeException("Unknown Principal " +
                                         principalHref);
    }

    ((SvciPrincipalInfo)principalInfo).pushPrincipal(pr);
    getCal().principalChanged();
  }

  /** Switch back to the previous principal.
   *
   */
  void popPrincipal() {
    ((SvciPrincipalInfo)principalInfo).popPrincipal();
    getCal().principalChanged();
  }

  BwPrincipal<?> getFakeUser(final String account) {
    final Users users = (Users)getUsersHandler();

    /* Run this in a separate transaction to ensure we don't fail if the user
     * gets created by a concurrent process.
     */

    // Get a fake user
    return users.initUserObject(account);
  }

  /* Create the user. Get a new CalSvc object for that purpose.
   *
   */
  BwPrincipal<?> addUser(final String val) {
    final Users users = (Users)getUsersHandler();

    /* Run this in a separate transaction to ensure we don't fail if the user
     * gets created by a concurrent process.
     */

    //if (creating) {
    //  // Get a fake user
    //  return users.initUserObject(val);
    //}

    getCal().flush(); // In case we need to replace the session

    try {
      users.createUser(val);
    } catch (final BedeworkException be) {
      if (be instanceof BedeworkConstraintViolationException) {
        // We'll assume it was created by another process.
        warn("ConstraintViolationException trying to create " + val);

        // Does this session still work?
      } else {
        rollbackTransaction();
        if (debug()) {
          error(be);
        }
        throw be;
      }
    } catch (final Throwable t) {
      rollbackTransaction();
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    }

    final var principal = users.getUser(val);

    if (principal == null) {
      return null;
    }

    final String caladdr =
            getDirectories().userToCaladdr(principal.getPrincipalRef());
    if (caladdr != null) {
      final List<String> emails = Collections.singletonList(caladdr.substring("mailto:".length()));
      final Notifications notify = (Notifications)getNotificationsHandler();
      notify.subscribe(principal, emails);
    }
    return principal;
  }

  private UserAuthCallBack getUserAuthCallBack() {
    if (uacb == null) {
      uacb = new UserAuthCallBack(this);
    }

    return (UserAuthCallBack)uacb;
  }

  private GroupsCallBack getGroupsCallBack() {
    if (gcb == null) {
      gcb = new GroupsCallBack(this);
    }

    return (GroupsCallBack)gcb;
  }

  private class IcalCallbackcb implements IcalCallback {
    private int strictness = conformanceRelaxed;

    private final Boolean timezonesByReference;

    IcalCallbackcb(final Boolean timezonesByReference) {
      this.timezonesByReference = timezonesByReference;
    }

    @Override
    public void setStrictness(final int val) {
      strictness = val;
    }

    @Override
    public int getStrictness() {
      return strictness;
    }

    @Override
    public BwPrincipal<?> getPrincipal() {
      return CalSvc.this.getPrincipal();
    }

    @Override
    public BwPrincipal<?> getOwner() {
      if (isPublicAdmin()) {
        return getUsersHandler().getPublicUser();
      }

      return CalSvc.this.getPrincipal();
    }

    @Override
    public String getCaladdr(final String val) {
      return getDirectories().userToCaladdr(val);
    }

    @Override
    public GetEntityResponse<BwCategory> findCategory(final BwString val) {
      return getCategoriesHandler().findPersistent(val);
    }

    @Override
    public void addCategory(final BwCategory val) {
      getCategoriesHandler().add(val);
    }

    @Override
    public GetEntityResponse<BwContact> getContact(final String uid) {
      return getContactsHandler().getByUid(uid);
    }

    @Override
    public GetEntityResponse<BwContact> findContact(final BwString val) {
      return getContactsHandler().findPersistent(val);
    }

    @Override
    public void addContact(final BwContact val) {
      getContactsHandler().add(val);
    }

    @Override
    public GetEntityResponse<BwLocation> getLocation(final String uid) {
      return getLocationsHandler().getByUid(uid);
    }

    @Override
    public GetEntityResponse<BwLocation> fetchLocationByKey(
            final String name,
            final String val) {
      return getLocationsHandler().fetchLocationByKey(name, val);
    }

    @Override
    public GetEntityResponse<BwLocation> findLocation(final BwString address) {
      return getLocationsHandler().findPersistent(address);
    }

    @Override
    public GetEntityResponse<BwLocation> fetchLocationByCombined(
            final String val, final boolean persisted) {
      return getLocationsHandler().fetchLocationByCombined(val, persisted);
    }

    @Override
    public void addLocation(final BwLocation val) {
      getLocationsHandler().add(val);
    }

    @Override
    public GetEntitiesResponse<EventInfo> getEvent(final String colPath,
                                                   final String guid) {
      final GetEntitiesResponse<EventInfo> resp = new GetEntitiesResponse<>();

      try {
        final var ents =
                getEventsHandler().getByUid(colPath, guid,
                                            null,
                                            RecurringRetrievalMode.overrides);
        if (Util.isEmpty(ents)) {
          resp.setStatus(Response.Status.notFound);
        } else {
          resp.setEntities(ents);
        }

        return resp;
      } catch (final Throwable t) {
        return Response.error(resp, t);
      }
    }

    @Override
    public boolean getTimezonesByReference() {
      if (timezonesByReference != null) {
        return timezonesByReference;
      }

      return getSystemProperties().getTimezonesByReference();
    }
  }

  /* Remove trailing "/" from user principals.
   */
  private void fixUsers() {
    String auser = pars.getAuthUser();
    while ((auser != null) && (auser.endsWith("/"))) {
      auser = auser.substring(0, auser.length() - 1);
    }

    pars.setAuthUser(auser);
  }

  private String getSynchItems(final BwCalendar col,
                               final String vpath,
                               final String tokenPar,
                               final Set<SynchReportItem> items,
                               final boolean recurse) {
    final Events eventsH = (Events)getEventsHandler();
    final ResourcesImpl resourcesH = (ResourcesImpl)getResourcesHandler();
    final Calendars colsH = (Calendars)getCalendarsHandler();
    final var path = col.getPath();
    String newToken = "";

    if (debug()) {
      debug("sync token: " + tokenPar + " col: " + col.getPath());
    }

    if (col.getTombstoned()) {
      return tokenPar;
    }

    final BwCalendar resolvedCol;

    if (col.getInternalAlias()) {
      resolvedCol = getCalendarsHandler().resolveAlias(col, true, false);
    } else {
      resolvedCol = col;
    }
    
    if (resolvedCol.getTombstoned()) {
      return tokenPar;
    }

    /* Each collection could be:
     *    a. A calendar collection or special - like Inbox -
     *           only need to look for events.
     *    b. Other collections. Need to look for events, resources and collections.
     */

    final String token;
    if (tokenPar == null) {
      token = null;
    } else if (colsH.getSyncTokenIsValid(tokenPar, path)) {
      token = tokenPar;
    } else {
      return null; // Bad token
    }

    final boolean eventsOnly = resolvedCol.getCollectionInfo().onlyCalEntities;

    final Set<EventInfo> evs = eventsH.getSynchEvents(resolvedCol.getPath(), token);

    for (final EventInfo ei: evs) {
      // May be a filtered alias. Remove all those that aren't visible.
      // TODO - if the filter changes this may result in an invalid response. Should force a resynch
      // Could add an earliest valid sync token property.
      
      // TODO - ALso tombstoned items need to be stored in the index.
      // For the moment just let any tombstoned event through
      
      if (!ei.getEvent().getTombstoned() &&
              !eventsH.isVisible(col, ei.getEvent().getName())) {
        continue;
      }
      
      final SynchReportItem sri = new SynchReportItem(vpath, ei);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }
    }

    if (!eventsOnly) {
      // Look for resources
      final List<BwResource> ress = resourcesH.getSynchResources(resolvedCol.getPath(), token);

      for (final BwResource r: ress) {
        final SynchReportItem sri = new SynchReportItem(vpath, r);
        items.add(sri);

        if (sri.getToken().compareTo(newToken) > 0) {
          newToken = sri.getToken();
        }
      }
    }

    final Set<SynchReportItem> colItems = new TreeSet<>();
    final Set<BwCalendar> cols = colsH.getSynchCols(resolvedCol.getPath(), token);
    
    final List<BwCalendar> aliases = new ArrayList<>();

    for (final BwCalendar c: cols) {
      final int calType = c.getCalType();
      
      if (calType == BwCalendar.calTypePendingInbox) {
        continue;
      }

      if ((token != null) && (calType == BwCalendar.calTypeAlias)) {
        aliases.add(c);
        continue;
      }

      final SynchReportItem sri = new SynchReportItem(vpath, c, canSync(c));
      colItems.add(sri);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }

      if (debug()) {
        debug("     token=" + sri.getToken() + " for " + c.getPath());
      }
    }
    
    if (!Util.isEmpty(aliases)) {
      /* Resolve each one and see if the target is a candidate
       */
      for (final BwCalendar c: aliases) {
        final BwCalendar resolved = getCalendarsHandler().resolveAlias(c, true, false);
        
        if (resolved == null) {
          continue;
        }
        
        if (c.getTombstoned() && !getCal().testSynchCol(c, token)) {
          continue;
        }
        
        if (!getCal().testSynchCol(resolved, token)) {
          continue;
        }

        final SynchReportItem sri = new SynchReportItem(vpath, 
                                                        c, 
                                                        canSync(c),
                                                        resolved.getLastmod().getTagValue());
        colItems.add(sri);
        items.add(sri);

        if (sri.getToken().compareTo(newToken) > 0) {
          newToken = sri.getToken();
        }
      }
    }

    if (!recurse) {
      return newToken;
    }

    if (Util.isEmpty(colItems)) {
      return newToken;
    }

    for (final SynchReportItem sri: colItems) {
      if (!sri.getCanSync()) {
        continue;
      }

      final BwCalendar sricol = sri.getCol();
      final String t = getSynchItems(sricol,
                                     Util.buildPath(true, vpath, "/", sricol.getName()),
                                     token, items, true);

      // It's possible we get a really old synch token - ignore those
      if ((t == null) || !colsH.getSyncTokenIsValid(t, null)) {
        continue;
      }

      if (t.compareTo(newToken) > 0) {
        newToken = t;
      }
    }

    return newToken;
  }

  /* ====================================================================
   *                   Package private methods
   * ==================================================================== */

  void touchCalendar(final String href) {
    getCal().touchCalendar(href);
  }

  void touchCalendar(final BwCalendar col) {
    getCal().touchCalendar(col);
  }

  PwEncryptionIntf getEncrypter() {
    if (pwEncrypt != null) {
      return pwEncrypt;
    }

    try {
      final String pwEncryptClass = "org.bedework.util.security.PwEncryptionDefault";
      //String pwEncryptClass = getSysparsHandler().get().getPwEncryptClass();

      pwEncrypt = (PwEncryptionIntf)Util.getObject(
              getClass().getClassLoader(),
              pwEncryptClass,
              PwEncryptionIntf.class);

      String privKeys = null;
      String pubKeys = null;

      final GenKeysMBean gk =
              (GenKeysMBean)MBeanUtil.getMBean(GenKeysMBean.class,
                                               GenKeysMBean.serviceName);
      if (gk != null) {
        privKeys = gk.getPrivKeyFileName();
        pubKeys = gk.getPublicKeyFileName();
      }

      if (privKeys == null) {
        throw new BedeworkException(
                "Unable to get keyfile locations. Is genkeys service installed?");
      }

      pwEncrypt.init(privKeys, pubKeys);

      return pwEncrypt;
    } catch (final Throwable t) {
      error(t);
      throw new BedeworkException(t);
    }
  }

  /* Get current parameters
   */
  CalSvcIPars getPars() {
    return pars;
  }

  /* See if in public admin mode
   */
  private boolean isPublicAdmin() {
    return pars.getPublicAdmin();
  }

  /* See if in public auth mode
   */
  private boolean isPublicAuth() {
    return pars.getPublicAuth();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();
  private static final BwLogger authLogger = new BwLogger();

  static {
    authLogger.setLoggedName("org.bedework.authentication");
  }

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
      logger.enableAuditLogger();
    }

    return logger;
  }
}
