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
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeSet;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfFactory;
import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.CacheStats;
import org.bedework.calfacade.BwStats.StatsEntry;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.mail.MailerIntf;
import org.bedework.calfacade.security.GenKeysMBean;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.svc.UserAuth;
import org.bedework.calfacade.svc.wrappers.BwCalSuiteWrapper;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calsvc.indexing.BwIndexerFactory;
import org.bedework.calsvc.scheduling.Scheduling;
import org.bedework.calsvc.scheduling.SchedulingIntf;
import org.bedework.calsvci.AdminI;
import org.bedework.calsvci.CalSuitesI;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.Categories;
import org.bedework.calsvci.DumpIntf;
import org.bedework.calsvci.EventProperties;
import org.bedework.calsvci.EventsI;
import org.bedework.calsvci.FiltersI;
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
import org.bedework.calsvci.indexing.BwIndexer;
import org.bedework.icalendar.IcalCallback;
import org.bedework.icalendar.URIgen;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.jmx.MBeanUtil;
import org.bedework.util.misc.Util;
import org.bedework.util.security.PwEncryptionIntf;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.property.DtStamp;
import org.apache.log4j.Logger;
import org.hibernate.exception.ConstraintViolationException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** This is an implementation of the service level interface to the calendar
 * suite.
 *
 * @author Mike Douglass       douglm rpi.edu
 */
public class CalSvc extends CalSvcI {
  //private String systemName;

  private CalSvcIPars pars;

  private boolean debug;

  private static Configurations configs;

