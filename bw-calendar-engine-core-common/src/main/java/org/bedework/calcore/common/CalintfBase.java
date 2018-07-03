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
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.common.indexing.BwIndexerFactory;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.FiltersCommonI;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.NotificationsHandlerFactory;
import org.bedework.sysevents.events.SysEventBase;
import org.bedework.util.misc.Logged;
import org.bedework.util.misc.Util;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.LinkedTransferQueue;

/** Base Implementation of CalIntf which throws exceptions for most methods.
*
* @author Mike Douglass   douglm   rpi.edu
*/
public abstract class CalintfBase extends Logged implements Calintf {
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

  /** Ensure we don't open while open
   */
  protected boolean isOpen;

  protected final LinkedTransferQueue<SysEventBase> queuedNotifications = new LinkedTransferQueue<>();

  /** For evaluating access control
   */
  protected AccessUtil access;

  public CollectionCache colCache;

  public class CIAccessChecker implements AccessChecker {
    @Override
    public CurrentAccess checkAccess(final BwShareableDbentity ent,
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
    if (publicIndexer != null) {
      publicIndexer.close();
      publicIndexer = null;
    }

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
  public String getCalendarNameFromType(final int calType) throws CalFacadeException {
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

  private BwIndexer publicIndexer;
  private Map<String, BwIndexer> principalIndexers =
          new HashMap<>();

  public BwIndexer getIndexer() {
    if (currentMode == CalintfDefs.publicAdminMode ||
                              !authenticated) {
      return getPublicIndexer();
    }

    return getIndexer(getPrincipal());
  }

  @Override
  public BwIndexer getIndexer(final BwOwnedDbentity entity) {
    if ((currentMode == CalintfDefs.guestMode) ||
            (currentMode == CalintfDefs.publicUserMode) ||
            (currentMode == CalintfDefs.publicAdminMode)) {
      return getPublicIndexer();
    }

    if ((entity != null) && entity.getPublick()) {
      return getPublicIndexer();
    }

    return getIndexer(getPrincipal());
  }

  public BwIndexer getIndexer(final BwIndexer indexer) {
    if (indexer != null) {
      return indexer;
    }
    if ((currentMode == CalintfDefs.publicAdminMode) ||
            (currentMode == CalintfDefs.publicUserMode) ||
            !authenticated) {
      return getPublicIndexer();
    }

    return getIndexer(getPrincipal());
  }

  public BwIndexer getIndexer(final String principalHref) {
    if ((currentMode == CalintfDefs.publicAdminMode) ||
            (currentMode == CalintfDefs.publicUserMode) ||
            !authenticated) {
      return getPublicIndexer();
    }

    try {
      return getIndexer(getPrincipal(principalHref));
    } catch (final CalFacadeException cfe) {
      error(cfe);
      throw new RuntimeException(cfe);
    }
  }

  @Override
  public BwIndexer getPublicIndexer() {
    if (publicIndexer != null) {
      return publicIndexer;
    }
    publicIndexer = BwIndexerFactory.getPublicIndexer(configs,
                                                      currentMode,
                                                      ac);
    return publicIndexer;
  }

  @Override
  public BwIndexer getIndexer(final boolean publick) {
    if (publick) {
      return getPublicIndexer();
    }

    return getIndexer(getPrincipal());
  }

  @Override
  public BwIndexer getIndexer(final BwPrincipal principal) {
    if (BwPrincipal.publicUserHref.equals(principal.getPrincipalRef())) {
      return getPublicIndexer();
    }

    BwIndexer idx = principalIndexers.get(principal.getPrincipalRef());
    if (idx != null) {
      return idx;
    }
    idx = BwIndexerFactory.getIndexer(configs, principal,
                                      getSuperUser(),
                                      currentMode,
                                      ac);
    principalIndexers.put(principal.getPrincipalRef(), idx);
    return idx;
  }

  @Override
  public BwIndexer getIndexer(final BwPrincipal principal,
                              final String indexRoot) {
    return BwIndexerFactory.getIndexer(configs, principal,
                                       currentMode,
                                       ac,
                                       indexRoot);
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
   *                   Eventss
   * ==================================================================== */

  @Override
  public Collection<CoreEventInfo> postGetEvents(final Collection evs,
                                                 final int desiredAccess,
                                                 final boolean nullForNoAccess,
                                                 final FiltersCommonI f)
          throws CalFacadeException {
    final TreeSet<CoreEventInfo> outevs = new TreeSet<>();

    for (final Object ev1 : evs) {
      final BwEvent ev = (BwEvent)ev1;

      final CoreEventInfo cei = postGetEvent(ev, desiredAccess,
                                             nullForNoAccess, f);

      if (cei == null) {
        continue;
      }

      outevs.add(cei);
    }

    return outevs;
  }

  @Override
  public CoreEventInfo postGetEvent(final BwEvent ev,
                                    final FiltersCommonI f,
                                    final CurrentAccess ca) throws CalFacadeException {
    /* XXX-ALARM
    if (currentMode == userMode) {
      ev.setAlarms(getAlarms(ev, user));
    }
    */

    BwEvent event;

    if (ev instanceof BwEventAnnotation) {
      event = new BwEventProxy((BwEventAnnotation)ev);

      if ((f != null) && !f.postFilter(ev, getPrincipalRef())) {
        return null;
      }
    } else {
      event = ev;
    }

    return new CoreEventInfo(event, ca);
  }

  @Override
  public CoreEventInfo postGetEvent(final BwEvent ev,
                                    final int desiredAccess,
                                    final boolean nullForNoAccess,
                                    final FiltersCommonI f) throws CalFacadeException {
    if (ev == null) {
      return null;
    }

    final CurrentAccess ca = ac.checkAccess(ev,
                                                desiredAccess,
                                                nullForNoAccess);

    if (!ca.getAccessAllowed()) {
      return null;
    }

    return postGetEvent(ev, f, ca);
  }

  /* ====================================================================
   *                   Notifications
   * ==================================================================== */

  @Override
  public void postNotification(final SysEventBase ev) throws CalFacadeException {
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
  public void flushNotifications() throws CalFacadeException {
    try {
      while (!queuedNotifications.isEmpty()) {
        SysEventBase ev = null;
        try {
          ev = queuedNotifications.take();
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
    } finally {
      NotificationsHandlerFactory.close();
    }
  }

  public void kill() {
    killed = true;
  }

  @Override
  public void clearNotifications() throws CalFacadeException {
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

  /*
  protected void updated(BwUser user) {
    personalModified.add(user);
  }*/

}
