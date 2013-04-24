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

/** Common database configuration properties for the clients
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class DbConfig implements Serializable {
  /* True if caching is on.
   */
  private boolean cachingOn = true;

  private String cachePrefix;

  /** Constructor
   *
   */
  public DbConfig() {
  }

  /** True if caching on.
   *
   * @param val
   */
  public void setCachingOn(boolean val) {
    cachingOn = val;
  }

  /**
   * @return boolean
   */
  public boolean getCachingOn() {
    return cachingOn;
  }

  /**
   * @param val
   */
  public void setCachePrefix(String val) {
    cachePrefix = val;
  }

  /**
   * @return String
   */
  public String getCachePrefix() {
    return cachePrefix;
  }

  /** Copy this object to val.
   *
   * @param val
   */
  public void copyTo(DbConfig val) {
    val.setCachingOn(getCachingOn());
    val.setCachePrefix(getCachePrefix());
  }

  public Object clone() {
    DbConfig conf = new DbConfig();

    copyTo(conf);

    return conf;
  }
}
