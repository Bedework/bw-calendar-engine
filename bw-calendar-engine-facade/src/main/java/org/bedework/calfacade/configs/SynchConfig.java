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
package org.bedework.calfacade.configs;

import java.io.Serializable;

/** Information to access carddav
 *
 * @author Mike Douglass
 */
public class SynchConfig implements Serializable {
  private String wsdlUri;

  private String managerUri;

  private String connectorId;

  /** Set the wsdlUri
   *
   * @param val    String
   */
  public void setWsdlUri(final String val) {
    wsdlUri = val;
  }

  /** get the wsdlUri
   *
   * @return String
   */
  public String getWsdlUri() {
    return wsdlUri;
  }

  /** Set the managerUri
   *
   * @param val    String
   */
  public void setManagerUri(final String val) {
    managerUri = val;
  }

  /** get the managerUri
   *
   * @return String
   */
  public String getManagerUri() {
    return managerUri;
  }

  /** Set the connectorId
   *
   * @param val    String
   */
  public void setConnectorId(final String val) {
    connectorId = val;
  }

  /** Get the connectorId
   *
   * @return String
   */
  public String getConnectorId() {
    return connectorId;
  }
}
