/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.jscal;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.convert.CnvUtil;
import org.bedework.convert.Icalendar;
import org.bedework.convert.ical.Ical2BwEvent;
import org.bedework.convert.ical.IcalUtil;
import org.bedework.jsforj.impl.values.dataTypes.JSLocalDateTimeImpl;
import org.bedework.jsforj.impl.values.dataTypes.JSUTCDateTimeImpl;
import org.bedework.jsforj.model.DateTimeComponents;
import org.bedework.jsforj.model.JSCalendarObject;
import org.bedework.jsforj.model.JSProperty;
import org.bedework.jsforj.model.JSPropertyNames;
import org.bedework.jsforj.model.values.JSAbsoluteTrigger;
import org.bedework.jsforj.model.values.JSAlert;
import org.bedework.jsforj.model.values.JSOffsetTrigger;
import org.bedework.jsforj.model.values.JSTrigger;
import org.bedework.jsforj.model.values.collections.JSAlerts;
import org.bedework.jsforj.model.values.collections.JSList;
import org.bedework.jsforj.model.values.dataTypes.JSDateTime;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;

import static org.bedework.calfacade.BwAlarm.TriggerVal;
import static org.bedework.jsforj.model.JSTypes.typeJSEvent;
import static org.bedework.jsforj.model.JSTypes.typeJSTask;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex.CATEGORIES;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex.CREATED;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex.DESCRIPTION;
import static org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex.SUMMARY;
import static org.bedework.util.misc.response.Response.Status.failed;

/**
 * User: mike Date: 3/30/20 Time: 23:11
 */
public class JsCal2BwEvent {
  private final static BwLogger logger =
          new BwLogger().setLoggedClass(Ical2BwEvent.class);

  /**
   *
   * @param cb          IcalCallback object
   * @param val         JSCalendar object we are converting
   * @param col         Possible collection for event
   * @param ical        Icalendar we are converting into. We check its events for
   *                    overrides.
   * @return Response with status and EventInfo object representing new entry or updated entry
   */
  public static GetEntityResponse<EventInfo> toEvent(
          final IcalCallback cb,
          final JSCalendarObject val,
          final BwCalendar col,
          final Icalendar ical) {
    final var resp = new GetEntityResponse<EventInfo>();

    if (val == null) {
      return Response.notOk(resp, failed, "No entity supplied");
    }

    final var jstype = val.getType();

    final int entityType;

    switch (jstype) {
      case typeJSEvent:
        entityType = IcalDefs.entityTypeEvent;
        break;

      case typeJSTask:
        entityType = IcalDefs.entityTypeTodo;
        break;

      default:
        return Response
                .error(resp, "org.bedework.invalid.component.type: " +
                        jstype);
    }

    /* See if we have a recurrence id */

    BwDateTime ridObj = null;
    String rid = null;

    // Get the guid from the component

    final String guid = val.getUid();

    if (guid == null) {
      /* XXX A guid is required - but are there devices out there without a
       *       guid - and if so how do we handle it?
       */
      return Response.notOk(resp, failed, CalFacadeException.noGuid);
    }

    /* If we have a recurrence id then we assume this is a detached instance.
     * This may happen if we are invited to one or more instances of a
     * meeting. In this case we try to retrieve the master and if it doesn't
     * exist we manufacture one. We consider such an instance an update to
     * that instance only and leave the others alone.
     */

    final var dtOnlyP = val.getBooleanProperty(
            JSPropertyNames.showWithoutTime);
    final var jsrid = val.getRecurrenceId();

    if (jsrid != null) {
      final IcalDate icalDate = new IcalDate(jsrid.getStringValue());
      ridObj = BwDateTime.makeBwDateTime(
              dtOnlyP,
              icalDate.format(dtOnlyP),
              val.getStringProperty(JSPropertyNames.recurrenceIdTimeZone));

      rid = ridObj.getDate();
    }

    final String evStart = val.getStringProperty(JSPropertyNames.start);
    final String icalEvstart;

    if (evStart != null) {
      final IcalDate icalDate = new IcalDate(evStart);
      icalEvstart = icalDate.format(dtOnlyP);
    } else if (ridObj == null) {
      // Invalid event - no start
      return Response.notOk(resp, failed, CalFacadeException.invalidOverride);
    } else {
      icalEvstart = ridObj.getDtval();
    }

    final var ger = CnvUtil.retrieveEvent(
            cb, guid, rid,
            entityType, col,
            ical,
            icalEvstart,
            val.getStringProperty(JSPropertyNames.recurrenceIdTimeZone),
            logger);

    if (!ger.isOk()) {
      return Response.fromResponse(resp, ger);
    }

    final var masterEI = ger.getEntity().masterEI;
    final var evinfo = ger.getEntity().evinfo;
    final var ev = evinfo.getEvent();

    final ChangeTable chg = evinfo.getChangeset(
            cb.getPrincipal().getPrincipalRef());

    if (rid != null) {
      final String evrid = evinfo.getEvent().getRecurrenceId();

      if ((evrid == null) || (!evrid.equals(rid))) {
        logger. warn("Mismatched rid ev=" + evrid + " expected " + rid);
        chg.changed(PropertyInfoIndex.RECURRENCE_ID, evrid, rid); // XXX spurious???
      }

      if (masterEI.getEvent().getSuppressed()) {
        // Handles the case of receiving an invite to another instance
        masterEI.getEvent().addRdate(ridObj);
      }
    }

    ev.setEntityType(entityType);
    ev.setCreatorHref(cb.getPrincipal().getPrincipalRef());
    ev.setOwnerHref(cb.getOwner().getPrincipalRef());

    setValues(resp, cb, chg, ridObj, evinfo, val);

    resp.setEntity(evinfo);
    return Response.ok(resp);
  }

