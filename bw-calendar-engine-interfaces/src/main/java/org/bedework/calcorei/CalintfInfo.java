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

import java.io.Serializable;

/** This class provides information about the underlying collection interface
 * allowing the service level to tailor queries appropriately.
 *
 * @author Mike Douglass
 */
public class CalintfInfo implements Serializable {
  /** True if this interface supports the locations methods.
   */
  private boolean handlesLocations;

  /** True if this interface supports the contacts methods.
   */
  private boolean handlesContacts;

  /** True if this interface supports the categories methods.
   */
  private boolean handlesCategories;

  /** Constructor
   *
   * @param handlesLocations
   * @param handlesContacts
   * @param handlesCategories
   */
  public CalintfInfo(boolean handlesLocations,
                     boolean handlesContacts,
                     boolean handlesCategories) {
    this.handlesLocations = handlesLocations;
    this.handlesContacts = handlesContacts;
    this.handlesCategories = handlesCategories;
  }

  /**
   * @return boolean true if this interface supports the locations methods
   */
  public boolean getHandlesLocations() {
    return handlesLocations;
  }

  /**
   * @return boolean true if this interface supports the sponsors methods
   */
  public boolean getHandlesContacts() {
    return handlesContacts;
  }

  /**
   * @return boolean true if this interface supports the categories methods
   */
  public boolean getHandlesCategories() {
    return handlesCategories;
  }

  public Object clone() {
    return new CalintfInfo(
       getHandlesLocations(),
       getHandlesContacts(),
       getHandlesCategories()
     );
  }
}

