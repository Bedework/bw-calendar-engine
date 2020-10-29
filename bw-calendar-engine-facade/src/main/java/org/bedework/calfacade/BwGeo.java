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
import org.bedework.util.misc.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/** A geo value in bedework.
 *
 *  @version 1.0
 */
@Dump(elementName="geo", keyFields={"latitude", "longitude"})
public class BwGeo extends DumpEntity<BwGeo>
      implements Comparable<BwGeo>, Serializable {
  private BigDecimal latitude;
  private BigDecimal longitude;

  /** Constructor
   */
  public BwGeo() {
    super();
  }

  /**
   * @param latitude BigDecimal
   * @param longitude BigDecimal
   */
  public BwGeo(final BigDecimal latitude, final BigDecimal longitude) {
    this.latitude = latitude;
    this.longitude = longitude;
  }

  /** Set the latitude
   *
   * @param val    BigDecimal latitude
   */
  public void setLatitude(final BigDecimal val) {
    latitude = val;
  }

  /** Get the latitude
   *
   *  @return BigDecimal   latitude
   */
  public BigDecimal getLatitude() {
    return latitude;
  }

  /** Set the longitude
   *
   * @param val    BigDecimal longitude
   */
  public void setLongitude(final BigDecimal val) {
    longitude = val;
  }

  /** Get the longitude
   *
   *  @return BigDecimal   longitude
   */
  public BigDecimal getLongitude() {
    return longitude;
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  public int compareTo(final BwGeo that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    final int res = CalFacadeUtil.cmpObjval(getLatitude(),
                                            that.getLatitude());

    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpObjval(getLongitude(), that.getLongitude());
  }

  @Override
  public int hashCode() {
    int hc = 13;

    if (getLatitude() != null) {
      hc *= getLatitude().hashCode();
    }

    if (getLongitude() != null) {
      hc *= getLongitude().hashCode();
    }

    return hc;
  }

  @Override
  public boolean equals(final Object o) {
    if (!(o instanceof BwGeo)) {
      return false;
    }

    return compareTo((BwGeo)o) == 0;
  }

  @Override
  public String toString() {
    final var ts = new ToString(this);

    ts.append("latitude", getLatitude());
    ts.append("longitude", getLongitude());

    return ts.toString();
  }

  @Override
  public Object clone() {
    return new BwGeo(getLatitude(), getLongitude());
  }
}
