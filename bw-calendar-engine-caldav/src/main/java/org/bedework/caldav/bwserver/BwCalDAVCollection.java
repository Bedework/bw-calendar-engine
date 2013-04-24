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

import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.calfacade.BwCalendar;

import edu.rpi.cct.webdav.servlet.shared.WebdavException;
import edu.rpi.cmt.access.AccessPrincipal;
import edu.rpi.sss.util.Util;
import edu.rpi.sss.util.xml.tagdefs.NamespaceAbbrevs;

import java.util.List;

import javax.xml.namespace.QName;

/**
 *
 * @author douglm
 *
 */
public class BwCalDAVCollection extends CalDAVCollection<BwCalDAVCollection> {
  private BwSysIntfImpl intf;

  private BwCalendar col;

  /**
   * @param intf
   * @param col
   * @throws WebdavException
   */
  BwCalDAVCollection(final BwSysIntfImpl intf,
                     final BwCalendar col) throws WebdavException {
    this.intf = intf;
    this.col = col;
  }

  /* ====================================================================
   *                      Abstract method implementations
   * ==================================================================== */

  @Override
  public boolean getCanShare() throws WebdavException {
    return getCol().getCanAlias();
  }

  @Override
  public boolean getCanPublish() throws WebdavException {
    return getCol().getCanAlias();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#isAlias()
   */
  @Override
  public boolean isAlias() throws WebdavException {
    return getCol().getInternalAlias();
  }

  @Override
  public String getAliasUri() throws WebdavException {
    if (!isAlias()) {
      return null;
    }

    String s = getCol().getInternalAliasPath();

    if (s != null) {
      return intf.getUrlHandler().prefix(Util.buildPath(true, s));
    }

    return getCol().getAliasUri();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdEntity#getAliasTarget()
   */
  @Override
  public BwCalDAVCollection resolveAlias(final boolean resolveSubAlias) throws WebdavException {
    if (!col.getAlias()) {
      return this;
    }

    BwCalendar c = intf.resolveAlias(col, true);
    if (c == null) {
      return null;
    }

    return new BwCalDAVCollection(intf, c);
  }

  @Override
  public void setProperty(final QName name, final String val) throws WebdavException {
    getCol().setProperty(NamespaceAbbrevs.prefixed(name), val);
  }

  @Override
  public String getProperty(final QName name) throws WebdavException {
    return getCol().getProperty(NamespaceAbbrevs.prefixed(name));
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#setCalType(int)
   */
  @Override
  public void setCalType(final int val) throws WebdavException {
    getCol().setCalType(val);
  }

  @Override
  public int getCalType() throws WebdavException {
    int calType;
    BwCalendar c = getCol();

    if (isAlias()) {
      c = intf.resolveAlias(col, true);
    }

    if (c == null) {
      return CalDAVCollection.calTypeUnknown;
    }

    calType = c.getCalType();

    if (calType == BwCalendar.calTypeFolder) {
      // Broken alias
      return CalDAVCollection.calTypeCollection;
    }

    if ((calType == BwCalendar.calTypeCalendarCollection) ||
        (calType == BwCalendar.calTypeEventList) ||
        (calType == BwCalendar.calTypeExtSub)) {
      // Broken alias
      return CalDAVCollection.calTypeCalendarCollection;
    }

    if (calType == BwCalendar.calTypeInbox) {
      // Broken alias
      return CalDAVCollection.calTypeInbox;
    }

    if (calType == BwCalendar.calTypeOutbox) {
      // Broken alias
      return CalDAVCollection.calTypeOutbox;
    }

    if (calType == BwCalendar.calTypeNotifications) {
      return CalDAVCollection.calTypeNotifications;
    }

    return CalDAVCollection.calTypeUnknown;
  }

  @Override
  public boolean freebusyAllowed() throws WebdavException {
    return getCol().getCollectionInfo().allowFreeBusy;
  }

  @Override
  public boolean getDeleted() throws WebdavException {
    return getCol().getTombstoned();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#entitiesAllowed()
   */
  @Override
  public boolean entitiesAllowed() throws WebdavException {
    return getCol().getCollectionInfo().entitiesAllowed;
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#setAffectsFreeBusy(boolean)
   */
  @Override
  public void setAffectsFreeBusy(final boolean val) throws WebdavException {
    getCol().setAffectsFreeBusy(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#getAffectsFreeBusy()
   */
  @Override
  public boolean getAffectsFreeBusy() throws WebdavException {
    return getCol().getAffectsFreeBusy();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#setTimezone(java.lang.String)
   */
  @Override
  public void setTimezone(final String val) throws WebdavException {
    getCol().setTimezone(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#getTimezone()
   */
  @Override
  public String getTimezone() throws WebdavException {
    return getCol().getTimezone();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#setColor(java.lang.String)
   */
  @Override
  public void setColor(final String val) throws WebdavException {
    getCol().setColor(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#getColor()
   */
  @Override
  public String getColor() throws WebdavException {
    return getCol().getColor();
  }

  @Override
  public List<String> getSupportedComponents() throws WebdavException {
    return getCol().getSupportedComponents();
  }

  /* ====================================================================
   *                      Overrides
   * ==================================================================== */

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setName(java.lang.String)
   */
  @Override
  public void setName(final String val) throws WebdavException {
    getCol().setName(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getName()
   */
  @Override
  public String getName() throws WebdavException {
    String n = getCol().getName();

    if (!n.endsWith(BwCalendar.tombstonedSuffix)) {
      return n;
    }

    return n.substring(0, n.length() - BwCalendar.tombstonedSuffix.length());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setDisplayName(java.lang.String)
   */
  @Override
  public void setDisplayName(final String val) throws WebdavException {
    getCol().setSummary(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getDisplayName()
   */
  @Override
  public String getDisplayName() throws WebdavException {
    return getCol().getSummary();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setPath(java.lang.String)
   */
  @Override
  public void setPath(final String val) throws WebdavException {
    getCol().setPath(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getPath()
   */
  @Override
  public String getPath() throws WebdavException {
    String p = getCol().getPath();

    if (!p.endsWith(BwCalendar.tombstonedSuffix)) {
      return p;
    }

    return p.substring(0, p.length() - BwCalendar.tombstonedSuffix.length());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setParentPath(java.lang.String)
   */
  @Override
  public void setParentPath(final String val) throws WebdavException {
    getCol().setColPath(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getParentPath()
   */
  @Override
  public String getParentPath() throws WebdavException {
    return getCol().getColPath();
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setOwner(edu.rpi.cmt.access.AccessPrincipal)
   */
  @Override
  public void setOwner(final AccessPrincipal val) throws WebdavException {
    getCol().setOwnerHref(val.getPrincipalRef());
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getOwner()
   */
  @Override
  public AccessPrincipal getOwner() throws WebdavException {
    return intf.getPrincipal(getCol().getOwnerHref());
  }

  @Override
  public void setCreated(final String val) throws WebdavException {
    getCol().setCreated(val);
  }

  @Override
  public String getCreated() throws WebdavException {
    return getCol().getCreated();
  }

  @Override
  public void setLastmod(final String val) throws WebdavException {
    getCol().getLastmod().setTimestamp(val);
  }

  @Override
  public String getLastmod() throws WebdavException {
    return getCol().getLastmod().getTimestamp();
  }

  @Override
  public String getEtag() throws WebdavException {
    return getCol().getEtag();
  }

  @Override
  public String getPreviousEtag() throws WebdavException {
    return "\"" + getCol().getLastEtag() +
           "\"";
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#setDescription(java.lang.String)
   */
  @Override
  public void setDescription(final String val) throws WebdavException {
    getCol().setDescription(val);
  }

  /* (non-Javadoc)
   * @see edu.rpi.cct.webdav.servlet.shared.WdCollection#getDescription()
   */
  @Override
  public String getDescription() throws WebdavException {
    return getCol().getDescription();
  }

  /* ====================================================================
   *                      Private methods
   * ==================================================================== */

  BwCalendar getCol() throws WebdavException {
    if (col == null) {
      col = new BwCalendar();
    }

    return col;
  }
}
