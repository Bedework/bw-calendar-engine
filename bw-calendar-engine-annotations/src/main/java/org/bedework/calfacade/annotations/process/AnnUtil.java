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

import java.io.FileReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.Collection;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.ReferenceType;
import com.sun.mirror.type.TypeMirror;

/** Utility methods for annotations
 *
 * @author Mike DOuglass
 */
public class AnnUtil {
  //private AnnotationProcessorEnvironment env;
  private String className;

  private PrintWriter out;

  private LineNumberReader templateRdr;

  /** We use a template file which has code insertion points marked by lines
   * starting with "++++".
   *
   * @param env
   * @param className
   * @param templateName
   * @param outFileName
   * @throws Throwable
   */
  public AnnUtil(final AnnotationProcessorEnvironment env,
                 final String className,
                 final String templateName,
                 final String outFileName) throws Throwable {
    //this.env = env;
    this.className = className;
    templateRdr = new LineNumberReader(new FileReader(templateName));
    out = env.getFiler().createSourceFile(outFileName);
  }

  /** Close readers/writers
   *
   * @throws Throwable
   */
  public void close() throws Throwable {
    if (templateRdr != null) {
      templateRdr.close();
    }

    if (out != null) {
      out.close();
    }
  }

  /** Emit a section of template up to a delimiter or to end of file.
   *
   * @return true if read delimiter, false for eof.
   * @throws Throwable
   */
  public boolean emitTemplateSection() throws Throwable {
    for (;;) {
      String ln = templateRdr.readLine();

      if (ln == null) {
        return false;
      }

      if (ln.startsWith("++++")) {
        return true;
      }

      out.println(ln);
    }
  }

  /** Generate a call to a getter.
   *
   * @param objRef - the reference to the getters class
   * @param ucFieldName - name of field wit first char upper cased
   * @return String call to getter
   */
  public static String makeCallGetter(final String objRef,
                                      final String ucFieldName) {
    StringBuilder sb = new StringBuilder(objRef);
    sb.append(".");
    sb.append("get");
    sb.append(ucFieldName);
    sb.append("()");

    return sb.toString();
  }

  /** Make a call to a setter
   *
   * @param objRef - the reference to the getters class
   * @param ucFieldName - name of field wit first char upper cased
   * @param val - represents the value
   * @return String call to setter
   */
  public static String makeCallSetter(final String objRef,
                                      final String ucFieldName,
                                      final Object val) {
    StringBuilder sb = new StringBuilder(objRef);
    sb.append(".");
    sb.append("set");
    sb.append(ucFieldName);
    sb.append("(");
    sb.append(val);
    sb.append(")");

    return sb.toString();
  }

  /**
   * @param methName
   * @param pars
   * @param returnType
   * @param thrownTypes
   */
  public void generateSignature(final String methName,
                                final Collection<ParameterDeclaration> pars,
                                final TypeMirror returnType,
                                final Collection<ReferenceType> thrownTypes) {
    /* Generate some javadoc first.
     */
    println("  /* (non-Javadoc)");
    prntncc("   * @see ", className, "#", methName, "(");

    int sz = pars.size();
    int i = 0;

    for (ParameterDeclaration par: pars) {
      out.print(nonGeneric(par.getType().toString()));

      i++;
      if (i < sz) {
        out.print(", ");
      }
    }

    prntlns(")",
            "   */");

    String rType = getClassName(returnType);

    //BwEvent.ProxiedFieldIndex pfi = BwEvent.ProxiedFieldIndex.valueOf(
    //                       "pfi" + ucFieldName);

    //env.getMessager().printNotice("ProxyMethod: " +
    //                              methName + " - " +
    //                              rType); // + " " + (pfi.ordinal()));


    /* Use a stringbuilder to get length of first part. */
    StringBuilder sb = new StringBuilder();

    sb.append("  public ");
    sb.append(rType);
    sb.append(" ");
    sb.append(methName);
    sb.append("(");
    out.print(sb.toString());

    int padlen = sb.length();
    sb = new StringBuilder();
    for (int lni = 0; lni < padlen; lni++) {
      sb.append(" ");
    }

    i = 0;

    for (ParameterDeclaration par: pars) {
      prntncc(fixName(par.getType().toString()), " ", par.getSimpleName());

      i++;
      if (i < sz) {
        out.println(", ");
        out.print(sb.toString());
      }
    }

    out.print(")");

    if (thrownTypes.size() > 0) {
      out.println();
      out.print("        throws ");
      boolean first = true;
      for (ReferenceType rt: thrownTypes) {
        if (!first) {
          out.print(", ");
        }

        out.print(fixName(rt.toString()));
      }
    }

    out.println(" {");
  }

