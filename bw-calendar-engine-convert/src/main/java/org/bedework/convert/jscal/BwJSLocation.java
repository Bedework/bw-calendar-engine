package org.bedework.convert.jscal;

import org.bedework.jsforj.model.values.JSValue;

/**
 * User: mike Date: 10/25/19 Time: 12:46
 */
public interface BwJSLocation extends JSValue {
  void setUid(String val);

  String getUid();

  void setAddr(String val);

  String getAddr();

  void setRoom(String val);

  String getRoom();

  void setAccessible(boolean val);

  boolean getAccessible();

  void setSfield1(String val);

  String getSfield1();

  void setSfield2(String val);

  String getSfield2();

  void setGeo(String val);

  String getGeo();

  void setStreet(String val);

  String getStreet();

  void setCity(String val);

  String getCity();

  void setState(String val);

  String getState();

  void setZip(String val);

  String getZip();

  void setLink(String val);

  String getLink();
}
