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
package org.bedework.chgnote;

import org.bedework.caldav.util.notifications.BaseNotificationType;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.ResourceChangeType;
import org.bedework.caldav.util.notifications.UpdatedType;
import org.bedework.caldav.util.notifications.admin.AdminNotificationType;
import org.bedework.caldav.util.notifications.admin.ApprovalResponseNotificationType;
import org.bedework.caldav.util.notifications.admin.AwaitingApprovalNotificationType;
import org.bedework.caldav.util.notifications.parse.Parser;
import org.bedework.caldav.util.notifications.suggest.SuggestBaseNotificationType;
import org.bedework.caldav.util.notifications.suggest.SuggestNotificationType;
import org.bedework.caldav.util.notifications.suggest.SuggestResponseNotificationType;
import org.bedework.calfacade.AliasesInfo;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calsvc.AbstractScheduler;
import org.bedework.sysevents.events.CollectionMovedEvent;
import org.bedework.sysevents.events.EntityDeletedEvent;
import org.bedework.sysevents.events.EntityMovedEvent;
import org.bedework.sysevents.events.EntityUpdateEvent;
import org.bedework.sysevents.events.NotificationEvent;
import org.bedework.sysevents.events.OwnedHrefEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.sysevents.events.publicAdmin.EntityApprovalNeededEvent;
import org.bedework.sysevents.events.publicAdmin.EntityApprovalResponseEvent;
import org.bedework.sysevents.events.publicAdmin.EntitySuggestedEvent;
import org.bedework.sysevents.events.publicAdmin.EntitySuggestedResponseEvent;
import org.bedework.util.misc.Uid;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.AppleServerTags;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import static java.lang.String.format;

/** Handles processing of the notification messages.
 *
 * <p>We assume that delays are introduced elsewhere - probably in message queues.
 *
 * @author Mike Douglass
 */
public class Notifier extends AbstractScheduler {
  /**
   */
  public Notifier() {
    super();
  }

  @Override
  public ProcessMessageResult processMessage(final SysEvent msg) {
    final SysCode sysCode = msg.getSysCode();

    if ((sysCode == SysCode.SUGGESTED) ||
            (sysCode == SysCode.SUGGESTED_RESPONSE)) {
      return processSuggested(msg);
    }

    if ((sysCode == SysCode.APPROVAL_STATUS) ||
            (sysCode == SysCode.APPROVAL_NEEDED)) {
      return processApproved(msg);
    }

    if (!sysCode.getNotifiableEvent()) {
      // Ignore it
      return ProcessMessageResult.IGNORED;
    }

    return processChangeEvent(msg);
  }

  private ProcessMessageResult processApproved(final SysEvent msg) {
    try {
      String targetPrincipal = null;
      final SysCode sysCode = msg.getSysCode();
      AdminNotificationType ant = null;

      if (sysCode == SysCode.APPROVAL_STATUS) {
        final EntityApprovalResponseEvent eare =
                (EntityApprovalResponseEvent)msg;

        final ApprovalResponseNotificationType arnt =
                new ApprovalResponseNotificationType();

        arnt.setUid(Uid.getUid());
        arnt.setHref(eare.getHref());
        arnt.setPrincipalHref(eare.getOwnerHref());
        arnt.setAccepted(eare.getApproved());
        arnt.setComment(eare.getComment());
        arnt.setCalsuiteHref(eare.getCalsuiteHref());

        targetPrincipal = eare.getCalsuiteHref();
        ant = arnt;
      } else if (sysCode == SysCode.APPROVAL_NEEDED) {
        final EntityApprovalNeededEvent eane =
                (EntityApprovalNeededEvent)msg;

        final AwaitingApprovalNotificationType aant =
                new AwaitingApprovalNotificationType();

        aant.setUid(Uid.getUid());
        aant.setHref(eane.getHref());
        aant.setPrincipalHref(eane.getOwnerHref());
        aant.setComment(eane.getComment());
        aant.setCalsuiteHref(eane.getCalsuiteHref());

        targetPrincipal = eane.getCalsuiteHref();
        ant = aant;
      }

      if (ant == null) {
        return ProcessMessageResult.IGNORED;
      }

      try {
        getSvci(targetPrincipal, "notifier-appr");

        /* See if we have any notifications for this entity
           *
           * SCHEMA: If we could store the entire encoded path in the name we
           * could just do a get
           */
        NotificationType storedNote = null;

        for (final NotificationType n:
                getNotes().getMatching(ant.getElementName())) {
          if ((n == null) || (n.getNotification() == null)) {
            // Bad notiifcation?
            continue;
          }

          final AdminNotificationType ns =
                  (AdminNotificationType)n.getNotification();

          if (ant.getHref().equals(ns.getHref())) {
            // Already have a notification for resource
            storedNote = n;
            break;
          }
        }

        /* If we already have a notification we should delete it
           */

        if (storedNote != null) {
          getNotes().remove(storedNote);
        }

        // save this one
        ant.setName(getEncodedUuid());

        final NotificationType n = new NotificationType();
        n.setNotification(ant);

        getNotes().add(n);
        return ProcessMessageResult.PROCESSED;
      } finally {
        closeSvci(getSvc());
      }
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    } finally {
      try {
        closeSvci(getSvc());
      } catch (final Throwable ignored) {
      }
    }

    return ProcessMessageResult.FAILED;
  }

