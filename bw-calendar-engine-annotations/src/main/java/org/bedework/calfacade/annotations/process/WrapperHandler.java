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

import org.bedework.calfacade.annotations.Wrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.MethodDeclaration;

/** TODO: We need to be able to handle something less generic than Collection,
 * e.g. List or Set.
 *
 * @author douglm
 *
 */
public class WrapperHandler {
  private static final String wrapperTemplateName = "Wrapper.java.rsrc";

  private List<WrapperMethod> wrapperMethods = new ArrayList<WrapperMethod>();

  private Map<String, WrapperMethod> fieldNameMap = new HashMap<String, WrapperMethod>();

  private String currentClassName;

  private ProcessState pstate;

  private AnnUtil annUtil;

  //private Wrapper wpr;

  private String wrappedClassName;
  private String wrapperClassName;

  /**
   * @param pstate
   * @param wpr
   */
  public WrapperHandler(final ProcessState pstate,
                        final Wrapper wpr) {
    this.pstate = pstate;
    //this.wpr = wpr;
    currentClassName = pstate.currentClassName;
    wrappedClassName = AnnUtil.fixName(pstate.currentClassName);

    pstate.generateQuotaSupport = wpr.quotas();
  }

  /**
   * @param env
   * @return boolean true for OK
   */
  public boolean start(final AnnotationProcessorEnvironment env) {
    try {
      wrapperClassName = wrappedClassName + "NewWrapper";

      annUtil = new AnnUtil(env,
                            "org.bedework.calfacade.BwEvent",
                            pstate.resourcePath + "/" + wrapperTemplateName,
                            "org.bedework.calfacade.wrappers." + wrapperClassName);

      if (!annUtil.emitTemplateSection()) {
        Messager msg = env.getMessager();
        msg.printError("Apparently no more input available from template");
      }

      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      Messager msg = env.getMessager();
      msg.printError("Exception: " + t.getMessage());
      return false;
    }
  }

  /**
   * @param env
   * @param d
   * @param fromSuper if declared in a superdeclaration
   * @return boolean true for ok
   */
  public boolean method(final AnnotationProcessorEnvironment env,
                        final MethodDeclaration d,
                        final boolean fromSuper) {
    Messager msg = env.getMessager();

    if (pstate.debug) {
      msg.printNotice("Wrapperhandler.method: " + d +
                      " declared by " + d.getDeclaringType());
    }

    try {
      WrapperMethod wm = new WrapperMethod(env, annUtil, pstate, d, fromSuper);

      if (pstate.debug) {
        msg.printNotice("          " + wm);
      }

      if (wrapperMethods.contains(wm)) {
        // Overridden?
        return true;
      }

      wrapperMethods.add(wm);

      WrapperMethod setGet =  fieldNameMap.get(wm.fieldName);

      if (setGet == null) {
        fieldNameMap.put(wm.fieldName, wm);
      } else if (setGet.setter) {
        // This should be a getter
        if (!wm.getter) {
          msg.printError("Error - class: " + currentClassName +
                         " found setter " + setGet + "\n in table for " + wm + " decl: " + d);
        } else {
          setGet.setGet = wm;
          wm.setGet = setGet;
        }
      } else if (setGet.getter) {
        if (!wm.setter) {
          msg.printError("Error - class: " + currentClassName +
                         "  found getter in table for " + wm);
        } else {
          // Make setter first
          setGet.setGet = wm;
          wm.setGet = setGet;
          fieldNameMap.put(wm.fieldName, wm);
        }
      }

      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      msg.printError("Exception: " + t.getMessage());
      return false;
    }
  }

  /**
   * @param env
   * @return boolean true for OK
   */
  public boolean end(final AnnotationProcessorEnvironment env) {
    try {
      TreeSet<String> imports = new TreeSet<String>();

      for (WrapperMethod wm: wrapperMethods) {
        imports.addAll(wm.getImports("org.bedework.calfacade.wrappers"));
      }

      if (pstate.generateQuotaSupport) {
        imports.add("org.bedework.calfacade.util.QuotaUtil");
      }

      for (String imp: imports) {
        annUtil.println("import ", imp, ";");
      }

      if (!annUtil.emitTemplateSection()) {
        Messager msg = env.getMessager();
        msg.printError("Apparently no more input available from template");
      }

      annUtil.println("public class ", wrapperClassName, " extends ",
                      wrappedClassName, " {");

      annUtil.println("  private ", wrappedClassName, " entity;",
                      "");

      if (pstate.generateQuotaSupport) {
        annUtil.prntlns("  private int sizeChange;",
                        "");
      }

      /* Generate constructor */
      annUtil.println("  ", wrapperClassName, "(",
                          AnnUtil.fixName(currentClassName), " entity) {");
      annUtil.prntlns("    this.entity = entity;",
                      "  }",
                      "");

      for (WrapperMethod wm: wrapperMethods) {
        if (wm.staticMethod) {
          continue;
        }

        if (pstate.debug) {
          Messager msg = env.getMessager();
          msg.printNotice("About to generate Wrapperhandler.method: " + wm);
        }

        wm.generate();
      }

      if (pstate.generateQuotaSupport) {
        /* Add quota support methods */

        annUtil.println("  private int sizeOverhead = ", String.valueOf(pstate.sizeOverhead), ";");

        annUtil.prntlns("  /* ====================================================================",
                        "   *                   Size methods",
                        "   * ==================================================================== */",
                        "",
                        "  /** Used to track size changes.",
                        "   *",
                        "   * @param val",
                        "   */",
                        "  public void setSizeChange(int val) {",
                        "   sizeChange = val;",
                        "  }",
                        "",
                        "  /**",
                        "   * @return int last byte size change",
                        "   */",
                        "  public int getSizeChange() {",
                        "    return sizeChange;",
                        "  }",
                        "",
                        "  /** Update the size change with the given increment",
                        "   *",
                        "   * @param val",
                        "   */",
                        "  public void updateSizeChange(int val) {",
                        "    sizeChange += val;",
                        "  }",
                        "}",
                        "");
      }

      if (annUtil.emitTemplateSection()) {
        Messager msg = env.getMessager();
        msg.printError("Apparently more input available from template");
      }

      annUtil.close();

      return true;
    } catch (Throwable t) {
      t.printStackTrace();
      Messager msg = env.getMessager();
      msg.printError("Exception: " + t.getMessage());
      return false;
    }
  }
}
