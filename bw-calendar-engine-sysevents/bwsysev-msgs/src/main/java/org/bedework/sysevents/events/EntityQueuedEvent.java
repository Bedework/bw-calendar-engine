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

import java.util.List;

/** Signal queuing of an entity in an inbox or outbox
 *
 * @author douglm
 *
 */
public class EntityQueuedEvent extends NamedEvent {
  private static final long serialVersionUID = 1L;

  private String ownerHref;
  private boolean inBox;

  /**
   * @param code
   * @param ownerHref
   * @param name
   * @param inBox
   */
  public EntityQueuedEvent(final SysCode code,
                           final String ownerHref,
                           final String name,
                           final boolean inBox) {
    super(code, name);

    this.ownerHref = ownerHref;
    this.inBox = inBox;
  }

  @Override
  public List<Attribute> getMessageAttributes() {
    List<Attribute> attrs = super.getMessageAttributes();

    if (getInBox()) {
      attrs.add(new Attribute("inbox", "true"));
    } else {
      attrs.add(new Attribute("outbox", "true"));
    }

    return attrs;
  }

  /**
   *
   * @return String    owner href of the entity
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /** Get the inbox flag
   *
   * @return String   inbox flag
   */
  public boolean getInBox() {
    return inBox;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.newLine();
    ts.append("ownerHref", getOwnerHref());
    ts.append("inBox", getInBox());
  }
}
