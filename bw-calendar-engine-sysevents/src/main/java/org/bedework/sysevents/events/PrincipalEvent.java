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

/** An event caused by the change in state of some principal, e.g new user,
 * login, logout etc.
 *
 * @author Mike Douglass
 */
public class PrincipalEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  private String principalHref;

  /** Constructor
   *
   * @param code
   * @param principalHref
   */
  public PrincipalEvent(final SysCode code,
                        final String principalHref) {
    super(code);

    this.principalHref = principalHref;
  }

  /**
   *
   * @return principal href
   */
  public String getPrincipalHref() {
    return principalHref;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("PrincipalEvent{");

    super.toStringSegment(sb);

    sb.append(",\n principalHref=");
    sb.append(getPrincipalHref());

    sb.append("}");

    return sb.toString();
  }
}
