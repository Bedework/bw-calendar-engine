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

import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.NotificationType.NotificationInfo;
import org.bedework.caldav.util.notifications.parse.Parser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calsvc.notifications.NotificationClient;
import org.bedework.calsvci.NotificationsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.util.misc.Util;

import java.io.InputStream;
import java.sql.Blob;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.xml.namespace.QName;

/** Handles bedework notifications - including CalDAV user notification
 * collections.
 *
 * <p>We treat the notifications as special resources. The content type will be
 * set to the serialized value of the QName for the notification. The stored
 * resource will be the XML for the notification.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Notifications extends CalSvcDb implements NotificationsI {
  private static NotificationClient notifyClient;

  /**
   * Constructor
   *
   * @param svci service interface
   */
  Notifications(final CalSvc svci) {
    super(svci);
  }

  @Override
  public boolean send(final BwPrincipal pr,
                      final NotificationType val)
          throws CalFacadeException {
    try {
      pushPrincipal(pr);
      return add(val);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      popPrincipal();
    }
  }

  @Override
  public boolean add(final NotificationType val)
          throws CalFacadeException {
    if ((val == null) ||
            (val.getNotification() == null) ||
            (val.getNotification().getElementName() == null)) {
      return false;
    }

    final BwCalendar ncol = getCols()
            .getSpecial(BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return false;
    }

    final BwResource noteRsrc = new BwResource();

    noteRsrc.setName(val.getName());
    noteRsrc.setEncoding(val.getNotification().getEncoding());

    final BwResourceContent rc = new BwResourceContent();
    noteRsrc.setContent(rc);

    try {
      final String xml = val.toXml(true);

      if (xml == null) {
        return false;
      }

      final byte[] xmlData = xml.getBytes();

      rc.setContent(xmlData);

      noteRsrc.setContentLength(xmlData.length);
      noteRsrc.setContentType(val.getContentType());
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }

    for (int i = 0; i <= 100; i++) {
      if (getSvc().getResourcesHandler().save(ncol.getPath(),
                                              noteRsrc,
                                              true)) {
        getNoteClient().informNotifier(getPrincipalHref());

        return true;
      }

      noteRsrc.setName(val.getName() + "-" + i);
    }

    throw new CalFacadeException(CalFacadeException.duplicateResource,
                                 val.getName());
  }

  @Override
  public boolean update(final NotificationType val)
          throws CalFacadeException {
    if ((val == null) ||
            (val.getNotification() == null) ||
            (val.getNotification().getElementName() == null)) {
      return false;
    }

    try {
      final String xml = val.toXml(true);

      if (xml == null) {
        return false;
      }

      final BwCalendar ncol = getCols()
              .getSpecial(BwCalendar.calTypeNotifications,
                          true);

      if (ncol == null) {
        return false;
      }

      final BwResource noteRsrc =
              getSvc().getResourcesHandler().get(Util.buildPath(false,
                                                                ncol.getPath(),
                                                                "/",
                                                                val.getName()));

      if (noteRsrc == null) {
        return false;
      }

      BwResourceContent rc = noteRsrc.getContent();
      if (rc == null) {
        rc = new BwResourceContent();
        noteRsrc.setContent(rc);
      }

      final byte[] xmlData = xml.getBytes();

      rc.setContent(xmlData);

      noteRsrc.setContentLength(xmlData.length);
      noteRsrc.setContentType(val.getContentType());

      getSvc().getResourcesHandler().update(noteRsrc, true);
      getNoteClient().informNotifier(getPrincipalHref());

      return true;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public NotificationType find(final String name)
          throws CalFacadeException {
    final BwCalendar ncol = getCols()
            .getSpecial(BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return null;
    }

    final BwResource noteRsrc =
            getSvc().getResourcesHandler()
                    .get(Util.buildPath(false, ncol.getPath(),
                                        "/", name));

    if (noteRsrc == null) {
      return null;
    }

    return makeNotification(noteRsrc);
  }

  @Override
  public NotificationType find(final String principalHref,
                               final String name)
          throws CalFacadeException {
    final BwCalendar ncol = getCols()
            .getSpecial(principalHref,
                        BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return null;
    }

    final BwResource noteRsrc =
            getSvc().getResourcesHandler()
                    .get(Util.buildPath(false, ncol.getPath(),
                                        "/", name));

    if (noteRsrc == null) {
      return null;
    }

    return makeNotification(noteRsrc);
  }

  @Override
  public void remove(final NotificationType val)
          throws CalFacadeException {
    if ((val == null) ||
            (val.getNotification() == null) ||
            (val.getNotification().getElementName() == null)) {
      return;
    }

    final BwCalendar ncol = getCols()
            .getSpecial(BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return;
    }

    final String path = Util
            .buildPath(false, ncol.getPath(), "/", val.getName());

    getSvc().getResourcesHandler().delete(path);
  }

  @Override
  public void remove(final String principalHref,
                     final NotificationType val)
          throws CalFacadeException {
    if ((val == null) ||
            (val.getNotification() == null) ||
            (val.getNotification().getElementName() == null)) {
      return;
    }

    final BwCalendar ncol = getCols()
            .getSpecial(principalHref,
                        BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return;
    }

    final String path = Util
            .buildPath(false, ncol.getPath(), "/", val.getName());

    getSvc().getResourcesHandler().delete(path);
  }

  @Override
  public void removeAll(final String principalHref)
          throws CalFacadeException {
    if (principalHref == null) {
      return;
    }

    final BwCalendar ncol = getCols()
            .getSpecial(principalHref,
                        BwCalendar.calTypeNotifications,
                        true);

    if (ncol == null) {
      return;
    }

    /* Remove resources */
    final ResourcesI resI = getSvc().getResourcesHandler();
    final Collection<BwResource> rs = resI.getAll(ncol.getPath());
    if (!Util.isEmpty(rs)) {
      for (final BwResource r : rs) {
        resI.delete(Util.buildPath(false, r.getColPath(), "/",
                                   r.getName()));
      }
    }
  }

  @Override
  public List<NotificationType> getAll() throws CalFacadeException {
    return getMatching(null);
  }

  @Override
  public List<NotificationType> getMatching(final QName type)
          throws CalFacadeException {
    final List<NotificationType> res = new ArrayList<>();

    final BwCalendar ncol =
            getCols().getSpecial(BwCalendar.calTypeNotifications,
                                 true);

    if (ncol == null) {
      return res;
    }

    final Collection<BwResource> rsrc =
            getSvc().getResourcesHandler().getAll(ncol.getPath());

    if (Util.isEmpty(rsrc)) {
      return res;
    }

    for (final BwResource r : rsrc) {
      if (type != null) {
        final NotificationInfo ni = NotificationType
                .fromContentType(r.getContentType());
        if ((ni == null) || !type.equals(ni.type)) {
          continue;
        }
      }

      final NotificationType nt = makeNotification(r);
      if (nt != null) {
        res.add(nt);
      }
    }

    return res;
  }

  @Override
  public List<NotificationType> getMatching(final BwPrincipal pr,
                                            final QName type)
          throws CalFacadeException {
    try {
      pushPrincipal(pr);
      return getMatching(type);
    } finally {
      popPrincipal();
    }
  }

  @Override
  public List<NotificationType> getMatching(final String href,
                                            final QName type)
          throws CalFacadeException {
    final BwPrincipal pr = getSvc().getDirectories()
            .caladdrToPrincipal(href);

    if (pr == null) {
      return null;
    }

    return getMatching(pr, type);
  }

  @Override
  public void subscribe(final String principalHref,
                        final List<String> emails)
          throws CalFacadeException {
    try {
      pushPrincipal(principalHref);
      final BwPreferences prefs = getSvc().getPrefsHandler().get();

      prefs.setNotificationToken(UUID.randomUUID().toString());
      getNoteClient().subscribe(principalHref, emails,
                                prefs.getNotificationToken());
    } finally {
      popPrincipal();
    }
  }

  @Override
  public void unsubscribe(final String principalHref,
                          final List<String> emails)
          throws CalFacadeException {
    try {
      pushPrincipal(principalHref);
      getNoteClient().unsubscribe(principalHref, emails);
    } finally {
      popPrincipal();
    }
  }

  void remove(final BwPrincipal pr,
              final NotificationType val)
          throws CalFacadeException {
    try {
      pushPrincipal(pr);
      remove(val);
    } finally {
      popPrincipal();
    }
  }

  private NotificationType makeNotification(final BwResource rsrc)
          throws CalFacadeException {
    getSvc().getResourcesHandler().getContent(rsrc);

    final BwResourceContent bwrc = rsrc.getContent();

    if (bwrc == null) {
      return null;
    }

    final Blob b = bwrc.getValue();

    if (b == null) {
      return null;
    }

    try {
      final InputStream is = b.getBinaryStream();

      final NotificationType note = Parser.fromXml(is);

      if (note != null) {
        note.setName(rsrc.getName());
        note.getNotification().setEncoding(rsrc.getEncoding());
      }

      return note;
    } catch (final Throwable t) {
      if (debug) {
        error(t);
      }
      error("Unable to parse notification " + rsrc.getColPath() +
                    " " + rsrc.getName());
      return null;
    }
  }

  private synchronized NotificationClient getNoteClient() throws CalFacadeException {
    if (notifyClient != null) {
      return notifyClient;
    }

    notifyClient = new NotificationClient(getSvc().getNotificationProperties());

    return notifyClient;
  }
}