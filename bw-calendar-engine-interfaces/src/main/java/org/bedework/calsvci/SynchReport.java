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
package org.bedework.calsvci;

import java.util.Set;

/** Response to a synch report. If the report is truncated then returning the
 * token will result in another batch of data
 *
 */
public class SynchReport {
  /**
   */
  private Set<SynchReportItem> items;

  /** True if the report was truncated
   */
  private boolean truncated;

  /** Token for next time.
   */
  private String token;

  /**
   * @param items
   * @param token
   */
  public SynchReport(final Set<SynchReportItem> items,
                     final String token) {
    this.items = items;
    this.token = token;
  }

  /**
   *
   * @return The items list
   */
  public Set<SynchReportItem> getItems() {
    return items;
  }

  /**
   *
   * @return boolean true if truncated
   */
  public boolean getTruncated() {
    return truncated;
  }

  /**
   *
   * @return The token
   */
  public String getToken() {
    return token;
  }

  /**
   * @return number of items
   */
  public int size() {
    return items.size();
  }
}