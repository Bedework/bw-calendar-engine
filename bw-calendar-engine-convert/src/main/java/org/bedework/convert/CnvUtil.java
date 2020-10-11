/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.convert.ical.IcalUtil;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.property.DtStart;

import static org.bedework.util.misc.response.Response.Status.failed;

/**
 * User: mike Date: 7/31/20 Time: 22:42
 */
public class CnvUtil {
  static boolean colCanRetrieve(final BwCalendar col) {
    return (col != null) &&
            (col.getCalType() != BwCalendar.calTypeInbox) &&
            (col.getCalType() != BwCalendar.calTypePendingInbox) &&
            (col.getCalType() != BwCalendar.calTypeOutbox);
  }

  public static class RetrievedEvents {
    // Non-null if evinfo is an instance
    public EventInfo masterEI;

    // Never null except for error conditions.
    public EventInfo evinfo;
  }

  public static GetEntityResponse<RetrievedEvents> retrieveEvent(
          final IcalCallback cb,
          final String uid,
          final String rid,
          final int entityType,
          final BwCalendar col,
          final Icalendar ical,
          final String startDate, // ical format
          final String timeZone,
          final BwLogger logger) {
    final var resp = new GetEntityResponse<RetrievedEvents>();

    if (!colCanRetrieve(col)) {
      return Response.notFound(resp);
    }

    final var re = new RetrievedEvents();
    resp.setEntity(re);
    final var colPath = col.getPath();

    if (logger.debug()) {
      logger.debug("TRANS-TO_EVENT: try to fetch event with guid=" + uid);
    }

    final GetEntitiesResponse<EventInfo> eisResp =
            cb.getEvent(colPath, uid);
    if (eisResp.isError()) {
      return Response.fromResponse(resp, eisResp);
    }

    final var eis = eisResp.getEntities();

    if (!Util.isEmpty(eis)) {
      if (eis.size() > 1) {
        // DORECUR - wrong again
        return Response.notOk(resp, failed,
                              "More than one event returned for uid.");
      }
      re.evinfo = eis.iterator().next();
    }

    if (logger.debug()) {
      if (re.evinfo != null) {
        logger.debug("TRANS-TO_EVENT: fetched event with uid");
      } else {
        logger.debug("TRANS-TO_EVENT: did not find event with uid");
      }
    }

    if (re.evinfo != null) {
      if (rid != null) {
        // We just retrieved it's master
        re.masterEI = re.evinfo;
        re.masterEI.setInstanceOnly(true);
        re.evinfo = re.masterEI.findOverride(rid);
        re.evinfo.recurrenceSeen = true;
        ical.addComponent(re.masterEI);
      } else if (ical.getMethodType() == ScheduleMethods.methodTypeCancel) {
        // This should never have an rid for cancel of entire event.
        re.evinfo.setInstanceOnly(re.evinfo.getEvent().getSuppressed());
      } else {
        // Presumably sent an update for the entire event. No longer suppressed master
        re.evinfo.getEvent().setSuppressed(false);
      }

      return resp;
    }

    if (rid == null) {
      // No event found - just create one
      re.evinfo = makeNewEvent(cb, entityType, uid, colPath);

      return resp;
    }

    /* Manufacture a master for the instance */
    re.masterEI = makeNewEvent(cb, entityType, uid, colPath);
    final BwEvent e = re.masterEI.getEvent();

    // XXX This seems bogus
    final DtStart mdtStart;

    final String bogusDate = "19980118";
    final String bogusTime = "T230000";

    final boolean isDateType = startDate.length() == 8;
    final boolean isUTC = startDate.endsWith("Z");

    try {
      if (isDateType) {
        mdtStart = new DtStart(new Date(bogusDate));
      } else if (isUTC) {
        mdtStart = new DtStart(bogusDate + bogusTime + "Z");
      } else if (timeZone == null) {
        mdtStart = new DtStart(bogusDate + bogusTime);
      } else {
        mdtStart = new DtStart(bogusDate + bogusTime,
                               ical.getTimeZone(timeZone));
      }
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }

    IcalUtil.setDates(cb.getPrincipal().getPrincipalRef(),
                      re.masterEI, mdtStart, null, null);
    // e.setRecurring(true);
    // We add the rdate later - flags as recurring
    e.setSummary("Generated master");
    e.setSuppressed(true);

    re.evinfo = re.masterEI.findOverride(rid);
    re.evinfo.recurrenceSeen = true;
    re.masterEI.setInstanceOnly(true);

    ical.addComponent(re.masterEI);

    return resp;
  }

  public static EventInfo makeNewEvent(final IcalCallback cb,
                                       final int entityType,
                                       final String uid,
                                       final String colPath) {
    final BwEvent ev = new BwEventObj();
    final EventInfo evinfo = new EventInfo(ev);

    //ev.setDtstamps();
    ev.setEntityType(entityType);
    ev.setCreatorHref(cb.getPrincipal().getPrincipalRef());
    ev.setOwnerHref(cb.getOwner().getPrincipalRef());
    ev.setUid(uid);

    ev.setColPath(colPath);

    final ChangeTable chg = evinfo.getChangeset(cb.getPrincipal().getPrincipalRef());
    chg.changed(PropertyIndex.PropertyInfoIndex.UID, null, uid); // get that out of the way

    evinfo.setNewEvent(true);

    return evinfo;
  }
}
