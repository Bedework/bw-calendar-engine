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
package org.bedework.calcorei;

import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Interface which handles manipulation of BwEventProperty subclasses which are
 * treated in the same manner, these being Category, Location and Contact.
 *
 * <p>Each has a single field which together with the owner makes a unique
 * key and all operations on those classes are the same.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 * @param <T> type of property, Location, Sponsor etc.
 */
public interface CoreEventPropertiesI <T extends BwEventProperty> extends Serializable {
  /**
   * @param val the event property
   */
  void add(T val);

  /**
   * @param val the event property
   */
  void update(T val);

  /** Return all entities satisfying the conditions and to which the current
   * user has access.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @param ownerHref   String principal href, null for current user
   * @return Collection     of objects
   */
  Collection<T> getAll(String ownerHref);

  /** Return an entity given the uid if the user has access
   *
   * @param uid       String uid
   * @return BwEventProperty object representing the entity in question
   *                     null if it doesn't exist.
   */
  T get(String uid);

  /** Return one or more entities matching the given BwString to which the
   * user has access.
   *
   * <p>All event properties have string values which are used as the external
   * representation in icalendar files. The combination of field and owner
   * should be unique. The field value may change over time while the
   * uid does not.
   *
   * @param val          BwString value
   * @param ownerHref   String principal href, null for current user
   * @return matching BwEventProperty object
   */
  T find(final BwString val,
         final String ownerHref);

  /** Check that the given property is unique.
   *
   * @param val          BwString value
   * @param ownerHref   String principal href, null for current user
   */
  void checkUnique(BwString val,
                   String ownerHref);

  /** Delete an entity
   *
   * @param val      BwEventProperty object to be deleted
   */
  void deleteProp(T val);

  /**
   * @param val
   * @return list of references
   */
  List<EventPropertiesReference> getRefs(final T val);


  /** Return count of events referencing the given entity
   *
   * @param val      BwEventProperty object to be checked
   * @return long count
   */
  long getRefsCount(final T val);
}

