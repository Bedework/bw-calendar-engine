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
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.base.CollatableEntity;
import org.bedework.calfacade.base.SizedEntity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Comparator;

/** The location of an <code>Event</code>
 *
 *  @version 1.0
 */
@Dump(elementName="location", keyFields={"uid"})
public class BwLocation extends BwEventProperty<BwLocation>
        implements CollatableEntity, Comparator<BwLocation>,
                   SizedEntity {
  /** To avoid schema changes we are packing a subfield into the
   * address field. Currently both address and subaddress have a
   * unique constraint. To accommodate room numbers we are putting
   * them on the end of the address field with a delimiter
   */
  public static final String roomDelimiter = "\t";

  private BwString address;
  private BwString subaddress;
  private String link;

  private String href;

  /** Constructor
   *
   */
  public BwLocation() {
    super();
  }

  /**
   * @param val the main address
   */
  public void setAddress(final BwString val) {
    address = val;
  }

  /** Get the main address of the location
   *
   * @return the main address of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ADDRESS)
  @JsonIgnore
  public BwString getAddress() {
    return address;
  }

  /** Set the building part of the main address of the location. This is
   * up to the room delimiter
   *
   * @param val the building part of the location
   */
  public void setAddressField(final String val) {
    if (val == null) {
      // Remove all
      setAddress(null);
      return;
    }

    BwString addr = getAddress();

    if (addr == null) {
      addr = new BwString(null, val);
      setAddress(addr);
      return;
    }

    final int pos = addr.getValue().lastIndexOf(roomDelimiter);

    if (pos < 0) {
      addr.setValue(val);
    } else {
      addr.setValue(
              val + addr.getValue().substring(pos));
    }
  }

  /** Get the main address of the location for json output. This is
   * up to the room delimiter
   *
   * @return the main address of the location
   */
  @NoDump
  public String getAddressField() {
    if (getAddress() == null) {
      return null;
    }

    final String val = getAddress().getValue();

    if ((val == null) || !val.contains(roomDelimiter)) {
      return val;
    }

    return val.substring(0, val.lastIndexOf(roomDelimiter));
  }

  /** Set the room part of the main address of the location. This is
   * after the room delimiter
   *
   * @param val the room part of the location
   */
  public void setRoomField(final String val) {
    if (val == null) {
      // Remove any room part
      if (getRoomField() == null) {
        return;
      }

      final BwString addr = getAddress();
      final int pos = addr.getValue().lastIndexOf(roomDelimiter);
      addr.setValue(addr.getValue().substring(0, pos));

      return;
    }

    BwString addr = getAddress();

    if (addr == null) {
      addr = new BwString(null, "");
      setAddress(addr);
    }

    final int pos = addr.getValue().lastIndexOf(roomDelimiter);

    if (pos < 0) {
      addr.setValue(
              addr.getValue() + BwLocation.roomDelimiter + val);
    } else {
      addr.setValue(
              addr.getValue().substring(0, pos + 1) + val);
    }
  }

  /** get the room part of the main address of the location for json output. This is
   * after the room delimiter
   *
   * @return the room part of the location
   */
  @NoDump
  public String getRoomField() {
    if (getAddress() == null) {
      return null;
    }

    final String val = getAddress().getValue();

    if ((val == null) || !val.contains(roomDelimiter)) {
      return null;
    }

    return val.substring(val.lastIndexOf(roomDelimiter) + 1);
  }

  /**
   * @param val the sub-address
   */
  @IcalProperty(pindex = PropertyInfoIndex.SUBADDRESS)
  public void setSubaddress(final BwString val) {
    subaddress = val;
  }

  /** Get the secondary address of the location
   *
   * @return the secondary address of the location
   */
  public BwString getSubaddress() {
    return subaddress;
  }

  /** Set the Location's URL
   *
   * @param link The new URL
   */
  public void setLink(final String link) {
    this.link = link;
  }

  /** Get the link for the location
   *
   * @return the link for the location
   */
  public String getLink() {
    return link;
  }

  /**
   * @return location with uid filled in.
   */
  public static BwLocation makeLocation() {
    return (BwLocation)new BwLocation().initUid();
  }

  /** Delete the address - this must be called rather than setting
   * the value to null.
   *
   */
  public void deleteAddress() {
    addDeletedEntity(getAddress());
    setAddress(null);
  }

  /** Delete the subaddress - this must be called rather than setting
   * the value to null.
   *
   */
  public void deleteSubaddress() {
    addDeletedEntity(getSubaddress());
    setSubaddress(null);
  }

  /* ====================================================================
   *                   FixNamesEntity methods
   * ==================================================================== */

  @Override
  public void fixNames(final BasicSystemProperties props,
                       final BwPrincipal principal) {
    if (getHref() != null) {
      return;
    }

    setColPath(props, principal, "locations", null);

    setHref(Util.buildPath(false, getColPath(), getUid()));
  }

  @Override
  public void setHref(final String val) {
    href = val;
  }

  @Override
  public String getHref(){
    return href;
  }

  /* ====================================================================
   *                   EventProperty methods
   * ==================================================================== */

  @Override
  @NoDump
  @JsonIgnore
  public BwString getFinderKeyValue() {
    return getAddress();
  }

  /* ====================================================================
   *                   CollatableEntity methods
   * ==================================================================== */

  @Override
  @NoDump
  @JsonIgnore
  public String getCollateValue() {
    if (getAddress().getValue() == null) {
      return "";
    }
    return getAddress().getValue();
  }

  /* ====================================================================
   *                   Action methods
   * ==================================================================== */

  @Override
  public void afterDeletion() {
    addDeletedEntity(getAddress());
    addDeletedEntity(getSubaddress());
  }

  @Override
  @NoDump
  @JsonIgnore
  public int getSize() {
    return super.length() +
           QuotaUtil.size(getAddress()) +
           QuotaUtil.size(getSubaddress()) +
           QuotaUtil.size(getLink());
  }

  public boolean updateFrom(final BwLocation ent) {
    boolean changed = false;

    if (!CalFacadeUtil.eqObjval(getAddress(), ent.getAddress())) {
      setAddress(ent.getAddress());
      changed = true;
    }

    if (!CalFacadeUtil.eqObjval(getSubaddress(), ent.getSubaddress())) {
      setSubaddress(ent.getSubaddress());
      changed = true;
    }

    if (!CalFacadeUtil.eqObjval(getLink(), ent.getLink())) {
      setLink(ent.getLink());
      changed = true;
    }

    return changed;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwLocation thisone, final BwLocation thatone) {
    if (thisone.equals(thatone)) {
      return 0;
    }

    return CalFacadeUtil.cmpObjval(thisone.getAddress().getValue(),
                                   thatone.getAddress().getValue());
  }

  @Override
  public int compareTo(final BwLocation that) {
    if (this == that) {
      return 0;
    }

    return CalFacadeUtil.cmpObjval(getUid(), that.getUid());
  }

  @Override
  public int hashCode() {
    return getUid().hashCode();
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    ts.append("uid", getUid());
    ts.append("address", getAddress());
    ts.append("subaddress", getSubaddress());
    ts.append("link", getLink());

    return ts.toString();
  }

  @Override
  public Object clone() {
    final BwLocation loc = new BwLocation();

    super.copyTo(loc);
    if (getAddress() != null) {
      loc.setAddress((BwString)getAddress().clone());
    }
    if (getSubaddress() != null) {
      loc.setSubaddress((BwString)getSubaddress().clone());
    }
    loc.setLink(getLink());

    return loc;
  }
}
