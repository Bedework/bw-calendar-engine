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
package org.bedework.calcore.ro;

import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.CollectionAliases;

import java.util.ArrayList;
import java.util.List;

/** Provide information about the aliases for the given collection.
 *
 * If the collection is an alias and the aliased list is empty, or
 * the last element in the aliased list is an alias then the target
 * of that alias is inaccessible or missing.
 *
 * @author Mike Douglass
 */
public class CollectionAliasesImpl implements CollectionAliases {
  /* The collection that was queried.
   */
  private final BwCollection collection;

  /* Any aliases
   */
  private final List<BwCollection> aliased = new ArrayList<>();

  private boolean circular;

  private BwCollection invalidAlias;

  // Used while building the list
  private final ArrayList<String> pathElements = new ArrayList<>();

  /**
   * @param collection collection of interest
   */
  public CollectionAliasesImpl(final BwCollection collection) {
    this.collection = collection;
    pathElements.add(collection.getPath());
  }

  /**
   * @return collection that was queried.
   */
  public BwCollection getCollection() {
    return collection;
  }

  /**
   * @return chain of aliases closest to collection first.
   *         Empty for none.
   */
  public List<BwCollection> getAliased() {
    return aliased;
  }

  void setInvalidAlias(final BwCollection val) {
    invalidAlias = val;
  }

  @Override
  public BwCollection getInvalidAlias() {
    return invalidAlias;
  }

  @Override
  public boolean getCircular() {
    return circular;
  }

  /** Add a collection to the info. Check to ensure it's not already
   * there.
   *
   * @param val collection
   * @return true added - false means we saw it before - list is circular
   */
  boolean addCollection(final BwCollection val) {
    final String path = val.getPath();

    if (pathElements.contains(path)) {
      invalidAlias = val;
      circular = true;
      return false;
    }

    pathElements.add(path);

    return true;
  }

  void markBad(final BwCollection val) {
    invalidAlias = val;
  }
}
