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

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.Modifier;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.ReferenceType;
import com.sun.mirror.type.TypeMirror;

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

  protected Collection<ParameterDeclaration> pars;

  protected Collection<ReferenceType> thrownTypes;

  protected AnnotationProcessorEnvironment env;
  protected Messager msg;

  // Points at other one of pair. Arranged so that setter is in map if present
  protected T setGet;

  /**
   * @param env
   * @param annUtil
   * @param d
   */
  public MethodHandler(final AnnotationProcessorEnvironment env,
                       final AnnUtil annUtil,
                       final MethodDeclaration d) {
    this.env = env;
    this.annUtil = annUtil;
    msg = env.getMessager();

    staticMethod = d.getModifiers().contains(Modifier.STATIC);
    methName = d.getSimpleName();
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
    returnsVoid = env.getTypeUtils().getVoidType().equals(returnType);

    pars = d.getParameters();
    thrownTypes = d.getThrownTypes();

    if ((setter) && (pars != null) && (pars.size() == 1)) {
      fieldType = pars.iterator().next().getType();
      basicType = fieldType instanceof PrimitiveType;
    }

    if (getter) {
      fieldType = returnType;
      basicType = returnType instanceof PrimitiveType;
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
    TreeSet<String> imports = new TreeSet<String>();

    String cname = AnnUtil.getImportableClassName(returnType, thisPackage);

    if (cname != null) {
      imports.add(cname);
    }

    for (ParameterDeclaration par: pars) {
      String parType = AnnUtil.getImportableClassName(par.getType(), thisPackage);

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
    for (ParameterDeclaration par: pars) {
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
   * @param sb
   */
  public void toStringSegment(final StringBuilder sb) {
    sb.append(fieldName);
    sb.append(", basic=");
    sb.append(basicType);
    sb.append(", method=");
    sb.append(methName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("Method{");
    toStringSegment(sb);

    return sb.toString();
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

    Iterator<ParameterDeclaration> it = that.pars.iterator();
    for (ParameterDeclaration pd: pars) {
      ParameterDeclaration thatPd = it.next();

      if (!pd.equals(thatPd)) {
        return 1;
      }
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
