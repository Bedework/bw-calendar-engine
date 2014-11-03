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
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.data.CalendarParserImpl;
import net.fortuna.ical4j.data.UnfoldingReader;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.CategoryList;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.ResourceList;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.Available;
import net.fortuna.ical4j.model.component.VAvailability;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.component.VJournal;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.component.VVoter;
import net.fortuna.ical4j.model.parameter.AltRep;
import net.fortuna.ical4j.model.parameter.FbType;
import net.fortuna.ical4j.model.parameter.RelType;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.parameter.XParameter;
import net.fortuna.ical4j.model.property.AcceptResponse;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.BusyType;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.Clazz;
import net.fortuna.ical4j.model.property.Comment;
import net.fortuna.ical4j.model.property.Completed;
import net.fortuna.ical4j.model.property.Contact;
import net.fortuna.ical4j.model.property.Created;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.LastModified;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.PercentComplete;
import net.fortuna.ical4j.model.property.PollMode;
import net.fortuna.ical4j.model.property.PollProperties;
import net.fortuna.ical4j.model.property.PollWinner;
import net.fortuna.ical4j.model.property.Priority;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RecurrenceId;
import net.fortuna.ical4j.model.property.RelatedTo;
import net.fortuna.ical4j.model.property.Resources;
import net.fortuna.ical4j.model.property.Sequence;
import net.fortuna.ical4j.model.property.Status;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Transp;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Url;
import net.fortuna.ical4j.model.property.Version;

