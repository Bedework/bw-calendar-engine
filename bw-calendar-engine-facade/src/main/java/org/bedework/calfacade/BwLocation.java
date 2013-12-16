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
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;

import java.util.Comparator;

/** The location of an <code>Event</code>
 *
 *  @version 1.0
 */
@Dump(elementName="location", keyFields={"uid"})
public class BwLocation extends BwEventProperty<BwLocation>
        implements CollatableEntity, Comparator<BwLocation>,
                   SizedEntity {
  private BwString address;
  private BwString subaddress;
  private String link;

  /** Constructor
   *
   */
  public BwLocation() {
    super();
  }

  /**
   * @param val
   */
  public void setAddress(final BwString val) {
    address = val;
  }

  /** Get the main address of the location
   *
   * @return the main address of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ADDRESS)
  public BwString getAddress() {
    return address;
  }

  /**
   * @param val
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
   *                   EventProperty methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.BwEventProperty#getFinderKeyValue()
   */
  @Override
  @NoDump
  public BwString getFinderKeyValue() {
    return getAddress();
  }

  /* ====================================================================
   *                   CollatableEntity methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.CollatableEntity#getCollateValue()
   */
  @Override
  @NoDump
  public String getCollateValue() {
    if (getAddress().getValue() == null) {
      return "";
    }
    return getAddress().getValue();
  }

  /* ====================================================================
   *                   Action methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.BwDbentity#afterDeletion()
   */
  @Override
  public void afterDeletion() {
    addDeletedEntity(getAddress());
    addDeletedEntity(getSubaddress());
  }

  /** Size to use for quotas.
   *
   * @return int
   */
  @Override
  @NoDump
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
    ToString ts = new ToString(this);

    toStringSegment(ts);

    ts.append("uid", getUid());
    ts.append("address", getAddress());
    ts.append("subaddress", getSubaddress());
    ts.append("link", getLink());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwLocation loc = new BwLocation();

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
