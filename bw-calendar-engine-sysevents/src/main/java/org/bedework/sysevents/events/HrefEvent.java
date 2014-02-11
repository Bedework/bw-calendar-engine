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

import org.bedework.util.misc.ToString;

/** Signal an event concerning an entity
 * @author douglm
 *
 */
public class HrefEvent extends SysEvent {
  private static final long serialVersionUID = 1L;

  private String href;

  /**
   * @param code
   * @param href
   */
  public HrefEvent(final SysCode code,
                   final String href) {
    super(code);

    this.href = href;
  }

  /** Get the href
   *
   * @return String   href
   */
  public String getHref() {
    return href;
  }

  /** Add our stuff to the ToString object
  *
  * @param ts    ToString for result
  */
 @Override
 public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("href", getHref());
  }
}
