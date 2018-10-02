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
package org.bedework.icalendar;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAlarm.TriggerVal;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VAlarm;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.parameter.Related;
import net.fortuna.ical4j.model.property.Action;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.Repeat;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.XProperty;

import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/** Class to provide utility methods for handline VAlarm ical4j classes
 *
 * @author Mike Douglass   douglm rpi.edu
 */
public class VAlarmUtil extends IcalUtil {

  /** If there are any alarms for this component add them to the events alarm
   * collection
   *
   * @param cb          IcalCallback object
   * @param val
   * @param ev
   * @param currentPrincipal - href for current authenticated user
   * @param chg
   * @throws CalFacadeException
   */
  public static void processComponentAlarms(final IcalCallback cb,
                                            final Component val,
                                            final BwEvent ev,
                                            final String currentPrincipal,
                                            final ChangeTable chg) throws CalFacadeException {
    try {
      ComponentList als = null;

      if (val instanceof VEvent) {
        als = ((VEvent)val).getAlarms();
      } else if (val instanceof VToDo) {
        als = ((VToDo)val).getAlarms();
      } else if (val instanceof VPoll) {
        als = ((VPoll)val).getAlarms();
      } else {
        return;
      }

      if ((als == null) || als.isEmpty()) {
        return;
      }

      for (Object o: als) {
        if (!(o instanceof VAlarm)) {
          throw new IcalMalformedException("Invalid alarm list");
        }

        VAlarm va = (VAlarm)o;

        PropertyList pl = va.getProperties();

        if (pl == null) {
          // Empty VAlarm
          throw new IcalMalformedException("Invalid alarm list");
        }

        Property prop;
        BwAlarm al;

        /* XXX Handle mozilla alarm stuff in a way that might work better with other clients.
         *
         */

        prop = pl.getProperty("X-MOZ-LASTACK");
        boolean mozlastAck = prop != null;

        String mozSnoozeTime = null;
        if (mozlastAck) {
          prop = pl.getProperty("X-MOZ-SNOOZE-TIME");

          if (prop == null) {
            // lastack and no snooze - presume dismiss so delete alarm
            continue;
          }

          mozSnoozeTime = prop.getValue(); // UTC time
        }

        // All alarm types require action and trigger

        prop = pl.getProperty(Property.ACTION);
        if (prop == null) {
          throw new IcalMalformedException("Invalid alarm");
        }

        String actionStr = prop.getValue();

        TriggerVal tr = getTrigger(pl, "NONE".equals(actionStr));

        if (mozSnoozeTime != null) {
          tr.trigger = mozSnoozeTime;
          tr.triggerDateTime = true;
          tr.triggerStart = false;
        }

        DurationRepeat dr = getDurationRepeat(pl);

        if ("EMAIL".equals(actionStr)) {
          al = BwAlarm.emailAlarm(ev, ev.getCreatorHref(),
                                  tr,
                                  dr.duration, dr.repeat,
                                  getOptStr(pl, "ATTACH"),
                                  getReqStr(pl, "DESCRIPTION"),
                                  getReqStr(pl, "SUMMARY"),
                                  null);

          Iterator<?> atts = getReqStrs(pl, "ATTENDEE");

          while (atts.hasNext()) {
            al.addAttendee(getAttendee(cb, (Attendee)atts.next()));
          }
        } else if ("AUDIO".equals(actionStr)) {
          al = BwAlarm.audioAlarm(ev, ev.getCreatorHref(),
                                  tr,
                                  dr.duration, dr.repeat,
                                  getOptStr(pl, "ATTACH"));
        } else if ("DISPLAY".equals(actionStr)) {
          al = BwAlarm.displayAlarm(ev, ev.getCreatorHref(),
                                    tr,
                                    dr.duration, dr.repeat,
                                    getReqStr(pl, "DESCRIPTION"));
        } else if ("PROCEDURE".equals(actionStr)) {
          al = BwAlarm.procedureAlarm(ev, ev.getCreatorHref(),
                                      tr,
                                      dr.duration, dr.repeat,
                                      getReqStr(pl, "ATTACH"),
                                      getOptStr(pl, "DESCRIPTION"));
        } else if ("NONE".equals(actionStr)) {
          al = BwAlarm.noneAlarm(ev, ev.getCreatorHref(),
                                 tr,
                                 dr.duration, dr.repeat,
                                 getOptStr(pl, "DESCRIPTION"));
        } else {
          al = BwAlarm.otherAlarm(ev, ev.getCreatorHref(),
                                  actionStr,
                                  tr,
                                  dr.duration, dr.repeat,
                                  getOptStr(pl, "DESCRIPTION"));
        }

        /* Mozilla is add xprops to the containing event to set the snooze time.
         * Seems wrong - there could be multiple alarms.
         *
         * We possibly want to try this sort of trick..

        prop = pl.getProperty("X-MOZ-LASTACK");
        boolean mozlastAck = prop != null;

        String mozSnoozeTime = null;
        if (mozlastAck) {
          prop = pl.getProperty("X-MOZ-SNOOZE-TIME");

          if (prop == null) {
            // lastack and no snooze - presume dismiss so delete alarm
            continue;
          }

          mozSnoozeTime = prop.getValue(); // UTC time
        }
        ...

        TriggerVal tr = getTrigger(pl);

        if (mozSnoozeTime != null) {
          tr.trigger = mozSnoozeTime;
          tr.triggerDateTime = true;
          tr.triggerStart = false;
        }

         */

        Iterator it = pl.iterator();

        while (it.hasNext()) {
          prop = (Property)it.next();

          if (prop instanceof XProperty) {
            /* ------------------------- x-property --------------------------- */

            XProperty xp = (XProperty)prop;

            al.addXproperty(new BwXproperty(xp.getName(),
                                            xp.getParameters().toString(),
                                            xp.getValue()));
            continue;
          }

          if (prop instanceof Uid) {
            Uid p = (Uid)prop;

            al.addXproperty(BwXproperty.makeIcalProperty(p.getName(),
                                            p.getParameters().toString(),
                                            p.getValue()));
            continue;
          }
        }

        al.setEvent(ev);
        al.setOwnerHref(currentPrincipal);
        chg.addValue(PropertyInfoIndex.VALARM, al);
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Process any alarms.
   *
   * @param ev
   * @param comp
   * @param currentPrincipal - href for current authenticated user
   * @throws CalFacadeException
   */
  public static void processEventAlarm(final BwEvent ev,
                                       final Component comp,
                                       final String currentPrincipal) throws CalFacadeException {
    if (currentPrincipal == null) {
      // No alarms for unauthenticated users.
      return;
    }

    Collection<BwAlarm> als = ev.getAlarms();
    if ((als == null) || als.isEmpty()) {
      return;
    }

    ComponentList vals = null;

    if (comp instanceof VEvent) {
      vals = ((VEvent)comp).getAlarms();
    } else if (comp instanceof VToDo) {
      vals = ((VToDo)comp).getAlarms();
    } else {
      throw new CalFacadeException("org.bedework.invalid.component.type",
                                   comp.getName());
    }

    for (BwAlarm alarm: als) {
      /* Only add alarms for the current authenticated user */
      if (!currentPrincipal.equals(alarm.getOwnerHref())) {
        continue;
      }

      vals.add(setAlarm(ev, alarm));
    }
  }

  private static VAlarm setAlarm(final BwEvent ev,
                                 final BwAlarm val) throws CalFacadeException {
    try {
      VAlarm alarm = new VAlarm();

      int atype = val.getAlarmType();
      String action;

      if (atype != BwAlarm.alarmTypeOther) {
        action = BwAlarm.alarmTypes[atype];
      } else {
        List<BwXproperty> xps = val.getXicalProperties("ACTION");

        action = xps.get(0).getValue();
      }

      addProperty(alarm, new Action(action));

      if (val.getTriggerDateTime()) {
        DateTime dt = new DateTime(val.getTrigger());
        addProperty(alarm, new Trigger(dt));
      } else {
        Trigger tr = new Trigger(new Dur(val.getTrigger()));
        if (!val.getTriggerStart()) {
          addParameter(tr, Related.END);
        } else {
          // Not required - it's the default - but we fail some Cyrus tests otherwise
          // Apparently Cyrus now handles the default state correctly
          addParameter(tr, Related.START);
        }
        addProperty(alarm, tr);
      }

      if (val.getDuration() != null) {
        addProperty(alarm, new Duration(new Dur(val.getDuration())));
        addProperty(alarm, new Repeat(val.getRepeat()));
      }

      if (atype == BwAlarm.alarmTypeAudio) {
        if (val.getAttach() != null) {
          addProperty(alarm, new Attach(new URI(val.getAttach())));
        }
      } else if (atype == BwAlarm.alarmTypeDisplay) {
        /* This is required but somehow we got a bunch of alarms with no description
         * Is it possibly because of the rollback issue I (partially) fixed?
         */
        //checkRequiredProperty(val.getDescription(), "alarm-description");
        if (val.getDescription() != null) {
          addProperty(alarm, new Description(val.getDescription()));
        } else {
          addProperty(alarm, new Description(ev.getSummary()));
        }
      } else if (atype == BwAlarm.alarmTypeEmail) {
        if (val.getAttach() != null) {
          addProperty(alarm, new Attach(new URI(val.getAttach())));
        }
        checkRequiredProperty(val.getDescription(), "alarm-description");
        addProperty(alarm, new Description(val.getDescription()));
        checkRequiredProperty(val.getSummary(), "alarm-summary");
        addProperty(alarm, new Summary(val.getSummary()));

        if (val.getNumAttendees() > 0) {
          for (BwAttendee att: val.getAttendees()) {
            addProperty(alarm, setAttendee(att));
          }
        }
      } else if (atype == BwAlarm.alarmTypeProcedure) {
        checkRequiredProperty(val.getAttach(), "alarm-attach");
        addProperty(alarm, new Attach(new URI(val.getAttach())));

        if (val.getDescription() != null) {
          addProperty(alarm, new Description(val.getDescription()));
        }
      } else {
        if (val.getDescription() != null) {
          addProperty(alarm, new Description(val.getDescription()));
        }
      }

      if (val.getNumXproperties() > 0) {
        /* This event has x-props */

        IcalUtil.xpropertiesToIcal(alarm.getProperties(),
                                   val.getXproperties());
      }

      return alarm;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void checkRequiredProperty(final String val,
                                            final String name) throws CalFacadeException {
    if (val == null) {
      throw new CalFacadeException("org.bedework.icalendar.missing.required.property",
                                   name);
    }
  }
}
