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
import java.util.ArrayList;
import java.util.Collection;

/** A value and its abbreviations
 *
 * @author Mike Douglass
 *
 */
public class AbbreviatedValue implements Comparable<AbbreviatedValue>, Serializable {
  /** */
  public Collection<String> abbrevs;
  /** */
  public String value;

  /** Constructor
   *
   * @param abbrevs
   * @param value
   */
  public AbbreviatedValue(Collection<String>abbrevs, String value) {
    this.abbrevs = abbrevs;
    this.value = value;
  }

  /**
   * @param val
   */
  public void setAbbrevs(Collection<String> val) {
    abbrevs = val;
  }

  /**
   * @return Collection<String>
   */
  public Collection<String> getAbbrevs() {
    return abbrevs;
  }

  /** Set the value
   *
   * @param val    String value
   */
  public void setValue(String val) {
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
   *                        Object methods
   * ==================================================================== */

  public int compareTo(AbbreviatedValue that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    int res = CalFacadeUtil.cmpObjval(getAbbrevs(), that.getAbbrevs());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  public int hashCode() {
    int hc = 7;

    if (getAbbrevs() != null) {
      hc *= getAbbrevs().hashCode();
    }

    if (getValue() != null) {
      hc *= getValue().hashCode();
    }

    return hc;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("AbbreviatedValue{");

    sb.append("value=");
    sb.append(value);
    sb.append("}");

    return sb.toString();
  }

  public Object clone() {
    return new AbbreviatedValue(new ArrayList<String>(getAbbrevs()),
                                getValue());
  }
}
