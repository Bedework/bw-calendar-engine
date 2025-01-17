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
package org.bedework.calsvc.scheduling;

import org.bedework.access.PrivilegeDefs;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.caldav.util.filter.EntityTypeFilter;
import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.OrFilter;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDuration;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwFreeBusyComponent;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.ScheduleResult;
import org.bedework.calfacade.ScheduleResult.ScheduleRecipientResult;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.EventPeriod;
import org.bedework.calfacade.util.EventPeriods;
import org.bedework.calfacade.util.Granulator;
import org.bedework.calfacade.util.Granulator.GetPeriodsPars;
import org.bedework.calsvc.CalSvc;
import org.bedework.calsvci.CalendarsI;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.webdav.servlet.shared.WebdavException;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Period;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.TreeSet;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressivly implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate peices.
 *
 * <p>This piece handles the freebusy method
 *
 * @author douglm
 *
 */
public abstract class FreeAndBusyHandler extends OutBoxHandler {
  private static final int fbtb = BwFreeBusyComponent.typeBusy;
  private static final int fbtf = BwFreeBusyComponent.typeFree;
  private static final int fbtbu = BwFreeBusyComponent.typeBusyUnavailable;
  private static final int fbtbt = BwFreeBusyComponent.typeBusyTentative;

  private static final int[][] typeTable = {
    {fbtb, fbtb, fbtb, fbtb}, // typeto == typeBusy
    {fbtb, fbtf, fbtbu, fbtbt}, // typeto == typeFree
    {fbtb, fbtbu, fbtbu, fbtbu}, // typeto == typeBusyUnavailable
    {fbtb, fbtbt, fbtbu, fbtbt} // typeto == typeBusyTentative
  };

  FreeAndBusyHandler(final CalSvc svci) {
    super(svci);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SchedulingI#getFreeBusy(java.util.Collection, org.bedework.calfacade.BwPrincipal, org.bedework.calfacade.BwDateTime, org.bedework.calfacade.BwDateTime, org.bedework.calfacade.BwOrganizer, java.lang.String)
   */
  @Override
  public BwEvent getFreeBusy(final Collection<BwCalendar> fbset,
                             final BwPrincipal<?> who,
                             final BwDateTime start,
                             final BwDateTime end,
                             final BwOrganizer org,
                             final String uid,
                             final String exceptUid) {
    final CalendarsI colHandler = getSvc().getCalendarsHandler();
    Collection<BwCalendar> cals = null;

    if (fbset != null) {
      /* Don't check - we do so at the fetch of events
      getCal().checkAccess(cal, PrivilegeDefs.privReadFreeBusy, false);
      */
      cals = addToFreeBusySet(cals, fbset);
    } else if (getPrincipal().equals(who)) {
      cals = getFreebusySet();
    } else {
      /* First see if we have free busy access to the principals calendar */

      /* XXX This needs to be brought into line with CalDAV.

      cal = getCal().getCalendars(u, PrivilegeDefs.privReadFreeBusy);
      if (cal == null) {
        throw new BedeworkAccessException();
      }

      getCal().checkAccess(cal, PrivilegeDefs.privReadFreeBusy, false);
      */
      /* CalDAV uses Inbox to determine scheduling acccess */
      try {
        getSpecialCalendar(who,
                           BwCalendar.calTypeInbox,
                           true,
                           PrivilegeDefs.privReadFreeBusy);
      } catch (final BedeworkAccessException ignored) {
        getSpecialCalendar(who,
                           BwCalendar.calTypeInbox,
                           true,
                           PrivilegeDefs.privScheduleFreeBusy);
      }

      cals = addToFreeBusySet(cals,
                              colHandler.getChildren(colHandler.getHome(who, true)));
    }

    if (cals == null) {
      throw new BedeworkAccessException();
    }

    final var fb = new BwEventObj();

    fb.setEntityType(IcalDefs.entityTypeFreeAndBusy);
    fb.setOwnerHref(who.getPrincipalRef());
    fb.setDtstart(start);
    fb.setDtend(end);
    fb.setEndType(StartEndComponent.endTypeDate);

    if (uid == null) {
      fb.assignGuid(getSvc().getSystemProperties().getSystemid());
    } else {
      fb.setUid(uid);
    }

    fb.setDtstamps(getCurrentTimestamp());

    final String uri = getSvc().getDirectories().principalToCaladdr(who);

    BwAttendee att = new BwAttendee();
    att.setAttendeeUri(uri);
    fb.addAttendee(att);

    fb.setOrganizer((BwOrganizer)org.clone());

    final Collection<EventInfo> events = new TreeSet<>();
    /* Only events and freebusy for freebusy reports. */
    final FilterBase filter = new OrFilter();
    try {
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "event",
                                                            false));
      filter.addChild(EntityTypeFilter.makeEntityTypeFilter(null,
                                                            "freeAndBusy",
                                                            false));
    } catch (final WebdavException t) {
      throw new BedeworkException(t);
    }

