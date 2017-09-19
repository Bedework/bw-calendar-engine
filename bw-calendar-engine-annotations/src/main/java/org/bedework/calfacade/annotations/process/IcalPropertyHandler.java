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
package org.bedework.calfacade.annotations.process;

import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * @author douglm
 *
 */
public class IcalPropertyHandler {
  private final static String thisPackage = "org.bedework.calfacade.annotations.process";

  private ProcessState pstate;

  private PrintWriter pinfoOut;

  Set<String> imports = new TreeSet<>();

  private HashMap<PropertyInfoIndex, MergedIcalProperty> pinfos =
          new HashMap<>();

  private AnnUtil annUtil;

  /* There doesn't appear to be a way to modify an annotation object
   * so this is a copy of the object
   */
  private class MergedIcalProperty {
    PropertyInfoIndex pindex;

    String dbFieldName;

    String adderName;

    String jname;

    String fieldType;
    
    boolean nested;
    PropertyInfoIndex keyindex;

    boolean analyzed;

    boolean isCollectionType;

    boolean param;

    boolean reschedule;

    String presenceField;

    boolean required;
    boolean annotationRequired;

    boolean eventProperty;
    boolean todoProperty;
    boolean journalProperty;
    boolean freeBusyProperty;
    boolean timezoneProperty;
    boolean alarmProperty;
    boolean vavailabilityProperty;
    boolean availableProperty;

    MergedIcalProperty(final IcalProperty p,
                       final String fieldName,
                       final String fieldType,
                       final boolean isCollectionType) {
      pindex = p.pindex();
      dbFieldName = fieldName;
      this.fieldType = fieldType;

      if ((p.adderName() != null) &&
              (p.adderName().length() > 0)) {
        adderName = p.adderName();
      }

      if ((p.jname() != null) &&
              (p.jname().length() > 0)) {
        jname = p.jname();
      } else {
        jname = dbFieldName;
      }

      if ((p.presenceField() != null) &&
          (p.presenceField().length() > 0)) {
        presenceField = p.presenceField();
      }

      this.isCollectionType = isCollectionType;

      param = p.param();

      reschedule = p.reschedule();

      nested = p.nested();
      keyindex = p.keyindex();
      analyzed = p.analyzed();
      required = p.required();
      annotationRequired = p.annotationRequired();

      eventProperty = p.eventProperty();
      todoProperty = p.todoProperty();
      journalProperty = p.journalProperty();
      freeBusyProperty = p.freeBusyProperty();
      timezoneProperty = p.timezoneProperty();
      alarmProperty = p.alarmProperty();
      vavailabilityProperty = p.vavailabilityProperty();
      availableProperty = p.availableProperty();
    }

    boolean check(final IcalProperty p, final String fieldName,
                  final boolean isCollectionType) {
      if (!pindex.equals(p.pindex())) {
        annUtil.error("Mismatched indexes " + pindex +
                              ", " + p.pindex() +
                              " in class " + pstate.getCurrentClassName());
        return false;
      }

      if (!dbFieldName.equals(fieldName)) {
        annUtil.error("Mismatched field names " + pindex +
                              ", " + dbFieldName + ", " + fieldName +
                              " in class " + pstate
                .getCurrentClassName());
        return false;
      }

      if (this.isCollectionType != isCollectionType) {
        mismatched("method types");
        return false;
      }

      return true;
    }

    void merge(final IcalProperty p) {
      param = merge(param, p.param());

      nested = mergeWarn("nested", nested, p.nested());
      
      if (keyindex != p.keyindex()) {
        mismatched("keyindex");
        if (p.keyindex() != PropertyInfoIndex.UNKNOWN_PROPERTY) {
          keyindex = p.keyindex();
        }
      } 
      
      analyzed = mergeWarn("analyzed", analyzed, p.analyzed());
      required = merge(required, p.required());
      annotationRequired = merge(annotationRequired, p.annotationRequired());
      reschedule = merge(reschedule, p.reschedule());

      eventProperty = merge(eventProperty, p.eventProperty());
      todoProperty = merge(todoProperty, p.todoProperty());
      journalProperty = merge(journalProperty, p.journalProperty());
      freeBusyProperty = merge(freeBusyProperty, p.freeBusyProperty());
      timezoneProperty = merge(timezoneProperty, p.timezoneProperty());
      alarmProperty = merge(alarmProperty, p.alarmProperty());
      vavailabilityProperty = merge(vavailabilityProperty, p.vavailabilityProperty());
      availableProperty = merge(availableProperty, p.availableProperty());
    }

