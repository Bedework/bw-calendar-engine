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
package org.bedework.tools.cmdutil;

import org.bedework.calfacade.configs.CmdUtilProperties;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.base.ToString;

/**
 * @author douglm
 *
 */
@ConfInfo(elementName = "cmdutil-properties",
          type = "org.bedework.calfacade.configs.CmdUtilProperties")
public class CmdUtilPropertiesImpl
        extends ConfigBase<CmdUtilPropertiesImpl>
        implements CmdUtilProperties {
  private String account;

  private boolean superUser;

  private String dataOut;

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setAccount(final String val) {
    account = val;
  }

  @Override
  public String getAccount() {
    return account;
  }

  @Override
  public void setSuperUser(final boolean val) {
    superUser = val;
  }

  @Override
  public boolean getSuperUser() {
    return superUser;
  }

  @Override
  public void setDataOut(final String val) {
    dataOut = val;
  }

  @Override
  public String getDataOut() {
    return dataOut;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("account", getAccount());
    ts.append("superUser", getSuperUser());
    ts.append("dataOut", getDataOut());

    return ts.toString();
  }
}
