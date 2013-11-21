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

import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;

import net.fortuna.ical4j.model.property.RequestStatus;

import java.io.Serializable;

/** An RFC5545 request status.
 *.
 *  @version 1.0
 */
public class BwRequestStatus implements Comparable, Serializable {
  private String code;

  private BwString description;

  private String data;

  /** Constructor
   *
   */
  public BwRequestStatus() {
  }

  /** Constructor
   *
   * @param code
   * @param description
   */
  public BwRequestStatus(final String code,
                         final String description) {
    this(code, new BwString(null, description), null);
  }

  /** Constructor
   *
   * @param code
   * @param description
   * @param data
   */
  public BwRequestStatus(final String code,
                         final BwString description,
                         final String data) {
    this.code = code;
    this.description = description;
    this.data = data;
  }

  /** Set the status code
   *
   * @param val    String status code
   */
  public void setCode(final String val) {
    code = val;
  }

  /** Get the status code
   *
   *  @return String   status code
   */
  public String getCode() {
    return code;
  }

  /** Set the description
   *
   * @param val    BwString description
   */
  public void setDescription(final BwString val) {
    description = val;
  }

  /** Get the description
   *
   *  @return BwString   description
   */
  public BwString getDescription() {
    return description;
  }

  /** Set the data
   *
   * @param val    String data
   */
  public void setData(final String val) {
    data = val;
  }

  /** Get the data
   *
   * @return String   data
   */
  public String getData() {
    return data;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /**
   * @return String rfc value
   */
  public String strVal() {
    StringBuilder sb = new StringBuilder();

    if (getCode() != null) {
      sb.append(getCode());
      sb.append(";");
    }

    if (getDescription() != null) {
      sb.append(getDescription().getValue());
      sb.append(";");
    }

    if (getData() != null) {
      sb.append(getData());
      sb.append(";");
    }

    return sb.toString();
  }

  /**
   * @param val
   * @return BwRequestStatus
   */
  public static BwRequestStatus fromRequestStatus(final RequestStatus val) {
    BwString str = new BwString(null, val.getDescription());
    // LANG

     return new BwRequestStatus(val.getStatusCode(), str, val.getExData());
  }

  /** Figure out what's different and update it. This should reduce the number
   * of spurious changes to the db.
   *
   * @param from
   * @return true if we changed something.
   */
  public boolean update(final BwRequestStatus from) {
    boolean changed = false;

    if (CalFacadeUtil.cmpObjval(getCode(), from.getCode()) != 0) {
      setCode(from.getCode());
      changed = true;
    }

    if (getDescription() == null) {
      if (from.getDescription() != null) {
        setDescription(from.getDescription());
        changed = true;
      }
    } else if (getDescription().update(from.getDescription())) {
      changed = true;
    }

    if (CalFacadeUtil.cmpObjval(getData(), from.getData()) != 0) {
      setData(from.getData());
      changed = true;
    }

    return changed;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  public int compareTo(final Object o) {
    if (o == this) {
      return 0;
    }

    if (o == null) {
      return -1;
    }

    if (!(o instanceof BwRequestStatus)) {
      return -1;
    }

    BwRequestStatus that = (BwRequestStatus)o;

    int res = CalFacadeUtil.cmpObjval(getCode(), that.getCode());

    if (res != 0) {
      return res;
    }

    res = getDescription().compareTo(that.getDescription());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getData(), that.getData());
  }

  @Override
  public int hashCode() {
    int hc = getCode().hashCode();

    if (getDescription() != null) {
      hc *= getDescription().hashCode();
    }

    if (getData() != null) {
      hc *= getData().hashCode();
    }

    return hc;
  }

  @Override
  public boolean equals(final Object o) {
    return compareTo(o) == 0;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("code", getCode());
    ts.append("description", getDescription());
    ts.append("data", getData());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwRequestStatus rs = new BwRequestStatus();

    rs.setCode(getCode());

    BwString desc = getDescription();
    if (desc != null) {
      rs.setDescription((BwString)desc.clone());
    }

    rs.setData(getData());

    return rs;
  }
}