    private boolean mergeWarn(final String name, 
                              final boolean thisval, final boolean thatval) {
      if (thisval != thatval) {
        mismatched(name);
      }
      
      return thisval || thatval;
    }

    private boolean merge(final boolean thisval, final boolean thatval) {
      return thisval || thatval;
    }
    
    private void mismatched(final String name) {
      annUtil.warn("Mismatched " + name +
                           " values - setting to true " + pindex +
                           ", " + dbFieldName +
                           " in class " +
                           pstate.getCurrentClassName());
    }
  }

  /**
   * @param pstate
   */
  public IcalPropertyHandler(final ProcessState pstate) {
    this.pstate = pstate;
  }

  /**
   * @param annUtil for messaging etc
   * @param env processing env
   * @param ip the property
   * @param d the element
   * @return boolean true for OK
   */
  public boolean property(final AnnUtil annUtil,
                          final ProcessingEnvironment env,
                          final IcalProperty ip,
                          final ExecutableElement d) {
    try {
      this.annUtil = annUtil;
      
      if (pinfoOut == null) {
        openPinfo(env);
      }

      String methName = d.getSimpleName().toString();

      boolean getter = methName.startsWith("get");
      boolean setter = methName.startsWith("set");
      if (!getter && !setter) {
        annUtil.error("Annotation must be applied to a setter or getter. " +
                              "Found on method " + methName +
                              " in class " + pstate
                .getCurrentClassName());
        return false;
      }

      List<? extends VariableElement> pars = d.getParameters();
      TypeMirror fldDcl;

      if (setter) {
        // Only 1 parameter
        if (pars.size() != 1) {
          annUtil.error("Expect only 1 parameter for setter " +
                                d.getSimpleName() +
                                " in class " + pstate
                  .getCurrentClassName());
          return false;
        }

        VariableElement par = pars.iterator().next();
        fldDcl = par.asType();
      } else {
        // No parameters
        if ((pars != null) && (pars.size() > 0)) {
          annUtil.error("No parameters allowed for getter " +
                                d.getSimpleName() +
                                " in class " + pstate
                  .getCurrentClassName());
          return false;
        }

        fldDcl = d.getReturnType();
      }

      boolean isCollectionType = ProcessState.isCollection(fldDcl);

      String fieldName;

      if (ip.dbFieldName().length() > 0) {
        fieldName = ip.dbFieldName();
      } else {
        fieldName = methName.substring(3, 4).toLowerCase() + methName.substring(4);
      }

      String fieldType = null;

      fieldType = AnnUtil.getImportableClassName(fldDcl, thisPackage);

      if (fieldType != null) {
        imports.add(fieldType);
      }

      fieldType = AnnUtil.fixName(fldDcl.toString());

      int bi = fieldType.indexOf('<');
      if (bi > 0) {
        fieldType = fieldType.substring(0, bi);
      }

      //env.getMessager().printNotice("*** " + fieldName + ": " + par.getType() +
      //                            " fixed: " + fieldType);

      MergedIcalProperty mip = pinfos.get(ip.pindex());
      if (mip == null) {
        pinfos.put(ip.pindex(), new MergedIcalProperty(ip, fieldName,
                                                       fieldType,
                                                       isCollectionType));
        return true;
      }

      /* Already got one. Ensure valid */
      if (!mip.check(ip, fieldName,
                     isCollectionType)) {
        return false;
      }

      mip.merge(ip);

      return true;
    } catch (Throwable t) {
      Messager msg = env.getMessager();
      t.printStackTrace();
      msg.printMessage(Kind.ERROR,
                       "Exception: " + t.getMessage());
      return false;
    }
  }

