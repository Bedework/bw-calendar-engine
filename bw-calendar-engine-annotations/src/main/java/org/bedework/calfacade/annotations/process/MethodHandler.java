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

import org.bedework.calfacade.annotations.CloneForOverride;
import org.bedework.calfacade.annotations.ical.Immutable;
import org.bedework.util.misc.ToString;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

/** We create a list of these as we process the event. We tie together the
 * setter and getter methods so that only the setter needs to be annotated
 *
 * @author Mike DOuglass
 */
/**
 * @author douglm
 *
 * @param <T>
 */
public abstract class MethodHandler<T> implements Comparable<MethodHandler> {
  protected String forClass;

  protected AnnUtil annUtil;

  protected String ucFieldName; // no lower cased first char.
  protected String fieldName;

  protected TypeMirror fieldType;
  protected boolean basicType; // field type is int, boolean etc.

  protected boolean staticMethod;

  protected String methName;

  protected boolean setter;
  protected boolean getter;

  protected boolean immutable;

  protected boolean cloneForOverride;
  protected String cloneCollectionType;
  protected String cloneElementType;

  protected TypeMirror returnType;
  protected boolean returnsVoid;

  protected List<? extends VariableElement> pars;

  protected List<? extends TypeMirror> thrownTypes;

  protected ProcessingEnvironment env;
  protected Messager msg;

  // Points at other one of pair. Arranged so that setter is in map if present
  protected T setGet;

  /**
   * @param env the environment
   * @param annUtil utils
   * @param d the method
   */
  public MethodHandler(final ProcessingEnvironment env,
                       final AnnUtil annUtil,
                       final ExecutableElement d,
                       final ProcessState pstate) {
    this.env = env;
    this.annUtil = annUtil;
    msg = env.getMessager();
    forClass = pstate.currentClassName;

    staticMethod = d.getModifiers().contains(Modifier.STATIC);
    methName = d.getSimpleName().toString();
    getter = methName.startsWith("get");
    setter = methName.startsWith("set");

    immutable = d.getAnnotation(Immutable.class) != null;

    CloneForOverride cfo = d.getAnnotation(CloneForOverride.class);
    cloneForOverride = cfo != null;
    if (cloneForOverride) {
      cloneCollectionType = cfo.cloneCollectionType();
      cloneElementType = cfo.cloneElementType();
    }

    returnType = d.getReturnType();
    returnsVoid = env.getTypeUtils().getNoType(TypeKind.VOID).equals(returnType);

    pars = d.getParameters();
    thrownTypes = d.getThrownTypes();

    if ((setter) && (pars != null) && (pars.size() == 1)) {
      fieldType = pars.iterator().next().asType();
      basicType = fieldType.getKind().isPrimitive();
    }

    if (getter) {
      fieldType = returnType;
      basicType = returnType.getKind().isPrimitive();
    }

    if (setter || getter) {
      ucFieldName = methName.substring(3);
      fieldName = ucFieldName.substring(0, 1).toLowerCase() +
                  ucFieldName.substring(1);
    }
  }

  /**
   * @param thisPackage
   * @return List
   */
  public Set<String> getImports(final String thisPackage) {
    TreeSet<String> imports = new TreeSet<>();

    String cname = AnnUtil.getImportableClassName(returnType, thisPackage);

    if (cname != null) {
      imports.add(cname);
    }

    for (VariableElement par: pars) {
      String parType = AnnUtil.getImportableClassName(par.asType(), thisPackage);

      if (parType != null) {
        imports.add(parType);
      }
    }

    return imports;
  }

  /** Generate the method
   */
  public void generate() {
    annUtil.generateSignature(methName, pars, returnType, thrownTypes);

    if (getter && (pars.size() == 0)) {
      generateGet();
    } else if (setter && (pars.size() == 1)) {
      generateSet();
    } else {
      generateMethod();
    }
  }

  /**
   */
  public abstract void generateGet();

  /**
   */
  public abstract void generateSet();

  /**
   */
  public abstract void generateMethod();

  protected String makeCallGetter(final String objRef) {
    return AnnUtil.makeCallGetter(objRef, ucFieldName);
  }

  protected String makeCallSetter(final String objRef, final Object val) {
    return AnnUtil.makeCallSetter(objRef, ucFieldName, val);
  }

  /** Make a cal to this method
   *
   * @return String
   */
  protected String makeCall() {
    StringBuilder sb = new StringBuilder(methName);

    sb.append("(");

    boolean first = true;
    for (VariableElement par: pars) {
      if (!first) {
        sb.append(", ");
      }
      sb.append(par.getSimpleName());

      first = false;
    }
    sb.append(")");

    return sb.toString();
  }

  /**
   * @param ts to string object
   */
  public void toStringSegment(final ToString ts) {
    ts.append("forClass", forClass);
    ts.append("fieldName", fieldName);
    ts.append("fieldType", fieldType);
    ts.append("method", methName);
    ts.append("getter", getter);
    ts.append("setter", setter);
    ts.append("staticMethod", staticMethod);
    ts.append("basicType", basicType);
    ts.append("pars.size()", pars.size());
    for (VariableElement pd: pars) {
      ts.append("par", pd);
    }
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  public int compareTo(final MethodHandler that) {
    if (this == that) {
      return 0;
    }

    int res = methName.compareTo(that.methName);
    if (res != 0) {
      return res;
    }

    if (pars.size() < that.pars.size()) {
      return -1;
    }

    if (pars.size() > that.pars.size()) {
      return 1;
    }

    if (fieldType == null) {
      if (that.fieldType == null) {
        return 0;
      }

      return -1;
    } if (!fieldType.equals(that.fieldType))  {
      return 1;
    }

    return 0;
  }

  @Override
  public int hashCode() {
    return methName.hashCode();
  }

  /* We always use the compareTo method
   */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    return compareTo((MethodHandler)obj) == 0;
  }
}
