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
package org.bedework.calfacade.wrappers;

import org.bedework.calfacade.base.BwDbentity;

/** Base class for wrappers. These classes can be extended to protect methods of
 * the wrapped class and to carry out validation and change tracking.
 *
 * @author Mike Douglass
 *
 * @param <T>
 */
public class BwDbentityWrapper<T extends BwDbentity> extends BwDbentity
        implements EntityWrapper<T> {
  protected T entity;

  private int sizeChange;

  /** Constructor
   *
   * @param entity
   */
  public BwDbentityWrapper(T entity) {
    putEntity(entity);
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.wrappers.EntityWrapper#putEntity(org.bedework.calfacade.base.BwDbentity)
   */
  public void putEntity(T val) {
    entity = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.wrappers.EntityWrapper#fetchEntity()
   */
  public T fetchEntity() {
    return entity;
  }

  /* ====================================================================
   *                   Overridden methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.BwDbentity#getSeq()
   */
  public int getSeq() {
    throw new RuntimeException("org.bedework.noaccess");
  }

  /* ====================================================================
   *                   Size methods
   * ==================================================================== */

  /** Used to track size changes.
   *
   * @param val
   */
  public void setSizeChange(int val) {
    sizeChange = val;
  }

  /**
   * @return int last byte size change
   */
  public int getSizeChange() {
    return sizeChange;
  }

  /** Update the size change with the given increment
   *
   * @param val
   */
  public void updateSizeChange(int val) {
    sizeChange += val;
  }

  /** Update the size change with the size difference
   *
   * @param oldVal
   * @param newVal
   */
  public void updateSizeChange(BwDbentity oldVal, BwDbentity newVal) {
    updateSizeChange(newVal.length() - oldVal.length());
  }
}