    final String userHref = who.getPrincipalRef();

    for (final BwCalendar c: cals) {
      if (!c.getAffectsFreeBusy()) {
        continue;
      }

      // XXX If it's an external subscription we probably just get free busy and
      // merge it in.

      final RecurringRetrievalMode rrm = new RecurringRetrievalMode(
                              Rmode.expanded, start, end);

      final Collection<EventInfo> evs = getEvents(Collections.singleton(c),
                                                  filter, start, end,
                                                  rrm, true);

      // Filter out transparent events
      for (final EventInfo ei : evs) {
        final BwEvent ev = ei.getEvent();

        if ((exceptUid != null) &&
            exceptUid.equals(ev.getUid())) {
          continue;
        }

        if (!c.getIgnoreTransparency() &&
            IcalDefs.transparencyTransparent.equals(ev.getPeruserTransparency(userHref))) {
          // Ignore this one.
          continue;
        }

        if (BwEvent.statusCancelled.equals(ev.getStatus())) {
          // Ignore this one.
          continue;
        }

        /* if it's a meeting and this principal is an attendee, drop declined
         * meetings or unanswered messages.
         */

        if (ev.getAttendeeSchedulingObject()) {
          att = ev.findAttendee(uri);

          if (att != null) {
            final int pstat = IcalDefs.checkPartstat(att.getPartstat());

            if (pstat == IcalDefs.partstatDeclined) {
              continue;
            }

            if (pstat == IcalDefs.partstatNeedsAction) {
              continue;
            }
          }
        }

        events.add(ei);
      }
    }

    try {
      final EventPeriods eventPeriods = new EventPeriods(start, end);

      for (final EventInfo ei: events) {
        final BwEvent ev = ei.getEvent();
        int type;

        if (ev.getEntityType() == IcalDefs.entityTypeEvent) {
          if (BwEvent.statusCancelled.equals(ev.getStatus())) {
            // Ignore this one.
            continue;
          }

          type = BwFreeBusyComponent.typeBusy;

          if (ev.getAttendeeSchedulingObject()) {
            att = ev.findAttendee(uri);

            if (att != null) {
              if (IcalDefs.checkPartstat(att.getPartstat()) == IcalDefs.partstatTentative) {
                type = BwFreeBusyComponent.typeBusyTentative;
              }
            }
          }

          if (BwEvent.statusTentative.equals(ev.getStatus())) {
            type = BwFreeBusyComponent.typeBusyTentative;
          } else if (BwEvent.statusUnavailable.equals(ev.getStatus())) {
            type = BwFreeBusyComponent.typeBusyUnavailable;
          }

          eventPeriods.addPeriod(ev.getDtstart(), ev.getDtend(), type);
        } else if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
          final Collection<BwFreeBusyComponent> fbcs = ev.getFreeBusyPeriods();

          for (final BwFreeBusyComponent fbc: fbcs) {
            type = fbc.getType();

            for (final Period p: fbc.getPeriods()) {
              eventPeriods.addPeriod(p.getStart(), p.getEnd(),
                                     type);
            }
          }
        }
      }

      /* iterate through the sorted periods combining them where they are
       adjacent or overlap */

      BwFreeBusyComponent fbc = eventPeriods.makeFreeBusyComponent(BwFreeBusyComponent.typeBusy);
      if (fbc != null) {
        fb.addFreeBusyPeriod(fbc);
      }

      fbc = eventPeriods.makeFreeBusyComponent(BwFreeBusyComponent.typeBusyUnavailable);
      if (fbc != null) {
        fb.addFreeBusyPeriod(fbc);
      }

