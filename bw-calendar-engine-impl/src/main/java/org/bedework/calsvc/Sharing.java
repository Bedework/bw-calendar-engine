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

  private static final QName noresponseStatus = Parser.inviteNoresponseTag;

  /** Constructor
  *
  * @param svci
  */
  Sharing(final CalSvc svci) {
    super(svci);
  }

  @Override
  public ShareResultType share(final String principalHref,
                               final BwCalendar col,
                               final ShareType share) throws CalFacadeException {
    try {
      pushPrincipal(principalHref);
      return getSvc().getSharingHandler().share(col, share);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      popPrincipal();
    }
  }

  @Override
  public ShareResultType share(final BwCalendar col,
                               final ShareType share) throws CalFacadeException {
    if (!col.getCanAlias()) {
      throw new CalFacadeForbidden("Cannot share");
    }

    final ShareResultType sr = new ShareResultType();
    final Acl acl = col.getCurrentAccess().getAcl();
    final Holder<Acl> hacl = new Holder<Acl>();
    hacl.value = acl;

    final String calAddr = principalToCaladdr(getPrincipal());

    final InviteType invite = getInviteStatus(col);

    final List<InviteNotificationType> notifications =
        new ArrayList<InviteNotificationType>();

    boolean addedSharee = false;

    for (final RemoveType rem: share.getRemove()) {
      final InviteNotificationType n = doRemove(col, rem, hacl, calAddr, invite);

      if (n != null) {
        notifications.add(n);
        sr.addGood(rem.getHref());
      } else {
        sr.addBad(rem.getHref());
      }
    }

    for(final SetType set: share.getSet()) {
      final InviteNotificationType n = doSet(col, set, hacl, calAddr, invite);

      if (n != null) {
        addedSharee = true;
        notifications.add(n);
        sr.addGood(set.getHref());
      } else {
        sr.addBad(set.getHref());
      }
    }

    if (notifications.isEmpty()) {
      // Nothing changed
      return sr;
    }

    /* Send the invitations and update the sharing status.
     * If it's a removal and the current status is not
     * accepted then just delete the current invitation
     */

    final NotificationsI notify = getSvc().getNotificationsHandler();

    sendNotifications:
    for (final InviteNotificationType in: notifications) {
      final BwPrincipal pr = caladdrToPrincipal(in.getHref());
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
              notify.remove(pr, n);
              continue sendNotifications;
            }
          } else {
            notify.remove(pr, n);
          }
        }
      }

      final NotificationType note = new NotificationType();

      note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
      note.setNotification(in);

      notify.send(pr, note);

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

      getSvc().changeAccess(col, hacl.value.getAces(), true);
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
    BwCalendar home = getCols().getHome();

    if (!home.getPath().equals(col.getPath())) {
      throw new CalFacadeForbidden("Not calendar home");
    }

    /* We must have at least read access to the shared collection */

    BwCalendar sharerCol = getCols().get(Util.buildPath(true, reply.getHostUrl()));

    if (sharerCol == null) {
      // Bad hosturl
      throw new CalFacadeForbidden("Bad hosturl or no access");
    }

    Holder<AccessType> access = new Holder<AccessType>();

    if (!updateSharingStatus(sharerCol.getOwnerHref(),
                             sharerCol.getPath(),
                             reply,
                             access)) {
      return null;
    }

    /* Accepted */

    AccessType at = access.value;
    boolean sharedWritable = (at != null) && at.testReadWrite();

    /* This may be a change in access or a new sharing request. See if an alias
     * already exists to the shared collection. If it does we're done.
     * Otherwise we need to create an alias in the calendar home using the
     * reply summary as the display name */

    List<BwCalendar> aliases = findAlias(sharerCol.getPath());
    if (!Util.isEmpty(aliases)) {
      BwCalendar alias = aliases.get(0);

      alias.setSharedWritable(sharedWritable);
      getCols().update(alias);

      return ReplyResult.success(alias.getPath());
    }

    BwCalendar alias = new BwCalendar();

    String summary = reply.getSummary();
    if ((summary == null) || (summary.length() == 0)) {
      summary = "Shared Calendar";
    }

    alias.setName(getEncodedUuid());
    alias.setSummary(summary);
    alias.setCalType(BwCalendar.calTypeAlias);
    //alias.setPath(home.getPath() + "/" + UUID.randomUUID().toString());
    alias.setAliasUri("bwcal://" + sharerCol.getPath());
    alias.setShared(true);
    alias.setSharedWritable(sharedWritable);

    alias = getCols().add(alias, home.getPath());

    return ReplyResult.success(alias.getPath());
  }

  @Override
  public InviteType getInviteStatus(final BwCalendar col) throws CalFacadeException {
    String inviteStr = col.getProperty(NamespaceAbbrevs.prefixed(AppleServerTags.invite));

    if (inviteStr == null) {
      return new InviteType();
    }

    try {
      return new Parser().parseInvite(inviteStr);
    } catch (WebdavException we) {
      throw new CalFacadeException(we);
    }
  }

  @Override
  public void delete(final BwCalendar col) throws CalFacadeException {
    InviteType invite = getInviteStatus(col);

    for (UserType u: invite.getUsers()) {
      if (u.getInviteStatus().equals(Parser.inviteNoresponseTag)) {
        // An outstanding invitation
        BwPrincipal pr = caladdrToPrincipal(u.getHref());

        if (pr != null) {     // Unknown user
          NotificationType n = findInvite(pr, col.getPath());

          if (n != null) {
            //InviteNotificationType in = (InviteNotificationType)n.getNotification();

            /* Delete the notification */
            deleteInvite(pr, n);
          }
        }
      } else {
        /* Send a notification indicating we deleted/uninvited and remove their
         * alias.
         */
        String calAddr = principalToCaladdr(getPrincipal());

        InviteNotificationType in = deletedNotification(u.getHref(),
                                                        col.getPath(),
                                                        calAddr);
        NotificationType note = new NotificationType();

        note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
        note.setNotification(in);

        NotificationsI notify = getSvc().getNotificationsHandler();
        BwPrincipal pr = caladdrToPrincipal(u.getHref());

        notify.send(pr, note);
      }

        /* Now we need to remove the alias - in theory we shouldn't have any
         * but do this anyway to clean up */

      try {
        pushPrincipal(u.getHref());

        List<BwCalendar> cols = ((Calendars)getCols()).findUserAlias(col.getPath());

        if (!Util.isEmpty(cols)) {
          for (BwCalendar alias: cols) {
            getCols().delete(alias, false, true);
          }
        }
      } finally {
        popPrincipal();
      }
    }
  }

  @Override
  public void publish(final BwCalendar col) throws CalFacadeException {
    if (!col.getCanAlias()) {
      throw new CalFacadeForbidden("Cannot publish");
    }

    /* Set access to read for everybody */

    Acl acl = setAccess(col,
                        null,
                        WhoDefs.whoTypeAll,
                        col.getCurrentAccess().getAcl(),
                        true);  // Read access

    // Mark the collection as shared and published
    col.setQproperty(AppleServerTags.publishUrl, col.getPath());

    try {
      getCols().update(col);

      getSvc().changeAccess(col, acl.getAces(), true);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
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

    Acl acl = removeAccess(col.getCurrentAccess().getAcl(),
                           null,
                           WhoDefs.whoTypeAll);

    // Mark the collection as published
    col.removeQproperty(AppleServerTags.publishUrl);

    try {
      getCols().update(col);


      if (acl != null) {
        getSvc().changeAccess(col, acl.getAces(), true);
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public SubscribeResult subscribe(final String colPath,
                                   final String subscribedName) throws CalFacadeException {
    /* We must have at least read access to the published collection */

    BwCalendar publishedCol = getCols().get(colPath);
    SubscribeResult sr = new SubscribeResult();

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

    List<BwCalendar> aliases = findAlias(colPath);
    if (!Util.isEmpty(aliases)) {
      sr.path = aliases.get(0).getPath();
      sr.alreadySubscribed = true;

      return sr;
    }

    BwCalendar alias = new BwCalendar();

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
    BwCalendar alias = new BwCalendar();
    SubscribeResult sr = new SubscribeResult();

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

    refreshRate *= 60;

    alias.setRemoteId(remoteId);

    if (remotePw != null) {
      try {
        String pw = getSvc().getEncrypter().encrypt(remotePw);
        alias.setRemotePw(pw);
        alias.setPwNeedsEncrypt(false);
      } catch (CalFacadeException cfe) {
        throw cfe;
      } catch (Throwable t) {
        throw new CalFacadeException(t);
      }
    }

    sr.path = getCols().add(alias, getCols().getHome().getPath()).getPath();

    return sr;
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
    try {
      pushPrincipal(sharerHref);
      BwCalendar col = getCols().get(path);

      if (col == null) {
        // Bad hosturl?
        throw new CalFacadeForbidden(CalFacadeException.shareTargetNotFound);
      }

      /* See if we have an outstanding invite for this user */

      QName qn = new QName(AppleServerTags.inviteNotification.getNamespaceURI(),
                           AppleServerTags.inviteNotification.getLocalPart() +
                           reply.getInReplyTo());

      String pname = NamespaceAbbrevs.prefixed(qn);
      String xmlInvite = col.getProperty(pname);

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

      InviteType invite = getInviteStatus(col);
      UserType uentry = null;
      String invitee = getSvc().getDirectories().normalizeCua(reply.getHref());

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

      NotificationType note = new NotificationType();

      note.setDtstamp(new DtStamp(new DateTime(true)).getValue());
      note.setNotification(reply);

      getSvc().getNotificationsHandler().add(note);

      return reply.testAccepted();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
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
                                          final Holder<Acl> hacl,
                                          final String calAddr,
                                          final InviteType invite) throws CalFacadeException {
    Acl acl = hacl.value;

    if (acl == null) {
      return null;
    }

    String href = getSvc().getDirectories().normalizeCua(rem.getHref());

    UserType uentry = invite.finduser(href);

    if (uentry == null) {
      // Not in list of sharers
      return null;
    }

    invite.getUsers().remove(uentry);

    try {
      if (Util.isEmpty(acl.getAces())) {
        return null;
      }

      BwPrincipal pr = caladdrToPrincipal(href);

      acl = removeAccess(acl, pr.getAccount(), pr.getKind());

      if (acl == null) {
        // no change
        return null;
      }

      hacl.value = acl;

      return deletedNotification(href, col.getPath(), calAddr);
    } catch (AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  private InviteNotificationType deletedNotification(final String shareeHref,
                                                     final String sharedUrl,
                                                     final String sharerHref) throws CalFacadeException {
    InviteNotificationType in = new InviteNotificationType();

    in.setUid(Uid.getUid());
    in.setHref(shareeHref);
    in.setInviteStatus(removeStatus);
    // in.setAccess(xxx); <-- current access from sharing status?
    in.setHostUrl(shareeHref);

    OrganizerType org = new OrganizerType();
    org.setHref(sharerHref);

    in.setOrganizer(org);

    return in;
  }

  private NotificationProperties getNoteProps() throws CalFacadeException {
    return getSvc().getNotificationProperties();
  }

  private InviteNotificationType doSet(final BwCalendar col,
                                       final SetType s,
                                       final Holder<Acl> hacl,
                                       final String calAddr,
                                       final InviteType invite) throws CalFacadeException {
    final String href = getSvc().getDirectories().normalizeCua(s.getHref());

    final BwPrincipal pr = caladdrToPrincipal(href);

    final boolean principalExists = pr != null &&
            getUsers().getPrincipal(pr.getPrincipalRef()) != null;

    /*
       pr != null means this is potentially one of our users.

       If the principal doesn't exist and we handle outbound
       notifications then we should drop a notification into the
       global notification collection. Eventually the user will log in
       we hope - we can then turn that invite into a real local user
       invite,
     */

    UserType uentry = invite.finduser(href);

    if (!principalExists) {
      if (!getNoteProps().getOutboundEnabled()) {
        // Cannot invite - need to set a good response

        return null;
      }
    } else {
      if (uentry != null) {
        if (uentry.getInviteStatus().equals(Parser.inviteNoresponseTag)) {
          // Already an outstanding invitation
          final NotificationType n = findInvite(pr, col.getPath());

          if (n != null) {
            final InviteNotificationType in =
                    (InviteNotificationType)n.getNotification();

            if (in.getAccess().equals(s.getAccess())) {
              // In their collection - no need to resend.
              return null;
            }

            /* Delete the old notification - we're changing the access */
            deleteInvite(pr,n);
          }
        }
      }

      hacl.value = setAccess(col,
                             pr.getAccount(),
                             pr.getKind(),
                             hacl.value,
                             s.getAccess().testRead());
    }

    final InviteNotificationType in = new InviteNotificationType();

    in.setSharedType(InviteNotificationType.sharedTypeCalendar);
    in.setUid(Uid.getUid());
    in.setHref(href);
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

      uentry.setHref(href);
      uentry.setInviteStatus(in.getInviteStatus());
      uentry.setCommonName(s.getCommonName());
      uentry.setAccess(in.getAccess());
      uentry.setSummary(s.getSummary());

      invite.getUsers().add(uentry);
    }

    uentry.setExternalUser(!principalExists);

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

  private final static Collection<Privilege> allPrivs = new ArrayList<Privilege>();

  private final static Collection<Privilege> readPrivs = new ArrayList<Privilege>();

  private final static Collection<Privilege> readWritePrivs = new ArrayList<Privilege>();

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

  private Acl setAccess(final BwCalendar col,
                        final String whoHref,
                        final int whoKind,
                        final Acl theAcl,
                        final boolean forRead) throws CalFacadeException {
    try {
      Acl acl = theAcl;
      AceWho who = AceWho.getAceWho(whoHref, whoKind, false);

      Collection<Privilege> desiredPriv;

      if (forRead) {
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

      BwPrincipal owner = getUsers().getPrincipal(col.getOwnerHref());
      AceWho ownerWho = AceWho.getAceWho(owner.getAccount(), owner.getKind(), false);

      removed = acl.removeWho(ownerWho);

      if (removed != null) {
        acl = removed;
      }

      Collection<Ace> aces = new ArrayList<Ace>();
      aces.addAll(acl.getAces());

      aces.add(Ace.makeAce(who, desiredPriv, null));

      aces.add(Ace.makeAce(ownerWho, allPrivs, null));

      return new Acl(aces);
    } catch (AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  /* Return null for no change */
  private Acl removeAccess(final Acl acl,
                           final String href,
                           final int whoKind) throws CalFacadeException {
    try {
      AceWho who = AceWho.getAceWho(href, whoKind, false);

      Acl newAcl = acl.removeWho(who);

      return newAcl;
    } catch (AccessException ae) {
      throw new CalFacadeException(ae);
    }
  }

  private NotificationType findInvite(final BwPrincipal pr,
                                            final String href) throws CalFacadeException {
    List<NotificationType> ns =
        getSvc().getNotificationsHandler().getMatching(pr,
                                                       Parser.inviteNotificationTag);

    for (NotificationType n: ns) {
      InviteNotificationType in = (InviteNotificationType)n.getNotification();

      if (in.getHostUrl().equals(href)) {
        return n;
      }
    }

    return null;
  }

  private void deleteInvite(final BwPrincipal pr,
                            final NotificationType n) throws CalFacadeException {
    getSvc().getNotificationsHandler().remove(pr, n);
  }
}
