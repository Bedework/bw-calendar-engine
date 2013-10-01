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

/** These are the properties that the dump/restore module needs to know about.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "dumprestore-properties")
public interface DumpRestoreProperties {
  /** Account we run under
   *
   * @param val
   */
  void setAccount(String val);

  /**
   * @return String account we use
   */
  @MBeanInfo("account indexer runs as")
  String getAccount();

  /** XML data input file name - full path. Used for data restore
   *
   * @param val
   */
  void setDataIn(String val);

  /**
   * @return XML data input file name - full path
   */
  @MBeanInfo("XML data input file name - full path")
  String getDataIn();

  /** XML data output directory name - full path. Used for data restore
   *
   * @param val
   */
  void setDataOut(String val);

  /**
   * @return XML data output directory name - full path
   */
  @MBeanInfo("XML data output file name - full path")
  String getDataOut();

  /** XML data output file prefix - for data dump
   *
   * @param val
   */
  void setDataOutPrefix(String val);

  /**
   * @return XML data output file prefix - for data dump
   */
  @MBeanInfo("XML data output file prefix - for data dump")
  String getDataOutPrefix();

  /**
   * @return copy of this
   */
  DumpRestoreProperties cloneIt();
}
