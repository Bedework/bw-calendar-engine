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
package org.bedework.calfacade;

import org.bedework.util.calendar.ScheduleStates;
import org.bedework.util.misc.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Result for a call to a scheduling method
 *
 * @author douglm
 */
public class ScheduleResult {
  /** Result for a single recipient.
   */
  public static class ScheduleRecipientResult
       implements ScheduleStates, Comparable<ScheduleRecipientResult> {
    /** */
    public String recipient;

    private int status = scheduleUnprocessed;

    /** Set if this is the result of a freebusy request. */
    public BwEvent freeBusy;

    @Override
    public int compareTo(final ScheduleRecipientResult that) {
      return recipient.compareTo(that.recipient);
    }

    @Override
    public String toString() {
      final ToString ts = new ToString(this);

      ts.append("recipient", recipient);
      ts.append("status", status);

      return ts.toString();
    }

    /**
     * @param val - the value
     */
    public void setStatus(final int val) {
      status = val;
    }

    /**
     * @return scheduling status
     */
    public int getStatus() {
      return status;
    }
  }

  /** If non-null error from CalFacadeException
   */
  public String errorCode;

  /** To go with the error code
   */
  public Object extraInfo;

  /** True if an event had a previously seen sequence and dtstamp.
   * recipient results will be set but no action taken.
   */
  public boolean ignored;

  /** Flagged by incremented sequence or dtstamp in event. */
  public boolean reschedule;

  /** Flagged by same sequence and dtstamp in event. */
  public boolean update;

  /** Set of ScheduleRecipientResult */
  public Map<String, ScheduleRecipientResult> recipientResults =
    new HashMap<>();

  /** Recipients external to the system. */
  public Set<String> externalRcs = new TreeSet<>();

  public void addRecipientResult(final ScheduleRecipientResult srr) {
    recipientResults.put(srr.recipient, srr);
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("errorCode", errorCode);

    ts.append("ignored", ignored);
    ts.append("reschedule", reschedule);
    ts.append("ignored", ignored);

    if ((recipientResults != null) && !recipientResults.isEmpty()) {
      for (final ScheduleRecipientResult srr: recipientResults.values()) {
        ts.append(srr);
      }
    }

    return ts.toString();
  }
}
