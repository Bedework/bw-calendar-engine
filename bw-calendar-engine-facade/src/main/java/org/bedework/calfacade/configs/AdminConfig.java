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



/**
 * @author douglm
 *
 */
public class AdminConfig extends WebConfigCommon {
  /* True if categories are optional.
   */
  private boolean categoryOptional = true;

  private boolean allowEditAllCategories;
  private boolean allowEditAllLocations;
  private boolean allowEditAllContacts;

  private boolean noGroupAllowed;

  private String adminGroupsIdPrefix;

  /** Constructor
   *
   */
  public AdminConfig() {
  }

  /** True if categories are optional.
   *
   * @param val
   */
  public void setCategoryOptional(boolean val) {
    categoryOptional = val;
  }

  /**
   * @return boolean
   */
  public boolean getCategoryOptional() {
    return categoryOptional;
  }

  /**
   *  @param val
   */
  public void setAllowEditAllCategories(boolean val) {
    allowEditAllCategories = val;
  }

  /**
   * @return boolean
   */
  public boolean getAllowEditAllCategories() {
    return allowEditAllCategories;
  }

  /**
   *  @param val
   */
  public void setAllowEditAllLocations(boolean val) {
    allowEditAllLocations = val;
  }

  /**
   * @return boolean
   */
  public boolean getAllowEditAllLocations() {
    return allowEditAllLocations;
  }

  /**
   *  @param val
   */
  public void setAllowEditAllContacts(boolean val) {
    allowEditAllContacts = val;
  }

  /**
   * @return boolean
   */
  public boolean getAllowEditAllContacts() {
    return allowEditAllContacts;
  }

  /**
   *  @param val
   */
  public void setNoGroupAllowed(boolean val) {
    noGroupAllowed = val;
  }

  /**
   * @return boolean
   */
  public boolean getNoGroupAllowed() {
    return noGroupAllowed;
  }

  /**
   *  @param val
   */
  public void setAdminGroupsIdPrefix(String val) {
    adminGroupsIdPrefix = val;
  }

  /**
   * @return boolean
   */
  public String getAdminGroupsIdPrefix() {
    return adminGroupsIdPrefix;
  }

  public Object clone() {
    AdminConfig conf = new AdminConfig();

    copyTo(conf);

    conf.setCategoryOptional(getCategoryOptional());
    conf.setAllowEditAllCategories(getAllowEditAllCategories());
    conf.setAllowEditAllLocations(getAllowEditAllLocations());
    conf.setAllowEditAllContacts(getAllowEditAllContacts());

    conf.setNoGroupAllowed(getNoGroupAllowed());
    conf.setAdminGroupsIdPrefix(getAdminGroupsIdPrefix());

    return conf;
  }
}
