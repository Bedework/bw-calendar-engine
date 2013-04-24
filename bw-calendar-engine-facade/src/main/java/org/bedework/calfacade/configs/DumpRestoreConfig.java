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

/** Configuration propeties for the restore phase
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class DumpRestoreConfig extends ConfigCommon {
  private boolean debug;

  private boolean debugEntity;

  private boolean initSyspars;

  /* When converting put all admin groups into the new group with this name
   * When initialising set default access to be read/write-content for this group
   */
  private String superGroupName;

  /* If non-null we will set any events with no calendar to this one.
   * This is mainly to fix errors in the data. All events should have a calendar.
   */
  private String defaultPublicCalPath;

  /**
   * @param val
   */
  public void setDebug(boolean val)  {
    debug = val;
  }

  /** .
   *
   * @return booelan val
   */
  public boolean getDebug()  {
    return debug;
  }

  /**
   * @param val
   */
  public void setDebugEntity(boolean val)  {
    debugEntity = val;
  }

  /**
   *
   * @return boolean val
   */
  public boolean getDebugEntity()  {
    return debugEntity;
  }

  /** True if we initialise the system parameters
   *
   * @param val
   */
  public void setInitSyspars(boolean val)  {
    initSyspars = val;
  }

  /** True if we initialise the system parameters
   *
   * @return booelan val
   */
  public boolean getInitSyspars()  {
    return initSyspars;
  }

  /** When converting put all admin groups into the new group with this name
   *
   * @param val
   */
  public void setSuperGroupName(String val)  {
    superGroupName = val;
  }

  /** When converting put all admin groups into the new group with this name
   *
   * @return String val
   */
  public String getSuperGroupName()  {
    return superGroupName;
  }

  /** If non-null we will set any events with no calendar to this one.
   * This is mainly to fix errors in the data. All events should have a calendar.
   *
   * @param val
   */
  public void setDefaultPublicCalPath(String val)  {
    defaultPublicCalPath = val;
  }

  /** If non-null we will set any events with no calendar to this one.
   * This is mainly to fix errors in the data. All events should have a calendar.
   *
   * @return String val
   */
  public String getDefaultPublicCalPath()  {
    return defaultPublicCalPath;
  }
}
