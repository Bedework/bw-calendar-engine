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
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.WhoDefs;
import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.caldav.server.CalDAVEvent;
import org.bedework.caldav.server.CalDAVResource;
import org.bedework.caldav.server.CalDavHeaders;
import org.bedework.caldav.server.Organizer;
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
import org.bedework.calfacade.BwCalendar.EventListEntry;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.ScheduleResult.ScheduleRecipientResult;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.exc.CalFacadeInvalidSynctoken;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.filter.ColorMap;
import org.bedework.calfacade.filter.FilterBuilder;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.EventsI.CopyMoveStatus;
import org.bedework.calsvci.SharingI.ReplyResult;
import org.bedework.calsvci.SynchReport;
import org.bedework.calsvci.SynchReportItem;
import org.bedework.icalendar.IcalMalformedException;
import org.bedework.icalendar.IcalTranslator;
import org.bedework.icalendar.Icalendar;
import org.bedework.icalendar.Icalendar.TimeZoneInfo;
import org.bedework.icalendar.VFreeUtil;
import org.bedework.sysevents.events.HttpEvent;
import org.bedework.sysevents.events.HttpOutEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.IcalDefs.IcalComponentType;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.XmlUtil;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.WebdavTags;
import org.bedework.util.xml.tagdefs.XcalTags;
import org.bedework.webdav.servlet.shared.PrincipalPropertySearch;
import org.bedework.webdav.servlet.shared.UrlHandler;
import org.bedework.webdav.servlet.shared.WdEntity;
import org.bedework.webdav.servlet.shared.WebdavBadRequest;
import org.bedework.webdav.servlet.shared.WebdavException;
import org.bedework.webdav.servlet.shared.WebdavForbidden;
import org.bedework.webdav.servlet.shared.WebdavNotFound;
import org.bedework.webdav.servlet.shared.WebdavNsNode.PropertyTagEntry;
import org.bedework.webdav.servlet.shared.WebdavProperty;

import ietf.params.xml.ns.caldav.ExpandType;
import ietf.params.xml.ns.caldav.LimitRecurrenceSetType;
import ietf.params.xml.ns.icalendar_2.IcalendarType;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.component.VFreeBusy;
import org.apache.log4j.Logger;
import org.oasis_open.docs.ws_calendar.ns.soap.ComponentSelectionType;

import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.namespace.QName;

/** Bedework implementation of SysIntf.
 *
 * @author Mike Douglass douglm at bedework.edu
 */
public class BwSysIntfImpl implements SysIntf {
  private boolean debug;

  protected transient Logger log;

  protected AccessPrincipal currentPrincipal;

  private CalPrincipalInfo principalInfo;

  private BwPreferences prefs;

  private static byte[] key;
  private static volatile Object keyPairLock = new Object();

  /* These two set after a call to getSvci()
   */
  private IcalTranslator trans;
  private CalSvcI svci;

  private UrlHandler urlHandler;

  private AuthProperties authProperties;

  private SystemProperties sysProperties;

  private long reqInTime;

  private boolean calWs;

  private BasicSystemProperties syspars;

