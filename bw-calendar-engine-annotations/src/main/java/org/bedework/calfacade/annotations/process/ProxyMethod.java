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

import java.util.Collection;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.PrimitiveType;
import com.sun.mirror.type.TypeMirror;

/** We create a list of these as we process the event. We tie together the
 * setter and getter methods so that only the setter needs to be annotated
 *
 * @author Mike DOuglass
 */
public class ProxyMethod extends MethodHandler<ProxyMethod> {
  /**
   * @param env
   * @param annUtil
   * @param d
   */
  public ProxyMethod(final AnnotationProcessorEnvironment env,
                     final AnnUtil annUtil,
                     final MethodDeclaration d) {
    super(env, annUtil, d);
  }

  /**
   */
  @Override
  public void generateGet() {
    String typeStr = AnnUtil.getClassName(returnType);

    /* check corresponding setter to see if this is immutable */
    if ((setGet != null) && setGet.immutable) {
      annUtil.println("    return ", makeCallGetter("getTarget()"), ";");
      annUtil.prntlns("  }",
                      "");

      return;
    }

    if (!AnnUtil.isCollection(returnType)) {
      if (!(returnType instanceof PrimitiveType)) {
        annUtil.println("    ", typeStr, " val = ", makeCallGetter("ref"), ";");

        annUtil.prntlns("    if (val != null) {",
                        "      return val;",
                        "    }",
                        "");

        annUtil.println("    if (", makeGetEmptyFlag("ref"), ") {");

        annUtil.prntlns("      return null;",
                        "    }",
                        "");
      }

      annUtil.println("    return ", makeCallGetter("getTarget()"), ";");

      annUtil.prntlns("  }",
                      "");

      return;
    }

    annUtil.println("    ", typeStr, " c = super.", methName, "();");
    annUtil.println("    if (c == null) {");
    annUtil.println("      c = new Override", typeStr,
                               "(BwEvent.ProxiedFieldIndex.pfi",
                               ucFieldName, ",");
    annUtil.println("                                    ref, this) {");
    annUtil.println("        public void setOverrideCollection(", typeStr, " val) {");
    annUtil.println("          ", makeCallSetter("ref", "val"), ";");
    annUtil.prntlns("          setChangeFlag(true);",
                    "        }",
                    "");

    annUtil.println("        public ", typeStr, " getOverrideCollection() {");
    annUtil.println("          return ", makeCallGetter("ref"), ";");
    annUtil.prntlns("        }",
                    "");

    annUtil.println("        public void copyIntoOverrideCollection() {");
    annUtil.println("          ", typeStr, " mstr = getMasterCollection();");
    annUtil.println(" ");
    annUtil.println("          if (mstr != null) {");
    annUtil.println("            ", typeStr, " over = getOverrideCollection();");
    if (cloneForOverride) {
      annUtil.println("            for (", cloneElementType, " el: mstr) {");
      annUtil.println("              over.add((", cloneElementType, ")el.clone());");
      annUtil.println("            }");
    } else {
      annUtil.println("            over.addAll(mstr);");
    }
    annUtil.prntlns("          }",
                    "        }",
                    "");

    /*
     * From ClassType
     * Collection<TypeMirror> getActualTypeArguments()
     * Needed to build TreeSet decl below.
     */
    String typePar = null;
    if (returnType instanceof ClassType) {
      ClassType ct = (ClassType)returnType;

      Collection<TypeMirror> tms = ct.getActualTypeArguments();

      typePar = tms.iterator().next().toString();
    } else if (returnType instanceof InterfaceType) {
      InterfaceType ct = (InterfaceType)returnType;

      Collection<TypeMirror> tms = ct.getActualTypeArguments();

      typePar = tms.iterator().next().toString();
    } else {
      Messager msg = env.getMessager();
      msg.printWarning("Unhandled returnType: " + returnType);
    }

    typePar = AnnUtil.fixName(typePar);

    // XXX Having done all that we didn't use typePar

    annUtil.println("        public ", typeStr, " getMasterCollection() {");
    annUtil.println("          return ", makeCallGetter("getTarget()"), ";");
    annUtil.prntlns("        }",
                    "      };",
                    "");
    annUtil.println("      ", makeCallSetter("super", "c"), ";");
    annUtil.prntlns("    }",
                    "",
                    "    return c;",
                    "  }",
                    "");
  }

  /**
   */
  @Override
  public void generateSet() {
    if (basicType) {
      annUtil.println("    if (", makeCallGetter("ref"), " != val) {");

      if (immutable) {
        annUtil.println("      throw new RuntimeException(\"Immutable\");");
      } else {
        annUtil.println("      ", makeCallSetter("ref", "val"), ";");
        annUtil.println("      setChangeFlag(true);");
      }

      annUtil.prntlns("    }",
                      "  }",
                      "");

      return;
    }

    if (AnnUtil.isCollection(fieldType)) {
      String fieldTypeStr = AnnUtil.getClassName(fieldType);

      annUtil.println("    if (val instanceof Override", AnnUtil.nonGeneric(fieldTypeStr),
                                        ") {");
      annUtil.println("      val = ((Override", fieldTypeStr, ")val).getOverrideCollection();");
      annUtil.println("    }");
    }

    annUtil.println("    int res = doSet(", makeFieldIndex(), ", ",
                                         String.valueOf(immutable), ",");
    annUtil.println("                    ", makeCallGetter("getTarget()"), ",");
    annUtil.println("                    ", makeCallGetter("ref"), ", val);");

    annUtil.println("    if (res == setRefNull) {");
    annUtil.println("      ", makeCallSetter("ref", null), ";");
    annUtil.prntlns("    }",
                    "",
                    "    if (res == setRefVal) {");
    annUtil.println("      ", makeCallSetter("ref", "val"), ";");
    annUtil.prntlns("    }",
                    "  }",
                    "");
  }

  /**
   */
  @Override
  public void generateMethod() {
    env.getMessager().printError("Proxy should only do set/get, found: " +
                                 methName);
  }

  private String makeGetEmptyFlag(final String objRef) {
    StringBuilder sb = new StringBuilder(objRef);
    sb.append(".");
    sb.append("getEmptyFlag(");
    sb.append(makeFieldIndex());
    sb.append(")");

    return sb.toString();
  }

  private String makeFieldIndex() {
    return "ProxiedFieldIndex.pfi" + ucFieldName;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("ProxyMethod{");
    sb.append(fieldName);
    sb.append(", basic=");
    sb.append(basicType);
    sb.append(", method=");
    sb.append(methName);

    return sb.toString();
  }
}