  /**
   * @param tm
   * @return String
   */
  public static String getClassName(final TypeMirror tm) {
    if (tm instanceof ClassType) {
      ClassType ct = (ClassType)tm;

      return fixName(ct.getDeclaration().getSimpleName());
    }

    return fixName(tm.toString());
  }

  /** Return a name we might need to import or null.
   *
   * @param tm
   * @param thisPackage
   * @return String
   */
  public static String getImportableClassName(final TypeMirror tm,
                                       final String thisPackage) {
    /*
    if (tm instanceof ClassType) {
      ClassType ct = (ClassType)tm;

      String className = ct.getDeclaration().getQualifiedName();

      if (samePackage(thisPackage, className)) {
        return null;
      }

      return className;
    } else {
      env.getMessager().printNotice("getImportableClassName: " +
                                    tm.getClass().getName() +
                                    " " + tm.toString());
    }*/
    if (!(tm instanceof PrimitiveType)) {
      String className = nonGeneric(tm.toString());

      if ("void".equals(className)) {
        return null;
      }

      if (className.startsWith("java.lang.")) {
        return null;
      }

      if (samePackage(thisPackage, className)) {
        return null;
      }

      return className;
    }

    return null;
  }

  /**
   * @param str
   * @return String
   */
  public static String fixName(String str) {
    if (str == null) {
      return null;
    }

    /* Has to be  a better way than this */

    str = str.replaceAll("java\\.util\\.", "");
    str = str.replaceAll("java\\.lang\\.", "");
    str = str.replaceAll("org\\.bedework\\.calfacade\\.Bw", "Bw");
    str = str.replaceAll("org\\.bedework\\.calfacade\\.base\\.Bw", "Bw");
    str = str.replaceAll("org\\.bedework\\.calfacade\\.wrappers\\.Bw", "Bw");
    //if (str.startsWith("java.util.")) {
    //  return str.substring("java.util.".length());
    //}

    //if (str.startsWith("java.lang.")) {
    //  return str.substring("java.lang.".length());
    //}

    //if (str.startsWith("org.bedework.calfacade.BW")) {
    //  return str.substring("org.bedework.calfacade.".length());
    //}

    return str;
  }

  /** Return the non-generic type (class without the type parameters) for the
   * given type string
   *
   * <p>Note: ClassDeclaration.getQualifiedName() does this.
   *
   * @param type
   * @return String generic type name
   */
  public static String nonGeneric(final String type) {
    if (!type.endsWith(">")) {
      return type;
    }

    int pos = type.indexOf("<");
    return type.substring(0, pos);
  }

  /** ClassDeclaration.getPackage() could be useful here
   *
   * @param thisPackage
   * @param thatClass
   * @return boolean
   */
  public static boolean samePackage(final String thisPackage,
                                    final String thatClass) {
    //env.getMessager().printNotice("samePackage: " + thisPackage + " " + thatClass);

    if (!thatClass.startsWith(thisPackage)) {
      return false;
    }

    if (thatClass.charAt(thisPackage.length()) != '.') {
      return false;
    }

    return thatClass.indexOf(".", thisPackage.length() + 1) < 0;
  }

  /**
   * @param tm
   * @return boolean
   */
  public static boolean isCollection(final TypeMirror tm) {
    if (tm instanceof ClassType) {
      ClassType ct = (ClassType)tm;

      Collection<InterfaceType> sis = ct.getSuperinterfaces();
      for (InterfaceType it: sis) {
        if (ProcessState.isCollection(it)) {
          return  true;
        }

        return false;
      }
    }

    return ProcessState.isCollection(tm);
  }

  /**
   * @param val
   */
  public void prntncc(final String... val) {
    for (String ln: val) {
      out.print(ln);
    }
  }

  /**
   * @param val
   */
  public void println(final String... val) {
    for (String ln: val) {
      out.print(ln);
    }

    out.println();
  }

  /**
   * @param val
   */
  public void println(final String val) {
    out.println(val);
  }

  /**
   * @param lines
   */
  public void prntlns(final String... lines) {
    for (String ln: lines) {
      out.println(ln);
    }
  }
}
