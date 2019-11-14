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

import org.bedework.caldav.bwserver.PropertyUpdater.Component;
import org.bedework.caldav.bwserver.stdupdaters.DateDatetimePropUpdater.DatesState;
import org.bedework.caldav.server.sysinterface.SysIntf.UpdateResult;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.convert.IcalTranslator;
import org.bedework.convert.xcal.Xalarms;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.calendar.XcalUtil.TzGetter;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.xml.tagdefs.XcalTags;
import org.bedework.webdav.servlet.shared.WebdavException;

import ietf.params.xml.ns.icalendar_2.AvailableType;
import ietf.params.xml.ns.icalendar_2.BaseComponentType;
import ietf.params.xml.ns.icalendar_2.BaseParameterType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.RecurrenceIdPropType;
import ietf.params.xml.ns.icalendar_2.UidPropType;
import ietf.params.xml.ns.icalendar_2.ValarmType;
import ietf.params.xml.ns.icalendar_2.VavailabilityType;
import ietf.params.xml.ns.icalendar_2.VeventType;
import ietf.params.xml.ns.icalendar_2.VjournalType;
import ietf.params.xml.ns.icalendar_2.VtodoType;
import ietf.params.xml.ns.icalendar_2.XBedeworkWrapperPropType;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import org.oasis_open.docs.ws_calendar.ns.soap.ComponentReferenceType;
import org.oasis_open.docs.ws_calendar.ns.soap.ComponentSelectionType;
import org.oasis_open.docs.ws_calendar.ns.soap.ComponentsSelectionType;
import org.oasis_open.docs.ws_calendar.ns.soap.ParameterReferenceType;
import org.oasis_open.docs.ws_calendar.ns.soap.ParameterSelectionType;
import org.oasis_open.docs.ws_calendar.ns.soap.ParametersSelectionType;
import org.oasis_open.docs.ws_calendar.ns.soap.PropertiesSelectionType;
import org.oasis_open.docs.ws_calendar.ns.soap.PropertyReferenceType;
import org.oasis_open.docs.ws_calendar.ns.soap.PropertySelectionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/** Bedework implementation of SysIntf.
 *
 * @author Mike Douglass douglm at rpi.edu
 */
public class BwUpdates implements Logged {
  protected IcalCallback cb;

  protected String userHref;

  private Properties state = new Properties();

  /**
   * @param userHref
   */
  public BwUpdates(final String userHref) {
    this.userHref = userHref;
  }

  private static Map<Class, Integer> entTypes = new HashMap<Class, Integer>();

  static {
    entTypes.put(ValarmType.class, IcalDefs.entityTypeAlarm);
    entTypes.put(VavailabilityType.class, IcalDefs.entityTypeVavailability);
    entTypes.put(AvailableType.class, IcalDefs.entityTypeAvailable);
    entTypes.put(VeventType.class, IcalDefs.entityTypeEvent);
    entTypes.put(VtodoType.class, IcalDefs.entityTypeTodo);
    entTypes.put(VjournalType.class, IcalDefs.entityTypeJournal);
  }

  private PropertyUpdaterRegistry propUpdaterRegistry =
      new PropertyUpdaterRegistry();

  /** Register an updater used by all instances of the updater. i.e. a
   * standard updater
   *
   * @param cl
   * @param updCl
   */
  public static void registerUpdater(final Class cl,
                                     final String updCl) {
    PropertyUpdaterRegistry.registerStandardUpdater(cl, updCl);
  }

  /** Register an updater used only by this instance of the updater.
   *
   * @param cl
   * @param updCl
   */
  public void registerInstanceUpdater(final Class cl,
                                      final String updCl) {
    propUpdaterRegistry.registerUpdater(cl, updCl);
  }