  @Override
  public void init(final HttpServletRequest req,
                   final String account,
                   final boolean service,
                   final boolean calWs) throws WebdavException {
    try {
      this.calWs = calWs;
      debug = getLogger().isDebugEnabled();

      principalInfo = null; // In case we reinit

      urlHandler = new UrlHandler(req, !calWs);

      HttpSession session = req.getSession();
      ServletContext sc = session.getServletContext();

      String appName = sc.getInitParameter("bwappname");

      if ((appName == null) || (appName.length() == 0)) {
        throw new WebdavException("bwappname is not set in web.xml");
      }

      /* Find the mbean and get the config */

//      ObjectName mbeanName = new ObjectName(CalDAVConf.getServiceName(appName));

      // Call to set up ThreadLocal variables

      getSvci(account,
              CalDavHeaders.getRunAs(req),
              service,
              CalDavHeaders.getClientId(req));

      authProperties = svci.getAuthProperties();
      sysProperties = svci.getSystemProperties();
      svci.postNotification(new HttpEvent(SysCode.CALDAV_IN));
      reqInTime = System.currentTimeMillis();

      currentPrincipal = svci.getUsersHandler().getUser(account);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public AuthProperties getAuthProperties() throws WebdavException {
    return authProperties;
  }

  @Override
  public SystemProperties getSystemProperties() throws WebdavException {
    return sysProperties;
  }

  @Override
  public boolean allowsSyncReport(final CalDAVCollection col) throws WebdavException {
    /* This - or any target if an alias - cannot be filtered at the moment. */
    BwCalendar bwcol = ((BwCalDAVCollection)col).getCol();

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

    String uhome = getSysPars().getUserCalendarRoot();

    if (uhome.endsWith("/")) {
      uhome = uhome.substring(0, uhome.length() - 1);
    }

    if (uhome.startsWith("/")) {
      uhome = uhome.substring(1);
    }

    String[] els = bwcol.getPath().split("/");

    // First element should be "" for the leading "/"

    if ((els.length < 3) ||
        (!"".equals(els[0])) ||
        (els[1] == null) ||
        (els[2] == null) ||
        (els[2].length() == 0)) {
      return false;
    }

    return els[1].equals(uhome);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#getAcceptContentType()
   */
  @Override
  public String getDefaultContentType() throws WebdavException {
    if (calWs) {
      return XcalTags.mimetype;
    }

    return "text/calendar";
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getPrincipal()
   */
  @Override
  public AccessPrincipal getPrincipal() throws WebdavException {
    return currentPrincipal;
  }

  @Override
  public byte[] getPublicKey(final String domain,
                             final String service) throws WebdavException {
    try {
      if (key == null) {
        synchronized (keyPairLock) {
          key = svci.getPublicKey(domain, service);
        }
      }

      return key;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private static class MyPropertyHandler extends PropertyHandler {
    private final static HashMap<QName, PropertyTagEntry> propertyNames =
      new HashMap<QName, PropertyTagEntry>();

    @Override
    public Map<QName, PropertyTagEntry> getPropertyNames() {
      return propertyNames;
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getPropertyHandler(org.bedework.caldav.server.PropertyHandler.PropertyType)
   */
  @Override
  public PropertyHandler getPropertyHandler(final PropertyType ptype) throws WebdavException {
    return new MyPropertyHandler();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getUrlHandler()
   */
  @Override
  public UrlHandler getUrlHandler() {
    return urlHandler;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getUrlPrefix()
   * /
  public String getUrlPrefix() {
    return urlPrefix;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getRelativeUrls()
   * /
  public boolean getRelativeUrls() {
    return relativeUrls;
  }*/

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#isPrincipal(java.lang.String)
   */
  @Override
  public boolean isPrincipal(final String val) throws WebdavException {
    try {
      return getSvci().getDirectories().isPrincipal(val);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getPrincipal(java.lang.String)
   */
  @Override
  public AccessPrincipal getPrincipal(final String href) throws WebdavException {
    try {
      return getSvci().getDirectories().getPrincipal(href);
    } catch (CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.principalNotFound)) {
        throw new WebdavNotFound(href);
      }
      throw new WebdavException(cfe);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#makeHref(java.lang.String, boolean)
   */
  @Override
  public String makeHref(final String id, final int whoType) throws WebdavException {
    try {
      return getUrlHandler().prefix(getSvci().getDirectories().makePrincipalUri(id, whoType));
//      return getUrlPrefix() + getSvci().getDirectories().makePrincipalUri(id, whoType);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getGroups(java.lang.String, java.lang.String)
   */
  @Override
  public Collection<String>getGroups(final String rootUrl,
                                     final String principalUrl) throws WebdavException {
    try {
      return getSvci().getDirectories().getGroups(rootUrl, principalUrl);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#caladdrToUser(java.lang.String)
   */
  @Override
  public AccessPrincipal caladdrToPrincipal(final String caladdr) throws WebdavException {
    try {
      // XXX This needs to work for groups.
      return getSvci().getDirectories().caladdrToPrincipal(caladdr);
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#userToCaladdr(java.lang.String)
   */
  @Override
  public String principalToCaladdr(final AccessPrincipal principal) throws WebdavException {
    try {
      if (principal instanceof BwPrincipal) {
        return getSvci().getDirectories().principalToCaladdr((BwPrincipal)principal);
      }

      return getSvci().getDirectories().principalToCaladdr(
                  (BwPrincipal)getPrincipal(principal.getPrincipalRef()));
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  boolean updateQuota(final AccessPrincipal principal,
                      final long inc) throws WebdavException {
    try {
      BwPrincipal p = getSvci().getUsersHandler().getPrincipal(principal.getPrincipalRef());

      if (p == null) {
        return false;  // No quota - fail
      }

      if (p.getKind() != WhoDefs.whoTypeUser) {
        // XXX Cannot handle this yet
        return false;  // No quota - fail
      }

      BwPreferences prefs = getPrefs();

      long used = prefs.getQuotaUsed() + inc;
      prefs.setQuotaUsed(used);

      getSvci().getUsersHandler().update(p);

      return (inc < 0) ||  // Decreasing usage - let it pass
          (used <= p.getQuota());
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getCalPrincipalInfo(AccessPrincipal)
   */
  @Override
  public CalPrincipalInfo getCalPrincipalInfo(final AccessPrincipal principal) throws WebdavException {
    try {
      if (principal == null) {
        return null;
      }

      if (principalInfo != null) {
        return principalInfo;
      }

      BwPrincipal p = getSvci().getUsersHandler().getPrincipal(principal.getPrincipalRef());
      if (p == null) {
        return null;
      }

      if (p.getKind() != WhoDefs.whoTypeUser) {
        // XXX Cannot handle this yet
        return null;
      }

      // SCHEDULE - just get home path and get default cal from user prefs.
      BasicSystemProperties sys = getSysPars();
      BwCalendar cal = getSvci().getCalendarsHandler().getHome(p, true);
      if (cal == null) {
        return null;
      }

      String userHomePath = Util.buildPath(true, cal.getPath());

      String defaultCalendarPath = Util.buildPath(true, userHomePath +
                                                  sys.getUserDefaultCalendar());
      String inboxPath = Util.buildPath(true, userHomePath, "/",
                                        sys.getUserInbox());;
      String outboxPath = Util.buildPath(true, userHomePath, "/",
                                         sys.getUserOutbox());
      String notificationsPath = Util.buildPath(true, userHomePath, "/",
                                                sys.getDefaultNotificationsName());

      principalInfo = new CalPrincipalInfo(p,
                                           userHomePath,
                                           defaultCalendarPath,
                                           inboxPath,
                                           outboxPath,
                                           notificationsPath,
                                           p.getQuota());

      return principalInfo;
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<String> getPrincipalCollectionSet(final String resourceUri)
          throws WebdavException {
    try {
      ArrayList<String> al = new ArrayList<String>();

      al.add(getSvci().getDirectories().getPrincipalRoot());

      return al;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<CalPrincipalInfo> getPrincipals(String resourceUri,
                                               final PrincipalPropertySearch pps)
          throws WebdavException {
    ArrayList<CalPrincipalInfo> principals = new ArrayList<CalPrincipalInfo>();

    if (pps.applyToPrincipalCollectionSet) {
      /* I believe it's valid (if unhelpful) to return nothing
       */
      return principals;
    }

    if (!resourceUri.endsWith("/")) {
      resourceUri += "/";
    }

    try {
      String proot = getSvci().getDirectories().getPrincipalRoot();

      if (!proot.endsWith("/")) {
        proot += "/";
      }

      if (!resourceUri.equals(proot)) {
        return principals;
      }
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    /* If we don't support any of the properties in the searches we don't match.
     *
     * Currently we only support calendarUserAddressSet or calendarHomeSet.
     *
     * For calendarUserAddressSet the value to match must be a valid CUA
     *
     * For calendarHomeSet it must be a valid home uri
     */
    String matchVal = null;
    boolean calendarUserAddressSet = false;
    boolean calendarHomeSet = false;

    for (PrincipalPropertySearch.PropertySearch ps: pps.propertySearches) {
      for (WebdavProperty prop: ps.props) {
        if (CaldavTags.calendarUserAddressSet.equals(prop.getTag())) {
          calendarUserAddressSet = true;
        } else if (CaldavTags.calendarHomeSet.equals(prop.getTag())) {
          calendarHomeSet = true;
        } else {
          return principals;
        }
      }

      if (calendarUserAddressSet && calendarHomeSet) {
        return principals;
      }

      String mval;
      try {
        mval = XmlUtil.getElementContent(ps.match);
      } catch (Throwable t) {
        throw new WebdavException("org.bedework.caldavintf.badvalue");
      }

      if (debug) {
        debugMsg("Try to match " + mval);
      }

      if ((matchVal != null) && !matchVal.equals(mval)) {
        return principals;
      }

      matchVal = mval;
    }

    CalPrincipalInfo cui = null;

    if (calendarUserAddressSet) {
      cui = getCalPrincipalInfo(caladdrToPrincipal(matchVal));
    } else {
      String path = getUrlHandler().unprefix(matchVal);

      CalDAVCollection col = getCollection(path);
      if (col != null) {
        cui = getCalPrincipalInfo(col.getOwner());
      }
    }

    if (cui != null) {
      principals.add(cui);
    }

    return principals;
  }

  @Override
  public boolean validPrincipal(final String account) throws WebdavException {
    try {
      return getSvci().getDirectories().validPrincipal(account);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Notifications
   * ==================================================================== */

  @Override
  public boolean sendNotification(final String href,
                                  final NotificationType val) throws WebdavException {
    AccessPrincipal pr = caladdrToPrincipal(href);

    if (pr == null) {
      return false;
    }

    try {
      return svci.getNotificationsHandler().send((BwPrincipal)pr, val);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void removeNotification(final String href,
                                 final NotificationType val) throws WebdavException {
    AccessPrincipal pr = caladdrToPrincipal(href);

    if (pr == null) {
      return;
    }

    try {
      svci.getNotificationsHandler().remove((BwPrincipal)pr, val);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public List<NotificationType> getNotifications() throws WebdavException {
    try {
      return prefix(svci.getNotificationsHandler().getAll());
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public List<NotificationType> getNotifications(final String href,
                                                 final QName type) throws WebdavException {
    try {
      return prefix(svci.getNotificationsHandler().getMatching(href, type));
    } catch (WebdavException wde) {
      throw wde;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private List<NotificationType> prefix(final List<NotificationType> notes) throws Throwable {
    for (NotificationType n: notes) {
      n.getNotification().prefixHrefs(getUrlHandler());
    }

    return notes;
  }

  @Override
  public ShareResultType share(final CalDAVCollection col,
                               final ShareType share) throws WebdavException {
    try {
      return svci.getSharingHandler().share(unwrap(col), share);
    } catch (CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String sharingReply(final CalDAVCollection col,
                             final InviteReplyType reply) throws WebdavException {
    try {
      ReplyResult rr = svci.getSharingHandler().reply(unwrap(col), reply);

      if ((rr == null) || !rr.getOk()) {
        return null;
      }

      return getUrlHandler().prefix(rr.getSharedAs().getHref());
    } catch (CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public InviteType getInviteStatus(final CalDAVCollection col) throws WebdavException {
    try {
      InviteType inv = svci.getSharingHandler().getInviteStatus(unwrap(col));

      if (inv == null) {
        return null;
      }

      UrlHandler uh = getUrlHandler();

      for (UserType u: inv.getUsers()) {
        u.setHref(uh.prefix(u.getHref()));
      }

      return inv;
    } catch (CalFacadeForbidden cf) {
      throw new WebdavForbidden(cf.getMessage());
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Scheduling
   * ==================================================================== */

  @Override
  public Collection<String> getFreebusySet() throws WebdavException {
    try {
      Collection<BwCalendar> cals = svci.getScheduler().getFreebusySet();
      Collection<String> hrefs = new ArrayList<String>();

      if (cals == null) {
        return hrefs;
      }

      for (BwCalendar cal: cals) {
        hrefs.add(getUrlHandler().prefix(cal.getPath()));
        //hrefs.add(getUrlPrefix() + cal.getPath());
      }

      return hrefs;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<SchedRecipientResult> schedule(final CalDAVEvent ev) throws WebdavException {
    try {
      ScheduleResult sr;

      BwEvent event = getEvent(ev);
      event.setOwnerHref(currentPrincipal.getPrincipalRef());
      if (Icalendar.itipReplyMethodType(event.getScheduleMethod())) {
        sr = getSvci().getScheduler().scheduleResponse(getEvinfo(ev));
      } else {
        sr = getSvci().getScheduler().schedule(getEvinfo(ev),
                                               event.getScheduleMethod(),
                                               null, null,
                                               true); // iSchedule
      }

      return checkStatus(sr);
    } catch (WebdavException we) {
      throw we;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#addEvent(org.bedework.caldav.server.CalDAVEvent, boolean, boolean)
   */
  @Override
  public Collection<CalDAVEvent> addEvent(final CalDAVEvent ev,
                                          final boolean noInvites,
                                          final boolean rollbackOnError) throws WebdavException {
    try {
      /* Is the event a scheduling object? */

      EventInfo ei = getEvinfo(ev);
      Collection<BwEventProxy> bwevs =
             getSvci().getEventsHandler().add(ei, noInvites,
                                              false,  // scheduling - inbox
                                              rollbackOnError).failedOverrides;

      if (bwevs == null) {
        return null;
      }

      Collection<CalDAVEvent> evs = new ArrayList<CalDAVEvent>();

      for (BwEvent bwev: bwevs) {
        evs.add(new BwCalDAVEvent(this, new EventInfo(bwev)));
      }

      return evs;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.invalidOverride.equals(cfe.getMessage())) {
        throw new WebdavForbidden(CaldavTags.validCalendarData,
                                  ev.getParentPath() + "/" + cfe.getExtra());
      }
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavForbidden(CaldavTags.noUidConflict,
                                  ev.getParentPath() + "/" + cfe.getExtra());
      }
      if (CalFacadeException.duplicateName.equals(cfe.getMessage())) {
        throw new WebdavForbidden(CaldavTags.noUidConflict,
                                  ev.getParentPath() + "/" + ev.getName());
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void updateEvent(final CalDAVEvent event) throws WebdavException {
    try {
      EventInfo ei = getEvinfo(event);

      getSvci().getEventsHandler().update(ei, false);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeForbidden cff) {
      throw new WebdavForbidden(cff.getQname(), cff.getMessage());
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public UpdateResult updateEvent(final CalDAVEvent event,
                                  final List<ComponentSelectionType> updates) throws WebdavException {
    try {
      EventInfo ei = getEvinfo(event);

      if (updates == null) {
        return new UpdateResult("No updates");
      }

      UpdateResult ur = new BwUpdates(getPrincipal().getPrincipalRef()).updateEvent(ei, updates,
                                                    getSvci().getIcalCallback());
      if (!ur.getOk()) {
        getSvci().rollbackTransaction();
        return ur;
      }

      getSvci().getEventsHandler().update(ei, false);
      return ur;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeForbidden cff) {
      throw new WebdavForbidden(cff.getQname(), cff.getMessage());
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#getEvents(org.bedework.caldav.server.CalDAVCollection, org.bedework.caldav.util.filter.Filter, java.util.List, org.bedework.caldav.server.sysinterface.RetrievalMode)
   */
  @Override
  public Collection<CalDAVEvent> getEvents(final CalDAVCollection col,
                                           final FilterBase filter,
                                           final List<String> retrieveList,
                                           final RetrievalMode recurRetrieval)
          throws WebdavException {
    try {
      /* Limit the results to just this collection by adding an ANDed filter */
      FilterBase f = FilterBase.addAndChild(filter,
                                   new CdvFilterBuilder().buildFilter(unwrap(col)));

      Collection<EventInfo> bwevs =
             getSvci().getEventsHandler().getEvents(null,  // Collection
                                                    f,
                                                    null,  // start
                                                    null,  // end
                                                    retrieveList,
                                                    getRrm(recurRetrieval));

      if (bwevs == null) {
        return null;
      }

      Collection<CalDAVEvent> evs = new ArrayList<CalDAVEvent>();

      for (EventInfo ei: bwevs) {
        if (recurRetrieval != null) {
          ei.getEvent().setForceUTC(recurRetrieval.getExpand() != null);
        }
        evs.add(new BwCalDAVEvent(this, ei));
      }

      return evs;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.unknownProperty.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Unknown property " + cfe.getExtra());
      }
      throw new WebdavException(cfe);
    } catch (WebdavException wde) {
      throw wde;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private class CdvFilterBuilder extends FilterBuilder {
    public CdvFilterBuilder() {
      super(new ColorMap());
    }
    public FilterBase buildFilter(final BwCalendar cal) throws CalFacadeException {
      List<String> paths;
      boolean conjunction = false;

      paths = new ArrayList<String>();

      paths.add(cal.getPath());

      return buildFilter(paths,
                         conjunction,
                         null, true);
    }

    public BwCalendar getCollection(String path) throws CalFacadeException {
      try {
        return getSvci().getCalendarsHandler().get(path);
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (WebdavException wde) {
        throw new CalFacadeException(wde);
      }
    }

    public BwCalendar resolveAlias(BwCalendar val,
                                   boolean resolveSubAlias) throws CalFacadeException {
      try {
        return getSvci().getCalendarsHandler().resolveAlias(val,
                                                            resolveSubAlias,
                                                            false);
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (WebdavException wde) {
        throw new CalFacadeException(wde);
      }
    }
    public Collection<BwCalendar> getChildren(BwCalendar col)
            throws CalFacadeException {
      try {
        return getSvci().getCalendarsHandler().getChildren(col);
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (WebdavException wde) {
        throw new CalFacadeException(wde);
      }
    }

    @Override
    public BwCategory getCategoryByName(final String name) throws CalFacadeException {
      try {
        return getSvci().getCategoriesHandler().find(new BwString(null, name));
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (WebdavException wde) {
        throw new CalFacadeException(wde);
      }
    }

    @Override
    public BwCategory getCategory(final String uid) throws CalFacadeException {
      try {
        return getSvci().getCategoriesHandler().get(uid);
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (WebdavException wde) {
        throw new CalFacadeException(wde);
      }
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getEvent(org.bedework.caldav.server.CalDAVCollection, java.lang.String, org.bedework.caldav.server.SysIntf.RetrievalMode)
   */
  @Override
  public CalDAVEvent getEvent(final CalDAVCollection col, final String val,
                              final RetrievalMode recurRetrieval)
              throws WebdavException {
    try {
      BwCalendar resolved = unwrap(col);

      if (resolved.getAlias()) {
        resolved = resolveAlias(resolved, true);

        if (resolved == null) {
          return null;
        }
      }

      String path = resolved.getPath();

      /* If the collection is an event list collection we need to change the
       * path part to be the path of the actual event
       */

      if (resolved.getCalType() == BwCalendar.calTypeEventList) {
        /* Find the event in the list using the name */
        SortedSet<EventListEntry> eles = resolved.getEventList();

        findHref: {
          for (EventListEntry ele: eles) {
            if (ele.getName().equals(val)) {
              path = ele.getPath();
              break findHref;
            }
          }

          return null; // Not in list
        } // findHref
      }

      EventInfo ei = getSvci().getEventsHandler().get(path, val,
                                                      getRrm(recurRetrieval));

      if (ei == null) {
        return null;
      }

      return new BwCalDAVEvent(this, ei);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void deleteEvent(final CalDAVEvent ev,
                          final boolean scheduleReply) throws WebdavException {
    try {
      if (ev == null) {
        return;
      }

      getSvci().getEventsHandler().delete(getEvinfo(ev), scheduleReply);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public Collection<SchedRecipientResult> requestFreeBusy(final CalDAVEvent val,
                                                          final boolean iSchedule) throws WebdavException {
    try {
      ScheduleResult sr;

      BwEvent ev = getEvent(val);
      if (currentPrincipal != null) {
        ev.setOwnerHref(currentPrincipal.getPrincipalRef());
      }

      if (Icalendar.itipReplyMethodType(ev.getScheduleMethod())) {
        sr = getSvci().getScheduler().scheduleResponse(getEvinfo(val));
      } else {
        sr = getSvci().getScheduler().schedule(getEvinfo(val),
                                               ev.getScheduleMethod(),
                                               null, null, iSchedule);
      }

      return checkStatus(sr);
    } catch (CalFacadeAccessException cfae) {
      if (debug) {
        error(cfae);
      }
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (WebdavException wde) {
      throw wde;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#getSpecialFreeBusy(java.lang.String, java.util.Set, java.lang.String, org.bedework.caldav.util.TimeRange, java.io.Writer)
   */
  @Override
  public void getSpecialFreeBusy(final String cua,
                                 final Set<String> recipients,
                                 final String originator,
                                 final TimeRange tr,
                                 final Writer wtr) throws WebdavException {
    BwOrganizer org = new BwOrganizer();
    org.setOrganizerUri(cua);

    BwEvent ev = new BwEventObj();
    ev.setDtstart(getBwDt(tr.getStart()));
    ev.setDtend(getBwDt(tr.getEnd()));

    ev.setEntityType(IcalDefs.entityTypeFreeAndBusy);

    ev.setScheduleMethod(ScheduleMethods.methodTypeRequest);

    ev.setRecipients(recipients);
    ev.setOriginator(originator);
    ev.setOrganizer(org);

    Collection<SchedRecipientResult> srrs = requestFreeBusy(
                         new BwCalDAVEvent(this, new EventInfo(ev)), false);

    for (SchedRecipientResult srr: srrs) {
      // We expect one only
      BwCalDAVEvent rfb = (BwCalDAVEvent)srr.freeBusy;
      if (rfb != null) {
        rfb.getEv().setOrganizer(org);

        try {
          VFreeBusy vfreeBusy = VFreeUtil.toVFreeBusy(rfb.getEv());
          net.fortuna.ical4j.model.Calendar ical = IcalTranslator.newIcal(ScheduleMethods.methodTypeReply);
          ical.getComponents().add(vfreeBusy);
          IcalTranslator.writeCalendar(ical, wtr);
        } catch (Throwable t) {
          if (debug) {
            error(t);
          }
          throw new WebdavException(t);
        }
      }
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#getFreeBusy(org.bedework.caldav.server.CalDAVCollection, int, org.bedework.caldav.util.TimeRange)
   */
  @Override
  public CalDAVEvent getFreeBusy(final CalDAVCollection col,
                                 final int depth,
                                 final TimeRange timeRange) throws WebdavException {
    try {
      BwCalendar bwCol = unwrap(col);

      int calType = bwCol.getCalType();

      if (!bwCol.getCollectionInfo().allowFreeBusy) {
        throw new WebdavForbidden(WebdavTags.supportedReport);
      }

      Collection<BwCalendar> cals = new ArrayList<BwCalendar>();

      if (calType == BwCalendar.calTypeCalendarCollection) {
        cals.add(bwCol);
      } else if (depth == 0) {
        /* Cannot return anything */
      } else {
        /* Make new cal object with just calendar collections as children */

        for (BwCalendar ch: getSvci().getCalendarsHandler().getChildren(bwCol)) {
          // For depth 1 we only add calendar collections
          if ((depth > 1) ||
              (ch.getCalType() == BwCalendar.calTypeCalendarCollection)) {
            cals.add(ch);
          }
        }
      }

      AccessPrincipal owner = col.getOwner();
      String orgUri;
      if (owner instanceof BwPrincipal) {
        orgUri = getSvci().getDirectories().principalToCaladdr((BwPrincipal)owner);
      } else {
        BwPrincipal p = BwPrincipal.makeUserPrincipal();
        p.setAccount(owner.getAccount());
        orgUri = getSvci().getDirectories().principalToCaladdr(p);
      }

      BwOrganizer org = new BwOrganizer();
      org.setOrganizerUri(orgUri);

      BwEvent fb;
      if (cals.isEmpty()) {
        // Return an empty object
        fb = new BwEventObj();
        fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);
        fb.setDtstart(getBwDt(timeRange.getStart()));
        fb.setDtend(getBwDt(timeRange.getEnd()));
      } else {
        fb = getSvci().getScheduler().getFreeBusy(cals,
                                                  (BwPrincipal)currentPrincipal,
                                                  getBwDt(timeRange.getStart()),
                                                  getBwDt(timeRange.getEnd()),
                                                  org,
                                                  null, // uid
                                                  null);
      }


      EventInfo ei = new EventInfo(fb);
      return new BwCalDAVEvent(this, ei);
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (WebdavException wde) {
      throw wde;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public CurrentAccess checkAccess(final WdEntity ent,
                                   final int desiredAccess,
                                   final boolean returnResult)
          throws WebdavException {
    try {
      if (ent instanceof CalDAVCollection) {
        return getSvci().checkAccess(unwrap((CalDAVCollection)ent),
                                     desiredAccess, returnResult);
      }

      if (ent instanceof CalDAVEvent) {
        return getSvci().checkAccess(getEvent((CalDAVEvent)ent),
                                     desiredAccess, returnResult);
      }

      if (ent instanceof CalDAVResource) {
        return getSvci().checkAccess(getRsrc((CalDAVResource)ent),
                                     desiredAccess, returnResult);
      }

      throw new WebdavBadRequest();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  @Override
  public void updateAccess(final CalDAVEvent ev,
                           final Acl acl) throws WebdavException{
    try {
      getSvci().changeAccess(getEvent(ev), acl.getAces(), true);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Collections
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#newCollectionObject(boolean, java.lang.String)
   */
  @Override
  public CalDAVCollection newCollectionObject(final boolean isCalendarCollection,
                                              final String parentPath) throws WebdavException {
    BwCalendar col = new BwCalendar();

    if (isCalendarCollection) {
      col.setCalType(BwCalendar.calTypeCalendarCollection);
    } else {
      col.setCalType(BwCalendar.calTypeFolder);
    }

    col.setColPath(parentPath);
    col.setOwnerHref(currentPrincipal.getPrincipalRef());

    return new BwCalDAVCollection(this, col);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#updateAccess(org.bedework.caldav.server.CalDAVCollection, Acl)
   */
  @Override
  public void updateAccess(final CalDAVCollection col,
                           final Acl acl) throws WebdavException {
    try {
      getSvci().changeAccess(unwrap(col), acl.getAces(), true);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#makeCollection(org.bedework.caldav.server.CalDAVCollection)
   */
  @Override
  public int makeCollection(final CalDAVCollection col) throws WebdavException {
    BwCalendar bwCol = unwrap(col);

    try {
      getSvci().getCalendarsHandler().add(bwCol, bwCol.getColPath());
      return HttpServletResponse.SC_CREATED;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      String msg = cfe.getMessage();
      if (CalFacadeException.duplicateCalendar.equals(msg)) {
        throw new WebdavForbidden(WebdavTags.resourceMustBeNull);
      }
      if (CalFacadeException.illegalCalendarCreation.equals(msg)) {
        throw new WebdavForbidden();
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#copyMove(org.bedework.caldav.server.CalDAVCollection, org.bedework.caldav.server.CalDAVCollection, boolean, boolean)
   */
  @Override
  public void copyMove(final CalDAVCollection from,
                       final CalDAVCollection to,
                       final boolean copy,
                       final boolean overwrite) throws WebdavException {
    try {
      BwCalendar bwFrom = unwrap(from);
      BwCalendar bwTo = unwrap(to);

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
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    throw new WebdavException("unimplemented");
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#copyMove(org.bedework.caldav.server.CalDAVEvent, org.bedework.caldav.server.CalDAVCollection, java.lang.String, boolean, boolean)
   */
  @Override
  public boolean copyMove(final CalDAVEvent from,
                          final CalDAVCollection to,
                          String name,
                          final boolean copy,
                          final boolean overwrite) throws WebdavException {
    CopyMoveStatus cms;
    try {
      cms = getSvci().getEventsHandler().copyMoveNamed(getEvinfo(from),
                                                       unwrap(to), name,
                                                       copy, overwrite, false);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    if (cms == CopyMoveStatus.changedUid) {
      throw new WebdavForbidden("Cannot change uid");
    }

    if (cms == CopyMoveStatus.duplicateUid) {
      throw new WebdavForbidden("duplicate uid");
    }

    if (cms == CopyMoveStatus.destinationExists) {
      if (name == null) {
        name = from.getName();
      }
      throw new WebdavForbidden("Destination exists: " + name);
    }

    if (cms == CopyMoveStatus.ok) {
      return false;
    }

    if (cms == CopyMoveStatus.created) {
      return true;
    }

    throw new WebdavException("Unexpected response from copymove");
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getCollection(java.lang.String)
   */
  @Override
  public CalDAVCollection getCollection(final String path) throws WebdavException {
    try {
      BwCalendar col = getSvci().getCalendarsHandler().get(path);

      if (col == null) {
        return null;
      }

      getSvci().getCalendarsHandler().resolveAlias(col, true, false);

      return new BwCalDAVCollection(this, col);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public void updateCollection(final CalDAVCollection col) throws WebdavException {
    try {
      getSvci().getCalendarsHandler().update(unwrap(col));
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      if (CalFacadeException.duplicateGuid.equals(cfe.getMessage())) {
        throw new WebdavBadRequest("Duplicate-guid");
      }
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#deleteCollection(org.bedework.caldav.server.CalDAVCollection)
   */
  @Override
  public void deleteCollection(final CalDAVCollection col) throws WebdavException {
    try {
      getSvci().getCalendarsHandler().delete(unwrap(col), true);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException ce) {
      String msg = ce.getMessage();

      if (CalFacadeException.cannotDeleteDefaultCalendar.equals(msg) ||
          CalFacadeException.cannotDeleteCalendarRoot.equals(msg)) {
        throw new WebdavForbidden();
      } else {
        throw new WebdavException(ce);
      }
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getCollections(org.bedework.caldav.server.CalDAVCollection)
   */
  @Override
  public Collection<CalDAVCollection> getCollections(final CalDAVCollection col) throws WebdavException {
    try {
      Collection<BwCalendar> bwch = getSvci().getCalendarsHandler().getChildren(unwrap(col));

      Collection<CalDAVCollection> ch = new ArrayList<CalDAVCollection>();

      if (bwch == null) {
        return ch;
      }

      for (BwCalendar c: bwch) {
        getSvci().getCalendarsHandler().resolveAlias(c, true, false);
        ch.add(new BwCalDAVCollection(this, c));
      }

      return ch;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /** Attempt to get calendar referenced by the alias. For an internal alias
   * the result will also be set in the aliasTarget property of the parameter.
   *
   * @param col
   * @param resolveSubAlias - if true and the alias points to an alias, resolve
   *                  down to a non-alias.
   * @return BwCalendar
   * @throws CalFacadeException
   */
  BwCalendar resolveAlias(final BwCalendar col,
                          final boolean resolveSubAlias) throws WebdavException {
    try {
      return getSvci().getCalendarsHandler().resolveAlias(col, resolveSubAlias, false);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* ====================================================================
   *                   Files
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#newResourceObject(java.lang.String)
   */
  @Override
  public CalDAVResource newResourceObject(final String parentPath) throws WebdavException {
    CalDAVResource r = new BwCalDAVResource(this,
                                            null);

    r.setParentPath(parentPath);
    r.setOwner(currentPrincipal);

    return r;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#putFile(org.bedework.caldav.server.CalDAVCollection, org.bedework.caldav.server.CalDAVResource)
   */
  @Override
  public void putFile(final CalDAVCollection coll,
                      final CalDAVResource val) throws WebdavException {
    try {
      getSvci().getResourcesHandler().save(coll.getPath(), getRsrc(val));
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getFile(org.bedework.caldav.server.CalDAVCollection, java.lang.String)
   */
  @Override
  public CalDAVResource getFile(final CalDAVCollection coll,
                                final String name) throws WebdavException {
    try {
      BwResource rsrc = getSvci().getResourcesHandler().get(
                              Util.buildPath(false, coll.getPath(), "/", name));

      if (rsrc == null) {
        return null;
      }

      return new BwCalDAVResource(this,
                                  rsrc);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getFileContent(org.bedework.caldav.server.CalDAVResource)
   */
  @Override
  public void getFileContent(final CalDAVResource val) throws WebdavException {
    try {
      getSvci().getResourcesHandler().getContent(getRsrc(val));
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#getFiles(org.bedework.caldav.server.CalDAVCollection)
   */
  @Override
  public Collection<CalDAVResource> getFiles(final CalDAVCollection coll) throws WebdavException {
    try {
      Collection<BwResource> bwrs =
            getSvci().getResourcesHandler().getAll(coll.getPath());

      if (bwrs == null) {
        return  null;
      }

      Collection<CalDAVResource> rs = new ArrayList<CalDAVResource>();

      for (BwResource r: bwrs) {
        rs.add(new BwCalDAVResource(this, r));
      }

      return rs;
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#updateFile(org.bedework.caldav.server.CalDAVResource, boolean)
   */
  @Override
  public void updateFile(final CalDAVResource val,
                         final boolean updateContent) throws WebdavException {
    try {
      getSvci().getResourcesHandler().update(getRsrc(val), updateContent);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#deleteFile(org.bedework.caldav.server.CalDAVResource)
   */
  @Override
  public void deleteFile(final CalDAVResource val) throws WebdavException {
    try {
      updateQuota(val.getOwner(), -val.getQuotaSize());
      getSvci().getResourcesHandler().delete(Util.buildPath(false, val.getParentPath(),
                                                            "/",
                                                            val.getName()));
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.SysIntf#copyMoveFile(org.bedework.caldav.server.CalDAVResource, java.lang.String, java.lang.String, boolean, boolean)
   */
  @Override
  public boolean copyMoveFile(final CalDAVResource from,
                              final String toPath,
                              final String name,
                              final boolean copy,
                              final boolean overwrite) throws WebdavException {
    try {
      return getSvci().getResourcesHandler().copyMove(getRsrc(from),
                                                      toPath, name,
                                                      copy,
                                                      overwrite);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String getSyncToken(final CalDAVCollection col) throws WebdavException {
    try {
      BwCalendar bwcol = ((BwCalDAVCollection)col).getCol();
      String path = col.getPath();

      if (bwcol.getInternalAlias()) {
        path = bwcol.getAliasTarget().getPath();
      }

      return "data:," + getSvci().getCalendarsHandler().getSyncToken(path);
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public SynchReportData getSyncReport(final String path,
                                       final String token,
                                       final int limit,
                                       final boolean recurse) throws WebdavException {
    try {
      String syncToken = null;

      if (token != null) {
        if (!token.startsWith("data:,")) {
          throw new CalFacadeInvalidSynctoken(token);
        }

        syncToken = token.substring(6);
      }

      /* We managed to embed a bad sync token in the system - if it's length 16
       * treat it as null
       */

      if ((syncToken != null) && (syncToken.length() == 16)) {
        syncToken = null; // Force a full reload
      }

      SynchReportData srd = new SynchReportData();

      srd.items = new ArrayList<SynchReportDataItem>();

      SynchReport sr = getSvci().getSynchReport(path, syncToken, limit, recurse);

      srd.token = "data:," + sr.getToken();
      srd.truncated = sr.getTruncated();

      for (SynchReportItem sri: sr.getItems()) {
        SynchReportDataItem srdi;

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
    } catch (CalFacadeAccessException cfae) {
      throw new WebdavForbidden();
    } catch (CalFacadeInvalidSynctoken cist) {
      throw new WebdavBadRequest(WebdavTags.validSyncToken);
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#toCalendar(org.bedework.caldav.server.CalDAVEvent, boolean)
   */
  @Override
  public Calendar toCalendar(final CalDAVEvent ev,
                             final boolean incSchedMethod) throws WebdavException {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }
      return trans.toIcal(getEvinfo(ev), meth);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public IcalendarType toIcalendar(final CalDAVEvent ev,
                                   final boolean incSchedMethod,
                                   final IcalendarType pattern) throws WebdavException {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }

      return trans.toXMLIcalendar(getEvinfo(ev), meth, pattern);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String toJcal(final CalDAVEvent ev,
                       final boolean incSchedMethod,
                       final IcalendarType pattern) throws WebdavException {
    try {
      int meth = ScheduleMethods.methodTypeNone;

      if (incSchedMethod) {
        meth = getEvent(ev).getScheduleMethod();
      }

      return trans.toJcal(getEvinfo(ev), meth, pattern);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#toIcalString(net.fortuna.ical4j.model.Calendar)
   */
  @Override
  public String toIcalString(final Calendar cal,
                             final String contentType) throws WebdavException {
    try {
      if (contentType.equals("text/calendar")) {
        return IcalTranslator.toIcalString(cal);
      }

      if (contentType.equals("application/calendar+json")) {
        return IcalTranslator.toJcal(cal, null);
      }

      throw new WebdavException("Unhandled content type" + contentType);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String writeCalendar(final Collection<CalDAVEvent> evs,
                              final MethodEmitted method,
                              final XmlEmit xml,
                              final Writer wtr,
                              final String contentType) throws WebdavException {
    try {
      Collection<EventInfo> bwevs = new ArrayList<EventInfo>();

      int meth = ScheduleMethods.methodTypeNone;

      if (method == MethodEmitted.publish) {
        meth = ScheduleMethods.methodTypePublish;
      }
      for (CalDAVEvent cde: evs) {
        BwCalDAVEvent bcde = (BwCalDAVEvent)cde;

        if (method == MethodEmitted.eventMethod) {
          meth = getEvent(bcde).getScheduleMethod();
        }

        bwevs.add(bcde.getEvinfo());
      }

      String ctype = contentType;
      if ((ctype == null) ||
          (!ctype.equals("text/calendar") &&
           !ctype.equals("application/calendar+json") &&
           !ctype.equals(XcalTags.mimetype))) {
        ctype = getDefaultContentType();
      }

      if (ctype.equals("text/calendar")) {
        Calendar ical = trans.toIcal(bwevs, meth);
        if (xml == null) {
          IcalTranslator.writeCalendar(ical, wtr);
        } else {
          xml.cdataValue(toIcalString(ical, "text/calendar"));
        }
      } else if (ctype.equals("application/calendar+json")) {
        if (xml == null) {
          trans.writeJcal(bwevs, meth, wtr);
        } else {
          trans.writeJcal(bwevs, meth, xml.getWriter());
        }
      } else if (ctype.equals(XcalTags.mimetype)) {
        XmlEmit x;
        if (xml == null) {
          x = new XmlEmit();
          x.startEmit(wtr);
        } else {
          x = xml;
        }
        trans.writeXmlCalendar(bwevs, meth, x);
      }

      return ctype;
    } catch (WebdavException we) {
      throw we;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.sysinterface.SysIntf#fromIcal(org.bedework.caldav.server.CalDAVCollection, java.io.Reader, java.lang.String, org.bedework.caldav.server.sysinterface.SysIntf.IcalResultType, boolean)
   */
  @Override
  public SysiIcalendar fromIcal(final CalDAVCollection col,
                                final Reader rdr,
                                final String contentType,
                                final IcalResultType rtype,
                                final boolean mergeAttendees) throws WebdavException {
    getSvci(); // Ensure open
    boolean rollback = true;

      /* (CALDAV:supported-calendar-data) */
    if (!contentType.equals("text/calendar") &&
            !contentType.equals("application/calendar+json")) {
      if (debug) {
        debugMsg("Bad content type: " + contentType);
      }
      throw new WebdavForbidden(CaldavTags.supportedCalendarData,
                                "Bad content type: " + contentType);
    }

    try {
      BwCalendar bwcol = null;
      if (col != null) {
        bwcol = unwrap(col);
      }

      Icalendar ic = trans.fromIcal(bwcol, new SysIntfReader(rdr),
                                    contentType,
                                    true,  // diff the contents
                                    mergeAttendees);

      if (rtype == IcalResultType.OneComponent) {
        if (ic.getComponents().size() != 1) {
          throw new WebdavBadRequest(CaldavTags.validCalendarObjectResource);
        }

        if (!(ic.getComponents().iterator().next() instanceof EventInfo)) {
          throw new WebdavBadRequest(CaldavTags.validCalendarObjectResource);
        }
      } else if (rtype == IcalResultType.TimeZone) {
        if (ic.getTimeZones().size() != 1) {
          throw new WebdavBadRequest("Expected one timezone");
        }
      }
      SysiIcalendar sic = new MySysiIcalendar(this, ic);
      rollback = false;

      return sic;
    } catch (WebdavException wde) {
      throw wde;
    } catch (IcalMalformedException ime) {
      throw new WebdavForbidden(CaldavTags.validCalendarData,
                                ime.getMessage());
    } catch (Throwable t) {
      if (debug) {
        error(t);
      }
      // Assume bad data in some way
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                t.getMessage());
    } finally {
      if (rollback) {
        try {
          getSvci().rollbackTransaction();
        } catch (Throwable t) {
        }
      }
    }
  }

  @Override
  public SysiIcalendar fromIcal(final CalDAVCollection col,
                                final IcalendarType ical,
                                final IcalResultType rtype) throws WebdavException {
    getSvci(); // Ensure open
    boolean rollback = true;

    try {
      BwCalendar bwcol = null;
      if (col != null) {
        bwcol = unwrap(col.resolveAlias(true));
      }

      Icalendar ic = trans.fromIcal(bwcol,
                                    ical,
                                    true);  // diff the contents

      if (rtype == IcalResultType.OneComponent) {
        if (ic.getComponents().size() != 1) {
          throw new WebdavBadRequest(CaldavTags.validCalendarObjectResource);
        }

        if (!(ic.getComponents().iterator().next() instanceof EventInfo)) {
          throw new WebdavBadRequest(CaldavTags.validCalendarObjectResource);
        }
      } else if (rtype == IcalResultType.TimeZone) {
        if (ic.getTimeZones().size() != 1) {
          throw new WebdavBadRequest("Expected one timezone");
        }
      }
      SysiIcalendar sic = new MySysiIcalendar(this, ic);
      rollback = false;

      return sic;
    } catch (WebdavException wde) {
      throw wde;
    } catch (IcalMalformedException ime) {
      throw new WebdavForbidden(CaldavTags.validCalendarData,
                                ime.getMessage());
    } catch (Throwable t) {
      if (debug) {
        error(t);
      }
      // Assume bad data in some way
      throw new WebdavForbidden(CaldavTags.validCalendarObjectResource,
                                t.getMessage());
    } finally {
      if (rollback) {
        try {
          getSvci().rollbackTransaction();
        } catch (Throwable t) {
        }
      }
    }
  }

  @Override
  public String toStringTzCalendar(final String tzid) throws WebdavException {
    try {
      return trans.toStringTzCalendar(tzid);
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public String tzidFromTzdef(final String val) throws WebdavException {
    try {
      getSvci(); // Ensure open
      StringReader sr = new StringReader(val);

      Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.size() != 0) || // No components other than timezones
          (ic.getTimeZones().size() != 1)) {
        if (debug) {
          debugMsg("Not single timezone");
        }
        throw new WebdavForbidden(CaldavTags.calendarTimezone, "Not single timezone");
      }

      /* This should be the only timezone ion the Calendar object
       */
      TimeZone tz = ic.getTimeZones().iterator().next().tz;

      return tz.getID();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private static final String ValidateAlarmPrefix =
      "BEGIN:VCALENDAR\n" +
      "VERSION:2.0\n" +
      "PRODID:bedework-validate\n" +
      "BEGIN:VEVENT\n" +
      "DTSTART:20101231T230000\n" +
      "DTEND:20110101T010000\n" +
      "SUMMARY:Just checking\n" +
      "UID:1234\n" +
      "DTSTAMP:20101125T112600\n";

  private static final String ValidateAlarmSuffix =
      "END:VEVENT\n" +
      "END:VCALENDAR\n";

  /** Validate an alarm component
   *
   * @param val
   * @return boolean false for failure
   * @throws WebdavException
   */
  @Override
  public boolean validateAlarm(final String val) throws WebdavException {
    try {
      getSvci(); // Ensure open
      StringReader sr = new StringReader(ValidateAlarmPrefix +
                                         val +
                                         ValidateAlarmSuffix);

      Icalendar ic = trans.fromIcal(null, sr);

      if ((ic == null) ||
          (ic.getEventInfo() == null)) {
        if (debug) {
          debugMsg("Not single event");
        }

        return false;
      }

      /* There should be alarms in the Calendar object
       */
      EventInfo ei = ic.getEventInfo();
      BwEvent ev = ei.getEvent();

      if ((ev.getAlarms() == null) ||
          ev.getAlarms().isEmpty()) {
        return false;
      }

      return true;
    } catch (CalFacadeException cfe) {
      if (debug) {
        error(cfe);
      }

      return false;
    }
  }

  @Override
  public void rollback() {
    try {
      svci.rollbackTransaction();
    } catch (Throwable t) {
    }
  }

  @Override
  public void close() throws WebdavException {
    close(svci);
  }

  /* ====================================================================
   *                         Private methods
   * ==================================================================== */

  /**
   * @param sr
   * @return recipient results
   * @throws WebdavException
   */
  private Collection<SchedRecipientResult> checkStatus(final ScheduleResult sr) throws WebdavException {
    if ((sr.errorCode == null) ||
        (sr.errorCode == CalFacadeException.schedulingNoRecipients)) {
      Collection<SchedRecipientResult> srrs = new ArrayList<SchedRecipientResult>();

      for (ScheduleRecipientResult bwsrr: sr.recipientResults.values()) {
        SchedRecipientResult srr = new SchedRecipientResult();

        srr.recipient = bwsrr.recipient;
        srr.status = bwsrr.getStatus();

        if (bwsrr.freeBusy != null) {
          srr.freeBusy = new BwCalDAVEvent(this, new EventInfo(bwsrr.freeBusy));
        }

        srrs.add(srr);
      }

      return srrs;
    }

    if (sr.errorCode == CalFacadeException.schedulingBadMethod) {
      throw new WebdavForbidden(CaldavTags.validCalendarData, "Bad METHOD");
    }

    if (sr.errorCode == CalFacadeException.schedulingBadAttendees) {
      throw new WebdavForbidden(CaldavTags.attendeeAllowed, "Bad attendees");
    }

    if (sr.errorCode == CalFacadeException.schedulingAttendeeAccessDisallowed) {
      throw new WebdavForbidden(CaldavTags.attendeeAllowed, "attendeeAccessDisallowed");
    }

    throw new WebdavForbidden(sr.errorCode);
  }

  private BwCalendar unwrap(final CalDAVCollection col) throws WebdavException {
    if (!(col instanceof BwCalDAVCollection)) {
      throw new WebdavBadRequest("Unknown implemenation of BwCalDAVCollection" +
                                 col.getClass());
    }

    return ((BwCalDAVCollection)col).getCol();
  }

  private EventInfo getEvinfo(final CalDAVEvent ev) throws WebdavException {
    if (ev == null) {
      return null;
    }

    return ((BwCalDAVEvent)ev).getEvinfo();
  }

  private BwEvent getEvent(final CalDAVEvent ev) throws WebdavException {
    if (ev == null) {
      return null;
    }

    return ((BwCalDAVEvent)ev).getEv();
  }

  private BwResource getRsrc(final CalDAVResource rsrc) throws WebdavException {
    if (rsrc == null) {
      return null;
    }

    return ((BwCalDAVResource)rsrc).getRsrc();
  }

  /**
   * @return CalSvcI
   * @throws WebdavException
   */
  private CalSvcI getSvci() throws WebdavException {
    if (!svci.isOpen()) {
      try {
        svci.open();
        svci.beginTransaction();
      } catch (Throwable t) {
        throw new WebdavException(t);
      }
    }

    return svci;
  }

  private CalSvcI getSvci(final String account,
                          final String runAs,
                          final boolean service,
                          final String clientId) throws WebdavException {
    try {
      /* account is what we authenticated with.
       * user, if non-null, is the user calendar we want to access.
       */

      boolean possibleSuperUser = "root".equals(account) || // allow SuperUser
                                  "admin".equals(account);
      String runAsUser = null;
      String clientIdent = null;

      if (possibleSuperUser) {
        runAsUser = runAs;
        clientIdent = clientId;
      }

      CalSvcIPars pars = CalSvcIPars.getCaldavPars(account,
                                                   runAsUser,
                                                   clientIdent,
                                                   possibleSuperUser,   // allow SuperUser
                                         service);
      svci = new CalSvcFactoryDefault().getSvc(pars);

      svci.open();
      svci.beginTransaction();

      trans = new IcalTranslator(svci.getIcalCallback());
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    return svci;
  }

  private void close(CalSvcI svci) throws WebdavException {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();

      long reqTime = System.currentTimeMillis() - reqInTime;
      svci.postNotification(new HttpOutEvent(SysCode.CALDAV_OUT, reqTime));
    } catch (Throwable t) {
      try {
        svci.close();
      } catch (Throwable t1) {
      }
      svci = null;
      if (t instanceof CalFacadeStaleStateException) {
        throw new WebdavException(HttpServletResponse.SC_CONFLICT);
      }

      throw new WebdavException(t);
    }

    try {
      svci.close();
    } catch (Throwable t) {
      svci = null;
      throw new WebdavException(t);
    }
  }

  private RecurringRetrievalMode getRrm(final RetrievalMode rm) throws WebdavException {
    if (rm == null) {
      return new RecurringRetrievalMode(Rmode.overrides);
    }

    try {
      if (rm.getExpand() != null) {
        /* expand with time range */
        ExpandType ex = rm.getExpand();

        DateTime s = new DateTime(XcalUtil.getIcalFormatDateTime(
                ex.getStart()));
        DateTime e = new DateTime(XcalUtil.getIcalFormatDateTime(ex.getEnd()));
        return new RecurringRetrievalMode(Rmode.expanded,
                                          getBwDt(s),
                                          getBwDt(e));
      }

      if (rm.getLimitRecurrenceSet() != null) {
        /* Only return master event and overrides in range */
        LimitRecurrenceSetType l = rm.getLimitRecurrenceSet();

        DateTime s = new DateTime(XcalUtil.getIcalFormatDateTime(l.getStart()));
        DateTime e = new DateTime(XcalUtil.getIcalFormatDateTime(l.getEnd()));
        return new RecurringRetrievalMode(Rmode.overrides,
                                          getBwDt(s),
                                          getBwDt(e));
      }
    } catch (Throwable t) {
      throw new WebdavBadRequest(CaldavTags.validFilter, "Invalid time-range");
    }

    /* Return master + overrides */
    return new RecurringRetrievalMode(Rmode.overrides);
  }

  /**
   * @author douglm
   *
   */
  private static class MySysiIcalendar extends SysiIcalendar {
    private Icalendar ic;
    private BwSysIntfImpl sysi;

    private Iterator icIterator;

    private MySysiIcalendar(final BwSysIntfImpl sysi, final Icalendar ic) {
      this.sysi = sysi;
      this.ic = ic;
    }

    @Override
    public String getProdid() {
      return ic.getProdid();
    }

    @Override
    public String getVersion() {
      return ic.getVersion();
    }

    @Override
    public String getCalscale() {
      return ic.getCalscale();
    }

    @Override
    public String getMethod() {
      return ic.getMethod();
    }

    @Override
    public Collection<TimeZone> getTimeZones() {
      Collection<TimeZone> tzs = new ArrayList<TimeZone>();

      for (TimeZoneInfo tzi: ic.getTimeZones()) {
        tzs.add(tzi.tz);
      }

      return tzs;
    }

    @Override
    public Collection<Object> getComponents() {
      return ic.getComponents();
    }

    @Override
    public IcalComponentType getComponentType() {
      return ic.getComponentType();
    }

    @Override
    public int getMethodType() {
      return ic.getMethodType();
    }

    @Override
    public int getMethodType(final String val) {
      return Icalendar.getMethodType(val);
    }

    @Override
    public String getMethodName(final int mt) {
      return Icalendar.getMethodName(mt);
    }

    @Override
    public Organizer getOrganizer() {
      BwOrganizer bworg = ic.getOrganizer();

      if (bworg == null) {
        return null;
      }

      return new Organizer(bworg.getCn(), bworg.getDir(),
                           bworg.getLanguage(),
                           bworg.getSentBy(),
                           bworg.getOrganizerUri());
    }

    @Override
    public CalDAVEvent getEvent() throws WebdavException {
      //if ((size() != 1) || (getComponentType() != ComponentType.event)) {
      //  throw new RuntimeException("org.bedework.icalendar.component.not.event");
      //}

      return (CalDAVEvent)iterator().next();
    }

    @Override
    public Iterator<WdEntity> iterator() {
      return this;
    }

    @Override
    public int size() {
      return ic.size();
    }

    @Override
    public boolean validItipMethodType() {
      return validItipMethodType(getMethodType());
    }

    @Override
    public boolean requestMethodType() {
      return itipRequestMethodType(getMethodType());
    }

    @Override
    public boolean replyMethodType() {
      return itipReplyMethodType(getMethodType());
    }

    @Override
    public boolean itipRequestMethodType(final int mt) {
      return Icalendar.itipRequestMethodType(mt);
    }

    @Override
    public boolean itipReplyMethodType(final int mt) {
      return Icalendar.itipReplyMethodType(mt);
    }

    @Override
    public boolean validItipMethodType(final int val) {
      return Icalendar.validItipMethodType(val);
    }

    /* ====================================================================
     *                        Iterator methods
     * ==================================================================== */

    @Override
	public boolean hasNext() {
      return getIcIterator().hasNext();
    }

    @Override
	public WdEntity next() {
      Object o = getIcIterator().next();

      if (!(o instanceof EventInfo)) {
        return null;
      }

      EventInfo ei = (EventInfo)o;

      try {
        return new BwCalDAVEvent(sysi, ei);
      } catch (Throwable t) {
        throw new RuntimeException(t);
      }
    }

    @Override
	public void remove() {
      throw new UnsupportedOperationException();
    }

    private Iterator getIcIterator() {
      if (icIterator == null) {
        icIterator = ic.iterator();
      }

      return icIterator;
    }
  }

  private BwDateTime getBwDt(final DateTime dt) throws WebdavException {
    try {
      if (dt == null) {
        return null;
      }

      return BwDateTime.makeBwDateTime(false, dt.toString(), null);
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private BasicSystemProperties getSysPars() throws WebdavException {
    try {
      if (syspars == null) {
        syspars = getSvci().getBasicSystemProperties();
      }
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    return syspars;
  }

  private BwPreferences getPrefs() throws WebdavException {
    try {
      if (prefs == null) {
        prefs = getSvci().getPrefsHandler().get();
      }
    } catch (Throwable t) {
      throw new WebdavException(t);
    }

    return prefs;
  }

  /* ====================================================================
   *                        Protected methods
   * ==================================================================== */

  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void logIt(final String msg) {
    getLogger().info(msg);
  }
}
