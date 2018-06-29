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
package org.bedework.calfacade.base;

import org.bedework.calfacade.BwCategory;

import java.util.Set;

/** An entity that can have one or more categories will implement this interface.
 *
 * @author douglm
 */
public interface CategorisedEntity {
  /** Set the categories uids Set
   *
   * @param val    Set of category uids
   */
  void setCategoryHrefs(Set<String> val);

  /** Get the categories
   *
   *  @return Set of category uids
   */
  Set<String> getCategoryHrefs();

  /** Set the categories Set
   *
   * @param val    Set of categories
   */
  void setCategories(Set<BwCategory> val);

  /** Get the categories
   *
   *  @return Set of categories
   */
  Set<BwCategory> getCategories();

  /**
   * @return int number of categories.
   */
  int getNumCategories();

  /**
   * @param val
   * @return boolean true if added.
   */
  boolean addCategory(BwCategory val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  boolean removeCategory(BwCategory val);

  /** Check the categories for the same entity
   *
   * @param val        Category to test
   * @return boolean   true if the event has a particular category
   */
  boolean hasCategory(BwCategory val);

  /** Return a copy of the Set
   *
   * @return Set of BwCategory
   */
  Set<BwCategory> copyCategories();

  /** Return a clone of the Set
   *
   * @return Set of BwCategory
   */
  Set<BwCategory> cloneCategories();
}