  private ProcessMessageResult processSuggested(final SysEvent msg) {
    try {
      final SysCode sysCode = msg.getSysCode();
      String targetPrincipal = null;
      SuggestBaseNotificationType sbnt = null;

      if (sysCode == SysCode.SUGGESTED) {
        final EntitySuggestedEvent ese = (EntitySuggestedEvent)msg;

        final SuggestNotificationType snt =
                new SuggestNotificationType();

        snt.setUid(Uid.getUid());
        snt.setHref(ese.getHref());
        snt.setSuggesterHref(ese.getAuthPrincipalHref());
        snt.setSuggesteeHref(ese.getTargetPrincipalHref());

        targetPrincipal = ese.getTargetPrincipalHref();
        sbnt = snt;
      } else if (sysCode == SysCode.SUGGESTED_RESPONSE) {
        final EntitySuggestedResponseEvent esre = (EntitySuggestedResponseEvent)msg;

        final SuggestResponseNotificationType srnt =
                new SuggestResponseNotificationType();

        srnt.setUid(Uid.getUid());
        srnt.setHref(esre.getHref());
        srnt.setSuggesteeHref(esre.getAuthPrincipalHref());
        srnt.setSuggesterHref(esre.getTargetPrincipalHref());
        srnt.setAccepted(esre.getAccepted());

        targetPrincipal = srnt.getSuggesterHref();
        sbnt = srnt;
      }

      if (sbnt == null) {
        return ProcessMessageResult.IGNORED;
      }

      try {
        getSvci(targetPrincipal, "notifier-sug");

          /* See if we have any notifications for this entity
           *
           * SCHEMA: If we could store the entire encoded path in the name we
           * could just do a get
           */
        NotificationType storedNote = null;

        for (final NotificationType n:
                getNotes().getMatching(sbnt.getElementName())) {
          if ((n == null) || (n.getNotification() == null)) {
            // Bad notiifcation?
            continue;
          }

          final SuggestBaseNotificationType ns =
                  (SuggestBaseNotificationType)n.getNotification();

          if (sbnt.getHref().equals(ns.getHref())) {
            // Suggested resource
            storedNote = n;
            break;
          }
        }

        /* If we already have a suggestion we don't add another
           */

        if (storedNote == null) {
          // save this one
          sbnt.setName(getEncodedUuid());

          final NotificationType n = new NotificationType();
          n.setNotification(sbnt);

          getNotes().add(n);
          return ProcessMessageResult.PROCESSED;
        }

        return ProcessMessageResult.IGNORED;
      } finally {
        closeSvci(getSvc());
      }
    } catch (final CalFacadeStaleStateException csse) {
      if (debug()) {
        debug("Stale state exception");
      }

      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    } finally {
      try {
        closeSvci(getSvc());
      } catch (final Throwable ignored) {
      }
    }

    return ProcessMessageResult.FAILED;
  }

  private ProcessMessageResult processChangeEvent(final SysEvent msg) {
    final boolean collection = msg.getSysCode().getCollectionRef();

    try {
      if (!(msg instanceof OwnedHrefEvent)) {
        return ProcessMessageResult.IGNORED;
      }

      final OwnedHrefEvent oheMsg = (OwnedHrefEvent)msg;

      if (collection) {
        return processCollection(msg);
      } else {
        return processEntity(oheMsg);
      }
    } catch (final CalFacadeStaleStateException csse) {
      if (debug()) {
        debug("Stale state exception");
      }

      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    } finally {
      try {
        closeSvci(getSvc());
      } catch (final Throwable ignored) {}
    }

    return ProcessMessageResult.FAILED;
  }

  /* Process a collection change event.
   *
   */
  @SuppressWarnings("UnusedParameters")
  private ProcessMessageResult processCollection(final SysEvent msg) throws CalFacadeException {
    return ProcessMessageResult.FAILED_NORETRIES;
  }

