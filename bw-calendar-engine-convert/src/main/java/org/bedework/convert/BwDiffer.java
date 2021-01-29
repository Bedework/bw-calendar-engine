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
package org.bedework.convert;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.Differable;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Class to provide methods for comparing property values
 *
 * @author Mike Douglass
 */
public class BwDiffer {
  private final static BwLogger logger =
          new BwLogger().setLoggedClass(BwDiffer.class);

  static <T extends Comparable<T>,
          CT extends Collection<T>> DifferResult<T, CT> diffres(
          @SuppressWarnings("unused") final Class<T> cl,
          final boolean differs) {
    return (DifferResult<T, CT>)new DifferListResult<T>(differs);
  }

  /**
   *
   * @param pi index to identify property
   * @param val value to compare
   * @param master true if this is a recurring instance
   * @return differ result
   */
  @SuppressWarnings("unchecked")
  public static <T extends Comparable<T>,
          CT extends Collection<T>> DifferResult<T, CT> differs(
          final Class<T> cl,
          final PropertyInfoIndex pi,
          final Object val,
          final EventInfo master) {
    if (master == null) {
      return diffres(cl, val != null);
    }

    final BwEvent ev = master.getEvent();

    switch (pi) {
      case UNKNOWN_PROPERTY:
        break;

      case CLASS:
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getClassification()) != 0);

