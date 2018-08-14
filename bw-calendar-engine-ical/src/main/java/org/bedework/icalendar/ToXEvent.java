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
import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.XcalUtil;

import ietf.params.xml.ns.icalendar_2.ArrayOfComponents;
import ietf.params.xml.ns.icalendar_2.ArrayOfParameters;
import ietf.params.xml.ns.icalendar_2.ArrayOfProperties;
import ietf.params.xml.ns.icalendar_2.AttachPropType;
import ietf.params.xml.ns.icalendar_2.AttendeePropType;
import ietf.params.xml.ns.icalendar_2.BaseComponentType;
import ietf.params.xml.ns.icalendar_2.BaseParameterType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.CategoriesPropType;
import ietf.params.xml.ns.icalendar_2.ClassPropType;
import ietf.params.xml.ns.icalendar_2.CnParamType;
import ietf.params.xml.ns.icalendar_2.CommentPropType;
import ietf.params.xml.ns.icalendar_2.CompletedPropType;
import ietf.params.xml.ns.icalendar_2.ContactPropType;
import ietf.params.xml.ns.icalendar_2.CreatedPropType;
import ietf.params.xml.ns.icalendar_2.CutypeParamType;
import ietf.params.xml.ns.icalendar_2.DelegatedFromParamType;
import ietf.params.xml.ns.icalendar_2.DelegatedToParamType;
import ietf.params.xml.ns.icalendar_2.DescriptionPropType;
import ietf.params.xml.ns.icalendar_2.DirParamType;
import ietf.params.xml.ns.icalendar_2.DtendPropType;
import ietf.params.xml.ns.icalendar_2.DtstampPropType;
import ietf.params.xml.ns.icalendar_2.DtstartPropType;
import ietf.params.xml.ns.icalendar_2.DuePropType;
import ietf.params.xml.ns.icalendar_2.DurationPropType;
import ietf.params.xml.ns.icalendar_2.FbtypeParamType;
import ietf.params.xml.ns.icalendar_2.FreebusyPropType;
import ietf.params.xml.ns.icalendar_2.FreqRecurType;
import ietf.params.xml.ns.icalendar_2.GeoPropType;
import ietf.params.xml.ns.icalendar_2.LanguageParamType;
import ietf.params.xml.ns.icalendar_2.LastModifiedPropType;
import ietf.params.xml.ns.icalendar_2.LocationPropType;
import ietf.params.xml.ns.icalendar_2.MemberParamType;
import ietf.params.xml.ns.icalendar_2.OrganizerPropType;
import ietf.params.xml.ns.icalendar_2.PartstatParamType;
import ietf.params.xml.ns.icalendar_2.PercentCompletePropType;
import ietf.params.xml.ns.icalendar_2.PeriodType;
import ietf.params.xml.ns.icalendar_2.PriorityPropType;
import ietf.params.xml.ns.icalendar_2.RecurType;
import ietf.params.xml.ns.icalendar_2.RecurrenceIdPropType;
import ietf.params.xml.ns.icalendar_2.RelatedToPropType;
import ietf.params.xml.ns.icalendar_2.ReltypeParamType;
import ietf.params.xml.ns.icalendar_2.ResourcesPropType;
import ietf.params.xml.ns.icalendar_2.RoleParamType;
import ietf.params.xml.ns.icalendar_2.RrulePropType;
import ietf.params.xml.ns.icalendar_2.RsvpParamType;
import ietf.params.xml.ns.icalendar_2.ScheduleStatusParamType;
import ietf.params.xml.ns.icalendar_2.SentByParamType;
import ietf.params.xml.ns.icalendar_2.SequencePropType;
import ietf.params.xml.ns.icalendar_2.StatusPropType;
import ietf.params.xml.ns.icalendar_2.SummaryPropType;
import ietf.params.xml.ns.icalendar_2.TranspPropType;
import ietf.params.xml.ns.icalendar_2.UidPropType;
import ietf.params.xml.ns.icalendar_2.UntilRecurType;
import ietf.params.xml.ns.icalendar_2.UrlPropType;
import ietf.params.xml.ns.icalendar_2.ValarmType;
import ietf.params.xml.ns.icalendar_2.VeventType;
import ietf.params.xml.ns.icalendar_2.VfreebusyType;
import ietf.params.xml.ns.icalendar_2.VjournalType;
import ietf.params.xml.ns.icalendar_2.VtodoType;
import ietf.params.xml.ns.icalendar_2.XBedeworkCostPropType;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.property.RRule;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBElement;

