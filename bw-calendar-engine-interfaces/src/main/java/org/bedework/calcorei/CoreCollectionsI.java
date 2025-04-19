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

import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.CollectionAliases;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.wrappers.CollectionWrapper;
import org.bedework.base.response.GetEntityResponse;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** This is the collections section of the low level interface to the collection
 * database.
 * The type property defines which type of collection we are
 * dealing with. The CalDAV spec defines what is allowable, e.g. no collections
 * inside a calendar collection.
 *
 * <p>To allow us to enforce access checks we wrap the object inside a wrapper
 * class which blocks access to the getChildren method. To retrieve the children
 * of a collection object call the getCollections(BwCollection) method. The resulting
 * collection is a set of access checked, wrapped objects. Only accessible
 * children will be returned.
 *
 * @author Mike Douglass
 */
public interface CoreCollectionsI extends Serializable {

  /* ====================================================================
   *                   Collections
   * ==================================================================== */

  /** Called whenever we start running under a new principal. May require a
   * flush of some cached information.
   */
  void principalChanged();

  /**
   * @param path to collection
   * @param token or null if first call
   * @return CollectionSynchInfo
   */
  CollectionSynchInfo getSynchInfo(String path,
                                   String token);

  /** Returns children of the given collection to which the current user has
   * some access.
   *
   * @param  cal          parent collection
   * @param indexer not null means use indexer
   * @return Collection   of BwCollection
   */
  Collection<BwCollection> getCollections(BwCollection cal,
                                          BwIndexer indexer);

  /** Attempt to get collection referenced by the alias. For an internal alias
   * the result will also be set in the aliasTarget property of the parameter.
   *
   * @param val the alias
   * @param resolveSubAlias - if true and the alias points to an alias, resolve
   *                  down to a non-alias.
   * @param freeBusy determines required access
   * @param indexer not null means use indexer
   * @return BwCollection
   */
  BwCollection resolveAlias(BwCollection val,
                            boolean resolveSubAlias,
                            boolean freeBusy,
                            BwIndexer indexer);

  /**
   * @param val a collection to check
   * @return response with status and info.
   */
  GetEntityResponse<CollectionAliases> getAliasInfo(BwCollection val);

  /** Find any aliases to the given collection.
   *
   * @param val - the alias
   * @return list of aliases
   */
  List<BwCollection> findAlias(String val);

  /** Get a collection given the path. If the path is that of a 'special'
   * collection, for example the deleted collection, it may not exist if it has
   * not been used.
   *
   * @param  path          String path of collection
   * @param  desiredAccess int access we need
   * @param  alwaysReturnResult  false to raise access exceptions
   *                             true to return only those we have access to
   * @return BwCollection null for unknown collection
   */
  BwCollection getCollection(String path,
                             int desiredAccess,
                             boolean alwaysReturnResult);

  /** Get a collection given the path. If the path is that of a 'special'
   * collection, for example the deleted collection, it may not exist if it has
   * not been used.
   *
   * @param  indexer       Used to retrieve the object
   * @param  path          String path of collection
   * @param  desiredAccess int access we need
   * @param  alwaysReturnResult  false to raise access exceptions
   *                             true to return only those we have access to
   * @return BwCollection null for unknown collection
   */
  BwCollection getCollectionIdx(BwIndexer indexer,
                                String path,
                                int desiredAccess,
                                boolean alwaysReturnResult);

  /** Returned by getSpecialCollection
   */
  class GetSpecialCollectionResult {
    /** True if user does not exist
     */
    public boolean noUserHome;

    /** True if collection was created
     */
    public boolean created;

    /**
     */
    public BwCollection cal;
  }
  /** Get a special collection (e.g. Trash) for the given user. If it does not
   * exist and is supported by the target system it will be created.
   *
   * @param  indexer       Used to retrieve the object
   * @param  owner     of the entity
   * @param  calType   int special collection type.
   * @param  create    true if we should create it if non-existant.
   * @param  access    int desired access - from PrivilegeDefs
   * @return GetSpecialCollectionResult null for unknown collection
   */
  GetSpecialCollectionResult getSpecialCollection(
          BwIndexer indexer,
          BwPrincipal<?> owner,
          int calType,
          boolean create,
          int access);