      case COMPLETED: /* Todo only */
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getCompleted()) != 0);

      case CREATED:
        break;

      case DESCRIPTION:
          /*
          for (BwLongString s: ev.getDescriptions()) {
            chg.addValue(Property.DESCRIPTION, s);
          }
          */
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getDescription()) != 0);

      case DTEND: /* Event only */
      case DUE: /* Todo only */
        final BwDateTime dt = (BwDateTime)val;
        return diffres(cl,
                       !CalFacadeUtil.eqObjval(ev.getDtend(), dt));

      case DTSTAMP:
        break;

      case DTSTART:
        final BwDateTime dt1 = (BwDateTime)val;
        return diffres(cl,
                       !CalFacadeUtil.eqObjval(ev.getDtstart(), dt1));

      case DURATION:
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getDuration()) != 0);

      case GEO:
        return diffres(cl,
                       Util.cmpObjval((BwGeo)val,
                                      ev.getGeo()) != 0);

      case LAST_MODIFIED:
        break;

      case LOCATION:
        return diffres(cl,
                       Util.cmpObjval((BwLocation)val,
                                      ev.getLocation()) != 0);

      case ORGANIZER:
        final var organizer = (BwOrganizer)val;
        return diffres(cl,
                       (Util.cmpObjval(organizer,
                                      ev.getOrganizer()) != 0) ||
                               organizer.differsFrom(ev.getOrganizer()));

      case PRIORITY:
        return diffres(cl,
                       Util.cmpObjval((Integer)val,
                                      ev.getPriority()) != 0);

      case RECURRENCE_ID:
        break;

      case SEQUENCE:
        return diffres(cl,
                       (Integer)val != ev.getSequence());

      case STATUS:
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getStatus()) != 0);

      case SUMMARY:
          /*
          for (BwString s: ev.getSummaries()) {
            chg.addValue(Property.SUMMARY, s);
          }
          */
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getSummary()) != 0);

      case PERCENT_COMPLETE: /* Todo only */
        return diffres(cl,
                       Util.cmpObjval((Integer)val,
                                      ev.getPercentComplete()) != 0);

      case UID:
        break;

      case URL:
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getLink()) != 0);

      case TRANSP:
        return diffres(cl,
                       Util.cmpObjval((String)val,
                                      ev.getTransparency()) != 0);

      /* ---------------------------- Multi valued --------------- */

      case ATTACH:
        return (DifferResult<T, CT>)cmpObjval((Set<BwAttachment>)val,
                                             ev.getAttachments());

      case ATTENDEE :
        return (DifferResult<T, CT>)cmpObjval((Set<BwAttendee>)val, ev.getAttendees());

      case CATEGORIES:
        return (DifferResult<T, CT>)cmpObjval((Set<BwCategory>)val, ev.getCategories());

      case COMMENT:
        return (DifferResult<T, CT>)cmpObjval((Set<BwString>)val,
                                              ev.getComments());

      case CONCEPT:
        final var evConcepts =
                ev.getXicalProperties("CONCEPT");
        return (DifferResult<T, CT>)cmpObjval((List<BwXproperty>)val,
                                              evConcepts);

      case CONTACT:
        return (DifferResult<T, CT>)cmpObjval((Set<BwContact>)val, ev.getContacts());

      case EXDATE:
        return (DifferResult<T, CT>)cmpObjval((Set<BwDateTime>)val, ev.getExdates());

      case EXRULE:
        return (DifferResult<T, CT>)cmpObjval((Set<String>)val, ev.getExrules());

      case REQUEST_STATUS:
        return (DifferResult<T, CT>)cmpObjval((Set<BwRequestStatus>)val,
                                             ev.getRequestStatuses());

      case RELATED_TO:
        return diffres(cl, Util.cmpObjval((BwRelatedTo)val,
                                          ev.getRelatedTo()) != 0);

      case RESOURCES:
        return (DifferResult<T, CT>)cmpObjval((Set<BwString>)val, ev.getResources());

      case RDATE:
        return (DifferResult<T, CT>)cmpObjval((Set<BwDateTime>)val, ev.getRdates());

      case RRULE:
        return (DifferResult<T, CT>)cmpObjval((Set<String>)val, ev.getRrules());

      case VALARM:
        return (DifferResult<T, CT>)cmpObjval((Set<BwAlarm>)val, ev.getAlarms());

      case XPROP:
        return (DifferResult<T, CT>)cmpObjval((List<BwXproperty>)val, ev.getXproperties());

      /* -------------- Other event/task fields ------------------ */
      case SCHEDULE_METHOD:
      case SCHEDULE_STATE:
      case SCHEDULE_TAG:
      case TRIGGER_DATE_TIME:
      case URI:

        /* -------------- Other non-event, non-todo ---------------- */

      case FREEBUSY:
      case TZID:
      case TZNAME:
      case TZOFFSETFROM:
      case TZOFFSETTO:
      case TZURL:
      case ACTION:
      case REPEAT:
      case TRIGGER:
        break;

      case COLLECTION: // non ical
      case COST: // non ical
      case CREATOR: // non ical
      case OWNER: // non ical
      case ENTITY_TYPE: // non ical
        break;

      case LANG: // Param
      case TZIDPAR: // Param
        break;

      case PUBLISH_URL:
      case POLL_ITEM_ID:
      case END_TYPE:
      case ETAG:
      case HREF:
      case XBEDEWORK_COST:
      case CALSCALE:
      case METHOD:
      case PRODID:
      case VERSION:
      case ACL:
      case AFFECTS_FREE_BUSY:
      case ALIAS_URI:
      case ATTENDEE_SCHEDULING_OBJECT:
      case CALTYPE:
      case COL_PROPERTIES:
      case COLPATH:
      case CTOKEN:
      case DISPLAY:
      case DOCTYPE:
      case EVENTREG_END:
      case EVENTREG_MAX_TICKETS:
      case EVENTREG_MAX_TICKETS_PER_USER:
      case EVENTREG_START:
      case EVENTREG_WAIT_LIST_LIMIT:
      case FILTER_EXPR:
      case IGNORE_TRANSP:
      case IMAGE:
      case INDEX_END:
      case INDEX_START:
      case INSTANCE:
      case LAST_REFRESH:
      case LAST_REFRESH_STATUS:
      case LOCATION_HREF:
      case LOCATION_STR:
        break;

      // Internal bedework properties
      case CALSUITE:
      case LASTMODSEQ:
      case MASTER:
      case NAME:
      case NO_START:
      case ORGANIZER_SCHEDULING_OBJECT:
      case ORIGINATOR:
      case OVERRIDE:
      case PARAMETERS:
      case PUBLIC:
      case RECIPIENT:
      case REFRESH_RATE:
      case REMOTE_ID:
      case REMOTE_PW:
      case SUGGESTED_TO:
      case TAG:
      case TARGET:
      case THUMBIMAGE:
      case TOMBSTONED:
      case TOPICAL_AREA:
      case UNREMOVEABLE:
      case VPATH:
      case VIEW:
      case X_BEDEWORK_CATEGORIES:
      case X_BEDEWORK_CONTACT:
      case X_BEDEWORK_LOCATION:
        break;

      default:
        logger.warn("Not handling icalendar property " + pi);
    } // switch

    return diffres(cl, false);
  }

  public static <T extends Comparable<T>,
          CT extends Collection<T>> DifferResult<T, CT> cmpObjval(
          final CT to,
          final CT from) {
    if (Util.isEmpty(to)) {
      if (Util.isEmpty(from)) {
        return new DifferResult<>(false);
      }

      return new DifferResult<>(false, null, true, null);
    }

    if (Util.isEmpty(from)) {
      return new DifferResult<>(true, to, false, null);
    }

    final DifferResult<T, CT> res;

    if (to instanceof Set<?>) {
      res = (DifferResult<T, CT>)new DifferSetResult<T>();
    } else {
      res = (DifferResult<T, CT>)new DifferListResult<T>();
    }

    // First look to see if every element in thisOne is in thatOne

    for (final T c: to) {
      if (!from.contains(c)) {
        res.toAdd(c);
      }
    }

    // Now we do it the other way round - because thatOne may have 2
    // equal elements

    boolean differable = false;

    for (final T c: from) {
      if (!to.contains(c)) {
        res.toRemove(c);
        continue;
      }

      if (differable || (c instanceof Differable)) {
        final Differable<T> fromC = (Differable<T>)c;

        // Could probably figure this out through reflection
        differable = true; // avoid check
        for (final T toC: to) {
          if (!c.equals(toC)) {
            continue;
          }

          if (fromC.differsFrom(toC)) {
            res.doesDiffer(toC);
          }
        }
      }
    }

    return res;
  }
}

