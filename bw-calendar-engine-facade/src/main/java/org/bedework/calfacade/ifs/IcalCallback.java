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
package org.bedework.calfacade.ifs;

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.responses.GetEntitiesResponse;
import org.bedework.calfacade.responses.GetEntityResponse;
import org.bedework.calfacade.svc.EventInfo;

import java.io.Serializable;

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
   */
  void setStrictness(int val);

  /** Get the conformance level. This relates to handling itip etc.
   * Should we be extra picky, just complain or let it all through.
   *
   * <p>For example, rfc2446 say's no attendees for method PUBLISH. If we
   * are actually handling an itip interaction then we should probably stick to
   * the rules. If the user is just doing an import of an event then maybe we
   * can be more relaxed about things.
   *
   * @return int level of conformance.
   */
  int getStrictness();

  // ENUM
  /** */
  int conformanceRelaxed = 0;

  /** */
  int conformanceWarn = 1;

  /** */
  int conformanceStrict = 2;

  /** Get the current principal
   *
   * @return BwPrincipal object
   */
  BwPrincipal getPrincipal();

  /** Get the current principal to set as owner
   *
   * @return BwPrincipal object
   */
  BwPrincipal getOwner();

  /** Return a calendar user address corresponding to the supplied value. We may
   * have been supplied with a user principal.
   *
   * @param val user account or principal
   * @return caladdr of form mailto:x@y we hope.
   */
  String getCaladdr(String val);

  /** Look for the given category for this user. Return null for not found.
   * This returns a persistent object and is only for use when
   * reconstructing events from a calendar input stream.
   *
   * @param val identifying category
   * @return Response with Category object
   */
  GetEntityResponse<BwCategory> findCategory(BwString val);

  /** Add the given category.
   *
   * @param val
   */
  void addCategory(BwCategory val);

  /** Get the contact with the given uid.
   *
   * @param uid
   * @return contact object
   */
  GetEntityResponse<BwContact> getContact(String uid);

  /** Find the contact.
   *
   * @param val identifying contact
   * @return response with contact object
   */
  GetEntityResponse<BwContact> findContact(BwString val);

  /** Add the contact
   * @param val
   */
  void addContact(BwContact val);

  /** Get the location with the given uid.
   *
   * @param uid
   * @return status and Location object
   */
  GetEntityResponse<BwLocation> getLocation(String uid);

  /** Find a location owned by the current user which has a named
   * key field which matches the value.
   *
   * @param name - of key field
   * @param val - expected full value
   * @return null or location object
   */
  GetEntityResponse<BwLocation> fetchLocationByKey(String name,
                                                   String val);

  /** Find the location given the address.
   *
   * <p>NOTE: the addition of multi-valued fields to the location object
   * has led to some possible issues. Setting and accessing the address
   * field should continue to work for personal clients that treat it as
   * a single value.
   *
   * <p>Public events will not be able to match on the address field.
   * Use the combined value as a key and use the findLocationByCombined
   *
   * @param address
   * @return Response with status and Location object
   */
  GetEntityResponse<BwLocation> findLocation(BwString address);

  /** Find the location given the combined address values.
   *
   * @param val - address, room, city, state, zip
   * @param persisted - true if we want the db copy
   * @return Location object
   */
  GetEntityResponse<BwLocation> fetchLocationByCombined(String val,
                                                        boolean persisted);

  /** Add the location
   * @param val
   */
  void addLocation(BwLocation val);

  /** Return a Collection of EventInfo objects. There should only be
   * one returned.
   *
   * @param colPath of collection to search
   * @param guid of entity
   * @return Collection of EventInfo
   */
  GetEntitiesResponse<EventInfo> getEvent(String colPath, String guid);

  /**
   * @return true if we are not including the full tz specification
   */
  boolean getTimezonesByReference();
}

