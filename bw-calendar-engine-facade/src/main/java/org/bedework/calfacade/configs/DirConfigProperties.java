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

import org.bedework.util.jmx.MBeanInfo;

import java.io.Serializable;

/** This interface defines the various common directory interface properties.
 *
 * @author Mike Douglass
 */
public interface DirConfigProperties extends Serializable {
  /** Mbean class name
   *
   * @param val    String
   */
  void setMbeanClassName(String val);

  /** Class name
   *
   * @return String
   */
  @MBeanInfo("The mbean class.")
  String getMbeanClassName();

  /**
   * @param val Comma separated list of domains - '*' should be treated as a wildcard
   */
  public void setDomains(String val);

  /** Comma separated list of domains - '*' should be treated as a wildcard
   *
   * @return String val
   */
  @MBeanInfo("Comma separated list of domains - '*' should be treated as a wildcard.")
  public String getDomains();

  /**
   * @param val unspecified for no default or a single exactly specified domain.
   */
  public void setDefaultDomain(String val);

  /**
   *
   * @return String val
   */
  @MBeanInfo("defaultDomain can be left unspecified for no default or a single" +
  		" exactly specified domain.")
  public String getDefaultDomain();

  /** To enable mapping of calendar addresses e.g. mailto:fred@example.org
   *  on to principals we need to either do a directory lookup or have
   *  some sort of pattern map.
   *
   * <p>Setting a caladdr prefix enables pattern mapping. By default
   * calendar addresses are users
   *
   * <pre>
   * &lt;caladdrPrefixes classname="org.bedework.calfacade.configs.CalAddrPrefixes">
   *   &lt;!--
   *        This would specify that any calendar user address starting with loc_
   *        is to be treated as a location, e.g. we might get
   *            mailto:loc_vcc315@example.org
   *      &lt;location>loc_&lt;/location>
   *       -->
   *    &lt;/caladdrPrefixes>
   * </pre>
   *
   * @param val the prefixes
   */
  void setCalAddrPrefixes(CalAddrPrefixes val);

  /**
   * @return CalAddrPrefixes or null
   */
  @MBeanInfo("Calendar address prefixes")
  CalAddrPrefixes getCalAddrPrefixes();
}
