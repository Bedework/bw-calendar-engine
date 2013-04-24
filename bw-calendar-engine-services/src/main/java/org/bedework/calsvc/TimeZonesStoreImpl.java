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

import org.bedework.calcorei.CoreEventInfo;
import org.bedework.calcorei.CoreEventsI.InternalEventKey;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.base.BwEventKey;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo;
import org.bedework.calfacade.base.UpdateFromTimeZonesInfo.UnknownTimezoneInfo;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.BwDateTimeUtil;
import org.bedework.calsvci.TimeZonesStoreI;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.property.LastModified;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class TimeZonesStoreImpl implements TimeZonesStoreI {
  protected boolean debug;

  private CalSvc svci;

  private transient Logger log;

  /*
   */
  TimeZonesStoreImpl(final CalSvc svci) {
    this.svci = svci;
    debug = getLogger().isDebugEnabled();
  }

  /** Extended info clas which has internal state information.
   *
   */
  private static class UpdateFromTimeZonesInfoInternal implements
          UpdateFromTimeZonesInfo {
    /* Event ids */
    Collection<? extends InternalEventKey> ids;
    Iterator<? extends InternalEventKey> idIterator;

    long lastmod = 0;

    int totalEventsChecked;

    int totalEventsUpdated;

    Collection<UnknownTimezoneInfo> unknownTzids = new TreeSet<UnknownTimezoneInfo>();

    Collection<BwEventKey> updatedList = new ArrayList<BwEventKey>();

    /* (non-Javadoc)
     * @see org.bedework.calfacade.base.UpdateFromTimeZonesInfo#getTotalEventsToCheck()
     */
    public int getTotalEventsToCheck() {
      return ids.size();
    }

    /* (non-Javadoc)
     * @see org.bedework.calfacade.base.UpdateFromTimeZonesInfo#getTotalEventsChecked()
     */
    public int getTotalEventsChecked() {
      return totalEventsChecked;
    }

    /* (non-Javadoc)
     * @see org.bedework.calfacade.base.UpdateFromTimeZonesInfo#getTotalEventsUpdated()
     */
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
      StringBuffer sb = new StringBuffer();

      sb.append("------------------------------------------");
      sb.append("\nUpdateFromTimeZonesInfoInternal:");
      sb.append("\ntotalEventsToCheck: ");
      sb.append(getTotalEventsToCheck());
      sb.append("\ntotalEventsChecked: ");
      sb.append(totalEventsChecked);
      sb.append("\ntotalEventsUpdated: ");
      sb.append(totalEventsUpdated);
      sb.append("\n------------------------------------------");

      return sb.toString();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.Calintf#updateFromTimeZones(int, boolean, org.bedework.calfacade.base.UpdateFromTimeZonesInfo)
   */
  public UpdateFromTimeZonesInfo updateFromTimeZones(final int limit,
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

    UpdateFromTimeZonesInfoInternal iinfo;

    if (info != null) {
      if (info.getTotalEventsToCheck() == info.getTotalEventsChecked()) {
        redo = true;
      }

      iinfo = (UpdateFromTimeZonesInfoInternal)info;
    } else {
      iinfo = new UpdateFromTimeZonesInfoInternal();
    }

    if (redo || (iinfo.ids == null)) {
      String lastmod = null;

      if (redo) {
        lastmod = new LastModified(new DateTime(iinfo.lastmod - 5000)).getValue();
      }
      // Get event ids from db.
      iinfo.lastmod = System.currentTimeMillis();
      iinfo.ids = ((Events)svci.getEventsHandler()).getEventKeysForTzupdate(lastmod);

      if (iinfo.ids == null) {
        iinfo.ids = new ArrayList<InternalEventKey>();
      }

      iinfo.totalEventsChecked = 0;
      iinfo.totalEventsUpdated = 0;
      iinfo.idIterator = iinfo.ids.iterator();
    }

    for (int i = 0; i < limit; i++) {
      if (!iinfo.idIterator.hasNext()) {
        break;
      }

      InternalEventKey ikey = iinfo.idIterator.next();

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
              if (debug) {
                trace("Updated start: ev.tzid=" + evstart.getTzid() +
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
              if (debug) {
                trace("Updated end: ev.tzid=" + evend.getTzid() +
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

      iinfo.totalEventsChecked++;
    }

    if (debug) {
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
      BwDateTime newVal = BwDateTimeUtil.getDateTime(val.getDtval(), false,
                                                   false, val.getTzid());

      if (newVal.getDate().equals(val.getDate())) {
        return null;
      }

      return newVal;
    } catch (CalFacadeException cfe) {
      if (cfe.getMessage().equals(CalFacadeException.unknownTimezone)) {
        iinfo.unknownTzids.add(new UnknownTimezoneInfo(owner, val.getTzid()));
        return null;
      }
      throw cfe;
    }
  }

  /* Get a logger for messages
   */
  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void trace(final String msg) {
    getLogger().debug("trace: " + msg);
  }
}
