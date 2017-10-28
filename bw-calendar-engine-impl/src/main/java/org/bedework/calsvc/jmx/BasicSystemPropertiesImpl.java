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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.CalAddrPrefixes;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.jmx.MBeanInfo;
import org.bedework.util.misc.ToString;

/** Provides access to some of the basic configuration for the system. Most of
 * these values should not be changed. While their values do leak out into the
 * user interface they are intended to be independent of any language.
 *
 * <p>Display names should be used to localize the names of collections and paths.
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "basic-properties",
          type = "org.bedework.calfacade.configs.BasicSystemProperties")
public class BasicSystemPropertiesImpl extends ConfigBase<BasicSystemPropertiesImpl>
        implements BasicSystemProperties {
  /* Default calendar names */
  private String publicCalendarRoot;
  private String userCalendarRoot;
  private String userDefaultCalendar;
  private String userDefaultTasksCalendar;
  private String userDefaultPollsCalendar;
  private String defaultNotificationsName;
  private String defaultAttachmentsName;
  private String defaultReferencesName;
  private String userInbox;
  private String userOutbox;

  private String indexRoot;

  private String globalResourcesPath;

  private CalAddrPrefixes calAddrPrefixes;

  private String bedeworkResourceDirectory;

  private boolean testMode;

  @Override
  public void setPublicCalendarRoot(final String val) {
    publicCalendarRoot = val;
  }

  @Override
  @MBeanInfo("public Calendar Root - do not change")
  public String getPublicCalendarRoot() {
    return publicCalendarRoot;
  }

  @Override
  public void setUserCalendarRoot(final String val) {
    userCalendarRoot = val;
  }

  @Override
  @MBeanInfo("user Calendar Root - do not change")
  public String getUserCalendarRoot() {
    return userCalendarRoot;
  }

  @Override
  public void setUserDefaultCalendar(final String val) {
    userDefaultCalendar = val;
  }

  @Override
  @MBeanInfo("user default events calendar - do not change")
  public String getUserDefaultCalendar() {
    return userDefaultCalendar;
  }

  @Override
  public void setUserDefaultTasksCalendar(final String val) {
    userDefaultTasksCalendar = val;
  }

  @Override
  @MBeanInfo("user default tasks calendar - do not change")
  public String getUserDefaultTasksCalendar() {
    return userDefaultTasksCalendar;
  }

  @Override
  public void setUserDefaultPollsCalendar(final String val) {
    userDefaultPollsCalendar = val;
  }

  @Override
  @MBeanInfo("user default polls calendar - do not change")
  public String getUserDefaultPollsCalendar() {
    return userDefaultPollsCalendar;
  }

  @Override
  public void setDefaultNotificationsName(final String val) {
    defaultNotificationsName = val;
  }

  @Override
  @MBeanInfo("name of notifications collection - do not change")
  public String getDefaultNotificationsName() {
    return defaultNotificationsName;
  }

  @Override
  public void setDefaultAttachmentsName(final String val) {
    defaultAttachmentsName = val;
  }

  @Override
  @MBeanInfo("name of attachments collection - do not change")
  public String getDefaultAttachmentsName() {
    return defaultAttachmentsName;
  }

  @Override
  public void setDefaultReferencesName(final String val) {
    defaultReferencesName = val;
  }

  @Override
  @MBeanInfo("name of default references collection - do not change")
  public String getDefaultReferencesName() {
    return defaultReferencesName;
  }

  @Override
  public void setUserInbox(final String val) {
    userInbox = val;
  }

  @Override
  @MBeanInfo("user inbox name - do not change")
  public String getUserInbox() {
    return userInbox;
  }

  @Override
  public void setUserOutbox(final String val) {
    userOutbox = val;
  }

  @Override
  @MBeanInfo("user outbox - do not change")
  public String getUserOutbox() {
    return userOutbox;
  }

  @Override
  public void setIndexRoot(final String val) {
    indexRoot = val;
  }

  @Override
  @MBeanInfo("path to the root for non-solr indexes")
  public String getIndexRoot() {
    return indexRoot;
  }

  @Override
  public void setGlobalResourcesPath(final String val) {
    globalResourcesPath = val;
  }

  @Override
  public String getGlobalResourcesPath() {
    return globalResourcesPath;
  }

  @Override
  public void setCalAddrPrefixes(final CalAddrPrefixes val) {
    calAddrPrefixes = val;
  }

  @Override
  public CalAddrPrefixes getCalAddrPrefixes() {
    return calAddrPrefixes;
  }

  @Override
  public void setBedeworkResourceDirectory(final String val) {
    bedeworkResourceDirectory = val;
  }

  @Override
  public String getBedeworkResourceDirectory() {
    return bedeworkResourceDirectory;
  }

  @Override
  public void setTestMode(final boolean val) {
    testMode = val;
  }

  @Override
  public boolean getTestMode() {
    return testMode;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.newLine();
    ts.append("name", getName());

    ts.newLine();
    ts.append("publicCalendarRoot", getPublicCalendarRoot());
    ts.append("userCalendarRoot", getUserCalendarRoot());

    ts.newLine();
    ts.append("userDefaultCalendar", getUserDefaultCalendar());
    ts.append("defaultNotificationsName", getDefaultNotificationsName());
    ts.append("userInbox", getUserInbox());
    ts.append("userOutbox", getUserOutbox());

    ts.append("calAddrPrefixes", getCalAddrPrefixes());

    ts.newLine();
    ts.append("indexRoot", getIndexRoot());
    ts.append("bedeworkResourceDirectory", getBedeworkResourceDirectory());
    ts.append("testMode", getTestMode());

    return ts.toString();
  }

  /**
   * @return clone of this
   */
  public BasicSystemPropertiesImpl cloneIt() {
    BasicSystemPropertiesImpl clone = new BasicSystemPropertiesImpl();

    clone.setName(getName());

    clone.setPublicCalendarRoot(getPublicCalendarRoot());
    clone.setUserCalendarRoot(getUserCalendarRoot());
    clone.setUserDefaultCalendar(getUserDefaultCalendar());
    clone.setUserInbox(getUserInbox());
    clone.setUserOutbox(getUserOutbox());

    clone.setDefaultNotificationsName(getDefaultNotificationsName());
    clone.setDefaultReferencesName(getDefaultReferencesName());

    clone.setIndexRoot(getIndexRoot());

    clone.setGlobalResourcesPath(getGlobalResourcesPath());

    clone.setCalAddrPrefixes(getCalAddrPrefixes());

    clone.setBedeworkResourceDirectory(getBedeworkResourceDirectory());

    return clone;
  }
}
