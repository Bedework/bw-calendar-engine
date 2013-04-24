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

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.util.CalFacadeUtil;

import java.io.Serializable;

/** A related-to property.
 *.
 *  @version 1.0
 */
@Dump(elementName="relatedTo", keyFields={"relType", "value"})
public class BwRelatedTo extends DumpEntity
         implements Comparable<BwRelatedTo>, Serializable {
  private String relType;

  private String value;

  /** Constructor
   *
   */
  public BwRelatedTo() {
  }

  /** Constructor
   *
   * @param relType
   * @param value
   */
  public BwRelatedTo(final String relType, final String value) {
    this.relType = relType;
    this.value = value;
  }

  /** Set the relType
   *
   * @param val    String relType
   */
  public void setRelType(final String val) {
    relType = val;
  }

  /** Get the relType
   *
   *  @return String   relType
   */
  public String getRelType() {
    return relType;
  }

  /** Set the value
   *
   * @param val    String value
   */
  public void setValue(final String val) {
    value = val;
  }

  /** Get the value
   *
   *  @return String   value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /**
   * @return String rfc value
   */
  public String strVal() {
    StringBuilder sb = new StringBuilder();

    if (getRelType() != null) {
      sb.append("RELTYPE=");
      sb.append(getRelType());
      sb.append(":");
    }

    if (getValue() != null) {
      sb.append(getValue());
    }

    return sb.toString();
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  public int compareTo(final BwRelatedTo that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    int res = CalFacadeUtil.cmpObjval(getRelType(), that.getRelType());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  @Override
  public int hashCode() {
    int hc = 13;

    if (getRelType() != null) {
      hc *= getRelType().hashCode();
    }

    if (getValue() != null) {
      hc *= getValue().hashCode();
    }

    return hc;
  }

  @Override
  public boolean equals(final Object o) {
    return compareTo((BwRelatedTo)o) == 0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("BwRelatedTo{");

    sb.append(", relType=");
    sb.append(getRelType());
    sb.append(", value=");
    sb.append(getValue());
    sb.append("}");

    return sb.toString();
  }

  @Override
  public Object clone() {
    return new BwRelatedTo(getRelType(), getValue());
  }
}
