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

import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexer;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/** Interface which handles manipulation of Category entities.
 *
 * <p>These are maintained in the database but completely cached by the indexer
 * and in memory.
 * There are methods to retrieve only the cached objects for the many cases where
 * speed is important.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 */
public interface Categories extends Serializable {
  /** Initialize the object
   *
   * @param adminCanEditAllPublic   True if administrators can edit all public entities
   */
  void init(boolean adminCanEditAllPublic);

  /** Return all entities satisfying the conditions and to which the current
   * user has access.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @param ownerHref   String principal href, null for current user
   * @param  creatorHref        non-null means limit to this
   * @return List of categories
   * @throws CalFacadeException
   */
  List<BwCategory> get(String ownerHref,
                       String creatorHref) throws CalFacadeException;

  /** Return all current user entities.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @return Collection     of objects
   * @throws CalFacadeException
   */
  List<BwCategory> get() throws CalFacadeException;

  /** Return all entities satisfying the conditions and to which the current
   * user has edit access.
   *
   * <p>Returns an empty collection for none.
   *
   * <p>The returned objects may not be persistent objects but the result of a
   * report query.
   *
   * @return List     of objects
   * @throws CalFacadeException
   */
  List<BwCategory> getEditable() throws CalFacadeException;

  /** Return a category given the uid if the user has access. The
   * returned object will be a non-persistent copy of the database
   * entity.
   *
   * @param uid       String uid
   * @return BwCategory object representing the entity in question
   *                     null if it doesn't exist.
   * @throws CalFacadeException
   */
  BwCategory get(String uid) throws CalFacadeException;

  /** Return the listed categories The
   * returned objects will be non-persistent copies of the database
   * entities. The returned list may be shorter than the supplied
   * list of uids.
   *
   * @param uids       Collection of String uid
   * @return List of BwCategory objects.
   * @throws CalFacadeException
   */
  List<BwCategory> get(Collection<String> uids) throws CalFacadeException;

  /** Check for existence
   *
   * @param cat the category
   * @return true if exists
   * @throws CalFacadeException
   */
  boolean exists(BwCategory cat) throws CalFacadeException;

  /** Return a category given the uid if the user has access. The
   * returned object will be a persistent version of the database
   * entity.
   *
   * <p>This method is required while we still have live collections
   * of database objects attached to entities such as events and
   * categories</p>
   *
   * @param uid       String uid
   * @return BwCategory object representing the entity in question
   *                     null if it doesn't exist.
   * @throws CalFacadeException
   */
  BwCategory getPersistent(String uid) throws CalFacadeException;

  /** Return one or more entities matching the given BwString to which the
   * user has access.
   *
   * <p>All event properties have string values which are used as the external
   * representation in icalendar files. The field should be unique
   * fo rthe owner. The field value may change over time while the
   * uid does not.
   *
   * @param val          BwString value
   * @return matching BwEventProperty object
   * @throws CalFacadeException
   */
  BwCategory find(final BwString val) throws CalFacadeException;

  /** Add an entity to the database. The id will be set in the parameter
   * object.
   *
   * @param val   BwEventProperty object to be added
   * @return boolean true for added, false for already exists
   * @throws CalFacadeException
   */
  boolean add(BwCategory val) throws CalFacadeException;

  /** Update an entity in the database.
   *
   * @param val   BwEventProperty object to be updated
   * @throws CalFacadeException
   */
  void update(BwCategory val) throws CalFacadeException;

  /** Delete an entity
   *
   * @param val      BwEventProperty object to be deleted
   * @return int     0 if it was deleted.
   *                 1 if it didn't exist
   *                 2 if in use
   * @throws CalFacadeException
   */
  int delete(BwCategory val) throws CalFacadeException;

  /** Return references to the entity
   *
   * @param val
   * @return a collection of references.
   * @throws CalFacadeException
   */
  Collection<EventPropertiesReference> getRefs(BwCategory val) throws CalFacadeException;

  /** Reindex current users categories
   *
   * @param indexer to use for this operation
   * @return number of entities reindexed
   * @throws CalFacadeException
   */
  int reindex(BwIndexer indexer) throws CalFacadeException;
}

