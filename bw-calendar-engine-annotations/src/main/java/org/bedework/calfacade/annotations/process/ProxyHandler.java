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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.MethodDeclaration;

/** TODO: We need to be able to handle something less generic than Collection,
 * e.g. List or Set.
 *
 * @author douglm
 *
 */
public class ProxyHandler {
  private static final String proxyTemplateName = "BwEventProxy.java.rsrc";

  private List<ProxyMethod> proxyMethods = new ArrayList<ProxyMethod>();

  private Map<String, ProxyMethod> fieldNameMap = new HashMap<String, ProxyMethod>();

  private ProcessState pstate;

  private AnnUtil annUtil;

  /* For the proxy class */

  /**
   * @param pstate
   */
  public ProxyHandler(final ProcessState pstate) {
    this.pstate = pstate;
  }

  /**
   * @param env
   * @return boolean true for OK
   */
  public boolean startProxy(final AnnotationProcessorEnvironment env) {
    try {
      annUtil = new AnnUtil(env,
                            "org.bedework.calfacade.BwEvent",
                            pstate.resourcePath + "/" + proxyTemplateName,
                            "org.bedework.calfacade." + "BwEventProxy");

      return annUtil.emitTemplateSection();
    } catch (Throwable t) {
      Messager msg = env.getMessager();
      t.printStackTrace();
      msg.printError("Exception: " + t.getMessage());
      return false;
    }
  }

  /**
   * @param env
   * @param d
   * @return boolean true for ok
   */
  public boolean proxyMethod(final AnnotationProcessorEnvironment env,
                             final MethodDeclaration d) {
    Messager msg = env.getMessager();
    try {
      ProxyMethod pm = new ProxyMethod(env, annUtil, d);

      proxyMethods.add(pm);

      ProxyMethod setGet =  fieldNameMap.get(pm.fieldName);

      if (setGet == null) {
        fieldNameMap.put(pm.fieldName, pm);
      } else if (setGet.setter) {
        // This should be a getter
        if (!pm.getter) {
          msg.printError("Error: found setter in table for " + pm);
        } else {
          setGet.setGet = pm;
          pm.setGet = setGet;
        }
      } else if (setGet.getter) {
        if (!pm.setter) {
          msg.printError("Error: found getter in table for " + pm);
        } else {
          // Make setter first
          setGet.setGet = pm;
          pm.setGet = setGet;
          fieldNameMap.put(pm.fieldName, pm);
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
  public boolean endProxy(final AnnotationProcessorEnvironment env) {
    try {
      for (ProxyMethod pm: proxyMethods) {
        pm.generate();
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