  /**
   * @param env
   * @return boolean true for ok
   */
  public boolean closePinfo(final ProcessingEnvironment env) {
    try {
      if ((pinfos == null) || pinfos.isEmpty()) {
        return true;
      }
      startPinfo(env);

      SortedMap<String, PropertyInfoIndex> pixNames =
              new TreeMap<>();

      for (PropertyInfoIndex ipe: PropertyInfoIndex.values()) {
        pixNames.put(ipe.name(), ipe);
      }

      /* Now in sort order... */
      List<PropertyInfoIndex> sorted =
              new ArrayList<>(pixNames.values());

      for (PropertyInfoIndex ipe: sorted) {
        MergedIcalProperty mip = pinfos.get(ipe);
        if (mip == null) {
          continue;
        }

        emit(env, mip);
        pixNames.remove(ipe.name());
      }

      pinfoOut.println("  }");
      pinfoOut.println();

      pinfoOut.println("  /** An array of property indexes required for valid events */");
      pinfoOut.println("  public static Set<PropertyInfoIndex> requiredPindexes = ");
      pinfoOut.println("          new TreeSet<>();");
      pinfoOut.println();
      pinfoOut.println("  static {");

      for (PropertyInfoIndex ipe: sorted) {
        MergedIcalProperty mip = pinfos.get(ipe);
        if ((mip == null) || !mip.required) {
          continue;
        }

        pinfoOut.println("    requiredPindexes.add(PropertyInfoIndex." +
                                 ipe.name() + ");");
      }

      pinfoOut.println("  }");
      pinfoOut.println();

      pinfoOut.println("  /** An array of property indexes required for valid annotations */");
      pinfoOut.println("  public static Set<PropertyInfoIndex> requiredAnnotationPindexes = ");
      pinfoOut.println("          new TreeSet<>();");
      pinfoOut.println();
      pinfoOut.println("  static {");

      for (PropertyInfoIndex ipe: sorted) {
        MergedIcalProperty mip = pinfos.get(ipe);
        if ((mip == null) || !mip.annotationRequired) {
          continue;
        }

        pinfoOut.println("    requiredAnnotationPindexes.add(PropertyInfoIndex." +
                                 ipe.name() + ");");
      }

      pinfoOut.println("  }");
      pinfoOut.println();

      pinfoOut.println("  /** An array of unreferenced property indexes */");
      pinfoOut.println("  PropertyInfoIndex[] unreferencedPindexes = {");
      for (PropertyInfoIndex ipe: pixNames.values()) {
        pinfoOut.println("    PropertyInfoIndex." + ipe.name() + ",");
      }
      pinfoOut.println("  };");
      pinfoOut.println();

      pinfoOut.println("  /** Get entry given an index");
      pinfoOut.println("   * @param pindex");
      pinfoOut.println("   * @return BwIcalPropertyInfoEntry");
      pinfoOut.println("   */");
      pinfoOut.println("  public static BwIcalPropertyInfoEntry getPinfo(PropertyInfoIndex pindex) {");
      pinfoOut.println("    return info.get(pindex);");
      pinfoOut.println("  }");

      pinfoOut.println();

      pinfoOut.println("  private static void addPinfo(BwIcalPropertyInfoEntry pinfo) {");
      pinfoOut.println("    BwIcalPropertyInfoEntry pinfo1 = info.put(pinfo.getPindex(), pinfo);");
      pinfoOut.println("    if (pinfo1 != null) {");
      pinfoOut.println("      throw new RuntimeException(\"Duplicate index \" + pinfo.getPindex());");
      pinfoOut.println("    }");

      //  propertyInfoByPname.put(pinfo.name, pinfo);
      pinfoOut.println("  }");

      pinfoOut.println("}");

      pinfoOut.close();

      pinfoOut = null;

      return true;
    } catch (Throwable t) {
      Messager msg = env.getMessager();
      t.printStackTrace();
      msg.printMessage(Kind.ERROR,
                       "Exception: " + t.getMessage());
      return false;
    }
  }

  private static class PinfoField {
    String type;
    String name;
    String comment;
    boolean first;
    boolean last;

    PinfoField(final String type, final String name) {
      this.type = type;
      this.name = name;
    }

