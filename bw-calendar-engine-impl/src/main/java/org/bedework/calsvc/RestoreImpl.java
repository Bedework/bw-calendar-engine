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

import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.UpdateEventResult;
import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.RestoreIntf;

import java.util.Collection;

/** Allow the restore process to work.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
class RestoreImpl extends CalSvcDb implements RestoreIntf {
  private RestoreLogger log;

  private boolean debug;

  protected int currentMode = CalintfDefs.userMode;

  private boolean transactionStarted;

  private int curBatchSize;
  private int batchSize;

  RestoreImpl(final CalSvc svci) throws CalFacadeException {
    super(svci);
  }

  @Override
  public void setLogger(final RestoreLogger val) {
    log = val;
    debug = log.isDebugEnabled();
  }

  @Override
  public void setBatchSize(final int val) {
    batchSize = val;
  }


  @Override
  public void endTransactionNow() throws Throwable {
    if (transactionStarted) {
      getSvc().endTransaction();
      getSvc().close();
    }

    transactionStarted = false;
    curBatchSize = 0;
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#endTransaction()
   */
  @Override
  public void endTransaction() throws Throwable {
    if ((batchSize > 0) &&
        (curBatchSize < batchSize)) {
      return;
    }

    endTransactionNow();
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreSyspars(org.bedework.calfacade.BwSystem)
   */
  @Override
  public void restoreSyspars(final BwSystem o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restorePrincipal(final BwPrincipal o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } catch (Throwable t) {
      handleException(t, "Exception restoring user " + o);
    } finally {
      endTransaction();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreAdminGroup(org.bedework.calfacade.svc.BwAdminGroup)
   */
  @Override
  public void restoreAdminGroup(final BwAdminGroup o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);

      if (debug) {
        log.debug("Saved admin group " + o);
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public void addAdminGroupMember(final BwAdminGroup o,
                                  final BwPrincipal pr) throws Throwable {
    try {
      startTransaction();

      getCal().addMember(o, pr, true);
    } finally {
      endTransaction();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#getAdminGroup(java.lang.String)
   */
  @Override
  public BwAdminGroup getAdminGroup(final String account) throws Throwable {
    startTransaction();

    return (BwAdminGroup)getCal().findGroup(account, true);
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreAuthUser(org.bedework.calfacade.svc.BwAuthUser)
   */
  @Override
  public void restoreAuthUser(final BwAuthUser o) throws Throwable {
    try {
      startTransaction();

      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreEvent(org.bedework.calfacade.BwEvent)
   */
  @Override
  public void restoreEvent(final EventInfo ei) throws Throwable {
    try {
      startTransaction();

      BwEvent ev = ei.getEvent();
      BwEvent saveEv = ev;

      if (ev instanceof BwEventProxy) {
        BwEventProxy proxy = (BwEventProxy)ev;
        saveEv = proxy.getRef();
      }

      UpdateEventResult uer = getCal().addEvent(saveEv,
                                            ei.getOverrideProxies(),
                                            false, // scheduling
                                            false);

      if (!uer.addedUpdated) {
        throw new CalFacadeException(uer.errorCode);
      }
      if (uer.failedOverrides != null) {
        error("Following overrides failed for event ");
        error(ev.toString());

        for (BwEventProxy proxy: uer.failedOverrides) {
          error(proxy.toString());
        }
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public BwEvent getEvent(final BwPrincipal owner,
                          final String colPath,
                          final String recurrenceId,
                          final String uid) throws Throwable {
    startTransaction();
    Collection<CoreEventInfo> ceis = getCal().getEvent(colPath,
                                                   uid, recurrenceId,
                                                   false,
                                                   new RecurringRetrievalMode(Rmode.entityOnly));

    if (ceis.size() != 1) {
      error("Expected one event for {" + colPath + ", " +
            recurrenceId + ", " + uid + "} found " + ceis.size());
      return null;
    }

    BwEvent ev = ceis.iterator().next().getEvent();

    if (ev instanceof BwEventAnnotation) {
      ev = new BwEventProxy((BwEventAnnotation)ev);
    }

    return ev;
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreCategory(org.bedework.calfacade.BwCategory)
   */
  @Override
  public void restoreCategory(final BwCategory o) throws Throwable {
    try {
      startTransaction();

      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreCalSuite(final BwCalSuite o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }


  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#restoreLocation(org.bedework.calfacade.BwLocation)
   */
  @Override
  public void restoreLocation(final BwLocation o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreContact(final BwContact o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreFilter(final BwFilterDef o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreResource(final BwResource o) throws Throwable {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().saveOrUpdate(o);

      BwResourceContent rc = o.getContent();

      rc.markUnsaved();
      getCal().saveOrUpdate(rc);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreUserPrefs(final BwPreferences o) throws Throwable {
    try {
      startTransaction();

      /* If the indexer or some other activity is running this can result in
       * a preferences object being created. See if one exists.
       */

      BwPreferences p = getSvc().getPreferences(o.getOwnerHref());

      if (p != null) {
        warn("Found instance of preferences for " + o.getOwnerHref());
        o.setId(p.getId());
        p = (BwPreferences)getSvc().merge(o);
      } else {
        p = o;

        /* Ensure views are unsaved objects */
        Collection<BwView> v = p.getViews();
        if (v != null) {
          for (BwView view: v) {
            view.markUnsaved();
          }
        }

        p.markUnsaved();
      }

      getCal().saveOrUpdate(o);
    } finally {
      endTransaction();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#getCalendar(java.lang.String)
   */
  @Override
  public BwCalendar getCalendar(final String path) throws Throwable {
    startTransaction();

    return getCols().get(path);
  }

  @Override
  public BwCategory getCategory(final String uid) throws Throwable {
    startTransaction();

    return getSvc().getCategoriesHandler().get(uid);
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#getContact(java.lang.String)
   */
  @Override
  public BwContact getContact(final String uid) throws Throwable {
    startTransaction();

    return getSvc().getContactsHandler().get(uid);
  }

  @Override
  public BwLocation getLocation(final String uid) throws Throwable {
    startTransaction();

    return getSvc().getLocationsHandler().get(uid);
  }

  @Override
  public BwPrincipal getPrincipal(final String href) throws CalFacadeException {
    startTransaction();

    return getSvc().getUsersHandler().getPrincipal(href);
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#saveRootCalendar(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public void saveRootCalendar(final BwCalendar val) throws Throwable {
    // Ensure id not set
    val.markUnsaved();

    try {
      startTransaction();

      getCal().saveOrUpdate(val);
    } finally {
      endTransaction();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.dumprestore.restore.RestoreIntf#addCalendar(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public void addCalendar(final BwCalendar o) throws Throwable {
    // Ensure id not set
    o.markUnsaved();

    try {
      startTransaction();

      getCal().saveOrUpdate(o);
      curBatchSize++;
    } finally {
      endTransaction();
    }
  }

  /* ====================================================================
   *                       Private methods
   * ==================================================================== */

  private void startTransaction() throws CalFacadeException {
    if (transactionStarted) {
      return;
    }

    getSvc().open();
    getSvc().beginTransaction();
    transactionStarted = true;
  }

  private void handleException(final Throwable t, final String msg) {
    if (log == null) {
      return;
    }
    log.error(msg, t);
  }

  protected void info(final String msg) {
    if (log == null) {
      return;
    }
    log.info(msg);
  }

  @Override
  protected void warn(final String msg) {
    if (log == null) {
      return;
    }
    log.warn(msg);
  }

  @Override
  protected void error(final String msg) {
    if (log == null) {
      return;
    }
    log.error(msg);
  }
}
