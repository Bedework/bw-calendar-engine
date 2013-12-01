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
package org.bedework.calfacade.filter;

import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwLongString;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.webdav.servlet.shared.WebdavException;

/** A filter that wraps CalDAV object filter.
 *
 * <p>This allows us to implement the match method so we can post-process
 * annotations and overrides etc. The entity is the object filter we are
 * wrapping.
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class BwObjectFilter extends ObjectFilter<ObjectFilter> {
  /** Match on any of the categories.
   *
   * @param name - null one will be created
   * @param of
   */
  public BwObjectFilter(final String name,
                        final ObjectFilter of) {
    super(name, PropertyInfoIndex.CATEGORIES);

    setEntity(of);
    setPropertyIndex(of.getPropertyIndex());
    setParentPropertyIndex(of.getParentPropertyIndex());
    setExact(of.getExact());
    setNot(of.getNot());
    setCaseless(of.getCaseless());
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.util.filter.PropertyFilter#getPropertyIndex()
   */
  @Override
  public PropertyInfoIndex getPropertyIndex() {
    return getEntity().getPropertyIndex();
  }

  /* (non-Javadoc)
   * @see org.bedework.caldav.util.filter.PropertyFilter#getParentPropertyIndex()
   */
  @Override
  public PropertyInfoIndex getParentPropertyIndex() {
    return getEntity().getParentPropertyIndex();
  }

  /* ====================================================================
   *                   matching methods
   * ==================================================================== */

  @Override
  public boolean match(final Object o,
                       final String userHref) throws WebdavException {
    ObjectFilter of = getEntity();
    Object ent = of.getEntity();
    boolean not = of.getNot();

    String val = null;
    Integer ival = null;

    if (ent instanceof BwCalendar) {
      val = ((BwCalendar)ent).getPath();
    } else if (ent instanceof String) {
      val = (String)ent;
    } else if (ent instanceof Integer) {
      ival = (Integer)ent;
    } else {
      throw new WebdavException("Unmatchable filter");
    }

    BwEvent ev = null;

    if (o instanceof BwEvent) {
      ev = (BwEvent)o;
    }

    if (of instanceof EntityTypeFilter) {
      if (ev == null) {
        return false;
      }

      if (not) {
        return ev.getEntityType() != ival;
      }

      return ev.getEntityType() == ival;
    }

    PropertyInfoIndex pii = of.getPropertyIndex();
    BwIcalPropertyInfoEntry pi = BwIcalPropertyInfo.getPinfo(pii);

    if (pi.getParam()) {
      pii = of.getParentPropertyIndex();
    }

    switch (pii) {
    case CLASS:
      if (ev.getClassification() == null) {
        return false;
      }
      return stringMatch(ev.getClassification(), val);

    case CREATED:
      return stringMatch(ev.getCreated(), val);

    case DESCRIPTION:
      for (BwLongString ls: ev.getDescriptions()) {
        if (stringMatch(ls.getValue(), val)) {
          return true;
        }
      }

      return false;

    case DTSTAMP:
      return stringMatch(ev.getDtstamp(), val);

    case DTEND: /* Event only */
    case DUE: /* Todo only */
      return matchDateTime(pi, ev.getDtend(), val);

    case DTSTART:
      return matchDateTime(pi, ev.getDtstart(), val);

    case DURATION:
      return stringMatch(ev.getDuration(), val);

    case GEO:
      if (ev.getGeo() == null) {
        return false;
      }
      return stringMatch(ev.getGeo().toString(), val);

    case LAST_MODIFIED:
      return stringMatch(ev.getLastmod(), val);

    case LOCATION:
      if (ev.getLocation() == null) {
        return false;
      }
      return stringMatch(ev.getLocation().getAddress().getValue(), val);

    case ORGANIZER:
      if (ev.getOrganizer() == null) {
        return false;
      }
      return stringMatch(ev.getOrganizer().getOrganizerUri(), val);

    case PRIORITY:
      if (ev.getPriority() == null) {
        return false;
      }
      return stringMatch(String.valueOf(ev.getPriority()), val);

    case RECURRENCE_ID:
      if (ev.getRecurrenceId() == null) {
        return false;
      }
      return stringMatch(ev.getRecurrenceId(), val);

    case SEQUENCE:
      return stringMatch(String.valueOf(ev.getSequence()), val);

    case STATUS:
      if (ev.getStatus() == null) {
        return false;
      }
      return stringMatch(ev.getStatus(), val);

    case SUMMARY:
      for (BwString s: ev.getSummaries()) {
        if (stringMatch(s.getValue(), val)) {
          return true;
        }
      }

      return false;

    case UID:
      return stringMatch(ev.getUid(), val);

    case URL:
      if (ev.getLink() == null) {
        return false;
      }
      return stringMatch(ev.getLink(), val);

    case TRANSP:
      try {
        if (ev.getPeruserTransparency(userHref) == null) {
          return false;
        }
        return stringMatch(ev.getPeruserTransparency(userHref), val);
      } catch (Throwable t) {
        throw new WebdavException(t);
      }

    /* Todo only */

    case COMPLETED:
      if (ev.getCompleted() == null) {
        return false;
      }
      return stringMatch(ev.getCompleted(), val);

    case PERCENT_COMPLETE:
      if (ev.getPercentComplete() == null) {
        return false;
      }
      return stringMatch(String.valueOf(ev.getPercentComplete()), val);

    /* ---------------------------- Multi valued --------------- */

    /* Event and Todo */

    case ATTACH:
      break;

    case ATTENDEE :
      break;

    case CATEGORIES:
      for (BwCategory cat: ev.getCategories()) {
        if (stringMatch(cat.getWordVal(), val)) {
          return true;
        }
      }

      return false;

    case COMMENT:
      for (BwString s: ev.getComments()) {
        if (stringMatch(s.getValue(), val)) {
          return true;
        }
      }

      return false;

    case CONTACT:
      for (BwContact c: ev.getContacts()) {
        if (stringMatch(c.getCn().getValue(), val)) {
          return true;
        }
      }

      return false;

    case EXDATE:
      for (BwDateTime dt: ev.getExdates()) {
        if (stringMatch(dt.getDtval(), val)) {
          return true;
        }
      }

      return false;

    case EXRULE :
      for (String s: ev.getExrules()) {
        if (stringMatch(s, val)) {
          return true;
        }
      }

      return false;

    case REQUEST_STATUS:
      for (BwRequestStatus rs: ev.getRequestStatuses()) {
        if (stringMatch(rs.getCode(), val)) {
          return true;
        }
      }

      return false;

    case RELATED_TO:
      if (ev.getRelatedTo() == null) {
        return false;
      }

      return stringMatch(ev.getRelatedTo().getValue(), val);

    case RESOURCES:
      for (BwString s: ev.getResources()) {
        if (stringMatch(s.getValue(), val)) {
          return true;
        }
      }

      return false;

    case RDATE:
      for (BwDateTime dt: ev.getRdates()) {
        if (stringMatch(dt.getDtval(), val)) {
          return true;
        }
      }

      return false;

    case RRULE :
      for (String s: ev.getRrules()) {
        if (stringMatch(s, val)) {
          return true;
        }
      }

      return false;

    /* -------------- Other non-event: non-todo ---------------- */

    case FREEBUSY:
      break;

    case TZID:
      break;

    case TZNAME:
      break;

    case TZOFFSETFROM:
      break;

    case TZOFFSETTO:
      break;

    case TZURL:
      break;

    case ACTION:
      break;

    case REPEAT:
      break;

    case TRIGGER:
      break;

    case COLLECTION:
      return stringMatch(ev.getColPath(), val);

    case CREATOR:
      return stringMatch(ev.getCreatorHref(), val);

    case OWNER:
      return stringMatch(ev.getOwnerHref(), val);

    case ENTITY_TYPE:
      break;

    }

    return false;
  }

  private boolean matchDateTime(final BwIcalPropertyInfoEntry pi,
                                final BwDateTime dt,
                                final String val) {
    if (!pi.getParam()) {
      return false; // Dealt with by time range?
    }

    if (dt == null) {
      return false;
    }

    if (getPropertyIndex() != PropertyInfoIndex.TZIDPAR) {
      return false;
    }

    return stringMatch(dt.getTzid(), val);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("(");

    sb.append(getPropertyIndex());

    if (getExact()) {
      if (getNot()) {
        sb.append(" != ");
      } else {
        sb.append(" = ");
      }
    } else {
      if (getNot()) {
        sb.append(" not ");
      }

      sb.append(" like ");
    }

    sb.append(getEntity().getEntity());

    sb.append(")");

    return sb.toString();
  }

  private boolean stringMatch(final String fldVal,
                              final String val) {
    boolean matches;

    domatch: {
      if (getExact()) {
        if (getCaseless()) {
          matches = val.toLowerCase().equals(fldVal.toLowerCase());
          break domatch;
        }

        matches = val.equals(fldVal);
        break domatch;
      }

      if (getCaseless()) {
        matches = fldVal.toLowerCase().indexOf(val.toLowerCase()) >= 0;
        break domatch;
      }

      matches = val.indexOf(fldVal) >= 0;
    } // domatch

    if (getNot()) {
      return !matches;
    }

    return matches;
  }
}
