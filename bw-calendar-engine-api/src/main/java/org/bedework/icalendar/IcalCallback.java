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
package org.bedework.icalendar;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;

import java.io.Serializable;
import java.util.Collection;

/** Allow the translation process to retrieve objects and information it might
 * need from the system.
 *
 * @author Mike Douglass douglm@rpi.edu
 * @version 1.0
 */
public interface IcalCallback extends Serializable {
  /** Set the conformance
   *
   * @param val
   * @throws CalFacadeException
   */
  void setStrictness(int val) throws CalFacadeException;

  /** Get the conformance level. This relates to handling itip etc.
   * Should we be extra picky, just complain or let it all through.
   *
   * <p>For example, rfc2446 say's no attendees for method PUBLISH. If we
   * are actually handling an itip interaction then we should probably stick to
   * the rules. If the user is just doing an import of an event then maybe we
   * can be more relaxed about things.
   *
   * @return int level of conformance.
   * @throws CalFacadeException
   */
  int getStrictness() throws CalFacadeException;

  // ENUM
  /** */
  final static int conformanceRelaxed = 0;

  /** */
  final static int conformanceWarn = 1;

  /** */
  final static int conformanceStrict = 2;

  /** Get the current principal
   *
   * @return BwPrincipal object
   * @throws CalFacadeException
   */
  BwPrincipal getPrincipal() throws CalFacadeException;

  /** Get the current principal to set as owner
   *
   * @return BwPrincipal object
   * @throws CalFacadeException
   */
  BwPrincipal getOwner() throws CalFacadeException;

  /** Return a calendar user address corresponding to the supplied value. We may
   * have been supplied with a user principal.
   *
   * @param val
   * @return caladdr of form mailto:x@y we hope.
   * @throws CalFacadeException
   */
  String getCaladdr(String val) throws CalFacadeException;

  /** Look for the given category for this user. Return null for not found.
   * This returns a persistent object and is only for use when
   * reconstructing events from a calendar input stream.
   *
   * @param val
   * @return Category object
   * @throws CalFacadeException
   */
  BwCategory findCategory(BwString val) throws CalFacadeException;

  /** Add the given category.
   *
   * @param val
   * @throws CalFacadeException
   */
  void addCategory(BwCategory val) throws CalFacadeException;

  /** Get the contact with the given uid.
   *
   * @param uid
   * @return contact object
   * @throws CalFacadeException
   */
  BwContact getContact(String uid) throws CalFacadeException;

  /** Find the contact.
   *
   * @param val
   * @return contact object
   * @throws CalFacadeException
   */
  BwContact findContact(BwString val) throws CalFacadeException;

  /** Add the contact
   * @param val
   * @throws CalFacadeException
   */
  void addContact(BwContact val) throws CalFacadeException;

  /** Get the location with the given uid.
   *
   * @param uid
   * @return Location object
   * @throws CalFacadeException
   */
  BwLocation getLocation(String uid) throws CalFacadeException;

  /** Get the location with the given address.
   *
   * @param address to find
   * @return Location object or null if not found
   * @throws CalFacadeException
   */
  BwLocation getLocation(BwString address) throws CalFacadeException;

  /** Find the location given the address.
   *
   * @param address
   * @return Location object
   * @throws CalFacadeException
   */
  BwLocation findLocation(BwString address) throws CalFacadeException;

  /** Add the location
   * @param val
   * @throws CalFacadeException
   */
  void addLocation(BwLocation val) throws CalFacadeException;

  /** Return a Collection of EventInfo objects. There should only be
   * one returned.
   *
   * @param cal       calendar to search
   * @param guid
   * @return Collection of EventInfo
   * @throws CalFacadeException
   */
  Collection<EventInfo> getEvent(BwCalendar cal, String guid)
          throws CalFacadeException;

  /** URIgen object used to provide ALTREP values - or null for no altrep
   *
   * @return object or null
   * @throws CalFacadeException
   */
  URIgen getURIgen() throws CalFacadeException;

  /**
   * @return true if we are not including the full tz specification
   * @throws CalFacadeException
   */
  boolean getTimezonesByReference() throws CalFacadeException;
}

