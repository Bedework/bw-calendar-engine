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

import org.bedework.calfacade.BwPrincipal;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author Mike Douglass
 *
 */
public interface UpdateFromTimeZonesInfo extends Serializable {
  /** Creates list of unknown tzids.
   */
  public static class UnknownTimezoneInfo
      implements Comparable<UnknownTimezoneInfo>, Serializable {
    /**
     * @param owner
     * @param tzid
     */
    public UnknownTimezoneInfo(final BwPrincipal owner, final String tzid) {
      this.owner = owner;
      this.tzid = tzid;
    }

    /* owner of unknown tzid */
    private BwPrincipal owner;

    /* unknown tzid */
    private String tzid;

    /**
     * @param val the owner to set
     */
    public void setOwner(final BwPrincipal val) {
      owner = val;
    }

    /**
     * @return the owner
     */
    public BwPrincipal getOwner() {
      return owner;
    }

    /**
     * @param val the tzid to set
     */
    public void setTzid(final String val) {
      tzid = val;
    }

    /**
     * @return the tzid
     */
    public String getTzid() {
      return tzid;
    }

    @Override
    public int compareTo(final UnknownTimezoneInfo that) {
      if (this == that) {
        return 0;
      }

      int res = owner.compareTo(that.owner);
      if (res != 0) {
        return res;
      }

      return tzid.compareTo(that.tzid);
    }

    @Override
    public int hashCode() {
      return owner.hashCode() * tzid.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }

      return compareTo((UnknownTimezoneInfo)obj) == 0;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      sb.append("UnknownTimezoneInfo(owner=");
      sb.append(owner.getAccount());
      sb.append(", tzid=");
      sb.append(tzid);
      sb.append(")");

      return sb.toString();
    }
  }

  /** Number of events to be updated
   *
   * @return int
   */
  public int getTotalEventsToCheck();

  /** Number of events checked
   *
   * @return int
   */
  public int getTotalEventsChecked();

  /** Number of events updated
   *
   * @return int
   */
  public int getTotalEventsUpdated();

  /**
   * @return sorted list of unknown tzids.
   */
  public Collection<UnknownTimezoneInfo> getUnknownTzids();

  /**
   * @return list of events updated or needing updates.
   */
  public Collection<BwEventKey> getUpdatedList();
}
