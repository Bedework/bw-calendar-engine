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
package org.bedework.calfacade.svc.prefs;

import java.io.Serializable;
/** A class just to allow me to delete all entries referring to a given entity
 * Hibernate doesn't seem to allow this any other way (though 3.1 might)
 *
 *  @author Mike Douglass douglm@rpi.edu
 *  @version 1.0
 */
public class BwAuthUserPrefsContact implements Serializable {
  private int id;
  private int contactid;

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /**
   * @param val
   */
  public void setId(int val) {
    id = val;
  }

  /**
   * @return int db id
   */
  public int getId() {
    return id;
  }

  /**
   * @param val
   */
  public void setContactid(int val) {
    contactid = val;
  }

  /**
   * @return int db id
   */
  public int getContactid() {
    return contactid;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BwAuthUserPrefsContact)) {
      return false;
    }

    BwAuthUserPrefsContact that = (BwAuthUserPrefsContact)obj;

    return (getId() == that.getId()) &&
           (getContactid() == that.getContactid());
  }

  public int hashCode() {
    return getId() * getContactid();
  }

  public String toString() {
    StringBuffer sb = new StringBuffer();

    sb.append("BwAuthUserPrefsSponsor{id=");
    sb.append(getId());
    sb.append(", sponsorid=");
    sb.append(getContactid());
    sb.append("}");

    return sb.toString();
  }
}
