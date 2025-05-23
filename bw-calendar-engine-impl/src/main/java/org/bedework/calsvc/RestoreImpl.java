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

import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcorei.CalintfDefs;
import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.UpdateEventResult;
import org.bedework.caldav.util.sharing.AccessType;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.RestoreIntf;
import org.bedework.util.logging.Logged;
import org.bedework.util.xml.tagdefs.AppleServerTags;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/** Allow the restore process to work.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
class RestoreImpl extends CalSvcDb implements RestoreIntf {
  private Logged log;

  protected int currentMode = CalintfDefs.userMode;

  private boolean transactionStarted;

  private int curBatchSize;
  private int batchSize;

  RestoreImpl(final CalSvc svci) {
    super(svci);
  }

  @Override
  public void setLogger(final Logged val) {
    log = val;
  }

  @Override
  public void setBatchSize(final int val) {
    batchSize = val;
  }


  @Override
  public void endTransactionNow() {
    if (transactionStarted) {
      getSvc().endTransaction();
      getSvc().close();
    }

    transactionStarted = false;
    curBatchSize = 0;
  }

  @Override
  public void endTransaction() {
    if ((batchSize > 0) &&
        (curBatchSize < batchSize)) {
      return;
    }

    endTransactionNow();
  }

  @Override
  public void checkEmptySystem() {
    try {
      startTransaction();

      if (getSvc().getSysparsHandler().present()) {
        throw new BedeworkException("System is not empty - restore terminated");
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreSyspars(final BwSystem o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restorePrincipal(final BwPrincipal<?> o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);
    } catch (final Throwable t) {
      handleException(t, "Exception restoring user " + o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreAdminGroup(final BwAdminGroup o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);

      if (debug()) {
        log.debug("Saved admin group " + o);
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public void addAdminGroupMember(final BwAdminGroup o,
                                  final BwPrincipal<?> pr) {
    try {
      startTransaction();

      getCal().addMember(o, pr, true);
    } finally {
      endTransaction();
    }
  }

  @Override
  public BwAdminGroup getAdminGroup(final String account) {
    startTransaction();

    return (BwAdminGroup)getCal().findGroup(account, true);
  }

  @Override
  public void restoreAuthUser(final BwAuthUser o) {
    try {
      startTransaction();

      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreEvent(final EventInfo ei) {
    try {
      startTransaction();

      final UpdateEventResult uer = getCal().addEvent(ei,
                                            false, // scheduling
                                            false);

      if (!uer.addedUpdated) {
        throw new BedeworkException(uer.errorCode);
      }
      if (uer.failedOverrides != null) {
        error("Following overrides failed for event ");
        error(ei.getEvent().toString());

        for (final BwEventProxy proxy: uer.failedOverrides) {
          error(proxy.toString());
        }
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public BwEvent getEvent(final BwPrincipal<?> owner,
                          final String colPath,
                          final String recurrenceId,
                          final String uid) {
    startTransaction();
    final Collection<CoreEventInfo> ceis = getCal().getEvent(colPath,
                                                             uid);

    if (ceis.size() != 1) {
      error("Expected one event for {" + colPath + ", " +
            recurrenceId + ", " + uid + "} found " + ceis.size());
      return null;
    }

    final CoreEventInfo ei = ceis.iterator().next();

    BwEvent ev = null;
    if (recurrenceId == null) {
      ev = ei.getEvent();
    } else {
      for (final CoreEventInfo cei: ei.getOverrides()){
        if (cei.getEvent().getRecurrenceId().equals(recurrenceId)) {
          ev = cei.getEvent();
          break;
        }
      }
    }

    if (ev == null) {
      return null;
    }

    if (ev instanceof BwEventAnnotation) {
      ev = new BwEventProxy((BwEventAnnotation)ev);
    }

    return ev;
  }

  @Override
  public void restoreCategory(final BwCategory o) {
    try {
      startTransaction();

      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreCalSuite(final BwCalSuite o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().add(o);
    } finally {
      endTransaction();
    }
  }


  @Override
  public void restoreLocation(final BwLocation o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreContact(final BwContact o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreFilter(final BwFilterDef o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().addRestoredEntity(o);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreResource(final BwResource o) {
    try {
      startTransaction();

      o.markUnsaved();
      getCal().add(o);

      final BwResourceContent rc = o.getContent();

      rc.markUnsaved();
      rc.setByteValue(rc.getByteValue());
      getCal().addContent(o, rc);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void restoreUserPrefs(final BwPreferences o) {
    try {
      startTransaction();

      /* If the indexer or some other activity is running this can result in
       * a preferences object being created. See if one exists.
       */

      final BwPreferences p =
              getSvc().getPreferences(o.getOwnerHref());

      if (p != null) {
        warn("Found instance of preferences for " + o.getOwnerHref());
        o.setId(p.getId());
        o.setSeq(p.getSeq());
        getCal().update(o);
      } else {
        /* Ensure views are unsaved objects */
        final var v = o.getViews();
        if (v != null) {
          for (final BwView view: v) {
            view.markUnsaved();
          }
        }

        o.markUnsaved();

        getCal().add(o);
      }
    } finally {
      endTransaction();
    }
  }

  @Override
  public BwCollection getCalendar(final String path) {
    startTransaction();

    return getCols().get(path);
  }

  @Override
  public BwCategory getCategory(final String uid) {
    startTransaction();

    return getSvc().getCategoriesHandler().getPersistent(uid);
  }

  @Override
  public BwContact getContact(final String uid) {
    startTransaction();

    return getSvc().getContactsHandler().getPersistent(uid);
  }

  @Override
  public BwLocation getLocation(final String uid) {
    startTransaction();

    return getSvc().getLocationsHandler().getPersistent(uid);
  }

  @Override
  public BwPrincipal<?> getPrincipal(final String href) {
    startTransaction();

    return getSvc().getUsersHandler().getPrincipal(href);
  }

  @Override
  public void saveRootCalendar(final BwCollection val) {
    // Ensure id not set
    val.markUnsaved();

    try {
      startTransaction();

      getCal().addRestoredEntity(val);
    } finally {
      endTransaction();
    }
  }

  @Override
  public void addCalendar(final BwCollection o) {
    // Ensure id not set
    o.markUnsaved();

    try {
      startTransaction();

      getCal().addRestoredEntity(o);
      curBatchSize++;
    } finally {
      endTransaction();
    }
  }

  @Override
  public FixAliasResult fixSharee(final BwCollection col,
                                  final String shareeHref,
                                  final AccessType a) {
    /* First ensure this alias is not circular */

    final Set<String> paths = new TreeSet<>();

    BwCollection curCol = col;
    while (curCol.getInternalAlias()) {
      if (paths.contains(curCol.getPath())) {
        return FixAliasResult.circular;
      }

      paths.add(curCol.getPath());
      try {
        curCol = getCols().resolveAliasIdx(curCol, false, false);
      } catch (final BedeworkAccessException ignored) {
    	// Just exit the loop here. We may have multiple levels of aliases and one or more may not be accessible,
    	// but we can still check for circularity and broken aliases.
        break;
      }

      if (curCol == null) {
        return FixAliasResult.broken;
      }
    }

    // See if we are in the invite list

    final InviteType invite =
            getSvc().getSharingHandler().getInviteStatus(col);
    final String shareeCua =
            getSvc().getDirectories().userToCaladdr(shareeHref);

    UserType uentry = invite.finduser(shareeCua);

    if (uentry != null) {
      // Already in list of sharers
      return FixAliasResult.ok;
    }

    /* Now fix the sharing invite info */

    uentry = new UserType();

    uentry.setHref(shareeCua);
    uentry.setInviteStatus(AppleServerTags.inviteAccepted);
    //uentry.setCommonName(...);
    uentry.setAccess(a);
    //uentry.setSummary(s.getSummary());

    invite.getUsers().add(uentry);

    try {
      col.setQproperty(AppleServerTags.invite, invite.toXml());
      getCols().update(col);
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }

    return FixAliasResult.reshared;
  }

  /* ====================================================================
   *                       Private methods
   * ==================================================================== */

  private void startTransaction() {
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

  @Override
  public void info(final String msg) {
    if (log == null) {
      return;
    }
    log.info(msg);
  }

  @Override
  public void warn(final String msg) {
    if (log == null) {
      return;
    }
    log.warn(msg);
  }

  @Override
  public void error(final String msg) {
    if (log == null) {
      return;
    }
    log.error(msg);
  }
}
