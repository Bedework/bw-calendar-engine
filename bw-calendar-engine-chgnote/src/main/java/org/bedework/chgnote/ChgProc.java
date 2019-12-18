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
package org.bedework.chgnote;

import org.bedework.calsvc.MesssageHandler;
import org.bedework.calsvc.MesssageHandler.ProcessMessageResult;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;

import static java.lang.String.format;

/** Listener class which handles change system events sent via JMS.
 *
 * <p>JMS messages are delivered to the appropriate service so we don't check
 * them here.
 *
 * <p>We look at an incoming change notification and determine if there are any
 * changes to be posted to users. If so we add a notification entry to their
 * notification collection.
 *
 * @author Mike Douglass
 */
public class ChgProc extends JmsSysEventListener implements Runnable {
  private final MesssageCounts counts;

  private int retryLimit = 10;

  private MesssageHandler handler;

  /** Constructor to run
   *
   * @param counts for stats
   * @param retryLimit how often we retry
   */
  ChgProc(final MesssageCounts counts,
          final int retryLimit) {
    this.retryLimit = retryLimit;
    this.counts = counts;
    enableAuditLogger();
  }

  /**
   * @return counts for this processor
   */
  public MesssageCounts getCounts() {
    return counts;
  }

  @Override
  public void run() {
    try (final JmsSysEventListener ignored =
                 open(changesQueueName,
                      CalSvcFactoryDefault.getPr())) {
      handler = new Notifier();

      process(false);
    } catch (final Throwable t) {
      error("Notification processor terminating with exception:");
      error(t);
    }
  }

  @Override
  public void action(final SysEvent ev) {
    if (ev == null) {
      return;
    }

    try {
      if (debug()) {
        debug("Received message with syscode " + ev.getSysCode());
      }

      if (isAuditLoggerEnabled()) {
        audit(ev.toString());
      }

      counts.total++;

      for (int ct = 1; ct <= retryLimit; ct++) {
        if ((ct - 1) > counts.maxRetries) {
          counts.maxRetries = ct - 1;
        }

        final long before = System.currentTimeMillis();

        final ProcessMessageResult pmr = handler.processMessage(ev);
        if (trace()) {
          trace(format("handler.processMessage; result %s, took %s",
                       pmr,
                       System.currentTimeMillis() - before));
        }

        if (pmr == ProcessMessageResult.PROCESSED) {
          counts.processed++;
          return;
        }

        if (pmr == ProcessMessageResult.IGNORED) {
          counts.ignored++;
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

        /* Failed after retries */
        counts.failedRetries++;
      }
    } catch (final Throwable t) {
      error("Error processing message " + ev);
      error(t);
    }
  }
}
