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

/** Entity fetch event.
 * @author douglm
 *
 */
public class EntityFetchEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  int count;

  /**
   * @param code
   * @param count - number of events
   */
  public EntityFetchEvent(final SysCode code,
                      final int count) {
    super(code);

    this.count = count;
  }

  /** Get the count
   *
   * @return int count
   */
  public int getCount() {
    return count;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    sb.append(", count=");
    sb.append(getCount());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("EntityFetchEvent{");

    toStringSegment(sb);

    sb.append("}");

    return sb.toString();
  }
}
