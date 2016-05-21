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
package org.bedework.calfacade.util;

import org.bedework.caldav.util.notifications.CalendarChangesType;
import org.bedework.caldav.util.notifications.ChangedByType;
import org.bedework.caldav.util.notifications.ChangedPropertyType;
import org.bedework.caldav.util.notifications.ChangesType;
import org.bedework.caldav.util.notifications.CreatedType;
import org.bedework.caldav.util.notifications.DeletedDetailsType;
import org.bedework.caldav.util.notifications.DeletedType;
import org.bedework.caldav.util.notifications.NotificationType;
import org.bedework.caldav.util.notifications.RecurrenceType;
import org.bedework.caldav.util.notifications.ResourceChangeType;
import org.bedework.caldav.util.notifications.UpdatedType;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwLongString;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;

import java.util.Collection;

import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

/** Generate change notification messages from event and other information.
 * Output is an XML object following the Apple extensions.
 *
 * <p>Call open first, then one or more of the methods describing changes,
 * followed by a call to close which returns the entire XML body.
 *
 * @author douglm
 */
public class NotificationsInfo {
  /**
   * @param currentAuth
   * @param ev
   * @return Info for single added event.
   * @throws CalFacadeException
   */
  public static String added(final String currentAuth,
                             final BwEvent ev) throws CalFacadeException {
    NotificationType note = getNotification();

    note.setNotification(getAdded(currentAuth, ev));

    try {
      return note.toXml(true);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param currentAuth
   * @param ev
   * @return Info for single deleted event.
   * @throws CalFacadeException
   */
  public static String deleted(final String currentAuth,
                               final BwEvent ev) throws CalFacadeException {
    NotificationType note = getNotification();

    note.setNotification(getDeleted(currentAuth, ev));

    try {
      return note.toXml(true);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param currentAuth
   * @param ev
   * @return Info for single updated event.
   * @throws CalFacadeException
   */
  public static String updated(final String currentAuth,
                               final BwEvent ev) throws CalFacadeException {
    ResourceChangeType rc = getUpdated(currentAuth, ev);

    if (rc == null) {
      return null;
    }

    NotificationType note = getNotification();

    note.setNotification(rc);

    try {
      return note.toXml(true);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Call for a deleted event
   *
   * @param currentAuth
   * @param ev
   * @return resource deleted notification.
   * @throws CalFacadeException
   */
  public static ResourceChangeType getDeleted(final String currentAuth,
                                              final BwEvent ev) throws CalFacadeException {
    try {
      ResourceChangeType rc = new ResourceChangeType();

      DeletedType del = new DeletedType();

      del.setHref(getHref(ev));

      del.setChangedBy(getChangedBy(currentAuth));

      DeletedDetailsType dd = new DeletedDetailsType();

      dd.setDeletedComponent(getType(ev));
      dd.setDeletedSummary(ev.getSummary());
      if (ev.isRecurringEntity()) {
          // TODO: Set these correctly.
          //dd.setDeletedNextInstance(val);
          //dd.setDeletedNextInstanceTzid(val);
          //dd.setDeletedHadMoreInstances(val);
      }
        
      if (ev.getDtstart() != null) {
        ChangedPropertyType start = new ChangedPropertyType();
        start.setName(PropertyInfoIndex.DTSTART.name());
        start.setDataFrom(String.valueOf(ev.getDtstart()));
        dd.getDeletedProps().add(start);
      }
        
      if (ev.getDtend() != null) {
        ChangedPropertyType end = new ChangedPropertyType();
        end.setName(PropertyInfoIndex.DTEND.name());
        end.setDataFrom(String.valueOf(ev.getDtend()));
        dd.getDeletedProps().add(end);
      }
       
      if (ev.getDuration() != null && !ev.getDuration().isEmpty()) {
        ChangedPropertyType dur = new ChangedPropertyType();
        dur.setName(PropertyInfoIndex.DURATION.name());
        dur.setDataFrom(ev.getDuration());
        dd.getDeletedProps().add(dur);
      }
        
      if (ev.getLocation() != null) {
        ChangedPropertyType loc = new ChangedPropertyType();
        loc.setName(PropertyInfoIndex.LOCATION.name());
        loc.setDataFrom(ev.getLocation().getAddress().getValue());
        dd.getDeletedProps().add(loc);
      }
        
      if (ev.getDescription() != null) {
        ChangedPropertyType desc = new ChangedPropertyType();
        desc.setName(PropertyInfoIndex.DESCRIPTION.name());
        desc.setDataFrom(ev.getDescription());
        dd.getDeletedProps().add(desc);
      }

      del.setDeletedDetails(dd);

      rc.setDeleted(del);

      return rc;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Call for an added event
   *
   * @param currentAuth
   * @param ev
   * @return resource created notification.
   * @throws CalFacadeException
   */
  public static ResourceChangeType getAdded(final String currentAuth,
                                            final BwEvent ev) throws CalFacadeException {
    try {
      ResourceChangeType rc = new ResourceChangeType();

      CreatedType cre = new CreatedType();

      cre.setHref(getHref(ev));

      cre.setChangedBy(getChangedBy(currentAuth));

      rc.setCreated(cre);

      return rc;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Call for an updated event.
   *
   * @param currentAuth
   * @param ev
   * @return resource updated notification.
   * @throws CalFacadeException
   */
  public static ResourceChangeType getUpdated(final String currentAuth,
                                              final BwEvent ev) throws CalFacadeException {
    try {
      ChangeTable changes = ev.getChangeset(currentAuth);

      if (changes.isEmpty()) {
        return null;
      }

      ResourceChangeType rc = new ResourceChangeType();

      UpdatedType upd = new UpdatedType();

      upd.setHref(getHref(ev));

      upd.setChangedBy(getChangedBy(currentAuth));

      upd.getCalendarChanges().add(instanceChanges(currentAuth, ev));

      rc.addUpdate(upd);

      return rc;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private NotificationsInfo() {}

  private static ChangedByType getChangedBy(final String currentAuth) {
    ChangedByType cb = new ChangedByType();
    // firstName
    // lastName
    cb.setCommonName(currentAuth); // XXX - need real name(s)
    cb.setDtstamp(DateTimeUtil.rfcDateTime());
    cb.setHref(currentAuth);

    return cb;
  }

  private static NotificationType getNotification() {
    NotificationType note = new NotificationType();

    note.setDtstamp(DateTimeUtil.rfcDateTime());

    return note;
  }

  private static CalendarChangesType instanceChanges(final String currentAuth,
                                                     final BwEvent ev) throws Throwable {
    CalendarChangesType cc = new CalendarChangesType();

    RecurrenceType r = new RecurrenceType();

    r.setRecurrenceid(ev.getRecurrenceId());

    ChangesType c = new ChangesType();

    for (ChangeTableEntry cte: ev.getChangeset(currentAuth).getEntries()) {
      if (!cte.getChanged()) {
        continue;
      }

      if (cte.getIndex() == PropertyInfoIndex.XPROP) {
        /* Reflected a a set of removes and adds. */
        if (!Util.isEmpty(cte.getRemovedValues())) {
          for (BwXproperty xp: ((Collection<BwXproperty>)cte.getRemovedValues())) {
            ChangedPropertyType cp = new ChangedPropertyType();
            cp.setName(xp.getName());

            cp.setDataFrom(String.valueOf(xp));

            c.getChangedProperty().add(cp);
          }
        }

        if (!Util.isEmpty(cte.getAddedValues())) {
          for (BwXproperty xp: ((Collection<BwXproperty>)cte.getAddedValues())) {
            ChangedPropertyType cp = new ChangedPropertyType();
            cp.setName(xp.getName());

            cp.setDataTo(String.valueOf(xp));

            c.getChangedProperty().add(cp);
          }
        }
      } else {
        ChangedPropertyType cp = new ChangedPropertyType();

        cp.setName(cte.getIndex().name());

        cp.setDataFrom(getDataFrom(cte));
        cp.setDataTo(getDataTo(cte));

        c.getChangedProperty().add(cp);
      }
    }

    r.getChanges().add(c);

    cc.getRecurrence().add(r);

    return cc;
  }

  private static String getDataFrom(final ChangeTableEntry cte) {
    return getData(cte, cte.getOldVal());
  }

  private static String getDataTo(final ChangeTableEntry cte) {
    return getData(cte, cte.getNewVal());
  }

  private static String getData(final ChangeTableEntry cte,
                                final Object o) {
    if (o == null) {
      return null;
    }

    if (!cte.getIndex().getDbMultiValued()) {
      if (o instanceof BwString) {
        return ((BwString)o).getValue();  
      } else if (o instanceof BwLongString) {
        return ((BwLongString)o).getValue();  
      } else if (o instanceof BwLocation) {
        return ((BwLocation)o).getAddress().getValue();  
      } else if (o instanceof BwXproperty) {
        return ((BwXproperty)o).getValue();  
      } else {
        return String.valueOf(o);
      }
    }

    if (o instanceof BwString) {
      return ((BwString)o).getValue();  
    } else if (o instanceof BwLongString) {
      return ((BwLongString)o).getValue();  
    } else if (o instanceof BwLocation) {
      return ((BwLocation)o).getAddress().getValue();  
    } else if (o instanceof BwXproperty) {
      return ((BwXproperty)o).getValue();  
    } else {
      return String.valueOf(o);
    }
  }



  private static String getType(final BwEvent ev) throws Throwable {
    try {
      return IcalDefs.entityTypeIcalNames[ev.getEntityType()];
    } catch (Throwable t) {
      return "X";
    }
  }

  private static String getHref(final BwEvent ev) throws Throwable {
    return Util.buildPath(false, ev.getColPath(), "/", ev.getName());
  }
}