  /** Update an event based on a list of web service updates
   * @param ei
   * @param updates
   * @param cb
   * @return true if updated OK
   * @throws WebdavException
   */
  public UpdateResult updateEvent(final EventInfo ei,
                                  final List<ComponentSelectionType> updates,
                                  final IcalCallback cb) throws WebdavException {
    this.cb = cb;

    try {
      if (updates == null) {
        return new UpdateResult("No updates");
      }

      for (ComponentSelectionType sel: updates) {
        UpdateResult ur = applyUpdate(ei, sel);
        if (!ur.getOk()) {
          return ur;
        }
      }

      return UpdateResult.getOkResult();
    } catch (WebdavException we) {
      if (debug()) {
        error(we);
      }
      throw we;
    } catch (Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new WebdavException(t);
    }
  }

  private UpdateResult applyUpdate(final EventInfo ei,
                                   final ComponentSelectionType selPar) throws WebdavException {
    /* First two selects just get us in to the events */

    ComponentSelectionType sel = selPar;
    // Top level must be "vcalendar"
    if ((sel == null) ||
        (sel.getVcalendar() == null)) {
      return new UpdateResult("Not \"vcalendar\"");
    }

    // Next - expect "components"
    ComponentsSelectionType csel = sel.getComponents();

    if (csel == null) {
      return new UpdateResult("Not \"component\"");
    }

    /* Only overrides may be added or removed as we only allow updates to
     * single entities. An override is considered part of a single event.
     */

    for (ComponentReferenceType c: csel.getRemove()) {
      UpdateResult ur = removeOverride(ei, c);
      if (!ur.getOk()) {
        return ur;
      }
    }

    for (ComponentReferenceType c: csel.getAdd()) {
      UpdateResult ur = addOverride(ei, c);
      if (!ur.getOk()) {
        return ur;
      }
    }

    /* Updates may be applied to the master or any overrides selected by uid and
     * recurrence-id
     */
    for (ComponentSelectionType cs: csel.getComponent()) {
      BaseComponentType ent = cs.getBaseComponent().getValue();

      // Must be a component matching the current one.
      if (ent == null) {
        return new UpdateResult("Missing component to match");
      }

      Integer entType = entTypes.get(ent.getClass());
      if (entType == null) {
        return new UpdateResult("Unknown entity type: " + ent.getClass());
      }

      if (entType != ei.getEvent().getEntityType()) {
        return new UpdateResult("No matching entity");
      }

      List<EventInfo> entities = new ArrayList<>();

      entities.add(ei);
      entities.addAll(ei.getOverrides());

      entities = match(entities, //sel,
                       ent);

      if ((entities == null) || (entities.size() == 0)) {
        return new UpdateResult("No matching entity");
      }

      if ((cs.getProperties() == null) &&
          (cs.getComponents() == null)) {
        // No properties or components - error
        return new UpdateResult("Must select \"components\" and/or \"properties\"");
      }

      /* At this point we either select properties to change or nested components
       */
      if (cs.getComponents() != null) {
        UpdateResult ur = applySubCompUpdates(ei, cs.getComponents());
        if (!ur.getOk()) {
          return ur;
        }
      }

      if (cs.getProperties() != null) {
        // Updating properties
        UpdateResult ur = updateEventsProperties(entities, cs.getProperties());
        if (!ur.getOk()) {
          return ur;
        }
      }
    }

    return UpdateResult.getOkResult();
  }

  /** Apply updates to sub-components of the given entity.
   *
   * @param ei
   * @param csel
   * @return UpdateResult
   * @throws WebdavException
   */
  private UpdateResult applySubCompUpdates(final EventInfo ei,
                                      final ComponentsSelectionType csel) throws WebdavException {
    /* Deal with removals first */

    for (ComponentReferenceType c: csel.getRemove()) {
      UpdateResult ur = removeSubComp(ei, c);
      if (!ur.getOk()) {
        return ur;
      }
    }

    for (ComponentReferenceType c: csel.getAdd()) {
      UpdateResult ur = addSubComp(ei, c);
      if (!ur.getOk()) {
        return ur;
      }
    }

    /* Now any updates to sub-components
     */

    for (ComponentSelectionType cs: csel.getComponent()) {
      UpdateResult ur = updateSubComp(ei, cs);
      if (!ur.getOk()) {
        return ur;
      }
    }

    return UpdateResult.getOkResult();
  }

