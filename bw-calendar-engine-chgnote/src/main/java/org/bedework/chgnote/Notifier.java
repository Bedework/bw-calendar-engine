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

import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.ResourceChangeType;
import org.bedework.caldav.util.notifications.UpdatedType;
import org.bedework.caldav.util.notifications.parse.Parser;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.notifications.SuggestBaseNotificationType;
import org.bedework.calfacade.notifications.SuggestNotificationType;
import org.bedework.calfacade.notifications.SuggestResponseNotificationType;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calsvc.AbstractScheduler;
import org.bedework.sysevents.events.CollectionMovedEvent;
import org.bedework.sysevents.events.EntityDeletedEvent;
import org.bedework.sysevents.events.EntityMovedEvent;
import org.bedework.sysevents.events.EntityUpdateEvent;
import org.bedework.sysevents.events.NotificationEvent;
import org.bedework.sysevents.events.OwnedHrefEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;
import org.bedework.sysevents.events.publicAdmin.EntitySuggestedEvent;
import org.bedework.sysevents.events.publicAdmin.EntitySuggestedResponseEvent;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Uid;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.AppleServerTags;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Handles processing of the notification messages.
 *
 * <p>We assume that delays are introduced elsewhere - probably in message queues.
 *
 * @author Mike Douglass
 */
public class Notifier extends AbstractScheduler {
  private static class ColInfo {
    boolean shared;

    Set<String> enabledSharees;

    private void addSharee(final String val) {
      if (enabledSharees == null) {
        enabledSharees = new TreeSet<>();
      }

      enabledSharees.add(val);
    }
  }

  private static final Map<String, ColInfo> colInfo =
      new FlushMap<>(100, // size
          60 * 1000 * 3,
          500);

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

    if (!sysCode.getNotifiableEvent()) {
      // Ignore it
      return ProcessMessageResult.IGNORED;
    }