  /* Process an entity change event.
   *
   */
  private ProcessMessageResult processEntity(final OwnedHrefEvent msg) throws CalFacadeException {
    try {
      final NotificationType note = getNotification(msg);

      if (note == null) {
        return ProcessMessageResult.PROCESSED;
      }

      if ((msg instanceof EntityDeletedEvent) ||
          (msg instanceof EntityUpdateEvent)) {
        return doChangeNotification(msg, note);
      }

      return ProcessMessageResult.PROCESSED;
    } catch (final Throwable t) {
      error(t);
      return ProcessMessageResult.FAILED_NORETRIES;
    }
  }

  private ProcessMessageResult doChangeNotification(final OwnedHrefEvent msg,
                                                    final NotificationType note) throws CalFacadeException {
    var before = System.currentTimeMillis();
    try {
      getSvci(msg.getOwnerHref(), "notifier-chg");
      if (trace()) {
        var after = System.currentTimeMillis();
        trace(format("Getsvci took %s", after - before));
        before = after;
      }

      // Normalized
      final String ownerHref = getPrincipalHref();
      final String href = msg.getHref();

      if (debug()) {
        trace(msg.toString());
        debug("Notification for entity " + href +
              " owner principal " + ownerHref);
      }

      final String[] split = Util.splitName(href);

      final AliasesInfo ai = getCols().getAliasesInfo(split[0],
                                                      split[1]);
      if (trace()) {
        var after = System.currentTimeMillis();
        trace(format("getAliasesInfo took %s", after - before));
        before = after;
      }

      if (ai == null) {
        // path pointing to non-existent collection
        return ProcessMessageResult.PROCESSED;
      }

      if (!ai.getShared()) {
        return ProcessMessageResult.PROCESSED;
      }

      if (!(note.getNotification() instanceof ResourceChangeType)) {
        // Don't know what to do with that
        return ProcessMessageResult.PROCESSED;
      }

      final ResourceChangeType rc = (ResourceChangeType)note.getNotification();

      // SCHEMA - encoding is the base64 encoded name
      if (rc.getEncoding() == null) {
        // No changes were added
        return ProcessMessageResult.PROCESSED;
      }
      
      var processed = processAliasInfo(ai, msg.getAuthPrincipalHref(), rc);
      if (trace()) {
        var after = System.currentTimeMillis();
        trace(format("processAliasInfo took %s", after - before));
        before = after;
      }

      if (processed) {
        return ProcessMessageResult.PROCESSED;
      }

      return ProcessMessageResult.IGNORED;
    } finally {
      closeSvci(getSvc());
    }
  }

