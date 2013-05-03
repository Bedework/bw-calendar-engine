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
public class CardDavInfo implements Serializable {
  private boolean auth;

  private String host;

  private int port;

  private String contextPath;

  /** Require auth?
   *
   * @param val    boolean
   */
  public void setAuth(final boolean val) {
    auth = val;
  }

  /** Require auth?
   *
   * @return boolean
   */
  public boolean getAuth() {
    return auth;
  }

  /** Set the host
   *
   * @param val    String
   */
  public void setHost(final String val) {
    host = val;
  }

  /** get the host
   *
   * @return String
   */
  public String getHost() {
    return host;
  }

  /** Set the port
   *
   * @param val    int
   */
  public void setPort(final int val) {
    port = val;
  }

  /** get the v
   *
   * @return int
   */
  public int getport() {
    return port;
  }

  /** Set the contextPath
   *
   * @param val    String
   */
  public void setContextPath(final String val) {
    contextPath = val;
  }

  /** Get the contextPath
   *
   * @return String
   */
  public String getContextPath() {
    return contextPath;
  }
}
