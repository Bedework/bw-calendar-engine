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
    try (final CalSvcI svci = getSvci(msg.getOwnerHref())) {
      if (debug) {
        trace("ScheduleUpdateEvent for principal " +
              msg.getOwnerHref());
      }

      final EventInfo ei =
              svci.getEventsHandler().get(getParentPath(msg.getHref()),
                                          getName(msg.getHref()));
      if (ei == null) {
        // Event deleted?.
        if (debug) {
          trace("InSchedule event deleted?");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      final BwEvent ev = ei.getEvent();

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

      final SchedProcResult pr = proc.process(ei);

      if (debug) {
        trace("InSchedule " + pr.sr);
      }

      return ProcessMessageResult.PROCESSED;
    } catch (final CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
      }
      rollback(getSvc());

      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    }

    return ProcessMessageResult.FAILED;
  }

  private ProcessMessageResult processEntityQueuedEvent(final EntityQueuedEvent msg) {
    /* These are events that are placed in the inbox.
     */

    EventInfo ei = null;

    try (final CalSvcI svci = getSvci(msg.getOwnerHref())) {
      if (debug) {
        trace("InSchedule inbox entry for principal " +
              msg.getOwnerHref());
      }

      ei = getInboxEvent(svci, msg.getName());

      if (ei == null) {
        // Event deleted from inbox.
        if (debug) {
          trace("InSchedule event deleted from inbox");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      final BwEvent ev = ei.getEvent();
      final int method = ev.getScheduleMethod();

      if (debug) {
        trace("InSchedule event for " + msg.getOwnerHref() + " " +
              msg.getName() +
              " with method " + ScheduleMethods.methods[method] + "\n" +
              ev);

        if (ev.getSuppressed()){
          for (final EventInfo oei: ei.getOverrides()) {
            trace("Override: " + oei.getEvent());
          }
        }
      }

      InProcessor proc = null;

      switch (method) {
        case Icalendar.methodTypeCancel:
          proc = new InCancel(svci);
          break;

        case Icalendar.methodTypeRequest:
        case Icalendar.methodTypePollStatus:
          proc = new InRequest(svci);
          break;

        case Icalendar.methodTypeReply:
          proc = new InReply(svci);
          break;

        case Icalendar.methodTypeRefresh:
          proc = new InRefresh(svci);
          break;

        default:
          warn("InSchedule: unhandled method for " + ev.getOwnerHref() +
               " " + method);
      }

      if (proc == null) {
        deleteEvent(ei, false, false);
        return ProcessMessageResult.PROCESSED;
      }

      final ProcessResult pr = proc.process(ei);

      if (debug) {
        trace("InSchedule " + pr.sr);
      }

      if (!pr.noInboxChange) {
        proc.pendingToInbox(ei, ev.getOwnerHref(),
                            pr.attendeeAccepting,
                            pr.removeInboxEntry);
      }

      deleteEvent(ei, false, false);
      return ProcessMessageResult.PROCESSED;
    } catch (final CalFacadeForbidden cff) {
      if (debug) {
        trace("Forbidden exception" + cff);
      }

      if (ei != null) {
        try {
          deleteEvent(ei, false, false);
        } catch (final Throwable ignored) {
        }
      }
      return ProcessMessageResult.FAILED_NORETRIES;
    } catch (final CalFacadeStaleStateException csse) {
      if (debug) {
        trace("Stale state exception");
      }

      rollback(getSvc());
      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    }

    return ProcessMessageResult.FAILED;
  }

  private EventInfo getInboxEvent(final CalSvcI svci,
                                  final String eventName) throws CalFacadeException {
    final BwCalendar inbox = svci.getCalendarsHandler().
            getSpecial(BwCalendar.calTypePendingInbox, false);
    if (inbox == null) {
      return null;
    }

    final EventInfo ei = svci.getEventsHandler()
                             .get(inbox.getPath(),
                                  eventName);
    if (ei == null) {
      if (debug) {
        trace("autoSchedule: no event with name " + eventName);
      }
      return null;
    }

    if (debug) {
      final boolean recur = ei.getEvent().getRecurring();
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
