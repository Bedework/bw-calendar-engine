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
package org.bedework.caldav.bwserver;

import org.bedework.access.AccessPrincipal;
import org.bedework.access.Acl;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.access.WhoDefs;
import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.caldav.server.CalDAVEvent;
import org.bedework.caldav.server.CalDAVResource;
import org.bedework.caldav.server.CalDavHeaders;
import org.bedework.caldav.server.PropertyHandler;
import org.bedework.caldav.server.PropertyHandler.PropertyType;
import org.bedework.caldav.server.SysIntfReader;
import org.bedework.caldav.server.SysiIcalendar;
import org.bedework.caldav.server.sysinterface.CalPrincipalInfo;
import org.bedework.caldav.server.sysinterface.RetrievalMode;
import org.bedework.caldav.server.sysinterface.SysIntf;
import org.bedework.caldav.server.sysinterface.SysIntf.SynchReportData.SynchReportDataItem;
import org.bedework.caldav.util.TimeRange;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.sharing.InviteReplyType;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.ShareResultType;
import org.bedework.caldav.util.sharing.ShareType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwVersion;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.CollectionAliases;
import org.bedework.calfacade.CollectionInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.ScheduleResult.ScheduleRecipientResult;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.exc.CalFacadeInvalidSynctoken;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.filter.RetrieveList;
import org.bedework.calfacade.filter.SfpTokenizer;
import org.bedework.calfacade.filter.SimpleFilterParser;
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.ifs.Directories;
import org.bedework.calfacade.indexing.BwIndexer.DeletedState;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwPreferences.CategoryMapping;
import org.bedework.calfacade.svc.BwPreferences.CategoryMappings;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.SharingReplyResult;
import org.bedework.calfacade.util.CategoryMapInfo;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.SynchReport;
import org.bedework.calsvci.SynchReportItem;
import org.bedework.convert.IcalTranslator;
import org.bedework.convert.Icalendar;
import org.bedework.convert.ical.IcalMalformedException;
import org.bedework.convert.ical.VFreeUtil;
import org.bedework.convert.jcal.JcalTranslator;
import org.bedework.convert.jscal.JSCalTranslator;
import org.bedework.convert.xcal.XmlTranslator;
import org.bedework.jsforj.model.JSGroup;
import org.bedework.sysevents.events.HttpEvent;
import org.bedework.sysevents.events.HttpOutEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.IcalendarUtil;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.WebdavTags;
import org.bedework.util.xml.tagdefs.XcalTags;
import org.bedework.webdav.servlet.shared.PrincipalPropertySearch;
import org.bedework.webdav.servlet.shared.UrlHandler;
import org.bedework.webdav.servlet.shared.WdCollection;
import org.bedework.webdav.servlet.shared.WdEntity;
import org.bedework.webdav.servlet.shared.WebdavBadRequest;
import org.bedework.webdav.servlet.shared.WebdavException;
import org.bedework.webdav.servlet.shared.WebdavForbidden;
import org.bedework.webdav.servlet.shared.WebdavNotFound;
import org.bedework.webdav.servlet.shared.WebdavNsNode.PropertyTagEntry;
import org.bedework.webdav.servlet.shared.WebdavProperty;
import org.bedework.webdav.servlet.shared.WebdavUnauthorized;

import ietf.params.xml.ns.caldav.ExpandType;
import ietf.params.xml.ns.caldav.LimitRecurrenceSetType;
import ietf.params.xml.ns.icalendar_2.IcalendarType;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VFreeBusy;
import org.apache.james.jdkim.api.JDKIM;
import org.oasis_open.docs.ws_calendar.ns.soap.ComponentSelectionType;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

import static org.bedework.util.misc.response.Response.Status.limitExceeded;
import static org.bedework.util.misc.response.Response.Status.noAccess;
import static org.bedework.util.misc.response.Response.Status.ok;

/** Bedework implementation of SysIntf.
 *
 * @author Mike Douglass douglm at rpi.edu
 */
public class BwSysIntfImpl implements Logged, SysIntf {
  private boolean bedeworkExtensionsEnabled;

  protected BwPrincipal<?> currentPrincipal;

  private CalPrincipalInfo principalInfo;

  private BwPreferences prefs;

  private static byte[] key;
  private static final Object keyPairLock = new Object();

  /* These two set after a call to getSvci()
   */
  private IcalTranslator trans;
  private XmlTranslator xmlTrans;
  private JcalTranslator jcalTrans;
  private JSCalTranslator jscalTrans;
  private CalSvcI svci;

  private UrlHandler urlHandler;

  private AuthProperties authProperties;

  private SystemProperties sysProperties;

  private long reqInTime;

  private boolean calWs;

  private boolean synchWs;

  private static Configurations configs;

  static {
    try {
      configs = CalSvcFactoryDefault.getSystemConfig();
    } catch (final Throwable t) {
      t.printStackTrace();
    }
  }

  final static List<String> roMethods = Arrays.asList("GET", "REPORT", "PROPFIND");
  private final static Set<String> readOnlyMethods =
          new TreeSet<>(roMethods);

  private static class HttpReqInfo {
    private final HttpServletRequest req;

    HttpReqInfo(final HttpServletRequest req) {
      this.req = req;
    }

    String runAs() {
      if (req == null) {
        return null;
      }

      return CalDavHeaders.getRunAs(req);
    }

    boolean readOnly() {
      return readOnlyMethods.contains(req.getMethod());
    }

    String clientId() {
      if (req == null) {
        return null;
      }

      return CalDavHeaders.getClientId(req);
    }

    String principalToken() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-PT");
    }

