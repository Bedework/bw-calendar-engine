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
package org.bedework.dumprestore;

import org.bedework.calfacade.configs.DumpRestoreProperties;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

/**
 * @author douglm
 *
 */
@ConfInfo(elementName = "dumprestore-properties",
          type = "org.bedework.calfacade.configs.DumpRestoreProperties")
public class DumpRestorePropertiesImpl
        extends ConfigBase<DumpRestorePropertiesImpl>
        implements DumpRestoreProperties {
  private String account;

  private String dataIn;

  private String dataOut;

  private String dataOutPrefix;

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
  public void setDataIn(final String val) {
    dataIn = val;
  }

  @Override
  public String getDataIn() {
    return dataIn;
  }

  @Override
  public void setDataOut(final String val) {
    dataOut = val;
  }

  @Override
  public String getDataOut() {
    return dataOut;
  }

  @Override
  public void setDataOutPrefix(final String val) {
    dataOutPrefix = val;
  }

  @Override
  public String getDataOutPrefix() {
    return dataOutPrefix;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("account", getAccount());
    ts.append("dataIn", getDataIn());
    ts.append("dataOut", getDataOut());
    ts.append("dataOutPrefix", getDataOutPrefix());

    return ts.toString();
  }

  @Override
  public DumpRestorePropertiesImpl cloneIt() {
    DumpRestorePropertiesImpl clone = new DumpRestorePropertiesImpl();

    clone.setName(getName());

    clone.setAccount(getAccount());
    clone.setDataIn(getDataIn());
    clone.setDataOut(getDataOut());
    clone.setDataOutPrefix(getDataOutPrefix());

    return clone;
  }
}
