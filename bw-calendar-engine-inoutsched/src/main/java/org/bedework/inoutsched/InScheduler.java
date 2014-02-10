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
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeForbidden;
import org.bedework.calfacade.exc.CalFacadeStaleStateException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.AbstractScheduler;
import org.bedework.calsvci.CalSvcI;
import org.bedework.icalendar.Icalendar;
import org.bedework.inoutsched.processors.InCancel;
import org.bedework.inoutsched.processors.InProcessor;
import org.bedework.inoutsched.processors.InProcessor.ProcessResult;
import org.bedework.inoutsched.processors.InRefresh;
import org.bedework.inoutsched.processors.InReply;
import org.bedework.inoutsched.processors.InRequest;
import org.bedework.inoutsched.processors.SchedAttendeeUpdate;
import org.bedework.inoutsched.processors.SchedProcessor;
import org.bedework.inoutsched.processors.SchedProcessor.SchedProcResult;
import org.bedework.sysevents.events.EntityQueuedEvent;
import org.bedework.sysevents.events.ScheduleUpdateEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.misc.Util;

import java.util.Collection;

/** Handles a queue of scheduling requests. We need to delay
 * processing until after the initiating request is processed. In addition,
 * processing of the message can cause a significant amount of traffic as each
 * message can itself generate more messages.
 *
 * @author Mike Douglass
 */
public class InScheduler extends AbstractScheduler {
  /**
   */
  public InScheduler() {
    super();
  }

  /* (non-Javadoc)
   * @see org.bedework.inoutsched.ScheduleMesssageHandler#processMessage(org.bedework.sysevents.events.SysEvent)
   */
  @Override
  public ProcessMessageResult processMessage(final SysEvent msg) {
    if (msg instanceof EntityQueuedEvent) {
      return processEntityQueuedEvent((EntityQueuedEvent)msg);
    }

    if (msg instanceof ScheduleUpdateEvent) {
      return processScheduleUpdateEvent((ScheduleUpdateEvent)msg);
    }

    // Ignore it
    return ProcessMessageResult.PROCESSED;
  }

  private ProcessMessageResult processScheduleUpdateEvent(final ScheduleUpdateEvent msg) {
    CalSvcI svci = null;

    try {
      if (debug) {
        trace("ScheduleUpdateEvent for principal " +
              msg.getOwnerHref());
      }

      svci = getSvci(msg.getOwnerHref());

      Collection<EventInfo> eis = svci.getEventsHandler().get(getParentPath(msg.getHref()),
                                                              getName(msg.getHref()),
                                                              msg.getRecurrenceId(),
                                                              RecurringRetrievalMode.expanded);
      if (Util.isEmpty(eis)) {
        // Event deleted?.
        if (debug) {
          trace("InSchedule event deleted?");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      if (eis.size() != 1) {
        // This should not happen.
        if (debug) {
          trace("InSchedule expeected only 1 event");
        }

        return ProcessMessageResult.FAILED;
      }

      EventInfo ei = eis.iterator().next();
      BwEvent ev = ei.getEvent();

      SchedProcessor proc = null;

      switch (msg.getChange()) {
        case attendeeChange: {
          proc = new SchedAttendeeUpdate(svci);
          break;
        }

        default:
          warn("InSchedule: unhandled change type for " + ev.getOwnerHref() +
               " " + msg.getChange());
      }

      if (proc == null) {
        return ProcessMessageResult.PROCESSED;
      }

      SchedProcResult pr = proc.process(ei);

      if (debug) {
        trace("InSchedule " + pr.sr);
      }

      return ProcessMessageResult.PROCESSED;
    } catch (CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
      }
      rollback(svci);

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

  private ProcessMessageResult processEntityQueuedEvent(final EntityQueuedEvent msg) {
    /* These are events that are placed in the inbox.
     */
    CalSvcI svci = null;

    try {
      if (debug) {
        trace("InSchedule inbox entry for principal " +
              msg.getOwnerHref());
      }

      svci = getSvci(msg.getOwnerHref());

      EventInfo ei = getInboxEvent(svci, msg.getName());

      if (ei == null) {
        // Event deleted from inbox.
        if (debug) {
          trace("InSchedule event deleted from inbox");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      BwEvent ev = ei.getEvent();
      int method = ev.getScheduleMethod();

      if (debug) {
        trace("InSchedule event for " + msg.getOwnerHref() + " " +
              msg.getName() +
              " with method " + ScheduleMethods.methods[method] + "\n" +
              ev);

        if (ev.getSuppressed()){
          for (EventInfo oei: ei.getOverrides()) {
            trace("Override: " + oei.getEvent());
          }
        }
      }

      InProcessor proc = null;

      switch (method) {
        case Icalendar.methodTypeCancel: {
          proc = new InCancel(svci);
          break;
        }

        case Icalendar.methodTypeRequest: {
          proc = new InRequest(svci);
          break;
        }

        case Icalendar.methodTypeReply: {
          proc = new InReply(svci);
          break;
        }

        case Icalendar.methodTypeRefresh: {
          proc = new InRefresh(svci);
          break;
        }

        default:
          warn("InSchedule: unhandled method for " + ev.getOwnerHref() +
               " " + method);
      }

      if (proc == null) {
        return ProcessMessageResult.PROCESSED;
      }

      ProcessResult pr = proc.process(ei);

      if (debug) {
        trace("InSchedule " + pr.sr);
      }

      if (!pr.noInboxChange) {
        proc.pendingToInbox(ei, ev.getOwnerHref(),
                            pr.attendeeAccepting,
                            pr.removeInboxEntry);
      }

      return ProcessMessageResult.PROCESSED;
    } catch (CalFacadeForbidden cff) {
      if (debug) {
        trace("Forbidden exception" + cff);
      }

      rollback(svci);
      return ProcessMessageResult.FAILED_NORETRIES;
    } catch (CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
      }

      rollback(svci);
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

  private EventInfo getInboxEvent(final CalSvcI svci,
                                  final String eventName) throws CalFacadeException {
    BwCalendar inbox = svci.getCalendarsHandler().
            getSpecial(BwCalendar.calTypePendingInbox, false);
    if (inbox == null) {
      return null;
    }

    EventInfo ei = svci.getEventsHandler().get(inbox.getPath(),
                                               eventName,
                                               RecurringRetrievalMode.overrides);
    if (ei == null) {
      if (debug) {
        trace("autoSchedule: no event with name " + eventName);
      }
      return null;
    }

    if (debug) {
      boolean recur = ei.getEvent().getRecurring();
      int numOverrides = 0;
      if (recur && (ei.getOverrides() != null)) {
        numOverrides = ei.getOverrides().size();
      }

      if (recur) {
        trace("autoSchedule: retrieved recurring event with name " + eventName +
              " and " + numOverrides + " overrides");
      } else {
        trace("autoSchedule: retrieved non-recurring event with name " + eventName);
      }
    }

    return ei;
  }
}