      fbc = eventPeriods.makeFreeBusyComponent(BwFreeBusyComponent.typeBusyTentative);
      if (fbc != null) {
        fb.addFreeBusyPeriod(fbc);
      }
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new BedeworkException(t);
    }

    return fb;
  }

  @Override
  public Collection<BwCalendar> getFreebusySet() {
    final Collection<BwCalendar> fbset = new ArrayList<>();

    fbset.add(getSvc().getCalendarsHandler().getHome());

    return addToFreeBusySet(null, fbset);
  }

  private Collection<BwCalendar> addToFreeBusySet(final Collection<BwCalendar> cals,
                                                  final Collection<BwCalendar> fbset) {
    final Collection<BwCalendar> resCals;
    if (cals == null) {
      resCals = new ArrayList<>();
    } else {
      resCals = cals;
    }

    for (BwCalendar cal: fbset) {
      if (cal.getCalType() == BwCalendar.calTypeCalendarCollection) {
        if (cal.getAffectsFreeBusy()) {
          resCals.add(cal);
        }
      } else if (cal.getCalType() == BwCalendar.calTypeAlias) {
        if (!cal.getAffectsFreeBusy()) {
          continue;
        }

        cal = getCols().resolveAlias(cal, true, true);
        if (cal.getCalType() == BwCalendar.calTypeCalendarCollection) {
          resCals.add(cal);
        }
      } else {
        addToFreeBusySet(resCals, getSvc().getCalendarsHandler().getChildren(cal));
      }
    }

    return resCals;
  }

  @Override
  public FbResponses aggregateFreeBusy(final ScheduleResult sr,
                                       final BwDateTime start, final BwDateTime end,
                                       final BwDuration granularity) {
    final FbResponses resps = new FbResponses();

    if (start.getDateType() || end.getDateType()) {
      throw new BedeworkException(CalFacadeErrorCode.schedulingBadGranulatorDt);
    }

    resps.setResponses(new ArrayList<>());

    /* Combine the responses into one big collection */
    final FbGranulatedResponse allResponses = 
            new FbGranulatedResponse();

    allResponses.setStart(start);
    allResponses.setEnd(end);
    resps.setAggregatedResponse(allResponses);

    for (final ScheduleRecipientResult srr: sr.recipientResults.values()) {
      final FbGranulatedResponse fb = new FbGranulatedResponse();

      resps.getResponses().add(fb);

      fb.setRespCode(srr.getStatus());
      fb.setNoResponse(srr.freeBusy == null);
      fb.setRecipient(srr.recipient);

      if (!fb.okResponse()) {
        continue;
      }

      final var freeBusySi = srr.freeBusy.getSchedulingInfo();
      final var freeBusyPart = freeBusySi.getOnlyParticipant();

      if (freeBusyPart.isOk()) {
        fb.setAttendee(freeBusyPart.getEntity().getAttendee());
      }

      granulateFreeBusy(fb, srr.freeBusy, start, end, granularity);

      if (fb.getStart() == null) {
        continue;
      }

      boolean first = false;

      if (allResponses.eps.isEmpty()) {
        first = true;
      }

      // Merge resp into allResponses - they should have the same start/end
      final Iterator<EventPeriod> allIt = allResponses.eps.iterator();

      for (final EventPeriod respEp: fb.eps) {
        final EventPeriod allEp;
        if (first) {
          // Just set the event period from this first response.
          allResponses.eps.add(respEp);
          allEp = respEp;
          continue;
        }

        // Merge this response period into the corresponding aggregated response
        allEp = allIt.next();

        // Validity check
        if (!allEp.getStart().equals(respEp.getStart()) ||
            !allEp.getEnd().equals(respEp.getEnd())) {
          throw new BedeworkException(CalFacadeErrorCode.schedulingBadResponse);
        }

        if ((respEp.getType() == BwFreeBusyComponent.typeBusy) ||
            (respEp.getType() == BwFreeBusyComponent.typeBusyUnavailable)) {
          allEp.setNumBusy(allEp.getNumBusy() + 1);
        } else if (respEp.getType() == BwFreeBusyComponent.typeBusyTentative) {
          allEp.setNumTentative(allEp.getNumTentative() + 1);
        }

        allEp.setType(typeTable[allEp.getType()][respEp.getType()]);
      }
    }

    return resps;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.SchedulingI#granulateFreeBusy(org.bedework.calfacade.BwFreeBusy, org.bedework.calfacade.BwDateTime, org.bedework.calfacade.BwDateTime, org.bedework.calfacade.BwDuration)
   */
  @Override
  public FbGranulatedResponse granulateFreeBusy(final BwEvent fb,
                                                final BwDateTime start, final BwDateTime end,
                                                final BwDuration granularity) {
    final FbGranulatedResponse fbresp = new FbGranulatedResponse();

    granulateFreeBusy(fbresp, fb, start, end, granularity);

    return fbresp;
  }

  /* ====================================================================
   *                         Private methods
   * ==================================================================== */

  /*
  private void addFbcal(Collection<BwCalendar> cals,
                        BwCalendar cal) {
    if (cal.getCalType() == BwCalendar.calTypeCollection) {
      // Leaf
      cals.add(cal);
      return;
    }

    Collection<BwCalendar> chs = getSvc().getCalendarsHandler().getChildren(cal);
    for (BwCalendar ch: chs) {
      addFbcal(cals, ch);
    }
  }
  */

  private void granulateFreeBusy(final FbGranulatedResponse fbresp,
                                 final BwEvent fb,
                                 final BwDateTime start, final BwDateTime end,
                                 final BwDuration granularity) {
    final DateTime startDt;
    final DateTime endDt;
    try {
      startDt = new DateTime(start.getDate());
      endDt = new DateTime(end.getDate());
    } catch (final ParseException pe) {
      throw new BedeworkException(pe);
    }

    if (fb.getDtstart().after(start)) {
      // XXX Should warn - or fill in with tentative?
      //warn("Response start after requested start");
    }

    if (fb.getDtend().before(end)) {
      // XXX Should warn - or fill in with tentative?
      //warn("Response end before requested end");
    }

    fbresp.setStart(start);
    fbresp.setEnd(end);

    final Collection<EventPeriod> periods = new ArrayList<>();

    if (fb.getFreeBusyPeriods() != null) {
      for (final BwFreeBusyComponent fbcomp: fb.getFreeBusyPeriods()) {
        for (final Period p: fbcomp.getPeriods()) {
          DateTime pstart = p.getStart();
          DateTime pend = p.getEnd();
          if (!pend.isUtc()) {
            pend.setUtc(true);
          }

          /* Adjust for servers sending times outside requested range */
          if (pend.after(endDt)) {
            pend = endDt;
          }

          if (pstart.before(startDt)) {
            pstart = startDt;
          }

          if (!pend.after(pstart)) {
            continue;
          }
          periods.add(new EventPeriod(pstart, pend, fbcomp.getType()));
        }
      }
    }

    final var gpp = new GetPeriodsPars();

    gpp.periods = periods;
    gpp.startDt = start;
    gpp.dur = granularity;

    final Collection<EventPeriod> respeps = new ArrayList<>();
    fbresp.eps = respeps;

    int limit = 10000; // XXX do this better

    /* endDt is null first time through, then represents end of last
     * segment.
     */
    while ((gpp.endDt == null) || (gpp.endDt.before(end))) {
      //if (debug()) {
      //  debug("gpp.startDt=" + gpp.startDt + " end=" + end);
      //}
      if (limit < 0) {
        throw new BedeworkException("org.bedework.svci.limit.exceeded");
      }
      limit--;

      final Collection<?> periodEvents = Granulator.getPeriodsEvents(gpp);

      /* Some events fall in the period. Add an entry.
       * We eliminated cancelled events earler. Now we should set the
       * free/busy type based on the events status.
       */

      final DateTime psdt;
      final DateTime pedt;
      try {
        psdt = new DateTime(gpp.startDt.getDtval());
        pedt = new DateTime(gpp.endDt.getDtval());
      } catch (final ParseException pe) {
        throw new BedeworkException(pe);
      }

      psdt.setUtc(true);
      pedt.setUtc(true);


      final EventPeriod ep = new EventPeriod(psdt, pedt, 0);
      setFreeBusyType(ep, periodEvents);
      respeps.add(ep);
    }
  }

  private void setFreeBusyType(final EventPeriod ep,
                               final Collection<?> periodEvents) {
    int fbtype = BwFreeBusyComponent.typeFree;
    int busy = 0;
    int tentative = 0;

//    Iterator it = periodEvents.iterator();
//    while (it.hasNext()) {
    for (final Object o: periodEvents) {
//      int type = ((EventPeriod)it.next()).getType();
      final int type = ((EventPeriod)o).getType();

      if (type == BwFreeBusyComponent.typeBusy) {
        fbtype = BwFreeBusyComponent.typeBusy;
        busy++;
      }

      if (type == BwFreeBusyComponent.typeBusyUnavailable) {
        fbtype = BwFreeBusyComponent.typeBusy;
        busy++;
      }

      if (type == BwFreeBusyComponent.typeBusyTentative) {
        fbtype = BwFreeBusyComponent.typeBusyTentative;
        tentative++;
      }
    }

    ep.setNumBusy(busy);
    ep.setNumTentative(tentative);
    ep.setType(fbtype);
  }
}
