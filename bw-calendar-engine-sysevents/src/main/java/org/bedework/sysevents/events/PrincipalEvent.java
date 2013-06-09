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
package org.bedework.sysevents.events;

import edu.rpi.sss.util.ToString;

/** An event caused by the change in state of some principal, e.g new user,
 * login, logout etc.
 *
 * @author Mike Douglass
 */
public class PrincipalEvent extends SysEvent implements MillisecsEvent {
  private static final long serialVersionUID = 1L;

  private String principalHref;

  private long millis;

  /** Constructor
   *
   * @param code
   * @param principalHref
   * @param millis - time for stats - e.g. time to process login
   */
  public PrincipalEvent(final SysCode code,
                        final String principalHref,
                        final long millis) {
    super(code);

    this.principalHref = principalHref;
    this.millis = millis;
  }

  /**
   *
   * @return principal href
   */
  public String getPrincipalHref() {
    return principalHref;
  }

  @Override
  public long getMillis() {
    return millis;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("principalHref", getPrincipalHref());
  }
}
