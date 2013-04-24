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

import org.bedework.calfacade.util.CalFacadeUtil;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author Mike Douglass
 */
public class TypedUrl implements Comparable, Comparator, Serializable {
  private String type;

  private String value;

  /** Set the name
   *
   * @param val    String type
   */
  public void setType(String val) {
    type = val;
  }

  /** Get the type.
   *
   * @return String   type
   */
  public String getType() {
    return type;
  }

  /** Set the value
   *
   * @param val    String value
   */
  public void setValue(String val) {
    value = val;
  }

  /** Get the value.
   *
   * @return String   value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  public int compare(Object o1, Object o2) {
    if (o1 == o2) {
      return 0;
    }

    if (!(o1 instanceof TypedUrl)) {
      return -1;
    }

    if (!(o2 instanceof TypedUrl)) {
      return 1;
    }

    TypedUrl u1 = (TypedUrl)o1;
    TypedUrl u2 = (TypedUrl)o2;

    int res = CalFacadeUtil.cmpObjval(u1.getType(),
                                      u2.getType());
    if (res != 0) {
      return res;
    }

    return u1.getValue().compareTo(u2.getValue());
  }

  public int compareTo(Object o) {
    return compare(this, o);
  }

  public int hashCode() {
    return getValue().hashCode();
  }

  public boolean equals(Object o) {
    return compareTo(o) == 0;
  }

  public String toString() {
    StringBuffer sb = new StringBuffer("TypedUrl{");

    sb.append("type=");
    sb.append(type);
    sb.append(", value=");
    sb.append(value);
    sb.append("}");

    return sb.toString();
  }
}
