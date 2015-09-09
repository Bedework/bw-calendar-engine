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

import org.bedework.access.AccessException;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl;
import org.bedework.access.Privilege;
import org.bedework.access.Privileges;
import org.bedework.access.WhoDefs;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.sharing.AccessType;
import org.bedework.caldav.util.sharing.InviteNotificationType;
import org.bedework.caldav.util.sharing.InviteReplyType;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.OrganizerType;
import org.bedework.caldav.util.sharing.RemoveType;
import org.bedework.caldav.util.sharing.SetType;
import org.bedework.caldav.util.sharing.ShareResultType;
import org.bedework.caldav.util.sharing.ShareType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.caldav.util.sharing.parse.Parser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.SharingI;
import org.bedework.util.misc.Uid;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.AppleServerTags;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;
import org.bedework.webdav.servlet.shared.WebdavException;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.DtStamp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.ws.Holder;

/** This type of object will handle sharing operations.
 *
 * @author Mike Douglass
 */
public class Sharing extends CalSvcDb implements SharingI {
  private static final QName removeStatus = Parser.inviteDeletedTag;

  private static final QName declineStatus = Parser.inviteDeclinedTag;

  private static final QName noresponseStatus = Parser.inviteNoresponseTag;

  /** Constructor
  *
  * @param svci service interface
  */
  Sharing(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ShareResultType share(final String principalHref,
                               final BwCalendar col,
                               final ShareType share) throws CalFacadeException {
    /* Switch identity to the sharer then reget the handler
     * and do the share
     */
    pushPrincipal(principalHref);

    try {
      return getSvc().getSharingHandler().share(col, share);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      popPrincipal();
    }
  }

  private final static class AddPrincipal {
    final BwPrincipal pr;
    final boolean forRead;

    private AddPrincipal(final BwPrincipal pr,
                         final boolean forRead) {
      this.pr = pr;
      this.forRead = forRead;
    }
  }

  @Override
  public ShareResultType share(final BwCalendar col,
                               final ShareType share) throws CalFacadeException {
    if (!col.getCanAlias()) {
      throw new CalFacadeForbidden("Cannot share");
    }

    final ShareResultType sr = new ShareResultType();
    final List<String> removePrincipalHrefs = new ArrayList<>();
    final List<AddPrincipal> addPrincipals = new ArrayList<>();

    final String calAddr = principalToCaladdr(getPrincipal());

    final InviteType invite = getInviteStatus(col);

    final List<InviteNotificationType> notifications =
        new ArrayList<>();

    boolean addedSharee = false;
    boolean removedSharee = false;

    /* If there are any removal elements in the invite, remove those
     * sharees. We'll flag hrefs as bad if they are not actually sharees.
     *
     * If we do remove a sharee we'll add notifications to the list
     * to send later.
     */
    for (final RemoveType rem: share.getRemove()) {
      final InviteNotificationType n = doRemove(col, rem,
                                                calAddr, invite);

      if (n != null) {
        removedSharee = true;
        if ((n.getPreviousStatus() != null) &&
                !n.getPreviousStatus().equals(declineStatus)) {
          // We don't notify if the user had declined
          notifications.add(n);
        }
        sr.addGood(rem.getHref());
        removePrincipalHrefs.add(rem.getHref());
      } else {
        sr.addBad(rem.getHref());
      }
    }

    /* Now deal with the added sharees if there are any.
     */
    for(final SetType set: share.getSet()) {
      final InviteNotificationType n = doSet(col, set,
                                             addPrincipals,
                                             calAddr, invite);

      if (n != null) {
        addedSharee = true;
        notifications.add(n);
        sr.addGood(set.getHref());
      } else {
        sr.addBad(set.getHref());
      }
    }

    if (!addedSharee && !removedSharee) {
      // Nothing changed
      return sr;
    }

    /* Send any invitations and update the sharing status.
     * If it's a removal and the current status is not
     * accepted then just delete the current invitation
     */

    final Notifications notify =
            (Notifications)getSvc().getNotificationsHandler();

    sendNotifications:
    for (final InviteNotificationType in: notifications) {
      final Sharee sh = getSharee(in.getHref());

      final boolean remove = in.getInviteStatus().equals(removeStatus);

      final List<NotificationType> notes =
          notify.getMatching(in.getHref(),
                             AppleServerTags.inviteNotification);

      if (!Util.isEmpty(notes)) {
        for (final NotificationType n: notes) {
          final InviteNotificationType nin = (InviteNotificationType)n.getNotification();

          if (!nin.getHostUrl().equals(in.getHostUrl())) {
            continue;
          }

          /* If it's a removal and the current status is not
           * accepted then just delete the current invitation
           *
           * If it's not a removal - remove the current one and add the new one
           */

          if (remove) {
            if (nin.getInviteStatus().equals(noresponseStatus)) {
              notify.remove(sh.pr, n);
              continue sendNotifications;
            }
          } else {
            notify.remove(sh.pr, n);
          }
        }
      }

      final NotificationType note = new NotificationType();

      note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
      note.setNotification(in);

      notify.send(sh.pr, note);

      /* Add the invite to the set of properties associated with this collection
       * We give it a name consisting of the inviteNotification tag + uid.
       */
      final QName qn = new QName(AppleServerTags.inviteNotification.getNamespaceURI(),
                                 AppleServerTags.inviteNotification.getLocalPart() +
                                         in.getUid());
      try {
        col.setProperty(NamespaceAbbrevs.prefixed(qn), in.toXml());
      } catch (final CalFacadeException cfe) {
        throw cfe;
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    if (addedSharee && !col.getShared()) {
      // Mark the collection as shared
      col.setShared(true);
    }

    try {
      col.setQproperty(AppleServerTags.invite, invite.toXml());
      getCols().update(col);

      for (final String principalHref: removePrincipalHrefs) {
        removeAccess(col, principalHref);
      }

      for (final AddPrincipal ap: addPrincipals) {
        setAccess(col, ap);
      }
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    return sr;
  }

  @Override
  public ReplyResult reply(final BwCalendar col,
                           final InviteReplyType reply) throws CalFacadeException {
    final BwCalendar home = getCols().getHome();

    if (!home.getPath().equals(col.getPath())) {
      throw new CalFacadeForbidden("Not calendar home");
    }

    /* We must have at least read access to the shared collection */

    final BwCalendar sharerCol = getCols().get(Util.buildPath(true, reply.getHostUrl()));

    if (sharerCol == null) {
      // Bad hosturl
      throw new CalFacadeForbidden("Bad hosturl or no access");
    }

    final Holder<AccessType> access = new Holder<>();

    if (!updateSharingStatus(sharerCol.getOwnerHref(),
                             sharerCol.getPath(),
                             reply,
                             access)) {
      return null;
    }

    /* Accepted */

    final AccessType at = access.value;
    final boolean sharedWritable = (at != null) && at.testReadWrite();

    /* This may be a change in access or a new sharing request. See if an alias
     * already exists to the shared collection. If it does we're done.
     * Otherwise we need to create an alias in the calendar home using the
     * reply summary as the display name */

    final List<BwCalendar> aliases = findAlias(sharerCol.getPath());
    if (!Util.isEmpty(aliases)) {
      final BwCalendar alias = aliases.get(0);

      alias.setSharedWritable(sharedWritable);
      getCols().update(alias);

      return ReplyResult.success(alias.getPath());
    }

    final BwCalendar alias = new BwCalendar();

    String summary = reply.getSummary();
    if ((summary == null) || (summary.length() == 0)) {
      summary = "Shared Calendar";
    }

    alias.setName(reply.getInReplyTo());
    alias.setSummary(summary);
    alias.setCalType(BwCalendar.calTypeAlias);
    //alias.setPath(home.getPath() + "/" + UUID.randomUUID().toString());
    alias.setAliasUri("bwcal://" + sharerCol.getPath());
    alias.setShared(true);
    alias.setSharedWritable(sharedWritable);

    final BwCalendar createdAlias = getCols().add(alias, home.getPath());

    return ReplyResult.success(createdAlias.getPath());
  }

  @Override
  public InviteType getInviteStatus(final BwCalendar col) throws CalFacadeException {
    final String inviteStr =
            col.getProperty(NamespaceAbbrevs.prefixed(
                    AppleServerTags.invite));

    if (inviteStr == null) {
      return new InviteType();
    }

    try {
      return new Parser().parseInvite(inviteStr);
    } catch (final WebdavException we) {
      throw new CalFacadeException(we);
    }
  }

  @Override
  public void delete(final BwCalendar col) throws CalFacadeException {
    final InviteType invite = getInviteStatus(col);

    for (final UserType u: invite.getUsers()) {
      if (u.getInviteStatus().equals(Parser.inviteNoresponseTag)) {
        // An outstanding invitation
        final BwPrincipal pr = caladdrToPrincipal(u.getHref());

        if (pr != null) {     // Unknown user
          final NotificationType n = findInvite(pr, col.getPath());

          if (n != null) {
            //InviteNotificationType in = (InviteNotificationType)n.getNotification();

            /* Delete the notification */
            deleteInvite(pr, n);
          }
        }
      } else if (u.getInviteStatus().equals(Parser.inviteAcceptedTag)) {
        /* Send a notification indicating we deleted/uninvited and remove their
         * alias.
         */
        final String calAddr = principalToCaladdr(getPrincipal());

        final InviteNotificationType in = deletedNotification(u.getHref(),
                                                              calAddr);
        final NotificationType note = new NotificationType();

        note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
        note.setNotification(in);

        final NotificationsI notify = getSvc().getNotificationsHandler();
        final BwPrincipal pr = caladdrToPrincipal(u.getHref());

        notify.send(pr, note);
      }

      /* Now we need to remove the alias - in theory we shouldn't have any
       * but do this anyway to clean up */

      removeAlias(col, u.getHref());
    }
  }

  @Override
  public void publish(final BwCalendar col) throws CalFacadeException {
    if (!col.getCanAlias()) {
      throw new CalFacadeForbidden("Cannot publish");
    }

    // Mark the collection as shared and published
    col.setQproperty(AppleServerTags.publishUrl, col.getPath());

    try {
      getCols().update(col);

      /* Set access to read for everybody */

      setAccess(col,
                new AddPrincipal(null, true));
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public void unpublish(final BwCalendar col) throws CalFacadeException {
    if (col.getPublick() ||
        (col.getQproperty(AppleServerTags.publishUrl) == null)) {
      throw new CalFacadeForbidden("Not published");
    }

    /* Remove access to all */

    final Acl acl = removeAccess(col.getCurrentAccess().getAcl(),
                                 null,
                                 WhoDefs.whoTypeAll);

    // Mark the collection as published
    col.removeQproperty(AppleServerTags.publishUrl);

    try {
      getCols().update(col);

      if (acl != null) {
        getSvc().changeAccess(col, acl.getAces(), true);
      }
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public SubscribeResult subscribe(final String colPath,
                                   final String subscribedName) throws CalFacadeException {
    /* We must have at least read access to the published collection */

    final BwCalendar publishedCol = getCols().get(colPath);
    final SubscribeResult sr = new SubscribeResult();

    if (publishedCol == null) {
      // Bad url?
      throw new CalFacadeForbidden("Bad url or no access");
    }

    /* The collection MUST be published - all public calendars are considered
     * to be published.
     */

    if (!publishedCol.getPublick() &&
        (publishedCol.getQproperty(AppleServerTags.publishUrl) == null)) {
      throw new CalFacadeForbidden("Not published");
    }

    /* We may already be subscribed. If so we're done.
     * Otherwise we need to create an alias in the calendar home using the
     * reply summary as the display name */

    final List<BwCalendar> aliases = findAlias(colPath);
    if (!Util.isEmpty(aliases)) {
      sr.path = aliases.get(0).getPath();
      sr.alreadySubscribed = true;

      return sr;
    }

    final BwCalendar alias = new BwCalendar();

    String summary = subscribedName;
    if ((summary == null) || (summary.length() == 0)) {
      summary = "Published Calendar";
    }

    alias.setName(getEncodedUuid());
    alias.setSummary(summary);
    alias.setCalType(BwCalendar.calTypeAlias);
    //alias.setPath(home.getPath() + "/" + UUID.randomUUID().toString());
    alias.setAliasUri("bwcal://" + colPath);
    alias.setShared(true);
    alias.setSharedWritable(false);

    sr.path = getCols().add(alias, getCols().getHome().getPath()).getPath();

    return sr;
  }

  @Override
  public SubscribeResult subscribeExternal(final String extUrl,
                                           final String subscribedName,
                                           final int refresh,
                                           final String remoteId,
                                           final String remotePw) throws CalFacadeException {
    final BwCalendar alias = new BwCalendar();
    final SubscribeResult sr = new SubscribeResult();

    String summary = subscribedName;
    if ((summary == null) || (summary.length() == 0)) {
      summary = "Published Calendar";
    }

    alias.setName(getEncodedUuid());
    alias.setSummary(summary);
    alias.setCalType(BwCalendar.calTypeExtSub);
    //alias.setPath(home.getPath() + "/" + UUID.randomUUID().toString());
    alias.setAliasUri(extUrl);
    alias.setSharedWritable(false);

    int refreshRate = 5; // XXX make this configurable 5 mins refresh minimum
    if (refresh > refreshRate) {
      refreshRate = refresh;
    }

    //noinspection UnusedAssignment
    refreshRate *= 60;

    alias.setRemoteId(remoteId);

    if (remotePw != null) {
      try {
        final String pw = getSvc().getEncrypter().encrypt(remotePw);
        alias.setRemotePw(pw);
        alias.setPwNeedsEncrypt(false);
      } catch (final CalFacadeException cfe) {
        throw cfe;
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    sr.path = getCols().add(alias, getCols().getHome().getPath()).getPath();

    return sr;
  }

  @Override
  public void unsubscribe(final BwCalendar col) throws CalFacadeException {
    if (!col.getInternalAlias()) {
      return;
    }

    final BwCalendar shared = getCols().resolveAlias(col, true, false);
    if (shared == null) {
      // Gone or no access - nothing to do now.
      return;
    }

    final String sharerHref = shared.getOwnerHref();
    final BwPrincipal sharee = getSvc().getPrincipal();

    pushPrincipal(sharerHref);

    try {

      /* Get the invite property and locate and update this sharee */

      final InviteType invite = getInviteStatus(shared);
      UserType uentry = null;
      final String invitee = principalToCaladdr(sharee);

      if (invite != null) {
        uentry = invite.finduser(invitee);
      }

      if (uentry == null) {
        if (debug) {
          trace("Cannot find invitee: " + invitee);
        }

        return;
      }

      uentry.setInviteStatus(AppleServerTags.inviteDeclined);

      shared.setProperty(NamespaceAbbrevs.prefixed(
              AppleServerTags.invite),
                         invite.toXml());
      getCols().update(shared);

      /* At this stage we need a message to notify the sharer -
         change notification.
         The name of the alias is the uid of the original invite
         */

      final NotificationType note = new NotificationType();

      note.setDtstamp(new DtStamp(new DateTime(true)).getValue());

      // Create a reply object.
      final InviteReplyType reply = new InviteReplyType();
      reply.setHref(principalToCaladdr(sharee));
      reply.setAccepted(false);
      reply.setHostUrl(shared.getPath());
      reply.setInReplyTo(col.getName());

      note.setNotification(reply);

      getSvc().getNotificationsHandler().add(note);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      popPrincipal();
    }
    /*
    final BwPrincipal pr = caladdrToPrincipal(getPrincipalHref());

    if (pr != null) {
      pushPrincipal(shared.getOwnerHref());
      NotificationType n = null;
      try {
        n = findInvite(pr, shared.getPath());
      } finally {
        popPrincipal();
      }
      if (n != null) {
        InviteNotificationType in = (InviteNotificationType)n.getNotification();
        Holder<AccessType> access = new Holder<AccessType>();

        // Create a dummy reply object.
        InviteReplyType reply = new InviteReplyType();
        reply.setHref(getPrincipalHref());
        reply.setAccepted(false);
        reply.setHostUrl(shared.getPath());
        reply.setInReplyTo(in.getUid());

        updateSharingStatus(shared.getOwnerHref(), shared.getPath(), reply, access);
      }
    }
    */

  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /* This requires updating the shared calendar to reflect the accept/decline
   * status
   */
  private boolean updateSharingStatus(final String sharerHref,
                                      final String path,
                                      final InviteReplyType reply,
                                      final Holder<AccessType> access) throws CalFacadeException {
    pushPrincipal(sharerHref);

    try {
      final BwCalendar col = getCols().get(path);

      if (col == null) {
        // Bad hosturl?
        throw new CalFacadeForbidden(CalFacadeException.shareTargetNotFound);
      }

      /* See if we have an outstanding invite for this user */

      final QName qn = new QName(AppleServerTags.inviteNotification.getNamespaceURI(),
                                 AppleServerTags.inviteNotification.getLocalPart() +
                                         reply.getInReplyTo());

      final String pname = NamespaceAbbrevs.prefixed(qn);
      final String xmlInvite = col.getProperty(pname);

      if (xmlInvite == null) {
        // No invite
        if (debug) {
          trace("No invite notification on collection with name: " + pname);
        }
        throw new CalFacadeForbidden(CalFacadeException.noInvite);
      }

      /* Remove the invite */
      col.setProperty(pname, null);

      /* Get the invite property and locate and update this sharee */

      final InviteType invite = getInviteStatus(col);
      UserType uentry = null;
      final String invitee = getSvc().getDirectories().normalizeCua(reply.getHref());

      if (invite != null) {
        uentry = invite.finduser(invitee);
      }

      if (uentry == null) {
        if (debug) {
          trace("Cannot find invitee: " + invitee);
        }
        throw new CalFacadeForbidden(CalFacadeException.noInviteeInUsers);
      }

      if (reply.testAccepted()) {
        uentry.setInviteStatus(AppleServerTags.inviteAccepted);
      } else {
        uentry.setInviteStatus(AppleServerTags.inviteDeclined);
      }

      access.value = uentry.getAccess();

      col.setProperty(NamespaceAbbrevs.prefixed(AppleServerTags.invite),
                      invite.toXml());
      getCols().update(col);

      /* Now send the sharer the reply as a notification */

      final NotificationType note = new NotificationType();

      note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
      note.setNotification(reply);

      getSvc().getNotificationsHandler().add(note);

      return reply.testAccepted();
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      popPrincipal();
    }
  }

  /** Remove a principal from the list of sharers
   *
   */
  private InviteNotificationType doRemove(final BwCalendar col,
                                          final RemoveType rem,
                                          final String calAddr,
                                          final InviteType invite) throws CalFacadeException {
    final String href = getSvc().getDirectories().normalizeCua(rem.getHref());

    final UserType uentry = invite.finduser(href);

    if (uentry == null) {
      // Not in list of sharers
      return null;
    }

    invite.getUsers().remove(uentry);

    final InviteNotificationType note =
            deletedNotification(href, calAddr);

    note.setPreviousStatus(uentry.getInviteStatus());

    removeAlias(col, uentry.getHref());

    return note;
  }

  private boolean removeAccess(final BwCalendar col,
                               final String principalHref) throws CalFacadeException {
    Acl acl = col.getCurrentAccess().getAcl();

    try {
      if (Util.isEmpty(acl.getAces())) {
        return false;
      }

      final BwPrincipal pr = caladdrToPrincipal(principalHref);

      acl = removeAccess(acl, pr.getAccount(), pr.getKind());

      if (acl == null) {
        // no change
        return false;
      }

      getSvc().changeAccess(col, acl.getAces(), true);

      if (!col.getInternalAlias()) {
        return true;
      }

      final BwCalendar target =
              getSvc().getCalendarsHandler().resolveAlias(col,
                                                          false,
                                                          false);

      if (target == null) {
        return false;
      }

      /* Switch identity to the sharee then reget the handler
       * and do the share
       */
      pushPrincipal(target.getOwnerHref());

      try {
        return removeAccess(target, principalHref);
      } catch (final CalFacadeException cfe) {
        throw cfe;
      } catch (final Throwable t) {
        throw new CalFacadeException(t);
      } finally {
        popPrincipal();
      }
    } catch (final AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  private InviteNotificationType deletedNotification(final String shareeHref,
                                                     final String sharerHref) throws CalFacadeException {
    final InviteNotificationType in = new InviteNotificationType();

    in.setUid(Uid.getUid());
    in.setHref(shareeHref);
    in.setInviteStatus(removeStatus);
    // in.setAccess(xxx); <-- current access from sharing status?
    in.setHostUrl(shareeHref);

    final OrganizerType org = new OrganizerType();
    org.setHref(sharerHref);

    in.setOrganizer(org);

    return in;
  }

  private NotificationProperties getNoteProps() throws CalFacadeException {
    return getSvc().getNotificationProperties();
  }

  private static class Sharee {
    /** Either the real principal or where we send external notifications
     * or null if we can't send
     */
    String href;
    BwPrincipal pr;

    /* This is an external sharee
     */
    boolean external;
  }

  private Sharee getSharee(final String cua) throws CalFacadeException {
    final Sharee sh = new Sharee();

    sh.href = getSvc().getDirectories().normalizeCua(cua);
    sh.pr = caladdrToPrincipal(sh.href);
    if ((sh.pr != null) &&
            (getUsers().getPrincipal(sh.pr.getPrincipalRef()) == null)) {
      /* One of our principals but doesn't yet exist in the system
       * Create the principal so we can store their notifications.
       */
      // GROUP-PRINCIPAL needs fixing here
      sh.pr = getUsers().getAlways(sh.pr.getAccount());
    } else if ((sh.pr == null) && getNoteProps().getOutboundEnabled()) {
      /* This is an external user - we are going to send a notification so
       * ensure we have the global notification id.
       */
      sh.external = true;
      sh.pr = getUsers().getAlways(getNoteProps().getNotifierId());
    }

    return sh;
  }

  private InviteNotificationType doSet(final BwCalendar col,
                                       final SetType s,
                                       final List<AddPrincipal> addPrincipals,
                                       final String calAddr,
                                       final InviteType invite) throws CalFacadeException {
    final Sharee sh = getSharee(s.getHref());

    /*
       pr != null means this is potentially one of our users.

       If the principal doesn't exist and we handle outbound
       notifications then we should drop a notification into the
       global notification collection. Eventually the user will log in
       we hope - we can then turn that invite into a real local user
       invite,
     */

    UserType uentry = invite.finduser(sh.href);

    if (uentry != null) {
      if (uentry.getInviteStatus().equals(Parser.inviteNoresponseTag)) {
        // Already an outstanding invitation
        final NotificationType n = findInvite(sh.pr, col.getPath());

        if (n != null) {
          final InviteNotificationType in =
                  (InviteNotificationType)n.getNotification();

          if (in.getAccess().equals(s.getAccess())) {
            // In their collection - no need to resend.
            return null;
          }

          /* Delete the old notification - we're changing the access */
          deleteInvite(sh.pr, n);
        }
      }
    }

    final InviteNotificationType in = new InviteNotificationType();

    in.setSharedType(InviteNotificationType.sharedTypeCalendar);
    in.setUid(Uid.getUid());
    in.setHref(sh.href);
    in.setInviteStatus(Parser.inviteNoresponseTag);
    in.setAccess(s.getAccess());
    in.setHostUrl(col.getPath());
    in.setSummary(s.getSummary());

    final OrganizerType org = new OrganizerType();
    org.setHref(calAddr);

    in.setOrganizer(org);

    in.getSupportedComponents().addAll(col.getSupportedComponents());

    // Update the collection sharing status
    if (uentry != null) {
      uentry.setInviteStatus(in.getInviteStatus());
      uentry.setAccess(in.getAccess());
    } else {
      uentry = new UserType();

      uentry.setHref(sh.href);
      uentry.setInviteStatus(in.getInviteStatus());
      uentry.setCommonName(s.getCommonName());
      uentry.setAccess(in.getAccess());
      uentry.setSummary(s.getSummary());

      invite.getUsers().add(uentry);
    }

    uentry.setExternalUser(!sh.external);

    if (!sh.external) {
      addPrincipals.add(new AddPrincipal(sh.pr,
                                         s.getAccess().testRead()));
    }

    return in;
  }

  /* For owner of collection */
  private final static Privilege allPriv = Privileges.makePriv(Privileges.privAll);

  private final static Privilege bindPriv = Privileges.makePriv(Privileges.privBind);
  private final static Privilege readPriv = Privileges.makePriv(Privileges.privRead);
  private final static Privilege unbindPriv = Privileges.makePriv(Privileges.privUnbind);
  private final static Privilege write = Privileges.makePriv(Privileges.privWrite);

  // Old scheduling privs
  private final static Privilege readFreeBusyPriv = Privileges.makePriv(Privileges.privReadFreeBusy);
  private final static Privilege schedulePriv = Privileges.makePriv(Privileges.privSchedule);

  private final static Privilege scheduleDeliverPriv = Privileges.makePriv(Privileges.privScheduleDeliver);

  private final static Collection<Privilege> allPrivs = new ArrayList<>();

  private final static Collection<Privilege> readPrivs = new ArrayList<>();

  private final static Collection<Privilege> readWritePrivs = new ArrayList<>();

  static {
    allPrivs.add(allPriv);

    readPrivs.add(readPriv);
    readPrivs.add(readFreeBusyPriv); // old
    readPrivs.add(schedulePriv); // old
    readPrivs.add(scheduleDeliverPriv);

    readWritePrivs.add(bindPriv);
    readWritePrivs.add(readPriv);
    readWritePrivs.add(unbindPriv);
    readWritePrivs.add(write);
    readWritePrivs.add(readFreeBusyPriv); // old
    readWritePrivs.add(schedulePriv); //old
    readWritePrivs.add(scheduleDeliverPriv);
  }

  private void setAccess(final BwCalendar col,
                         final AddPrincipal ap) throws CalFacadeException {
    try {
      final String whoHref;
      final int whoKind;

      if (ap.pr != null) {
        whoHref = ap.pr.getPrincipalRef();
        whoKind = ap.pr.getKind();
      } else {
        // Read to all
        whoHref = null;
        whoKind = WhoDefs.whoTypeAll;
      }

      Acl acl = col.getCurrentAccess().getAcl();
      final AceWho who = AceWho.getAceWho(whoHref, whoKind, false);

      final Collection<Privilege> desiredPriv;

      if (ap.forRead) {
        desiredPriv = readPrivs;
      } else {
        desiredPriv = readWritePrivs;
      }

      /*
      boolean removeCurrentPrivs = false;

      for (Ace a: ainfo.acl.getAces()) {
        if (a.getWho().equals(who)) {
          if (a.getHow().equals(desiredPriv)) {
            // Already have that access
            return null;
          }

          removeCurrentPrivs = true;
        }
      }

      if (removeCurrentPrivs) {
        ainfo.acl = ainfo.acl.removeWho(who);
      }
      */
      Acl removed = acl.removeWho(who);

      if (removed != null) {
        acl = removed;
      }

      final BwPrincipal owner = getUsers().getPrincipal(col.getOwnerHref());
      final AceWho ownerWho =
              AceWho.getAceWho(owner.getAccount(), owner.getKind(), false);

      removed = acl.removeWho(ownerWho);

      if (removed != null) {
        acl = removed;
      }

      final Collection<Ace> aces = new ArrayList<>();
      aces.addAll(acl.getAces());

      aces.add(Ace.makeAce(who, desiredPriv, null));

      aces.add(Ace.makeAce(ownerWho, allPrivs, null));

      getSvc().changeAccess(col, aces, true);

      if (!col.getInternalAlias()) {
        return;
      }

      final BwCalendar target =
              getSvc().getCalendarsHandler().resolveAlias(col,
                                                          false,
                                                          false);

      if (target != null) {
        /* Switch identity to the sharee then reget the handler
         * and do the share
         */
        pushPrincipal(target.getOwnerHref());
        try {
          setAccess(target, ap);
        } catch (final CalFacadeException cfe) {
          throw cfe;
        } catch (final Throwable t) {
          throw new CalFacadeException(t);
        } finally {
          popPrincipal();
        }
      }
    } catch (final AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  /* Return null for no change */
  private Acl removeAccess(final Acl acl,
                           final String href,
                           final int whoKind) throws CalFacadeException {
    try {
      final AceWho who = AceWho.getAceWho(href, whoKind, false);

      return acl.removeWho(who);
    } catch (final AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  private void removeAlias(final BwCalendar col,
                           final String shareeHref) throws CalFacadeException {
    pushPrincipal(shareeHref);

    try {
      final List<BwCalendar> cols =
              ((Calendars)getCols()).findUserAlias(col.getPath());

      if (!Util.isEmpty(cols)) {
        for (final BwCalendar alias: cols) {
          getCols().delete(alias, false, true);
        }
      }
    } finally {
      popPrincipal();
    }
  }

  private NotificationType findInvite(final BwPrincipal pr,
                                            final String href) throws CalFacadeException {
    final List<NotificationType> ns =
        getSvc().getNotificationsHandler().getMatching(pr,
                                                       Parser.inviteNotificationTag);

    for (final NotificationType n: ns) {
      final InviteNotificationType in = (InviteNotificationType)n.getNotification();

      if (in.getHostUrl().equals(href)) {
        return n;
      }
    }

    return null;
  }

  private void deleteInvite(final BwPrincipal pr,
                            final NotificationType n) throws CalFacadeException {
    ((Notifications)getSvc().getNotificationsHandler()).remove(pr, n);
  }
}
