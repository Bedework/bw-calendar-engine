/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.ical.jscal;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.ical.BwEventUtil;
import org.bedework.jsforj.model.JSCalendarObject;
import org.bedework.jsforj.model.JSProperty;
import org.bedework.jsforj.model.JSPropertyNames;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

import static org.bedework.jsforj.model.JSTypes.typeJSEvent;
import static org.bedework.jsforj.model.JSTypes.typeJSTask;
import static org.bedework.util.misc.response.Response.Status.failed;

/**
 * User: mike Date: 3/30/20 Time: 23:11
 */
public class JsCal2BwEvent {
  private static BwLogger logger =
          new BwLogger().setLoggedClass(BwEventUtil.class);

  /**
   *
   * @param cb          IcalCallback object
   * @param val         JSCalendar object
   * @return Response with status and EventInfo object representing new entry or updated entry
   */
  public static GetEntityResponse<EventInfo> toEvent(
          final IcalCallback cb,
          final JSCalendarObject val) {
    var resp = new GetEntityResponse<EventInfo>();

    if (val == null) {
      return Response.notOk(resp, failed, "No entity supplied");
    }

    var jstype = val.getType();

    final int entityType;

    switch (jstype) {
      case typeJSEvent:
        entityType = IcalDefs.entityTypeEvent;
        break;

      case typeJSTask:
        entityType = IcalDefs.entityTypeTodo;
        break;

      default:
        return Response.error(resp, "org.bedework.invalid.component.type: " +
                jstype);
    }

    final BwEvent ev = new BwEventObj();
    final EventInfo ei = new EventInfo(ev);

    ev.setEntityType(entityType);
    ev.setCreatorHref(cb.getPrincipal().getPrincipalRef());
    ev.setOwnerHref(cb.getOwner().getPrincipalRef());

    for (final JSProperty prop: val.getProperties()) {
      var pname = prop.getName();

      switch (pname) {
        case JSPropertyNames.type:
          // Already done
          break;

        case JSPropertyNames.alerts:
          break;

        case JSPropertyNames.categories:
          break;

        case JSPropertyNames.color:
          break;

        case JSPropertyNames.created:
          break;

        case JSPropertyNames.description:
          break;

        case JSPropertyNames.descriptionContentType:
          break;

        case JSPropertyNames.due:
          break;

        case JSPropertyNames.duration:
          break;

        case JSPropertyNames.entries:
          break;

        case JSPropertyNames.estimatedDuration:
          break;

        case JSPropertyNames.excluded:
          break;

        case JSPropertyNames.freeBusyStatus:
          break;

        case JSPropertyNames.keywords:
          break;

        case JSPropertyNames.links:
          if (!doLinks(resp, ei, prop)) {
            return resp;
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

        case JSPropertyNames.recurrenceId:
          break;

        case JSPropertyNames.recurrenceOverrides:
          break;

        case JSPropertyNames.recurrenceRule:
          break;

        case JSPropertyNames.relatedTo:
          break;

        case JSPropertyNames.replyTo:
          break;

        case JSPropertyNames.sequence:
          break;

        case JSPropertyNames.showWithoutTime:
          break;

        case JSPropertyNames.source:
          break;

        case JSPropertyNames.start:
          break;

        case JSPropertyNames.status:
          break;

        case JSPropertyNames.statusUpdatedAt:
          break;

        case JSPropertyNames.timeZone:
          break;

        case JSPropertyNames.timeZones:
          break;

        case JSPropertyNames.title:
          break;

        case JSPropertyNames.uid:
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

    resp.setEntity(ei);
    return Response.ok(resp);
  }

  private static boolean doLinks(final GetEntityResponse<EventInfo> resp,
                                 final EventInfo ei,
                                 final JSProperty prop) {
    return true;
  }
}