/** Class to provide utility methods for translating to XML icalendar classes
 *
 * @author Mike Douglass   douglm    rpi.edu
 */
public class ToXEvent extends Xutil {
  /** Make a BaseComponentType component from a BwEvent object. This may produce a
   * VEvent, VTodo or VJournal.
   *
   * @param val the event
   * @param isOverride - true if event object is an override
   * @param pattern - if non-null limit returned components and values to those
   *                  supplied in the pattern.
   * @return converted to a JAXBElement
   * @throws CalFacadeException on fatal error
   */
  public static JAXBElement<? extends BaseComponentType>
                    toComponent(final BwEvent val,
                                final boolean isOverride,
                                final boolean wrapXprops,
                                final BaseComponentType pattern) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    boolean isInstance = false;

    try {
      JAXBElement<? extends BaseComponentType> el;

      boolean freeBusy = false;
      boolean todo = false;

      int entityType = val.getEntityType();
      if (entityType == IcalDefs.entityTypeEvent) {
        el = of.createVevent(new VeventType());
      } else if (entityType == IcalDefs.entityTypeTodo) {
        el = of.createVtodo(new VtodoType());
        todo = true;
      } else if (entityType == IcalDefs.entityTypeJournal) {
        el = of.createVjournal(new VjournalType());
      } else if (entityType == IcalDefs.entityTypeFreeAndBusy) {
        el = of.createVfreebusy(new VfreebusyType());
        freeBusy = true;
      } else {
        throw new CalFacadeException("org.bedework.invalid.entity.type",
                                     String.valueOf(entityType));
      }

      BaseComponentType comp = el.getValue();

      Class masterClass = comp.getClass();

      comp.setProperties(new ArrayOfProperties());
      List<JAXBElement<? extends BasePropertyType>> pl = comp.getProperties().getBasePropertyOrTzid();

      //BasePropertyType prop;

      /* ------------------- RecurrenceID --------------------
       * Done early so we know if this is an instance.
       */

      if (emit(pattern, masterClass, RecurrenceIdPropType.class)) {
        String strval = val.getRecurrenceId();
        if ((strval != null) && (strval.length() > 0)) {
          isInstance = true;
          // DORECUR - we should be restoring to original form.

          /* Try using timezone from dtstart so we can more often be in same form
           * as original.
           */
          BwDateTime dts = val.getDtstart();
          RecurrenceIdPropType ri = new RecurrenceIdPropType();
          String tzid = null;

          if (dts.getDateType()) {
            // RECUR - fix all day recurrences sometime
            if (strval.length() > 8) {
              // Try to fix up bad all day recurrence ids. - assume a local timezone
              strval = strval.substring(0, 8);
            }

            ri.setDate(XcalUtil.fromDtval(strval));
          } else {
            if (!val.getForceUTC()) {
              if ((dts != null) && !dts.isUTC()) {
                tzid = dts.getTzid();
              }
            }

            XcalUtil.initDt(ri, strval, tzid);
          }

          pl.add(of.createRecurrenceId(ri));
        }
      }

      /* ------------------- Alarms -------------------- */
      processEventAlarm(val, comp, pattern, masterClass);

      /* ------------------- Attachments -------------------- */
      if (emit(pattern, masterClass, AttachPropType.class)) {
        if (val.getNumAttachments() > 0) {
          for (BwAttachment att: val.getAttachments()) {
  //          pl.add(setAttachment(att));
          }
        }
      }

      /* ------------------- Attendees -------------------- */
      if (emit(pattern, masterClass, AttendeePropType.class)) {
        if (val.getNumAttendees() > 0) {
          for (BwAttendee att: val.getAttendees()) {
            pl.add(of.createAttendee(makeAttendee(att)));
          }
        }
      }

      /* ------------------- Categories -------------------- */

      if (emit(pattern, masterClass, CategoriesPropType.class)) {
        if (val.getNumCategories() > 0) {
          /* This event has a category - do each one separately */

          // LANG - filter on language - group language in one cat list?
          for (BwCategory cat: val.getCategories()) {
            CategoriesPropType c = new CategoriesPropType();

            c.getText().add(cat.getWord().getValue());

            pl.add(of.createCategories((CategoriesPropType)langProp(c, cat.getWord())));
          }
        }
      }

      /* ------------------- Class -------------------- */

      if (emit(pattern, masterClass, ClassPropType.class)) {
        String pval = val.getClassification();
        if (pval != null) {
          ClassPropType c = new ClassPropType();
          c.setText(pval);
          pl.add(of.createClass(c));
        }
      }

      /* ------------------- Comments -------------------- */

      if (emit(pattern, masterClass, CommentPropType.class)) {
        if (val.getNumComments() > 0) {
          for (BwString str: val.getComments()) {
            CommentPropType c = new CommentPropType();
            c.setText(str.getValue());
            pl.add(of.createComment((CommentPropType)langProp(c, str)));
          }
        }
      }

      /* ------------------- Completed -------------------- */

      if (emit(pattern, masterClass, CompletedPropType.class)) {
        if ((entityType == IcalDefs.entityTypeTodo) &&
            (val.getCompleted() != null)) {
          CompletedPropType c = new CompletedPropType();
          c.setUtcDateTime(XcalUtil.getXMlUTCCal(val.getCompleted()));
          pl.add(of.createCompleted(c));
        }
      }

      /* ------------------- Contact -------------------- */

      if (emit(pattern, masterClass, ContactPropType.class)) {
        if (val.getNumContacts() > 0) {
          for (BwContact ctct: val.getContacts()) {
            // LANG
            ContactPropType c = new ContactPropType();
            c.setText(ctct.getCn().getValue());

            pl.add(of.createContact(
                      (ContactPropType)langProp(
                          uidProp(altrepProp(c, ctct.getLink()),
                                  ctct.getUid()),
                                  ctct.getCn())));
          }
        }
      }

      /* ------------------- Created -------------------- */

      if (emit(pattern, masterClass, CreatedPropType.class)) {
        CreatedPropType created = new CreatedPropType();
        created.setUtcDateTime(XcalUtil.getXMlUTCCal(val.getCreated()));
        pl.add(of.createCreated(created));
      }

      /* ------------------- Description -------------------- */

      if (emit(pattern, masterClass, DescriptionPropType.class)) {
        BwStringBase bwstr = val.findDescription(null);
        if (bwstr != null) {
          DescriptionPropType desc = new DescriptionPropType();

          if (bwstr.getValue().contains("Â")) {
            warn("Odd character Â in description: " + bwstr.getValue());
          }
          desc.setText(bwstr.getValue());
          pl.add(of.createDescription((DescriptionPropType)langProp(desc, bwstr)));
        }
      }

      /* ------------------- Due/DtEnd/Duration --------------------
      */

      if ((todo && emit(pattern, masterClass, DuePropType.class)) ||
          (!todo && emit(pattern, masterClass, DtendPropType.class))) {
        if (val.getEndType() == BwEvent.endTypeDate) {
          if (todo) {
            DuePropType due = (DuePropType)makeDateDatetime(new DuePropType(),
                                                            val.getDtend(),
                                                            freeBusy | val.getForceUTC());
            pl.add(of.createDue(due));
          } else {
            DtendPropType dtend = (DtendPropType)makeDateDatetime(new DtendPropType(),
                                                                  val.getDtend(),
                                                                  freeBusy | val.getForceUTC());
            pl.add(of.createDtend(dtend));
          }
        } else if (val.getEndType() == BwEvent.endTypeDuration) {
          DurationPropType dur = new DurationPropType();

          dur.setDuration(val.getDuration());
          pl.add(of.createDuration(dur));
        }
      }

      /* ------------------- DtStamp -------------------- */

      if (emit(pattern, masterClass, DtstampPropType.class)) {
        DtstampPropType dtstamp = new DtstampPropType();
        dtstamp.setUtcDateTime(XcalUtil.getXMlUTCCal(val.getDtstamp()));
        pl.add(of.createDtstamp(dtstamp));
      }

      /* ------------------- DtStart -------------------- */

      if (emit(pattern, masterClass, DtstartPropType.class)) {
        if ((val.getNoStart() == null) || !val.getNoStart()) {
          DtstartPropType dtstart = (DtstartPropType)makeDateDatetime(new DtstartPropType(),
                                                                val.getDtstart(),
                                                                freeBusy | val.getForceUTC());
          pl.add(of.createDtstart(dtstart));
        }
      }

      /* ------------------- ExDate --below------------ */
      /* ------------------- ExRule --below------------- */

      /* ------------------- freebusy -------------------- */

      if (emit(pattern, masterClass, FreebusyPropType.class)) {
        if (entityType == IcalDefs.entityTypeFreeAndBusy) {
          Collection<BwFreeBusyComponent> fbps = val.getFreeBusyPeriods();

          if (fbps != null) {
            for (BwFreeBusyComponent fbc: fbps) {
              FreebusyPropType fb = new FreebusyPropType();

              /*
              int type = fbc.getType();
              FbtypeValueType fbtype;

              if (type == BwFreeBusyComponent.typeBusy) {
                fbtype = FbtypeValueType.BUSY;
              } else if (type == BwFreeBusyComponent.typeFree) {
                fbtype = FbtypeValueType.FREE;
              } else if (type == BwFreeBusyComponent.typeBusyUnavailable) {
                fbtype = FbtypeValueType.BUSY_UNAVAILABLE;
              } else if (type == BwFreeBusyComponent.typeBusyTentative) {
                fbtype = FbtypeValueType.BUSY_TENTATIVE;
              } else {
                fbtype = FbtypeValueType.BUSY;
  //              throw new CalFacadeException("Bad free-busy type " + type);
              }

              ArrayOfParameters pars = getAop(fb);

              FbtypeParamType f = new FbtypeParamType();
              f.setText(fbtype.name());
              */
              ArrayOfParameters pars = getAop(fb);

              FbtypeParamType f = new FbtypeParamType();
              f.setText(BwFreeBusyComponent.fbtypes[fbc.getType()]);
              JAXBElement<FbtypeParamType> param = of.createFbtype(f);
              pars.getBaseParameter().add(param);

              List<PeriodType> pdl =  fb.getPeriod();

              for (Period p: fbc.getPeriods()) {
                PeriodType np = new PeriodType();

                np.setStart(XcalUtil.getXMlUTCCal(p.getStart().toString()));
                np.setEnd(XcalUtil.getXMlUTCCal(p.getEnd().toString()));
                pdl.add(np);
              }

              pl.add(of.createFreebusy(fb));
            }
          }
        }
      }

      /* ------------------- Geo -------------------- */

      if (emit(pattern, masterClass, GeoPropType.class)) {
        BwGeo geo = val.getGeo();
        if (geo != null) {
          GeoPropType g = new GeoPropType();

          g.setLatitude(geo.getLatitude().floatValue());
          g.setLatitude(geo.getLongitude().floatValue());
          pl.add(of.createGeo(g));
        }
      }

      /* ------------------- LastModified -------------------- */

      if (emit(pattern, masterClass, LastModifiedPropType.class)) {
        LastModifiedPropType lm = new LastModifiedPropType();
        lm.setUtcDateTime(XcalUtil.getXMlUTCCal(val.getLastmod()));
        pl.add(of.createLastModified(lm));
      }

      /* ------------------- Location -------------------- */

      if (emit(pattern, masterClass, LocationPropType.class)) {
        BwLocation loc = val.getLocation();
        if (loc != null) {
          LocationPropType l = new LocationPropType();
          l .setText(loc.getAddress().getValue());

          pl.add(of.createLocation((LocationPropType)langProp(uidProp(l,
                                                                      loc.getUid()),
                                                                      loc.getAddress())));
        }
      }

      /* ------------------- Organizer -------------------- */

      if (emit(pattern, masterClass, OrganizerPropType.class)) {
        BwOrganizer org = val.getOrganizer();
        if (org != null) {
          pl.add(of.createOrganizer(makeOrganizer(org)));
        }
      }

      /* ------------------- PercentComplete -------------------- */

      if (emit(pattern, masterClass, PercentCompletePropType.class)) {
        Integer pc = val.getPercentComplete();
        if (pc != null) {
          PercentCompletePropType p = new PercentCompletePropType();
          p.setInteger(BigInteger.valueOf(pc));

          pl.add(of.createPercentComplete(p));
        }
      }

      /* ------------------- Priority -------------------- */

      if (emit(pattern, masterClass, PriorityPropType.class)) {
        Integer prio = val.getPriority();
        if (prio != null) {
          PriorityPropType p = new PriorityPropType();
          p.setInteger(BigInteger.valueOf(prio));

          pl.add(of.createPriority(p));
        }
      }

      /* ------------------- RDate -below------------------- */

      /* ------------------- RelatedTo -------------------- */

      if (emit(pattern, masterClass, RelatedToPropType.class)) {
        BwRelatedTo relto = val.getRelatedTo();
        if (relto != null) {
          RelatedToPropType rt = new RelatedToPropType();

          rt.setUid(relto.getValue());

          if (relto.getRelType() != null) {
            ArrayOfParameters pars = getAop(rt);

            ReltypeParamType r = new ReltypeParamType();
            r.setText(relto.getRelType());
            JAXBElement<ReltypeParamType> param = of.createReltype(r);
            pars.getBaseParameter().add(param);
          }

          pl.add(of.createRelatedTo(rt));
        }
      }

      /* ------------------- Resources -------------------- */

      if (emit(pattern, masterClass, ResourcesPropType.class)) {
        if (val.getNumResources() > 0) {
          /* This event has a resource */

          ResourcesPropType r = new ResourcesPropType();

          List<String> rl = r.getText();

          for (BwString str: val.getResources()) {
            // LANG
            rl.add(str.getValue());
          }

          pl.add(of.createResources(r));
        }
      }

      /* ------------------- RRule -below------------------- */

      /* ------------------- Sequence -------------------- */

      if (emit(pattern, masterClass, SequencePropType.class)) {
        if (val.getSequence() > 0) {
          SequencePropType s = new SequencePropType();
          s.setInteger(BigInteger.valueOf(val.getSequence()));

          pl.add(of.createSequence(s));
        }
      }

      /* ------------------- Status -------------------- */

      if (emit(pattern, masterClass, StatusPropType.class)) {
        String status = val.getStatus();
        if ((status != null) && !status.equals(BwEvent.statusMasterSuppressed)) {
          StatusPropType s = new StatusPropType();

          s.setText(status);
          pl.add(of.createStatus(s));
        }
      }

      /* ------------------- Summary -------------------- */

      if (emit(pattern, masterClass, SummaryPropType.class)) {
        BwString bwstr = val.findSummary(null);
        if (bwstr != null) {
          SummaryPropType s = new SummaryPropType();
          s.setText(bwstr.getValue());

          s = (SummaryPropType)langProp(s, bwstr);

          pl.add(of.createSummary(s));
        }
      }

      /* ------------------- Transp -------------------- */

      if (emit(pattern, masterClass, TranspPropType.class)) {
        String strval = val.getTransparency();
        if ((strval != null) && (strval.length() > 0)) {
          TranspPropType t = new TranspPropType();
          t.setText(strval);
          pl.add(of.createTransp(t));
        }
      }

      /* ------------------- Uid -------------------- */

      if (emit(pattern, masterClass, UidPropType.class)) {
        UidPropType uid = new UidPropType();
        uid.setText(val.getUid());
        pl.add(of.createUid(uid));
      }

      /* ------------------- Url -------------------- */

      if (emit(pattern, masterClass, UrlPropType.class)) {
        String strval = val.getLink();

        if (strval != null) {
          // Possibly drop this if we do it on input and check all data
          strval = strval.trim();
        }

        if ((strval != null) && (strval.length() > 0)) {
          UrlPropType u = new UrlPropType();

          u.setUri(strval);
          pl.add(of.createUrl(u));
        }
      }

      /* ------------------- X-PROPS -------------------- */

      /* ------------------- Cost -------------------- */

      if (emit(pattern, masterClass, XBedeworkCostPropType.class)) {
        if (val.getCost() != null) {
          XBedeworkCostPropType c = new XBedeworkCostPropType();

          c.setText(val.getCost());
          pl.add(of.createXBedeworkCost(c));
        }
      }

      if (val.getNumXproperties() > 0) {
        /* This event has x-props */

        try {
          xpropertiesToXcal(pl, val.getXproperties(),
                            pattern, masterClass,
                            wrapXprops);
        } catch (Throwable t) {
          // XXX For the moment swallow these.
          error(t);
        }
      }

      /* ------------------- Overrides -------------------- */

      if (!isInstance && !isOverride && val.testRecurring()) {
        doRecurring(pattern, masterClass, val, pl);
      }

      return el;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Build recurring properties from event.
   *
   * @param pattern
   * @param compCl - component class for pattern matching
   * @param val
   * @param pl
   * @throws CalFacadeException
   */
  public static void doRecurring(final BaseComponentType pattern,
                                 final Class compCl,
                                 final BwEvent val,
                                 final List<JAXBElement<? extends BasePropertyType>> pl) throws CalFacadeException {
    try {
      if (emit(pattern, compCl, RrulePropType.class) &&
          val.hasRrules()) {
        for(String s: val.getRrules()) {
          RRule rule = new RRule();
          rule.setValue(s);

          Recur r = rule.getRecur();
          RecurType rt = new RecurType();

          rt.setFreq(FreqRecurType.fromValue(r.getFrequency()));
          if (r.getCount() > 0) {
            rt.setCount(BigInteger.valueOf(r.getCount()));
          }

          Date until = r.getUntil();
          if (until != null) {
            UntilRecurType u = new UntilRecurType();
            /*
            if (until instanceof DateTime) {
              u.setDateTime(until.toString());
            } else {
              u.setDate(until.toString());
            }
            */
            XcalUtil.initUntilRecur(u, until.toString());
          }

          if (r.getInterval() > 0) {
            rt.setInterval(String.valueOf(r.getInterval()));
          }

          listFromNumberList(rt.getBysecond(),
                             r.getSecondList());

          listFromNumberList(rt.getByminute(),
                             r.getMinuteList());

          listFromNumberList(rt.getByhour(),
                             r.getHourList());

          if (r.getDayList() != null) {
            List<String> l = rt.getByday();

            for (WeekDay wd: r.getDayList()) {
              l.add(wd.getDay().name());
            }
          }

          listFromNumberList(rt.getByyearday(),
                             r.getYearDayList());

          intlistFromNumberList(rt.getBymonthday(),
                                r.getMonthDayList());

          listFromNumberList(rt.getByweekno(),
                             r.getWeekNoList());

          intlistFromNumberList(rt.getBymonth(),
                                r.getMonthList());

          bigintlistFromNumberList(rt.getBysetpos(),
                                r.getSetPosList());

          RrulePropType rrp = new RrulePropType();
          rrp.setRecur(rt);
          pl.add(of.createRrule(rrp));
        }
      }

      /*
      if (emit(pattern, compCl, ExrulePropType.class) &&
          val.hasExrules()) {
        for(String s: val.getExrules()) {
          ExRule rule = new ExRule();
          rule.setValue(s);

          pl.add(rule);
        }
      }

      if (emit(pattern, compCl, RdatePropType.class) {
        makeDlp(false, val.getRdates(), pl);
      }

      if (emit(pattern, compCl, ExdatePropType.class) {
        makeDlp(true, val.getExdates(), pl);
      }
      */
//    } catch (CalFacadeException cfe) {
  //    throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  /** make an attendee
   *
   * @param val
   * @return Attendee
   * @throws Throwable
   */
  public static AttendeePropType makeAttendee(final BwAttendee val) throws Throwable {
    AttendeePropType prop = new AttendeePropType();

    prop.setCalAddress(val.getAttendeeUri());

    ArrayOfParameters pars = new ArrayOfParameters();
    JAXBElement<? extends BaseParameterType> param;
    prop.setParameters(pars);

    if (val.getRsvp()) {
      RsvpParamType r = new RsvpParamType();
      r.setBoolean(true);
      param = of.createRsvp(r);
      pars.getBaseParameter().add(param);
    }

    String temp = val.getCn();
    if (temp != null) {
      CnParamType cn = new CnParamType();
      cn.setText(temp);
      param = of.createCn(cn);
      pars.getBaseParameter().add(param);
    }

    temp = val.getPartstat();
    if (temp == null) {
      temp = IcalDefs.partstatValNeedsAction;
    }

    PartstatParamType partstat = new PartstatParamType();
    partstat.setText(temp);
    param = of.createPartstat(partstat);
    pars.getBaseParameter().add(param);

    temp = val.getScheduleStatus();
    if (temp != null) {
      ScheduleStatusParamType ss = new ScheduleStatusParamType();
      ss.setText(temp);
      param = of.createScheduleStatus(ss);
      pars.getBaseParameter().add(param);
    }

    temp = val.getCuType();
    if (temp != null) {
      /*
      CutypeValueType cp;
      try {
        cp = CutypeValueType.fromValue(temp);
      } catch (Throwable t) {
        cp = CutypeValueType.UNKNOWN;
      }
      CutypeParamType c = new CutypeParamType();
      c.setText(cp.name());
      */
      CutypeParamType c = new CutypeParamType();
      c.setText(val.getCuType());
      param = of.createCutype(c);
      pars.getBaseParameter().add(param);
    }

    temp = val.getDelegatedFrom();
    if (temp != null) {
      DelegatedFromParamType df = new DelegatedFromParamType();
      df.getCalAddress().add(temp);
      param = of.createDelegatedFrom(df);
      pars.getBaseParameter().add(param);
    }

    temp = val.getDelegatedTo();
    if (temp != null) {
      DelegatedToParamType dt = new DelegatedToParamType();
      dt.getCalAddress().add(temp);
      param = of.createDelegatedTo(dt);
      pars.getBaseParameter().add(param);
    }

    temp = val.getDir();
    if (temp != null) {
      DirParamType d = new DirParamType();
      d.setUri(temp);
      param = of.createDir(d);
      pars.getBaseParameter().add(param);
    }

    temp = val.getLanguage();
    if (temp != null) {
      LanguageParamType l = new LanguageParamType();
      l.setText(temp);
      param = of.createLanguage(l);
      pars.getBaseParameter().add(param);
    }

    temp = val.getMember();
    if (temp != null) {
      MemberParamType m = new MemberParamType();
      m.getCalAddress().add(temp);
      param = of.createMember(m);
      pars.getBaseParameter().add(param);
    }

    /*
    temp = val.getRole();
    if (temp != null) {
      RoleValueType role;
      try {
        role = RoleValueType.fromValue(temp);
      } catch (Throwable t) {
        role = RoleValueType.REQ_PARTICIPANT;
      }

      RoleParamType r = new RoleParamType();
      r.setText(val.getRole());
      param = of.createRole(r);
      pars.getBaseParameter().add(param);
    }*/

    temp = val.getRole();
    if (temp != null) {
      RoleParamType r = new RoleParamType();
      r.setText(val.getRole());
      param = of.createRole(r);
      pars.getBaseParameter().add(param);
    }

    temp = val.getSentBy();
    if (temp != null) {
      SentByParamType sb = new SentByParamType();
      sb.setCalAddress(temp);
      param = of.createSentBy(sb);
      pars.getBaseParameter().add(param);
    }

    return prop;
  }

  /**
   * @param val
   * @return Organizer
   * @throws Throwable
   */
  public static OrganizerPropType makeOrganizer(final BwOrganizer val) throws Throwable {
    OrganizerPropType prop = new OrganizerPropType();

    prop.setCalAddress(val.getOrganizerUri());

    ArrayOfParameters pars = new ArrayOfParameters();
    JAXBElement<? extends BaseParameterType> param;
    prop.setParameters(pars);

    String temp = val.getScheduleStatus();
    if (temp != null) {
      ScheduleStatusParamType ss = new ScheduleStatusParamType();
      ss.setText(temp);
      param = of.createScheduleStatus(ss);
      pars.getBaseParameter().add(param);
    }

    temp = val.getCn();
    if (temp != null) {
      CnParamType cn = new CnParamType();
      cn.setText(temp);
      param = of.createCn(cn);
      pars.getBaseParameter().add(param);
    }

    temp = val.getDir();
    if (temp != null) {
      DirParamType d = new DirParamType();
      d.setUri(temp);
      param = of.createDir(d);
      pars.getBaseParameter().add(param);
    }

    temp = val.getLanguage();
    if (temp != null) {
      LanguageParamType l = new LanguageParamType();
      l.setText(temp);
      param = of.createLanguage(l);
      pars.getBaseParameter().add(param);
    }

    temp = val.getSentBy();
    if (temp != null) {
      SentByParamType sb = new SentByParamType();
      sb.setCalAddress(temp);
      param = of.createSentBy(sb);
      pars.getBaseParameter().add(param);
    }

    return prop;
  }

  /** Process any alarms.
   *
   * @param ev
   * @param comp
   * @param pattern
   * @param masterClass
   * @throws CalFacadeException
   */
  public static void processEventAlarm(final BwEvent ev,
                                       final BaseComponentType comp,
                                       final BaseComponentType pattern,
                                       final Class masterClass) throws CalFacadeException {
    if (!emit(pattern, masterClass, ValarmType.class)) {
      return;
    }

    Set<BwAlarm> als = ev.getAlarms();
    if ((als == null) || als.isEmpty()) {
      return;
    }

    if (!(comp instanceof VeventType) && !(comp instanceof VtodoType)) {
      warn("Entity of class " + ev.getClass() +
           " has alarms but not allowed by entity of type " +
           comp.getClass());
    }

    ArrayOfComponents aoc = comp.getComponents();

    if (aoc == null) {
      aoc = new ArrayOfComponents();
      comp.setComponents(aoc);
    }

    for (BwAlarm alarm: als) {
      ValarmType va = Xalarms.toXAlarm(ev, alarm, pattern, masterClass);
      aoc.getBaseComponent().add(of.createValarm(va));
    }
  }
}
