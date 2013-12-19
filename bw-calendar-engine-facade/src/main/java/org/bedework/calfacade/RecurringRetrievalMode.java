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

import org.bedework.util.misc.ToString;

import java.io.Serializable;

/** How recurring events should be retrieved.
 *
 * @author douglm
 */
public class RecurringRetrievalMode implements Serializable {
  /**
   * Values which define how to retrieve events. For recurring events
   * we have the following choices (derived from caldav)
   */

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

  public static final RecurringRetrievalMode expanded =
          new RecurringRetrievalMode();

  public static final RecurringRetrievalMode overrides =
          new RecurringRetrievalMode(Rmode.overrides);

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
    ToString ts = new ToString(this);

    ts.append("mode", mode);
    ts.append("start", start);
    ts.append("end", end);

    return ts.toString();
  }
}
