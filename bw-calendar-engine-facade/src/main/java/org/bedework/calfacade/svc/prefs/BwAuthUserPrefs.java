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

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.util.misc.ToString;

/** Value object to represent authorized calendar user preferences.
 * These should really be in the same table.
 *
 *  @author Mike Douglass
 *  @version 1.0
 */
public class BwAuthUserPrefs extends DumpEntity<BwAuthUserPrefs>
        implements BwCommonUserPrefs {
  /** Users preferred categories.
   */
  private CategoryPref categoryPrefs;

  /** Users preferred locations.
   */
  protected LocationPref locationPrefs;

  /** Users preferred contacts.
   */
  private ContactPref contactPrefs;

  /** Users preferred calendars.
   */
  protected CalendarPref calendarPrefs;

  /* ====================================================================
   *                   Factory method
   * ==================================================================== */

  /**
   * @return BwAuthUserPrefs
   */
  public static BwAuthUserPrefs makeAuthUserPrefs() {
    BwAuthUserPrefs aup = new BwAuthUserPrefs();

    aup.setCategoryPrefs(new CategoryPref());
    aup.setLocationPrefs(new LocationPref());
    aup.setContactPrefs(new ContactPref());
    aup.setCalendarPrefs(new CalendarPref());

    return aup;
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#setCategoryPrefs(org.bedework.calfacade.svc.prefs.CategoryPref)
   */
  public void setCategoryPrefs(CategoryPref val) {
    categoryPrefs = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#getCategoryPrefs()
   */
  @Dump(compound = true)
  public CategoryPref getCategoryPrefs() {
    return categoryPrefs;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.BwCommonUserPrefs#setPreferredLocations(java.util.Collection)
   */
  public void setLocationPrefs(LocationPref val) {
    locationPrefs = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.BwCommonUserPrefs#getPreferredLocations()
   */
  @Dump(compound = true)
  public LocationPref getLocationPrefs() {
    return locationPrefs;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#setContactPrefs(org.bedework.calfacade.svc.prefs.ContactPref)
   */
  public void setContactPrefs(ContactPref val) {
    contactPrefs = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#getContactPrefs()
   */
  @Dump(compound = true)
  public ContactPref getContactPrefs() {
    return contactPrefs;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#setCalendarPrefs(org.bedework.calfacade.svc.prefs.CalendarPref)
   */
  public void setCalendarPrefs(CalendarPref val) {
    calendarPrefs = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.svc.prefs.BwCommonUserPrefs#getCalendarPrefs()
   */
  @Dump(compound = true)
  public CalendarPref getCalendarPrefs() {
    return calendarPrefs;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  public String toString() {
    final ToString ts = new ToString(this);

    ts.append("categoryPrefs", getCategoryPrefs());
    ts.newLine().append("locationPrefs", getLocationPrefs());
    ts.newLine().append("sponsorPrefs", getContactPrefs());
    ts.newLine().append("calendarPrefs", getCalendarPrefs());

    return ts.toString();
  }

  @SuppressWarnings("MethodDoesntCallSuperMethod")
  public Object clone() {
    BwAuthUserPrefs aup = new BwAuthUserPrefs();

    aup.setCategoryPrefs((CategoryPref)getCategoryPrefs().clone());
    aup.setLocationPrefs((LocationPref)getLocationPrefs().clone());
    aup.setContactPrefs((ContactPref)getContactPrefs().clone());
    aup.setCalendarPrefs((CalendarPref)getCalendarPrefs().clone());

    return aup;
  }
}
