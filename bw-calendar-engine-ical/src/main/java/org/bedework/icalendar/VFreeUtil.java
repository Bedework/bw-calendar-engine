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

import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.parameter.FbType;
import net.fortuna.ical4j.model.property.Comment;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Uid;

import java.util.Collection;

/**
 *   @author Mike Douglass   douglm   rpi.edu
 */
public class VFreeUtil extends IcalUtil {

  /** Make a VFreeBusy object from a BwFreeBusy.
   */
  /**
   * @param val
   * @return VFreeBusy
   * @throws CalFacadeException
   */
  public static VFreeBusy toVFreeBusy(final BwEvent val) throws CalFacadeException {
    try {
      VFreeBusy vfb = new VFreeBusy(IcalUtil.makeDateTime(val.getDtstart()),
                                    IcalUtil.makeDateTime(val.getDtend()));

      PropertyList pl = vfb.getProperties();
      Property prop;

      /* ------------------- Attendees -------------------- */
      if (val.getNumAttendees() > 0) {
        for (BwAttendee att: val.getAttendees()) {
          pl.add(setAttendee(att));
        }
      }

      /* ------------------- Comments -------------------- */

      if (val.getNumComments() > 0) {
        for (BwString str: val.getComments()) {
          // LANG
          pl.add(new Comment(str.getValue()));
        }
      }

      /* ------------------- Dtstamp -------------------- */

      if (val.getDtstamp() != null) {
        DtStamp dts = (DtStamp)pl.getProperty(Property.DTSTAMP);

        if (dts == null) {
          prop = new DtStamp(new DateTime(val.getDtstamp()));
//      if (pars.includeDateTimeProperty) {
//      prop.getParameters().add(Value.DATE_TIME);
//      }
          pl.add(prop);
        } else {
          dts.setDateTime(new DateTime(val.getDtstamp()));
        }
      }

      /* ------------------- freebusy -------------------- */

      Collection<BwFreeBusyComponent> times = val.getFreeBusyPeriods();

      if (times != null) {
        for (BwFreeBusyComponent fbc: times) {
          FreeBusy fb = new FreeBusy();

          int type = fbc.getType();
          if (type == BwFreeBusyComponent.typeBusy) {
            addParameter(fb, FbType.BUSY);
          } else if (type == BwFreeBusyComponent.typeFree) {
            addParameter(fb, FbType.FREE);
          } else if (type == BwFreeBusyComponent.typeBusyUnavailable) {
            addParameter(fb, FbType.BUSY_UNAVAILABLE);
          } else if (type == BwFreeBusyComponent.typeBusyTentative) {
            addParameter(fb, FbType.BUSY_TENTATIVE);
          } else {
            throw new CalFacadeException("Bad free-busy type " + type);
          }

          PeriodList pdl =  fb.getPeriods();

          for (Period p: fbc.getPeriods()) {
            // XXX inverse.ca plugin cannot handle durations.
            Period np = new Period(p.getStart(), p.getEnd());
            pdl.add(np);
          }

          pl.add(fb);
        }
      }

      /* ------------------- Organizer -------------------- */

      BwOrganizer org = val.getOrganizer();
      if (org != null) {
        pl.add(setOrganizer(org));
      }


      /* ------------------- Uid -------------------- */

      if (val.getUid() != null) {
        pl.add(new Uid(val.getUid()));
      }

      return vfb;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}

