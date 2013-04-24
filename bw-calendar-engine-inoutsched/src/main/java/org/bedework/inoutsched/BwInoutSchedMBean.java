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

import edu.rpi.cmt.jmx.ConfBaseMBean;

/**
 * @author douglm
 *
 */
public interface BwInoutSchedMBean extends ConfBaseMBean {
  /** Set the number of times we retry an incoming message when we get stale state
   * exceptions.
   *
   * @param val
   */
  public void setIncomingRetryLimit(int val);

  /**
   * @return current limit
   */
  public int getIncomingRetryLimit();

  /** Set the number of times we retry an outgoing message when we get stale state
   * exceptions.
   *
   * @param val
   */
  public void setOutgoingRetryLimit(int val);

  /**
   * @return current limit
   */
  public int getOutgoingRetryLimit();

  /** */
  public static class Counts {
    ScheduleMesssageCounts inCounts;

    ScheduleMesssageCounts outCounts;

    /**
     */
    public Counts() {
      inCounts = new ScheduleMesssageCounts("Inbox processing counts");
      outCounts = new ScheduleMesssageCounts("Outbox processing counts");
    }

    @Override
    public String toString() {
      return inCounts.toString() + outCounts.toString();
    }
  }

  /**
   * @return some counts
   */
  public Counts getCounts();

  /** Name apparently must be the same as the name attribute in the
   * jboss service definition
   *
   * @return Name
   */
  public String getName();

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  /** Lifecycle
   *
   */
  public void create();

  /** Lifecycle
   *
   */
  public void start();

  /** Lifecycle
   *
   */
  public void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  public boolean isStarted();

  /** Lifecycle
   *
   */
  public void destroy();
}