    String notePr() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-NOTEPR");
    }

    String note() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-NOTE");
    }

    String socketToken() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-SOCKETTKN");
    }

    String socketPr() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-SOCKETPR");
    }

    String bedeworkExtensions() {
      if (req == null) {
        return null;
      }

      return req.getHeader("X-BEDEWORK-EXTENSIONS");
    }
  }

  @Override
  public String init(final HttpServletRequest req,
                     final String account,
                     final boolean service,
                     final boolean calWs,
                     final boolean synchWs,
                     final boolean notifyWs,
                     final boolean socketWs,
                     final String opaqueData) {
    try {
      this.calWs = calWs;
      this.synchWs = synchWs;

      final HttpReqInfo reqi = new HttpReqInfo(req);

      principalInfo = null; // In case we reinit

      if (req != null) {
        urlHandler = new UrlHandler(req, !calWs);
      } else {
        urlHandler = new UrlHandler("", null, true);
      }

      //final HttpSession session = req.getSession();
      //final ServletContext sc = session.getServletContext();

      //final String appName = sc.getInitParameter("bwappname");

      //if ((appName == null) || (appName.length() == 0)) {
      //  throw new WebdavException("bwappname is not set in web.xml");
      //}

      // Notification service calling?
      final String id;
      final String notePr = reqi.notePr();

      if (notifyWs) {
        id = doNoteHeader(reqi.note(), notePr);
      } else if (socketWs) {
        final String tkn = reqi.socketToken();

        if ((tkn == null) ||
                !tkn.equals(getSystemProperties().getSocketToken())) {
          throw new WebdavForbidden();
        }

        id = reqi.socketPr();
      } else {
        id = account;
      }

      doBedeworkExtensions(reqi.bedeworkExtensions());

      /* Find the mbean and get the config */

//      ObjectName mbeanName = new ObjectName(CalDAVConf.getServiceName(appName));

      // Call to set up ThreadLocal variables

      boolean publicAdmin = false;
      boolean adminCreateEprops = false;
      String calSuite = null;

      if (!notifyWs && (opaqueData != null)) {
        final String[] vals = opaqueData.split("\t");

        for (final String val: vals) {
          final int pos = val.indexOf("=");
          if (pos < 0) {
            continue;
          }

          final String rhs = val.substring(pos + 1);
          if (val.startsWith("public-admin=")) {
            publicAdmin = Boolean.parseBoolean(rhs);
            continue;
          }

          if (val.startsWith("adminCreateEprops=")) {
            adminCreateEprops = Boolean.parseBoolean(rhs);
            continue;
          }

          if (val.startsWith("calsuite=")) {
            calSuite = rhs;
          }
        }
      }

      getSvci(id,
              reqi.runAs(),
              service,
              publicAdmin,
              calSuite,
              reqi.clientId(),
              adminCreateEprops,
              reqi.readOnly());

      authProperties = svci.getAuthProperties();
      sysProperties = configs.getSystemProperties();
      svci.postNotification(new HttpEvent(SysCode.CALDAV_IN));
      reqInTime = System.currentTimeMillis();

      currentPrincipal = svci.getUsersHandler().getUser(id);

      if (notifyWs && (notePr != null)) {
        final String principalToken = reqi.principalToken();
        if (principalToken == null) {
          throw new WebdavUnauthorized();
        }

        final BwPreferences prefs = svci.getPrefsHandler().get();

        if ((prefs == null) ||
                (prefs.getNotificationToken() == null) ||
                !principalToken.equals(prefs.getNotificationToken())) {
          throw new WebdavUnauthorized();
        }
      }
      return id;
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public boolean testMode() {
    return sysProperties.getTestMode();
  }

  @Override
  public boolean bedeworkExtensionsEnabled() {
    return bedeworkExtensionsEnabled;
  }

  @Override
  public AuthProperties getAuthProperties() {
    return authProperties;
  }

  @Override
  public SystemProperties getSystemProperties() {
    if (sysProperties == null) {
      sysProperties = configs.getSystemProperties();
    }
    return sysProperties;
  }

  @Override
  public JDKIM getJDKIM() {
    return getSvci().getJDKIM();
  }

  @Override
  public boolean allowsSyncReport(final WdCollection<?> col) {
    if (col == null) {
      return false;
    }

    /* This - or any target if an alias - cannot be filtered at the moment. */
    final BwCalendar bwcol = ((BwCalDAVCollection)col).getCol();

    if (bwcol.getFilterExpr() != null) {
      return false;
    }

    BwCalendar leaf = bwcol;

    while (leaf.getInternalAlias()) {
      leaf = resolveAlias(leaf, false);

      if ((leaf == null) ||  // bad alias
          (leaf.getFilterExpr() != null)) {
        return false;
      }
    }

    /* This must be a collection which is either a user home or below. */

    final String[] els = bwcol.getPath().split("/");

    // First element should be "" for the leading "/"

    if ((els.length < 3) ||
        (!"".equals(els[0])) ||
        (els[1] == null) ||
        (els[2] == null) ||
        (els[2].isEmpty())) {
      return false;
    }

    return els[1].equals(BasicSystemProperties.userCalendarRoot);
  }

  @Override
  public String getDefaultContentType() {
    if (calWs) {
      return XcalTags.mimetype;
    }

    return "text/calendar";
  }

  @Override
  public String getNotificationURL() {
    final CalPrincipalInfo cpi =
            getCalPrincipalInfo(currentPrincipal);

    if (cpi == null) {
      return null;
    }

    return cpi.notificationsPath;
  }

  @Override
  public AccessPrincipal getPrincipal() {
    return currentPrincipal;
  }

  @Override
  public byte[] getPublicKey(final String domain,
                             final String service) {
    try {
      if (key == null) {
        synchronized (keyPairLock) {
          key = svci.getPublicKey(domain, service);
        }
      }

      return key;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private static class MyPropertyHandler extends PropertyHandler {
    private final static HashMap<QName, PropertyTagEntry> propertyNames =
      new HashMap<>();

    @Override
    public Map<QName, PropertyTagEntry> getPropertyNames() {
      return propertyNames;
    }
  }

  @Override
  public PropertyHandler getPropertyHandler(final PropertyType ptype) {
    return new MyPropertyHandler();
  }

  @Override
  public UrlHandler getUrlHandler() {
    return urlHandler;
  }

  @Override
  public boolean isPrincipal(final String val) {
    return getSvci().getDirectories().isPrincipal(val);
  }

  @Override
  public AccessPrincipal getPrincipalForUser(final String account) {
    try {
      final Directories dir = getSvci().getDirectories();
      return dir.getPrincipal(dir.makePrincipalUri(account, WhoDefs.whoTypeUser));
    } catch (final CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.principalNotFound)) {
        throw new WebdavNotFound(account);
      }
      throw new WebdavException(cfe);
    }
  }
  
  @Override
  public AccessPrincipal getPrincipal(final String href) {
    try {
      return getSvci().getDirectories().getPrincipal(href);
    } catch (final CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.principalNotFound)) {
        throw new WebdavNotFound(href);
      }
      throw new WebdavException(cfe);
    }
  }

  @Override
  public String makeHref(final String id, final int whoType) {
    return getUrlHandler().prefix(
            getSvci().getDirectories().makePrincipalUri(id,
                                                        whoType));
//      return getUrlPrefix() + getSvci().getDirectories().makePrincipalUri(id, whoType);
  }

  @Override
  public Collection<String>getGroups(final String rootUrl,
                                     final String principalUrl) {
    try {
      return getSvci().getDirectories().getGroups(rootUrl,
                                                  principalUrl);
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  @Override
  public AccessPrincipal caladdrToPrincipal(final String caladdr) {
      // XXX This needs to work for groups.
    return getSvci().getDirectories().caladdrToPrincipal(caladdr);
  }

  @Override
  public String principalToCaladdr(final AccessPrincipal principal) {
    try {
      return getSvci().getDirectories().principalToCaladdr(principal);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  boolean updateQuota(final AccessPrincipal principal,
                      final long inc) {
    try {
      final BwPrincipal<?> p =
              getSvci().getUsersHandler()
                       .getPrincipal(principal.getPrincipalRef());

      if (p == null) {
        return false;  // No quota - fail
      }

      if (p.getKind() != WhoDefs.whoTypeUser) {
        // XXX Cannot handle this yet
        return false;  // No quota - fail
      }

      final BwPreferences prefs = getPrefs();

      final long used = prefs.getQuotaUsed() + inc;
      prefs.setQuotaUsed(used);

      getSvci().getUsersHandler().update(p);

      return (inc < 0) ||  // Decreasing usage - let it pass
          (used <= p.getQuota());
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  @Override
  public CalPrincipalInfo getCalPrincipalInfo(final AccessPrincipal principal) {
    if (principal == null) {
      return null;
    }

    final boolean thisPrincipal = principal.equals(getSvci().getPrincipal());

    if (thisPrincipal && (principalInfo != null)) {
      return principalInfo;
    }

    final BwPrincipal<?> p =
            getSvci().getUsersHandler().getPrincipal(
                    principal.getPrincipalRef());
    if (p == null) {
      return null;
    }

    if (p.getKind() != WhoDefs.whoTypeUser) {
      // XXX Cannot handle this yet
      return null;
    }

    // SCHEDULE - just get home path and get default cal from user prefs.

    final String userHomePath = Util.buildPath(true,
                                               getSvci().getPrincipalInfo().getCalendarHomePath(p));

    final String defaultCalendarPath =
            Util.buildPath(true, userHomePath, "/",
                           BasicSystemProperties.userDefaultCalendar);
    final String inboxPath =
            Util.buildPath(true, userHomePath, "/",
                           BasicSystemProperties.userInbox);
    final String outboxPath =
            Util.buildPath(true, userHomePath, "/",
                           BasicSystemProperties.userOutbox);
    final String notificationsPath =
            Util.buildPath(true, userHomePath, "/",
                           BasicSystemProperties.defaultNotificationsName);

    final CalPrincipalInfo pi =
            new CalPrincipalInfo(p,
                                 null,
                                 null,
                                 userHomePath,
                                 defaultCalendarPath,
                                 inboxPath,
                                 outboxPath,
                                 notificationsPath,
                                 p.getQuota());

    if (thisPrincipal) {
      principalInfo = pi;
    }

    return pi;
  }

  private CalPrincipalInfo getCalPrincipalInfo(final BwPrincipalInfo pi) {
    try {
      // SCHEDULE - just get home path and get default cal from user prefs.

      String userHomePath = Util.buildPath(false, "/",
                                           BasicSystemProperties.userCalendarRoot);
      if (pi.getPrincipalHref() == null) {
        return new CalPrincipalInfo(null,
                                    pi.getCard(),
                                    pi.getCardStr(),
                                    null, // userHomePath,
                                    null, // defaultCalendarPath,
                                    null, // inboxPath,
                                    null, // outboxPath,
                                    null, // notificationsPath,
                                    0);
      }

      final BwPrincipal<?> p =
              getSvci().getDirectories()
                       .getPrincipal(pi.getPrincipalHref());

      if (pi.getPrincipalHref().startsWith(BwPrincipal.userPrincipalRoot)) {
        userHomePath = Util.buildPath(true, userHomePath,
                                      pi.getPrincipalHref().
            substring(BwPrincipal.userPrincipalRoot.length()));
      } else {
        userHomePath = Util.buildPath(true, userHomePath,
                                      pi.getPrincipalHref());
      }

      final String defaultCalendarPath =
              Util.buildPath(true, userHomePath, "/",
                      BasicSystemProperties.userDefaultCalendar);
      final String inboxPath =
              Util.buildPath(true, userHomePath, "/",
                             BasicSystemProperties.userInbox);
      final String outboxPath =
              Util.buildPath(true, userHomePath, "/",
                             BasicSystemProperties.userOutbox);
      final String notificationsPath =
              Util.buildPath(true, userHomePath, "/",
                             BasicSystemProperties.defaultNotificationsName);

      return new CalPrincipalInfo(p,
                                  pi.getCard(),
                                  pi.getCardStr(),
                                  userHomePath,
                                  defaultCalendarPath,
                                  inboxPath,
                                  outboxPath,
                                  notificationsPath,
                                  0);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<String> getPrincipalCollectionSet(final String resourceUri) {
    try {
      final ArrayList<String> al = new ArrayList<>();

      al.add(BwPrincipal.principalRoot);

      return al;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<CalPrincipalInfo> getPrincipals(
          String resourceUri,
          final PrincipalPropertySearch pps) {
    List<CalPrincipalInfo> principals = null;

    if (pps.applyToPrincipalCollectionSet) {
      /* I believe it's valid (if unhelpful) to return nothing
       */
      return new ArrayList<>();
    }

    if (!resourceUri.endsWith("/")) {
      resourceUri += "/";
    }

    final String proot = BwPrincipal.principalRoot;

    if (!resourceUri.equals(proot)) {
      return new ArrayList<>();
    }

    /* If we don't support any of the properties in the searches we don't match.
     *
     * Currently we only support calendarUserAddressSet or calendarHomeSet.
     *
     * For calendarUserAddressSet the value to match must be a valid CUA
     *
     * For calendarHomeSet it must be a valid home uri
     */
    final List<WebdavProperty> props = new ArrayList<>();
    String cutype = null;

    for (final WebdavProperty prop: pps.props) {
      if (debug()) {
        debug("Try to match " + prop);
      }

      final String pval = prop.getPval();

      if (CaldavTags.calendarUserAddressSet.equals(prop.getTag())) {
        principals = and(principals,
                         getCalPrincipalInfo(caladdrToPrincipal(pval)));
      } else if (CaldavTags.calendarHomeSet.equals(prop.getTag())) {
        final String path = getUrlHandler().unprefix(pval);

        final CalDAVCollection<?> col = getCollection(path);
        if (col != null) {
          principals = and(principals, getCalPrincipalInfo(col.getOwner()));
        }
      } else if (CaldavTags.calendarUserType.equals(prop.getTag())) {
        cutype = pval;
      } else if (WebdavTags.displayname.equals(prop.getTag())) {
        // Store for directory search
        props.add(prop);
      }
    }

    if (!props.isEmpty()) {
      // Directory search
      final Holder<Boolean> truncated = new Holder<>();
      if (principals == null) {
        principals = new ArrayList<>();
      }

      final List<BwPrincipalInfo> pis =
              getSvci().getDirectories().find(props,
                                              pps.pr.props,
                                              cutype, truncated);

      if (pis != null) {
        for (final BwPrincipalInfo pi: pis) {
          principals.add(getCalPrincipalInfo(pi));
        }
      }
    }

    if (principals == null) {
      return new ArrayList<>();
    }

    return principals;
  }
  private List<CalPrincipalInfo> and(final List<CalPrincipalInfo> pis,
                                     final CalPrincipalInfo pi) {
    if (pis == null) {
      final List<CalPrincipalInfo> newPis = new ArrayList<>();
      newPis.add(pi);
      return newPis;
    }

    if (pis.isEmpty()) {
      return pis;
    }

    for (final CalPrincipalInfo listPi: pis) {
      if (pi.principal.equals(listPi.principal)) {
        if (pis.size() == 1) {
          return pis;
        }

        final List<CalPrincipalInfo> newPis = new ArrayList<>();
        newPis.add(pi);
        return newPis;
      }
    }

    return new ArrayList<>();
  }

  @Override
  public boolean validPrincipal(final String account) {
    try {
      return getSvci().getDirectories().validPrincipal(account);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Notifications
   * ==================================================================== */

  public boolean subscribeNotification(final String principalHref,
                                       final String action,
                                       final List<String> emails) {
    final boolean add = "add".equals(action);
    final boolean remove = "remove".equals(action);

    if (!add && !remove) {
      return false;
    }

    try {
      if (remove) {
        svci.getNotificationsHandler().unsubscribe(principalHref, emails);
        return true;
      }

      if (Util.isEmpty(emails)) {
        return false;
      }

      svci.getNotificationsHandler().subscribe(principalHref, emails);
      return true;
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  @Override
  public boolean sendNotification(final String href,
                                  final NotificationType val) {
    final AccessPrincipal pr = caladdrToPrincipal(href);

    if (pr == null) {
      return false;
    }

    try {
      return svci.getNotificationsHandler()
                 .send((BwPrincipal<?>)pr, val);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void removeNotification(final String href,
                                 final NotificationType val) {
    try {
      svci.getNotificationsHandler().remove(href, val);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public List<NotificationType> getNotifications() {
    try {
      return prefix(svci.getNotificationsHandler().getAll());
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public List<NotificationType> getNotifications(final String href,
                                                 final QName type) {
    try {
      return prefix(svci.getNotificationsHandler().getMatching(href, type));
    } catch (final WebdavException wde) {
      throw wde;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private List<NotificationType> prefix(final List<NotificationType> notes) {
    for (final NotificationType n: notes) {
      n.getNotification().prefixHrefs(getUrlHandler());
    }

    return notes;
  }

  @Override
  public ShareResultType share(final CalDAVCollection<?> col,
                               final ShareType share) {
    try {
      return svci.getSharingHandler().share(unwrap(col), share);
    } catch (final CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String sharingReply(final CalDAVCollection<?> col,
                             final InviteReplyType reply) {
    try {
      final SharingReplyResult rr = svci.getSharingHandler()
                                        .reply(unwrap(col), reply);

      if ((rr == null) || !rr.getOk()) {
        return null;
      }

      return getUrlHandler().prefix(rr.getSharedAs().href());
    } catch (final CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (final CalFacadeException e) {
      throw new WebdavException(e);
    }
  }

  @Override
  public InviteType getInviteStatus(final CalDAVCollection<?> col) {
    if (col == null) {
      return null;
    }

    try {
      final InviteType inv = svci.getSharingHandler()
                                 .getInviteStatus(unwrap(col));

      if (inv == null) {
        return null;
      }

      final UrlHandler uh = getUrlHandler();

      for (final UserType u: inv.getUsers()) {
        u.setHref(uh.prefix(u.getHref()));
      }

      return inv;
    } catch (final CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (final CalFacadeException t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Scheduling
   * ==================================================================== */

  @Override
  public Collection<String> getFreebusySet() {
    try {
      final Collection<BwCalendar> cals = svci.getScheduler()
                                              .getFreebusySet();
      final Collection<String> hrefs = new ArrayList<>();

      if (cals == null) {
        return hrefs;
      }

      for (final BwCalendar cal: cals) {
        hrefs.add(getUrlHandler().prefix(cal.getPath()));
        //hrefs.add(getUrlPrefix() + cal.getPath());
      }

      return hrefs;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<SchedRecipientResult> schedule(final CalDAVEvent<?> ev) {
    final BwEvent event = getEvent(ev);
    event.setOwnerHref(currentPrincipal.getPrincipalRef());

    final var sched = getSvci().getScheduler();
    final var evinfo = getEvinfo(ev);

    if (Icalendar.itipReplyMethodType(event.getScheduleMethod())) {
      return checkStatus(sched.scheduleResponse(evinfo, null));
    }

    return checkStatus(sched.schedule(evinfo,
                                      null, null,
                                      true, null)); // iSchedule
  }

  /* ==============================================================
   *                   Events
   * ============================================================== */

  @Override
  public Collection<CalDAVEvent<?>> addEvent(final CalDAVEvent<?> ev,
                                          final boolean noInvites,
                                          final boolean rollbackOnError) {
    try {
      /* Is the event a scheduling object? */

      final EventInfo ei = getEvinfo(ev);
      mapCategories(ei);
      final EventInfo.UpdateResult resp =
              getSvci().getEventsHandler().add(ei, noInvites,
                                               false,  // scheduling - inbox
                                               false,  // autocreate
                                               rollbackOnError);

      if (resp.isOk()) {
        final Collection<BwEventProxy> bwevs = resp.failedOverrides;

        if (bwevs == null) {
          return null;
        }

        final Collection<CalDAVEvent<?>> evs = new ArrayList<>();

        for (final BwEvent bwev: bwevs) {
          evs.add(new BwCalDAVEvent(this, new EventInfo(bwev)));
        }

        return evs;
      }

      if (resp.getStatus() == noAccess) {
        throw new WebdavForbidden();
      }

      if (resp.getStatus() == limitExceeded) {
        if (CalFacadeException.schedulingTooManyAttendees
                .equals(resp.getMessage())) {
          throw new WebdavForbidden(
                  CaldavTags.maxAttendeesPerInstance,
                  ev.getParentPath());
        }
        if (CalFacadeException.invalidOverride
                .equals(resp.getMessage())) {
          throw new WebdavForbidden(CaldavTags.validCalendarData,
                                    ev.getParentPath());
        }
        throw new WebdavForbidden(resp.getMessage());
      }

      if (CalFacadeException.duplicateGuid.equals(resp.getMessage())) {
        throw new WebdavForbidden(CaldavTags.noUidConflict,
                                  ev.getParentPath());
      }
      if (CalFacadeException.duplicateName.equals(resp.getMessage())) {
        throw new WebdavForbidden(CaldavTags.noUidConflict,
                                  ev.getParentPath());
      }
      if (resp.getException() != null) {
        throw new WebdavException(resp.getException());
      }
      throw new WebdavForbidden(resp.toString());
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void reindexEvent(final CalDAVEvent<?> event) {
    try {
      final EventInfo ei = getEvinfo(event);

      getSvci().getEventsHandler().reindex(ei);
    } catch (final Throwable t) {
      error(t);
    }
  }

  @Override
  public void updateEvent(final CalDAVEvent<?> event) {
    try {
      final EventInfo ei = getEvinfo(event);

      handleUpdateResult(getSvci().getEventsHandler().update(ei, false));
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public UpdateResult updateEvent(final CalDAVEvent<?> event,
                                  final List<ComponentSelectionType> updates) {
    try {
      final EventInfo ei = getEvinfo(event);

      if (updates == null) {
        return new UpdateResult("No updates");
      }

      final CategoryMapInfo cm = getCatMapping();

      final UpdateResult ur =
              new BwUpdates(getPrincipal().getPrincipalRef(),
                            cm).
                      updateEvent(ei, updates,
                                  getSvci().getIcalCallback());
      if (!ur.getOk()) {
        return ur;
      }

      handleUpdateResult(getSvci().getEventsHandler().update(ei, false));
      return ur;
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void handleUpdateResult(final EventInfo.UpdateResult ur) {
    if (ur.getStatus() == ok) {
      return;
    }

    if (ur.getStatus() == noAccess) {
      throw new WebdavForbidden();
    }

    if (ur.getException() != null) {
      if (ur.getException() instanceof final CalFacadeException cfe) {
        if (CalFacadeException.duplicateGuid
                .equals(cfe.getMessage())) {
          throw new WebdavBadRequest("Duplicate-guid");
        }
        if (cfe instanceof final CalFacadeForbidden cff) {
          throw new WebdavForbidden(cff.getQname(), cff.getMessage());
        }
      }
      throw new WebdavException(ur.getException());
    }

    throw new WebdavException(ur.getMessage());
  }

  @Override
  public Collection<CalDAVEvent<?>> getEvents(final CalDAVCollection<?> col,
                                              final FilterBase filter,
                                              final List<String> retrieveList,
                                              final RetrievalMode recurRetrieval) {
    try {
      /* Limit the results to just this collection by adding an ANDed filter */
      final SimpleFilterParser sfp = getSvci().getFilterParser();
      final String expr = "(colPath='" + SfpTokenizer.escapeQuotes(col.getPath()) + "')";

      final ParseResult pr = sfp.parse(expr, true, null);
      if (!pr.ok) {
        throw new WebdavBadRequest("Failed to reference collection " +
                                           col.getPath() + 
                                           ": message was " + pr.message);
      }

      final FilterBase f = FilterBase.addAndChild(filter,
                                            pr.filter);

      final Collection<EventInfo> bwevs =
             getSvci().getEventsHandler().getEvents(null,// Collection
                                                    f,
                                                    null,  // start
                                                    null,  // end
                                                    RetrieveList.getRetrieveList(retrieveList),
                                                    DeletedState.noDeleted,
                                                    getRrm(recurRetrieval));

      if (bwevs == null) {
        return null;
      }

      final Collection<CalDAVEvent<?>> evs = new ArrayList<>();

      for (final EventInfo ei: bwevs) {
        if (recurRetrieval != null) {
          ei.getEvent().setForceUTC(recurRetrieval.getExpand() != null);
        }
        evs.add(new BwCalDAVEvent(this, ei));
      }

      return evs;
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final CalFacadeException cfe) {
      if (CalFacadeException.unknownProperty.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Unknown property " + cfe.getExtra());
      }
      throw new WebdavException(cfe);
    } catch (final WebdavException wde) {
      throw wde;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public CalDAVEvent<?> getEvent(final CalDAVCollection<?> col,
                                 final String val) {
    try {
      if ((col == null) || (val == null)) {
        throw new WebdavBadRequest();
      }

      final EventInfo ei = 
              getSvci().getEventsHandler().get(unwrap(col),
                                               val, null, null);
      
      if (ei == null) {
        return null;
      }

      return new BwCalDAVEvent(this, ei);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void deleteEvent(final CalDAVEvent<?> ev,
                          final boolean scheduleReply) {
    if (ev == null) {
      return;
    }

    final Response resp = getSvci().getEventsHandler()
                                   .delete(getEvinfo(ev), scheduleReply);
    if (!resp.isOk()) {
      throw new WebdavException("Failed delete:" + resp);
    }
  }

  @Override
  public Collection<SchedRecipientResult> requestFreeBusy(
          final CalDAVEvent<?> val,
          final boolean iSchedule) {
    final BwEvent ev = getEvent(val);
    if (currentPrincipal != null) {
      ev.setOwnerHref(currentPrincipal.getPrincipalRef());
    }

    final var sched = getSvci().getScheduler();
    final var evinfo = getEvinfo(val);

    if (Icalendar.itipReplyMethodType(ev.getScheduleMethod())) {
      return checkStatus(sched.scheduleResponse(evinfo, null));
    }

    return checkStatus(sched.schedule(evinfo,
                                      null, null, iSchedule, null));
  }

  @Override
  public void getSpecialFreeBusy(final String cua,
                                 final Set<String> recipients,
                                 final String originator,
                                 final TimeRange tr,
                                 final Writer wtr) {
    final BwOrganizer org = new BwOrganizer();
    org.setOrganizerUri(cua);

    final BwEvent ev = new BwEventObj();
    ev.setDtstart(getBwDt(tr.getStart()));
    ev.setDtend(getBwDt(tr.getEnd()));

    ev.setEntityType(IcalDefs.entityTypeFreeAndBusy);

    ev.setScheduleMethod(ScheduleMethods.methodTypeRequest);

    ev.setRecipients(recipients);
    ev.setOriginator(originator);
    ev.setOrganizer(org);

    final Collection<SchedRecipientResult> srrs = requestFreeBusy(
                         new BwCalDAVEvent(this, new EventInfo(ev)), false);

    for (final SchedRecipientResult srr: srrs) {
      // We expect one only
      final BwCalDAVEvent rfb = (BwCalDAVEvent)srr.freeBusy;
      if (rfb != null) {
        rfb.getEv().setOrganizer(org);

        try {
          final VFreeBusy vfreeBusy = VFreeUtil.toVFreeBusy(rfb.getEv());
          final net.fortuna.ical4j.model.Calendar ical = 
                  IcalendarUtil.newIcal(
                          ScheduleMethods.methodTypeReply,
                          BwVersion.prodId);
          ical.getComponents().add(vfreeBusy);
          IcalendarUtil.writeCalendar(ical, wtr);
        } catch (final Throwable t) {
          if (debug()) {
            error(t);
          }
          throw new WebdavException(t);
        }
      }
    }
  }

  @Override
  public CalDAVEvent<?> getFreeBusy(final CalDAVCollection<?> col,
                                    final int depth,
                                    final TimeRange timeRange) {
    try {
      final BwCalendar bwCol = unwrap(col);

      final int calType = bwCol.getCalType();

      if (!bwCol.getCollectionInfo().allowFreeBusy) {
        throw new WebdavForbidden(WebdavTags.supportedReport);
      }

      final Collection<BwCalendar> cals = new ArrayList<>();

      if (calType == BwCalendar.calTypeCalendarCollection) {
        cals.add(bwCol);
      } else if (depth != 0) { /* Cannot return anything for 0 */
        /* Make new cal object with just calendar collections as children */

        for (final BwCalendar ch: getSvci().getCalendarsHandler()
                                           .getChildren(bwCol)) {
          // For depth 1 we only add calendar collections
          if ((depth > 1) ||
              (ch.getCalType() == BwCalendar.calTypeCalendarCollection)) {
            cals.add(ch);
          }
        }
      }

      final AccessPrincipal owner = col.getOwner();
      final String orgUri;
      if (owner instanceof final BwPrincipal<?> powner) {
        orgUri = getSvci().getDirectories()
                          .principalToCaladdr(powner);
      } else {
        final BwPrincipal<?> p = BwPrincipal.makeUserPrincipal();
        p.setAccount(owner.getAccount());
        orgUri = getSvci().getDirectories().principalToCaladdr(p);
      }

      final BwOrganizer org = new BwOrganizer();
      org.setOrganizerUri(orgUri);

      final BwEvent fb;
      if (cals.isEmpty()) {
        // Return an empty object
        fb = new BwEventObj();
        fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);
        fb.setDtstart(getBwDt(timeRange.getStart()));
        fb.setDtend(getBwDt(timeRange.getEnd()));
      } else {
        fb = getSvci().getScheduler().getFreeBusy(cals,
                                                  currentPrincipal,
                                                  getBwDt(timeRange.getStart()),
                                                  getBwDt(timeRange.getEnd()),
                                                  org,
                                                  null, // uid
                                                  null);
      }


      final EventInfo ei = new EventInfo(fb);
      return new BwCalDAVEvent(this, ei);
    } catch (final WebdavException wde) {
      throw wde;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public CurrentAccess checkAccess(final WdEntity<?> ent,
                                   final int desiredAccess,
                                   final boolean returnResult) {
    try {
      if (ent instanceof CalDAVCollection) {
        return getSvci().checkAccess(unwrap((CalDAVCollection<?>)ent),
                                     desiredAccess, returnResult);
      }

      if (ent instanceof CalDAVEvent) {
        return getSvci().checkAccess(getEvent((CalDAVEvent<?>)ent),
                                     desiredAccess, returnResult);
      }

      if (ent instanceof CalDAVResource) {
        return getSvci().checkAccess(getRsrc((CalDAVResource<?>)ent),
                                     desiredAccess, returnResult);
      }

      throw new WebdavBadRequest();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  @Override
  public void updateAccess(final CalDAVEvent<?> ev,
                           final Acl acl) throws WebdavException{
    try {
      getSvci().changeAccess(getEvent(ev), acl.getAces(), true);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Collections
   * ==================================================================== */

  @Override
  public CalDAVCollection<?> newCollectionObject(final boolean isCalendarCollection,
                                                 final String parentPath) {
    final BwCalendar col = new BwCalendar();

    if (isCalendarCollection) {
      col.setCalType(BwCalendar.calTypeCalendarCollection);
    } else {
      col.setCalType(BwCalendar.calTypeFolder);
    }

    col.setColPath(parentPath);
    col.setOwnerHref(currentPrincipal.getPrincipalRef());

    return new BwCalDAVCollection(this, col);
  }

  @Override
  public void updateAccess(final CalDAVCollection<?> col,
                           final Acl acl) {
    try {
      getSvci().changeAccess(unwrap(col), acl.getAces(), true);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public int makeCollection(final CalDAVCollection<?> col) {
    final BwCalendar bwCol = unwrap(col);

    try {
      getSvci().getCalendarsHandler().add(bwCol, bwCol.getColPath());
      return HttpServletResponse.SC_CREATED;
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final CalFacadeException cfe) {
      final String msg = cfe.getMessage();
      if (CalFacadeException.duplicateCalendar.equals(msg)) {
        throw new WebdavForbidden(WebdavTags.resourceMustBeNull);
      }
      if (CalFacadeException.illegalCalendarCreation.equals(msg)) {
        throw new WebdavForbidden();
      }
      throw new WebdavException(cfe);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void copyMove(final CalDAVCollection<?> from,
                       final CalDAVCollection<?> to,
                       final boolean copy,
                       final boolean overwrite) {
    try {
      final BwCalendar bwFrom = unwrap(from);
      final BwCalendar bwTo = unwrap(to);

      if (!copy) {
        /* Move the from collection to the new location "to".
         * If the parent calendar is the same in both cases, this is just a rename.
         */
        if ((bwFrom.getColPath() == null) || (bwTo.getColPath() == null)) {
          throw new WebdavForbidden("Cannot move root");
        }

        if (bwFrom.getColPath().equals(bwTo.getColPath())) {
          // Rename
          getSvci().getCalendarsHandler().rename(bwFrom, to.getName());
          return;
        }
      }
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }

    throw new WebdavException("unimplemented");
  }

  @Override
  public boolean copyMove(final CalDAVEvent<?> from,
                          final CalDAVCollection<?> to,
                          final String name,
                          final boolean copy,
                          final boolean overwrite) {
    final Response resp = getSvci().getEventsHandler()
                                   .copyMoveNamed(getEvinfo(from),
                                                  unwrap(to), name,
                                                  copy, overwrite, false);
    if (resp.getException() != null) {
      throw new WebdavException(resp.getException());
    }

    if (resp.isOk()) {
      if ("created".equals(resp.getMessage())) {
        return true;
      }

      return false;
    }

    if (resp.getStatus() == Response.Status.forbidden) {
      throw new WebdavForbidden(resp.getMessage());
    }

    throw new WebdavException("Unexpected response from copymove: " +
                                      resp);
  }

  @Override
  public CalDAVCollection<?> getCollection(final String path) {
    try {
      final BwCalendar col = getSvci().getCalendarsHandler().get(path);

      if (col == null) {
        return null;
      }

      getSvci().getCalendarsHandler().resolveAlias(col, true, false);

      return new BwCalDAVCollection(this, col);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void updateCollection(final CalDAVCollection<?> col) {
    try {
      getSvci().getCalendarsHandler().update(unwrap(col));
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void deleteCollection(final CalDAVCollection<?> col,
                               final boolean sendSchedulingMessage) {
    try {
      getSvci().getCalendarsHandler().delete(unwrap(col), true,
                                             sendSchedulingMessage);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final CalFacadeException ce) {
      final String msg = ce.getMessage();

      if (CalFacadeException.cannotDeleteDefaultCalendar.equals(msg) ||
          CalFacadeException.cannotDeleteCalendarRoot.equals(msg)) {
        throw new WebdavForbidden();
      } else {
        throw new WebdavException(ce);
      }
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<CalDAVCollection<?>> getCollections(final CalDAVCollection<?> col) {
    try {
      final BwCalendar bwCol = unwrap(col);
      boolean isUserHome = false;
      List<Integer> provisionedTypes = null;

      /* Is this the calendar home? If so we have to ensure all
         provisioned collections exist */
      if (getPrincipal() != null) {
        final String userHomePath =
                Util.buildPath(true,
                               getSvci().getPrincipalInfo()
                                        .getCalendarHomePath(
                                                getPrincipal()));

        if (Util.buildPath(true, bwCol.getPath())
                .equals(userHomePath)) {
          isUserHome = true;
          provisionedTypes = new ArrayList<>();

          for (final CollectionInfo ci:
                  BwCalendar.getAllCollectionInfo()) {
            if (ci.provision) {
              provisionedTypes.add(ci.collectionType);
            }
          }
        }
      }

      final CalendarsI ci = getSvci().getCalendarsHandler();
      final Collection<BwCalendar> bwch = ci.getChildren(bwCol);

      final Collection<CalDAVCollection<?>> ch = new ArrayList<>();

      if (bwch == null) {
        return ch;
      }

      for (final BwCalendar c: bwch) {
        if (bedeworkExtensionsEnabled() || !c.getName().startsWith(".")) {
          ci.resolveAlias(c, true, false);
          ch.add(new BwCalDAVCollection(this, c));
        }

        if (isUserHome && !c.getAlias()) {
          provisionedTypes.remove(Integer.valueOf(c.getCalType()));
        }
      }

      if (isUserHome && !provisionedTypes.isEmpty()) {
        // Need to add some
        for (final int colType: provisionedTypes) {
          final BwCalendar pcol =
                  ci.getSpecial(currentPrincipal, colType,
                                true, PrivilegeDefs.privAny);

          ch.add(new BwCalDAVCollection(this, pcol));
        }
      }

      return ch;
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  /** Attempt to get calendar referenced by the alias. For an internal alias
   * the result will also be set in the aliasTarget property of the parameter.
   *
   * @param col to resolve
   * @param resolveSubAlias - if true and the alias points to an alias, resolve
   *                  down to a non-alias.
   * @return BwCalendar
   */
  BwCalendar resolveAlias(final BwCalendar col,
                          final boolean resolveSubAlias) {
    try {
      return getSvci().getCalendarsHandler().resolveAlias(col, resolveSubAlias, false);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Files
   * ==================================================================== */

  @Override
  public CalDAVResource<?> newResourceObject(final String parentPath) {
    final CalDAVResource<?> r = new BwCalDAVResource(this,
                                                     null);

    r.setParentPath(parentPath);
    r.setOwner(currentPrincipal);

    return r;
  }

  @Override
  public void putFile(final CalDAVCollection<?> coll,
                      final CalDAVResource<?> val) {
    try {
      final BwResource rsrc = getRsrc(val);
      rsrc.setColPath(coll.getPath());

      getSvci().getResourcesHandler().save(rsrc,
                                           false);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public CalDAVResource<?> getFile(final CalDAVCollection<?> coll,
                                   final String name) {
    try {
      final BwResource rsrc = getSvci().getResourcesHandler().get(
                              Util.buildPath(false, coll.getPath(), "/", name));

      if (rsrc == null) {
        return null;
      }

      return new BwCalDAVResource(this,
                                  rsrc);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void getFileContent(final CalDAVResource<?> val) {
    try {
      getSvci().getResourcesHandler().getContent(getRsrc(val));
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public Collection<CalDAVResource<?>> getFiles(final CalDAVCollection<?> coll) {
    try {
      final Collection<BwResource> bwrs =
            getSvci().getResourcesHandler().getAll(coll.getPath());

      if (bwrs == null) {
        return  null;
      }

      final Collection<CalDAVResource<?>> rs =
              new ArrayList<>();

      for (final BwResource r: bwrs) {
        rs.add(new BwCalDAVResource(this, r));
      }

      return rs;
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void updateFile(final CalDAVResource<?> val,
                         final boolean updateContent) {
    try {
      getSvci().getResourcesHandler().update(getRsrc(val), updateContent);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void deleteFile(final CalDAVResource<?> val) {
    try {
      updateQuota(val.getOwner(), -val.getQuotaSize());
      getSvci().getResourcesHandler().delete(Util.buildPath(false, val.getParentPath(),
                                                            "/",
                                                            val.getName()));
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public boolean copyMoveFile(final CalDAVResource<?> from,
                              final String toPath,
                              final String name,
                              final boolean copy,
                              final boolean overwrite) {
    try {
      return getSvci().getResourcesHandler().copyMove(getRsrc(from),
                                                      toPath, name,
                                                      copy,
                                                      overwrite);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String getSyncToken(final CalDAVCollection<?> col) {
    try {
      final BwCalendar bwcol = ((BwCalDAVCollection)col).getCol();
      String path = col.getPath();

      if (bwcol.getInternalAlias()) {
        path = bwcol.getAliasTarget().getPath();
      }

      return "data:," + getSvci().getCalendarsHandler().getSyncToken(path);
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public SynchReportData getSyncReport(final String path,
                                       final String token,
                                       final int limit,
                                       final boolean recurse) {
    try {
      String syncToken = null;

      if (token != null) {
        if (!token.startsWith("data:,")) {
          throw new WebdavForbidden(WebdavTags.validSyncToken, token);
        }

        syncToken = token.substring(6);
      }

      /* We managed to embed a bad sync token in the system - if it's length 16
       * treat it as null
       */

      if ((syncToken != null) && (syncToken.length() == 16)) {
        syncToken = null; // Force a full reload
      }

      final SynchReport sr = 
              getSvci().getSynchReport(path, syncToken, limit, recurse);
      if (sr == null) {
        return null;
      }

      final SynchReportData srd = new SynchReportData();

      srd.tokenValid = sr.getTokenValid();

      if (!sr.getTokenValid()) {
        return srd;
      }

      srd.items = new ArrayList<>();

      srd.token = "data:," + sr.getToken();
      srd.truncated = sr.getTruncated();

      for (final SynchReportItem sri: sr.getItems()) {
        final SynchReportDataItem srdi;

        if (sri.getEvent() != null) {
          srdi = new SynchReportDataItem(sri.getVpath(),
                                         new BwCalDAVEvent(this,
                                                           sri.getEvent()),
                                                           sri.getToken());
        } else if (sri.getResource() != null) {
          srdi = new SynchReportDataItem(sri.getVpath(),
                                         new BwCalDAVResource(this,
                                                              sri.getResource()),
                                                              sri.getToken());
        } else if (sri.getCol() != null) {
          srdi = new SynchReportDataItem(sri.getVpath(),
                                         new BwCalDAVCollection(this,
                                                                sri.getCol()),
                                         sri.getToken(),
                                         sri.getCanSync());
        } else {
          throw new RuntimeException("Unhandled sync report item type");
        }

        srd.items.add(srdi);
      }

      return srd;
    } catch (final CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (final CalFacadeInvalidSynctoken cist) {
      throw new WebdavBadRequest(WebdavTags.validSyncToken);
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Calendar toCalendar(final CalDAVEvent<?> ev,
                             final boolean incSchedMethod) {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }
      return trans.toIcal(getEvinfo(ev), meth);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public IcalendarType toIcalendar(final CalDAVEvent<?> ev,
                                   final boolean incSchedMethod,
                                   final IcalendarType pattern) {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }

      return getXmlTrans().toXMLIcalendar(getEvinfo(ev), meth, pattern,
                                          synchWs);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String toJcal(final CalDAVEvent<?> ev,
                       final boolean incSchedMethod) {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }

      return getJcalTrans().toJcal(getEvinfo(ev), meth);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String toIcalString(final Calendar cal,
                             final String contentType) {
    try {
      String ctype = null;

      if (contentType != null) {
        final String[] contentTypePars = contentType.split(";");
        ctype = contentTypePars[0];
      }

      if (ctype == null) {
        throw new WebdavException("Null content type");
      }

      if (ctype.equals("text/calendar")) {
        return IcalendarUtil.toIcalString(cal);
      }

      if (ctype.equals("application/calendar+json")) {
        return JcalTranslator.toJcal(cal);
      }

      throw new WebdavException("Unhandled content type" + contentType);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String writeCalendar(final Collection<CalDAVEvent<?>> evs,
                              final MethodEmitted method,
                              final XmlEmit xml,
                              final Writer wtr,
                              final String contentType) {
    try {
      final Collection<EventInfo> bwevs = new ArrayList<>();

      int meth = ScheduleMethods.methodTypeNone;

      if (method == MethodEmitted.publish) {
        meth = ScheduleMethods.methodTypePublish;
      }
      for (final CalDAVEvent<?> cde: evs) {
        final BwCalDAVEvent bcde = (BwCalDAVEvent)cde;

        if (method == MethodEmitted.eventMethod) {
          meth = getEvent(bcde).getScheduleMethod();
        }

        bwevs.add(bcde.getEvinfo());
      }

      String ctype = contentType;
      if ((ctype == null) ||
          (!ctype.equals("text/calendar") &&
           !ctype.equals("application/calendar+json") &&
           !ctype.equals("application/jscalendar+json") &&
           !ctype.equals(XcalTags.mimetype))) {
        ctype = getDefaultContentType();
      }

      switch (ctype) {
        case "text/calendar":
          final Calendar ical = trans.toIcal(bwevs, meth);
          if (xml == null) {
            IcalendarUtil.writeCalendar(ical, wtr);
          } else {
            xml.cdataValue(toIcalString(ical, "text/calendar"));
          }
          break;
        case "application/calendar+json":
          if (xml == null) {
            getJcalTrans().writeJcal(bwevs, meth, wtr);
          } else {
            final StringWriter sw = new StringWriter();
            getJcalTrans().writeJcal(bwevs, meth, sw);
            xml.cdataValue(sw.toString());
          }
          break;
        case "application/jscalendar+json":
          final JSGroup grp = getJScalTrans().toJScal(bwevs, meth);

          if (xml == null) {
            JSCalTranslator.writeJSCalendar(grp, wtr);
          } else {
            final StringWriter sw = new StringWriter();
            JSCalTranslator.writeJSCalendar(grp, sw);
            xml.cdataValue(sw.toString());
          }
          break;
        case XcalTags.mimetype:
          final XmlEmit x;
          if (xml == null) {
            x = new XmlEmit();
            x.startEmit(wtr);
          } else {
            x = xml;
          }
          getXmlTrans().writeXmlCalendar(bwevs, meth, x);
          break;
      }

      return ctype;
    } catch (final WebdavException we) {
      throw we;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public SysiIcalendar fromIcal(final CalDAVCollection<?> col,
                                final Reader rdr,
                                final String contentType,
                                final IcalResultType rtype,
                                final boolean mergeAttendees) {
    getSvci(); // Ensure open
    boolean rollback = true;

    final IcalTranslator trans = getTrans(contentType);
      /* (CALDAV:supported-calendar-data) */
    if (trans == null) {
      if (debug()) {
        debug("Bad content type: " + contentType);
      }
      throw new WebdavForbidden(CaldavTags.supportedCalendarData,
                                "Bad content type: " + contentType);
    }

    try {
      BwCalendar bwcol = null;
      if (col != null) {
        bwcol = unwrap(col);
      }

      final Icalendar ic = 
              trans.fromIcal(bwcol, new SysIntfReader(rdr),
                             contentType,
                             mergeAttendees);

      if (rtype == IcalResultType.OneComponent) {
        if (ic.getComponents().size() != 1) {
          throw new WebdavForbidden(CaldavTags.validCalendarObjectResource);
        }
      } else if (rtype == IcalResultType.TimeZone) {
        if (ic.getTimeZones().size() != 1) {
          throw new WebdavForbidden("Expected one timezone");
        }
      }
      final SysiIcalendar sic = new BwSysiIcalendar(this,
                                                    col,
                                                    ic);
      rollback = false;

      return sic;
    } catch (final IcalMalformedException ime) {
      throw new WebdavForbidden(ime, CaldavTags.validCalendarData);
    } catch (final CalFacadeException cfe) {
      if (CalFacadeException.unknownTimezone.equals(
              cfe.getDetailMessage())) {
        throw new WebdavForbidden(CaldavTags.validTimezone,
                                  cfe.getMessage());
      }
      if (debug()) {
        error(cfe);
      }
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                cfe.getMessage());
    } catch (final WebdavException wde) {
      if (debug()) {
        error(wde);
      }
      throw wde;
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      // Assume bad data in some way
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                t.getMessage());
    } finally {
      if (rollback) {
        getSvci().rollbackTransaction();
      }
    }
  }

  @Override
  public SysiIcalendar fromIcal(final CalDAVCollection<?> col,
                                final IcalendarType ical,
                                final IcalResultType rtype) {
    getSvci(); // Ensure open
    boolean rollback = true;

    try {
      BwCalendar bwcol = null;
      if (col != null) {
        bwcol = unwrap(col.resolveAlias(true));
      }

      final Icalendar ic = trans.fromIcal(bwcol,
                                          ical);

      if (rtype == IcalResultType.OneComponent) {
        if (ic.getComponents().size() != 1) {
          throw new WebdavBadRequest(CaldavTags.validCalendarObjectResource);
        }
      } else if (rtype == IcalResultType.TimeZone) {
        if (ic.getTimeZones().size() != 1) {
          throw new WebdavBadRequest("Expected one timezone");
        }
      }
      final SysiIcalendar sic = new BwSysiIcalendar(this, col,
                                                    ic);
      rollback = false;

      return sic;
    } catch (final WebdavException wde) {
      throw wde;
    } catch (final IcalMalformedException ime) {
      throw new WebdavForbidden(CaldavTags.validCalendarData,
                                ime.getMessage());
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      // Assume bad data in some way
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                t.getMessage());
    } finally {
      if (rollback) {
        getSvci().rollbackTransaction();
      }
    }
  }

  @Override
  public String toStringTzCalendar(final String tzid) {
    try {
      return IcalendarUtil.toStringTzCalendar(
              tzid,
              BwVersion.prodId);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String tzidFromTzdef(final String val) {
    try {
      getSvci(); // Ensure open
      final StringReader sr = new StringReader(val);

      final Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.size() != 0) || // No components other than timezones
          (ic.getTimeZones().size() != 1)) {
        if (debug()) {
          debug("Not single timezone");
        }
        throw new WebdavForbidden(CaldavTags.calendarTimezone, "Not single timezone");
      }

      /* This should be the only timezone ion the Calendar object
       */
      final TimeZone tz = ic.getTimeZones().iterator().next().tz;

      return tz.getID();
    } catch (final CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private static final String ValidateAlarmPrefix =
          """
                  BEGIN:VCALENDAR
                  VERSION:2.0
                  PRODID:bedework-validate
                  BEGIN:VEVENT
                  DTSTART:20101231T230000
                  DTEND:20110101T010000
                  SUMMARY:Just checking
                  UID:1234
                  DTSTAMP:20101125T112600
                  """;

  private static final String ValidateAlarmSuffix =
          """
                  END:VEVENT
                  END:VCALENDAR
                  """;

  /** Validate an alarm component
   *
   * @param val alarm component as string
   * @return boolean false for failure
   */
  @Override
  public boolean validateAlarm(final String val) {
    try {
      getSvci(); // Ensure open
      final StringReader sr = new StringReader(ValidateAlarmPrefix +
                                         val +
                                         ValidateAlarmSuffix);

      final Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.getEventInfo() == null)) {
        if (debug()) {
          debug("Not single event");
        }

        return false;
      }

      /* There should be alarms in the Calendar object
       */
      final EventInfo ei = ic.getEventInfo();
      final BwEvent ev = ei.getEvent();

      return ((ev.getAlarms() != null) &&
          !ev.getAlarms().isEmpty());
    } catch (final CalFacadeException cfe) {
      if (debug()) {
        error(cfe);
      }

      return false;
    }
  }

  @Override
  public void rollback() {
    svci.rollbackTransaction();
  }

  @Override
  public void close() {
    close(svci);
  }

  public Blob getBlob(final byte[] val) {
    return getSvci().getBlob(val);
  }

  /* ====================================================================
   *                         Private methods
   * ==================================================================== */

  private CategoryMapInfo getCatMapping() {
    final BwPreferences prefs = getPrefs();
    final CategoryMappings catMaps;

    if (prefs == null) {
      return new CategoryMapInfo(null, null);
    }

    final GetEntityResponse<CategoryMappings> ger =
            prefs.readCategoryMappings();
    if (!ger.isOk()) {
      // Should emit warning
      return new CategoryMapInfo(null, null);
    }

    catMaps = ger.getEntity();

    final Set<BwCalendar> topicalAreas;

    if (catMaps == null) {
      return new CategoryMapInfo(null, null);
    }

    topicalAreas = new TreeSet<>();

    // We'll need all the topical areas for the calsuite
    final BwCalendar home =
            getSvci().getCalendarsHandler().get(getSvci().getPrincipalInfo().getCalendarHomePath());
    if (home == null) {
      throw new RuntimeException("No home directory");
    }

    final Collection<BwCalendar> children = getSvci().
            getCalendarsHandler().getChildren(home);
    for (final BwCalendar child: children) {
      if (!child.getIsTopicalArea()) {
        continue;
      }

      final GetEntityResponse<CollectionAliases> geca =
              getSvci().getCalendarsHandler().getAliasInfo(child);
      if (!geca.isOk()) {
        throw new RuntimeException("Failed to get alias info: " +
                                           geca.getMessage());
      }

      final CollectionAliases ca = geca.getEntity();
      if (ca.getInvalidAlias() != null) {
        continue;
      }

      topicalAreas.add(child);
    }

    return new CategoryMapInfo(catMaps, topicalAreas);
  }

  private void mapCategories(final EventInfo ei) {
    final CategoryMapInfo cm = getCatMapping();

    if (cm.getNoMapping()) {
      return;
    }

    final BwEvent ev = ei.getEvent();
    final List<BwCategory> toRemove = new ArrayList<>();

    for (final BwCategory cat: ev.getCategories()) {
      final CategoryMapping catMap =
              cm.findMapping(cat.getWordVal());

      if (catMap == null) {
        // Not a candidate
        continue;
      }

      toRemove.add(cat);

      if (catMap.isTopicalArea()) {
        final BwCalendar mapTo = cm.getTopicalArea(catMap);

        if (mapTo == null) {
          // Should warn
          continue;
        }

        // Add an x-prop to define the alias. Categories will be added by realias.

        final BwCalendar aliasTarget = mapTo.getAliasTarget();
        final BwXproperty xp = BwXproperty.makeBwAlias(
                mapTo.getName(),
                mapTo.getAliasUri().substring(
                        BwCalendar.internalAliasUriPrefix.length()),
                aliasTarget.getPath(),
                mapTo.getPath());
        ev.addXproperty(xp);
      } else {
        // Add a category
        final BwCategory newCat = BwCategory.makeCategory();
        newCat.setWord(new BwString(null, catMap.getTo()));
        getSvci().getCategoriesHandler()
                 .ensureExists(newCat, ev.getOwnerHref());
      }
    }

    for (final BwCategory cat: toRemove) {
      ev.getCategories().remove(cat);
    }

    if (!Util.isEmpty(ei.getOverrides())) {
      for (final EventInfo oei: ei.getOverrides()) {
        mapCategories(oei);
      }
    }
  }

  private String doNoteHeader(final String hdr,
                              final String account) {

    if (hdr == null) {
      return account;
    }

    try {
      final String[] hparts = hdr.split(":");

      if (hparts.length != 2) {
        throw new WebdavBadRequest();
      }

      final String id = hparts[0];

      final NotificationProperties nprops = configs.getNotificationProps();

      final String token = hparts[1];

      if (id == null) {
        throw new WebdavBadRequest();
      }

      if (!id.equals(nprops.getNotifierId()) ||
              (token == null) ||
              !token.equals(nprops.getNotifierToken())) {
        throw new WebdavBadRequest();
      }

      if (account != null) {
        return account;
      }

      return id;
    } catch (final WebdavException wde) {
      throw wde;
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private void doBedeworkExtensions(final String hdr) {
    if (hdr == null) {
      return;
    }

    bedeworkExtensionsEnabled = Boolean.parseBoolean(hdr);
  }

  /**
   * @param sr schedule result
   * @return recipient results
   */
  private Collection<SchedRecipientResult> checkStatus(final ScheduleResult sr) {
    final String errorCode;
    final var exc = sr.getException();

    if (exc != null) {
      errorCode = sr.getException().getMessage();
    } else {
      errorCode = null;
    }

    if ((errorCode == null) ||
        (errorCode.equals(CalFacadeException.schedulingNoRecipients))) {
      final Collection<SchedRecipientResult> srrs = new ArrayList<>();

      for (final ScheduleRecipientResult bwsrr: sr.recipientResults.values()) {
        final SchedRecipientResult srr = new SchedRecipientResult();

        srr.recipient = bwsrr.recipient;
        srr.status = bwsrr.getStatus();

        if (bwsrr.freeBusy != null) {
          srr.freeBusy = new BwCalDAVEvent(this, new EventInfo(bwsrr.freeBusy));
        }

        srrs.add(srr);
      }

      return srrs;
    }

    switch (errorCode) {
      case CalFacadeException.schedulingBadMethod ->
              throw new WebdavForbidden(CaldavTags.validCalendarData,
                                        "Bad METHOD");
      case CalFacadeException.schedulingBadAttendees ->
              throw new WebdavForbidden(CaldavTags.attendeeAllowed,
                                        "Bad attendees");
      case CalFacadeException.schedulingAttendeeAccessDisallowed ->
              throw new WebdavForbidden(CaldavTags.attendeeAllowed,
                                        "attendeeAccessDisallowed");
    }

    throw new WebdavForbidden(errorCode);
  }

  private BwCalendar unwrap(final CalDAVCollection<?> col) {
    if (col == null) {
      return null;
    }

    if (!(col instanceof BwCalDAVCollection)) {
      throw new RuntimeException("Unknown implementation of BwCalDAVCollection" +
                                 col.getClass());
    }

    return ((BwCalDAVCollection)col).getCol();
  }

  private EventInfo getEvinfo(final CalDAVEvent<?> ev) {
    if (ev == null) {
      return null;
    }

    return ((BwCalDAVEvent)ev).getEvinfo();
  }

  private BwEvent getEvent(final CalDAVEvent<?> ev) {
    if (ev == null) {
      return null;
    }

    return ((BwCalDAVEvent)ev).getEv();
  }

  private BwResource getRsrc(final CalDAVResource<?> rsrc) {
    if (rsrc == null) {
      return null;
    }

    return ((BwCalDAVResource)rsrc).getRsrc();
  }

  /**
   * @return CalSvcI session object
   * @throws RuntimeException on fatal error
   */
  private CalSvcI getSvci() {
    if (!svci.isOpen()) {
      try {
        svci.open();
        svci.beginTransaction();
      } catch (final Throwable t) {
        throw new RuntimeException(t);
      }
    }

    return svci;
  }

  @SuppressWarnings("UnusedReturnValue")
  private CalSvcI getSvci(final String account,
                          final String runAs,
                          final boolean service,
                          final boolean publicAdmin,
                          final String calSuite,
                          final String clientId,
                          final boolean allowCreateEprops,
                          final boolean readonly) {
    try {
      /* account is what we authenticated with.
       * user, if non-null, is the user calendar we want to access.
       */

      final boolean possibleSuperUser =
              "root".equals(account) || // allow SuperUser
                      "admin".equals(account);
      String runAsUser = null;
      String clientIdent = null;

      if (possibleSuperUser) {
        runAsUser = runAs;
        clientIdent = clientId;
      }

      final CalSvcIPars pars =
              CalSvcIPars.getCaldavPars("bwcaldav",
                                        account,
                                        runAsUser,
                                        clientIdent,
                                        possibleSuperUser,   // allow SuperUser
                                        service,
                                        publicAdmin,
                                        calSuite,
                                        allowCreateEprops,
                                        readonly);
      svci = new CalSvcFactoryDefault().getSvc(
              getClass().getClassLoader(), pars);

      svci.open();
      svci.beginTransaction();

      trans = new IcalTranslator(svci.getIcalCallback());
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }

    return svci;
  }

  private XmlTranslator getXmlTrans() {
    if (xmlTrans == null) {
      xmlTrans = new XmlTranslator(svci.getIcalCallback());
    }

    return xmlTrans;
  }

  private JcalTranslator getJcalTrans() {
    if (jcalTrans == null) {
      jcalTrans = new JcalTranslator(svci.getIcalCallback());
    }

    return jcalTrans;
  }

  private JSCalTranslator getJScalTrans() {
    if (jscalTrans == null) {
      jscalTrans = new JSCalTranslator(svci.getIcalCallback());
    }

    return jscalTrans;
  }

  private IcalTranslator getTrans(final String contentType) {
    if (contentType == null) {
      return trans;
    }

    return switch (contentType) {
      case "text/calendar" -> trans;
      case "application/calendar+json" -> getJcalTrans();
      case "application/calendar+xml" -> getXmlTrans();
      case "application/jscalendar+json" -> getJScalTrans();
      default -> throw new RuntimeException(
              "Unsupported content type: " +
                      contentType);
    };
  }

  private void close(final CalSvcI svci) {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();

      final long reqTime = System.currentTimeMillis() - reqInTime;
      svci.postNotification(new HttpOutEvent(SysCode.CALDAV_OUT, reqTime));
    } catch (final Throwable t) {
      try {
        svci.close();
      } catch (final Throwable ignored) {
      }

      if (t instanceof CalFacadeStaleStateException) {
        throw new WebdavException(HttpServletResponse.SC_CONFLICT);
      }

      throw new WebdavException(t);
    }

    try {
      svci.close();
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private RecurringRetrievalMode getRrm(final RetrievalMode rm) {
    if (rm == null) {
      return RecurringRetrievalMode.overrides;
    }

    try {
      if (rm.getExpand() != null) {
        /* expand with time range */
        final ExpandType ex = rm.getExpand();

        final DateTime s = new DateTime(
                XcalUtil.getIcalFormatDateTime(ex.getStart()));
        final DateTime e = new DateTime(
                XcalUtil.getIcalFormatDateTime(ex.getEnd()));
        return new RecurringRetrievalMode(Rmode.expanded,
                                          getBwDt(s),
                                          getBwDt(e));
      }

      if (rm.getLimitRecurrenceSet() != null) {
        /* Only return master event and overrides in range */
        final LimitRecurrenceSetType l = rm.getLimitRecurrenceSet();

        final DateTime s = new DateTime(
                XcalUtil.getIcalFormatDateTime(l.getStart()));
        final DateTime e = new DateTime(
                XcalUtil.getIcalFormatDateTime(l.getEnd()));
        return new RecurringRetrievalMode(Rmode.overrides,
                                          getBwDt(s),
                                          getBwDt(e));
      }
    } catch (final Throwable t) {
      throw new WebdavBadRequest(CaldavTags.validFilter, "Invalid time-range");
    }

    /* Return master + overrides */
    return RecurringRetrievalMode.overrides;
  }

  private BwDateTime getBwDt(final DateTime dt) {
    try {
      if (dt == null) {
        return null;
      }

      return BwDateTime.makeBwDateTime(false, dt.toString(), null);
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  private BwPreferences getPrefs() {
    try {
      if (prefs == null) {
        prefs = getSvci().getPrefsHandler().get();
      }
    } catch (final Throwable t) {
      throw new WebdavException(t);
    }

    return prefs;
  }

  /* ==============================================================
   *                   Logged methods
   * ============================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
