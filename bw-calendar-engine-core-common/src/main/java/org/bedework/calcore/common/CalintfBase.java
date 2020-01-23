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
package org.bedework.calcore.common;

import org.bedework.access.Ace;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.common.indexing.BwIndexerFactory;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexFetcher;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.BwIndexerParams;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.NotificationsHandlerFactory;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedTransferQueue;

import static org.bedework.calfacade.indexing.BwIndexer.docTypeCategory;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeContact;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeLocation;
import static org.bedework.util.misc.response.Response.Status.failed;

/** Base Implementation of CalIntf which throws exceptions for most methods.
*
* @author Mike Douglass   douglm   rpi.edu
*/
public abstract class CalintfBase implements Logged, Calintf {
  protected Configurations configs;

  protected PrincipalInfo principalInfo;

  protected boolean sessionless;

  protected boolean dontKill;

  protected boolean authenticated;

  protected boolean forRestore;

  protected boolean indexRebuild;

  protected boolean killed;

  /** When we were created for debugging */
  protected Timestamp objTimestamp;

  protected long objMillis;
  protected String objKey;

  /** last state change */
  protected Timestamp lastStateChange;

  protected String state;

  protected String logId;

  /** User for whom we maintain this facade
   */
  //protected BwUser user;

  protected int currentMode = CalintfDefs.guestMode;
  protected boolean readOnlyMode;

  /** Ensure we don't open while open
   */
  protected boolean isOpen;

  protected final LinkedTransferQueue<SysEventBase> queuedNotifications = new LinkedTransferQueue<>();

  /** For evaluating access control
   */
  protected AccessUtil access;

  public CollectionCache colCache;

  private BwIndexFetcher indexFetcher = new BwIndexFetcherImpl();

  public class CIAccessChecker implements AccessChecker {
    @Override
    public CurrentAccess checkAccess(final BwShareableDbentity<?> ent,
                                         final int desiredAccess,
                                         final boolean returnResult)
            throws CalFacadeException {
      return access.checkAccess(ent, desiredAccess, returnResult);
    }

    @Override
    public CalendarWrapper checkAccess(final BwCalendar val)
            throws CalFacadeException {
      return checkAccess(val, PrivilegeDefs.privAny);
    }

    @Override
    public CalendarWrapper checkAccess(final BwCalendar val,
                                       final int desiredAccess)
            throws CalFacadeException {
      if (val == null) {
        return null;
      }

      if (val instanceof CalendarWrapper) {
        // CALWRAPPER get this from getEvents with an internal temp calendar
        return (CalendarWrapper)val;
      }

      final CalendarWrapper cw =
              new CalendarWrapper(val,
                                  ac.getAccessUtil());
      final CurrentAccess ca =
              checkAccess(cw,
                          desiredAccess,
                          true);
      if (!ca.getAccessAllowed()) {
        return null;
      }

      return cw;
    }

    @Override
    public AccessUtilI getAccessUtil() {
      return access;
    }
  }

  protected AccessChecker ac;

  protected FilterParserFetcher filterParserFetcher;

  static class IndexEntry {
    final BwIndexer indexer;
    final BwUnversionedDbentity<?> entity;

