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
package org.bedework.calsvc.scheduling;

import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvc.CalSvc;
import org.bedework.util.misc.Uid;
import org.bedework.base.response.Response;

import java.util.Set;

/** Rather than have a single class steering calls to a number of smaller classes
 * we will build up a full implementation by progressively implementing abstract
 * classes.
 *
 * <p>That allows us to split up some rather complex code into appropriate pieces.
 *
 * <p>This piece handlkes the outbox low-level methods
 *
 * @author douglm
 *
 */
public abstract class OutBoxHandler extends SchedulingBase {
  OutBoxHandler(final CalSvc svci) {
    super(svci);
  }

  protected Response addToOutBox(final EventInfo ei,
                                 final BwCollection outBox,
                                 final Set<String> externalRcs) {
    // We have external recipients. Put in the outbox for mailing
    final EventInfo outEi = copyEventInfo(ei, getPrincipal());

    final BwEvent event = outEi.getEvent();
    event.setScheduleState(BwEvent.scheduleStateNotProcessed);
    event.setRecipients(externalRcs);
    event.setColPath(outBox.getPath());

    final var addResp = addEvent(outEi,
                                 "Out-" + Uid.getUid() + "-" + event.getDtstamp(),
                                 BwCollection.calTypeOutbox,
                                 true);

    if (!addResp.isOk()) {
      return addResp;
    }

    addAutoScheduleMessage(false,
                           outBox.getOwnerHref(),
                           event.getName());

    return addResp;
  }
}
