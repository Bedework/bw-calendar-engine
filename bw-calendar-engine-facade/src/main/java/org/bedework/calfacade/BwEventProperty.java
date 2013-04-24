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
package org.bedework.calfacade;

import org.bedework.calfacade.base.BwShareableDbentity;

import edu.rpi.sss.util.Uid;

/** Base for those classes that can be a property of an event and are all
 * treated in the same manner, being Category, Location and Sponsor.
 *
 * <p>Each has a single field which together with the owner makes a unique
 * key and all operations on those classes are the same.
 *
 * @author Mike Douglass
 * @version 1.0
 *
 * @param <T>
 */
public abstract class BwEventProperty<T> extends BwShareableDbentity<T> {
  private String uid;

  /** Constructor
   *
   */
  public BwEventProperty() {
    super();
  }

  /**
   * @return Finder Key value from this object.
   */
  public abstract BwString getFinderKeyValue();

  /** Set the uid
   *
   * @param val    String uid
   */
  public void setUid(final String val) {
    uid = val;
  }

  /** Get the uid
   *
   * @return String   uid
   */
  public String getUid() {
    return uid;
  }

  /**
   * fill in the uid.
   *
   * @return this object
   */
  public BwEventProperty<T> initUid() {
    setUid(Uid.getUid());

    return this;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Copy this objects fields into the parameter
   *
   * @param val
   */
  public void copyTo(final BwEventProperty<?> val) {
    val.setUid(getUid());
  }
}
