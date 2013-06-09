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

/** A calendar (collection) change event. The fields define what changed together
 * with the syscode.<ul>
 * <li>colPath defines the collection</li>
 *
 * @author Mike Douglass
 */
public class CollectionChangeEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  private String colPath;

  /** Constructor
   *
   * @param code
   * @param colPath path for changed collection
   *
   * @deprecated - use CollectionUpdateEvent
   */
  @Deprecated
  public CollectionChangeEvent(final SysCode code,
                               final String colPath) {
    super(code);
    this.colPath = colPath;
  }

  /**
   * @return collection path
   */
  public String getColPath() {
    return colPath;
  }

  /** Add our stuff to the ToString object
   *
   * @param ts for result
   */
 @Override
 public void toStringSegment(final ToString ts) {
   super.toStringSegment(ts);

    ts.append("colPath", getColPath());
  }
}
