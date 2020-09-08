/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.jscal;

import org.bedework.jsforj.impl.JSPropertyNames;
import org.bedework.jsforj.impl.values.JSValueImpl;

import com.fasterxml.jackson.databind.JsonNode;

import static org.bedework.calfacade.BwXproperty.getXpropInfo;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationAccessible;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationAddr;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationCity;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationGeo;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationLink;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationRoom;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationSfield1;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationSfield2;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationState;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationStreet;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocationZip;

/**
 * User: mike Date: 10/25/19 Time: 12:45
 */
public class BwLocationImpl extends JSValueImpl
        implements BwJSLocation {
  public BwLocationImpl(final String type,
                        final JsonNode node) {
    super(type, node);
  }

  @Override
  public void setUid(final String val) {
    setProperty(JSPropertyNames.uid, val);
  }

  @Override
  public String getUid() {
    return getStringProperty(JSPropertyNames.uid);
  }

  @Override
  public void setAddr(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationAddr).jscalName, val);
  }

  @Override
  public String getAddr() {
    return getStringProperty(getXpropInfo(xBedeworkLocationAddr).jscalName);
  }

  @Override
  public void setRoom(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationRoom).jscalName, val);
  }

  @Override
  public String getRoom() {
    return getStringProperty(getXpropInfo(xBedeworkLocationRoom).jscalName);
  }

  @Override
  public void setAccessible(final boolean val) {
    setProperty(getXpropInfo(xBedeworkLocationAccessible).jscalName,
                val);
  }

  @Override
  public boolean getAccessible() {
    final var val = getPropertyValue(
            getXpropInfo(xBedeworkLocationAccessible).jscalName);

    if (val == null) {
      return false;
    }

    return val.getBooleanValue();
  }

  @Override
  public void setSfield1(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationSfield1).jscalName, val);
  }

  @Override
  public String getSfield1() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationSfield1).jscalName);
  }

  @Override
  public void setSfield2(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationSfield2).jscalName, val);
  }

  @Override
  public String getSfield2() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationSfield2).jscalName);
  }

  @Override
  public void setGeo(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationGeo).jscalName, val);
  }

  @Override
  public String getGeo() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationGeo).jscalName);
  }

  @Override
  public void setStreet(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationStreet).jscalName, val);
  }

  @Override
  public String getStreet() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationStreet).jscalName);
  }

  @Override
  public void setCity(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationCity).jscalName, val);
  }

  @Override
  public String getCity() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationCity).jscalName);
  }

  @Override
  public void setState(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationState).jscalName, val);
  }

  @Override
  public String getState() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationState).jscalName);
  }

  @Override
  public void setZip(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationZip).jscalName, val);
  }

  @Override
  public String getZip() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationZip).jscalName);
  }

  @Override
  public void setLink(final String val) {
    setProperty(getXpropInfo(xBedeworkLocationLink).jscalName, val);
  }

  @Override
  public String getLink() {
    return getStringProperty(
            getXpropInfo(xBedeworkLocationLink).jscalName);
  }
}
