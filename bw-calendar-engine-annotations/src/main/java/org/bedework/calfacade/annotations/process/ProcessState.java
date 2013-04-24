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

import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.TypeMirror;

/**
 * @author douglm
 *
 */
public class ProcessState {
  String currentClassName;

  boolean processingEvent;

  String resourcePath;

  private ProxyHandler proxyHandler;
  private IcalPropertyHandler icalPropertyHandler;

  private WrapperHandler wrapperHandler;

  boolean processingWrapper;

  boolean generateQuotaSupport;

  /* Calculated size of fixed fields. */
  protected int sizeOverhead;

  boolean debug;

  /**
   * @param val
   */
  public void setCurrentClassName(String val) {
    currentClassName = val;
  }

  /**
   * @return String
   */
  public String getCurrentClassName() {
    return currentClassName;
  }

  /**
   * @return ProxyHandler
   */
  public ProxyHandler getProxyHandler() {
    if (proxyHandler == null) {
      proxyHandler = new ProxyHandler(this);
    }

    return proxyHandler;
  }

  /**
   *
   * @param wpr
   * @return vHandler
   */
  public WrapperHandler getWrapperHandler(Wrapper wpr) {
    if (wrapperHandler == null) {
      wrapperHandler = new WrapperHandler(this, wpr);
    }

    return wrapperHandler;
  }

  /** Get current wrapper handler
   *
   * @return vHandler
   */
  public WrapperHandler getWrapperHandler() {
    return wrapperHandler;
  }

  /** Remove current wrapper handler
   *
   */
  public void endWrapperHandler() {
    wrapperHandler = null;
    processingWrapper = false;
    sizeOverhead = 0;
    generateQuotaSupport = false;
  }

  /**
   * @return IcalPropertyHandler
   */
  public IcalPropertyHandler getIcalPropertyHandler() {
    if (icalPropertyHandler == null) {
      icalPropertyHandler = new IcalPropertyHandler(this);
    }

    return icalPropertyHandler;
  }

  /**
   * @param tm
   * @return boolean
   */
  public static boolean isCollection(TypeMirror tm) {
    if (!(tm instanceof InterfaceType)) {
      return false;
    }

    /* XXX There must be a better way than this */
    String typeStr = tm.toString();

    return typeStr.startsWith("java.util.Collection") ||
           typeStr.startsWith("java.util.List") ||
           typeStr.startsWith("java.util.Set");
  }
}
