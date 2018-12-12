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

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwEventKey;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo.UnknownTimezoneInfo;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.BwDateTimeUtil;
import org.bedework.calsvci.TimeZonesStoreI;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.ToString;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class TimeZonesStoreImpl implements Logged, TimeZonesStoreI {
  private final CalSvc svci;

  /*
   */
  TimeZonesStoreImpl(final CalSvc svci) {
    this.svci = svci;
  }

  /** Extended info class which has internal state information.
   *
   */
  private static class UpdateFromTimeZonesInfoInternal implements
          UpdateFromTimeZonesInfo {
    /* Event ids */
    Collection<String> names;
    Iterator<String> iterator;

    long lastmod = 0;

    int totalEventsChecked;

    int totalEventsUpdated;

    Collection<UnknownTimezoneInfo> unknownTzids = new TreeSet<>();

    Collection<BwEventKey> updatedList = new ArrayList<>();

    public int getTotalEventsToCheck() {
      return names.size();
    }

    public int getTotalEventsChecked() {
      return totalEventsChecked;
    }

    public int getTotalEventsUpdated() {
      return totalEventsUpdated;
    }

    public Collection<UnknownTimezoneInfo> getUnknownTzids() {
      return unknownTzids;
    }

    public Collection<BwEventKey> getUpdatedList() {
      return updatedList;
    }

    @Override
    public String toString() {
      final ToString ts = new ToString(this);

      ts.append("------------------------------------------");
      ts.newLine();
      ts.append("totalEventsToCheck", getTotalEventsToCheck());
      ts.newLine();
      ts.append("totalEventsChecked", totalEventsChecked);
      ts.newLine();
      ts.append("totalEventsUpdated", totalEventsUpdated);
      ts.newLine();
      ts.append("------------------------------------------");

      return ts.toString();
    }
  }

  @Override
  public UpdateFromTimeZonesInfo updateFromTimeZones(final String colHref,
                                                     final int limit,
                                                     final boolean checkOnly,
                                                     final UpdateFromTimeZonesInfo info
                                                     ) throws CalFacadeException {
    /* Versions < 3.3 don't have recurrences fully implemented so we'll
     * ignore those.
     *
     * Fields that could be affected:
     * Event start + end
     * rdates and exdates
     * Recurrence instances
     *
     */
    if ((info != null) && !(info instanceof UpdateFromTimeZonesInfoInternal)) {
      throw new CalFacadeException(CalFacadeException.illegalObjectClass);
    }

    boolean redo = false;

    final UpdateFromTimeZonesInfoInternal iinfo;

    if (info != null) {
      if (info.getTotalEventsToCheck() == info.getTotalEventsChecked()) {
        redo = true;
      }

      iinfo = (UpdateFromTimeZonesInfoInternal)info;
    } else {
      iinfo = new UpdateFromTimeZonesInfoInternal();
    }

    if (redo || (iinfo.names == null)) {
      String lastmod = null;

      if (redo) {
        lastmod = new LastModified(new DateTime(iinfo.lastmod - 5000)).getValue();
      }
      // Get event ids from db.
      iinfo.lastmod = System.currentTimeMillis();
      /* This is what we used to do - replace this with a search
      iinfo.ids = ((Events)svci.getEventsHandler()).getEventKeysForTzupdate(lastmod);
       The code below also needs to be updated to do schedulign where necessary
       */

      if (iinfo.names == null) {
        iinfo.names = new ArrayList<>();
      }

      iinfo.totalEventsChecked = 0;
      iinfo.totalEventsUpdated = 0;
      iinfo.iterator = iinfo.names.iterator();
    }

    for (int i = 0; i < limit; i++) {
      if (!iinfo.iterator.hasNext()) {
        break;
      }

      final String name = iinfo.iterator.next();

      /*
      // See if event needs update
      BwPrincipal owner = svci.getUsersHandler().getPrincipal(ikey.getOwnerHref());

      BwDateTime start = checkDateTimeForTZ(ikey.getStart(), owner, iinfo);
      BwDateTime end = checkDateTimeForTZ(ikey.getEnd(), owner, iinfo);

      if ((start != null) || (end != null)) {
        CoreEventInfo cei = ((Events)svci.getEventsHandler()).getEvent(ikey);
        BwEvent ev = cei.getEvent();

        if (cei != null) {
          iinfo.updatedList.add(new BwEventKey(ev.getColPath(),
                                               ev.getUid(),
                                               ev.getRecurrenceId(),
                                               ev.getName(),
                                               ev.getRecurring()));
          if (!checkOnly) {
            if (start != null) {
              BwDateTime evstart = ev.getDtstart();
              if (debug()) {
                debug("Updated start: ev.tzid=" + evstart.getTzid() +
                      " ev.dtval=" + evstart.getDtval() +
                      " ev.date=" + evstart.getDate() +
                      " newdate=" + start.getDate());
              }

              ev.setDtstart(BwDateTime.makeBwDateTime(evstart.getDateType(),
                                                      evstart.getDtval(),
                                                      start.getDate(),
                                                      evstart.getTzid(),
                                                      evstart.getFloating()));
            }
            if (end != null) {
              BwDateTime evend = ev.getDtend();
              if (debug()) {
                debug("Updated end: ev.tzid=" + evend.getTzid() +
                      " ev.dtval=" + evend.getDtval() +
                      " ev.date=" + evend.getDate() +
                      " newdate=" + end.getDate());
              }
              ev.setDtend(BwDateTime.makeBwDateTime(evend.getDateType(),
                                                    evend.getDtval(),
                                                    end.getDate(),
                                                    evend.getTzid(),
                                                    evend.getFloating()));
            }

            EventInfo ei = new EventInfo(ev);

            Collection<CoreEventInfo> overrides = cei.getOverrides();
            if (overrides != null) {
              for (CoreEventInfo ocei: overrides) {
                BwEventProxy op = (BwEventProxy)ocei.getEvent();

                ei.addOverride(new EventInfo(op));
              }
            }

            svci.getEventsHandler().update(ei, false, null);
            iinfo.totalEventsUpdated++;
          }
        }
      }
      */

      iinfo.totalEventsChecked++;
    }

    if (debug()) {
      trace(iinfo.toString());
    }

    return iinfo;
  }

  /* Recalculate UTC for the given value.
   *
   * Return null if no change needed otherwise return new value
   */
  private BwDateTime checkDateTimeForTZ(final BwDateTime val,
                                        final BwPrincipal owner,
                                        final UpdateFromTimeZonesInfoInternal iinfo) throws CalFacadeException {
    if (val.getDateType()) {
      return null;
    }

    if (val.isUTC()) {
      return null;
    }

    if (val.getFloating()) {
      return null;
    }

    try {
      final BwDateTime newVal = 
              BwDateTimeUtil.getDateTime(val.getDtval(), false,
                                         false, val.getTzid());

      if (newVal.getDate().equals(val.getDate())) {
        return null;
      }

      return newVal;
    } catch (final CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.unknownTimezone)) {
        iinfo.unknownTzids.add(new UnknownTimezoneInfo(owner, val.getTzid()));
        return null;
      }
      throw cfe;
    }
  }
}
