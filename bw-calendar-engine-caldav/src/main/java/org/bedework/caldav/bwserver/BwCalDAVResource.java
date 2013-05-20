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

import org.bedework.caldav.server.CalDAVResource;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.NotificationType.NotificationInfo;
import org.bedework.caldav.util.notifications.parse.Parser;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cct.webdav.servlet.shared.WebdavForbidden;
import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.sss.util.Util;
import edu.rpi.sss.util.xml.tagdefs.CaldavTags;
import edu.rpi.sss.util.xml.tagdefs.WebdavTags;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.sql.Blob;

import javax.xml.namespace.QName;

/**
 *
 * @author douglm
 *
 */
public class BwCalDAVResource extends CalDAVResource<BwCalDAVResource> {
  private BwSysIntfImpl intf;

  private BwResource rsrc;

  private NotificationType note;
  private String xmlNote;

  /**
   * @param intf
   * @param rsrc
   * @throws WebdavException
   */
  BwCalDAVResource(final BwSysIntfImpl intf,
                   final BwResource rsrc) throws WebdavException {
    this.intf = intf;
    this.rsrc = rsrc;

    rsrc.setPrevLastmod(rsrc.getPrevLastmod());
    rsrc.setPrevSeq(rsrc.getPrevSeq());
  }

  /* ====================================================================
   *                      Abstract method implementations
   * ==================================================================== */

  @Override
  public boolean getCanShare() throws WebdavException {
    return false;
  }

  @Override
  public boolean getCanPublish() throws WebdavException {
    return false;
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#isAlias()
   */
  @Override
  public boolean isAlias() throws WebdavException {
    return false;
  }

  @Override
  public String getAliasUri() throws WebdavException {
    return null;
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#resolveAlias(boolean)
   */
  @Override
  public BwCalDAVResource resolveAlias(final boolean resolveSubAlias) throws WebdavException {
    return this;
  }

  @Override
  public void setProperty(final QName name,
                          final String val) throws WebdavException {
  }

  @Override
  public String getProperty(final QName name) throws WebdavException {
    return null;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVEvent#getNewEvent()
   */
  @Override
  public boolean isNew() throws WebdavException {
    return getRsrc().unsaved();
  }

  @Override
  public boolean getDeleted() throws WebdavException {
    return getRsrc().getTombstoned();
  }

  @Override
  public void setBinaryContent(final InputStream val) throws WebdavException {
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
      InputStream str;

      if (!isNotification()) {
        str = val;
      } else {
        NotificationType note = Parser.fromXml(val);

        note.getNotification().unprefixHrefs(intf.getUrlHandler());

        str = new ByteArrayInputStream(note.toXml().getBytes());
      }

      ByteArrayOutputStream outBuff = new ByteArrayOutputStream();
      byte[] inBuffer = new byte[1000];
      long clen = 0;
      int chunkSize;
      int maxSize = intf.getSystemProperties().getMaxUserEntitySize();

      long oldSize = r.getContentLength();

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

      rc.setContent(outBuff.toByteArray());
      r.setContentLength(clen);

      if (!intf.updateQuota(getOwner(),
                            clen - oldSize)) {
        throw new WebdavForbidden(WebdavTags.quotaNotExceeded);
      }

    } catch (WebdavException wde) {
      throw wde;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#getBinaryContent()
   */
  @Override
  public InputStream getBinaryContent() throws WebdavException {
    if (!isNotification()) {
      return getBinaryStream();
    }

    if (getNotification() == null) {
      return null;
    }

    return new ByteArrayInputStream(xmlNote.getBytes());
  }

  private InputStream getBinaryStream() throws WebdavException {
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

    Blob b = bwrc.getValue();

    if (b == null) {
      return null;
    }

    try {
      return b.getBinaryStream();
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private NotificationType getNotification() throws WebdavException {
    try {
      note = Parser.fromXml(getBinaryStream());

      if ((note == null) || (note.getNotification() == null)) {
        return null;
      }

      note.getNotification().prefixHrefs(intf.getUrlHandler());

      xmlNote = note.toXml();

      return note;
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#getContentLen()
   */
  @Override
  public long getContentLen() throws WebdavException {
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
  public long getQuotaSize() throws WebdavException {
    if (rsrc == null) {
      return 0;
    }

    return rsrc.getContentLength();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#setContentType(java.lang.String)
   */
  @Override
  public void setContentType(final String val) throws WebdavException {
    getRsrc().setContentType(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVResource#getContentType()
   */
  @Override
  public String getContentType() throws WebdavException {
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
  public NotificationInfo getNotificationType() throws WebdavException {
    return NotificationType.fromContentType(rsrc.getContentType());
  }

  /* ====================================================================
   *                      Overrides
   * ==================================================================== */

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setName(java.lang.String)
   */
  @Override
  public void setName(final String val) throws WebdavException {
    getRsrc().setName(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getName()
   */
  @Override
  public String getName() throws WebdavException {
    String n = getRsrc().getName();

    if (!n.endsWith(BwResource.tombstonedSuffix)) {
      return n;
    }

    return n.substring(0, n.length() - BwResource.tombstonedSuffix.length());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setDisplayName(java.lang.String)
   */
  @Override
  public void setDisplayName(final String val) throws WebdavException {
    // No display name
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#getDisplayName()
   */
  @Override
  public String getDisplayName() throws WebdavException {
    return getRsrc().getName();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setPath(java.lang.String)
   */
  @Override
  public void setPath(final String val) throws WebdavException {
    // Not actually saved
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getPath()
   */
  @Override
  public String getPath() throws WebdavException {
    return Util.buildPath(false, getRsrc().getColPath(), "/", getRsrc().getName());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setParentPath(java.lang.String)
   */
  @Override
  public void setParentPath(final String val) throws WebdavException {
    getRsrc().setColPath(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getParentPath()
   */
  @Override
  public String getParentPath() throws WebdavException {
    return getRsrc().getColPath();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setOwner(edu.rpi.cmt.access.AccessPrincipal)
   */
  @Override
  public void setOwner(final AccessPrincipal val) throws WebdavException {
    super.setOwner(val);
    getRsrc().setOwnerHref(val.getPrincipalRef());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getOwner()
   */
  @Override
  public AccessPrincipal getOwner() throws WebdavException {
    return intf.getPrincipal(getRsrc().getOwnerHref());
  }

  @Override
  public void setCreated(final String val) throws WebdavException {
    getRsrc().setCreated(val);
  }

  @Override
  public String getCreated() throws WebdavException {
    return getRsrc().getCreated();
  }

  @Override
  public void setLastmod(final String val) throws WebdavException {
    getRsrc().setLastmod(val);
  }

  @Override
  public String getLastmod() throws WebdavException {
    return getRsrc().getLastmod();
  }

  @Override
  public String getEtag() throws WebdavException {
    return getRsrc().getEtag();
  }

  @Override
  public String getPreviousEtag() throws WebdavException {
    return getRsrc().getPreviousEtag();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#setDescription(java.lang.String)
   */
  @Override
  public void setDescription(final String val) throws WebdavException {
    // No description
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#getDescription()
   */
  @Override
  public String getDescription() throws WebdavException {
    return getRsrc().getName();
  }

  /* ====================================================================
   *                      Package methods
   * ==================================================================== */

  BwResource getRsrc() throws WebdavException {
    if (rsrc == null) {
      rsrc = new BwResource();
    }

    return rsrc;
  }

  /* ====================================================================
   *                      Private methods
   * ==================================================================== */

  private boolean isNotification() throws WebdavException {
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