  private boolean processAliasInfo(final AliasesInfo ai,
                                   final String authHref,
                                   final ResourceChangeType rc) throws CalFacadeException {
    /* We have to notify the sharee of the change. We do not notify the
       * sharee that made the change.
       */

    final String shareeHref = ai.getPrincipalHref();

    if (shareeHref.equals(authHref) || !ai.getVisible()) {
      // This sharee made the change or the event is not visible to this alias. Do not notify, but process other aliases.
      return checkAliases(ai, authHref, rc);
    }

    boolean processed = false;
    
    // We need this a lot
    final String colHref = ai.getCollection().getPath();

    // We need to push if this is not the current user
    final boolean doPushPrincipal =
            !shareeHref.equals(getPrincipalHref());

    try {
      if (doPushPrincipal) {
        pushPrincipal(shareeHref);
      }

      if (debug()) {
        debug("Change notification for principal " + shareeHref +
                      " and href " + colHref);
      }

      final BwPreferences p = getSvc().getPrefsHandler().get();

      if ((p != null) && (p.getNoNotifications())) {
        if (debug()) {
          debug("Notification for principal " + shareeHref +
                        " is suppressed");
        }

        return checkAliases(ai, authHref, rc);
      }

      /* See if we have any notifications for this entity referenced
       * by the href for the current alias
       *
       */
      NotificationType storedNote = null;
      final String resourceHref = Util.buildPath(false, colHref,
                                                 "/",
                                                 ai.getEntityName());

      for (final NotificationType n:
              getNotes().getMatching(AppleServerTags.resourceChange)) {

        final BaseNotificationType bnt = n.getNotification();
        
        if (!(bnt instanceof ResourceChangeType)) {
          // Don't know what to do with that
          continue;
        }
        
        // SCHEMA: encoding is the base 64 encoded href
        if (((ResourceChangeType)bnt).sameHref(resourceHref)) {
          storedNote = n;
          break;
        }
      }


      /* Add to collection or update or merge this one into a
             stored one.

        1. If no notification is present add a new one to the
           notification collection

        2. If the new notification is a create discard the old and
           create a new one (somehow we left an old create in the
           collection)

        3. If the new notification is a deletion and a create is
           present throw them all away. User doesn't need to
           know an event was created then deleted

        4. If the new notification is a deletion and updates are
           present discard the updates.

        5. If the new notification is updates and a create is present
           discard the new (to the end user it just looks like a
           new event - they don't care that it changed - events
           will typically change a lot just after being added
           often due to implicit scheduling).

        6. If the new notification is updates and a deletion is
           present (should not occur - means we missed a create),
           discard the old and add the new.

        7. If the new notification is updates (only valid choice left)
           merge into the updates.

       */

      process:
      {
        if (storedNote == null) {
          // Choice 1 - Just save a copy of this one with our href

          final ResourceChangeType rcCopy =
                  rc.copyForAlias(colHref);
          rcCopy.setHref(resourceHref);

          final NotificationType note = new NotificationType();

          note.setNotification(rcCopy);
          note.setName(getEncodedUuid());
          getNotes().add(note);
          processed = true;
          break process;
        }

        final ResourceChangeType storedRc =
                (ResourceChangeType)storedNote.getNotification();

        if (rc.getCreated() != null) {
          // Choice 2 above - update the old one
          storedRc.setCollectionChanges(null);
          storedRc.setDeleted(null);
          storedRc.setCreated(rc.getCreated().copyForAlias(colHref));

          getNotes().update(storedNote);
          processed = true;
          break process;
        }

        if (rc.getDeleted() != null) {
          if (storedRc.getCreated() != null) {
            // Choice 3 above - discard both
            getNotes().remove(storedNote);
            processed = true;
            break process;
          }

          // Choice 4 above - discard updates
          storedRc.setCollectionChanges(null);
          storedRc.setDeleted(rc.getDeleted().copyForAlias(colHref));
          storedRc.clearUpdated();

          getNotes().update(storedNote);
          processed = true;
          break process;
        }

        if (storedRc.getCreated() != null) {
          // Choice 5 above - discard new updates
          break process;
        }

        if (!Util.isEmpty(rc.getUpdated())) {
          // Choices 6 and 7 above
          storedRc.setDeleted(null);
          storedRc.setCreated(null);
          storedRc.setCollectionChanges(null);

          for (final UpdatedType u : rc.getUpdated()) {
            storedRc.addUpdate(u.copyForAlias(colHref));
          }

          getNotes().update(storedNote);
          processed = true;
        }
      } // process:
    } finally {
      if (doPushPrincipal) {
        popPrincipal();
      }
    }

    if (checkAliases(ai, authHref, rc)) {
      processed = true;
    }

    return processed;
  }

  private boolean checkAliases(final AliasesInfo ai,
                               final String authHref,
                               final ResourceChangeType rc) throws CalFacadeException {
    boolean processed = false;

    for (final AliasesInfo aai: ai.getAliases()) {
      if (processAliasInfo(aai, authHref, rc)) {
        processed = true;
      }
    }

    return processed;
  }

  private NotificationType getNotification(final SysEvent msg) throws CalFacadeException {
    try {
      if (msg instanceof NotificationEvent) {
        return Parser.fromXml(((NotificationEvent)msg).getNotification());
      }
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
    return null;
  }

  private Set<String> adminGroupOwners() throws CalFacadeException {
    final Set<String> hrefs = new TreeSet<>();
    adminGroupOwners(hrefs,
                     getSvc().getAdminDirectories().getAll(true));

    return hrefs;
  }

  private void adminGroupOwners(final Set<String> hrefs,
                                final Collection<? extends BwPrincipal> prs) throws CalFacadeException {
    if (Util.isEmpty(prs)) {
      return;
    }

    for (final BwPrincipal pr: prs) {
      if (pr instanceof BwAdminGroup) {
        final BwAdminGroup adGrp = (BwAdminGroup)pr;

        hrefs.add(adGrp.getOwnerHref());

        adminGroupOwners(hrefs, adGrp.getGroupMembers());
      }
    }
  }

  private boolean inSharedCollection(final OwnedHrefEvent msg) {
    if (msg.getShared()) {
      return true;
    }

    if (msg.getSysCode() == SysCode.ENTITY_MOVED) {
      return ((EntityMovedEvent)msg).getOldShared();
    }

    if (msg.getSysCode() == SysCode.COLLECTION_MOVED) {
      return ((CollectionMovedEvent)msg).getOldShared();
    }

    return false;
  }
}