  static {
    try {
      configs = new CalSvcFactoryDefault().getSystemConfig();
    } catch (Throwable t) {
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

  /* If we're doing admin this is the authorised user entry
   */
  BwAuthUser adminUser;

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

  private EventProperties<BwLocation> locationsHandler;

  private EventProperties<BwContact> contactsHandler;

  private Collection<CalSvcDb> handlers = new ArrayList<CalSvcDb>();

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

  private transient Logger log;

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#init(org.bedework.calsvci.CalSvcIPars)
   */
  @Override
  public void init(final CalSvcIPars parsParam) throws CalFacadeException {
    init(parsParam, false);
  }

  private void init(final CalSvcIPars parsParam,
                    final boolean creating) throws CalFacadeException {
    pars = (CalSvcIPars)parsParam.clone();

    this.creating = creating;

    debug = getLogger().isDebugEnabled();
    long start = System.currentTimeMillis();

    try {
      if (configs == null) {
        // Try again - failed at static init?
        configs = new CalSvcFactoryDefault().getSystemConfig();
      }

      open();
      beginTransaction();

      if (userGroups != null) {
        userGroups.init(getGroupsCallBack(),
                        configs);
      }

      if (adminGroups != null) {
        adminGroups.init(getGroupsCallBack(),
                         configs);
      }

      SystemProperties sp = getSystemProperties();

      if (tzserverUri == null) {
        tzserverUri = sp.getTzServeruri();

        if (tzserverUri == null) {
          throw new CalFacadeException("No timezones server URI defined");
        }

        Timezones.initTimezones(tzserverUri);

        Timezones.setSystemDefaultTzid(sp.getTzid());
      }

      /* Some checks on parameter validity
       */
      //        BwUser =

      tzstore = new TimeZonesStoreImpl(this);

      /* Nominate our timezone registry */
      System.setProperty("net.fortuna.ical4j.timezone.registry",
      "org.bedework.icalendar.TimeZoneRegistryFactoryImpl");

      if (!creating) {
        String tzid = getPrefsHandler().get().getDefaultTzid();

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

      if ((pars.getPublicAdmin() || pars.getAllowSuperUser()) &&
          (pars.getAuthUser() != null)) {
        ((SvciPrincipalInfo)principalInfo).setSuperUser(
             getSysparsHandler().isRootUser(principalInfo.getAuthPrincipal()));
      }

      postNotification(
        SysEvent.makePrincipalEvent(SysEvent.SysCode.USER_SVCINIT,
                                                      getPrincipal(),
                                                      System.currentTimeMillis() - start));
    } catch (CalFacadeException cfe) {
      rollbackTransaction();
      cfe.printStackTrace();
      throw cfe;
    } catch (Throwable t) {
      rollbackTransaction();
      t.printStackTrace();
      throw new CalFacadeException(t);
    } finally {
      try {
        endTransaction();
      } catch (Throwable t1) {}
      try {
        close();
      } catch (Throwable t2) {}
    }
  }

  @Override
  public BasicSystemProperties getBasicSystemProperties() throws CalFacadeException {
    return configs.getBasicSystemProperties();
  }

  @Override
  public AuthProperties getAuthProperties() throws CalFacadeException {
    return configs.getAuthProperties(authenticated);
  }

  @Override
  public AuthProperties getAuthProperties(boolean auth) throws CalFacadeException {
    return configs.getAuthProperties(auth);
  }

  @Override
  public SystemProperties getSystemProperties() throws CalFacadeException {
    return configs.getSystemProperties();
  }

  @Override
  public IndexProperties getIndexProperties() throws CalFacadeException {
    return configs.getIndexProperties();
  }

  @Override
  public void setCalSuite(final String name) throws CalFacadeException {
    BwCalSuiteWrapper cs = getCalSuitesHandler().get(name);

    if (cs == null) {
      error("******************************************************");
      error("Unable to fetch calendar suite " + name);
      error("Is the database correctly initialised?");
      error("******************************************************");
      throw new CalFacadeException(CalFacadeException.unknownCalsuite,
          name);
    }

    getCalSuitesHandler().set(cs);

    BwPrincipal user = getUsersHandler().getPrincipal(cs.getGroup().getOwnerHref());

    if (!user.equals(principalInfo.getPrincipal())) {
      ((SvciPrincipalInfo)principalInfo).setPrincipal(user);
    }
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
                             final String service) throws CalFacadeException {
    try {
      return getEncrypter().getPublicKey();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public BwStats getStats() throws CalFacadeException {
    BwStats stats = getCal().getStats();

    if (timezones != null) {
      CacheStats cs = stats.getDateCacheStats();

      cs.setHits(timezones.getDateCacheHits());
      cs.setMisses(timezones.getDateCacheMisses());
      cs.setCached(timezones.getDatesCached());
    }

    stats.setAccessStats(Access.getStatistics());

    return stats;
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    getCal().setDbStatsEnabled(enable);
  }

  @Override
  public boolean getDbStatsEnabled() throws CalFacadeException {
    return getCal().getDbStatsEnabled();
  }

  @Override
  public void dumpDbStats() throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    trace(getStats().toString());
    getCal().dumpDbStats();
  }

  @Override
  public Collection<StatsEntry> getDbStats() throws CalFacadeException {
    //if (!pars.getPublicAdmin()) {
    //  throw new CalFacadeAccessException();
    //}

    return getCal().getDbStats();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#logStats()
   */
  @Override
  public void logStats() throws CalFacadeException {
    logIt(getStats().toString());
  }


  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#postNotification(org.bedework.sysevents.events.SysEvent)
   */
  @Override
  public void postNotification(final SysEventBase ev) throws CalFacadeException {
    getCal().postNotification(ev);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#flushAll()
   */
  @Override
  public void flushAll() throws CalFacadeException {
    getCal().flush();
  }

  @Override
  public void open() throws CalFacadeException {
    //TimeZoneRegistryImpl.setThreadCb(getIcalCallback());

    if (open) {
      return;
    }

    open = true;
    getCal().open(pars.getWebMode(),
                  pars.getForRestore());

    for (CalSvcDb handler: handlers) {
      handler.open();
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public boolean isRolledback() throws CalFacadeException {
    if (!open) {
      return false;
    }

    return getCal().isRolledback();
  }

  @Override
  public void close() throws CalFacadeException {
    open = false;
    getCal().close();

    for (CalSvcDb handler: handlers) {
      handler.close();
    }
  }

  @Override
  public void beginTransaction() throws CalFacadeException {
    getCal().beginTransaction();
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    getCal().endTransaction();
  }

  @Override
  public void rollbackTransaction() throws CalFacadeException {
    getCal().rollbackTransaction();
  }

  @Override
  public Timestamp getCurrentTimestamp() throws CalFacadeException {
    return getCal().getCurrentTimestamp();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#reAttach(org.bedework.calfacade.base.BwDbentity)
   */
  @Override
  public void reAttach(final BwDbentity val) throws CalFacadeException {
    getCal().reAttach(val);
  }

  @Override
  public BwUnversionedDbentity merge(final BwUnversionedDbentity val) throws CalFacadeException {
    return getCal().merge(val);
  }

  @Override
  public IcalCallback getIcalCallback() {
    if (icalcb == null) {
      icalcb = new IcalCallbackcb();
    }

    return icalcb;
  }

  /* ====================================================================
   *                   Factory methods
   * ==================================================================== */

  @Override
  public DumpIntf getDumpHandler() throws CalFacadeException {
    return new DumpImpl(this);
  }

  @Override
  public RestoreIntf getRestoreHandler() throws CalFacadeException {
    return new RestoreImpl(this);
  }

  class SvcSimpleFilterParser extends SimpleFilterParser {
    @Override
    public BwCategory getCategoryByName(final String name) throws CalFacadeException {
      return getCategoriesHandler().find(new BwString(null, name));
    }

    @Override
    public BwCategory getCategory(final String uid) throws CalFacadeException {
      return getCategoriesHandler().get(uid);
    }

    @Override
    public BwView getView(final String path)
            throws CalFacadeException {
      return getViewsHandler().find(path);
  }

  @Override
    public Collection<BwCalendar> decomposeVirtualPath(final String vpath)
            throws CalFacadeException {
      return getCalendarsHandler().decomposeVirtualPath(vpath);
    }

    @Override
    public SimpleFilterParser getParser() throws CalFacadeException {
      return new SvcSimpleFilterParser();
    }
  }

  @Override
  public SimpleFilterParser getFilterParser() throws CalFacadeException {
    return new SvcSimpleFilterParser();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getSysparsHandler()
   */
  @Override
  public SysparsI getSysparsHandler() throws CalFacadeException {
    if (sysparsHandler == null) {
      sysparsHandler = new Syspars(this);
      handlers.add((CalSvcDb)sysparsHandler);
    }

    return sysparsHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getMailer()
   */
  @Override
  public MailerIntf getMailer() throws CalFacadeException {
    /*
    if (mailer != null) {
      return mailer;
    }*/

    try {
      MailerIntf mailer = (MailerIntf)CalFacadeUtil.getObject(getSystemProperties().getMailerClass(),
                                                   MailerIntf.class);
      mailer.init(configs.getMailConfigProperties());

      return mailer;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getPrefsHandler()
   */
  @Override
  public PreferencesI getPrefsHandler() throws CalFacadeException {
    if (prefsHandler == null) {
      prefsHandler = new Preferences(this);
      handlers.add((CalSvcDb)prefsHandler);
    }

    return prefsHandler;
  }

  @Override
  public AdminI getAdminHandler() throws CalFacadeException {
    if (!isPublicAdmin()) {
      throw new CalFacadeAccessException();
    }

    if (adminHandler == null) {
      adminHandler = new Admin(this);
      handlers.add((CalSvcDb)adminHandler);
    }

    return adminHandler;
  }

  @Override
  public EventsI getEventsHandler() throws CalFacadeException {
    if (eventsHandler == null) {
      eventsHandler = new Events(this);
      handlers.add((CalSvcDb)eventsHandler);
    }

    return eventsHandler;
  }

  @Override
  public FiltersI getFiltersHandler() throws CalFacadeException {
    if (filtersHandler == null) {
      filtersHandler = new Filters(this);
      handlers.add((CalSvcDb)filtersHandler);
    }

    return filtersHandler;
  }

  @Override
  public CalendarsI getCalendarsHandler() throws CalFacadeException {
    if (calendarsHandler == null) {
      calendarsHandler = new Calendars(this);
      handlers.add((CalSvcDb)calendarsHandler);
    }

    return calendarsHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getCalSuitesHandler()
   */
  @Override
  public CalSuitesI getCalSuitesHandler() throws CalFacadeException {
    if (calSuitesHandler == null) {
      calSuitesHandler = new CalSuites(this);
      handlers.add((CalSvcDb)calSuitesHandler);
    }

    return calSuitesHandler;
  }

  @Override
  public BwIndexer getIndexer(final boolean publick,
                              final String principal) throws CalFacadeException {
    String pref = principal;

    if (pref == null) {
      pref = getPrincipal().getPrincipalRef();
    }

    return BwIndexerFactory.getIndexer(this, publick, pref,
                                       pars.isGuest());
  }

  @Override
  public BwIndexer getIndexer(final String principal,
                              final String indexRoot) throws CalFacadeException {
    String pref = principal;

    if (pref == null) {
      pref = getPrincipal().getPrincipalRef();
    }

    return BwIndexerFactory.getIndexer(this, pref,
                                       pars.isGuest(),
                                       indexRoot);
  }

  @Override
  public NotificationsI getNotificationsHandler() throws CalFacadeException {
    if (notificationsHandler == null) {
      notificationsHandler = new Notifications(this);
      handlers.add((CalSvcDb)notificationsHandler);
    }

    return notificationsHandler;
  }

  @Override
  public ResourcesI getResourcesHandler() throws CalFacadeException {
    if (resourcesHandler == null) {
      resourcesHandler = new ResourcesImpl(this);
      handlers.add((CalSvcDb)resourcesHandler);
    }

    return resourcesHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getScheduler()
   */
  @Override
  public SchedulingI getScheduler() throws CalFacadeException {
    if (sched == null) {
      sched = new Scheduling(this);
      handlers.add((CalSvcDb)sched);
    }

    return sched;
  }

  @Override
  public SharingI getSharingHandler() throws CalFacadeException {
    if (sharingHandler == null) {
      sharingHandler = new Sharing(this);
      handlers.add((CalSvcDb)sharingHandler);
    }

    return sharingHandler;
  }

  @Override
  public SynchI getSynch() throws CalFacadeException {
    if (synch == null) {
      try {
        synch = new Synch(this, configs.getSynchConfig());
        handlers.add((CalSvcDb)synch);
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    return synch;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getUsersHandler()
   */
  @Override
  public UsersI getUsersHandler() throws CalFacadeException {
    if (usersHandler == null) {
      usersHandler = new Users(this);
      handlers.add((CalSvcDb)usersHandler);
    }

    return usersHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getViewsHandler()
   */
  @Override
  public ViewsI getViewsHandler() throws CalFacadeException {
    if (viewsHandler == null) {
      viewsHandler = new Views(this);
      handlers.add((CalSvcDb)viewsHandler);
    }

    return viewsHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getDirectories()
   */
  @Override
  public Directories getDirectories() throws CalFacadeException {
    if (isPublicAdmin()) {
      return getAdminDirectories();
    }

    return getUserDirectories();
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getUserDirectories()
   */
  @Override
  public Directories getUserDirectories() throws CalFacadeException {
    if (userGroups != null) {
      return userGroups;
    }

    try {
      userGroups = (Directories)CalFacadeUtil.getObject(getSystemProperties().getUsergroupsClass(), Directories.class);
      userGroups.init(getGroupsCallBack(),
                      configs);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return userGroups;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getAdminDirectories()
   */
  @Override
  public Directories getAdminDirectories() throws CalFacadeException {
    if (adminGroups != null) {
      return adminGroups;
    }

    try {
      adminGroups = (Directories)CalFacadeUtil.getObject(getSystemProperties().getAdmingroupsClass(), Directories.class);
      adminGroups.init(getGroupsCallBack(),
                       configs);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return adminGroups;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getCategoriesHandler()
   */
  @Override
  public Categories getCategoriesHandler() throws CalFacadeException {
    if (categoriesHandler == null) {
      categoriesHandler = new CategoriesImpl(this);
      categoriesHandler.init(pars.getAdminCanEditAllPublicCategories());
      handlers.add((CalSvcDb)categoriesHandler);
    }

    return categoriesHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getLocationsHandler()
   */
  @Override
  public EventProperties<BwLocation> getLocationsHandler()
          throws CalFacadeException {
    if (locationsHandler == null) {
      locationsHandler = new EventPropertiesImpl<BwLocation>(this);
      locationsHandler.init(BwLocation.class.getName(),
                            pars.getAdminCanEditAllPublicLocations());
      handlers.add((CalSvcDb)locationsHandler);
    }

    return locationsHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getContactsHandler()
   */
  @Override
  public EventProperties<BwContact> getContactsHandler()
          throws CalFacadeException {
    if (contactsHandler == null) {
      contactsHandler = new EventPropertiesImpl<BwContact>(this);
      contactsHandler.init(BwContact.class.getName(),
                           pars.getAdminCanEditAllPublicContacts());
      handlers.add((CalSvcDb)contactsHandler);
    }

    return contactsHandler;
  }

  /* ====================================================================
   *                   Users and accounts
   * ==================================================================== */

  @Override
  public BwPrincipal getPrincipal() throws CalFacadeException {
    return principalInfo.getPrincipal();
  }

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return getCal().getPrincipal(href);
  }

  @Override
  public UserAuth getUserAuth() throws CalFacadeException {
    if (userAuth != null) {
      return userAuth;
    }

    userAuth = (UserAuth)CalFacadeUtil.getObject(getSystemProperties().getUserauthClass(),
                                                 UserAuth.class);

    userAuth.initialise(getUserAuthCallBack());

    return userAuth;
  }

  @Override
  public long getUserMaxEntitySize() throws CalFacadeException {
    long max = getPrefsHandler().get().getMaxEntitySize();

    if (max != 0) {
      return max;
    }

    return getAuthProperties().getMaxUserEntitySize();
  }

  @Override
  public BwPreferences getPreferences(final String principalHref) throws CalFacadeException {
    return getCal().getPreferences(principalHref);
  }

  /* ====================================================================
   *                       adminprefs
   * ==================================================================== */

  @Override
  public void removeFromAllPrefs(final BwShareableDbentity val) throws CalFacadeException {
    getCal().removeFromAllPrefs(val);
  }

  /* ====================================================================
   *                       groups
   * ==================================================================== */

  @Override
  public BwGroup findGroup(final String account,
                           final boolean admin) throws CalFacadeException {
    return getCal().findGroup(account, admin);
  }

  /* ====================================================================
   *                   Access
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#changeAccess(org.bedework.calfacade.base.BwShareableDbentity, java.util.Collection)
   */
  @Override
  public void changeAccess(BwShareableDbentity ent,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().changeAccess(ent, aces, replaceAll);

    if (ent instanceof BwCalendar) {
      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                         (BwCalendar)ent,
                                         null,
                                         null,
                                         null);
    } else if (ent instanceof BwEventProperty) {
      ((Preferences)getPrefsHandler()).updateAdminPrefs(false,
                                                        (BwEventProperty)ent);
    }
  }

  @Override
  public void defaultAccess(BwShareableDbentity ent,
                            final AceWho who) throws CalFacadeException {
    if (ent instanceof BwCalSuiteWrapper) {
      ent = ((BwCalSuiteWrapper)ent).fetchEntity();
    }
    getCal().defaultAccess(ent, who);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#checkAccess(org.bedework.calfacade.base.BwShareableDbentity, int, boolean)
   */
  @Override
  public CurrentAccess checkAccess(final BwShareableDbentity ent, final int desiredAccess,
                                   final boolean returnResult) throws CalFacadeException {
    return getCal().checkAccess(ent, desiredAccess, returnResult);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.CalSvcI#getSynchReport(java.lang.String, java.lang.String, int, boolean)
   */
  @Override
  public SynchReport getSynchReport(final String path,
                                    final String token,
                                    final int limit,
                                    final boolean recurse) throws CalFacadeException {
    BwCalendar col = getCalendarsHandler().get(path);
    if (col == null) {
      throw new CalFacadeAccessException();
    }

    Set<SynchReportItem> items = new TreeSet<SynchReportItem>();
    String resToken = getSynchItems(col, path, token, items, recurse);
    SynchReport res = new SynchReport(items, resToken);

    if ((limit > 0) && (res.size() >= limit)) {
      if (res.size() == limit) {
        return res;
      }

      items = new TreeSet<SynchReportItem>();
      resToken = "";

      for (SynchReportItem item: res.getItems()) {
        if (item.getToken().compareTo(resToken) > 0) {
          resToken = item.getToken();
        }

        items.add(item);

        if (items.size() == limit) {
          break;
        }
      }
    }

    if (resToken.length() == 0) {
      resToken = new DtStamp().getValue() + "-0000";
    }

    return new SynchReport(items, resToken);
  }

  private boolean canSync(final BwCalendar col) {
    //if (col.getCalType() == BwCalendar.calTypeAlias) {
    //  return false;
    //}

    //if (col.getCalType() == BwCalendar.calTypeExtSub) {
    //  return false;
    //}

    return true;
  }

  /* ====================================================================
   *                   Timezones
   * ==================================================================== */

  @Override
  public UpdateFromTimeZonesInfo updateFromTimeZones(final int limit,
                                                     final boolean checkOnly,
                                                     final UpdateFromTimeZonesInfo info
                                                     ) throws CalFacadeException {
    return tzstore.updateFromTimeZones(limit, checkOnly, info);
  }

  /* ====================================================================
   *                   Get back end interface
   * ==================================================================== */

  /* This will get a calintf based on the supplied collection object.
   */
  Calintf getCal(final BwCalendar cal) throws CalFacadeException {
    return getCal();
  }

  /* We need to synchronize this code to prevent stale update exceptions.
   * db locking might be better - this could still fail in a clustered
   * environment for example.
   */
  private static volatile Object synchlock = new Object();

  /* Currently this gets a local calintf only. Later we need to use a par to
   * get calintf from a table.
   */
  Calintf getCal() throws CalFacadeException {
    if (cali != null) {
      return cali;
    }

    long start = System.currentTimeMillis();

    try {
      long beforeGetIntf = System.currentTimeMillis() - start;

      cali = CalintfFactory.getIntf(CalintfFactory.hibernateClass);

      postNotification(
                       SysEvent.makeTimedEvent("Login: about to obtain calintf",
                                               beforeGetIntf));
      postNotification(
                       SysEvent.makeTimedEvent("Login: calintf obtained",
                                               System.currentTimeMillis() - start));

      cali.open(pars.getWebMode(),
                pars.getForRestore()); // Just for the user interactions

      postNotification(
                       SysEvent.makeTimedEvent("Login: intf opened",
                                               System.currentTimeMillis() - start));

      cali.beginTransaction();

      postNotification(
                       SysEvent.makeTimedEvent("Login: transaction started",
                                               System.currentTimeMillis() - start));

      String runAsUser = pars.getUser();

      if (pars.getCalSuite() != null) {
        BwCalSuite cs = cali.getCalSuite(pars.getCalSuite());

        if (cs == null) {
          error("******************************************************");
          error("Unable to fetch calendar suite " + pars.getCalSuite());
          error("Is the database correctly initialised?");
          error("******************************************************");
          throw new CalFacadeException(CalFacadeException.unknownCalsuite,
                                       pars.getCalSuite());
        }

        getCalSuitesHandler().set(new BwCalSuiteWrapper(cs));
        /* For administrative use we use the account of the admin group the user
         * is a direct member of
         *
         * For public clients we use the calendar suite owning group.
         */
        if (!pars.getPublicAdmin()) {
          runAsUser = cs.getGroup().getOwnerHref();
        }
      }

      postNotification(
                       SysEvent.makeTimedEvent("Login: before get dirs",
                                               System.currentTimeMillis() - start));

      Directories dir = getDirectories();

      /* Get ourselves a user object */
      String authenticatedUser = pars.getAuthUser();

      if (authenticatedUser != null) {
        if (dir.isPrincipal(authenticatedUser)) {
          authenticatedUser = dir.accountFromPrincipal(authenticatedUser);
        }

        if (authenticatedUser.endsWith("/")) {
          getLogger().warn("Authenticated user " + authenticatedUser +
              " ends with \"/\"");
        }
      }

      postNotification(
                       SysEvent.makeTimedEvent("Login: before user fetch",
                                               System.currentTimeMillis() - start));

      synchronized (synchlock) {
        Users users = (Users)getUsersHandler();

        if (runAsUser == null) {
          runAsUser = authenticatedUser;
        }

        BwPrincipal currentPrincipal;
        BwPrincipal authPrincipal;
        PrivilegeSet maxAllowedPrivs = null;

        if (pars.getForRestore()) {
          authenticated = true;
          currentPrincipal = dir.caladdrToPrincipal(pars.getAuthUser());
          authPrincipal = currentPrincipal;
        } else if (authenticatedUser == null) {
          authenticated = false;
          // Unauthenticated use
          currentPrincipal = users.getUser(runAsUser);
          if (currentPrincipal == null) {
            // XXX Should we set this one up?
            currentPrincipal = BwPrincipal.makeUserPrincipal();
          }

          currentPrincipal.setUnauthenticated(true);
          authPrincipal = currentPrincipal;
          maxAllowedPrivs = PrivilegeSet.readOnlyPrivileges;
        } else {
          authenticated = true;
          currentPrincipal = users.getUser(authenticatedUser);
          if (currentPrincipal == null) {
            /* Add the user to the database. Presumably this is first logon
             */
            getLogger().debug("Add new user " + authenticatedUser);

            currentPrincipal = addUser(authenticatedUser);
            if (currentPrincipal == null) {
              error("Failed to find user after adding: " + authenticatedUser);
            }
          }

          authPrincipal = currentPrincipal;

          if (authenticatedUser.equals(runAsUser)) {
            getLogger().debug("Authenticated user " + authenticatedUser +
                " logged on");
          } else {
            currentPrincipal = users.getUser(runAsUser);
            if (currentPrincipal == null) {
              //              throw new CalFacadeException("User " + runAsUser + " does not exist.");
              /* Add the user to the database. Presumably this is first logon
               */
              getLogger().debug("Add new run-as-user " + runAsUser);

              currentPrincipal = addUser(runAsUser);
            }

            getLogger().debug("Authenticated user " + authenticatedUser +
                              " logged on - running as " + runAsUser);
          }

          currentPrincipal.setGroups(dir.getAllGroups(currentPrincipal));
          postNotification(
                           SysEvent.makeTimedEvent("Login: after get Groups",
                                                   System.currentTimeMillis() - start));

          if (!pars.getService()) {
            currentPrincipal.setPrincipalInfo(dir.getDirInfo(currentPrincipal));

            postNotification(
                             SysEvent.makeTimedEvent("Login: got Dirinfo",
                                                     System.currentTimeMillis() - start));
          }
        }

        principalInfo = new SvciPrincipalInfo(this,
                                              currentPrincipal,
                                              authPrincipal,
                                              maxAllowedPrivs);

        cali.init(pars.getLogId(),
                  getBasicSystemProperties(),
                  principalInfo,
                  null,
                  pars.getPublicAdmin(),
                  pars.getSessionsless());

        if (!currentPrincipal.getUnauthenticated()) {
          if (pars.getService()) {
            postNotification(
                             SysEvent.makePrincipalEvent(SysEvent.SysCode.SERVICE_USER_LOGIN,
                                                         currentPrincipal,
                                                         System.currentTimeMillis() - start));
          } else if (!creating) {
            users.logon(currentPrincipal);

            postNotification(
                             SysEvent.makePrincipalEvent(SysEvent.SysCode.USER_LOGIN,
                                                         currentPrincipal,
                                                         System.currentTimeMillis() - start));
          }
        } else {
          // If we have a runAsUser it's a public client. Pretend we authenticated
          currentPrincipal.setUnauthenticated(runAsUser == null);
        }

        if (pars.getPublicAdmin() || pars.isGuest()) {
          if (debug) {
            trace("PublicAdmin: " + pars.getPublicAdmin() + " user: "
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

        return cali;
      }
    } catch (CalFacadeException cfe) {
      error(cfe);
      throw cfe;
    } catch (Throwable t) {
      error(t);
      throw new CalFacadeException(t);
    } finally {
      if (cali != null) {
        cali.endTransaction();
        cali.close();
        //cali.flushAll();
      }
    }
  }

  void initPrincipal(final BwPrincipal p) throws CalFacadeException {
    getCal().addNewCalendars(p);
  }

  /** Switch to the given principal to allow us to update their stuff - for
   * example - send a notification.
   *
   * @param principal
   */
  void pushPrincipal(final BwPrincipal principal) throws CalFacadeException {
    ((SvciPrincipalInfo)principalInfo).pushPrincipal(principal);
    getCal().principalChanged();
  }

  /** Switch back to the previous principal.
   *
   * @throws CalFacadeException
   */
  void popPrincipal() throws CalFacadeException {
    ((SvciPrincipalInfo)principalInfo).popPrincipal();
    getCal().principalChanged();
  }

  /* Create the user. Get a new CalSvc object for that purpose.
   *
   */
  BwPrincipal addUser(final String val) throws CalFacadeException {
    Users users = (Users)getUsersHandler();

    /* Run this in a separate transaction to ensure we don't fail if the user
     * gets created by a concurrent process.
     */

    if (creating) {
      // Get a fake user
      return users.initUserObject(val);
    }

    CalSvc nsvc = new CalSvc();

    nsvc.init(pars, true);

    try {
      nsvc.open();
      nsvc.beginTransaction();

      Users nusers = (Users)nsvc.getUsersHandler();

      nusers.createUser(val);
    } catch (CalFacadeException cfe) {
      nsvc.rollbackTransaction();
      if (debug) {
        cfe.printStackTrace();
      }
      throw cfe;
    } catch (Throwable t) {
      nsvc.rollbackTransaction();
      if (debug) {
        t.printStackTrace();
      }
      throw new CalFacadeException(t);
    } finally {
      try {
        nsvc.endTransaction();
      } catch (CalFacadeException cfe) {
        if (!(cfe.getCause() instanceof ConstraintViolationException)) {
          throw cfe;
        }

        //Othewise we'll assume it was created by another process.
        warn("ConstraintViolationException trying to create " + val);
      } finally {
        nsvc.close();
      }
    }

    return users.getUser(val);
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

    @Override
    public void setStrictness(final int val) throws CalFacadeException {
      strictness = val;
    }

    @Override
    public int getStrictness() throws CalFacadeException {
      return strictness;
    }

    @Override
    public BwPrincipal getPrincipal() throws CalFacadeException {
      return CalSvc.this.getPrincipal();
    }

    @Override
    public BwPrincipal getOwner() throws CalFacadeException {
      if (isPublicAdmin()) {
        return getUsersHandler().getPublicUser();
      }

      return CalSvc.this.getPrincipal();
    }

    @Override
    public String getCaladdr(final String val) throws CalFacadeException {
      return getDirectories().userToCaladdr(val);
    }

    @Override
    public BwCategory findCategory(final BwString val) throws CalFacadeException {
      return getCategoriesHandler().find(val);
    }

    @Override
    public void addCategory(final BwCategory val) throws CalFacadeException {
      getCategoriesHandler().add(val);
    }

    @Override
    public BwContact getContact(final String uid) throws CalFacadeException {
      return getContactsHandler().get(uid);
    }

    @Override
    public BwContact findContact(final BwString val) throws CalFacadeException {
      return getContactsHandler().find(val,
                                                   getOwner().getPrincipalRef());
    }

    @Override
    public void addContact(final BwContact val) throws CalFacadeException {
      getContactsHandler().add(val);
    }

    @Override
    public BwLocation getLocation(final String uid) throws CalFacadeException {
      return getLocationsHandler().get(uid);
    }

    /* (non-Javadoc)
     * @see org.bedework.icalendar.IcalCallback#findLocation(org.bedework.calfacade.BwString)
     */
    @Override
    public BwLocation findLocation(final BwString address) throws CalFacadeException {
      BwLocation loc = BwLocation.makeLocation();
      loc.setAddress(address);

      return getLocationsHandler().ensureExists(loc,
                                                            getOwner().getPrincipalRef()).entity;
    }

    /* (non-Javadoc)
     * @see org.bedework.icalendar.IcalCallback#addLocation(org.bedework.calfacade.BwLocation)
     */
    @Override
    public void addLocation(final BwLocation val) throws CalFacadeException {
      getLocationsHandler().add(val);
    }

    @Override
    public Collection getEvent(final BwCalendar cal, final String guid, final String rid,
                               final RecurringRetrievalMode recurRetrieval)
            throws CalFacadeException {
      return getEventsHandler().get(cal.getPath(), guid,
                                                rid, recurRetrieval,
                                                false);
    }

    @Override
    public URIgen getURIgen() throws CalFacadeException {
      return null;
    }

    @Override
    public boolean getTimezonesByReference() throws CalFacadeException {
      return getSystemProperties().getTimezonesByReference();
    }
  }

  private String getSynchItems(final BwCalendar col,
                               final String vpath,
                               final String token,
                               final Set<SynchReportItem> items,
                               final boolean recurse) throws CalFacadeException {
    Events eventsH = (Events)getEventsHandler();
    ResourcesImpl resourcesH = (ResourcesImpl)getResourcesHandler();
    Calendars colsH = (Calendars)getCalendarsHandler();
    String newToken = "";
    BwCalendar resolvedCol = col;

    if (debug) {
      trace("sync token: " + token + " col: " + resolvedCol.getPath());
    }

    if (col.getInternalAlias()) {
      resolvedCol = getCalendarsHandler().resolveAlias(col, true, false);
    }

    /* Each collection could be:
     *    a. A calendar collection or special - like Inbox -
     *           only need to look for events.
     *    b. Other collections. Need to look for events, resources and collections.
     */

    boolean eventsOnly = resolvedCol.getCollectionInfo().onlyCalEntities;

    Set<EventInfo> evs = eventsH.getSynchEvents(resolvedCol.getPath(), token);

    for (EventInfo ei: evs) {
      SynchReportItem sri = new SynchReportItem(vpath, ei);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }
    }

    if (!eventsOnly) {
      // Look for resources
      List<BwResource> ress = resourcesH.getSynchResources(resolvedCol.getPath(), token);

      for (BwResource r: ress) {
        SynchReportItem sri = new SynchReportItem(vpath, r);
        items.add(sri);

        if (sri.getToken().compareTo(newToken) > 0) {
          newToken = sri.getToken();
        }
      }
    }

    Set<SynchReportItem> colItems = new TreeSet<SynchReportItem>();
    Set<BwCalendar> cols = colsH.getSynchCols(resolvedCol.getPath(), token);

    for (BwCalendar c: cols) {
      SynchReportItem sri = new SynchReportItem(vpath, c, canSync(c));
      colItems.add(sri);
      items.add(sri);

      if (sri.getToken().compareTo(newToken) > 0) {
        newToken = sri.getToken();
      }

      if (debug) {
        trace("     add col: " + c.getPath());
      }
    }

    if (!recurse) {
      return newToken;
    }

    if (Util.isEmpty(colItems)) {
      return newToken;
    }

    for (SynchReportItem sri: colItems) {
      if (!sri.getCanSync()) {
        continue;
      }

      BwCalendar sricol = sri.getCol();
      String t = getSynchItems(sricol,
                               Util.buildPath(true, vpath, "/", sricol.getName()),
                               token, items, true);

      if (t.compareTo(newToken) > 0) {
        newToken = t;
      }
    }

    return newToken;
  }

  /* ====================================================================
   *                   Package private methods
   * ==================================================================== */

  void touchCalendar(final BwCalendar col) throws CalFacadeException {
    getCal().touchCalendar(col);
  }

  PwEncryptionIntf getEncrypter() throws CalFacadeException {
    if (pwEncrypt != null) {
      return pwEncrypt;
    }

    try {
      String pwEncryptClass = "org.bedework.util.security.PwEncryptionDefault";
      //String pwEncryptClass = getSysparsHandler().get().getPwEncryptClass();

      pwEncrypt = (PwEncryptionIntf)CalFacadeUtil.getObject(pwEncryptClass,
                                                            PwEncryptionIntf.class);

      String privKeys = null;
      String pubKeys = null;

      GenKeysMBean gk = (GenKeysMBean)MBeanUtil.getMBean(GenKeysMBean.class,
                                                   "org.bedework:service=GenKeys");
      if (gk != null) {
        privKeys = gk.getPrivKeyFileName();
        pubKeys = gk.getPublicKeyFileName();
      }

      if (privKeys == null) {
        throw new CalFacadeException("Unable to get keyfile locations. Is genkeys service installed?");
      }

      pwEncrypt.init(privKeys, pubKeys);

      return pwEncrypt;
    } catch (CalFacadeException cfe) {
      cfe.printStackTrace();
      throw cfe;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new CalFacadeException(t);
    }
  }

  /* Get current parameters
   */
  CalSvcIPars getPars() {
    return pars;
  }

  /* See if in public admin mode
   */
  private boolean isPublicAdmin() throws CalFacadeException {
    return pars.getPublicAdmin();
  }

  /* Get a logger for messages
   */
  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void logIt(final String msg) {
    getLogger().info(msg);
  }

  private void trace(final String msg) {
    getLogger().debug(msg);
  }

  private void warn(final String msg) {
    getLogger().warn(msg);
  }

  private void error(final String msg) {
    getLogger().error(msg);
  }

  private void error(final Throwable t) {
    getLogger().error(this, t);
  }
}
