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

import java.io.Serializable;

/** Information to access carddav
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "carddav-info")
public interface CardDavInfo extends Serializable {
  /** Require auth?
   *
   * @param val    boolean
   */
  public void setAuth(final boolean val);

  /** Require auth?
   *
   * @return boolean
   */
  public boolean getAuth();

  /** Set the host
   *
   * @param val    String
   */
  public void setHost(final String val);

  /** get the host
   *
   * @return String
   */
  public String getHost();

  /** Set the port
   *
   * @param val    int
   */
  public void setPort(final int val);

  /** get the v
   *
   * @return int
   */
  public int getPort();

  /** Set the contextPath
   *
   * @param val    String
   */
  public void setContextPath(final String val);

  /** Get the contextPath
   *
   * @return String
   */
  public String getContextPath();
}
