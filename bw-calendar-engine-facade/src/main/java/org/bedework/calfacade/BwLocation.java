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
import org.bedework.calfacade.annotations.ical.IcalProperties;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.base.CollatableEntity;
import org.bedework.calfacade.base.SizedEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.FieldSplitter;
import org.bedework.calfacade.util.QuotaUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.vcard.Card;
import org.bedework.util.xml.FromXmlCallback;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

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
  public static final String fieldDelimiter = "\t";

  private BwString address;
  private Splitter addressSplit;
  
  private static final int addrIndex = 0;
  private static final int roomIndex = 1;
  private static final int subf1Index = 2;
  private static final int subf2Index = 3;
  private static final int accessibleIndex = 4;
  private static final int geouriIndex = 5; // See rfc5870
  
  private BwString subaddress;
  private Splitter subaddressSplit;
  private FieldSplitter keysSplit;

  private static final int streetIndex = 0;
  private static final int cityIndex = 1;
  private static final int stateIndex = 2;
  private static final int zipIndex = 3;
  private static final int alternateAddressIndex = 4;
  private static final int codeIndex = 5;
  private static final int keysIndex = 6; // Array delimited by newlines

  private String link;

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
  @IcalProperties({
          @IcalProperty(pindex = PropertyInfoIndex.ADDRESS),
          @IcalProperty(pindex = PropertyInfoIndex.LOC_ALL,
                  jname = "loc_all",
                  termsField = "loc_all_terms",
                  analyzed = true)
  })
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
    assignAddressField(addrIndex, val);
  }

  /** Get the main address of the location for json output. This is
   * up to the room delimiter
   *
   * @return the main address of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ADDRESS_FLD)
  public String getAddressField() {
    return fetchAddressSplit().getFld(addrIndex);
  }

  /** Set the room part of the main address of the location. 
   *
   * @param val the room part of the location
   */
  public void setRoomField(final String val) {
    assignAddressField(roomIndex, val);
  }

  /** get the room part of the main address of the location.
   *
   * @return the room part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ROOM_FLD)
  public String getRoomField() {
    return fetchAddressSplit().getFld(roomIndex);
  }

  /** Set the subfield 1 part of the main address of the location. 
   *
   * @param val the subfield 1 part of the location
   */
  public void setSubField1(final String val) {
    assignAddressField(subf1Index, val);
  }

  /** get the subfield 1 part of the main address of the location.
   *
   * @return the subfield 1 part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.SUB1_FLD)
  public String getSubField1() {
    return fetchAddressSplit().getFld(subf1Index);
  }

  /** Set the subfield 2 part of the main address of the location. 
   *
   * @param val the subfield 2 part of the location
   */
  public void setSubField2(final String val) {
    assignAddressField(subf2Index, val);
  }

  /** get the subfield 2 part of the main address of the location.
   *
   * @return the subfield 2 part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.SUB2_FLD)
  public String getSubField2() {
    return fetchAddressSplit().getFld(subf2Index);
  }

  /** Set the accessible part of the main address of the location. 
   *
   * @param val the accessible part of the location
   */
  public void setAccessible(final boolean val) {
    final String flag;
    if (val) {
      flag = "T";
    } else {
      flag = null;
    }
    assignAddressField(accessibleIndex, flag);
  }

  /** get the accessible part of the main address of the location.
   *
   * @return the accessible part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ACCESSIBLE_FLD)
  public boolean getAccessible() {
    final String fld = fetchAddressSplit().getFld(accessibleIndex);
    return "T".equals(fld);
  }

  /** Set the geouri part of the main address of the location. 
   *
   * @param val the geouri part of the location
   */
  public void setGeouri(final String val) {
    assignAddressField(geouriIndex, val);
  }

  /** get the geouri part of the main address of the location.
   *
   * @return the geouri part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.GEOURI_FLD)
  public String getGeouri() {
    return fetchAddressSplit().getFld(geouriIndex);
  }

  public void setStatus(final String val) {
    if (getAddress() == null) {
      setAddress(new BwString(val, null));
    } else {
      getAddress().setLang(val);
    }
  }

  /**
   * @return String
   */
  @IcalProperty(pindex = PropertyInfoIndex.STATUS)
  public String getStatus() {
    final BwString s = getAddress();
    if (s == null) {
      return null;
    }

    return s.getLang();
  }

  public static class KeyFld {
    private final String keyName;
    private String keyVal;

    public KeyFld(final String keyName, final String keyVal) {
      this.keyName = keyName;
      this.keyVal = keyVal;
    }

    public String getKeyName() {
      return keyName;
    }

    public void setKeyVal(final String val) {
      keyVal = val;
    }

    public String getKeyVal() {
      return keyVal;
    }

    public int hashCode() {
      return getKeyName().hashCode();
    }

    public boolean equals(final Object o) {
      if (!(o instanceof KeyFld)) {
        return false;
      }

      if (o == this) {
        return true;
      }

      return getKeyName().equals(((KeyFld)o).getKeyName());
    }
  }

  Function<KeyFld, String> keyFldToString = val -> {
    if (val == null) {
      return null;
    }

    return val.getKeyName() + ":" + val.getKeyVal();
  };

  public void setKeys(final List<KeyFld> vals) {
    final List<String> strVals = vals.stream()
                                     .map(keyFldToString)
                                     .collect(Collectors.<String> toList());

    fetchKeysSplit().setFlds(strVals);
    assignSubaddressField(keysIndex, fetchKeysSplit().getCombined());
  }

  Function<String, KeyFld> stringToKeyFld = val -> {
    if (val == null) {
      return null;
    }

    String[] vals = val.split(":");
    if (vals.length != 2) {
      throw new RuntimeException("Bad keys value: " + val);
    }
    return new KeyFld(vals[0], vals[1]);
  };

  /**
   * @return KeyFld
   */
  @IcalProperty(pindex = PropertyInfoIndex.LOC_KEYS_FLD,
          nested = true,
          jname = "locKeys")
  public List<KeyFld> getKeys() {
    final List<String> vals = fetchKeysSplit().getFlds();
    if (Util.isEmpty(vals)) {
      return null;
    }

    return vals.stream()
               .map(stringToKeyFld)
               .collect(Collectors.toList());
  }

  /**
   * Add the named key with the value.
   *
   * @param name of key - non null
   * @param val of key - non null
   */
  public void addKey(final String name, final String val) {
    assert name != null;
    assert val != null;

    List<KeyFld> keyFlds = getKeys();
    if (keyFlds == null) {
      keyFlds = new ArrayList<>();
    }

    keyFlds.add(new KeyFld(name, val));

    setKeys(keyFlds);
  }

  /**
   * Update the named key with the value. Will add if it is
   * not present
   * @param name of key - non null
   * @param val of key - non null
   */
  public void updKey(final String name, final String val) {
    assert name != null;
    assert val != null;

    List<KeyFld> keyFlds = getKeys();
    if (keyFlds == null) {
      keyFlds = new ArrayList<>();
    }
    KeyFld kf = null;

    for (final KeyFld lkf: keyFlds) {
      if (lkf.getKeyName().equals(name)) {
        kf = lkf;
        break;
      }
    }

    if (kf == null) {
      keyFlds.add(new KeyFld(name, val));
    } else {
      kf.setKeyVal(val);
    }

    setKeys(keyFlds);
  }

  /** Delete the named key from the key set (if it exists)
   *
   * @param name of key - non null
   */
  public void delKey(final String name) {
    assert name != null;

    final List<KeyFld> keyFlds = getKeys();
    if (keyFlds == null) {
      return;
    }

    KeyFld kf = null;

    for (final KeyFld lkf: keyFlds) {
      if (lkf.getKeyName().equals(name)) {
        kf = lkf;
        break;
      }
    }

    if (kf == null) {
      return;
    }

    keyFlds.remove(kf);

    setKeys(keyFlds);
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
  @JsonIgnore
  public BwString getSubaddress() {
    return subaddress;
  }

  /** Get the secondary address of the location as a string
   *
   * @return the secondary address of the location
   */
  @JsonIgnore
  @NoDump
  public String getSubaddressField() {
    if (getSubaddress() == null) {
      return null;
    }
    return getSubaddress().getValue();
  }

  /** Set the street part of the subaddress of the location. 
   *
   * @param val the street part of the location
   */
  public void setStreet(final String val) {
    assignSubaddressField(streetIndex, val);
  }

  /** get the street part of the sub address of the location.
   *
   * @return the street part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.STREET_FLD)
  public String getStreet() {
    return fetchSubaddressSplit().getFld(streetIndex);
  }

  /** Set the city part of the subaddress of the location. 
   *
   * @param val the city part of the location
   */
  public void setCity(final String val) {
    assignSubaddressField(cityIndex, val);
  }

  /** get the city part of the sub address of the location.
   *
   * @return the city part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.CITY_FLD)
  public String getCity() {
    return fetchSubaddressSplit().getFld(cityIndex);
  }

  /** Set the state part of the subaddress of the location. 
   *
   * @param val the state part of the location
   */
  public void setState(final String val) {
    assignSubaddressField(stateIndex, val);
  }

  /** get the state part of the sub address of the location.
   *
   * @return the state part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.STATE_FLD)
  public String getState() {
    return fetchSubaddressSplit().getFld(stateIndex);
  }

  /** Set the zip part of the subaddress of the location. 
   *
   * @param val the zip part of the location
   */
  public void setZip(final String val) {
    assignSubaddressField(zipIndex, val);
  }

  /** get the zip part of the sub address of the location.
   *
   * @return the zip part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ZIP_FLD)
  public String getZip() {
    return fetchSubaddressSplit().getFld(zipIndex);
  }

  /** Set the alternateAddress part of the subaddress of the location. 
   *
   * @param val the alternateAddress part of the location
   */
  public void setAlternateAddress(final String val) {
    assignSubaddressField(alternateAddressIndex, val);
  }

  /** get the alternateAddress part of the sub address of the location.
   *
   * @return the alternateAddress part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.ALTADDRESS_FLD)
  public String getAlternateAddress() {
    return fetchSubaddressSplit().getFld(alternateAddressIndex);
  }

  /** Set the code part of the subaddress of the location. 
   *
   * @param val the code part of the location
   */
  public void setCode(final String val) {
    assignSubaddressField(codeIndex, val);
  }

  /** get the code part of the sub address of the location.
   *
   * @return the code part of the location
   */
  @IcalProperty(pindex = PropertyInfoIndex.CODEIDX_FLD)
  public String getCode() {
    return fetchSubaddressSplit().getFld(codeIndex);
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
  void fixNames() {
    setColPath("locations", null);

    setHref(Util.buildPath(false, getColPath(), getUid()));
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
   *                   Restore callback
   * ==================================================================== */

  private static FromXmlCallback fromXmlCb;

  @NoDump
  public static FromXmlCallback getRestoreCallback() {
    if (fromXmlCb == null) {
      fromXmlCb = new FromXmlCallback();

      fromXmlCb.addSkips("byteSize",
                         "id",
                         "seq");

      fromXmlCb.addMapField("public", "publick");
    }

    return fromXmlCb;
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
    
    if (that == null) {
      return 1;
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
    ts.append("addressField", getAddressField());
    ts.append("roomField", getRoomField());
    ts.append("accessible", getAccessible());
    ts.append("subField1", getSubField1());
    ts.append("subField2", getSubField2());
    ts.append("geouri", getGeouri());
    ts.append("subaddress", getSubaddress());
    ts.append("street", getStreet());
    ts.append("city", getCity());
    ts.append("state", getState());
    ts.append("zip", getZip());
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

  /**
   * 
   * @return a string for use in the iCalendar LOCATION 
   */
  @NoDump
  @JsonIgnore
  @IcalProperty(pindex = PropertyInfoIndex.LOC_COMBINED_VALUES)
  public String getCombinedValues() {
    if ((value(getAddress()) == null) && (value(getSubaddress()) == null)) {
      return null;
    }
    
    final StringBuilder sb = new StringBuilder();

    sb.append(getAddressField());
    addCombined(sb, getRoomField());
    addCombined(sb, getCity());
    addCombined(sb, getState());
    addCombined(sb, getZip());
    
    //if (getAccessible()) {
    //  sb.append(" (accessible)");
    //}
    
    return sb.toString();
  }

  /**
   *
   * @return a Card object representing the location.
   */
  @NoDump
  @JsonIgnore
  public Card getCard() {
    final Card card = new Card();

    card.setName(getUid() + ".vcf");
    card.setUid(getUid());

    final StringBuilder sb = new StringBuilder(getAddressField());
    addCombined(sb, getRoomField());
    card.setAddress(null, sb.toString(), getStreet(), getCity(),
                    getState(), getZip(),
                    null);  // country

    if (getLink() != null) {
      card.setUrl(getLink());
    }

    if (getGeouri() != null) {
      try {
        card.setGeoUri(getGeouri());
      } catch (final IllegalArgumentException ignored) {
      }
    }

    card.setAccessible(getAccessible());

    return card;
  }
  
  private void addCombined(final StringBuilder sb, 
                             final String field) {
    if (field == null) {
      return;
    }

    sb.append(", ");
    sb.append(field);
  }
  
  private String value(final BwString val) {
    if (val == null) {
      return null;
    }
    
    return val.getValue();
  }
  
  private Splitter fetchAddressSplit() {
    if (addressSplit == null) {
      addressSplit = new Splitter(getAddress());
    }
    
    return addressSplit;
  }
  
  private void assignAddressField(final int index, final String val) {
    fetchAddressSplit().setFld(index, val);
    setAddress(fetchAddressSplit().getCombined(getAddress()));
  }

  private Splitter fetchSubaddressSplit() {
    if (subaddressSplit == null) {
      subaddressSplit = new Splitter(getSubaddress());
    }

    return subaddressSplit;
  }

  private void assignSubaddressField(final int index, final String val) {
    fetchSubaddressSplit().setFld(index, val);
    setSubaddress(fetchSubaddressSplit().getCombined(getSubaddress()));
  }

  private FieldSplitter fetchKeysSplit() {
    if (keysSplit == null) {
      keysSplit = new FieldSplitter("\b");
      keysSplit.setVal(fetchSubaddressSplit().getFld(keysIndex));
    }

    return keysSplit;
  }

  private static class Splitter extends FieldSplitter {
    Splitter(final BwString fld) {
      super(fieldDelimiter);

      if ((fld != null) && (fld.getValue() != null)) {
        setVal(fld.getValue());
      }
    }

    public BwString getCombined(final BwString val) {
      if (val != null) {
        val.setValue(getCombined());
        return val;
      }
      
      return new BwString(null, getCombined());
    }
  }
}