  /** Add a collection object
   *
   * <p>The new collection object will be added to the db. If the indicated parent
   * is null it will be added as a root level collection.
   *
   * <p>Certain restrictions apply, mostly because of interoperability issues.
   * A collection cannot be added to another collection which already contains
   * entities, e.g. events etc.
   *
   * <p>Names cannot contain certain characters - (complete this)
   *
   * <p>Name must be unique at this level, i.e. all paths must be unique
   *
   * @param  val     BwCollection new object
   * @param  parentPath  String path to parent.
   * @return BwCollection object as added. Parameter val MUST be discarded
   */
  BwCollection add(BwCollection val,
                   String parentPath);

  /** Change the name (path segment) of a collection object.
   *
   * @param  val         BwCollection object
   * @param  newName     String name
   */
  void renameCollection(BwCollection val,
                        String newName);

  /** Move a collection object from one parent to another
   *
   * @param  val         BwCollection object
   * @param  newParent   BwCollection potential parent
   */
  void moveCollection(BwCollection val,
                      BwCollection newParent);

  /** Mark collection as modified
   *
   * @param  path    String path for the collection
   */
  void touchCollection(String path);

  /** Mark collection as modified
   *
   * @param  col         BwCollection object
   */
  void touchCollection(BwCollection col);

  /** Update a collection object
   *
   * @param  val     BwCollection object
   */
  void updateCollection(BwCollection val);

  /** Change the access to the given collection entity.
   *
   * @param cal      BwCollection
   * @param aces     Collection of ace
   * @param replaceAll true to replace the entire access list.
   */
  void changeAccess(BwCollection cal,
                    Collection<Ace> aces,
                    boolean replaceAll);

  /** Remove any explicit access for the given who to the given collection entity.
   *
   * @param cal      BwCollection
   * @param who      AceWho
   */
  void defaultAccess(BwCollection cal,
                     AceWho who);

  /** Delete the given collection
   *
   * <p>XXX Do we want a recursive flag or do we implement that higher up?
   *
   * @param val      BwCollection object to be deleted
   * @param reallyDelete Really delete it - otherwise it's tombstoned
   * @return boolean false if it didn't exist, true if it was deleted.
   */
  boolean deleteCollection(BwCollection val,
                           boolean reallyDelete);

  /** Check to see if a collection is empty. A collection is not empty if it
   * contains other collections or calendar entities.
   *
   * @param val      BwCollection object to check
   * @return boolean true if the collection is empty
   */
  boolean isEmpty(BwCollection val);

  /** Called after a principal has been added to the system.
   *
   * @param principal the new principal
   */
  void addNewCollections(BwPrincipal<?> principal);

  /** Return all collections on the given path with a lastmod GREATER
   * THAN that supplied. The path may not be null. A null lastmod will
   * return all collections in the collection.
   * 
   * <p>Also returned are those collections that are internal or 
   * external aliases. It is up to the caller to decide if they will 
   * be resolved and returned in the report</p>
   *
   * <p>Note that this is used only for synch reports and purging of tombstoned
   * collections. The returned objects are NOT to be delivered to clients.
   *
   * @param path - must be non-null
   * @param lastmod - limit search, may be null
   * @return set of collection paths.
   */
  Set<BwCollection> getSynchCols(String path,
                                 String lastmod);

  /** Return true if the collection has changed as defined by the sync token.
   *
   * @param col - must be non-null
   * @param lastmod - the token
   * @return true if changes made.
   */
  boolean testSynchCol(BwCollection col,
                       String lastmod);

  /** Return the value to be used as the sync-token property for the given path.
   * This is effectively the max sync-token of the collection and any child
   * collections.
   *
   * @param path of collection
   * @return a sync-token
   */
  String getSyncToken(String path);

  /* ==============================================================
   *                  Admin support
   * ============================================================== */

  /** Obtain the next batch of children paths for the supplied path. A path of
   * null will return the system roots.
   *
   * @param parentPath of parent collection
   * @param start start index in the batch - 0 for the first
   * @param count count of results we want
   * @return collection of String paths or null for no more
   */
  Collection<String> getChildCollections(String parentPath,
                                         int start,
                                         int count);

  BwCollection checkAccess(CollectionWrapper col,
                           int desiredAccess,
                           boolean alwaysReturnResult);

  /** Not sure this is the right thing to do.
   *
   * @param val to merge
   * @return merged entity
   */
  BwUnversionedDbentity<?> merge(BwUnversionedDbentity<?> val);
}
