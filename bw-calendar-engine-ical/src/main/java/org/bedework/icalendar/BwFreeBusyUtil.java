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
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;

import edu.rpi.cmt.calendar.IcalDefs;

import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.parameter.FbType;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Comment;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.Uid;

import java.util.Iterator;

/** Class to provide utility methods for translating to BwFreeBusy from ical4j classes
 *
 * @author Mike Douglass   douglm@rpi.edu
 */
public class BwFreeBusyUtil extends IcalUtil {
  /**
   * @param cb
   * @param val
   * @return BwFreeBusy
   * @throws CalFacadeException
   */
  public static EventInfo toFreeBusy(final IcalCallback cb,
                                     final VFreeBusy val) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    boolean debug = getLog().isDebugEnabled();

    try {
      PropertyList pl = val.getProperties();

      if (pl == null) {
        // Empty VEvent
        return null;
      }

      BwEvent fb = new BwEventObj();
      EventInfo ei = new EventInfo(fb);

      ChangeTable chg = ei.getChangeset(cb.getPrincipal().getPrincipalRef());

      fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);

      setDates(cb.getPrincipal().getPrincipalRef(),
               ei,
               (DtStart)pl.getProperty(Property.DTSTART),
               (DtEnd)pl.getProperty(Property.DTEND),
               (Duration)pl.getProperty(Property.DURATION));

      Iterator it = pl.iterator();

      while (it.hasNext()) {
        Property prop = (Property)it.next();

        String pval = prop.getValue();
        if ((pval != null) && (pval.length() == 0)) {
          pval = null;
        }

        if (prop instanceof Attendee) {
          /* ------------------- Attendee -------------------- */

          BwAttendee att = getAttendee(cb, (Attendee)prop);
          fb.addAttendee(att);
          chg.addValue(prop.getName(), att);
        } else if (prop instanceof Comment) {
          /* ------------------- Comment -------------------- */

          // LANG
          fb.addComment(null, pval);
          chg.addValue(prop.getName(), pval);
        } else if (prop instanceof DtEnd) {
          /* ------------------- DtEnd -------------------- */
        } else if (prop instanceof DtStamp) {
          /* ------------------- DtStamp -------------------- */

          chg.changed(prop.getName(), fb.getDtstamp(), pval);
          fb.setDtstamp(pval);
        } else if (prop instanceof DtStart) {
          /* ------------------- DtStart -------------------- */
        } else if (prop instanceof FreeBusy) {
          /* ------------------- freebusy -------------------- */

          FreeBusy fbusy = (FreeBusy)prop;
          PeriodList perpl = fbusy.getPeriods();
          Parameter par = getParameter(fbusy, "FBTYPE");
          int fbtype;

          if (par == null) {
            fbtype = BwFreeBusyComponent.typeBusy;
          } else if (par.equals(FbType.BUSY)) {
            fbtype = BwFreeBusyComponent.typeBusy;
          } else if (par.equals(FbType.BUSY_TENTATIVE)) {
            fbtype = BwFreeBusyComponent.typeBusyTentative;
          } else if (par.equals(FbType.BUSY_UNAVAILABLE)) {
            fbtype = BwFreeBusyComponent.typeBusyUnavailable;
          } else if (par.equals(FbType.FREE)) {
            fbtype = BwFreeBusyComponent.typeFree;
          } else {
            if (debug) {
              debugMsg("Unsupported parameter " + par.getName());
            }

            throw new IcalMalformedException("parameter " + par.getName());
          }

          BwFreeBusyComponent fbc = new BwFreeBusyComponent();

          fbc.setType(fbtype);

          Iterator perit = perpl.iterator();
          while (perit.hasNext()) {
            Period per = (Period)perit.next();

            fbc.addPeriod(per);
          }

          fb.addFreeBusyPeriod(fbc);
          chg.addValue(prop.getName(), fbc);
        } else if (prop instanceof Organizer) {
          /* ------------------- Organizer -------------------- */

          BwOrganizer org = getOrganizer(cb, (Organizer)prop);
          fb.setOrganizer(org);
          chg.addValue(prop.getName(), org);
        } else if (prop instanceof Uid) {
          /* ------------------- Uid -------------------- */

          chg.changed(prop.getName(), fb.getUid(), pval);
          fb.setUid(pval);
        } else {
          if (debug) {
            debugMsg("Unsupported property with class " + prop.getClass() +
                     " and value " + pval);
          }
        }
      }

      return ei;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
