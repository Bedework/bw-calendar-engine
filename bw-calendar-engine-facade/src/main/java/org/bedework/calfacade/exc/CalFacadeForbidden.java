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
package org.bedework.calfacade.exc;

import javax.xml.namespace.QName;

/** Exception that indicates someone tried a disallowed action
 *
 * @author Mike Douglass
 */
public class CalFacadeForbidden extends CalFacadeException {
  QName qname;

  /** Constructor
   *
   */
  public CalFacadeForbidden() {
    super(forbidden);
  }

  /** Constructor
  *
   * @param t Throwable
   */
  public CalFacadeForbidden(Throwable t) {
    super(t);
  }

  /** Constructor
  *
   * @param s String
   */
  public CalFacadeForbidden(String s) {
    super(forbidden, s);
  }

  /** Constructor
   *
   * @param qname
   * @param s String
   */
  public CalFacadeForbidden(QName qname, String s) {
    super(forbidden, s);
    this.qname = qname;
  }

  /**
   * @return QName
   */
  public QName getQname() {
    return qname;
  }
}