  /* Return matching entities. */
  private List<EventInfo> match(final List<EventInfo> eis,
//                                final SelectElementType sel,
                                final BaseComponentType selComp) throws WebdavException {
    List<EventInfo> matched = new ArrayList<EventInfo>();

    CompSelector cs = getCompSelector(selComp);

    for (EventInfo ei: eis) {
      if ((cs.uid == null) && (cs.recurrenceId == null)) {
        /* Only valid for a single non-recurring entity */
        if (ei.getEvent().isRecurringEntity() || (eis.size() > 1)) {
          return null;
        }

        matched.add(ei);
        return matched;
      }

//        matched.addAll(ei.getOverrideProxies());
      if (cs.uid == null) {
        // Not valid
        return null;
      }

      BwEvent ev = ei.getEvent();

      // UID must match
      if (!cs.uid.equals(ev.getUid())) {
        continue;
      }

      if (cs.recurrenceId == null) {
        // Master only unless all=true
//        if ((ev.getRecurrenceId() == null) || sel.isAll()) {
        if (ev.getRecurrenceId() == null) {
          matched.add(ei);
        }
        continue;
      }

      // Looking for override
      if ((ev.getRecurrenceId() != null) &&
           ev.getRecurrenceId().equals(cs.recurrenceId.getDate())) {
        matched.add(ei);
        continue;
      }
    }

    return matched;
  }

  private static class CompSelector {
    String uid;
    BwDateTime recurrenceId;
  }

  private CompSelector getCompSelector(final BaseComponentType selComp) throws WebdavException {
    CompSelector cs = new CompSelector();

    for (JAXBElement<? extends BasePropertyType> prop:
      selComp.getProperties().getBasePropertyOrTzid()) {
      if (prop.getName().equals(XcalTags.uid)) {
        cs.uid = ((UidPropType)prop.getValue()).getText();

        if ((cs.uid != null) && (cs.recurrenceId != null)) {
          return cs;
        }

        continue;
      }

      if (prop.getName().equals(XcalTags.recurrenceId)) {
        RecurrenceIdPropType rid = (RecurrenceIdPropType)prop.getValue();
        XcalUtil.DtTzid dtTzid = XcalUtil.getDtTzid(rid);

        try {
          cs.recurrenceId = BwDateTime.makeBwDateTime(dtTzid.dateOnly,
                                                      dtTzid.dt,
                                                      dtTzid.tzid);
        } catch (RuntimeException cfe) {
          throw new WebdavException(cfe);
        }

        if (cs.uid != null) {
          return cs;
        }

        continue;
      }
    } // for

    return cs;
  }

  private UpdateResult addOverride(final EventInfo ei,
                              final ComponentReferenceType sel) throws WebdavException {
    try {
      new IcalTranslator(cb).addOverride(ei, sel.getBaseComponent());

      return UpdateResult.getOkResult();
    } catch (Throwable t) {
      throw new WebdavException(t);
    }
  }

  private UpdateResult removeOverride(final EventInfo ei,
                              final ComponentReferenceType sel) throws WebdavException {
    return new UpdateResult("unimplemented - remove override");
  }