    IndexEntry(final BwIndexer indexer,
               final BwUnversionedDbentity<?> entity) {
      this.indexer = indexer;
      this.entity = entity;
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
  public CalintfBase() {
    try {
      objMillis = System.currentTimeMillis();
      objTimestamp = new Timestamp(objMillis);
      setState("-init-");
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  public void closeIndexers() {
    if (!awaitingIndex.isEmpty()) {
      final var vals = awaitingIndex.values();
      final var sz = vals.size();
      var ct = 1;

      try {
        for (final IndexEntry ie : vals) {
          try {
            ie.indexer.indexEntity(ie.entity,
                                   ct == sz); // wait
            ct++;
          } catch (final CalFacadeException cfe) {
            if (debug()) {
              error(cfe);
            }
            throw new RuntimeException(cfe);
          }
        }
      } finally {
        awaitingIndex.clear();
      }
    }

    for (final BwIndexer idx: publicIndexers.values()) {
      idx.close();
    }

    publicIndexers.clear();

    for (final BwIndexer idx: principalIndexers.values()) {
      idx.close();
    }

    principalIndexers.clear();
  }

  @Override
  public void initPinfo(final PrincipalInfo principalInfo) throws CalFacadeException {
    this.principalInfo = principalInfo;

    try {
      access = new AccessUtil();
      access.init(principalInfo);
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public String getLogId() {
    return logId;
  }

  @Override
  public boolean getDontKill() {
    return dontKill;
  }

  @Override
  public String getTraceId() {
    if (logId == null) {
      return String.valueOf(objTimestamp);
    }

    return logId + ": " + objTimestamp;
  }

  @Override
  public String getLastStateTime() {
    return lastStateChange.toString();
  }

  @Override
  public void setState(final String val) {
    state = val;
    lastStateChange = new Timestamp(System.currentTimeMillis());
  }

  @Override
  public String getState() {
    return state;
  }

  @Override
  public PrincipalInfo getPrincipalInfo() {
    return principalInfo;
  }

  @Override
  public boolean getForRestore() {
    return forRestore;
  }

  @Override
  public BasicSystemProperties getSyspars() {
    return configs.getBasicSystemProperties();
  }

  @Override
  public String getCalendarNameFromType(final int calType) {
    final String name;
    final BasicSystemProperties sys = getSyspars();

    if (calType == BwCalendar.calTypeInbox) {
      name = sys.getUserInbox();
    } else if (calType == BwCalendar.calTypePendingInbox) {
      name = ".pendingInbox";// sys.getUserInbox();
    } else if (calType == BwCalendar.calTypeOutbox) {
      name = sys.getUserOutbox();
    } else if (calType == BwCalendar.calTypeNotifications) {
      name = sys.getDefaultNotificationsName();
    } else if (calType == BwCalendar.calTypeEventList) {
      name = sys.getDefaultReferencesName();
    } else if (calType == BwCalendar.calTypePoll) {
      name = sys.getUserDefaultPollsCalendar();
    } else if (calType == BwCalendar.calTypeAttachments) {
      name = sys.getDefaultAttachmentsName();
    } else if (calType == BwCalendar.calTypeCalendarCollection) {
      name = sys.getUserDefaultCalendar();
    } else if (calType == BwCalendar.calTypeTasks) {
      name = sys.getUserDefaultTasksCalendar();
    } else {
      // Not supported
      return null;
    }

    return name;
  }

  public CalendarWrapper wrap(final BwCalendar val) {
    if (val == null) {
      return null;
    }

    if (val instanceof CalendarWrapper) {
      // CALWRAPPER get this from getEvents with an internal temp calendar
      return (CalendarWrapper)val;
    }
    return new CalendarWrapper(val, ac.getAccessUtil());
  }

  public boolean getSuperUser() {
    if (principalInfo == null) {
      return false;
    }

    return principalInfo.getSuperUser();
  }

  private Map<String, BwIndexer> publicIndexers = new HashMap<>();
  private Map<String, BwIndexer> principalIndexers =
          new HashMap<>();

  public BwIndexer getIndexer(final String docType) {
    if (currentMode == CalintfDefs.publicAdminMode ||
            readOnlyMode) {
      return getPublicIndexer(docType);
    }

    return getIndexer(getPrincipalRef(), docType);
  }

  @Override
  public BwIndexer getIndexer(final Object entity) {
    final String docType = docTypeFromClass(entity);

    if (readOnlyMode ||
            (currentMode == CalintfDefs.publicUserMode) ||
            (currentMode == CalintfDefs.publicAdminMode)) {
      return getPublicIndexer(docType);
    }

    if ((entity instanceof BwOwnedDbentity) &&
            ((BwOwnedDbentity<?>)entity).getPublick()) {
      return getPublicIndexer(docType);
    }

    return getIndexer(getPrincipalRef(), docType);
  }

  @Override
  public void indexEntity(final BwUnversionedDbentity<?> entity) {
    indexEntity(getIndexer(entity), entity);
  }

  public void indexEntity(final BwIndexer indexer,
                          final BwUnversionedDbentity<?> entity) {
    //indexer.indexEntity(entity, wait);

    var ie = new IndexEntry(indexer, entity);
    awaitingIndex.put(entity.getHref(), ie);
  }

  private static Map<Class<?>, String> toDocType = new HashMap<>();

  static {
    toDocType.put(BwAdminGroup.class, BwIndexer.docTypePrincipal);
    toDocType.put(BwAuthUser.class, BwIndexer.docTypePrincipal);
    toDocType.put(BwCalendar.class, BwIndexer.docTypeCollection);
    toDocType.put(BwCalSuite.class, BwIndexer.docTypePrincipal);
    toDocType.put(BwCategory.class, docTypeCategory);
    toDocType.put(BwContact.class, BwIndexer.docTypeContact);
    toDocType.put(BwEvent.class, BwIndexer.docTypeEvent);
    toDocType.put(BwEventObj.class, BwIndexer.docTypeEvent);
    toDocType.put(BwFilterDef.class, BwIndexer.docTypeFilter);
    toDocType.put(BwGroup.class, BwIndexer.docTypePrincipal);
    toDocType.put(BwLocation.class, BwIndexer.docTypeLocation);
    toDocType.put(BwPreferences.class, BwIndexer.docTypePreferences);
    toDocType.put(BwPrincipal.class, BwIndexer.docTypePrincipal);
    toDocType.put(BwResource.class, BwIndexer.docTypeResource);
    toDocType.put(BwResourceContent.class, BwIndexer.docTypeResourceContent);
    toDocType.put(CalendarWrapper.class, BwIndexer.docTypeCollection);
    toDocType.put(EventInfo.class, BwIndexer.docTypeEvent);
  }

  public String docTypeFromClass(final Object entity) {
    final String docType = toDocType.get(entity.getClass());

    if (docType == null) {
      throw new RuntimeException("Unable to get docType for class " +
                                         entity.getClass());
    }

    return docType;
  }

  public BwIndexer getIndexer(final BwIndexer indexer,
                              final String docType) {
    if (indexer != null) {
      return indexer;
    }
    if ((currentMode == CalintfDefs.publicAdminMode) ||
            (currentMode == CalintfDefs.publicUserMode) ||
            readOnlyMode) {
      return getPublicIndexer(docType);
    }

    return getIndexer(getPrincipalRef(), docType);
  }

  public BwIndexer getIndexer(final String principalHref,
                              final String docType) {
    if ((currentMode == CalintfDefs.publicAdminMode) ||
            (currentMode == CalintfDefs.publicUserMode) ||
            readOnlyMode ||
            BwPrincipal.publicUserHref.equals(principalHref)) {
      return getPublicIndexer(docType);
    }

    BwIndexer idx = principalIndexers.get(docType + "\t" +
                                                  principalHref);
    if (idx != null) {
      return idx;
    }

    idx = BwIndexerFactory.getIndexer(configs,
                                      docType,
                                      principalHref,
                                      getSuperUser(),
                                      ac,
                                      indexFetcher);
    principalIndexers.put(docType + "\t" +
                                  principalHref, idx);
    return idx;
  }

  private class BwIndexFetcherImpl implements BwIndexFetcher {
    @Override
    public GetEntityResponse<BwCategory> fetchCategory(
            final BwIndexerParams params, final String href) {
      final GetEntityResponse<BwCategory> resp = new GetEntityResponse<>();

      try {
        final BwCategory ent = getIndexer(params,
                                          docTypeCategory)
                .fetchCat(href, PropertyIndex.PropertyInfoIndex.HREF);

        if (ent == null) {
          return Response.notOk(resp,
                                Response.Status.notFound);
        }

        resp.setEntity(ent);

        return Response.ok(resp, null);
      } catch (final Throwable t) {
        return Response.error(resp, t);
      }
    }

    @Override
    public GetEntityResponse<BwContact> fetchContact(
            final BwIndexerParams params, final String href) {
      final GetEntityResponse<BwContact> resp = new GetEntityResponse<>();

      try {
        final BwContact ent = getIndexer(params,
                                          docTypeContact)
                .fetchContact(href, PropertyIndex.PropertyInfoIndex.HREF);

        if (ent == null) {
          return Response.notOk(resp, Response.Status.notFound);
        }

        resp.setEntity(ent);

        return Response.ok(resp, null);
      } catch (final Throwable t) {
        return Response.error(resp, t);
      }
    }

    @Override
    public GetEntityResponse<BwLocation> fetchLocation(
            final BwIndexerParams params, final String href) {
      final GetEntityResponse<BwLocation> resp = new GetEntityResponse<>();

      try {
        final BwLocation ent = getIndexer(params,
                                          docTypeLocation)
                .fetchLocation(href, PropertyIndex.PropertyInfoIndex.HREF);

        if (ent == null) {
          return Response.notOk(resp, Response.Status.notFound);
        }

        resp.setEntity(ent);

        return Response.ok(resp, null);
      } catch (final Throwable t) {
        return Response.error(resp, t);
      }
    }
  }

  public BwIndexer getIndexer(final BwIndexerParams params,
                              final String docType) {
    if (params.publick) {
      return getPublicIndexer(docType);
    }

    return getIndexer(params.principalHref, docType);
  }

  @Override
  public BwIndexer getPublicIndexer(final String docType) {
    BwIndexer idx = publicIndexers.get(docType);
    if (idx == null) {
      idx = BwIndexerFactory.getPublicIndexer(configs,
                                              docType,
                                              ac,
                                              indexFetcher);
      publicIndexers.put(docType, idx);
    }

    return idx;
  }

  @Override
  public BwIndexer getIndexer(final boolean publick,
                              final String docType) {
    if (publick) {
      return getPublicIndexer(docType);
    }

    return getIndexer(getPrincipal().getPrincipalRef(), docType);
  }

  @Override
  public BwIndexer getIndexerForReindex(final String principalHref,
                                        final String docType,
                                        final String indexName) {
    return BwIndexerFactory.getIndexerForReindex(configs,
                                                 docType,
                                                 principalHref,
                                                 ac,
                                                 indexFetcher,
                                                 indexName);
  }

  protected BwIndexer getEvIndexer() {
    return getIndexer(BwIndexer.docTypeEvent);
  }

  protected BwIndexer getColIndexer() {
    return getIndexer(BwIndexer.docTypeCollection);
  }

  protected BwIndexer getColIndexer(final BwIndexer indexer) {
    return getIndexer(indexer, BwIndexer.docTypeCollection);
  }

  protected String makeHref(final BwPrincipal principal,
                            final String userRoot,
                            final String colPathElement,
                            final String dir,
                            final String namePart) {
    if (BwPrincipal.publicUserHref.equals(principal.getPrincipalRef())) {
      return Util.buildPath(true,
                            "/public",
                            "/",
                            colPathElement,
                            "/",
                            dir,
                            "/",
                            namePart);
    }

    final String homeDir;

    if (principal.getKind() == Ace.whoTypeUser) {
      homeDir = userRoot;
    } else {
      homeDir = Util.pathElement(1, principal.getPrincipalRef());
    }

    return Util.buildPath(true,
                          "/",
                          homeDir,
                          "/",
                          principal.getAccount(),
                          "/",
                          colPathElement,
                          "/",
                          dir,
                          "/",
                          namePart);
  }

  /* ====================================================================
   *                   Events
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> postGetEvents(final Collection<?> evs,
                                                 final int desiredAccess,
                                                 final boolean nullForNoAccess)
          throws CalFacadeException {
    final TreeSet<CoreEventInfo> outevs = new TreeSet<>();

    for (final Object ev1 : evs) {
      final BwEvent ev = (BwEvent)ev1;

      final CoreEventInfo cei = postGetEvent(ev, desiredAccess,
                                             nullForNoAccess);

      if (cei == null) {
        continue;
      }

      outevs.add(cei);
    }

    return outevs;
  }

  @Override
  public CoreEventInfo postGetEvent(final BwEvent ev,
                                    final CurrentAccess ca) {
    /* XXX-ALARM
    if (currentMode == userMode) {
      ev.setAlarms(getAlarms(ev, user));
    }
    */

    BwEvent event;

    if (ev instanceof BwEventAnnotation) {
      event = new BwEventProxy((BwEventAnnotation)ev);
    } else {
      event = ev;
    }

    return new CoreEventInfo(event, ca);
  }

  @Override
  public CoreEventInfo postGetEvent(final BwEvent ev,
                                    final int desiredAccess,
                                    final boolean nullForNoAccess) throws CalFacadeException {
    if (ev == null) {
      return null;
    }

    final CurrentAccess ca = ac.checkAccess(ev,
                                            desiredAccess,
                                            nullForNoAccess);

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return postGetEvent(ev, ca);
  }

  /* ====================================================================
   *                   Notifications
   * ==================================================================== */

  @Override
  public void postNotification(final SysEventBase ev) {
    if (!isOpen) {
      try {
        NotificationsHandlerFactory.post(ev);
      } catch (final Throwable t) {
        error("Unable to post system notification " + ev);
        error(t);
      }

      return;
    }

    queuedNotifications.add(ev);
  }

  @Override
  public void flushNotifications() {
    while (!queuedNotifications.isEmpty()) {
      SysEventBase ev = null;
      try {
        ev = queuedNotifications.take();
        if (debug() &&
                ev.getSysCode().equals(SysEventBase.SysCode.SCHEDULE_QUEUED)) {
          debug("Post scheduling message: " + ev);
        }
        NotificationsHandlerFactory.post(ev);
      } catch (final Throwable t) {
        /* This could be a real issue as we are currently relying on jms
             * messages to trigger the scheduling process.
             *
             * At this point there's not much we can do about it.
             */
        error("Unable to post system notification " + ev);
        error(t);
      }
    }
  }

  public void kill() {
    killed = true;
  }

  @Override
  public void clearNotifications() {
    queuedNotifications.clear();
  }

  /**
   * @return BwPrincipal object for current principal
   */
  public BwPrincipal getPrincipal() {
    if (principalInfo == null) {
      return null;
    }
    return principalInfo.getPrincipal();
  }

  /**
   * @return href for current principal
   */
  public String getPrincipalRef() {
    if (getPrincipal() == null) {
      return null;
    }
    return getPrincipal().getPrincipalRef();
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void checkOpen() throws CalFacadeException {
    if (!killed && !isOpen) {
      throw new CalFacadeException("Calintf call when closed");
    }
  }

  protected boolean checkOpen(final Response resp) {
    if (!killed && !isOpen) {
      resp.setStatus(failed);
      resp.setMessage("Calintf call when closed");
      return false;
    }

    return true;
  }

  /*
  protected void updated(BwUser user) {
    personalModified.add(user);
  }*/


  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
