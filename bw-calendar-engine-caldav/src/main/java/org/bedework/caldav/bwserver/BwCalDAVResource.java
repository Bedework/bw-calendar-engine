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
package org.bedework.caldav.bwserver;

import org.bedework.access.AccessPrincipal;
import org.bedework.caldav.server.CalDAVResource;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.NotificationType.NotificationInfo;
import org.bedework.caldav.util.notifications.parse.Parser;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.CaldavTags;
import org.bedework.util.xml.tagdefs.WebdavTags;
import org.bedework.webdav.servlet.shared.WebdavException;
import org.bedework.webdav.servlet.shared.WebdavForbidden;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.xml.namespace.QName;

/**
 *
 * @author douglm
 *
 */
public class BwCalDAVResource extends CalDAVResource<BwCalDAVResource> {
  private final BwSysIntfImpl intf;

  private BwResource rsrc;

  private NotificationType note;
  private String xmlNote;

  /**
   * @param intf
   * @param rsrc
   */
  BwCalDAVResource(final BwSysIntfImpl intf,
                   final BwResource rsrc) {
    this.intf = intf;
    this.rsrc = rsrc;

    if (rsrc != null) {
      rsrc.setPrevLastmod(rsrc.getPrevLastmod());
      rsrc.setPrevSeq(rsrc.getPrevSeq());
    }
  }

  /* ====================================================================
   *                      Abstract method implementations
   * ==================================================================== */

  @Override
  public boolean getCanShare() {
    return false;
  }

  @Override
  public boolean getCanPublish() {
    return false;
  }

  @Override
  public boolean isAlias() {
    return false;
  }

  @Override
  public String getAliasUri() {
    return null;
  }

  @Override
  public BwCalDAVResource resolveAlias(final boolean resolveSubAlias) {
    return this;
  }

  @Override
  public void setProperty(final QName name,
                          final String val) {
  }

