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

import org.bedework.calfacade.base.BwDbentity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;

/** Represent an attachment.
 *
 *  @author Mike Douglass   douglm - rpi.edu
 */
public class BwAttachment extends BwDbentity<BwAttachment> {
  /* Params fields */

  private String fmtType;

  private String valueType; /* URI or BINARY */

  private String encoding;  /* Always binary */

  private String uri;

  private String value;

  /** Constructor
   *
   */
  public BwAttachment() {
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /** Set the fmttype
   *
   *  @param  val   String fmttype
   */
  public void setFmtType(final String val) {
    fmtType = val;
  }

  /** Get the fmttype
   *
   *  @return String     fmttype
   */
  public String getFmtType() {
    return fmtType;
  }

  /** Set the valueType
   *
   *  @param  val   String valueType
   */
  public void setValueType(final String val) {
    valueType = val;
  }

  /** Get the valueType
   *
   *  @return String     valueType
   */
  public String getValueType() {
    return valueType;
  }

  /** Set the encoding
   *
   *  @param  val   String encoding
   */
  public void setEncoding(final String val) {
    encoding = val;
  }

  /** Get the encoding
   *
   *  @return String     encoding
   */
  public String getEncoding() {
    return encoding;
  }

  /** Set the uri
   *
   *  @param  val   String uri
   */
  public void setUri(final String val) {
    uri = val;
  }

  /** Get the uri
   *
   *  @return String     uri
   */
  public String getUri() {
    return uri;
  }

  /** Set the value
   *
   *  @param  val   String value
   */
  public void setValue(final String val) {
    value = val;
  }

  /** Get the value
   *
   *  @return String     value
   */
  public String getValue() {
    return value;
  }

  /* ====================================================================
   *                   Other non-db methods
   * ==================================================================== */

  /** Copy this objects values into the parameter
   *
   * @param val
   */
  public void copyTo(final BwAttachment val) {
    val.setFmtType(getFmtType());
    val.setValueType(getValueType());
    val.setEncoding(getEncoding());
    val.setUri(getUri());
    val.setValue(getValue());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int hashCode() {
    int hc = 7;

    if (getUri() != null) {
      return hc * getUri().hashCode();
    }

    return hc * getValue().hashCode();
  }

  @Override
  public int compareTo(final BwAttachment that)  {
    if (this == that) {
      return 0;
    }

    int res = CalFacadeUtil.cmpObjval(getEncoding(), that.getEncoding());
    if (res != 0) {
      return res;
    }

    res = CalFacadeUtil.cmpObjval(getUri(), that.getUri());
    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getValue(), that.getValue());
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("fmttype", getFmtType());
    ts.append("valueType", getValueType());
    ts.append("encoding", getEncoding());

    if (getUri() != null) {
      ts.append("uri", getUri());
    } else {
      ts.append("value", getValue());
    }

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwAttachment nobj = new BwAttachment();
    copyTo(nobj);

    return nobj;
  }
}