  private static void setValues(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final ChangeTable chg,
          final BwDateTime recurrenceId,
          final EventInfo ei,
          final JSCalendarObject val) {
    final BwEvent ev = ei.getEvent();

    /* ------------------- Alarms -------------------- */
    if (!doAlarms(resp, cb, chg, ev,
                  val.getAlerts(false))) {
      return;
    }

    /* ------------------- Categories -------------------- */
    if (!doCategories(resp, cb, chg, ev,
                      val.getCategories(false))) {
      return;
    }

    /* ------------------- Created -------------------- */
    if (!doCreated(resp, cb, chg, ev,
                      val.getStringProperty(JSPropertyNames.created))) {
      return;
    }

    /* ------------------- Dates and duration --------------- */

    if (!setDates(resp, cb, val, ei, recurrenceId)) {
      return;
    }

    /* ------------------- Description -------------------- */

    if (val.hasProperty(JSPropertyNames.description)) {
      final var desc = val.getDescription();
      if (chg.changed(DESCRIPTION, ev.getDescription(), desc)) {
        ev.setDescription(desc);
      }
    }

    /* ------------- Description Content type--------------- */
    // Not used

    /* ------------------- keywords -------------------- */
    if (!doKeywords(resp, cb, chg, ev,
                      val.getKeywords(false))) {
      return;
    }

    /* ------------------- Locations ------------------------ */

    /* ------------------- Summary -------------------- */

    if (chg.changed(SUMMARY, ev.getSummary(), val.getTitle())) {
      ev.setSummary(val.getTitle());
    }

    for (final JSProperty<?> prop: val.getProperties()) {
      final var pname = prop.getName();
      final var pval = prop.getValue();

      switch (pname) {
        case JSPropertyNames.color:
          break;

        case JSPropertyNames.entries:
          break;

        case JSPropertyNames.estimatedDuration:
          break;

        case JSPropertyNames.excluded:
          break;

        case JSPropertyNames.freeBusyStatus:
          break;

        case JSPropertyNames.links:
          if (!doLinks(resp, ei, prop)) {
            return;
          }
          break;

        case JSPropertyNames.locale:
          break;

        case JSPropertyNames.localizations:
          break;

        case JSPropertyNames.locations:
          break;

        case JSPropertyNames.method:
          break;

        case JSPropertyNames.participants:
          break;

        case JSPropertyNames.priority:
          break;

        case JSPropertyNames.privacy:
          break;

        case JSPropertyNames.prodId:
          break;

        case JSPropertyNames.progress:
          break;

        case JSPropertyNames.progressUpdated:
          break;

        case JSPropertyNames.recurrenceRules:
          break;

        case JSPropertyNames.relatedTo:
          break;

        case JSPropertyNames.replyTo:
          break;

        case JSPropertyNames.sequence:
          break;

        case JSPropertyNames.source:
          break;

        case JSPropertyNames.status:
          break;

        case JSPropertyNames.timeZone:
          break;

        case JSPropertyNames.timeZones:
          break;

        case JSPropertyNames.title:
          break;

        case JSPropertyNames.updated:
          break;

        case JSPropertyNames.useDefaultAlerts:
          break;

        case JSPropertyNames.virtualLocations:
          break;

        default:
          logger.warn("Unknown property: " + pname);
      }
    }
  }

