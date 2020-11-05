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
import org.bedework.caldav.server.CalDAVCollection;
import org.bedework.calfacade.BwCalendar;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.tagdefs.NamespaceAbbrevs;
import org.bedework.webdav.servlet.shared.WebdavException;

import java.util.List;

import javax.xml.namespace.QName;

/**
 *
 * @author douglm
 *
 */
public class BwCalDAVCollection extends CalDAVCollection<BwCalDAVCollection> {
  private final BwSysIntfImpl intf;

  private BwCalendar col;

  /**
   * @param intf the system interface
   * @param col the collection
   */
  BwCalDAVCollection(final BwSysIntfImpl intf,
                     final BwCalendar col) {
    this.intf = intf;
    this.col = col;
  }

  /* ====================================================================
   *                      Abstract method implementations
   * ==================================================================== */

  @Override
  public boolean getCanShare() {
    return getCol().getCanAlias();
  }

  @Override
  public boolean getCanPublish() {
    return getCol().getCanAlias();
  }

  @Override
  public boolean isAlias() {
    return getCol().getInternalAlias();
  }

  @Override
  public void setAliasUri(final String val) {
    getCol().setAliasUri(val);
  }

  @Override
  public String getAliasUri() {
    if (!isAlias()) {
      return null;
    }

    final String s = getCol().getInternalAliasPath();

    if (s != null) {
      return intf.getUrlHandler().prefix(Util.buildPath(true, s));
    }

    return getCol().getAliasUri();
  }

  @Override
  public void setRefreshRate(final int val) {
    getCol().setRefreshRate(Math.max(BwCalendar.minRefreshRateSeconds, val));
  }

  @Override
  public int getRefreshRate() {
    return getCol().getRefreshRate();
  }

  @Override
  public BwCalDAVCollection resolveAlias(final boolean resolveSubAlias) {
    if (!col.getInternalAlias()) {
      return this;
    }

    final BwCalendar c;
    try {
      c = intf.resolveAlias(col, resolveSubAlias);
    } catch (final WebdavException e) {
      throw new RuntimeException(e);
    }
    if (c == null) {
      return null;
    }

    return new BwCalDAVCollection(intf, c);
  }

  @Override
  public void setProperty(final QName name, final String val) {
    getCol().setProperty(NamespaceAbbrevs.prefixed(name), val);
  }

  @Override
  public String getProperty(final QName name) {
    return getCol().getProperty(NamespaceAbbrevs.prefixed(name));
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.server.CalDAVCollection#setCalType(int)
   */
  @Override
  public void setCalType(final int val) {
    getCol().setCalType(val);
  }

  @Override
  public int getCalType() {
    final int calType;
    BwCalendar c = getCol();

    if (isAlias()) {
      try {
        c = intf.resolveAlias(col, true);
      } catch (final WebdavException e) {
        throw new RuntimeException(e);
      }
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

    if (calType == BwCalendar.calTypePoll) {
      return CalDAVCollection.calTypeCalendarCollection;
    }

    if (calType == BwCalendar.calTypeTasks) {
      return CalDAVCollection.calTypeCalendarCollection;
    }

    return CalDAVCollection.calTypeUnknown;
  }

  @Override
  public boolean freebusyAllowed() {
    return getCol().getCollectionInfo().allowFreeBusy;
  }

  @Override
  public boolean getDeleted() {
    return getCol().getTombstoned();
  }

  @Override
  public boolean entitiesAllowed() {
    return getCol().getCollectionInfo().onlyCalEntities;
  }

  @Override
  public void setAffectsFreeBusy(final boolean val) {
    getCol().setAffectsFreeBusy(val);
  }

  @Override
  public boolean getAffectsFreeBusy() {
    return getCol().getAffectsFreeBusy();
  }

  @Override
  public void setTimezone(final String val) {
    getCol().setTimezone(val);
  }

  @Override
  public String getTimezone() {
    return getCol().getTimezone();
  }

  @Override
  public void setColor(final String val) {
    getCol().setColor(val);
  }

  @Override
  public String getColor() {
    return getCol().getColor();
  }

  @Override
  public void setSupportedComponents(final List<String> val)
          {
    getCol().setSupportedComponents(val);
  }

  @Override
  public List<String> getSupportedComponents() {
    return getCol().getSupportedComponents();
  }

  @Override
  public List<String> getVpollSupportedComponents() {
    return getCol().getVpollSupportedComponents();
  }

  @Override
  public void setRemoteId(final String val) {
    getCol().setRemoteId(val);
  }

  @Override
  public String getRemoteId() {
    return getCol().getRemoteId();
  }

  @Override
  public void setRemotePw(final String val) {
    getCol().setRemotePw(val);
  }

  @Override
  public String getRemotePw() {
    return getCol().getRemotePw();
  }

  @Override
  public void setSynchDeleteSuppressed(final boolean val)
          {
    getCol().setSynchDeleteSuppressed(val);
  }

  @Override
  public boolean getSynchDeleteSuppressed() {
    return getCol().getSynchDeleteSuppressed();
  }

  /* ====================================================================
   *                      Overrides
   * ==================================================================== */

  @Override
  public void setName(final String val) {
    getCol().setName(val);
  }

  @Override
  public String getName() {
    final String n = getCol().getName();

    if (!n.endsWith(BwCalendar.tombstonedSuffix)) {
      return n;
    }

    return n.substring(0, n.length() - BwCalendar.tombstonedSuffix.length());
  }

  @Override
  public void setDisplayName(final String val) {
    getCol().setSummary(val);
  }

  @Override
  public String getDisplayName() {
    return getCol().getSummary();
  }

  @Override
  public void setPath(final String val) {
    getCol().setPath(val);
  }

  @Override
  public String getPath() {
    final String p = getCol().getPath();

    if (!p.endsWith(BwCalendar.tombstonedSuffix)) {
      return p;
    }

    return p.substring(0, p.length() - BwCalendar.tombstonedSuffix.length());
  }

  @Override
  public void setParentPath(final String val) {
    getCol().setColPath(val);
  }

  @Override
  public String getParentPath() {
    return getCol().getColPath();
  }

  @Override
  public void setOwner(final AccessPrincipal val) {
    getCol().setOwnerHref(val.getPrincipalRef());
  }

  @Override
  public AccessPrincipal getOwner() {
    try {
      return intf.getPrincipal(getCol().getOwnerHref());
    } catch (final WebdavException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void setCreated(final String val) {
    getCol().setCreated(val);
  }

  @Override
  public String getCreated() {
    return getCol().getCreated();
  }

  @Override
  public void setLastmod(final String val) {
    getCol().getLastmod().setTimestamp(val);
  }

  @Override
  public String getLastmod() {
    return getCol().getLastmod().getTimestamp();
  }

  @Override
  public String getEtag() {
    return getCol().getEtag();
  }

  @Override
  public String getPreviousEtag() {
    return "\"" + getCol().getLastEtag() +
           "\"";
  }

  @Override
  public void setDescription(final String val) {
    getCol().setDescription(val);
  }

  @Override
  public String getDescription() {
    return getCol().getDescription();
  }

  /* ====================================================================
   *                      Private methods
   * ==================================================================== */

  BwCalendar getCol() {
    if (col == null) {
      col = new BwCalendar();
    }

    return col;
  }
}