  @Override
  public String getProperty(final QName name) {
    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVEvent#getNewEvent()
   */
  @Override
  public boolean isNew() {
    return getRsrc().unsaved();
  }

  @Override
  public boolean getDeleted() {
    return getRsrc().getTombstoned();
  }

  @Override
  public void setBinaryContent(final InputStream val) {
    BwResource r = getRsrc();

    BwResourceContent rc = r.getContent();

    if (rc == null) {
      if (!isNew()) {
        intf.getFileContent(this);
        rc = r.getContent();
      }

      if (rc == null) {
        rc = new BwResourceContent();
        r.setContent(rc);
      }
    }

    try {
      /* If this is a notification we need to unprefix the data */
      final InputStream str;

      if (!isNotification()) {
        str = val;
      } else {
        final NotificationType note = Parser.fromXml(val);

        note.getNotification().unprefixHrefs(intf.getUrlHandler());

        str = new ByteArrayInputStream(note.toXml(true).getBytes());
      }

      final ByteArrayOutputStream outBuff = new ByteArrayOutputStream();
      final byte[] inBuffer = new byte[1000];
      long clen = 0;
      int chunkSize;
      final int maxSize = intf.getAuthProperties().getMaxUserEntitySize();

      final long oldSize = r.getContentLength();

      while (clen <= maxSize) {
        chunkSize = str.read(inBuffer);

        if (chunkSize < 0) {
          break;
        }

        outBuff.write(inBuffer, 0, chunkSize);
        clen += chunkSize;
      }

      if (clen > maxSize) {
        throw new WebdavForbidden(CaldavTags.maxResourceSize);
      }
      
      rc.setByteValue(outBuff.toByteArray());
      r.setContentLength(clen);

      if (!intf.updateQuota(getOwner(),
                            clen - oldSize)) {
        throw new WebdavForbidden(WebdavTags.quotaNotExceeded);
      }

    } catch (final Throwable t) {
      throw new WebdavException(t);
    }
  }

  @Override
  public InputStream getBinaryContent() {
    if (!isNotification()) {
      return getBinaryStream();
    }

    if (getNotification() == null) {
      return null;
    }

    return new ByteArrayInputStream(xmlNote.getBytes());
  }

  private InputStream getBinaryStream() {
    if (rsrc == null) {
      return null;
    }

    if (rsrc.getContent() == null) {
      intf.getFileContent(this);
    }

    BwResourceContent bwrc = rsrc.getContent();

    if (bwrc == null) {
      return null;
    }

    try {
      return bwrc.getBinaryStream();
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private NotificationType getNotification() {
    try {
      note = Parser.fromXml(getBinaryStream());

      if ((note == null) || (note.getNotification() == null)) {
        return null;
      }

      note.getNotification().prefixHrefs(intf.getUrlHandler());

      xmlNote = note.toXml(intf.bedeworkExtensionsEnabled());

      return note;
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#getContentLen()
   */
  @Override
  public long getContentLen() {
    if (rsrc == null) {
      return 0;
    }

    if (!isNotification()) {
      return rsrc.getContentLength();
    }

    if (getNotification() == null) {
      return 0;
    }

    return xmlNote.getBytes().length;
  }

  @Override
  public long getQuotaSize() {
    if (rsrc == null) {
      return 0;
    }

    return rsrc.getContentLength();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#setContentType(java.lang.String)
   */
  @Override
  public void setContentType(final String val) {
    getRsrc().setContentType(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#getContentType()
   */
  @Override
  public String getContentType() {
    if (rsrc == null) {
      return null;
    }

    String s = rsrc.getContentTypeStripped();

    if (s == null) {
      return null;
    }

    if (!NotificationType.isNotificationContentType(s)) {
      return s;
    }

    return "application/xml";
  }

  @Override
  public NotificationInfo getNotificationType() {
    return NotificationType.fromContentType(rsrc.getContentType());
  }

  /* ====================================================================
   *                      Overrides
   * ==================================================================== */

  @Override
  public void setName(final String val) {
    getRsrc().setName(val);
  }

  @Override
  public String getName() {
    String n = getRsrc().getName();

    if (!n.endsWith(BwResource.tombstonedSuffix)) {
      return n;
    }

    return n.substring(0, n.length() - BwResource.tombstonedSuffix.length());
  }

  @Override
  public void setDisplayName(final String val) {
    // No display name
  }

  @Override
  public String getDisplayName() {
    return getRsrc().getName();
  }

  @Override
  public void setPath(final String val) {
    // Not actually saved
  }

  @Override
  public String getPath() {
    return Util.buildPath(false, getRsrc().getColPath(), "/",
                          getRsrc().getName());
  }

  @Override
  public void setParentPath(final String val) {
    getRsrc().setColPath(val);
  }

  @Override
  public String getParentPath() {
    return getRsrc().getColPath();
  }

  @Override
  public void setOwner(final AccessPrincipal val) {
    super.setOwner(val);
    getRsrc().setOwnerHref(val.getPrincipalRef());
  }

  @Override
  public AccessPrincipal getOwner() {
    try {
      return intf.getPrincipal(getRsrc().getOwnerHref());
    } catch (WebdavException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setCreated(final String val) {
    getRsrc().setCreated(val);
  }

  @Override
  public String getCreated() {
    return getRsrc().getCreated();
  }

  @Override
  public void setLastmod(final String val) {
    getRsrc().setLastmod(val);
  }

  @Override
  public String getLastmod() {
    return getRsrc().getLastmod();
  }

  @Override
  public String getEtag() {
    return getRsrc().getEtag();
  }

  @Override
  public String getPreviousEtag() {
    return getRsrc().getPreviousEtag();
  }

  @Override
  public void setDescription(final String val) {
    // No description
  }

  @Override
  public String getDescription() {
    return getRsrc().getName();
  }

  /* ====================================================================
   *                      Package methods
   * ==================================================================== */

  BwResource getRsrc() {
    if (rsrc == null) {
      rsrc = new BwResource();
    }

    return rsrc;
  }

  /* ====================================================================
   *                      Private methods
   * ==================================================================== */

  private boolean isNotification() {
    if (rsrc == null) {
      return false;
    }

    String s = rsrc.getContentTypeStripped();

    if (s == null) {
      return false;
    }

    return NotificationType.isNotificationContentType(s);
  }
}
