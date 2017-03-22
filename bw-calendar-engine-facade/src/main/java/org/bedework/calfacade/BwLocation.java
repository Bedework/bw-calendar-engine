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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** The location of an <code>Event</code>
 *
 *  @version 1.0
 */
@Dump(elementName="location", keyFields={"uid"})
public class BwLocation extends BwEventProperty<BwLocation>
        implements CollatableEntity, Comparator<BwLocation>,
                   SizedEntity {
  /** Currently both address and subaddress have a
   * unique constraint. 
   * 
   * To avoid schema changes we are packing subfields into the
   * address field with a delimiter.
   * 
   * The fields in order are:
   *    addressField
   *    room number
   *    subField1
   *    subField2
   *    accessible flag "T"
   */
  public static final String roomDelimiter = "\t";

  private BwString address;
  private Splitter addressSplit;
  
  private static final int addrIndex = 0;
  private static final int roomIndex = 1;
  private static final int subf1Index = 2;
  private static final int subf2Index = 3;
  private static final int accessibleIndex = 4;
  
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
   * @param val the main address value
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
  @SuppressWarnings("unused")
  public void setAddressField(final String val) {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }
    
    addressSplit.setFld(addrIndex, val);
    address = addressSplit.getString(address);
  }

  /** Get the main address of the location for json output. This is
   * up to the room delimiter
   *
   * @return the main address of the location
   */
  @NoDump
  public String getAddressField() {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    return addressSplit.getFld(addrIndex);
  }

  /** Set the room part of the main address of the location. 
   *
   * @param val the room part of the location
   */
  public void setRoomField(final String val) {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    addressSplit.setFld(roomIndex, val);
    address = addressSplit.getString(address);
  }

  /** get the room part of the main address of the location.
   *
   * @return the room part of the location
   */
  @NoDump
  public String getRoomField() {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    return addressSplit.getFld(roomIndex);
  }

  /** Set the subfield 1 part of the main address of the location. 
   *
   * @param val the subfield 1 part of the location
   */
  public void setSubField1(final String val) {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    addressSplit.setFld(subf1Index, val);
    address = addressSplit.getString(address);
  }

  /** get the subfield 1 part of the main address of the location.
   *
   * @return the subfield 1 part of the location
   */
  @NoDump
  public String getSubField1() {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    return addressSplit.getFld(subf1Index);
  }

  /** Set the subfield 2 part of the main address of the location. 
   *
   * @param val the subfield 2 part of the location
   */
  public void setSubField2(final String val) {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    addressSplit.setFld(subf2Index, val);
    address = addressSplit.getString(address);
  }

  /** get the subfield 2 part of the main address of the location.
   *
   * @return the subfield 2 part of the location
   */
  @NoDump
  public String getSubField2() {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    return addressSplit.getFld(subf2Index);
  }

  /** Set the accessible part of the main address of the location. 
   *
   * @param val the accessible part of the location
   */
  public void setAccessible(final boolean val) {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    final String flag;
    if (val) {
      flag = "T";
    } else {
      flag = null;
    }
    addressSplit.setFld(accessibleIndex, flag);
    address = addressSplit.getString(address);
  }

  /** get the accessible part of the main address of the location.
   *
   * @return the accessible part of the location
   */
  @NoDump
  public boolean getAccessible() {
    if (addressSplit == null) {
      addressSplit = new Splitter(address);
    }

    return "T".equals(addressSplit.getFld(accessibleIndex));
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
  
  private static class Splitter {
    List<String> flds;
    
    Splitter(final BwString fld) {
      if ((fld != null) && (fld.getValue() != null)) {
        flds = new ArrayList<>(
                Arrays.asList(fld.getValue().split(roomDelimiter)));
      }
    }
    
    void setFld(final int i, final String val) {
      if (flds == null) {
        flds = new ArrayList<>(i + 1);
      }
      
      while (i > flds.size() - 1) {
        flds.add(null);
      }
      flds.set(i, val);
    }
    
    String getFld(final int i) {
      if (flds == null) {
        return null;
      }
      if (i >= flds.size()) {
        return null;
      }
      
      final String s = flds.get(i);
      
      if (s == null) {
        return null;
      }
      
      if (s.length() == 1) {
        return null;
      }
      
      return s;
    }
    
    public BwString getString(final BwString val) {
      if (val != null) {
        return new BwString(val.getLang(), toString());
      }
      
      return new BwString(null, toString());
    }
    
    public String toString() {
      if (flds == null) {
        return null;
      }
      
      String fld = null;
      for (final String s: flds) {
        if (fld != null) {
          fld += roomDelimiter;
        } else {
          fld = "";
        }
        
        if (s != null) {
          fld += s;
        }
      }
      
      return fld;
    }
  }
}
