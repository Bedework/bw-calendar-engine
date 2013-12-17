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
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.icalendar.Icalendar.TimeZoneInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.CategoryList;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ResourceList;
import net.fortuna.ical4j.model.component.Available;
import net.fortuna.ical4j.model.component.VAvailability;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.component.VJournal;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.parameter.AltRep;
import net.fortuna.ical4j.model.parameter.FbType;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.parameter.XParameter;
import net.fortuna.ical4j.model.property.AcceptResponse;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.PollItemId;
import net.fortuna.ical4j.model.property.PollMode;
import net.fortuna.ical4j.model.property.PollProperties;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RelatedTo;
import net.fortuna.ical4j.model.property.RequestStatus;
import net.fortuna.ical4j.model.property.Resources;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Voter;
import net.fortuna.ical4j.model.property.XProperty;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.ws.Holder;

/** Class to provide utility methods for translating to BwEvent from ical4j classes
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class BwEventUtil extends IcalUtil {
  /** We are going to try to construct a BwEvent object from a VEvent. This
   * may represent a new event or an update to a pre-existing event. In any
   * case, the VEvent probably has insufficient information to completely
   * reconstitute the event object so we'll get the uid first and retrieve
   * the event if it exists.
   *
   * <p>To put it another way we're doing a diff then update.
   *
   * <p>If it doesn't exist, we'll first fill in the appropriate fields,
   * (non-public, creator, created etc) then for both cases update the
   * remaining fields from the VEvent.
   *
   * <p>Recurring events present some challenges. If there is no recurrence
   * id the vevent represents the master entity which defines the recurrence
   * rules. If a recurrence id is present then the vevent represents a
   * recurrence instance override and we should not attempt to retrieve the
   * actual object but the referenced instance.
   *
   * <p>Also, note that we sorted the components first so we get the master
   * before any instances.
   *
   * <p>If DTSTART, RRULE, EXRULE have changed (also RDATE, EXDATE?) then any
   * existing overrides are unusable. We should delete all overrides and replace
   * with new ones.
   *
   * <p>For an update we have to keep track of which fields were present in
   * the vevent and set all absent fields to null in the BwEvent.
   *
   * @param cb          IcalCallback object
   * @param cal         Needed so we can retrieve the event.
   * @param ical        Icalendar we are converting. We check its events for
   *                    overrides.
   * @param val         VEvent object
   * @param diff        True if we should assume we are updating existing events.
   * @param mergeAttendees True if we should only update our own attendee.
   * @return EventInfo  object representing new entry or updated entry
   * @throws CalFacadeException
   */
  public static EventInfo toEvent(final IcalCallback cb,
                                  final BwCalendar cal,
                                  final Icalendar ical,
                                  final Component val,
                                  final boolean diff,
                                  final boolean mergeAttendees) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    String currentPrincipal = null;
    BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    boolean debug = getLog().isDebugEnabled();
    Holder<Boolean> hasXparams = new Holder<Boolean>(Boolean.FALSE);

    int methodType = ical.getMethodType();

    String attUri = null;

    if (mergeAttendees) {
      // We'll need this later.
      attUri = cb.getCaladdr(cb.getPrincipal().getPrincipalRef());
    }

    try {
      PropertyList pl = val.getProperties();

      if (pl == null) {
        // Empty component
        return null;
      }

      int entityType;

      if (val instanceof VEvent) {
        entityType = IcalDefs.entityTypeEvent;
      } else if (val instanceof VToDo) {
        entityType = IcalDefs.entityTypeTodo;
      } else if (val instanceof VJournal) {
        entityType = IcalDefs.entityTypeJournal;
      } else if (val instanceof VFreeBusy) {
        entityType = IcalDefs.entityTypeFreeAndBusy;
      } else if (val instanceof VAvailability) {
        entityType = IcalDefs.entityTypeVavailability;
      } else if (val instanceof Available) {
        entityType = IcalDefs.entityTypeAvailable;
      } else if (val instanceof VPoll) {
        entityType = IcalDefs.entityTypeVpoll;
      } else {
        throw new CalFacadeException("org.bedework.invalid.component.type",
                                     val.getName());
      }

      Property prop;

      // Get the guid from the component

      String guid = null;

      prop = pl.getProperty(Property.UID);
      if (prop != null) {
        testXparams(prop, hasXparams);
        guid = prop.getValue();
      }

      if (guid == null) {
        /* XXX A guid is required - but are there devices out there without a
         *       guid - and if so how do we handle it?
         */
        throw new CalFacadeException(CalFacadeException.noGuid);
      }

      /* See if we have a recurrence id */

      BwDateTime ridObj = null;
      String rid = null;

      prop = pl.getProperty(Property.RECURRENCE_ID);
      if (prop != null) {
        testXparams(prop, hasXparams);
        ridObj = BwDateTime.makeBwDateTime((DateProperty)prop);

        if (ridObj.getRange() != null) {
          /* XXX What do I do with it? */
          warn("TRANS-TO_EVENT: Got a recurrence id range");
        }

        rid = ridObj.getDate();
      }

      EventInfo masterEI = null;
      EventInfo evinfo = null;
      BwEvent ev = null;

      /* If we have a recurrence id see if we already have the master (we should
       * get a master + all it's overrides).
       *
       * If so find the override and use the annnotation or if no override,
       * make one.
       *
       * If no override retrieve the event, add it to our table and then locate the
       * annotation.
       *
       * If there is no annotation, create one.
       *
       * It's possible we have been sent 'detached' instances of a recurring
       * event. This may happen if we are invited to one or more instances of a
       * meeting. In this case we try to retrieve the master and if it doesn't
       * exist we manufacture one. We consider such an instance an update to
       * that instance only and leave the others alone.
       */

      /* We need this in a couple of places */
      DtStart dtStart = (DtStart)pl.getProperty(Property.DTSTART);

      /*
      if (rid != null) {
        // See if we have a new master event. If so create a proxy to that event.
        masterEI = findMaster(guid, ical.getComponents());

        if (masterEI == null) {
          masterEI = makeNewEvent(cb, chg, entityType, guid, cal);
          BwEvent e = masterEI.getEvent();

          // XXX This seems bogus
          DtStart mdtStart;

          String bogusDate = "19980118T230000";

          if (dtStart.isUtc()) {
            mdtStart = new DtStart(bogusDate + "Z");
          } else if (dtStart.getTimeZone() == null) {
            mdtStart = new DtStart(bogusDate);
          } else {
            mdtStart = new DtStart(bogusDate + "Z", dtStart.getTimeZone());
          }

          setDates(e, mdtStart, null, null, chg);
          e.setRecurring(true);
          e.addRdate(ridObj);
          e.setSuppressed(true);

          ical.addComponent(masterEI);
        }

        if (masterEI != null) {
          evinfo = masterEI.findOverride(rid);
        }
      }
      */

      /* If this is a recurrence instance see if we can find the master
       */
      if (rid != null) {
        // See if we have a new master event. If so create a proxy to this event.
        masterEI = findMaster(guid, ical.getComponents());

        if (masterEI != null) {
          evinfo = masterEI.findOverride(rid);
        }
      }

      if (diff && (evinfo == null) &&
          (cal != null) &&
          (cal.getCalType() != BwCalendar.calTypeInbox) &&
          (cal.getCalType() != BwCalendar.calTypeOutbox)) {
        if (debug) {
          debugMsg("TRANS-TO_EVENT: try to fetch event with guid=" + guid);
        }

        RecurringRetrievalMode rrm =
          new RecurringRetrievalMode(Rmode.overrides);
        Collection eis = cb.getEvent(cal, guid, null, rrm);
        if (Util.isEmpty(eis)) {
          // do nothing
        } else if (eis.size() > 1) {
          // DORECUR - wrong again
          throw new CalFacadeException("More than one event returned for guid.");
        } else {
          evinfo = (EventInfo)eis.iterator().next();
        }

        if (debug) {
          if (evinfo != null) {
            debugMsg("TRANS-TO_EVENT: fetched event with guid");
          } else {
            debugMsg("TRANS-TO_EVENT: did not find event with guid");
          }
        }

        if (evinfo != null) {
          if (rid != null) {
            // We just retrieved it's master
            masterEI = evinfo;
            masterEI.setInstanceOnly(true);
            evinfo = masterEI.findOverride(rid);
            ical.addComponent(masterEI);
          } else if (methodType == ScheduleMethods.methodTypeCancel) {
            // This should never have an rid for cancel of entire event.
            evinfo.setInstanceOnly(evinfo.getEvent().getSuppressed());
          } else {
            // Presumably sent an update for the entire event. No longer suppressed master
            evinfo.getEvent().setSuppressed(false);
          }
        } else if (rid != null) {
          /* Manufacture a master for the instance */
          masterEI = makeNewEvent(cb, entityType, guid, cal);
          BwEvent e = masterEI.getEvent();

          // XXX This seems bogus
          DtStart mdtStart;

          String bogusDate = "19980118";
          String bogusTime = "T230000";

          Parameter par = dtStart.getParameter("VALUE");
          boolean isDateType = (par != null) && (par.equals(Value.DATE));

          if (isDateType) {
            mdtStart = new DtStart(new Date(bogusDate));
          } else if (dtStart.isUtc()) {
            mdtStart = new DtStart(bogusDate + bogusTime + "Z");
          } else if (dtStart.getTimeZone() == null) {
            mdtStart = new DtStart(bogusDate + bogusTime);
          } else {
            mdtStart = new DtStart(bogusDate + bogusTime + "Z",
                                   dtStart.getTimeZone());
          }

          setDates(cb.getPrincipal().getPrincipalRef(),
                   masterEI, mdtStart, null, null);
          e.setRecurring(true);
//          e.addRdate(ridObj);
          e.setSuppressed(true);

          ical.addComponent(masterEI);

          evinfo = masterEI.findOverride(rid);
          masterEI.setInstanceOnly(rid != null);
        }
      }

      if (evinfo == null) {
        evinfo = makeNewEvent(cb, entityType, guid, cal);
      } else if (evinfo.getEvent().getEntityType() != entityType) {
        throw new CalFacadeException("org.bedework.mismatched.entity.type",
                                     val.toString());
      }

      ChangeTable chg = evinfo.getChangeset(cb.getPrincipal().getPrincipalRef());

      if (rid != null) {
        String evrid = evinfo.getEvent().getRecurrenceId();

        if ((evrid == null) || (!evrid.equals(rid))) {
          warn("Mismatched rid ev=" + evrid + " expected " + rid);
          chg.changed(PropertyInfoIndex.RECURRENCE_ID, evrid, rid); // XXX spurious???
        }

        if (masterEI.getEvent().getSuppressed()) {
          masterEI.getEvent().addRdate(ridObj);
        }
      }

      ev = evinfo.getEvent();
      ev.setScheduleMethod(methodType);

      DtEnd dtEnd = null;

      if (entityType == IcalDefs.entityTypeTodo) {
        Due due = (Due)pl.getProperty(Property.DUE);
        if (due != null ) {
          dtEnd = new DtEnd(due.getParameters(), due.getValue());
        }
      } else {
        dtEnd = (DtEnd)pl.getProperty(Property.DTEND);
      }

      Duration duration = (Duration)pl.getProperty(Property.DURATION);

      setDates(cb.getPrincipal().getPrincipalRef(),
               evinfo, dtStart, dtEnd, duration);

      Iterator it = pl.iterator();

      while (it.hasNext()) {
        prop = (Property)it.next();
        testXparams(prop, hasXparams);

        //debugMsg("ical prop " + prop.getClass().getName());
        String pval = prop.getValue();
        if ((pval != null) && (pval.length() == 0)) {
          pval = null;
        }

        PropertyInfoIndex pi;

        if (prop instanceof XProperty) {
          pi = PropertyInfoIndex.XPROP;
        } else {
          try {
            pi = PropertyInfoIndex.valueOf(prop.getName());
          } catch (Throwable t) {
            pi = null;
          }
        }

        if (pi == null) {
          debugMsg("Unknown property with name " + prop.getName() +
                           " class " + prop.getClass() +
                           " and value " + pval);
          continue;
        }

        chg.present(pi);

        switch (pi) {
          case ACCEPT_RESPONSE:
            /* ------------------- Accept Response -------------------- */

            String sval = ((AcceptResponse)prop).getValue();
            if (chg.changed(pi, ev.getPollAcceptResponse(), sval)) {
              ev.setPollAcceptResponse(sval);
            }
            break;

          case ATTACH:
            /* ------------------- Attachment -------------------- */

            chg.addValue(pi, getAttachment((Attach)prop));
            break;

          case ATTENDEE:
            /* ------------------- Attendee -------------------- */

            if (methodType == ScheduleMethods.methodTypePublish) {
              if (cb.getStrictness() == IcalCallback.conformanceStrict) {
                throw new CalFacadeException(CalFacadeException.attendeesInPublish);
              }

              if (cb.getStrictness() == IcalCallback.conformanceWarn) {
                //warn("Had attendees for PUBLISH");
              }
            }

            Attendee attPr = (Attendee)prop;

            if (evinfo.getNewEvent() || !mergeAttendees) {
              chg.addValue(pi, getAttendee(cb, attPr));
            } else {
              String pUri = cb.getCaladdr(attPr.getValue());

              if (pUri.equals(attUri)) {
                /* Only update for our own attendee
               * We're doing a PUT and this must be the attendee updating their
               * partstat. We don't allow them to change other attendees
               * whatever the PUT content says.
               */
                chg.addValue(pi, getAttendee(cb, attPr));
              } else {
                // Use the value we currently have
                for (BwAttendee att: ev.getAttendees()) {
                  if (pUri.equals(att.getAttendeeUri())) {
                    chg.addValue(pi, att.clone());
                    break;
                  }
                }
              }
            }

            break;

          case BUSYTYPE:
            int ibt = BwEvent.fromBusyTypeString(pval);
            if (chg.changed(pi,
                            ev.getBusyType(),
                            ibt)) {
              ev.setBusyType(ibt);
            }

            break;

          case CATEGORIES:
            /* ------------------- Categories -------------------- */

            Categories cats = (Categories)prop;
            CategoryList cl = cats.getCategories();
            String lang = getLang(cats);

            if (cl != null) {
              /* Got some categories */

              Iterator cit = cl.iterator();

              while (cit.hasNext()) {
                String wd = (String)cit.next();
                if (wd == null) {
                  continue;
                }

                BwString key = new BwString(lang, wd);

                BwCategory cat = cb.findCategory(key);

                if (cat == null) {
                  cat = BwCategory.makeCategory();
                  cat.setWord(key);

                  cb.addCategory(cat);
                }

                chg.addValue(pi, cat);
              }
            }

            break;

          case CLASS:
            /* ------------------- Class -------------------- */

            if (chg.changed(pi, ev.getClassification(), pval)) {
              ev.setClassification(pval);
            }

            break;

          case COMMENT:
            /* ------------------- Comment -------------------- */

            chg.addValue(pi,
                         new BwString(null, pval));

            break;

          case COMPLETED:
            /* ------------------- Completed -------------------- */

            if (chg.changed(pi, ev.getCompleted(), pval)) {
              ev.setCompleted(pval);
            }

            break;

          case CONTACT:
            /* ------------------- Contact -------------------- */

            String altrep = getAltRepPar(prop);
            lang = getLang(prop);
            String uid = getUidPar(prop);
            BwString nm = new BwString(lang, pval);

            BwContact contact = null;

            if (uid != null) {
              contact = cb.getContact(uid);
            }

            if (contact == null) {
              contact = cb.findContact(nm);
            }

            if (contact == null) {
              contact = BwContact.makeContact();
              contact.setCn(nm);
              contact.setLink(altrep);
              cb.addContact(contact);
            } else {
              contact.setCn(nm);
              contact.setLink(altrep);
            }

            chg.addValue(pi, contact);

            break;

          case CREATED:
            /* ------------------- Created -------------------- */

            if (chg.changed(pi, ev.getCreated(), pval)) {
              ev.setCreated(pval);
            }

            break;

          case DESCRIPTION:
            /* ------------------- Description -------------------- */

            if (chg.changed(pi, ev.getDescription(), pval)) {
              ev.setDescription(pval);
            }

            break;

          case DTEND:
            /* ------------------- DtEnd -------------------- */

            break;

          case DTSTAMP:
            /* ------------------- DtStamp -------------------- */

            ev.setDtstamp(pval);

            break;

          case DTSTART:
            /* ------------------- DtStart -------------------- */

            break;

          case DURATION:
            /* ------------------- Duration -------------------- */

            break;

          case EXDATE:
            /* ------------------- ExDate -------------------- */

            chg.addValues(pi,
                          makeDateTimes((DateListProperty)prop));

            break;

          case EXRULE:
            /* ------------------- ExRule -------------------- */

            chg.addValue(pi, pval);

            break;

          case FREEBUSY:
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

            ev.addFreeBusyPeriod(fbc);

            break;

          case GEO:
            /* ------------------- Geo -------------------- */

            Geo g = (Geo)prop;
            BwGeo geo = new BwGeo(g.getLatitude(), g.getLongitude());
            if (chg.changed(pi, ev.getGeo(), geo)) {
              ev.setGeo(geo);
            }

            break;

          case LAST_MODIFIED:
            /* ------------------- LastModified -------------------- */

            if (chg.changed(pi, ev.getLastmod(), pval)) {
              ev.setLastmod(pval);
            }

            break;

          case LOCATION:
            /* ------------------- Location -------------------- */
            BwLocation loc = null;
            //String uid = getUidPar(prop);

            /* At the moment Mozilla lightning is broken and this leads to all
           * sorts of problems.
            if (uid != null) {
              loc = cb.getLocation(uid);
            }
           */

            lang = getLang(prop);
            BwString addr = null;

            if (pval != null) {
              if (loc == null) {
                addr = new BwString(lang, pval);
                loc = cb.findLocation(addr);
              }

              if (loc == null) {
                loc = BwLocation.makeLocation();
                loc.setAddress(addr);
                cb.addLocation(loc);
              }
            }

            BwLocation evloc = ev.getLocation();

            if (chg.changed(pi, evloc, loc)) {
              // CHGTBL - this only shows that it's a different location object
              ev.setLocation(loc);
            } else if ((loc != null) && (evloc != null)) {
              // See if the value is changed
              String evval = evloc.getAddress().getValue();
              String inval = loc.getAddress().getValue();
              if (!evval.equals(inval)) {
                chg.changed(pi, evval, inval);
                evloc.getAddress().setValue(inval);
              }
            }

            break;

          case ORGANIZER:
            /* ------------------- Organizer -------------------- */

            BwOrganizer org = getOrganizer(cb, (Organizer)prop);
            BwOrganizer evorg = ev.getOrganizer();

            if (chg.changed(pi, evorg, org)) {
              if (evorg == null) {
                ev.setOrganizer(org);
              } else {
                evorg.update(org);
              }
            }

            break;

          case PERCENT_COMPLETE:
            /* ------------------- PercentComplete -------------------- */

            Integer ival = new Integer(((PercentComplete)prop).getPercentage());
            if (chg.changed(pi, ev.getPercentComplete(), ival)) {
              ev.setPercentComplete(ival);
            }

            break;

          case POLL_ITEM_ID:
            /* ------------------- Poll item id -------------------- */

            ival = new Integer(((PollItemId)prop).getPollitemid());
            if (chg.changed(pi, ev.getPollItemId(), ival)) {
              ev.setPollItemId(ival);
            }

            break;

          case POLL_MODE:
            /* ------------------- Poll mode -------------------- */

            sval = ((PollMode)prop).getValue();
            if (chg.changed(pi, ev.getPollMode(), sval)) {
              ev.setPollMode(sval);
            }

            break;

          case POLL_PROPERTIES:
            /* ------------------- Poll mode -------------------- */

            sval = ((PollProperties)prop).getValue();
            if (chg.changed(pi, ev.getPollProperties(), sval)) {
              ev.setPollProperties(sval);
            }

            break;
          case PRIORITY:
            /* ------------------- Priority -------------------- */

            ival = new Integer(((Priority)prop).getLevel());
            if (chg.changed(pi, ev.getPriority(), ival)) {
              ev.setPriority(ival);
            }

            break;

          case RDATE:
            /* ------------------- RDate -------------------- */

            chg.addValues(pi,
                          makeDateTimes((DateListProperty)prop));

            break;

          case RECURRENCE_ID:
            /* ------------------- RecurrenceID -------------------- */
            // Done above

            break;

          case RELATED_TO:
            /* ------------------- RelatedTo -------------------- */
            RelatedTo irelto = (RelatedTo)prop;
            BwRelatedTo relto = new BwRelatedTo();

            String parval = IcalUtil.getParameterVal(irelto, "RELTYPE");
            if (parval != null) {
              relto.setRelType(parval);
            }

            relto.setValue(irelto.getValue());

            if (chg.changed(pi, ev.getRelatedTo(), relto)) {
              ev.setRelatedTo(relto);
            }

            break;

          case REQUEST_STATUS:
            /* ------------------- RequestStatus -------------------- */
            BwRequestStatus rs = BwRequestStatus.fromRequestStatus((RequestStatus)prop);

            chg.addValue(pi, rs);

            break;

          case RESOURCES:
            /* ------------------- Resources -------------------- */

            ResourceList rl = ((Resources)prop).getResources();

            if (rl != null) {
              /* Got some resources */
              lang = getLang(prop);

              Iterator rit = rl.iterator();

              while (rit.hasNext()) {
                BwString rsrc = new BwString(lang, (String)rit.next());
                chg.addValue(pi, rsrc);
              }
            }

            break;

          case RRULE:
            /* ------------------- RRule -------------------- */

            chg.addValue(pi, pval);

            break;

          case SEQUENCE:
            /* ------------------- Sequence -------------------- */

            int seq = ((Sequence)prop).getSequenceNo();
            if (seq != ev.getSequence()) {
              chg.changed(pi, ev.getSequence(), seq);
              ev.setSequence(seq);
            }

            break;

          case STATUS:
            /* ------------------- Status -------------------- */

            if (chg.changed(pi, ev.getStatus(), pval)) {
              ev.setStatus(pval);
            }

            break;

          case SUMMARY:
            /* ------------------- Summary -------------------- */

            if (chg.changed(pi, ev.getSummary(), pval)) {
              ev.setSummary(pval);
            }

            break;

          case TRANSP:
            /* ------------------- Transp -------------------- */

            if (chg.changed(pi,
                            ev.getPeruserTransparency(cb.getPrincipal().getPrincipalRef()), pval)) {
              BwXproperty pu = ev.setPeruserTransparency(cb.getPrincipal().getPrincipalRef(),
                                                         pval);
              if (pu != null) {
                chg.addValue(PropertyInfoIndex.XPROP, pu);
              }
            }

            break;

          case UID:
            /* ------------------- Uid -------------------- */

            /* We did this above */

            break;

          case URL:
            /* ------------------- Url -------------------- */

            if (chg.changed(pi, ev.getLink(), pval)) {
              ev.setLink(pval);
            }

            break;

          case VOTER:
            /* ------------------- Voter -------------------- */

            if (methodType == ScheduleMethods.methodTypePublish) {
              if (cb.getStrictness() == IcalCallback.conformanceStrict) {
                throw new CalFacadeException(CalFacadeException.attendeesInPublish);
              }

              if (cb.getStrictness() == IcalCallback.conformanceWarn) {
                //warn("Had attendees for PUBLISH");
              }
            }

            Voter vPr = (Voter)prop;

            if (evinfo.getNewEvent() || !mergeAttendees) {
              chg.addValue(pi, getVoter(cb, vPr));
            } else {
              String pUri = cb.getCaladdr(vPr.getValue());

              if (pUri.equals(attUri)) {
                /* Only update for our own attendee
                 * We're doing a PUT and this must be the attendee updating their
                 * response. We don't allow them to change other voters
               * whatever the PUT content says.
               */
                chg.addValue(pi, getVoter(cb, vPr));
              } else {
                // Use the value we currently have
                for (BwAttendee att: ev.getAttendees()) {
                  if (pUri.equals(att.getAttendeeUri())) {
                    chg.addValue(pi, att.clone());
                    break;
                  }
                }
              }
            }

            break;

          case XPROP:
            /* ------------------------- x-property --------------------------- */

            String name = prop.getName();

            if (name.equalsIgnoreCase(BwXproperty.bedeworkCost)) {
              if (chg.changed(PropertyInfoIndex.COST, ev.getCost(), pval)) {
                ev.setCost(pval);
              }
            } else {
              XProperty xp = (XProperty)prop;
              chg.addValue(PropertyInfoIndex.XPROP,
                           new BwXproperty(name,
                                           xp.getParameters().toString(),
                                           pval));
            }
          default:
            if (debug) {
              debugMsg("Unsupported property with class " + prop.getClass() +
                               " and value " + pval);
            }

        }
      }

      if (val instanceof VAvailability) {
        processAvailable(cb, cal, ical, (VAvailability)val, evinfo);
      } else if (!(val instanceof Available)) {
        VAlarmUtil.processComponentAlarms(cb, val, ev, currentPrincipal, chg);
        if (val instanceof VPoll) {
          processCandidates(cb, cal, ical, (VPoll)val, evinfo);
        }
      }

      processTimezones(ev, ical, chg);

      /* Remove any recipients and originator
       */
      if (ev.getRecipients() != null) {
        ev.getRecipients().clear();
      }

      ev.setOriginator(null);

      if (hasXparams.value) {
        /* Save a text copy of the entire event as an x-property */

        Component valCopy = val.copy();

        /* Remove potentially large values */
        prop = valCopy.getProperty(Property.DESCRIPTION);
        if (prop != null) {
          prop.setValue(null);
        }

        prop = valCopy.getProperty(Property.ATTACH);
        // Don't store the entire attachment - we just need the parameters.
        if (prop != null) {
          Value v = (Value)prop.getParameter(Parameter.VALUE);

          if (v != null) {
            prop.setValue(String.valueOf(prop.getValue().hashCode()));
          }
        }

        chg.addValue(PropertyInfoIndex.XPROP,
                     new BwXproperty(BwXproperty.bedeworkIcal,
                                     null,
                                     valCopy.toString()));
      }

      chg.processChanges(ev, true);

      ev.setRecurring(new Boolean(ev.isRecurringEntity()));

      if (debug) {
        debugMsg(chg.toString());
        debugMsg(ev.toString());
      }

      if (masterEI != null) {
        // Just return null as this event is on its override list
        return null;
      }

      return evinfo;
    } catch (CalFacadeException cfe) {
      if (debug) {
        cfe.printStackTrace();
      }
      throw cfe;
    } catch (Throwable t) {
      if (debug) {
        t.printStackTrace();
      }
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private static void testXparams(final Property p,
                           final Holder<Boolean> hasXparams) {
    if (hasXparams.value) {
      // No need to check
      return;
    }

    ParameterList params = p.getParameters();

    Iterator parit = params.iterator();
    while (parit.hasNext()) {
      Parameter param = (Parameter)parit.next();

      if (!(param instanceof XParameter)) {
        continue;
      }

      XParameter xpar = (XParameter)param;

      if (xpar.getName().toUpperCase().equals(BwXproperty.xparUid)) {
        continue;
      }

      hasXparams.value = true;
    }
  }
  private static void processTimezones(final BwEvent ev,
                                       final Icalendar ical,
                                       final ChangeTable chg) throws CalFacadeException {
    for (TimeZoneInfo tzi: ical.getTimeZones()) {
      if (tzi.tzSpec == null) {
        // System
        continue;
      }

      if (EventTimeZonesRegistry.findTzValue(ev, tzi.tzid) != null) {
        // Seen already
        continue;
      }

      chg.addValue(PropertyInfoIndex.XPROP,
                   new BwXproperty(BwXproperty.bedeworkXTimezone,
                                   null,
                                   BwXproperty.escapeSemi(tzi.tzid) + ";" +
                                   tzi.tzSpec));
    }
  }

  private static void processAvailable(final IcalCallback cb,
                                       final BwCalendar cal,
                                       final Icalendar ical,
                                       final VAvailability val,
                                       final EventInfo vavail) throws CalFacadeException {

    try {
      ComponentList avls = val.getAvailable();

      if ((avls == null) || avls.isEmpty()) {
        return;
      }

      Iterator it = avls.iterator();

      while (it.hasNext()) {
        Object o = it.next();

        if (!(o instanceof Available)) {
          throw new IcalMalformedException("Invalid available list");
        }

        EventInfo availi = toEvent(cb, cal, ical, (Component)o, true,
                                   false);
        availi.getEvent().setOwnerHref(vavail.getEvent().getOwnerHref());

        vavail.addContainedItem(availi);
        vavail.getEvent().addAvailableUid(availi.getEvent().getUid());
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void processCandidates(final IcalCallback cb,
                                        final BwCalendar cal,
                                        final Icalendar ical,
                                        final VPoll val,
                                        final EventInfo vpoll) throws CalFacadeException {

    try {
      ComponentList cands = val.getCandidates();

      if ((cands == null) || cands.isEmpty()) {
        return;
      }

      Iterator it = cands.iterator();
      Set<Integer> pids = new TreeSet<Integer>();
      BwEvent event = vpoll.getEvent();

      if (!Util.isEmpty(event.getPollItems())) {
        event.getPollItems().clear();
      }

      while (it.hasNext()) {
        Component comp = (Component)it.next();

        event.addPollItem(comp.toString());

        Property p = comp.getProperty(Property.POLL_ITEM_ID);

        if (p == null) {
          throw new CalFacadeException("XXX - no poll item id");
        }

        int pid = ((PollItemId)p).getPollitemid();

        if (pids.contains(pid)) {
          throw new CalFacadeException("XXX - duplicate poll item id " + pid);
        }

        pids.add(pid);

//        EventInfo cand = toEvent(cb, cal, ical, (Component)o, true,
//                                 false);
//        cand.getEvent().setOwnerHref(vpoll.getEvent().getOwnerHref());

//        vpoll.addContainedItem(cand);
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static EventInfo makeNewEvent(final IcalCallback cb,
                                        final int entityType,
                                        final String uid,
                                        final BwCalendar col) throws CalFacadeException {
    BwEvent ev = new BwEventObj();
    EventInfo evinfo = new EventInfo(ev);

    //ev.setDtstamps();
    ev.setEntityType(entityType);
    ev.setCreatorHref(cb.getPrincipal().getPrincipalRef());
    ev.setOwnerHref(cb.getOwner().getPrincipalRef());
    ev.setUid(uid);

    if (col != null) {
      ev.setColPath(col.getPath());
    }

    ChangeTable chg = evinfo.getChangeset(cb.getPrincipal().getPrincipalRef());
    chg.changed(PropertyInfoIndex.UID, null, uid); // get that out of the way

    evinfo.setNewEvent(true);

    return evinfo;
  }

  /* See if the master event is already in the collection of events
   * we've processed for this calendar. Only called if we have an event
   * with a recurrence id
   */
  private static EventInfo findMaster(final String guid, final Collection evs) {
    if (evs == null) {
      return null;
    }
    Iterator it = evs.iterator();

    while (it.hasNext()) {
      EventInfo ei = (EventInfo)it.next();
      BwEvent ev = ei.getEvent();

      if ((ev.getRecurrenceId() == null) &&
          guid.equals(ev.getUid()) /* &&
          ei.getNewEvent()  */) {
        return ei;
      }
    }

    return null;
  }

  private static String getUidPar(final Property p) {
    ParameterList pars = p.getParameters();

    Parameter par = pars.getParameter(BwXproperty.xparUid);

    if (par == null) {
      return null;
    }

    return par.getValue();
  }

  private static String getAltRepPar(final Property p) {
    AltRep par = IcalUtil.getAltRep(p);

    if (par == null) {
      return null;
    }

    return par.getValue();
  }
}

