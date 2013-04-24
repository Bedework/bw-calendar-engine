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


/** Counts for incoming or outbound message processing.
 *
 * @author Mike Douglass
 *
 */
public class ScheduleMesssageCounts {
  /** some identification */
  public String name;

  /** total messages handled */
  public long total;

  /** no action taken - deleted from inbox already? */
  public long noaction;

  /** total complete failures - retry will not succeed (failed response from processor) */
  public long failedNoRetries;

  /** total failures - retry might succeed (failed response from processor) */
  public long failed;

  /** total stale-state exceptions */
  public long staleState;

  /** total complete failures after retries */
  public long failedRetries;

  /** total processed succesfully (possibly after retries) */
  public long processed;

  /** total number of retried messages */
  public long retried;

  /** total number of retries */
  public long retries;

  /** max retries */
  public long maxRetries;

  /**
   * @param name
   */
  public ScheduleMesssageCounts(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Counts {");

    sb.append(name);
    sb.append("\n");

    ctLine(sb, "total", total);
    ctLine(sb, "noaction", noaction);
    ctLine(sb, "failed-unretryable", failedNoRetries);
    ctLine(sb, "failed", failed);
    ctLine(sb, "failedRetries", failedRetries);
    ctLine(sb, "stale-state", staleState);
    ctLine(sb, "processed", processed);
    ctLine(sb, "retried", retried);
    ctLine(sb, "retries", retries);
    ctLine(sb, "maxRetries", maxRetries);

    sb.append("}\n");

    return sb.toString();
  }

  private static final String blanks = "               ";
  private static final int blen = blanks.length();

  private void ctLine(final StringBuilder sb,
                      final String name,
                      final long val) {
    int len = blen - name.length();

    if (len > 0) {
      sb.append(blanks.substring(0, blen));
    }

    sb.append(name);
    sb.append(": ");
    sb.append(val);
    sb.append("\n");
  }
}