  private static boolean doAlarms(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final ChangeTable chg,
          final BwEvent ev,
          final JSAlerts value) {
    if (value == null) {
      return true;
    }

    for (final var alertp: value.get()) {
      final var alert = alertp.getValue();
      final var action = alert.getAction();

      final TriggerVal tr = getTrigger(alert.getTrigger());

      if (JSAlert.alertActionDisplay.equals(action)) {
        ev.addAlarm(BwAlarm.displayAlarm(ev.getCreatorHref(),
                                         tr,
                                         null, 0,
                                         ev.getSummary()));
        continue;
      }

      if (JSAlert.alertActionEmail.equals(action)) {
        ev.addAlarm(BwAlarm.emailAlarm(ev.getCreatorHref(),
                                       tr,
                                       null, 0,
                                       null, // Attach
                                       ev.getDescription(),
                                       ev.getSummary(),
                                       null));
      }
    }
    return true;
  }

  /**
   * @param val JSTrigger subtype
   * @return TriggerVal
   */
  public static TriggerVal getTrigger(final JSTrigger val) {
    final TriggerVal tr = new TriggerVal();

    if (val instanceof JSAbsoluteTrigger) {
      final var absTrigger = (JSAbsoluteTrigger)val;
      final IcalDate icalDate =
              new IcalDate(absTrigger.getWhen().getStringValue());
      tr.trigger = icalDate.format(false);
      tr.triggerDateTime = true;

      return tr;
    }

    if (!(val instanceof JSOffsetTrigger)) {
      return tr;
    }

    final var offsetTrigger = (JSOffsetTrigger)val;
    final var rel = offsetTrigger.getRelativeTo();

    tr.triggerStart = (rel == null) ||
            JSOffsetTrigger.relativeToStart.equals(rel);

    tr.trigger = offsetTrigger.getOffset().getStringValue();

    return tr;
  }

  private static boolean doCategories(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final ChangeTable chg,
          final BwEvent ev,
          final JSList<String> value) {
    if (value == null) {
      return true;
    }

    for (final String cval: value.get()) {
      chg.addValue(PropertyInfoIndex.XPROP,
                   BwXproperty.makeIcalProperty("CONCEPT",
                                                null,
                                                cval));
    }

    return true;
  }

  private static boolean doKeywords(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final ChangeTable chg,
          final BwEvent ev,
          final JSList<String> value) {
    if (value == null) {
      return true;
    }

    for (final String kw: value.get()) {
      final BwString key = new BwString(null, kw);

      final var fcResp = cb.findCategory(key);
      final BwCategory cat;

      if (fcResp.isError()) {
        Response.fromResponse(resp, fcResp);
        return false;
      }

      if (fcResp.isNotFound()) {
        cat = BwCategory.makeCategory();
        cat.setWord(key);

        cb.addCategory(cat);
      } else {
        cat = fcResp.getEntity();
      }

      chg.addValue(CATEGORIES, cat);
    }

    return true;
  }

  private static boolean doCreated(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final ChangeTable chg,
          final BwEvent ev,
          final String value) {
    final IcalDate icalDate = new IcalDate(value);
    final String dt = icalDate.format(false);
    if (chg.changed(CREATED, ev.getCreated(), dt)) {
      ev.setCreated(dt);
    }

    return true;
  }

