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
package org.bedework.convert.xcal;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.ActionPropType;
import ietf.params.xml.ns.icalendar_2.ArrayOfParameters;
import ietf.params.xml.ns.icalendar_2.ArrayOfProperties;
import ietf.params.xml.ns.icalendar_2.AttachPropType;
import ietf.params.xml.ns.icalendar_2.AttendeePropType;
import ietf.params.xml.ns.icalendar_2.BaseComponentType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.DescriptionPropType;
import ietf.params.xml.ns.icalendar_2.DurationPropType;
import ietf.params.xml.ns.icalendar_2.RelatedParamType;
import ietf.params.xml.ns.icalendar_2.RepeatPropType;
import ietf.params.xml.ns.icalendar_2.SummaryPropType;
import ietf.params.xml.ns.icalendar_2.TriggerPropType;
import ietf.params.xml.ns.icalendar_2.ValarmType;

import java.math.BigInteger;
import java.util.List;

import javax.xml.bind.JAXBElement;

/** Class to provide utility methods for translating  between XML and Bedework
 * alarm representations
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class Xalarms extends Xutil {
  /**
   * @param ev
   * @param val
   * @param pattern
   * @param masterClass
   * @return ValarmType
   * @throws CalFacadeException
   */
  public static ValarmType toXAlarm(final BwEvent ev,
                                    final BwAlarm val,
                                    final BaseComponentType pattern,
                                    final Class masterClass) throws CalFacadeException {
    try {
      ValarmType alarm = new ValarmType();

      int atype = val.getAlarmType();

      alarm.setProperties(new ArrayOfProperties());
      List<JAXBElement<? extends BasePropertyType>> pl = alarm.getProperties().getBasePropertyOrTzid();

      if (emit(pattern, masterClass, ValarmType.class, ActionPropType.class)) {
        ActionPropType a = new ActionPropType();
        a.setText(BwAlarm.alarmTypes[val.getAlarmType()]);
        pl.add(of.createAction(a));
      }

      if (emit(pattern, masterClass, ValarmType.class, TriggerPropType.class)) {
        TriggerPropType t = new TriggerPropType();
        if (val.getTriggerDateTime()) {
          //t.setDateTime(val.getTrigger());
          t.setDateTime(XcalUtil.getXMlUTCCal(val.getTrigger()));
        } else {
          t.setDuration(val.getTrigger());
          if (!val.getTriggerStart()) {
            ArrayOfParameters pars = getAop(t);

            RelatedParamType r = new RelatedParamType();
            r.setText(IcalDefs.alarmTriggerRelatedEnd);
            JAXBElement<RelatedParamType> param = of.createRelated(r);
            pars.getBaseParameter().add(param);
          }
        }

        pl.add(of.createTrigger(t));
      }

      if (emit(pattern, masterClass, ValarmType.class, DurationPropType.class)) {
        if (val.getDuration() != null) {
          DurationPropType dur = new DurationPropType();
          dur.setDuration(val.getDuration());

          pl.add(of.createDuration(dur));

          RepeatPropType rep = new RepeatPropType();
          rep.setInteger(BigInteger.valueOf(val.getRepeat()));

          pl.add(of.createRepeat(rep));
        }
      }

      /* Description */
      if ((atype == BwAlarm.alarmTypeDisplay) ||
          (atype == BwAlarm.alarmTypeEmail) ||
          (atype == BwAlarm.alarmTypeProcedure)) {
        // Both require description
        String desc = val.getDescription();
        if (desc == null) {
          if (ev != null) {
            if (ev.getDescription() != null) {
              desc = ev.getDescription();
            } else {
              desc = ev.getSummary();
            }
          }
        }

        if (desc == null) {
          desc = " ";
        }

        DescriptionPropType d = new DescriptionPropType();
        d.setText(desc);

        pl.add(of.createDescription(d));
      }

      /* Summary */
      if (atype == BwAlarm.alarmTypeEmail) {
        SummaryPropType s = new SummaryPropType();
        s.setText(val.getSummary());

        pl.add(of.createSummary(s));
      }

      /* Attach */

      if ((atype == BwAlarm.alarmTypeAudio) ||
          (atype == BwAlarm.alarmTypeEmail) ||
          (atype == BwAlarm.alarmTypeProcedure)) {
        if (val.getAttach() != null) {
          AttachPropType a = new AttachPropType();

          a.setUri(val.getAttach());

          pl.add(of.createAttach(a));
        }
      }

      /* Attendees */
      if (atype == BwAlarm.alarmTypeEmail) {
        if (val.getNumAttendees() > 0) {
          for (BwAttendee att: val.getAttendees()) {
            pl.add(of.createAttendee(ToXEvent.makeAttendee(att)));
          }
        }
      }

      if (val.getNumXproperties() > 0) {
        /* This alarm has x-props */

      }

      return alarm;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** The generated alarm may not be a valid alarm if it is being used as a
   * selector. It must have at least the action as a selector.
   *
   * @param alarm
   * @param validate - true if alarm must be valid and complete
   * @return ValarmType
   * @throws CalFacadeException
   */
  public static BwAlarm toBwAlarm(final ValarmType alarm,
                                  final boolean validate) throws CalFacadeException {
    BwAlarm ba = new BwAlarm();

    /* ============ Action =================== */
    ActionPropType action = (ActionPropType)XcalUtil.findProperty(alarm,
                                                                  XcalTags.action);

    if (action == null) {
      throw new CalFacadeException("Invalid alarm - no action");
    }

    String actionVal = action.getText().toUpperCase();
    int atype = -1;

    for (int i = 0; i < BwAlarm.alarmTypes.length; i++) {
      if (actionVal.equals(BwAlarm.alarmTypes[i])) {
        atype = i;
        break;
      }
    }

    if (atype < 0) {
      throw new CalFacadeException("Unhandled alarm action");
    }

    ba.setAlarmType(atype);

    /* ============ Trigger =================== */
    TriggerPropType tr = (TriggerPropType)XcalUtil.findProperty(alarm,
                                                                XcalTags.trigger);

    if (tr == null) {
      if (validate) {
        throw new CalFacadeException("Invalid alarm - no action");
      }
    } else {
      if (tr.getDateTime() != null) {
        ba.setTrigger(XcalUtil.getIcalFormatDateTime(tr.getDateTime()));
        ba.setTriggerDateTime(true);
      } else {
        ba.setTrigger(tr.getDuration());

        RelatedParamType r = (RelatedParamType)XcalUtil.findParam(tr, XcalTags.related);

        ba.setTriggerStart((r == null) ||
                           (r.getText().toUpperCase().equals("START")));
      }
    }

    /* ============ Duration =================== */
    DurationPropType dur = (DurationPropType)XcalUtil.findProperty(alarm,
                                                                  XcalTags.duration);

    if (dur != null) {
      // MUST have repeat
      RepeatPropType rep = (RepeatPropType)XcalUtil.findProperty(alarm,
                                                                 XcalTags.repeat);

      ba.setDuration(dur.getDuration());

      if (rep == null) {
        if (validate) {
          throw new CalFacadeException("Invalid alarm - no repeat");
        }
      } else {
        ba.setRepeat(rep.getInteger().intValue());
      }
    }

    /* ============ Description ============ */
    if ((atype == BwAlarm.alarmTypeDisplay) ||
        (atype == BwAlarm.alarmTypeEmail) ||
        (atype == BwAlarm.alarmTypeProcedure)) {
      DescriptionPropType desc = (DescriptionPropType)XcalUtil.findProperty(alarm,
                                                                     XcalTags.description);

      if (desc != null) {
        ba.setDescription(desc.getText());
      }
    }

    /* ============ Summary ============ */
    if (atype == BwAlarm.alarmTypeEmail) {
      SummaryPropType s = (SummaryPropType)XcalUtil.findProperty(alarm,
                                                                 XcalTags.summary);
      if (s != null) {
        ba.setSummary(s.getText());
      }
    }

    /* ============ Attach ============ */

    if ((atype == BwAlarm.alarmTypeAudio) ||
        (atype == BwAlarm.alarmTypeEmail) ||
        (atype == BwAlarm.alarmTypeProcedure)) {
      AttachPropType a = (AttachPropType)XcalUtil.findProperty(alarm,
                                                                XcalTags.attach);

      // XXX Only handle URI
      // XXX Onl handle 1 attachment
      if ((a != null) && (a.getUri() != null)) {
        ba.setAttach(a.getUri());
      }
    }

    if (atype == BwAlarm.alarmTypeEmail) {
      for (JAXBElement<? extends BasePropertyType> bpel:
             alarm.getProperties().getBasePropertyOrTzid()) {
        if (!bpel.getName().equals(XcalTags.attendee)) {
          continue;
        }

        AttendeePropType attp = (AttendeePropType)bpel.getValue();

        BwAttendee batt = new BwAttendee();

        batt.setAttendeeUri(attp.getCalAddress());

        ba.addAttendee(batt);
      }
    }

    return ba;
  }
}
