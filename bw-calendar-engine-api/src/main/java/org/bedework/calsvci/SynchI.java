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
package org.bedework.calsvci;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.synch.BwSynchInfo;
import org.bedework.calsvci.CalendarsI.CheckSubscriptionResult;

import java.io.Serializable;

/** Interface for handling interactions with the synch engine from within
 * bedework.
 *
 * @author Mike Douglass
 *
 */
public interface SynchI extends Serializable {
  /** Represents a synch connection - an opaque object managed by the
   * implementation of this interface
   *
   * @author douglm
   *
   */
  public interface Connection {
  }

  /** Is synchronization active?
   *
   * @return true if synchronization active.
   * @throws CalFacadeException
   */
  boolean getActive() throws CalFacadeException;

  /** Get a connection to the synch server.
   *
   * @return null if synch not active otherwise a connection.
   * @throws CalFacadeException
   */
  Connection getSynchConnection() throws CalFacadeException;

  /** Make a default file subscription for the given collection.
   *
   * @param val
   * @return true if subscribed OK.
   * @throws CalFacadeException
   */
  boolean subscribe(final BwCalendar val) throws CalFacadeException;

  /** Check the subscription if this is an external subscription. Will contact
   * the synch server and check the validity. If there is no subscription
   * on the synch server will attempt to resubscribe.
   *
   * @param val
   * @return result of call
   * @throws CalFacadeException
   */
  public CheckSubscriptionResult checkSubscription(BwCalendar val) throws CalFacadeException;

  /** Remove a subscription for the given collection.
   *
   * @param val
   * @return true if unsubscribed OK.
   * @throws CalFacadeException
   */
  boolean unsubscribe(final BwCalendar val) throws CalFacadeException;

  /** Returns the synch service information.
   *
   * @return full synch info
   * @throws CalFacadeException
   */
  BwSynchInfo getSynchInfo() throws CalFacadeException;
}
