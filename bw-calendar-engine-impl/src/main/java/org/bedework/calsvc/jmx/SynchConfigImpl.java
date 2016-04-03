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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.SynchConfig;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;

/** Information to access the synch engine
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "synch",
          type = "org.bedework.calfacade.configs.SynchConfig")
public class SynchConfigImpl extends ConfigBase<SynchConfigImpl>
        implements SynchConfig {
  private String wsdlUri;

  private String managerUri;

  private String connectorId;

  @Override
  public void setWsdlUri(final String val) {
    wsdlUri = val;
  }

  @Override
  public String getWsdlUri() {
    return wsdlUri;
  }

  @Override
  public void setManagerUri(final String val) {
    managerUri = val;
  }

  @Override
  public String getManagerUri() {
    return managerUri;
  }

  @Override
  public void setConnectorId(final String val) {
    connectorId = val;
  }

  @Override
  public String getConnectorId() {
    return connectorId;
  }
}
