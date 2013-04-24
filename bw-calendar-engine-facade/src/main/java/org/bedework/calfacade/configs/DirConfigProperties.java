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

/** This class defines the various properties we need to make a connection
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
public class DirConfigProperties implements Serializable {
  private String moduleType;

  private String domains;
  private String defaultDomain;

  private boolean debug;

  /** Used by configuration tools
   *
   * @param val
   */
  public void setModuleType(String val)  {
    moduleType  = val;
  }

  /**
   * @return String
   */
  public String getModuleType()  {
    return moduleType;
  }

  /**
   * @param val
   */
  public void setDomains(String val)  {
    domains = val;
  }

  /** Comma separated list of domains - '*' should be treated as a wildcard
   *
   * @return String val
   */
  public String getDomains()  {
    return domains;
  }

  /**
   * @param val
   */
  public void setDefaultDomain(String val)  {
    defaultDomain = val;
  }

  /**
   *
   * @return String val
   */
  public String getDefaultDomain()  {
    return defaultDomain;
  }

  /**
   * @param val
   */
  public void setDebug(boolean val)  {
    debug = val;
  }

  /** Is debugging on?
   *
   * @return boolean val
   */
  public boolean getDebug()  {
    return debug;
  }
}
