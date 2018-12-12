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

import org.bedework.calsvc.MesssageHandler;
import org.bedework.calsvc.MesssageHandler.ProcessMessageResult;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.EntityQueuedEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;

/** Listener class which handles scheduling system events sent via JMS.
 *
 * <p>There are two invocations running, one which handles inbound scheduling
 * messages and one which handles outbound.
 *
 * <p>JMS messages are delivered to the appropriate service so we don't check
 * them here.
 *
 * <p>Inbound messages handle messages that have been delivered to the inbox and
 * which need processing, in effect we act as a virtual client. If autorespond
 * is on we also send out the response.
 *
 * <p>Outbound messages processing handles messages left in the outbox. At the
 * moment we are only handling mail messages. We really should handle all
 * outbound processing - i.e. the sender should just copy the message to their
 * outbox and let the asynch process handle the rest.
 *
 * @author Mike Douglass
 */
public class InoutSched extends JmsSysEventListener implements Runnable {
  private final ScheduleMesssageCounts counts;

  private int retryLimit = 10;

  private final boolean in;

  /** Constructor to run
   *
   * @param counts for stats
   * @param retryLimit number retries on exception
   * @param in or out
   */
  InoutSched(final ScheduleMesssageCounts counts,
             final int retryLimit,
             final boolean in) {
    this.in = in;
    this.retryLimit = retryLimit;
    this.counts = counts;
  }

  /** Set the number of times we retry a message when we get stale state
   * exceptions.
   *
   * @param val retry limit
   */
  public void setRetryLimit(final int val) {
    retryLimit = val;
  }

  /**
   * @return current limit
   */
  @SuppressWarnings("unused")
  public int getRetryLimit() {
    return retryLimit;
  }

  private MesssageHandler handler;

  @Override
  public void run() {
    final String qname;

    if (in) {
      qname = schedulerInQueueName;
      handler = new InScheduler();
    } else {
      qname = schedulerOutQueueName;
      handler = new OutScheduler();
    }

    try (final JmsSysEventListener ignored =
                 open(qname,
                      CalSvcFactoryDefault.getPr())) {
      process(false);
    } catch (final Throwable t) {
      error("Scheduler(" + in + ") terminating with exception:");
      error(t);
    }
  }

  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      if (debug()) {
        debug("Received message" + ev);
      }
      if (ev instanceof EntityQueuedEvent) {
        counts.total++;

        final EntityQueuedEvent eqe = (EntityQueuedEvent)ev;

        for (int ct = 1; ct <= retryLimit; ct++) {
          if ((ct - 1) > counts.maxRetries) {
            counts.maxRetries = ct - 1;
          }

          final ProcessMessageResult pmr = handler.processMessage(eqe);

          if (pmr == ProcessMessageResult.PROCESSED) {
            counts.processed++;

            return;
          }

          if (pmr == ProcessMessageResult.NO_ACTION) {
            counts.noaction++;
            return;
          }

          if (pmr == ProcessMessageResult.STALE_STATE) {
            counts.staleState++;
            counts.retries++;

            if (ct == 1) {
              counts.retried++;
            }
          }

          if (pmr == ProcessMessageResult.FAILED_NORETRIES) {
            counts.failedNoRetries++;
            return;
          }

          if (pmr == ProcessMessageResult.FAILED) {
            counts.failed++;
            counts.retries++;

            if (ct == 1) {
              counts.retried++;
            }
          }
        }

        /* Failed after retries */
        counts.failedRetries++;
      }
    } catch (final Throwable t) {
      error("Error processing message " + ev);
      error(t);
    }
  }
}