import java.io.StringReader;
import java.net.URI;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Class to provide utility methods for translating to VEvent ical4j classes
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public class VEventUtil extends IcalUtil {

  /** Make an Icalendar component from a BwEvent object. This may produce a
   * VEvent, VTodo, VJournal or VPoll.
   *
   * @param ei
   * @param isOverride - true if event object is an override
   * @param tzreg
   * @param uriGen
   * @param currentPrincipal - href for current authenticated user
   * @return Component
   * @throws CalFacadeException
   */
  public static Component toIcalComponent(final EventInfo ei,
                                          final boolean isOverride,
                                          final TimeZoneRegistry tzreg,
                                          final URIgen uriGen,
                                          final String currentPrincipal) throws CalFacadeException {
    if ((ei == null) || (ei.getEvent() == null)) {
      return null;
    }

    BwEvent val = ei.getEvent();

    boolean isInstance = false;

    try {
      Component xcomp = null;
      Calendar cal = null;

      List<BwXproperty> xcompProps = val.getXproperties(BwXproperty.bedeworkIcal);
      if (!Util.isEmpty(xcompProps)) {
        BwXproperty xcompProp = xcompProps.get(0);
        String xcompPropVal = xcompProp.getValue();

        if (xcompPropVal != null) {
          StringBuilder sb = new StringBuilder();
          Icalendar ic = new Icalendar();

          try {
            sb.append("BEGIN:VCALENDAR\n");
            sb.append(Version.VERSION_2_0.toString());
            sb.append("\n");
            sb.append(xcompPropVal);
            if (!xcompPropVal.endsWith("\n")) {
              sb.append("\n");
            }
            sb.append("END:VCALENDAR\n");

            CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

            UnfoldingReader ufrdr = new UnfoldingReader(new StringReader(sb.toString()),
                                                        true);

            cal = bldr.build(ufrdr);
          } catch (Throwable t) {
            error(t);
            error("Trying to parse:\n" + xcompPropVal);
          }
        }
      }

      Component comp;
      PropertyList pl = new PropertyList();
      boolean freeBusy = false;
      boolean vavail = false;
      boolean todo = false;
      boolean vpoll = false;

      int entityType = val.getEntityType();
      if (entityType == IcalDefs.entityTypeEvent) {
        comp = new VEvent(pl);
      } else if (entityType == IcalDefs.entityTypeTodo) {
        comp = new VToDo(pl);
        todo = true;
      } else if (entityType == IcalDefs.entityTypeJournal) {
        comp = new VJournal(pl);
      } else if (entityType == IcalDefs.entityTypeFreeAndBusy) {
        comp = new VFreeBusy(pl);
        freeBusy = true;
      } else if (entityType == IcalDefs.entityTypeVavailability) {
        comp = new VAvailability(pl);
        vavail = true;
      } else if (entityType == IcalDefs.entityTypeAvailable) {
        comp = new Available(pl);
      } else if (entityType == IcalDefs.entityTypeVpoll) {
        comp = new VPoll(pl);
        vpoll = true;
      } else {
        throw new CalFacadeException("org.bedework.invalid.entity.type",
                                     String.valueOf(entityType));
      }

      if (cal != null) {
        xcomp = cal.getComponent(comp.getName());
      }

      Property prop;

      /* ------------------- RecurrenceID --------------------
       * Done early so we know if this is an instance.
       */

      String strval = val.getRecurrenceId();
      if ((strval != null) && (strval.length() > 0)) {
        isInstance = true;

        pl.add(new RecurrenceId(makeZonedDt(val, strval)));
      }

      /* ------------------- Alarms -------------------- */
      VAlarmUtil.processEventAlarm(val, comp, currentPrincipal);

      /* ------------------- Attachments -------------------- */
      if (val.getNumAttachments() > 0) {
        for (BwAttachment att: val.getAttachments()) {
          pl.add(setAttachment(att));
        }
      }

      /* ------------------- Attendees -------------------- */
      if (!vpoll && (val.getNumAttendees() > 0)) {
        for (BwAttendee att: val.getAttendees()) {
          prop = setAttendee(att);
          mergeXparams(prop, xcomp);
          pl.add(prop);
        }
      }

      /* ------------------- Categories -------------------- */

      if (val.getNumCategories() > 0) {
        /* This event has a category - do each one separately */

        // LANG - filter on language - group language in one cat list?
        for (BwCategory cat: val.getCategories()) {
          prop = new Categories();
          CategoryList cl = ((Categories)prop).getCategories();

          cl.add(cat.getWord().getValue());

          pl.add(langProp(prop, cat.getWord()));
        }
      }

      /* ------------------- Class -------------------- */

      String pval = val.getClassification();
      if (pval != null) {
        pl.add(new Clazz(pval));
      }

      /* ------------------- Comments -------------------- */

      if (val.getNumComments() > 0) {
        for (BwString str: val.getComments()) {
          pl.add(langProp(new Comment(str.getValue()), str));
        }
      }

      /* ------------------- Completed -------------------- */

      if ((todo || vpoll) && (val.getCompleted() != null)) {
        prop = new Completed(new DateTime(val.getCompleted()));
        pl.add(prop);
      }

      /* ------------------- Contact -------------------- */

      if (val.getNumContacts() > 0) {
        for (BwContact c: val.getContacts()) {
          // LANG
          prop = new Contact(c.getCn().getValue());
          String l = c.getLink();

          if (l != null) {
            prop.getParameters().add(new AltRep(l));
          }
          pl.add(langProp(uidProp(prop, c.getUid()), c.getCn()));
        }
      }

      /* ------------------- Cost -------------------- */

      if (val.getCost() != null) {
        IcalUtil.addXproperty(pl, BwXproperty.bedeworkCost,
                              null, val.getCost());
      }

      /* ------------------- Created -------------------- */

      prop = new Created(val.getCreated());
//      if (pars.includeDateTimeProperty) {
//        prop.getParameters().add(Value.DATE_TIME);
//      }
      pl.add(prop);

      /* ------------------- Description -------------------- */

      BwStringBase bwstr = val.findDescription(null);
      if (bwstr != null) {
        pl.add(langProp(new Description(bwstr.getValue()), bwstr));
      }

      /* ------------------- Due/DtEnd/Duration --------------------
      */

      if (val.getEndType() == StartEndComponent.endTypeDate) {
        if (todo) {
          Due due = val.getDtend().makeDue(tzreg);
          if (freeBusy | val.getForceUTC()) {
            due.setUtc(true);
          }
          pl.add(due);
        } else {
          DtEnd dtend = val.getDtend().makeDtEnd(tzreg);
          if (freeBusy | val.getForceUTC()) {
            dtend.setUtc(true);
          }
          pl.add(dtend);
        }
      } else if (val.getEndType() == StartEndComponent.endTypeDuration) {
        addProperty(comp, new Duration(new Dur(val.getDuration())));
      }

      /* ------------------- DtStamp -------------------- */

      prop = new DtStamp(new DateTime(val.getDtstamp()));
//      if (pars.includeDateTimeProperty) {
//        prop.getParameters().add(Value.DATE_TIME);
//      }
      pl.add(prop);

      /* ------------------- DtStart -------------------- */

      if (!val.getNoStart()) {
        DtStart dtstart = val.getDtstart().makeDtStart(tzreg);
        if (freeBusy | val.getForceUTC()) {
          dtstart.setUtc(true);
        }
        pl.add(dtstart);
      }

      /* ------------------- ExDate --below------------ */
      /* ------------------- ExRule --below------------- */

      if (freeBusy) {
        Collection<BwFreeBusyComponent> fbps = val.getFreeBusyPeriods();

        if (fbps != null) {
          for (BwFreeBusyComponent fbc: fbps) {
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

      }

      /* ------------------- Geo -------------------- */

      if (!vpoll) {
        BwGeo geo = val.getGeo();
        if (geo != null) {
          pl.add(geo);
        }
      }

      /* ------------------- LastModified -------------------- */

      prop = new LastModified(new DateTime(val.getLastmod()));
//      if (pars.includeDateTimeProperty) {
//        prop.getParameters().add(Value.DATE_TIME);
//      }
      pl.add(prop);

      /* ------------------- Location -------------------- */

      if (!vpoll) {
        BwLocation loc = val.getLocation();
        if (loc != null) {
          prop = new Location(loc.getAddress().getValue());

          pl.add(langProp(uidProp(prop, loc.getUid()), loc.getAddress()));
        }
      }

      /* ------------------- Organizer -------------------- */

      BwOrganizer org = val.getOrganizer();
      if (org != null) {
        prop = setOrganizer(org);
        mergeXparams(prop, xcomp);
        pl.add(prop);
      }

      /* ------------------- PercentComplete -------------------- */

      if (todo) {
        Integer pc = val.getPercentComplete();
        if (pc != null) {
          pl.add(new PercentComplete(pc.intValue()));
        }
      }

      /* ------------------- Priority -------------------- */

      Integer prio = val.getPriority();
      if (prio != null) {
        pl.add(new Priority(prio.intValue()));
      }

      /* ------------------- RDate -below------------------- */

      /* ------------------- RelatedTo -------------------- */

      /* We encode related to (maybe) as triples - reltype, value-type, value */

      String[] info = null;

      BwRelatedTo relto = val.getRelatedTo();
      if (relto != null) {
        info = new String[3];

        info[0] = relto.getRelType();
        info[1] = ""; // default
        info[2] = relto.getValue();
      } else {
        String relx = val.getXproperty(BwXproperty.bedeworkRelatedTo);

        if (relx != null) {
          info = Util.decodeArray(relx);
        }
      }

      if (info != null) {
        int i = 0;

        while (i < info.length) {
          RelatedTo irelto;

          String reltype = info[i];
          String valtype = info[i + 1];
          String relval = info[i + 2];

          ParameterList rtpl = null;
          if (reltype.length() > 0) {
            rtpl = new ParameterList();
            rtpl.add(new RelType(reltype));
          }

          if (valtype.length() > 0) {
            if (rtpl == null) {
              rtpl = new ParameterList();
            }
            rtpl.add(new Value(valtype));
          }

          if (rtpl != null) {
            irelto = new RelatedTo(rtpl, relval);
          } else {
            irelto = new RelatedTo(relval);
          }

          pl.add(irelto);
          i += 3;
        }
      }

      /* ------------------- Resources -------------------- */

      if (val.getNumResources() > 0) {
        /* This event has a resource */

        prop = new Resources();
        ResourceList rl = ((Resources)prop).getResources();

        for (BwString str: val.getResources()) {
          // LANG
          rl.add(str.getValue());
        }

        pl.add(prop);
      }

      /* ------------------- RRule -below------------------- */

      /* ------------------- Sequence -------------------- */

      if (val.getSequence() > 0) {
        pl.add(new Sequence(val.getSequence()));
      }

      /* ------------------- Status -------------------- */

      String status = val.getStatus();
      if ((status != null) && !status.equals(BwEvent.statusMasterSuppressed)) {
        pl.add(new Status(status));
      }

      /* ------------------- Summary -------------------- */

      bwstr = val.findSummary(null);
      if (bwstr != null) {
        pl.add(langProp(new Summary(bwstr.getValue()), bwstr));
      }

      /* ------------------- Transp -------------------- */

      if (!todo && !vpoll) {
        strval = val.getPeruserTransparency(currentPrincipal);

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new Transp(strval));
        }
      }

      /* ------------------- Uid -------------------- */

      pl.add(new Uid(val.getUid()));

      /* ------------------- Url -------------------- */

      strval = val.getLink();

      if (strval != null) {
        // Possibly drop this if we do it on input and check all data
        strval = strval.trim();
      }

      if ((strval != null) && (strval.length() > 0)) {
        URI uri = Util.validURI(strval);
        if (uri != null) {
          pl.add(new Url(uri));
        }
      }

      /* ------------------- X-PROPS -------------------- */

      if (val.getNumXproperties() > 0) {
        /* This event has x-props */

        try {
          IcalUtil.xpropertiesToIcal(pl, val.getXproperties());
        } catch (Throwable t) {
          // XXX For the moment swallow these.
          error(t);
        }
      }

      /* ------------------- Overrides -------------------- */

      if (!vpoll && !isInstance && !isOverride && val.testRecurring()) {
        doRecurring(val, pl);
      }

      /* ------------------- Available -------------------- */

      if (vavail) {
        if (ei.getNumContainedItems() > 0) {
          VAvailability va = (VAvailability)comp;
          for (EventInfo aei: ei.getContainedItems()) {
            va.getAvailable().add(toIcalComponent(aei, false, tzreg,
                                                  uriGen, currentPrincipal));
          }
        }

        /* ----------- Vavailability - busyType ----------------- */

        String s = val.getBusyTypeString();
        if (s != null) {
          pl.add(new BusyType(s));
        }
      }

      /* ------------------- Vpoll -------------------- */

      //if (!vpoll && (val.getPollItemId() != null)) {
      //  pl.add(new PollItemId(val.getPollItemId()));
     // }

      if (vpoll) {
        final Integer ival = val.getPollWinner();

        if (ival != null) {
          pl.add(new PollWinner(ival));
        }

        strval = val.getPollAcceptResponse();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new AcceptResponse(strval));
        }

        strval = val.getPollMode();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollMode(strval));
        }

        strval = val.getPollProperties();

        if ((strval != null) && (strval.length() > 0)) {
          pl.add(new PollProperties(strval));
        }

        final Map<String, VVoter> vvoters = parseVpollVvoters(val);

        for (final VVoter vv: vvoters.values()) {
          ((VPoll)comp).getVoters().add(vv);
        }

        final Map<Integer, Component> comps = parseVpollCandidates(val);

        for (final Component candidate: comps.values()) {
          ((VPoll)comp).getCandidates().add(candidate);
        }
      }

      return comp;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Build recurring properties from event.
   *
   * @param val
   * @param pl
   * @throws CalFacadeException
   */
  public static void doRecurring(final BwEvent val,
                                 final PropertyList pl) throws CalFacadeException {
    try {
      if (val.hasRrules()) {
        for(String s: val.getRrules()) {
          RRule rule = new RRule();
          rule.setValue(s);

          pl.add(rule);
        }
      }

      if (val.hasExrules()) {
        for(String s: val.getExrules()) {
          ExRule rule = new ExRule();
          rule.setValue(s);

          pl.add(rule);
        }
      }

      makeDlp(val, false, val.getRdates(), pl);

      makeDlp(val, true, val.getExdates(), pl);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private static void mergeXparams(final Property p, final Component c) {
    if (c == null) {
      return;
    }

    PropertyList pl = c.getProperties(p.getName());

    if (Util.isEmpty(pl)) {
      return;
    }

    String pval = p.getValue();

    if (p instanceof Attach) {
      // We just saved the hash for binary content
      Value v = (Value)p.getParameter(Parameter.VALUE);

      if (v != null) {
        pval = String.valueOf(pval.hashCode());
      }
    }

    Property from = null;

    if (pl.size() == 1) {
      from = (Property)pl.get(0);
    } else {
      // Look for value?
      Iterator pit = pl.iterator();
      while (pit.hasNext()) {
        from = (Property)pit.next();
        if (from.getValue().equals(pval)) {
          break;
        }
      }
    }

    if (from == null) {
      return;
    }


    ParameterList params = from.getParameters();

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

      p.getParameters().add(xpar);
    }
  }

  private static Property uidProp(final Property prop, final String uid) {
    Parameter par = new XParameter(BwXproperty.xparUid, uid);

    prop.getParameters().add(par);

    return prop;
  }

  private static Property langProp(final Property prop, final BwStringBase s) {
    Parameter par = s.getLangPar();

    if (par != null) {
      prop.getParameters().add(par);
    }

    return prop;
  }

  private static void makeDlp(final BwEvent val,
                              final boolean exdt,
                              final Collection<BwDateTime> dts,
                              final PropertyList pl) throws Throwable {
    if ((dts == null) || (dts.isEmpty())) {
      return;
    }

    TimeZone tz = null;
    if (!val.getForceUTC()) {
      BwDateTime dtstart = val.getDtstart();

      if ((dtstart != null) && !dtstart.isUTC()) {
        DtStart ds = dtstart.makeDtStart();
        tz = ds.getTimeZone();
      }
    }

    /* Generate as one date per property - matches up to other vendors better */
    for (BwDateTime dt: dts) {
      DateList dl = null;

      /* Always use the UTC values */
      boolean dateType = false;

      if (dt.getDateType()) {
        dl = new DateList(Value.DATE);
        dl.setUtc(true);
        dateType = true;
        dl.add(new Date(dt.getDtval()));
      } else {
        dl = new DateList(Value.DATE_TIME);

        if (tz == null) {
          dl.setUtc(true);
          DateTime dtm = new DateTime(dt.getDate());
          dtm.setUtc(true);
          dl.add(dtm);
        } else {
          dl.setTimeZone(tz);
          DateTime dtm = new DateTime(dt.getDate());
          dtm.setTimeZone(tz);
          dl.add(dtm);
        }
      }

      DateListProperty dlp;

      if (exdt) {
        dlp = new ExDate(dl);
      } else {
        dlp = new RDate(dl);
      }

      if (tz != null) {
        dlp.setTimeZone(tz);
      }

      if (dateType) {
        dlp.getParameters().add(Value.DATE);
      }

      pl.add(dlp);
    }
  }

  private static Date makeZonedDt(final BwEvent val,
                                  final String dtval) throws Throwable {
    BwDateTime dtstart = val.getDtstart();

    Date dt = new DateTime(dtval);

    if (dtstart.getDateType()) {
      // RECUR - fix all day recurrences sometime
      if (dtval.length() > 8) {
        // Try to fix up bad all day recurrence ids. - assume a local timezone
        ((DateTime)dt).setTimeZone(null);
        return new Date(dt.toString().substring(0, 8));
      }

      return dt;
    }

    if (val.getForceUTC()) {
      return dt;
    }

    if ((dtstart != null) && !dtstart.isUTC()) {
      DtStart ds = dtstart.makeDtStart();
      ((DateTime)dt).setTimeZone(ds.getTimeZone());
    }

    return dt;
  }

     /*
  private String makeContactString(BwSponsor sp) {
    if (pars.simpleContact) {
      return sp.getName();
    }

    StringBuilder sb = new StringBuilder(sp.getName());
    addNonNull(defaultDelim, Resources.PHONENBR, sp.getPhone(), sb);
    addNonNull(defaultDelim, Resources.EMAIL, sp.getEmail(), sb);
    addNonNull(urlDelim, Resources.URL, sp.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }

  /* * Build a location string value from the location.
   *
   * <p>We try to build something we can parse later.
   * /
  private String makeLocationString(BwLocation loc) {
    if (pars.simpleLocation) {
      return loc.getAddress();
    }

    StringBuilder sb = new StringBuilder(loc.getAddress());
    addNonNull(defaultDelim, Resources.SUBADDRESS, loc.getSubaddress(), sb);
    addNonNull(urlDelim, Resources.URL, loc.getLink(), sb);

    if (sb.length() == 0) {
      return null;
    }

    return sb.toString();
  }*/
}

