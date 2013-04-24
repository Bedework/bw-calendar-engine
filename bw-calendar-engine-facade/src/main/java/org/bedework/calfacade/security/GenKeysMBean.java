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
package org.bedework.calfacade.security;

import java.util.ArrayList;


/**
 * @author douglm
 *
 */
public interface GenKeysMBean {
  /** Name apparently must be the same as the name attribute in the
   * jboss service definition
   *
   * @return Name
   */
  public String getName();

  /**
   *
   * @param val private key file name - full path
   */
  public void setPrivKeyFileName(String val);

  /**
   * @return private key file name - full path
   */
  public String getPrivKeyFileName();

  /**
   *
   * @param val public key file name - full path
   */
  public void setPublicKeyFileName(String val);

  /**
   * @return public key file name - full path
   */
  public String getPublicKeyFileName();

  /* ========================================================================
   * Operations
   * ======================================================================== */

  /** Output multi-line messages
   *
   */
  public class Msg extends ArrayList<String> {
    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();

      for (String s: this) {
        sb.append(s);
        sb.append("\n");
      }

      return sb.toString();
    }
  }

  /**
   *
   * @return message giving outcome
   */
  public Msg genKeys();

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */

  /** Lifecycle
   *
   */
  public void create();

  /** Lifecycle
   *
   */
  public void start();

  /** Lifecycle
   *
   */
  public void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  public boolean isStarted();

  /** Lifecycle
   *
   */
  public void destroy();
}
