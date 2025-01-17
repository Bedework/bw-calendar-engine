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

import org.bedework.base.exc.BedeworkStaleStateException;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.AbstractScheduler;
import org.bedework.calsvci.CalSvcI;
import org.bedework.convert.Icalendar;
import org.bedework.inoutsched.processors.InCancel;
import org.bedework.inoutsched.processors.InProcessor;
import org.bedework.inoutsched.processors.InProcessor.ProcessResult;
import org.bedework.inoutsched.processors.InRefresh;
import org.bedework.inoutsched.processors.InReply;
import org.bedework.inoutsched.processors.InRequest;
import org.bedework.inoutsched.processors.SchedAttendeeUpdate;
import org.bedework.inoutsched.processors.SchedProcessor;
import org.bedework.sysevents.events.EntityQueuedEvent;
import org.bedework.sysevents.events.ScheduleUpdateEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.ScheduleMethods;

import static org.bedework.sysevents.events.ScheduleUpdateEvent.ChangeType.attendeeChange;
import static org.bedework.util.misc.response.Response.Status.forbidden;

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
      if (debug()) {
        debug("ScheduleUpdateEvent for principal " +
              msg.getOwnerHref());
      }

      svci = getSvci(msg.getOwnerHref(), "in-scheduler-upd");

      final EventInfo ei =
              svci.getEventsHandler().get(getParentPath(msg.getHref()),
                                          getName(msg.getHref()));
      if (ei == null) {
        // Event deleted?.
        if (debug()) {
          debug("InSchedule event deleted?");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      final BwEvent ev = ei.getEvent();

      SchedProcessor proc = null;

      if (msg.getChange() == attendeeChange) {
        proc = new SchedAttendeeUpdate(svci);
      } else {
        warn("InSchedule: unhandled change type for " + ev
                .getOwnerHref() +
                     " " + msg.getChange());
      }

      if (proc == null) {
        return ProcessMessageResult.PROCESSED;
      }

      final var sr = proc.process(ei);

      if (debug()) {
        debug("InSchedule " + sr);
      }

      if (!sr.isOk()) {
        if (sr.getException() != null) {
          // Do better than this...
          throw sr.getException();
        }
      }

      return ProcessMessageResult.PROCESSED;
    } catch (final BedeworkStaleStateException ignored) {
      if (debug()) {
        debug("Stale state exception");
      }
      rollback(getSvc());

      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    } finally {
      try {
        closeSvci(svci);
      } catch (final Throwable ignored) {}
    }

    return ProcessMessageResult.FAILED;
  }

  private ProcessMessageResult processEntityQueuedEvent(final EntityQueuedEvent msg) {
    /* These are events that are placed in the inbox.
     */

    final EventInfo ei;
    CalSvcI svci = null;

    try {
      if (debug()) {
        debug("InSchedule inbox entry for principal " +
              msg.getOwnerHref());
      }
      svci = getSvci(msg.getOwnerHref(), "in-scheduler-qev");

      ei = getInboxEvent(svci, msg.getName());

      if (ei == null) {
        // Event deleted from inbox.
        if (debug()) {
          debug("InSchedule event deleted from inbox");
        }

        return ProcessMessageResult.NO_ACTION;
      }

      final BwEvent ev = ei.getEvent();
      final int method = ev.getScheduleMethod();

      if (debug()) {
        debug("InSchedule event for " + msg.getOwnerHref() + " " +
              msg.getName() +
              " with method " + ScheduleMethods.methods[method] + "\n" +
              ev);

        if (ev.getSuppressed()){
          for (final EventInfo oei: ei.getOverrides()) {
            debug("Override: " + oei.getEvent());
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
        if (svci.getEventsHandler()
                .delete(ei, false, false).isError()) {
          return ProcessMessageResult.FAILED;
        }
        return ProcessMessageResult.PROCESSED;
      }

      final ProcessResult pr = proc.process(ei);

      if (debug()) {
        debug("InSchedule: " + pr);
      }

      if (pr.getStatus() == forbidden) {
        svci.getEventsHandler()
            .delete(ei, false, false);
        return ProcessMessageResult.FAILED_NORETRIES;
      }

      if (!pr.noInboxChange) {
        if (proc.pendingToInbox(ei, ev.getOwnerHref(),
                                pr.attendeeAccepting,
                                pr.removeInboxEntry).isError()) {
          return ProcessMessageResult.FAILED;
        }
      }

      //deleteEvent(ei, false, false);
      return ProcessMessageResult.PROCESSED;
    } catch (final BedeworkStaleStateException ignored) {
      if (debug()) {
        debug("Stale state exception");
      }

      rollback(getSvc());
      return ProcessMessageResult.STALE_STATE;
    } catch (final Throwable t) {
      rollback(getSvc());
      error(t);
    } finally {
      try {
        closeSvci(svci);
      } catch (final BedeworkStaleStateException ignored) {
        if (debug()) {
          debug("Stale state exception");
        }
        return ProcessMessageResult.STALE_STATE;
      } catch (final Throwable ignored) {}
    }

    return ProcessMessageResult.FAILED;
  }

  private EventInfo getInboxEvent(final CalSvcI svci,
                                  final String eventName) {
    final BwCalendar inbox = svci.getCalendarsHandler().
            getSpecial(BwCalendar.calTypePendingInbox, false);
    if (inbox == null) {
      return null;
    }

    final EventInfo ei = svci.getEventsHandler()
                             .get(inbox.getPath(),
                                  eventName);
    if (ei == null) {
      if (debug()) {
        debug("autoSchedule: no event with name " + eventName);
      }
      return null;
    }

    if (debug()) {
      final boolean recur = ei.getEvent().getRecurring();
      int numOverrides = 0;
      if (recur && (ei.getOverrides() != null)) {
        numOverrides = ei.getOverrides().size();
      }

      if (recur) {
        debug("autoSchedule: retrieved recurring event with name " + eventName +
              " and " + numOverrides + " overrides");
      } else {
        debug("autoSchedule: retrieved non-recurring event with name " + eventName);
      }
    }

    return ei;
  }
}
