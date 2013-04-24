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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.svc.EventInfo.UpdateResult;
import org.bedework.calsvci.SchedulingI;

/**
 * @author douglm
 *
 */
public interface SchedulingIntf extends SchedulingI {
  /** For adding the event we already have the scheduling method set. If we are
   * updating the event then the action depends upon the new and old methods. The
   * scheduling result is set in the UpdateResult object.
   *
   * <p>If we already cancelled the event then we do no scheduling. (What if we
   * want to resend?)
   *
   * <p>If we want to refresh we just resend it.
   *
   * <p>If we want to cancel we send cancels.
   *
   * @param ei
   * @param uer   information about the change that took place.
   * @param noInvites - suppresses the sending of invitations. Does NOT suppress the
   *               sending of CANCEL to disinvited attendees
   * @throws CalFacadeException
   */
  void implicitSchedule(EventInfo ei,
                        UpdateResult uer,
                        boolean noInvites)
          throws CalFacadeException;

  /** Copy an event to send as a request or a response. Non-recurring is easy,
   * we just copy it.
   *
   * <p>Recurring events introduce a number of complications. We are only supposed
   * to send as much as the recipient needs to know, that is, if an attendee
   * has been disinvited from one instance we should not send that instance but
   * instead add an EXDATE. For that case we will have an override that does not
   * include the attendee.
   *
   * <p>For the case that an attendee has been added we should remove any rules
   * and add RDATES for all instances in which the attendee is present.
   *
   * @param ei
   * @param owner
   * @return a copy of the event.
   * @throws CalFacadeException
   */
  public EventInfo copyEventInfo(EventInfo ei,
                                 BwPrincipal owner) throws CalFacadeException;

  /** Save the event which is all set up except for the name. If we get a
   * conflict we add a suffix and try again
   *
   * @param ei
   * @param namePrefix
   * @param calType
   * @param noInvites
   * @return null if added, error code otherwise
   * @throws CalFacadeException
   */
  String addEvent(EventInfo ei,
                  String namePrefix,
                  int calType,
                  boolean noInvites) throws CalFacadeException;
}
