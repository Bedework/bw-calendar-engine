/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.jscal;

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.convert.EventTimeZonesRegistry;
import org.bedework.convert.IcalTranslator;
import org.bedework.jsforj.impl.JSFactory;
import org.bedework.jsforj.impl.JSMapper;
import org.bedework.jsforj.model.JSCalendarObject;
import org.bedework.jsforj.model.JSGroup;
import org.bedework.jsforj.model.JSPropertyNames;
import org.bedework.jsforj.model.JSTypes;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.response.GetEntityResponse;

import java.io.Writer;
import java.util.Collection;
import java.util.TreeSet;

/**
 * User: mike Date: 5/7/20 Time: 23:05
 */
public class JSCalTranslator extends IcalTranslator {
  private static final JSMapper mapper = new JSMapper();

  /**
   * Constructor:
   *
   * @param cb IcalCallback object for retrieval of entities
   */
  public JSCalTranslator(
          final IcalCallback cb) {
    super(cb);
  }

  /** Make a new JSGroup with default properties
   *
   * @param methodType - ical method
   * @return JSGroup
   */
  public static JSGroup newJSGroup(final int methodType) {
    final JSGroup group =
            (JSGroup)JSFactory.getFactory()
                              .newValue(JSTypes.typeJSGroup);

    group.setProperty(JSPropertyNames.prodId, prodId);

    if ((methodType > ScheduleMethods.methodTypeNone) &&
            (methodType < ScheduleMethods.methodTypeUnknown)) {
      group.setProperty(JSPropertyNames.method,
                        ScheduleMethods.methods[methodType]);
    }

    return group;
  }

  /** Turn a collection of events into a jsgroup
   *
   * @param vals          collection of events
   * @param methodType    int value fromIcalendar
   * @return Calendar
   * @throws RuntimeException on fatal error
   */
  public JSGroup toJScal(final Collection<EventInfo> vals,
                         final int methodType) {
    final JSGroup group = newJSGroup(methodType);

    if ((vals == null) || (vals.size() == 0)) {
      return group;
    }

    final TreeSet<String> added = new TreeSet<>();

    for (final EventInfo ei: vals) {
      addToGroup(group, ei, added, methodType);
    }

    return group;
  }

  /** Write a JSCalendar object
   *
   * @param obj JSCalendarObject to convert
   * @param wtr Writer for output
   */
  public static void writeJSCalendar(final JSCalendarObject obj,
                                     final Writer wtr) {

    obj.writeValue(wtr, mapper);
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private void addToGroup(final JSGroup group,
                          final EventInfo val,
                          final TreeSet<String> added,
                          final int methodType) {
    String currentPrincipal = null;
    final BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    final BwEvent ev = val.getEvent();

    final EventTimeZonesRegistry tzreg =
            new EventTimeZonesRegistry(this, ev);

    // Always by ref - except for custom?
    //if (!cb.getTimezonesByReference()) {
    //  /* Add referenced timezones to the calendar */
    //  addIcalTimezones(cal, ev, added, tzreg);
    //}

    JSCalendarObject jsCalMaster = null;
    GetEntityResponse<JSCalendarObject> res;

//      if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
    //      comp = VFreeUtil.toVFreeBusy(ev);
    //  } else {
    res = BwEvent2JsCal.convert(val, null, null,
                                methodType,
                                tzreg,
                                currentPrincipal);
    //}
    if (!res.isOk()) {
      throw new RuntimeException(res.toString());
    }
    jsCalMaster = res.getEntity();
    if (!ev.getSuppressed()) {
      /* Add it to the group */
      group.addEntry(jsCalMaster);
    }

    if (val.getNumOverrides() > 0) {
      for (final EventInfo oei: val.getOverrides()) {
        res = BwEvent2JsCal.convert(oei, val, jsCalMaster,
                                    methodType,
                                    tzreg,
                                    currentPrincipal);
        if (!res.isOk()) {
          throw new RuntimeException(res.toString());
        }
        if (ev.getSuppressed()) {
          /* Add it to the group */
          group.addEntry(res.getEntity());
        }
      }
    }
  }
}
