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
package org.bedework.dumprestore.restore;

/** Class to represent the key for entities indexed by uid and owner.
 *
 * @author Mike Douglass   douglm at bedework.edu
 */
public class OwnerUidKey implements Comparable {
  /** */
  public String ownerHref;
  /** */
  public String uid;

  /** No-arg Constructor
   */
  public OwnerUidKey() {
  }

  /** Constructor
   *
   * @param ownerHref
   * @param uid
   */
  public OwnerUidKey(String ownerHref, String uid) {
    this.ownerHref = ownerHref;
    this.uid = uid;
  }

  /**
   * @param val BwPrincipal
   */
  public void setOwnerHref(String val) {
    ownerHref = val;
  }

  /**
   * @return BwPrincipal kind
   */
  public String getOwnerHref() {
    return ownerHref;
  }

  /**
   * @param val
   */
  public void setUid(String val) {
    uid = val;
  }

  /**
   * @return  String uid
   */
  public String getUid() {
    return uid;
  }

  /**
   * @param o
   * @return int
   */
  public int compareTo(Object o) {
    if (o == null) {
      return -1;
    }

    if (!(o instanceof OwnerUidKey)) {
      return -1;
    }

    OwnerUidKey that = (OwnerUidKey)o;

    int res = ownerHref.compareTo(that.ownerHref);
    if (res != 0) {
      return res;
    }

    return uid.compareTo(that.uid);
  }

  public int hashCode() {
    return ownerHref.hashCode() * uid.hashCode();
  }

  /* We always use the compareTo method
   */
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    return compareTo(obj) == 0;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("OwnerUidKey[");

    sb.append(ownerHref);
    sb.append(", ");
    sb.append(uid);
    sb.append("]");

    return sb.toString();
  }
}
