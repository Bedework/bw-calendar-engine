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
package org.bedework.calfacade.svc.prefs;

import java.io.Serializable;

/** Common preferences for auth user and personal user.
 *
 *  @author Mike Douglass douglm@rpi.edu
 *  @version 1.0
 */
public interface BwCommonUserPrefs extends Serializable {
  /**
   * @param val
   */
  public void setCategoryPrefs(CategoryPref val);

  /**
   * @return Set of preferred categories
   */
  public CategoryPref getCategoryPrefs();

  /** Set locations preferences object
   *
   * @param val
   */
  public void setLocationPrefs(LocationPref val);

  /** Get locations preferences object
   *
   * @return locations preferences object
   */
  public LocationPref getLocationPrefs();

  /**
   * @param val
   */
  public void setContactPrefs(ContactPref val);

  /**
   * @return Set of preferred contacts
   */
  public ContactPref getContactPrefs();

  /**
   * @param val
   */
  public void setCalendarPrefs(CalendarPref val);

  /**
   * @return Set of preferred calendars
   */
  public CalendarPref getCalendarPrefs();
}
