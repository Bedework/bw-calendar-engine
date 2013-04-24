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

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.MesssageHandler;
import org.bedework.calsvc.MesssageHandler.ProcessMessageResult;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;

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
  private MesssageCounts counts;

  private int retryLimit = 10;

  boolean debug;

  private MesssageHandler handler;

  /** Constructor to run
   *
   * @param counts
   * @param retryLimit
   * @throws CalFacadeException
   */
  ChgProc(final MesssageCounts counts,
          final int retryLimit) throws CalFacadeException {
    this.retryLimit = retryLimit;
    this.counts = counts;

    debug = getLogger().isDebugEnabled();
  }

  /** Set the number of times we retry a message when we get stale state
   * exceptions.
   *
   * @param val
   */
  public void setRetryLimit(final int val) {
    retryLimit = val;
  }

  /**
   * @return current limit
   */
  public int getRetryLimit() {
    return retryLimit;
  }

  /**
   * @return counts for this processor
   */
  public MesssageCounts getCounts() {
    return counts;
  }

  @Override
  public void run() {
    try {
      open(changesQueueName);
      handler = new Notifier();

      process(false);
    } catch (Throwable t) {
      error("Notification processor terminating with exception:");
      error(t);
    }
  }

  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      if (debug) {
        trace("Received message with syscode " + ev.getSysCode());
      }

      if (getLogger().isInfoEnabled()) {
        info(ev.toString());
      }

      counts.total++;

      for (int ct = 1; ct <= retryLimit; ct++) {
        if ((ct - 1) > counts.maxRetries) {
          counts.maxRetries = ct - 1;
        }

        ProcessMessageResult pmr = handler.processMessage(ev);

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
    } catch (Throwable t) {
      error("Error processing message " + ev);
      error(t);
    }
  }
}
