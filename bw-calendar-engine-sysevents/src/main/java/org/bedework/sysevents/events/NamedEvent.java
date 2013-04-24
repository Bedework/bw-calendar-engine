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

/** System event with a name
 * @author douglm
 *
 */
public class NamedEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  private String name;

  /**
   * @param code
   * @param name
   */
  public NamedEvent(final SysCode code,
                    final String name) {
    super(code);

    this.name = name;
  }

  /** Get the name
   *
   * @return String   name
   */
  public String getName() {
    return name;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    sb.append(", name=");
    sb.append(getName());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("NamedEvent{");

    super.toStringSegment(sb);

    sb.append("}");

    return sb.toString();
  }
}
