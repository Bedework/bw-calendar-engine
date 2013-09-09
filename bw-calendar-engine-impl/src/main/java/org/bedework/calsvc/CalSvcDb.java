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

import org.bedework.calcorei.Calintf;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.UsersI;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.misc.Uid;
import org.bedework.util.security.PwEncryptionIntf;

import org.bedework.access.Acl.CurrentAccess;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** This acts as an interface to the database for more client oriented
 * bedework objects. CalIntf is a more general calendar specific interface.
 *
 * @author Mike Douglass       douglm - bedework.edu
 */
public class CalSvcDb implements Serializable {
  protected boolean debug;

  private CalSvc svci;

  private CalSvcIPars pars;

  private transient Logger log;

  /**
   * @param svci
   */
  public CalSvcDb(final CalSvc svci) {
    setSvc(svci);
    debug = getLogger().isDebugEnabled();
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

  void touchCalendar(final BwCalendar col) throws CalFacadeException {
    getSvc().touchCalendar(col);
  }

  protected Timestamp getCurrentTimestamp() throws CalFacadeException {
    return getSvc().getCurrentTimestamp();
  }

  protected BwPrincipal caladdrToPrincipal(final String href) throws CalFacadeException {
    return getSvc().getDirectories().caladdrToPrincipal(href);
  }

  protected String principalToCaladdr(final BwPrincipal p) throws CalFacadeException {
    return getSvc().getDirectories().principalToCaladdr(p);
  }

  protected void pushPrincipal(final String href) throws CalFacadeException {
    BwPrincipal pr = getSvc().getDirectories().caladdrToPrincipal(href);

    if (pr == null) {
      throw new CalFacadeException(CalFacadeException.badRequest,
                                   "unknown principal");
    }

    getSvc().pushPrincipal(pr);
  }

  protected void pushPrincipal(final BwPrincipal pr) throws CalFacadeException {
    getSvc().pushPrincipal(pr);
  }

  protected void popPrincipal() throws CalFacadeException {
    getSvc().popPrincipal();
  }

  /** Do NOT expose this via a public interface.
   * @return encrypter
   * @throws CalFacadeException
   */
  protected PwEncryptionIntf getEncrypter() throws CalFacadeException {
    return getSvc().getEncrypter();
  }

  protected List<BwCalendar> findAlias(final String val) throws CalFacadeException {
    return ((Calendars)getCols()).findUserAlias(val);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param colPath
   * @param guid
   * @param recurrenceId
   * @param scheduling
   * @param recurRetrieval
   * @return Collection<EventInfo> - collection as there may be more than
   *                one with this uid in the inbox.
   * @throws CalFacadeException
   */
  protected Collection<EventInfo> getEvents(final String colPath,
                                            final String guid, final String recurrenceId,
                                            final boolean scheduling,
                                            final RecurringRetrievalMode recurRetrieval)
                            throws CalFacadeException {
    Events events = (Events)getSvc().getEventsHandler();

    return events.get(colPath, guid, recurrenceId, scheduling,
                      recurRetrieval);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param ei
   * @param scheduling
   * @param sendSchedulingReply
   * @return boolean
   * @throws CalFacadeException
   */
  protected boolean deleteEvent(final EventInfo ei,
                                final boolean scheduling,
                                final boolean sendSchedulingReply) throws CalFacadeException {
    Events events = (Events)getSvc().getEventsHandler();

    return events.delete(ei, scheduling, sendSchedulingReply);
  }

  protected BwCalendar getSpecialCalendar(final BwPrincipal owner,
                                          final int calType,
                                          final boolean create,
                                          final int access) throws CalFacadeException {
    return ((Calendars)getCols()).getSpecial(owner, calType, create, access);
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  /** Called to notify container that an event occurred. This method should
   * queue up notifications until after transaction commit as consumers
   * should only receive notifications when the actual data has been written.
   *
   * @param ev
   * @throws CalFacadeException
   */
  public void postNotification(final SysEvent ev) throws CalFacadeException {
    getSvc().postNotification(ev);
  }

  /** Method which allows us to flag it as a scheduling action
   *
   * @param cals
   * @param filter
   * @param startDate
   * @param endDate
   * @param retrieveList List of properties to retrieve or null for a full event.
   * @param recurRetrieval
   * @param freeBusy
   * @return Collection of matching events
   * @throws CalFacadeException
   */
  protected Collection<EventInfo> getEvents(final Collection<BwCalendar> cals,
                                            final FilterBase filter,
                                            final BwDateTime startDate, final BwDateTime endDate,
                                            final List<String> retrieveList,
                                            final RecurringRetrievalMode recurRetrieval,
                                            final boolean freeBusy) throws CalFacadeException {
   Events events = (Events)getSvc().getEventsHandler();

   return events.getMatching(cals, filter, startDate, endDate,
                             retrieveList,
                             recurRetrieval, freeBusy);
 }

  /** Result of calling getCollectionAndName with a path */
  protected static class CollectionAndName {
    /** The containing collection */
    public BwCalendar coll;

    /** Name of object */
    public String name;
  }

  protected CollectionAndName getCollectionAndName(final String path) throws CalFacadeException {
    int end;

    if (path.endsWith("/")) {
      end = path.length() - 1;
    } else {
      end = path.length();
    }

    int pos = path.substring(0, end).lastIndexOf("/");
    if (pos < 0) {
      throw new CalFacadeException(CalFacadeException.badRequest);
    }

    CollectionAndName res = new CollectionAndName();

    res.name = path.substring(pos + 1, end);
    if (pos == 0) {
      // Root
      res.coll = null;
    } else {
      res.coll = getCols().get(path.substring(0, pos + 1));
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
  protected boolean isSuper() throws CalFacadeException {
    return pars.getPublicAdmin() && svci.getSuperUser();
  }

  /* See if current authorised is a guest.
   */
  protected boolean isGuest() throws CalFacadeException {
    return pars.isGuest();
  }

  /* See if in public admin mode
   */
  protected boolean isPublicAdmin() throws CalFacadeException {
    return pars.getPublicAdmin();
  }

  protected BwPrincipal getPrincipal() throws CalFacadeException {
    return svci.getPrincipal();
  }

  protected String getPrincipalHref() throws CalFacadeException {
    return svci.getPrincipal().getPrincipalRef();
  }

  protected String getOwnerHref() throws CalFacadeException {
    if (getSvc().getPars().getPublicAdmin() ||
            getPrincipal().getUnauthenticated()) {
      return getUsers().getPublicUser().getPrincipalRef();
    }

    return getPrincipal().getPrincipalRef();
  }

  protected BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    return svci.getUsersHandler().getPrincipal(href);
  }

  protected PrincipalInfo getPrincipalInfo() throws CalFacadeException {
    return svci.getPrincipalInfo();
  }

  /**
   * @param svci
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

  protected CalendarsI getCols() throws CalFacadeException {
    return svci.getCalendarsHandler();
  }

  protected NotificationsI getNotes() throws CalFacadeException {
    return svci.getNotificationsHandler();
  }

  protected ResourcesI getRess() throws CalFacadeException {
    return svci.getResourcesHandler();
  }

  protected UsersI getUsers() throws CalFacadeException {
    return svci.getUsersHandler();
  }

  protected Calintf getCal() throws CalFacadeException {
    return svci.getCal();
  }

  protected Calintf getCal(final BwCalendar cal) throws CalFacadeException {
    return svci.getCal(cal);
  }

  protected BwPrincipal getPublicUser() throws CalFacadeException {
    return getSvc().getUsersHandler().getPublicUser();
  }

  protected CurrentAccess checkAccess(final BwShareableDbentity ent, final int desiredAccess,
                                    final boolean returnResult) throws CalFacadeException {
    return svci.checkAccess(ent, desiredAccess, returnResult);
  }

  protected void trace(final String msg) {
    getLogger().debug("trace: " + msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  /** Assign a guid to an event. A noop if this event already has a guid.
   *
   * @param val      BwEvent object
   * @throws CalFacadeException
   */
  protected void assignGuid(final BwEvent val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    if ((val.getName() != null) &&
        (val.getUid() != null)) {
      return;
    }

    String guidPrefix = "CAL-" + Uid.getUid();

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

    BwOwnedDbentity ent = (BwOwnedDbentity)o;

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
   * @param entity
   * @param ownerHref - new owner
   * @throws CalFacadeException
   */
  protected void setupSharableEntity(final BwShareableDbentity entity,
                                     final String ownerHref)
          throws CalFacadeException {
    if (entity.getCreatorHref() == null) {
      entity.setCreatorHref(ownerHref);
    }

    setupOwnedEntity(entity, ownerHref);
  }

  /** Set the owner and publick on an owned entity.
   *
   * @param entity
   * @param ownerHref - new owner
   * @throws CalFacadeException
   */
  protected void setupOwnedEntity(final BwOwnedDbentity entity,
                                  final String ownerHref)
          throws CalFacadeException {
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
   * @throws CalFacadeException
   */
  protected BwPrincipal getEntityOwner(final BwPrincipal owner) throws CalFacadeException {
    if (isPublicAdmin()) {
      return getPublicUser();
    }

    return owner;
  }

  protected BasicSystemProperties getBasicSyspars() throws CalFacadeException {
    return getSvc().getBasicSystemProperties();
  }

  protected AuthProperties getAuthpars() throws CalFacadeException {
    return getSvc().getAuthProperties();
  }

  protected SystemProperties getSyspars() throws CalFacadeException {
    return getSvc().getSystemProperties();
  }

  protected BwCalendar unwrap(final BwCalendar val) throws CalFacadeException {
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

  /** Generate a where clause term for a query which handles the
   * public/not public and creator.
   *
   * @param sb        StringBuilder for result
   * @param entName   String name of the entity whose values we are matching
   * @param publicEvents
   * @param ignoreCreator  true if we can ignore the creator (owner)
   * @return boolean  true if we need to set the :user term
   * @throws CalFacadeException
   */
  static boolean appendPublicOrOwnerTerm(final StringBuilder sb, final String entName,
                                         final boolean publicEvents, final boolean ignoreCreator)
            throws CalFacadeException {
    //boolean all = (currentMode == guestMode) || ignoreCreator;
    boolean all = publicEvents || ignoreCreator;
    boolean setUser = false;

    sb.append("(");
    if (!all) {
      sb.append("(");
    }

    sb.append(entName);
    sb.append(".publick=");
    sb.append(String.valueOf(publicEvents));

    if (!all) {
      sb.append(") and (");
      sb.append(entName);
      sb.append(".owner=:user");
      sb.append(")");
      setUser = true;
    }
    sb.append(")");

    return setUser;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* Get a logger for messages
   */
  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }
}

