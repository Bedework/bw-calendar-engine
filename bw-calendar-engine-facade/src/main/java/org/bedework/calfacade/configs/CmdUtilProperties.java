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

import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.MBeanInfo;

/** These are the properties for cmdutil.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "cmdutil-properties")
public interface CmdUtilProperties {
  /** Default account we run under
   *
   * @param val - user account
   */
  void setAccount(String val);

  /**
   * @return String default account we use
   */
  @MBeanInfo("account indexer runs as")
  String getAccount();

  /** 
   * @param val true for super user
   */
  void setSuperUser(final boolean val);

  /**
   * @return true for super user
   */
  @MBeanInfo("true for super user")
  boolean getSuperUser();

  /** data output directory name - full path.
   *
   * @param val path
   */
  void setDataOut(String val);

  /**
   * @return data output directory name - full path
   */
  @MBeanInfo("Data output file name - full path")
  String getDataOut();
}