  private UpdateResult addSubComp(final EventInfo ei,
                              final ComponentReferenceType sel) throws WebdavException {
    try {
      BwEvent ev = ei.getEvent();
      int etype = ev.getEntityType();

      BaseComponentType bc = sel.getBaseComponent().getValue();

      if (bc instanceof ValarmType) {
        if ((etype != IcalDefs.entityTypeEvent) ||
            (etype != IcalDefs.entityTypeTodo)) {
          return new UpdateResult("Invalid entity type for alarm add");
        }

        BwAlarm al = Xalarms.toBwAlarm((ValarmType)bc, false);
        if (al == null) {
          return new UpdateResult("Invalid alarm for add");
        }

        ev.addAlarm(al);
        return UpdateResult.getOkResult();
      }

      return new UpdateResult("Invalid entity type for add");
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private UpdateResult removeSubComp(final EventInfo ei,
                              final ComponentReferenceType sel) throws WebdavException {
    Component subc = findSubComponent(ei, sel.getBaseComponent().getValue());
    if (subc == null) {
      return new UpdateResult("Invalid sub-component selection for remove");
    }

    ei.getAlarms().remove(subc.getAlarm());
    return UpdateResult.getOkResult();
  }

  private UpdateResult updateSubComp(final EventInfo ei,
                                     final ComponentSelectionType sel) throws WebdavException {
    Component subc = findSubComponent(ei, sel.getBaseComponent().getValue());
    if (subc == null) {
      return new UpdateResult("Invalid sub-component selection for update");
    }

    return updateEventProperties(ei, subc, sel.getProperties());
  }

  private Component findSubComponent(final EventInfo ei,
                              final BaseComponentType bc) throws WebdavException {
    try {
      BwEvent ev = ei.getEvent();
      int etype = ev.getEntityType();

      if (bc instanceof ValarmType) {
        if ((etype != IcalDefs.entityTypeEvent) ||
            (etype != IcalDefs.entityTypeTodo)) {
          return null;
        }

        /* Look for the alarm - we match on the whole component */
        BwAlarm matched = null;
        BwAlarm pattern = Xalarms.toBwAlarm((ValarmType)bc, false);

        if ((pattern == null) || (ev.getNumAlarms() == 0)) {
          return null;
        }

        for (BwAlarm al: ev.getAlarms()) {
          if (al.matches(pattern)) {
            if (matched != null) {
              return null; // Multiple matches - bad
            }

            matched = al;
          }
        }

        return new PropertyUpdateComponent(ei, matched);
      }

      return null;
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  /**
   * @param eis
   * @param sel - matching the "properties" element
   * @return UpdateResult
   * @throws WebdavException
   */
  private UpdateResult updateEventsProperties(final List<EventInfo> eis,
                                         final PropertiesSelectionType sel) throws WebdavException {
    for (final EventInfo ei: eis) {
      final UpdateResult ur = updateEventProperties(ei, null, sel);
      if (!ur.getOk()) {
        return ur;
      }
    }

    return UpdateResult.getOkResult();
  }

  /** The current selector is for the event properties.
   *
   * <p>We might have one or more updates - add or remove
   *
   * <p>We might also have one or more selects which allow for change of values
   *
   * @param ei
   * @param subComponent - non null if this is a sub-component update
   * @param sel - matching the "properties" element
   * @return true for updated OK
   * @throws WebdavException
   */
  private UpdateResult updateEventProperties(final EventInfo ei,
                                             final Component subComponent,
                                             final PropertiesSelectionType sel) throws WebdavException {
    /* First deal with all the changes
     */

    for (final PropertySelectionType psel: sel.getProperty()) {
      /* psel represents a selection on a property which must exist and for
       * which we must have an updater.
       *
       * We may be applying changes to the property with a change update and/or
       * updating the parameters through one or more a selections on the
       * parameters.
       */
      final BasePropertyType bprop;
      final QName pname;

      if (psel.getBaseProperty() == null) {
        return new UpdateResult("No selection property supplied");
      }

      bprop = psel.getBaseProperty().getValue();
      pname = psel.getBaseProperty().getName();

      final PropertyUpdater pu = getUpdater(bprop);
      if (pu == null) {
        return new UpdateResult("No updater for property: " + pname);
      }

      PropertyUpdateInfo ui = new PropertyUpdateInfo(bprop, pname, cb,
                                                     ei,
                                                     subComponent,
                                                     state,
                                                     userHref);
      UpdateResult ur = null;
      /* There is possibly no change - for example we are changing the tzid for
       * the dtstart
       */
      if (psel.getChange() != null) {
        ur = ui.setChange(psel.getChange());

        if (ur != null) {
          return ur;
        }
      }

      /* Look for updates to property parameters and add them to the property
       * updater info.
       */
      if (psel.getParameters() != null) {
        ui.setParameterUpdates(psel.getParameters());
      }

      // There may be no change
      ur = pu.applyUpdate(ui);
      if (!ur.getOk()) {
        return ur;
      }
    }

    /* Next adds
     */
    UpdateResult ur = addRemove(ei, subComponent, true,  sel.getAdd(), cb);
    if (!ur.getOk()) {
      return ur;
    }

    /* Next removes
     */

    ur = addRemove(ei, subComponent, false,  sel.getRemove(), cb);
    if (!ur.getOk()) {
      return ur;
    }

    /* We now need to validate the result to ensure the changes leave a
     * consistent entity. Date/time changes in particular can have a number
     * of side effects.
     */

    ur = validateDates(ei);
    if (!ur.getOk()) {
      return ur;
    }

    if (debug()) {
      ei.getChangeset(userHref).dumpEntries();
    }

    return UpdateResult.getOkResult();
  }

  private UpdateResult validateDates(final EventInfo ei) throws WebdavException {
    DatesState ds = (DatesState)state.get(DatesState.stateName);
    if (ds == null) {
      return UpdateResult.getOkResult();
    }

    BwEvent ev = ei.getEvent();
    boolean task = ev.getEntityType() == IcalDefs.entityTypeTodo;
    PropertyInfoIndex endPi;
    ChangeTable chg = ei.getChangeset(userHref);

    if (task) {
      endPi = PropertyInfoIndex.DUE;
    } else {
      endPi = PropertyInfoIndex.DTEND;
    }

    /* We maintain both end and duration - if either changed we need to adjust
     * the other.
     */

    try {
      boolean scheduleReply = ev.getScheduleMethod() == ScheduleMethods.methodTypeReply;
      // No dates valid for reply

      if (ds.start == null) {
        if (!scheduleReply && !task) {
          return new UpdateResult("org.bedework.error.nostartdate");
        }

        /* A todo can have no date and time. set start to now, end to
         * many years from now and the noStart flag.
         *
         * Such an entry has to appear only on the current day.
         */
        if (ds.end != null) {
          ds.start = ds.end;
        } else {
          Date now = new Date(new java.util.Date().getTime());
          DtStart dtStart = new DtStart(now);
          dtStart.getParameters().add(Value.DATE);
          ds.start = BwDateTime.makeBwDateTime(dtStart);
        }

        if (!ev.getNoStart() ||
            !CalFacadeUtil.eqObjval(ev.getDtstart(), ds.start)) {
          chg.changed(PropertyInfoIndex.DTSTART, ev.getDtstart(), null);
          ev.setDtstart(ds.start);
          ev.setNoStart(true);
        }
      } else if (ev.getNoStart() || !ds.start.equals(ev.getDtstart())) {
        chg.changed(PropertyInfoIndex.DTSTART, ev.getDtstart(), ds.start);
        ev.setNoStart(false);
        ev.setDtstart(ds.start);
      }

      char endType = StartEndComponent.endTypeNone;

      if (ds.end != null) {
        if ((ev.getEndType() != StartEndComponent.endTypeDate) ||
            !CalFacadeUtil.eqObjval(ev.getDtend(), ds.end)) {
          chg.changed(endPi, ev.getDtend(), ds.end);
          endType = StartEndComponent.endTypeDate;
          ev.setDtend(ds.end);
        }
      } else if (scheduleReply || task) {
        Dur years = new Dur(520); // about 10 years
        Date now = new Date(new java.util.Date().getTime());
        DtEnd dtEnd = new DtEnd(new Date(years.getTime(now)));
        dtEnd.getParameters().add(Value.DATE);
        ds.end = BwDateTime.makeBwDateTime(dtEnd);
        if (!CalFacadeUtil.eqObjval(ev.getDtend(), ds.end)) {
          chg.changed(endPi, ev.getDtend(), ds.end);
          ev.setDtend(ds.end);
        }
      }

      /** If we were given a duration store it in the event and calculate
          an end to the event - which we should not have been given.
       */
      if (ds.duration != null) {
        if (endType != StartEndComponent.endTypeNone) {
          if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
            // Apple is sending both - duration indicates the minimum
            // freebusy duration. Ignore for now.
          } else {
            return new UpdateResult(CalFacadeException.endAndDuration);
          }
        }

        endType = StartEndComponent.endTypeDuration;

        if (!ds.duration.equals(ev.getDuration())) {
          chg.changed(PropertyInfoIndex.DURATION, ev.getDuration(), ds.duration);
          ev.setDuration(ds.duration);
        }

        ev.setDtend(BwDateTime.makeDateTime(ev.getDtstart().makeDtStart(),
                                            ev.getDtstart().getDateType(),
                                            new Dur(ds.duration)));
      } else if (!scheduleReply &&
                 (endType == StartEndComponent.endTypeNone) &&
                 !task) {
        /* No duration and no end specified.
         * Set the end values to the start values + 1 for dates
         */
        boolean dateOnly = ev.getDtstart().getDateType();
        Dur dur;

        if (dateOnly) {
          dur = new Dur(1, 0, 0, 0); // 1 day
        } else {
          dur = new Dur(0, 0, 0, 0); // No duration
        }
        BwDateTime bwDtEnd = BwDateTime.makeDateTime(ev.getDtstart().makeDtStart(),
                                                     dateOnly, dur);
        if (!CalFacadeUtil.eqObjval(ev.getDtend(), bwDtEnd)) {
          chg.changed(endPi, ev.getDtend(), bwDtEnd);
          ev.setDtend(bwDtEnd);
        }
      }

      if ((endType != StartEndComponent.endTypeDuration) &&
          (ev.getDtstart() != null) &&
          (ev.getDtend() != null)) {
        // Calculate a duration
        String durVal = BwDateTime.makeDuration(ev.getDtstart(),
                                                ev.getDtend()).toString();
        if (!durVal.equals(ev.getDuration())) {
          chg.changed(PropertyInfoIndex.DURATION, ev.getDuration(), durVal);
          ev.setDuration(durVal);
        }
      }

      ev.setEndType(endType);

      return UpdateResult.getOkResult();
    } catch (CalFacadeException cfe) {
      throw new WebdavException(cfe);
    }
  }

  private UpdateResult addRemove(final EventInfo ei,
                                 final Component subComponent,
                                 final boolean add,
                                 final List<PropertyReferenceType> refs,
                                 final IcalCallback cb) throws WebdavException {
    for (PropertyReferenceType r: refs) {
      BasePropertyType bprop;
      QName pname;

      if (r.getBaseProperty() == null) {
        return new UpdateResult("No new value for add");
      }

      bprop = r.getBaseProperty().getValue();
      pname = r.getBaseProperty().getName();

      PropertyUpdater pu = getUpdater(bprop);
      if (pu == null) {
        return new UpdateResult("No updater for property: " + pname);
      }

      PropertyUpdateInfo ui = new PropertyUpdateInfo(bprop,
                                                     pname,
                                                     cb,
                                                     ei,
                                                     subComponent,
                                                     state,
                                                     userHref);

      UpdateResult ur;

      if (add) {
        ur = ui.setAdd(r);
      } else {
        ur = ui.setRemove(r);
      }

      if (ur != null) {
        return ur;
      }

      ur = pu.applyUpdate(ui);
      if (!ur.getOk()) {
        return ur;
      }
    }

    return UpdateResult.getOkResult();
  }

  private PropertyUpdater getUpdater(final Object o) {
    return propUpdaterRegistry.getUpdater(o);
  }

  static class PropertyUpdateComponent implements PropertyUpdater.Component {
    private Component parent;

    private EventInfo ei;

    private BwAlarm alarm;

    PropertyUpdateComponent(final EventInfo ei) {
      this.ei = ei;
    }

    PropertyUpdateComponent(final EventInfo ei,
                            final BwAlarm alarm) {
      this.ei = ei;
      this.alarm = alarm;

      parent = new PropertyUpdateComponent(ei);
    }

    @Override
    public Component getParent() {
      return parent;
    }

    @Override
    public EventInfo getEi() {
      return ei;
    }

    @Override
    public BwEvent getEvent() {
      return ei.getEvent();
    }

    @Override
    public BwAlarm getAlarm() {
      return alarm;
    }
  }

  static class PropertyUpdateInfo implements PropertyUpdater.UpdateInfo {
    private boolean add;
    private boolean change;
    private boolean remove;

    private TzGetter tzs = id -> Timezones.getTz(id);

    private BasePropertyType prop;

    private BasePropertyType updprop;

    private QName propName;

    private PropertyInfoIndex pi;

    private IcalCallback cb;

    private EventInfo ei;

    private Component subComponent;

    private ChangeTable chg;

    private Properties state;

    private String userHref;

    private List<ParameterUpdater.UpdateInfo> paramUpdates =
        new ArrayList<>();

    PropertyUpdateInfo(final BasePropertyType prop,
                       final QName pname,
                       final IcalCallback cb,
                       final EventInfo ei,
                       final Component subComponent,
                       final Properties state,
                       final String userHref) {
      this.prop = prop;
      propName = pname;
      this.cb = cb;
      this.ei = ei;
      this.subComponent = subComponent;
      this.state = state;
      this.userHref = userHref;

      if (prop instanceof XBedeworkWrapperPropType) {
        pi = PropertyInfoIndex.XPROP;
      } else {
        pi = PropertyInfoIndex.fromName(pname.getLocalPart());
      }
      if (pi == null) {
        throw new RuntimeException("unknown property " + pname);
      }

      chg = ei.getChangeset(userHref);
    }

    UpdateResult setAdd(final PropertyReferenceType r) {
      add = true;
      JAXBElement<? extends BasePropertyType> baseProperty = r.getBaseProperty();

      if (baseProperty == null) {
        return new UpdateResult("No add property supplied for " + propName);
      }

      if (!baseProperty.getValue().getClass().isInstance(prop)) {
        return new UpdateResult("Selection property " + propName +
                                " not same as " +
                                baseProperty.getName());
      }

      updprop = baseProperty.getValue();

      return null;
    }

    UpdateResult setRemove(final PropertyReferenceType r) {
      remove = true;

      return null;
    }

    UpdateResult setChange(final PropertyReferenceType ct) {
      change = true;
      JAXBElement<? extends BasePropertyType> baseProperty = ct.getBaseProperty();

      if (baseProperty == null) {
        return new UpdateResult("No add property supplied for " + propName);
      }

      if (!baseProperty.getValue().getClass().isInstance(prop)) {
        return new UpdateResult("Selection property " + propName +
                                " not same as " +
                                baseProperty.getName());
      }

      updprop = baseProperty.getValue();

      return null;
    }

    UpdateResult setParameterUpdates(final ParametersSelectionType params) {
      /* First selections for changes */
      for (ParameterSelectionType psel: params.getParameter()) {
        QName parname = psel.getBaseParameter().getName();
        BaseParameterType bselparam = psel.getBaseParameter().getValue();

        ParameterUpdateInfo pui = new ParameterUpdateInfo(this, bselparam,
                                                          parname);
        UpdateResult ur = pui.setChange(psel.getChange());
        if (ur != null) {
          return ur;
        }

        getParamUpdates().add(pui);
      }

      /* Now adds and removes */
      UpdateResult ur = addRemove(true, params.getAdd());
      if (ur != null) {
        return ur;
      }

      return addRemove(false, params.getRemove());
    }

    private UpdateResult addRemove(final boolean add,
                                   final List<ParameterReferenceType> refs) {
      for (ParameterReferenceType r: refs) {
        BaseParameterType bparam;
        QName pname;

        if (r.getBaseParameter() == null) {
          return new UpdateResult("No parameter value for add/remove");
        }

        bparam = r.getBaseParameter().getValue();
        pname = r.getBaseParameter().getName();

        ParameterUpdateInfo pui = new ParameterUpdateInfo(this, bparam,
                                                          pname);

        UpdateResult ur;

        if (add) {
          ur = pui.setAdd(r);
        } else {
          ur = pui.setRemove(r);
        }

        if (ur != null) {
          return ur;
        }

        getParamUpdates().add(pui);
      }

      return null;
    }

    @Override
    public boolean isAdd() {
      return add;
    }

    @Override
    public boolean isChange() {
      return change;
    }

    @Override
    public boolean isRemove() {
      return remove;
    }

    @Override
    public BasePropertyType getProp() {
      return prop;
    }

    @Override
    public BasePropertyType getUpdprop() {
      return updprop;
    }

    @Override
    public QName getPropName() {
      return propName;
    }

    @Override
    public IcalCallback getIcalCallback() {
      return cb;
    }

    @Override
    public TzGetter getTzs() {
      return tzs;
    }

    @Override
    public EventInfo getEi() {
      return ei;
    }

    @Override
    public BwEvent getEvent() {
      return ei.getEvent();
    }

    @Override
    public Component getSubComponent() {
      return subComponent;
    }

    @Override
    public ChangeTableEntry getCte() {
      return chg.getEntry(pi);
    }

    @Override
    public ChangeTableEntry getCte(final PropertyInfoIndex pi) {
      return chg.getEntry(pi);
    }

    @Override
    public void saveState(final String name, final Object val) {
      state.put(name, val);
    }

    @Override
    public Object getState(final String name) {
      return state.get(name);
    }

    @Override
    public List<ParameterUpdater.UpdateInfo> getParamUpdates() {
      return paramUpdates;
    }

    @Override
    public String getUserHref() {
      return userHref;
    }
  }

  static class ParameterUpdateInfo implements ParameterUpdater.UpdateInfo {
    private boolean add;
    private boolean change;
    private boolean remove;

    private PropertyUpdateInfo propInfo;

    private BaseParameterType param;

    private BaseParameterType updparam;

    private QName paramName;

    ParameterUpdateInfo(final PropertyUpdateInfo propInfo,
                        final BaseParameterType param,
                        final QName pname) {
      this.propInfo = propInfo;
      paramName = pname;
      this.param = param;
    }

    UpdateResult setAdd(final ParameterReferenceType r) {
        add = true;
        JAXBElement<? extends BaseParameterType> baseParam = r.getBaseParameter();

      if (baseParam == null) {
        return new UpdateResult("No update parameter supplied for " + paramName);
      }

      if (!baseParam.getName().equals(paramName)) {
        return new UpdateResult("Selection parameter name " + paramName +
                                " not equal to " +
                                baseParam.getName());
      }

      updparam = baseParam.getValue();

      return null;
    }

    UpdateResult setRemove(final ParameterReferenceType r) {
      remove = true;

      return null;
    }

    UpdateResult setChange(final ParameterReferenceType r) {
      change = true;
      JAXBElement<? extends BaseParameterType> baseParam = r.getBaseParameter();

      if (baseParam == null) {
        return new UpdateResult("No update parameter supplied for " + paramName);
      }

      if (!baseParam.getName().equals(paramName)) {
        return new UpdateResult("Selection parameter name " + paramName +
                                " not equal to " +
                                baseParam.getName());
      }

      updparam = baseParam.getValue();

      return null;
    }

    @Override
    public boolean isAdd() {
      return add;
    }

    @Override
    public boolean isChange() {
      return change;
    }

    @Override
    public boolean isRemove() {
      return remove;
    }

    @Override
    public PropertyUpdater.UpdateInfo getPropInfo() {
      return propInfo;
    }

    @Override
    public BaseParameterType getParam() {
      return param;
    }

    @Override
    public BaseParameterType getUpdparam() {
      return updparam;
    }

    @Override
    public QName getParamName() {
      return paramName;
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