    PinfoField(final String type, final String name,
               final String preceding) {
      this.type = type;
      this.name = name;
      this.comment = preceding;
    }

    PinfoField(final String type, final String name,
               final boolean first, final boolean last) {
      this.type = type;
      this.name = name;
      this.first = first;
      this.last = last;
    }
  }

  /* Same order as parameters of class */
  private static final PinfoField[] pinfoFields = {
          new PinfoField("PropertyInfoIndex", "pindex", true, false),
          new PinfoField("String", "dbFieldName"),
          new PinfoField("String", "adderName"),
          new PinfoField("String", "jname"),
          new PinfoField("Class", "fieldType"),
          new PinfoField("boolean", "nested", "True for nested types"),
          new PinfoField("PropertyInfoIndex", "keyindex", "!= UNKNOWN_PROPERTY for indexed values"),
          new PinfoField("boolean", "analyzed", "True for analyzed types"),
          new PinfoField("String", "presenceField", "field we test for presence"),
          new PinfoField("boolean", "param", "It's a parameter"),
          new PinfoField("boolean", "required", "Required for a valid event"),
          new PinfoField("boolean", "annotationRequired", "Required for a valid annotation"),
          new PinfoField("boolean", "reschedule",
                         "True if changing this forces a reschedule"),
          new PinfoField("boolean", "multiValued", "Derived during generation"),
          new PinfoField("boolean", "eventProperty"),
          new PinfoField("boolean", "todoProperty"),
          new PinfoField("boolean", "journalProperty"),
          new PinfoField("boolean", "freeBusyProperty"),
          new PinfoField("boolean", "timezoneProperty"),
          new PinfoField("boolean", "alarmProperty"),
          new PinfoField("boolean", "vavailabilityProperty"),
          new PinfoField("boolean", "availableProperty", false, true),
  };

  private boolean emit(final ProcessingEnvironment env,
                       final MergedIcalProperty ip) throws Throwable {

    pinfoOut.print("    addPinfo(new BwIcalPropertyInfoEntry(PropertyInfoIndex.");
    pinfoOut.print(ip.pindex.name());
    pinfoOut.println(",");

    String parIndent = "                                         ";

    makePar(parIndent, quote(ip.dbFieldName), "dbFieldName");

    makePar(parIndent, quote(ip.adderName), "adderName");

    makePar(parIndent, quote(ip.jname), "jname");

    if (ip.fieldType == null) {
      makePar(parIndent, "null", "fieldType");
    } else {
      makePar(parIndent, ip.fieldType + ".class", "fieldType");
    }

    makePar(parIndent, ip.nested, "nested");

    makePar(parIndent, "PropertyInfoIndex." + ip.keyindex.name(), "keyindex");

    makePar(parIndent, ip.analyzed, "analyzed");

    makePar(parIndent, quote(ip.presenceField), "presenceField");

    makePar(parIndent, ip.param, "param");

    makePar(parIndent, ip.required, "required");

    makePar(parIndent, ip.annotationRequired, "annotationRequired");

    makePar(parIndent, ip.reschedule, "reschedule");

    makePar(parIndent, ip.isCollectionType, "multiValued");

    makePar(parIndent, ip.eventProperty, "event");

    makePar(parIndent, ip.todoProperty, "todo");

    makePar(parIndent, ip.journalProperty, "journal");

    makePar(parIndent, ip.freeBusyProperty, "freebusy");

    makePar(parIndent, ip.timezoneProperty, "timezone");

    makePar(parIndent, ip.alarmProperty, "alarm");

    makePar(parIndent, ip.vavailabilityProperty, "vavailability");

    makePar(parIndent, String.valueOf(ip.availableProperty), "available", true);

    pinfoOut.println();

    return true;
  }

  private String quote(final String val) {
    if (val == null) {
      return "null";
    }
    return "\"" + val + "\"";
  }

  private void makePar(final String parIndent, final boolean val,
                       final String commentVal) {
    makePar(parIndent, String.valueOf(val), commentVal, false);
  }

  private void makePar(final String parIndent, final String val,
                       final String commentVal) {
    makePar(parIndent, val, commentVal, false);
  }

