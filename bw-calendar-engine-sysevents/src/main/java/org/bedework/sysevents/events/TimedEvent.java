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

/** An event for some timed activity. The label identifies the event
 *
 * @author Mike Douglass
 */
public class TimedEvent extends SysEvent implements MillisecsEvent {
  private static final long serialVersionUID = 1L;

  private String label;

  private long millis;

  /** Constructor
   *
   * @param code
   * @param label
   * @param millis - time for stats - e.g. time to process login
   */
  public TimedEvent(final SysCode code,
                    final String label,
                    final long millis) {
    super(code);

    this.label = label;
    this.millis = millis;
  }

  /**
   *
   * @return label
   */
  public String getLabel() {
    return label;
  }

  @Override
  public long getMillis() {
    return millis;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("TimedEvent{");

    super.toStringSegment(sb);

    sb.append(",\n principalHref=");
    sb.append(getLabel());

    sb.append("}");

    return sb.toString();
  }
}
