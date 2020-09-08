/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.convert.jscal;

import org.bedework.jsforj.JSRegistration;
import org.bedework.jsforj.JSTypeInfo;
import org.bedework.jsforj.JSValueFactory;
import org.bedework.jsforj.model.JSTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.bedework.calfacade.BwXproperty.getXpropInfo;
import static org.bedework.calfacade.BwXproperty.xBedeworkLocation;
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
 * User: mike Date: 7/26/20 Time: 00:14
 */
public class BwJSRegistration implements JSRegistration {
  private final static String registrationName =
          "bedework.org";

  // Type names for properties
  private final static Map<String, String> ptypes =
          new HashMap<>();

  // Type info for types
  private final static Map<String, JSTypeInfo> types =
          new HashMap<>();

  private final static Map<String, List<String>> validFor =
          new HashMap<>();

  private final static Map<String, List<String>> contains =
          new HashMap<>();

  public static final String typeBwLocation = "Bwlocation";

  static {
    //                        location fields

    ptype(getXpropInfo(xBedeworkLocation).jscalName,
          JSTypes.typeString);

    ptype(getXpropInfo(xBedeworkLocationAddr).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationRoom).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationAccessible).jscalName,
          JSTypes.typeBoolean);
    ptype(getXpropInfo(xBedeworkLocationSfield1).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationSfield2).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationGeo).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationStreet).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationCity).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationState).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationZip).jscalName,
          JSTypes.typeString);
    ptype(getXpropInfo(xBedeworkLocationLink).jscalName,
          JSTypes.typeString);

    type(typeBwLocation,
         true, // requiresType
         false, // valueList
         false, // propertyList
         null, // elementType
         true, // object
         BwLocationFactory.class); // factoryClass
  }

  private static void ptype(final String name,
                            final String type) {
    ptypes.put(name, type);
  }

  private static void type(final String typeName,
                           final boolean requiresType,
                           final boolean valueList,
                           final boolean propertyList,
                           final String[] elementType,
                           final boolean object,
                           final Class<? extends JSValueFactory> factoryClass) {
    types.put(typeName,
              new JSTypeInfo(typeName, requiresType, valueList, propertyList,
                             elementType, object, factoryClass));
  }

  @Override
  public String getRegistrationName() {
    return registrationName;
  }

  @Override
  public Set<String> propertyNames() {
    return ptypes.keySet();
  }

  @Override
  public String getType(final String propertyName) {
    return ptypes.get(propertyName);
  }

  /**
   *
   * @param name of type
   * @return type information - null if unknown type
   */
  public JSTypeInfo getTypeInfo(final String name) {
    return types.get(name);
  }
}
