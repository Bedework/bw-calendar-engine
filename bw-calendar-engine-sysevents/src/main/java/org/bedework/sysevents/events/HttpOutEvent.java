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

/** Outbound http event.
 * @author douglm
 *
 */
public class HttpOutEvent extends HttpEvent {
  private static final long serialVersionUID = 1L;

  long millis;

  /**
   * @param code
   * @param millis - time request took
   */
  public HttpOutEvent(final SysCode code,
                      final long millis) {
    super(code);

    this.millis = millis;
  }

  /** Get the millis
   *
   * @return long milliseconds
   */
  public long getMillis() {
    return millis;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    sb.append(", millis=");
    sb.append(getMillis());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("HttpOutEvent{");

    toStringSegment(sb);

    sb.append("}");

    return sb.toString();
  }
}
