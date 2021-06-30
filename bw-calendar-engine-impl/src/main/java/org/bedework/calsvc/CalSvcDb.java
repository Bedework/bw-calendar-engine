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

import org.bedework.access.CurrentAccess;
import org.bedework.calcorei.Calintf;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwPrincipalInfo;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calfacade.svc.CalSvcIPars;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.UsersI;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Uid;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.Response;
import org.bedework.util.security.PwEncryptionIntf;

import org.apache.commons.codec.binary.Base64;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.bedework.calfacade.indexing.BwIndexer.DeletedState.noDeleted;

/** This acts as an interface to the database for more client oriented
 * bedework objects. CalIntf is a more general calendar specific interface.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class CalSvcDb implements Logged, Serializable {
  private CalSvc svci;

  private CalSvcIPars pars;

  /**
   * @param svci
   */
  public CalSvcDb(final CalSvc svci) {
    setSvc(svci);
  }

  /** Call at svci open
   *
   */
  public void open() {
  }

  /** Call at svci close
   *
   */
  public void close() {
  }

  /** TODO - should probably use the db timestamp - not the system time.
   *
   * @return an encoded value for use as a unique uuid.
   */
  public static String getEncodedUuid() {
    return new String(Base64.encodeBase64(UUID.randomUUID().toString().getBytes()));
  }

  /* ====================================================================
   *                   Protected methods.
   * ==================================================================== */

  void touchCalendar(final String href) throws CalFacadeException {
    getSvc().touchCalendar(href);
  }

  void touchCalendar(final BwCalendar col) throws CalFacadeException {
    getSvc().touchCalendar(col);
  }

  protected Timestamp getCurrentTimestamp() {
    return getSvc().getCurrentTimestamp();
  }

  protected BwPrincipal caladdrToPrincipal(final String href) {
    return getSvc().getDirectories().caladdrToPrincipal(href);
  }

  protected String principalToCaladdr(final BwPrincipal p) {
    return getSvc().getDirectories().principalToCaladdr(p);
  }

  protected void pushPrincipal(final String href) throws CalFacadeException {
    final BwPrincipal pr = caladdrToPrincipal(href);

    if (pr == null) {
      throw new CalFacadeException(CalFacadeException.badRequest,
                                   "unknown principal");
    }

    getSvc().pushPrincipal(pr);
  }

  protected boolean pushPrincipalReturn(final String href) throws CalFacadeException {
    final BwPrincipal pr = caladdrToPrincipal(href);

    if (pr == null) {
      return false;
    }

    getSvc().pushPrincipal(pr);
    return true;
  }

  protected void pushPrincipal(final BwPrincipal pr) throws CalFacadeException {
    getSvc().pushPrincipal(pr);
  }

  protected void popPrincipal() throws CalFacadeException {
    getSvc().popPrincipal();
  }

  /** Do NOT expose this via a public interface.
   * @return encrypter
   */
  protected PwEncryptionIntf getEncrypter() {
    return getSvc().getEncrypter();
  }

  protected List<BwCalendar> findAlias(final String val) throws CalFacadeException {
    return ((Calendars)getCols()).findUserAlias(val);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param colPath path for collection
   * @param guid uid of event(s)
   * @return response with status and Collection<EventInfo> -
   *                collection as there may be more than
   *                one with this uid in the inbox.
   */
  protected GetEntitiesResponse<EventInfo> getEventsByUid(final String colPath,
                                                          final String guid) {
    final Events events = (Events)getSvc().getEventsHandler();
    final GetEntitiesResponse<EventInfo> resp = new GetEntitiesResponse<>();

    try {
      final var ents = events.getByUid(colPath, guid, null,
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

  protected BwCalendar getSpecialCalendar(final BwPrincipal owner,
                                          final int calType,
                                          final boolean create,
                                          final int access) throws CalFacadeException {
    return getCols().getSpecial(owner, calType, create, access);
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev the event
   */
  public void postNotification(final SysEvent ev) {
    getSvc().postNotification(ev);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param cols collections
   * @param filter a filter
   * @param startDate start
   * @param endDate end
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @param recurRetrieval expanded etc
   * @param freeBusy is this for freebusy
   * @return Collection of matching events
   * @throws CalFacadeException
   */
  protected Collection<EventInfo> getEvents(
          final Collection<BwCalendar> cols,
          final FilterBase filter,
          final BwDateTime startDate, final BwDateTime endDate,
          final List<BwIcalPropertyInfoEntry> retrieveList,
          final RecurringRetrievalMode recurRetrieval,
          final boolean freeBusy) throws CalFacadeException {
    final Events events = (Events)getSvc().getEventsHandler();

    return events.getMatching(cols, filter, startDate, endDate,
                              retrieveList, noDeleted,
                              recurRetrieval, freeBusy);
  }
  
  protected EventInfo getEvent(final BwCalendar col,
                               final String name,
                               final String recurrenceId)
          throws CalFacadeException {
    return getSvc().getEventsHandler().get(col, name, 
                                           recurrenceId,
                                           null);
  }

  /** Result of calling getCollectionAndName with a path */
  protected static class CollectionAndName {
    /** The containing collection */
    public BwCalendar coll;

    /** Name of object */
    public String name;
  }

  protected CollectionAndName getCollectionAndName(final String path) throws CalFacadeException {
    final int end;

    if (path.endsWith("/")) {
      end = path.length() - 1;
    } else {
      end = path.length();
    }

    final int pos = path.substring(0, end).lastIndexOf("/");
    if (pos < 0) {
      throw new CalFacadeException(CalFacadeException.badRequest);
    }

    final CollectionAndName res = new CollectionAndName();

    res.name = path.substring(pos + 1, end);
    if (pos == 0) {
      // Root
      res.coll = null;
    } else {
      res.coll = getCols().get(path.substring(0, pos));
      if (res.coll == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound);
      }
    }

    return res;
  }

  /* Get current parameters
   */
  protected CalSvcIPars getPars() {
    return pars;
  }

  /* See if current authorised user has super user access.
   */
  protected boolean isSuper() {
    return pars.getPublicAdmin() && svci.getSuperUser();
  }

  /* See if current authorised is a guest.
   */
  protected boolean isGuest() {
    return pars.isGuest();
  }

  /* See if in public admin mode
   */
  protected boolean isPublicAdmin() {
    return pars.getPublicAdmin();
  }

  /* See if is public authenticated calendar
   */
  protected boolean isPublicAuth() {
    return pars.getPublicAuth();
  }

  protected BwPrincipal getPrincipal() {
    return svci.getPrincipal();
  }

  protected String getPrincipalHref() {
    return svci.getPrincipal().getPrincipalRef();
  }

  protected String getOwnerHref() {
    if (getSvc().getPars().getPublicAdmin() ||
            getPrincipal().getUnauthenticated()) {
      return getUsers().getPublicUser().getPrincipalRef();
    }

    return getPrincipal().getPrincipalRef();
  }

  public BwIndexer getIndexer(final String docType) {
    return svci.getIndexer(docType);
  }

  public BwIndexer getIndexer(final boolean publick,
                              final String docType) {
    return svci.getIndexer(publick, docType);
  }

  protected BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return svci.getUsersHandler().getPrincipal(href);
  }

  protected PrincipalInfo getPrincipalInfo() {
    return svci.getPrincipalInfo();
  }

  protected BwPrincipalInfo getBwPrincipalInfo() throws CalFacadeException {
    return svci.getDirectories().getDirInfo(getPrincipal());
  }

  /**
   * @param svci service interface
   */
  public void setSvc(final CalSvcI svci) {
    this.svci = (CalSvc)svci;

    if (svci != null) {
      pars = this.svci.getPars();
    }
  }

  protected CalSvc getSvc() {
    return svci;
  }

  protected CalendarsI getCols() {
    return svci.getCalendarsHandler();
  }

  protected NotificationsI getNotes() {
    return svci.getNotificationsHandler();
  }

  protected ResourcesI getRess() {
    return svci.getResourcesHandler();
  }

  protected BwPreferences getPrefs() {
    return svci.getPrefsHandler().get();
  }

  protected BwPreferences getPrefs(final BwPrincipal principal) {
    return svci.getPrefsHandler().get(principal);
  }

  protected void update(final BwPreferences prefs) {
    svci.getPrefsHandler().update(prefs);
  }

  protected UsersI getUsers() {
    return svci.getUsersHandler();
  }

  protected Calintf getCal() {
    try {
      return svci.getCal();
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  protected Calintf getCal(final BwCalendar cal) {
    return svci.getCal(cal);
  }

  protected BwPrincipal getPublicUser() {
    return getSvc().getUsersHandler().getPublicUser();
  }

  protected CurrentAccess checkAccess(
          final BwShareableDbentity<?> ent, final int desiredAccess,
          final boolean returnResult) throws CalFacadeException {
    return svci.checkAccess(ent, desiredAccess, returnResult);
  }

  /** Assign a guid to an event. A noop if this event already has a guid.
   *
   * @param val      BwEvent object
   */
  protected void assignGuid(final BwEvent val) {
    if (val == null) {
      return;
    }

    if ((val.getName() != null) &&
        (val.getUid() != null)) {
      return;
    }

    final String guidPrefix = "CAL-" + Uid.getUid();

    if (val.getName() == null) {
      val.setName(guidPrefix + ".ics");
    }

    if (val.getUid() != null) {
      return;
    }

    val.setUid(guidPrefix + getSvc().getSystemProperties().getSystemid());
  }

  /* This checks to see if the current user has owner access based on the
   * supplied object. This is used to limit access to objects not normally
   * shared such as preferences and related objects like views and subscriptions.
   */
  protected void checkOwnerOrSuper(final Object o) throws CalFacadeException {
    if (isGuest()) {
      throw new CalFacadeAccessException();
    }

    if (isSuper()) {
      // Always ok?
      return;
    }

    if (!(o instanceof BwOwnedDbentity)) {
      throw new CalFacadeAccessException();
    }

    final BwOwnedDbentity<?> ent = (BwOwnedDbentity<?>)o;

    /*if (!isPublicAdmin()) {
      // Expect a different owner - always public-user????
      return;
    }*/

    if (getPrincipal().getPrincipalRef().equals(ent.getOwnerHref())) {
      return;
    }

    throw new CalFacadeAccessException();
  }

  /** Set the owner and creator on a shareable entity.
   *
   * @param entity shareable entity
   * @param ownerHref - new owner
   */
  protected void setupSharableEntity(final BwShareableDbentity<?> entity,
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
  protected void setupOwnedEntity(final BwOwnedDbentity<?> entity,
                                  final String ownerHref) {
    entity.setPublick(isPublicAdmin());

    if (entity.getOwnerHref() == null) {
      if (entity.getPublick()) {
        entity.setOwnerHref(getPublicUser().getPrincipalRef());
      } else {
        entity.setOwnerHref(ownerHref);
      }
    }
  }

  /** Return owner for entities
   *
   * @param owner - possible owner
   * @return BwPrincipal
   */
  protected BwPrincipal getEntityOwner(final BwPrincipal owner) {
    if (isPublicAdmin() || isGuest()) {
      return getPublicUser();
    }

    return owner;
  }

  protected AuthProperties getAuthpars() {
    return getSvc().getAuthProperties();
  }

  protected SystemProperties getSyspars() {
    return getSvc().getSystemProperties();
  }

  protected BwCalendar unwrap(final BwCalendar val) {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      // We get these at the moment - getEvents at svci level
      return val;
      // CALWRAPPER throw new CalFacadeException("org.bedework.not.wrapped");
    }

    return ((CalendarWrapper)val).fetchEntity();
  }

  public static class SplitResult {
    String path;
    String name;

    SplitResult(final String path, final String name) {
      this.path = path;
      this.name = name;
    }
  }

  /* Split the uri so that result.path is the path up to the name part result.name
   *
   */
  public SplitResult splitUri(final String uri) throws CalFacadeException {
    int end = uri.length();
    if (uri.endsWith("/")) {
      end--;
    }

    final int pos = uri.lastIndexOf("/", end);
    if (pos < 0) {
      // bad uri
      throw new CalFacadeException("Invalid uri: " + uri);
    }

    if (pos == 0) {
      return new SplitResult(uri, null);
    }

    return new SplitResult(uri.substring(0, pos), uri.substring(pos + 1, end));
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

