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
package org.bedework.inoutsched;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.RecurringRetrievalMode;
import org.bedework.calfacade.RecurringRetrievalMode.Rmode;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.AbstractScheduler;
import org.bedework.calsvci.CalSvcI;
import org.bedework.icalendar.IcalTranslator;
import org.bedework.sysevents.events.EntityQueuedEvent;
import org.bedework.sysevents.events.SysEvent;

import edu.rpi.sss.util.Util;

import net.fortuna.ical4j.model.Calendar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Handles processing of the outbox.
 *
 * <p>A single meeting request could result in a number of outgoing requsts to
 * the same recipient, by delaying we can group these together into a single
 * update.
 *
 * <p>We assume that delays are introduced elsewhere - probably in message queues.
 *
 * <p>At the moment we are only handling outbound email. What we should be doing
 * is just placing a copy of a scheduling message in the users outbox and have
 * this process ship internal messages straight into the inbox.
 *
 * @author Mike Douglass
 */
public class OutScheduler extends AbstractScheduler {
  /**
   */
  public OutScheduler() {
    super();
  }

  /* (non-Javadoc)
   * @see org.bedework.inoutsched.ScheduleMesssageHandler#processMessage(org.bedework.sysevents.events.SysEvent)
   */
  public ProcessMessageResult processMessage(final SysEvent msg) {
    if (msg instanceof EntityQueuedEvent) {
      return processEntityQueuedEvent((EntityQueuedEvent)msg);
    }

    // Ignore it
    return ProcessMessageResult.PROCESSED;
  }

  private ProcessMessageResult processEntityQueuedEvent(final EntityQueuedEvent msg) {
    CalSvcI svci = null;

    try {
      if (debug) {
        trace("autoSchedule outbox entry for for principal " +
              msg.getOwnerHref());
      }

      svci = getSvci(msg.getOwnerHref());

      return processOutBox();
    } catch (CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
      }

      return ProcessMessageResult.STALE_STATE;
    } catch (Throwable t) {
      rollback(svci);
      error(t);
    } finally {
      try {
        closeSvci(svci);
      } catch (Throwable t) {}
    }

    return ProcessMessageResult.FAILED;
  }

  /* Process pending messages in outbox.
   *
   */
  private ProcessMessageResult processOutBox() throws CalFacadeException {
    IcalTranslator trans = new IcalTranslator(getSvc().getIcalCallback());

    Collection<EventInfo> eis = getOutboxEvents();
    if (eis == null) {
      return ProcessMessageResult.NO_ACTION;
    }

    /* First go through the events and add them to a table indexed by the uid.
     * This allows us to delete multiple updates.
     */

    class DedupKey implements Comparable<DedupKey> {
      private String uid;
      private String rid;

      DedupKey(final String uid, final String rid) {
        this.uid = uid;
        this.rid = rid;
      }

      public int compareTo(final DedupKey that) {
        int res = uid.compareTo(that.uid);
        if (res != 0) {
          return res;
        }

        if ((rid == null) && (that.rid == null)) {
          return 0;
        }

        if ((rid != null) && (that.rid != null)) {
          return rid.compareTo(that.rid);
        }

        if (rid != null) {
          return 1;
        }

        return -1;
      }

      @Override
      public int hashCode() {
        int h = uid.hashCode();

        if (rid != null) {
          h *= rid.hashCode();
        }

        return  h;
      }

      @Override
      public boolean equals(final Object o) {
        return compareTo((DedupKey)o) == 0;
      }
    }

    Map<DedupKey, EventInfo> deduped = new HashMap<DedupKey, EventInfo>();
    int discarded = 0;

    for (EventInfo ei: eis) {
      BwEvent ev = ei.getEvent();
      DedupKey evKey = new DedupKey(ev.getUid(), ev.getRecurrenceId());
      EventInfo mapei = deduped.get(evKey);

      if (mapei == null) {
        deduped.put(evKey, ei);
        continue;
      }

      // Decide which to discard
      discarded++;
      BwEvent mapev = mapei.getEvent();

      if (mapev.getSequence() > ev.getSequence()) {
        // Ignore current

        continue;
      }

      if (mapev.getSequence() < ev.getSequence()) {
        // Replace

        deduped.put(evKey, ei);
        continue;
      }

      // sequence is equal -- try for dtstamp.

      int cmp = mapev.getDtstamp().compareTo(ev.getDtstamp());
      if (cmp >= 0) {
        // Ignore current

        continue;
      }

      deduped.put(evKey, ei);
    }

    if (debug) {
      trace("Outbox process discarded " + discarded);
    }

    boolean allOk = true;

    for (EventInfo ei: deduped.values()) {
      BwEvent ev = ei.getEvent();
      Calendar cal = trans.toIcal(ei, ev.getScheduleMethod());

      Collection<String> recipients = new ArrayList<String>();
      for (String r: ev.getRecipients()) {
        if (r.toLowerCase().startsWith("mailto:")) {
          recipients.add(r.substring(7));
        } else {
          recipients.add(r);
        }
      }

      String orig = ev.getOriginator();
      if (orig.toLowerCase().startsWith("mailto:")) {
        orig = orig.substring(7);
      }

      try {
        if (getSvc().getMailer().mailEntity(cal, orig, recipients,
                               ev.getSummary())) {
          /* Save sent messages somewhere - keep in outbox?
            ev.setScheduleState(BwEvent.scheduleStateExternalDone);
            updateEvent(ev, ei.getOverrideProxies(), null);
           */
          getSvc().getEventsHandler().delete(ei, false);
        }
      } catch (CalFacadeException cfe) {
        // Should count the exceptions and discard after a number of retries.
        error(cfe);
        allOk = false;
      }

    }

    if (allOk) {
      return ProcessMessageResult.PROCESSED;
    }

    return ProcessMessageResult.FAILED;
  }

  private Collection<EventInfo> getOutboxEvents() throws CalFacadeException {
    BwCalendar outbox = getSvc().getCalendarsHandler().getSpecial(BwCalendar.calTypeOutbox, false);
    if (outbox == null) {
      return null;
    }

    RecurringRetrievalMode rrm =
      new RecurringRetrievalMode(Rmode.overrides);
    Collection<EventInfo> eis = getSvc().getEventsHandler().getEvents(outbox, null,
                                                                null, null,
                                                                null, // retrieveList
                                                                rrm);
    if (Util.isEmpty(eis)) {
      if (debug) {
        trace("autoSchedule: no outbox events for " +
              getSvc().getPrincipal().getPrincipalRef());
      }
      return null;
    }

    return eis;
  }
}
