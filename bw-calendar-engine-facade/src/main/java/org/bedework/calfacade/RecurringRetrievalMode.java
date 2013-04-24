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

import java.io.Serializable;

/** How recurring events should be retrieved.
 *
 * @author douglm
 */
public class RecurringRetrievalMode implements Serializable {
  /**
   * Values which define how to retrieve recurring events. We have the
   * following choices (derived from caldav)
   */

  // DORECUR instancesOnlyWithTz needs to be dealt with correctly.
  /** return any instances that fall in the range and do not convert to UTC.
   *  Do not chain them together as a single master and instances but return
   *  each as a separate event.    (non-CalDAV)
   * /
  public final static int instancesOnlyWithTz = 0;*/

  public static enum Rmode {
    /** return all instances within the time range as
     *  individual events.
     *
     *  <p>For CalDAV, convert all times to UTC. No timezone information
     *  is returned.
     *
     *  <p>We use this a lot in bedework to return all events within a given time
     *  period. We display the times with all timezone information however.
     */
    expanded,

    /** return the master event and overrides only (start and end
     *                may be specified)
     */
    overrides,

    /** return the master if any instances fall in the range (non-CalDAV)
     * Also used for getting single event or instance.
     */
    masterOnly,

    /** return the single entity or instance only.
     */
    entityOnly}

  /** One of the above
   */
  public Rmode mode = Rmode.expanded;

  /** Limit expansion and recurrences.
   */
  public BwDateTime start;

  /** Limit expansion and recurrences.
   */
  public BwDateTime end;

  /** Default constructor - mode = Rmode.expanded, no date limits.
   */
  public RecurringRetrievalMode() {
  }

  /** Constructor
   *
   * @param mode
   */
  public RecurringRetrievalMode(final Rmode mode) {
    this.mode = mode;
  }

  /** Constructor
   *
   * @param mode
   * @param start
   * @param end
   */
  public RecurringRetrievalMode(final Rmode mode,
                                final BwDateTime start,
                                final BwDateTime end) {
    this.mode = mode;
    this.start = start;
    this.end = end;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("RecurringRetrievalMode{");

    sb.append("mode=");
    sb.append(mode);

    sb.append(", start=");
    sb.append(start);

    sb.append(", end=");
    sb.append(end);

    sb.append("}");
    return sb.toString();
  }
}
