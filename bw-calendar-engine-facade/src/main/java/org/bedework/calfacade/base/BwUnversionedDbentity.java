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

import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.annotations.NoWrap;
import org.bedework.util.misc.ToString;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

/** Base type for a database entity. We require an id and the subclasses must
 * implement hashcode and compareTo.
 *
 * @author Mike Douglass
 * @version 1.0
 *
 * @param <T>
 */
public class BwUnversionedDbentity<T> extends DumpEntity<T>
        implements Comparable<T>, Serializable {
  private int id = CalFacadeDefs.unsavedItemKey;

  /**
   * @param val
   */
  public void setId(final int val) {
    id = val;
  }

  /**
   * @return int id
   */
  @JsonIgnore
  public int getId() {
    return id;
  }

  /**
   * @return true if this entity is not saved.
   */
  public boolean unsaved() {
    return getId() == CalFacadeDefs.unsavedItemKey;
  }

  /**
   * Mark this entity as not saved.
   */
  public void markUnsaved() {
    setId(CalFacadeDefs.unsavedItemKey);
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Add our stuff to the ToString object
   *
   * @param ts    ToString for result
   */
  protected void toStringSegment(final ToString ts) {
    ts.append("id", getId());
  }

  /* ====================================================================
   *                   Object methods
   * The following are required for a db object.
   * ==================================================================== */

  /** Make visible
   * @return Object of class T
   */
  @Override
  @NoWrap
  public Object clone() {
    return null;
  }

  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  @NoWrap
  public int compareTo(final T o) {
    throw new RuntimeException("compareTo must be implemented for a db object");
  }

  @Override
  @NoWrap
  public int hashCode() {
    throw new RuntimeException("hashcode must be implemented for a db object");
  }

  /* We always use the compareTo method
   */
  @SuppressWarnings("unchecked")
  @Override
  @NoWrap
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    return compareTo((T)obj) == 0;
  }
}