    return processChangeEvent(msg);
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
        getSvci(targetPrincipal);

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
      if (debug) {
        trace("Stale state exception");
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

      if (!inSharedCollection(oheMsg)) {
        return ProcessMessageResult.IGNORED;
      }

      if (collection) {
        return processCollection(msg);
      } else {
        return processEntity(oheMsg);
      }
    } catch (final CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
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
    boolean processed = false;

    try {
      getSvci(msg.getOwnerHref());

      // Normalized
      final String ownerHref = getPrincipalHref();
      final String href = msg.getHref();

      if (debug) {
        trace(msg.toString());
        trace("Notification for entity " + href +
              " owner principal " + ownerHref);
      }

      final String colPath = getPathTo(href);

      final ColInfo ci = getColInfo(colPath);
      if (ci == null) {
        // path pointing to non-existent collection
        return ProcessMessageResult.PROCESSED;
      }

      if (!ci.shared || Util.isEmpty(ci.enabledSharees)) {
        return ProcessMessageResult.PROCESSED;
      }

      if (!(note.getNotification() instanceof ResourceChangeType)) {
        // Don't know what to do with that
        return ProcessMessageResult.PROCESSED;
      }

      final ResourceChangeType rc = (ResourceChangeType)note.getNotification();

      // SCHEMA
      if (rc.getEncoding() == null) {
        // No changes were added
        return ProcessMessageResult.PROCESSED;
      }

      /* We have to notify each sharee of the change. We do not notify the
       * sharee that made the change.
       */

      for (final String sh: ci.enabledSharees) {
        final String shareeHref = getSvc().getDirectories().normalizeCua(sh);

        /* No notification if this is the owner of the changed resource
         */
        if (shareeHref.equals(msg.getAuthPrincipalHref())) {
          continue;
        }

        try {
          pushPrincipal(shareeHref);

          /* See if we have any notifications for this entity
           *
           * SCHEMA: If we could store the entire encoded path in the name we
           * could just do a get
           */
          NotificationType storedNote = null;

          for (final NotificationType n:
                  getNotes().getMatching(AppleServerTags.resourceChange)) {
            if (rc.getEncoding().equals(n.getNotification().getEncoding())) {
              storedNote = n;
              break;
            }
          }

          /* Add to collection or update or merge this one into a
             stored one.

            1. If no notification is present add the new one to the
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

          if (storedNote == null) {
            // Choice 1 - Just save this one
            rc.setName(getEncodedUuid());
            getNotes().add(note);
            processed = true;
            continue;
          }

          if (!(storedNote.getNotification() instanceof ResourceChangeType)) {
            // Don't know what to do with that
            continue;
          }

          final ResourceChangeType storedRc =
                  (ResourceChangeType)storedNote.getNotification();

          if (rc.getCreated() != null) {
            // Choice 2 above - update the old one
            storedRc.setCollectionChanges(null);
            storedRc.setDeleted(null);
            storedRc.setCreated(rc.getCreated());

            getNotes().update(storedNote);
            processed = true;
            continue;
          }

          if (rc.getDeleted() != null) {
            if (storedRc.getCreated() != null) {
              // Choice 3 above - discard both
              getNotes().remove(storedNote);
              processed = true;
              continue;
            }

            // Choice 4 above - discard updates
            storedRc.setCollectionChanges(null);
            storedRc.setDeleted(rc.getDeleted());
            storedRc.clearUpdated();

            getNotes().update(storedNote);
            processed = true;
            continue;
          }

          if (storedRc.getCreated() != null) {
            // Choice 5 above - discard new updates
            continue;
          }

          if (!Util.isEmpty(rc.getUpdated())) {
            // Choices 6 and 7 above
            storedRc.setDeleted(null);
            storedRc.setCreated(null);
            storedRc.setCollectionChanges(null);

            for (final UpdatedType u: rc.getUpdated()) {
              storedRc.addUpdate(u);
            }

            getNotes().update(storedNote);
            processed = true;
          }
        } finally {
          popPrincipal();
        }
      }

      if (processed) {
        return ProcessMessageResult.PROCESSED;
      }

      return ProcessMessageResult.IGNORED;
    } finally {
      closeSvci(getSvc());
    }
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

  private synchronized ColInfo getColInfo(final String path) throws CalFacadeException {
    ColInfo ci = colInfo.get(path);

    if (ci != null) {
      return ci;
    }

    ci = new ColInfo();
    colInfo.put(path, ci);

    final BwCalendar col = getCols().get(path);
    if (col == null) {
      return null;
    }

    /* If this is a public collection we always send change notifications.
     * The notifications form part of the navigation.
     */

    if (col.getPublick()) {
      /* We need to notify all admin group event owners. There might
       * be a lot of these.
       * /
       NOT DOING THAT YET
      ci.shared = true;
      ci.enabledSharees = adminGroupOwners();
      */
      return ci;
    }

    if (!Boolean.valueOf(col.getQproperty(AppleServerTags.shared))) {
      ci.shared = false;
      return ci; // no sharees
    }

    /* for each sharee in the list find user collection(s) pointing to this
     * collection and add the sharee if any are enabled for notifications.
     */

    final InviteType invite =
            getSvc().getSharingHandler().getInviteStatus(col);

    if (invite == null) {
      // No sharees
      return ci; // no sharees
    }

    ci.shared = true;

    final boolean defaultEnabled =
            getAuthpars().getDefaultChangesNotifications();

    if (notificationsEnabled(col, defaultEnabled)) {
      ci.addSharee(col.getOwnerHref());
    }

    /* for sharees - it's the alias which points at this collection
     * which holds the status.
     */
    for (final UserType u: invite.getUsers()) {
      try {
        pushPrincipal(u.getHref());

        final List<BwCalendar> cols;

        cols = findAlias(path);

        if (Util.isEmpty(cols)) {
          return ci;
        }

        for (final BwCalendar c: cols) {
          if (notificationsEnabled(c, defaultEnabled)) {
            ci.addSharee(u.getHref());
          }
        }
      } finally {
        popPrincipal();
      }
    }

    return ci;
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

  /* For private collections we'll use the AppleServerTags.notifyChanges
   * property to indicate if we should notify the sharee.
   *
   * For public collections we always notify
   */
  private boolean notificationsEnabled(final BwCalendar col,
                                       final boolean defaultEnabled) {
    if (col.getPublick()) {
      return true;
    }

    final String enabledVal = col.getQproperty(AppleServerTags.notifyChanges);

    if (enabledVal == null) {
      return defaultEnabled;
    }

    return Boolean.valueOf(enabledVal);
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

  private String getPathTo(final String href) {
    if ((href == null) || (href.length() == 0)) {
      return null;
    }

    final int pos = href.lastIndexOf("/");

    if (pos == 0) {
      return null;
    }

    return href.substring(0, pos);
  }
}
