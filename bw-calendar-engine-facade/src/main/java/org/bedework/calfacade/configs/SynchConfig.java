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

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.jmx.MBeanInfo;

import java.io.Serializable;

/** Information to access the synch engine
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "synch")
public interface SynchConfig extends Serializable {
  /** Set the wsdlUri
   *
   * @param val    String
   */
  void setWsdlUri(String val);

  /** get the wsdlUri
   *
   * @return String
   */
  @MBeanInfo("wsdlUri.")
  String getWsdlUri();

  /** Set the managerUri
   *
   * @param val    String
   */
  void setManagerUri(String val);

  /** get the managerUri
   *
   * @return String
   */
  @MBeanInfo("Manager uri.")
  String getManagerUri();

  /** Set the connectorId. This must match the bedework connector id in the
   * synch engine config.
   *
   * <p>It identifies which connection we are using for communication with the
   * synch engine.
   *
   * @param val    String
   */
  void setConnectorId(String val);

  /** Get the connectorId
   *
   * @return String
   */
  @MBeanInfo("Identifies which connection we are using for communication with the" +
  		" synch engine.")
  String getConnectorId();
}