  private void makePar(final String parIndent, final String val,
                       final String commentVal, final boolean last) {
    pinfoOut.print(parIndent);
    pinfoOut.print(val);

    if (last) {
      pinfoOut.print("));  // ");
    } else {
      pinfoOut.print(",    // ");
    }
    pinfoOut.println(commentVal);
  }

  private void openPinfo(final ProcessingEnvironment env) throws Throwable {
    JavaFileObject fileObj = env.getFiler().
            createSourceFile("org.bedework.calfacade.ical." +
                                     "BwIcalPropertyInfo");
    pinfoOut = new PrintWriter(fileObj.openOutputStream());
  }

  private void startPinfo(final ProcessingEnvironment env) throws Throwable {
    imports.add("org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex");
    imports.add("java.io.Serializable");
    imports.add("java.util.HashMap");
    imports.add("java.util.Set");
    imports.add("java.util.TreeSet");

    pinfoOut.println("/* Auto generated file - do not edit ");
    pinfoOut.println(" */");
    pinfoOut.println("package org.bedework.calfacade.ical;");
    pinfoOut.println();

    for (String imp: imports) {
      pinfoOut.println("import " + imp + ";");
    }

    pinfoOut.println();
    pinfoOut.println("/** This class is auto generated");
    pinfoOut.println(" *");
    pinfoOut.println(" */");
    pinfoOut.println("public class BwIcalPropertyInfo implements Serializable {");
    pinfoOut.println("  /** This class is auto generated");
    pinfoOut.println("   * It provides information about bedework properties and");
    pinfoOut.println("   * their relationship to icalendar properties");
    pinfoOut.println("   */");
    pinfoOut.println("  public static class BwIcalPropertyInfoEntry implements Serializable {");

    /* Emit fields */
    for (PinfoField pif: pinfoFields) {
      if (pif.comment != null) {
        pinfoOut.println("    /* " + pif.comment + " */");
      }
      pinfoOut.println("    private " + pif.type + " " + pif.name + ";");
    }
    pinfoOut.println();

    /* Emit javadoc */
    pinfoOut.println("    /**");

    for (PinfoField pif: pinfoFields) {
      if (pif.comment != null) {
        pinfoOut.println("     * @param " + pif.name + "  " + pif.comment);
        continue;
      }
      
      pinfoOut.println("     * @param " + pif.name);
    }

    pinfoOut.println("     */");

    /* Emit parameters */
    for (PinfoField pif: pinfoFields) {
      if (pif.first) {
        pinfoOut.println("    public BwIcalPropertyInfoEntry(" +
                                 pif.type + " " + pif.name + ",");
      } else if (pif.last) {
        pinfoOut.println("                                   " +
                                 pif.type + " " + pif.name + ") {");
      } else {
        pinfoOut.println("                                   " +
                                 pif.type + " " + pif.name + ",");
      }
    }

    /* Set fields */
    for (PinfoField pif: pinfoFields) {
      pinfoOut.println("      this." + pif.name + " = " + pif.name + ";");
    }

    pinfoOut.println("    }");
    pinfoOut.println();

    /* Generate getter methods */
    for (PinfoField pif: pinfoFields) {
      makeGetter(pif);
    }

    pinfoOut.println("  }");

    pinfoOut.println("  private static HashMap<PropertyInfoIndex, BwIcalPropertyInfoEntry> info = ");
    pinfoOut.println("          new HashMap<>();");
    pinfoOut.println();
    pinfoOut.println("  static {");
  }

  private void makeGetter(final PinfoField pif) {
    pinfoOut.println("    /**");
    pinfoOut.print("     * @return ");
    pinfoOut.println(pif.type);
    pinfoOut.println("     */");
    pinfoOut.print("    public ");
    pinfoOut.print(pif.type);
    pinfoOut.print(" get");
    pinfoOut.print(pif.name.substring(0, 1).toUpperCase());
    pinfoOut.print(pif.name.substring(1));
    pinfoOut.println("() {");

    pinfoOut.print("      return ");
    pinfoOut.print(pif.name);
    pinfoOut.println(";");
    pinfoOut.println("    }");
    pinfoOut.println();
  }
}
