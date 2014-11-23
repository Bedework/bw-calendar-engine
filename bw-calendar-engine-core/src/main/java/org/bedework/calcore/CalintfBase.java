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
package org.bedework.calcore;

import org.bedework.calcore.indexing.BwIndexerFactory;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.sysevents.NotificationsHandlerFactory;
import org.bedework.sysevents.events.SysEventBase;

import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.concurrent.LinkedTransferQueue;

/** Base Implementation of CalIntf which throws exceptions for most methods.
*
* @author Mike Douglass   douglm   rpi.edu
*/
public abstract class CalintfBase implements Calintf {
  private Configurations configs;

  protected PrincipalInfo principalInfo;

  protected String url;

  protected boolean sessionless;

  protected boolean debug;

  protected boolean forRestore;

  protected boolean indexRebuild;

  /** When we were created for debugging */
  protected Timestamp objTimestamp;

  protected long objMillis;
  protected String objKey;

  protected String logId;

  /** User for whom we maintain this facade
   */
  //protected BwUser user;

  protected int currentMode = CalintfDefs.guestMode;

  /** Ensure we don't open while open
   */
  protected boolean isOpen;

  private transient Logger log;

  protected final LinkedTransferQueue<SysEventBase> queuedNotifications = new LinkedTransferQueue<>();

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
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public void init(final String logId,
                   final Configurations configs,
                   final PrincipalInfo PrincipalInfo,
                   final String url,
                   final boolean publicAdmin,
                   final boolean publicSubmission,
                   final boolean sessionless) throws CalFacadeException {
    this.logId = logId;
    this.configs = configs;
    this.principalInfo = PrincipalInfo;
    this.url = url;
    this.sessionless = sessionless;
    debug = getLogger().isDebugEnabled();

    if (principalInfo.getPrincipal().getUnauthenticated()) {
      currentMode = CalintfDefs.guestMode;
    } else if (publicAdmin) {
      currentMode = CalintfDefs.publicAdminMode;
    } else if (publicSubmission) {
      currentMode = CalintfDefs.publicUserMode;
    } else {
      currentMode = CalintfDefs.userMode;
    }
  }

  @Override
  public String getLogId() {
    return logId;
  }

  @Override
  public String getTraceId() {
    if (logId == null) {
      return String.valueOf(objTimestamp);
    }

    return logId + ": " + objTimestamp;
  }

  /**
   * @return PrincipalInfo
   * @throws CalFacadeException
   */
  public PrincipalInfo getPrincipalInfo() throws CalFacadeException {
    return principalInfo;
  }

  @Override
  public BasicSystemProperties getSyspars() throws CalFacadeException {
    return configs.getBasicSystemProperties();
  }

  @Override
  public BwIndexer getPublicIndexer() throws CalFacadeException {
    return BwIndexerFactory.getPublicIndexer(configs,
                                             currentMode);
  }

  @Override
  public BwIndexer getIndexer(final BwPrincipal principal) throws CalFacadeException {
    return BwIndexerFactory.getIndexer(configs, principal,
                                       getPrincipalInfo().getSuperUser(),
                                       currentMode);
  }

  @Override
  public BwIndexer getIndexer(final BwPrincipal principal,
                              final String indexRoot) throws CalFacadeException {
    return BwIndexerFactory.getIndexer(configs, principal,
                                       currentMode,
                                       indexRoot);
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
  }

  @Override
  public void clearNotifications() throws CalFacadeException {
    queuedNotifications.clear();
  }

  /**
   * @return BwPrincipal object for current principal
   * @throws CalFacadeException
   */
  public BwPrincipal getPrincipal() throws CalFacadeException {
    return principalInfo.getPrincipal();
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected void checkOpen() throws CalFacadeException {
    if (!isOpen) {
      throw new CalFacadeException("Calintf call when closed");
    }
  }

  /*
  protected void updated(BwUser user) {
    personalModified.add(user);
  }

  /** Get a logger for messages
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(getClass());
    }

    return log;
  }

  protected void error(final String msg) {
    getLogger().error(msg);
  }

  protected void warn(final String msg) {
    getLogger().warn(msg);
  }

  protected void error(final Throwable t) {
    getLogger().error(this, t);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }

  protected void debug(final String msg) {
    getLogger().debug(msg);
  }
}