  /*
     If this is an override the value will have a recurrence id which
     we can use to set the date.
   */
  private static boolean setDates(
          final GetEntityResponse<EventInfo> resp,
          final IcalCallback cb,
          final JSCalendarObject obj,
          final EventInfo evinfo,
          final BwDateTime recurrenceId) {
    /*
      We need the following - these values may come from overrides)
        * date or date-time - from showWithoutTimes flag
        * start timezone (if not date)
        * ending timezone (if not date) - from end location
        * start - if override from recurrence id or overridden start
        * duration - possibly overridden - for event
        * due - possibly overridden - for task

       If end timezone is not the same as start then we have to use a
       DTEND with timezone, otherwise duration will do
     */
    try {
//      final JSCalendarObject obj;

  //    if (val == null) {
    //    obj = master;
      //} else {
       // obj = val;
     // }

      // date or date-time - from showWithoutTimes flag
      final var dateOnly =
              obj.getBooleanProperty(JSPropertyNames.showWithoutTime);
      // start timezone
      final String startTimezoneId;
      final String endTimezoneId;

      if (dateOnly) {
        startTimezoneId = null;
        endTimezoneId = null;
      } else {
        startTimezoneId = obj.getStringProperty(JSPropertyNames.timeZone);
        endTimezoneId = null; // from location
      }

      final TimeZone startTz;
      if (startTimezoneId != null) {
        startTz = Timezones.getTz(startTimezoneId);
      } else {
        startTz = null;
      }

      final String start = obj.getStringProperty(JSPropertyNames.start);
      final DtStart st;

      if (start == null) {
        if (recurrenceId != null) {
          // start didn't come from an override.
          // Get it from the recurrence id
          st = recurrenceId.makeDtStart();
        } else {
          resp.setMessage("Missing start");
          resp.setStatus(failed);
          return false;
        }
      } else {
        final var icdt = new IcalDate(start);

        st = new DtStart(icdt.format(dateOnly), startTz);
      }

      DtEnd de = null;

      if (evinfo.getEvent().getEntityType() == IcalDefs.entityTypeTodo) {
        final var due = obj.getStringProperty(JSPropertyNames.due);
        if (due != null) {
          final var icdt = new IcalDate(due);

          de = new DtEnd(icdt.format(dateOnly), startTz);
        }
      }

      final var durStr = obj.getStringProperty(JSPropertyNames.duration);
      final Duration dur;
      if (durStr != null) {
        dur = new Duration(null, durStr);
      } else {
        dur = null;
      }

      IcalUtil.setDates(cb.getPrincipal().getPrincipalRef(),
                        evinfo, st, de, dur);
    } catch (final Throwable t) {
      resp.setException(t);
      return false;
    }

    return true;
  }

  private static class IcalDate {
    private final DateTimeComponents dtc;

    IcalDate(final String val) {
      final var utc = val.endsWith("Z");
      final JSDateTime dt;

      if (utc) {
        dt = new JSUTCDateTimeImpl(val);
      } else {
        dt = new JSLocalDateTimeImpl(val);
      }

      dtc = dt.getComponents();
    }

    private final String utcFormat = "%4d%2d%2dT%2d%2d%2dZ";
    private final String dateTimeFormat = "%4d%2d%2dT%2d%2d%2d";
    private final String dateFormat = "%4d%2d%2d";

    String format(final boolean dtOnly) {
      if (dtc.isUtc()) {
        return String.format(utcFormat,
                             dtc.getYear(),
                             dtc.getMonth(),
                             dtc.getDay(),
                             dtc.getHours(),
                             dtc.getMinutes(),
                             dtc.getSeconds());
      }

      if (dtOnly) {
        return String.format(dateFormat,
                             dtc.getYear(),
                             dtc.getMonth(),
                             dtc.getDay());
      }

      return String.format(dateTimeFormat,
                           dtc.getYear(),
                           dtc.getMonth(),
                           dtc.getDay(),
                           dtc.getHours(),
                           dtc.getMinutes(),
                           dtc.getSeconds());
    }

    long getFractions() {
      return dtc.getFractional();
    }

    boolean isUtc() {
      return dtc.isUtc();
    }
  }

  private static boolean doLinks(final GetEntityResponse<EventInfo> resp,
                                 final EventInfo ei,
                                 final JSProperty prop) {
    return true;
  }
}
