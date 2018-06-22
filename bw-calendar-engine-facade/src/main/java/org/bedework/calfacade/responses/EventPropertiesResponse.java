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
package org.bedework.calfacade.responses;

import org.bedework.util.misc.ToString;

import java.util.List;

/** Container for fetching event properties.
 *
 * @author Mike Douglass douglm
 */
public class EventPropertiesResponse extends Response {
  private List<String> preferred;

  /**
   * @param val list of hrefs
   */
  public void setPreferred(final List<String> val) {
    preferred = val;
  }

  /**
   * @return list of hrefs
   */
  public List<String> getPreferred() {
    return preferred;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("preferred", getPreferred());
  }
}
