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
package org.bedework.calfacade.svc;

import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.PropertiesEntity;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.FromXmlCallback;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Account preferences for Bedework. These affect the user view of calendars.
 *
 *  @author Mike Douglass douglm rpi.edu
 *  @version 1.0
 */
@Dump(elementName="preferences", keyFields={"ownerHref"}, firstFields={"ownerHref"})
public class BwPreferences extends BwOwnedDbentity<BwPreferences>
        implements PropertiesEntity {
  /** Collection of BwView
   */
  protected Collection<BwView> views;

  private String email;

  /** The calendar they will use by default.
   */
  private String defaultCalendarPath;

  private String skinName;

  private String skinStyle;

  /** Name of the view the user prefers to start with. null for default
   */
  private String preferredView;

  /** "day", "week" etc
   */
  private String preferredViewPeriod;

  /* Size of page in search results
   */
  private int pageSize = 10;

  /** Flag days as workdays. Space for not, "W" for a workday.
   * 7 characters with Sunday the first. Localization code should handle
   * first day of week.
   */
  private String workDays;

  /** Time in minutes for workday start, e.g. 14:30 is 870
   */
  private int workdayStart;

  /* Time in minutes for workday end, e.g. 17:30 is 1050
   */
  private int workdayEnd;

  /* When adding events do we prefer end date ("date")
   *  or duration ("duration"). Note the values this field takes
   *  are internal values only - not meant for display.
   */
  private String preferredEndType;

  /** */
  public static final String preferredEndTypeDuration = "duration";
  /** */
  public static final String preferredEndTypeDate = "date";

  /** Value identifying an extra simple user mode - we just do stuff without
   * asking
   */
  public static final int basicMode = 0;

  /** Value identifying a simple user mode - we hide some stuff but make
   * fewer assumptions
   */
  public static final int simpleMode = 1;

  /** Value identifying an advanced user mode - reveal it in all its glory
   */
  public static final int advancedMode = 2;

  /** Max mode value
   */
  public static final int maxMode = 2;

  private int userMode;

  private boolean hour24;

  private boolean scheduleAutoRespond;

  /** Set status to cancelled */
  public static int scheduleAutoCancelSetStatus = 0;

  /** Delete canceled meetings */
  public static int scheduleAutoCancelDelete = 1;

  /** */
  public static int scheduleMaxAutoCancel = 1;

  private int scheduleAutoCancelAction;

  private boolean scheduleDoubleBook;

  /** Don't leave any notification for accepts */
  public static int scheduleAutoProcessResponsesNoAcceptNotify = 0;

  /** Notify for all responses */
  public static int scheduleAutoProcessResponsesNotifyAll = 1;

  /** Don't leave any notification */
  public static int scheduleAutoProcessResponsesNoNotify = 2;

  /** */
  public static int scheduleMaxAutoProcessResponses = 2;

  private int scheduleAutoProcessResponses;

  private Set<BwProperty> properties;

  /** Property names - which will be prefixed by the bedework namespace */

  /** preferred locale */
  public static final String propertyPreferredLocale = "userpref:preferrred-locale";

  /** default view-mode grid, daily, list */
  public static final String propertyDefaultViewMode = "userpref:default-view-mode";

  /** last locale */
  public static final String propertyLastLocale = "userpref:last-locale";

  /** approvers for this calsuite  */
  public static final String propertyCalsuiteApprovers = "userpref:calsuite-approvers";

  /** path to attachments folder  */
  public static final String propertyAttachmentsFolder = "userpref:attachments-folder";

  /** default timezone id  */
  public static final String propertyDefaultTzid = "userpref:default-tzid";

  /** preferred locale */
  public static final String propertyDefaultCategory = "userpref:default-category";

  /** scheduling max-instances */
  public static final String propertyScheduleMaxinstances = "userpref:schedule-max-instances";

  /** default image directory */
  public static final String propertyDefaultImageDirectory = "userpref:default-image-directory";

  /** default admin resources directory */
  public static final String propertyAdminResourcesDirectory = "userpref:admin-resources-directory";

  /** default suite resources directory */
  public static final String propertySuiteResourcesDirectory = "userpref:suite-resources-directory";

  /** admin clear form on submit */
  public static final String propertyAdminClearFormsOnSubmit= "userpref:admin-clear-form-on-submit";

  /** preferred group - admin suggest event - this may occur multiple times */
  public static final String propertyPreferredGroup = "userpref:preferrred-group";

  /** Notification token */
  public static final String propertyNotificationToken = "userpref:notification-token";

  /** User want notifications suppressed? */
  public static final String propertySuppressNotifications = "userpref:no-notifications";

  /** XXX Only here till we update schema
      max entity size for this user -only settable by admin */
  public static final String propertyMaxEntitySize = "NOTuserpref:max-entity-size";

  /** XXX Only here till we update schema
      current quota used */
  public static final String propertyQuotaUsed = "NOTuserpref:quota-used-bytes";

  /** Constructor
   *
   */
  public BwPreferences() {
  }

  /* ====================================================================
   *                   Bean methods
   * ==================================================================== */

  /** Set of views principal has defined
   *
   * @param val        Collection of BwView
   */
  public void setViews(final Collection<BwView> val) {
    views = val;
  }

  /** Get the calendars principal is subscribed to
   *
   * @return Collection    of BwView
   */
  @Dump(collectionElementName = "view", compound = true)
  public Collection<BwView> getViews() {
    return views;
  }

  /**
   * @param val String email
   */
  public void setEmail(final String val) {
    email = val;
  }

  /**
   * @return String email
   */
  public String getEmail() {
    return email;
  }

  /**
   * @param val default calendar path
   */
  public void setDefaultCalendarPath(final String val) {
    defaultCalendarPath = val;
  }

  /**
   * @return String default calendar path
   */
  public String getDefaultCalendarPath() {
    return defaultCalendarPath;
  }

  /**
   * @param val skin name
   */
  public void setSkinName(final String val) {
    skinName = val;
  }

  /**
   * @return String skin name
   */
  public String getSkinName() {
    return skinName;
  }

  /**
   * @param val skin style
   */
  public void setSkinStyle(final String val) {
    skinStyle = val;
  }

  /**
   * @return String skin style
   */
  public String getSkinStyle() {
    return skinStyle;
  }

  /**
   * @param val preferred view
   */
  public void setPreferredView(final String val) {
    preferredView = val;
  }

  /**
   * @return String preferred view
   */
  public String getPreferredView() {
    return preferredView;
  }

  /** The value should be a non-internationalized String out of
   * "today", "day", "week", "month", "year". The user interface can present
   * language appropriate labels.
   *
   * @param val preferred view period
   */
  public void setPreferredViewPeriod(final String val) {
    preferredViewPeriod = val;
  }

  /**
   * @return String preferred view period
   */
  public String getPreferredViewPeriod() {
    return preferredViewPeriod;
  }

  /**
   * @param val number of results in search result page
   */
  public void setPageSize(final int val) {
    pageSize = val;
  }

  /**
   * @return number of results in search result page
   */
  public int getPageSize() {
    return pageSize;
  }

  /**
   * @param val work days
   */
  public void setWorkDays(final String val) {
    workDays = val;
  }

  /**
   * @return String work days
   */
  public String getWorkDays() {
    return workDays;
  }

  /**
   * @param val day number for start of week
   */
  public void setWorkdayStart(final int val) {
    workdayStart = val;
  }

  /**
   * @return int work day start
   */
  public int getWorkdayStart() {
    return workdayStart;
  }

  /**
   * @param val day number for end of week
   */
  public void setWorkdayEnd(final int val) {
    workdayEnd = val;
  }

  /**
   * @return int work day end
   */
  public int getWorkdayEnd() {
    return workdayEnd;
  }

  /**
   * @param val preferred end type
   */
  public void setPreferredEndType(final String val) {
    preferredEndType = val;
  }

  /**
   * @return String preferred end type (none, duration, date/time)
   */
  public String getPreferredEndType() {
    return preferredEndType;
  }

  /**
   * @param val user mode code
   */
  public void setUserMode(final int val) {
    userMode = val;
  }

  /**
   * @return int user mode
   */
  public int getUserMode() {
    return userMode;
  }

  /**
   * @param val true for 24 hour
   */
  public void setHour24(final boolean val) {
    hour24 = val;
  }

  /**
   * @return bool
   */
  public boolean getHour24() {
    return hour24;
  }

  /**
   * @param val true for auto respond
   */
  public void setScheduleAutoRespond(final boolean val) {
    scheduleAutoRespond = val;
  }

  /**
   * @return bool
   */
  public boolean getScheduleAutoRespond() {
    return scheduleAutoRespond;
  }

  /**
   * @param val auto cancel action code
   */
  public void setScheduleAutoCancelAction(final int val) {
    scheduleAutoCancelAction = val;
  }

  /**
   * @return int
   */
  public int getScheduleAutoCancelAction() {
    return scheduleAutoCancelAction;
  }

  /**
   * @param val true if double booking allowed
   */
  public void setScheduleDoubleBook(final boolean val) {
    scheduleDoubleBook = val;
  }

  /**
   * @return bool
   */
  public boolean getScheduleDoubleBook() {
    return scheduleDoubleBook;
  }

  /**
   * @param val response action code
   */
  public void setScheduleAutoProcessResponses(final int val) {
    scheduleAutoProcessResponses = val;
  }

  /**
   * @return int
   */
  public int getScheduleAutoProcessResponses() {
    return scheduleAutoProcessResponses;
  }

  /* ====================================================================
   *                   Property methods
   * ==================================================================== */

  @Override
  public void setProperties(final Set<BwProperty> val) {
    properties = val;
  }

  @Override
  @Dump(collectionElementName = "property", compound = true)
  public Set<BwProperty> getProperties() {
    return properties;
  }

  @Override
  public Set<BwProperty> getProperties(final String name) {
    final TreeSet<BwProperty> ps = new TreeSet<>();

    if (getNumProperties() == 0) {
      return null;
    }

    for (final BwProperty p: getProperties()) {
      if (p.getName().equals(name)) {
        ps.add(p);
      }
    }

    return ps;
  }

  @Override
  public void removeProperties(final String name) {
    final Set<BwProperty> ps = getProperties(name);

    if (ps == null) {
      return;
    }

    for (final BwProperty p: ps) {
      removeProperty(p);
    }
  }

  @Override
  @NoDump
  public int getNumProperties() {
    Set<BwProperty> p = getProperties();
    if (p == null) {
      return 0;
    }

    return p.size();
  }

  @Override
  public BwProperty findProperty(final String name) {
    Collection<BwProperty> props = getProperties();

    if (props == null) {
      return null;
    }

    for (BwProperty prop: props) {
      if (name.equals(prop.getName())) {
        return prop;
      }
    }

    return null;
  }

  @Override
  public void addProperty(final BwProperty val) {
    Set<BwProperty> c = getProperties();
    if (c == null) {
      c = new TreeSet<>();
      setProperties(c);
    }

    c.add(val);
  }

  private boolean removeProperty(final String name) {
    final BwProperty p = findProperty(name);
    if (p == null) {
      return false;
    }

    return removeProperty(p);
  }

  @Override
  public boolean removeProperty(final BwProperty val) {
    final Set<BwProperty> c = getProperties();
    if (Util.isEmpty(c)) {
      return false;
    }

    return c.remove(val);
  }

  @Override
  public Set<BwProperty> copyProperties() {
    if (getNumProperties() == 0) {
      return null;
    }

    return new TreeSet<>(getProperties());
  }

  @Override
  public Set<BwProperty> cloneProperties() {
    if (getNumProperties() == 0) {
      return null;
    }

    TreeSet<BwProperty> ts = new TreeSet<>();

    for (BwProperty p: getProperties()) {
      ts.add((BwProperty)p.clone());
    }

    return ts;
  }

  /* ====================================================================
   *                   Property convenience methods
   * ==================================================================== */

  /**
   * @param val  Comma separated calsuite approvers.
   */
  public void setCalsuiteApprovers(final String val) {
    setProp(propertyCalsuiteApprovers, val);
  }

  /**
   * @return Comma separated calsuite approvers.
   */
  @NoDump
  public String getCalsuiteApprovers() {
    return getProp(propertyCalsuiteApprovers);
  }

  /**
   * @return List from approvers.
   */
  @NoDump
  public List<String> getCalsuiteApproversList() {
    final List<String> approvers = new ArrayList<>();

    final String s = getProp(propertyCalsuiteApprovers);
    if (s == null) {
      return approvers;
    }

    final String[] split = s.split(",");

    for (final String el: split) {
      if (el == null) {
        continue;
      }

      approvers.add(el);
    }

    return approvers;
  }

  /**
   * @param val  String path.
   */
  public void setAttachmentsPath(final String val) {
    setProp(propertyAttachmentsFolder, val);
  }

  /**
   * @return String path.
   */
  @NoDump
  public String getAttachmentsPath() {
    return getProp(propertyAttachmentsFolder);
  }

  /**
   * @param val  String path.
   */
  public void setDefaultImageDirectory(final String val) {
    setProp(propertyDefaultImageDirectory, val);
  }

  /**
   * @return String path.
   */
  @NoDump
  public String getDefaultImageDirectory() {
    return getProp(propertyDefaultImageDirectory);
  }

  /**
   * @param val  String path.
   */
  public void setAdminResourcesDirectory(final String val) {
    setProp(propertyAdminResourcesDirectory, val);
  }

  /**
   * @return String path.
   */
  @NoDump
  public String getAdminResourcesDirectory() {
    return getProp(propertyAdminResourcesDirectory);
  }

  /**
   * @param val  String path.
   */
  public void setSuiteResourcesDirectory(final String val) {
    setProp(propertySuiteResourcesDirectory, val);
  }

  /**
   * @return String path.
   */
  @NoDump
  public String getSuiteResourcesDirectory() {
    return getProp(propertySuiteResourcesDirectory);
  }

  /**
   * @param val  String clear form pref - null for default.
   */
  public void setClearFormsOnSubmit(final String val) {
    setProp(propertyAdminClearFormsOnSubmit, val);
  }

  /**
   * @return String clear form pref.
   */
  @NoDump
  public String getClearFormsOnSubmit() {
    return getProp(propertyAdminClearFormsOnSubmit);
  }

  /**
   * @param val  String ViewMode.
   */
  public void setDefaultViewMode(final String val) {
    setProp(propertyDefaultViewMode, val);
  }

  /**
   * @return String ViewMode.
   */
  @NoDump
  public String getDefaultViewMode() {
    return getProp(propertyDefaultViewMode);
  }

  /**
   * @param val  String tzid.
   */
  public void setDefaultTzid(final String val) {
    setProp(propertyDefaultTzid, val);
  }

  /**
   * @return String tzid.
   */
  @NoDump
  public String getDefaultTzid() {
    return getProp(propertyDefaultTzid);
  }

  /** XXX only till we change schema and move this into principal object
   * @param val  long max entity size.
   */
  public void setMaxEntitySize(final long val) {
    setLongProp(propertyMaxEntitySize, val);
  }

  /** XXX only till we change schema and move this into principal object
   * @return long max entity size.
   */
  @NoDump
  public long getMaxEntitySize() {
    return getLongProp(propertyMaxEntitySize);
  }

  /** XXX only till we change schema and move this into principal object
   * @param val  long quota used.
   */
  public void setQuotaUsed(final long val) {
    setLongProp(propertyQuotaUsed, val);
  }

  /** XXX only till we change schema and move this into principal object
   * @return long quota used.
   */
  @NoDump
  public long getQuotaUsed() {
    return getLongProp(propertyQuotaUsed);
  }

  /** Supply the set of default category uids. Will replace the current set.
   *
   * @param val Set of category uids.
   */
  public void setDefaultCategoryUids(final Set<String> val) {
    final Set<BwProperty> catuids = getProperties(propertyDefaultCategory);

    final boolean noprops = Util.isEmpty(catuids);

    if (Util.isEmpty(val)) {
      if (noprops) {
        return;
      }

      for (BwProperty p: catuids) {
        removeProperty(p);
      }

      return;
    }

    // Work out what we have to add/remove
    for (final String uid: val) {
      final BwProperty p = new BwProperty(propertyDefaultCategory, uid);

      if (noprops) {
        addProperty(p);
        continue;
      }

      if (!catuids.contains(p)) {
        // Not in properties
        addProperty(p);
      } else {
        catuids.remove(p);
      }
    }

    if (Util.isEmpty(catuids)) {
      return;
    }

    // If any left in catuids remove them from properties
    for (final BwProperty p: catuids) {
      removeProperty(p);
    }
  }

  /** Get the set of default category uids.
   *
   * @return Set of category uids - always non-null.
   */
  @NoDump
  public Set<String> getDefaultCategoryUids() {
    return getProps(propertyDefaultCategory);
  }

  /** Get the set of preferred groups.
   *
   * @return Set of group hrefs - always non-null.
   */
  @NoDump
  public Set<String> getPreferredGroups() {
    return getProps(propertyPreferredGroup);
  }

  /** Add a preferred group.
   *
   * @param href group href
   */
  @NoDump
  public void addPreferredGroup(final String href) {
    addProperty(new BwProperty(propertyPreferredGroup, href));
  }

  /**
   * @param val  String Notification Token.
   */
  public void setNotificationToken(final String val) {
    setProp(propertyNotificationToken, val);
  }

  /**
   * @return String Notification Token.
   */
  @NoDump
  public String getNotificationToken() {
    return getProp(propertyNotificationToken);
  }

  /**
   * @param val  No Notifications?
   */
  public void setNoNotifications(final boolean val) {
    if (!val) {
      removeProperty(propertySuppressNotifications);
      return;
    }
    setProp(propertySuppressNotifications, String.valueOf(val));
  }

  /**
   * @return No Notifications?
   */
  @NoDump
  public boolean getNoNotifications() {
    final String s = getProp(propertySuppressNotifications);

    if (s == null) {
      return false;
    }

    return Boolean.parseBoolean(s);
  }

  /* ====================================================================
   *                   db entity methods
   * ==================================================================== */

  /** Set the href - ignored
   *
   * @param val    String href
   */
  public void setHref(final String val) { }

  public String getHref() {
    return getOwnerHref();
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /**
   * @param val - the view
   * @return boolean true if removed
   */
  public boolean addView(final BwView val) {
    Collection<BwView> c = getViews();
    if (c == null) {
      c = new TreeSet<>();
      setViews(c);
    }
    if (c.contains(val)) {
      return false;
    }

    c.add(val);
    return true;
  }

  /** Set the workday start minutes from a String time value
   *
   * @param val  String time value
   */
  public void setWorkdayStartTime(final String val) {
    setWorkdayStart(makeMinutesFromTime(val));
  }

  /** Get the workday start as a 4 digit String hours and minutes value
   *
   * @return String work day start time
   */
  @NoDump
  public String getWorkdayStartTime() {
    return CalFacadeUtil.getTimeFromMinutes(getWorkdayStart());
  }

  /** Set the workday end minutes from a String time value
   *
   * @param val  String time value
   */
  public void setWorkdayEndTime(final String val) {
    setWorkdayEnd(makeMinutesFromTime(val));
  }

  /** Get the workday end as a 4 digit String hours and minutes value
   *
   * @return String work day end time
   */
  @NoDump
  public String getWorkdayEndTime() {
    return CalFacadeUtil.getTimeFromMinutes(getWorkdayEnd());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  /** Comapre this view and an object
   *
   * @param  that    object to compare.
   * @return int -1, 0, 1
   */
  @Override
  public int compareTo(final BwPreferences that) {
    if (that == this) {
      return 0;
    }

    if (that == null) {
      return -1;
    }

    return getOwnerHref().compareTo(that.getOwnerHref());
  }

  @Override
  public int hashCode() {
    return getOwnerHref().hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof BwPreferences)) {
      return false;
    }

    if (obj == this) {
      return true;
    }

    return compareTo((BwPreferences)obj) == 0;
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    ts.append("email", getEmail());
    ts.append("defaultCalendarPath", getDefaultCalendarPath());
    ts.append("skinName", getSkinName());
    ts.append("skinStyle", getSkinStyle());
    ts.append("preferredView", getPreferredView());
    ts.append("preferredViewPeriod", getPreferredViewPeriod());
    ts.append("pageSize", getPageSize());
    ts.append("workDays", getWorkDays());
    ts.append("workdayStart", getWorkdayStart());
    ts.append("workdayEnd", getWorkdayEnd());
    ts.append("preferredEndType", getPreferredEndType());
    ts.append("userMode", getUserMode());
    ts.append("hour24", getHour24());
    ts.append("scheduleAutoRespond", getScheduleAutoRespond());
    ts.append("scheduleAutoCancelAction",
              getScheduleAutoCancelAction());
    ts.append("scheduleDoubleBook", getScheduleDoubleBook());
    ts.append("scheduleAutoProcessResponses",
              getScheduleAutoProcessResponses());

    ts.append("views", getViews());
    ts.append("properties", getProperties());
/*  
    private Set<BwProperty> properties;

    public static final String propertyPreferredLocale = "userpref:preferrred-locale";
    public static final String propertyDefaultViewMode = "userpref:default-view-mode";
    public static final String propertyLastLocale = "userpref:last-locale";
    public static final String propertyAttachmentsFolder = "userpref:attachments-folder";
    public static final String propertyDefaultTzid = "userpref:default-tzid";
    public static final String propertyDefaultCategory = "userpref:default-category";
    public static final String propertyScheduleMaxinstances = "userpref:schedule-max-instances";
    public static final String propertyDefaultImageDirectory = "userpref:default-image-directory";
    public static final String propertyAdminResourcesDirectory = "userpref:admin-resources-directory";
    public static final String propertySuiteResourcesDirectory = "userpref:suite-resources-directory";
    public static final String propertyPreferredGroup = "userpref:preferrred-group";
    public static final String propertyNotificationToken = "userpref:notification-token";
    public static final String propertyMaxEntitySize = "NOTuserpref:max-entity-size";
    public static final String propertyQuotaUsed = "NOTuserpref:quota-used-bytes";
*/

    return ts.toString();
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  /** Turn a String time value e.g. 1030 into a numeric minutes value and set
   * the numeric value in the prefeences.
   *
   * <p>Ignores anything after the first four characters which must all be digits.
   *
   * @param val  String time value
   * @return int minutes
   */
  private int makeMinutesFromTime(final String val) {
    boolean badval = false;
    int minutes = 0;

    try {
      int hours = Integer.parseInt(val.substring(0, 2));
      minutes = Integer.parseInt(val.substring(2, 4));
      if ((hours < 0) || (hours > 24)) {
        badval = true;
      } else if ((minutes < 0) || (minutes > 59)) {
        badval = true;
      } else {
        minutes *= (hours * 60);
      }
    } catch (Throwable t) {
      badval = true;
    }

    if (badval) {
      throw new RuntimeException("org.bedework.prefs.badvalue: " + val);
    }

    return minutes;
  }

  /** @param val  long.
   */
  private void setLongProp(final String name, final long val) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      prop = new BwProperty();
      prop.setName(name);
      prop.setValue(String.valueOf(val));

      addProperty(prop);

      return;
    }

    prop.setValue(String.valueOf(val));
  }

  /**
   * @return long
   */
  @NoDump
  private long getLongProp(final String name) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      return 0;
    }

    return Long.parseLong(prop.getValue());
  }


  /**
   * @param val  String
   */
  private void setProp(final String name, final String val) {
    BwProperty prop = findProperty(name);

    if (prop == null) {
      prop = new BwProperty();
      prop.setName(name);
      prop.setValue(val);

      addProperty(prop);

      return;
    }

    prop.setValue(val);
  }

  /**
   * @return String value.
   */
  @NoDump
  private String getProp(final String name) {
    final BwProperty prop = findProperty(name);

    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }

  /**
   * @return String values.
   */
  @NoDump
  private Set<String> getProps(final String name) {
    final Set<BwProperty> props = getProperties(name);

    final Set<String> vals = new TreeSet<>();

    if (props == null) {
      return vals;
    }

    for (final BwProperty p: props) {
      vals.add(p.getValue());
    }

    return vals;
  }

  /* ====================================================================
   *                   Restore callback
   * ==================================================================== */

  private static FromXmlCallback fromXmlCb;

  @NoDump
  public static FromXmlCallback getRestoreCallback() {
    if (fromXmlCb == null) {
      fromXmlCb = new FromXmlCallback();

      fromXmlCb.addClassForName("view", BwView.class);
      fromXmlCb.addClassForName("property", BwProperty.class);

      fromXmlCb.addSkips("byteSize",
                         "id",
                         "seq");
    }

    return fromXmlCb;
  }
}
