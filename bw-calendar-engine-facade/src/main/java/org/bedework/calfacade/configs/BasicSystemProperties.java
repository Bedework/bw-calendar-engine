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

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.jmx.MBeanInfo;

import java.io.Serializable;

/** Provides access to some of the basic configuration for the system. Most of
 * these values should not be changed. While their values do leak out into the
 * user interface they are intended to be independent of any language.
 *
 * <p>Display names should be used to localize the names of collections and paths.
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "basic-properties")
public interface BasicSystemProperties extends Serializable {
  /** Set the principal root e.g. "/principals"
   *
   * @param val    String
   */
  public void setPrincipalRoot(final String val);

  /** get the principal root e.g. "/principals"
   *
   * @return String
   */
  public String getPrincipalRoot();

  /** Set the user principal root e.g. "/principals/users"
   *
   * @param val    String
   */
  public void setUserPrincipalRoot(final String val);

  /** get the principal root e.g. "/principals/users"
   *
   * @return String
   */
  public String getUserPrincipalRoot();

  /** Set the group principal root e.g. "/principals/groups"
   *
   * @param val    String
   */
  public void setGroupPrincipalRoot(final String val);

  /** get the group principal root e.g. "/principals/groups"
   *
   * @return String
   */
  public String getGroupPrincipalRoot();

  /** Set the bedework admin group principal root
   * e.g. "/principals/groups/bwadmin"
   *
   * @param val    String
   */
  public void setBwadmingroupPrincipalRoot(final String val);

  /** Get the bedework admin group principal root
   * e.g. "/principals/groups/bwadmin"
   *
   * @return String
   */
  public String getBwadmingroupPrincipalRoot();

  /** Set the resource principal root e.g. "/principals/resources"
   *
   * @param val    String
   */
  public void setResourcePrincipalRoot(final String val);

  /** get the resource principal root e.g. "/principals/resources"
   *
   * @return String
   */
  public String getResourcePrincipalRoot();

  /** Set the venue principal root e.g. "/principals/locations"
   *
   * @param val    String
   */
  public void setVenuePrincipalRoot(final String val);

  /** get the venue principal root e.g. "/principals/locations"
   *
   * @return String
   */
  public String getVenuePrincipalRoot();

  /** Set the ticket principal root e.g. "/principals/tickets"
   *
   * @param val    String
   */
  public void setTicketPrincipalRoot(final String val);

  /** get the ticket principal root e.g. "/principals/tickets"
   *
   * @return String
   */
  public String getTicketPrincipalRoot();

  /** Set the host principal root e.g. "/principals/hosts"
   *
   * @param val    String
   */
  public void setHostPrincipalRoot(final String val);

  /** get the host principal root e.g. "/principals/hosts"
   *
   * @return String
   */
  public String getHostPrincipalRoot();

  /** Set the public Calendar Root
   *
   * @param val    String
   */
  public void setPublicCalendarRoot(final String val);

  /** Get the publicCalendarRoot
   *
   * @return String   publicCalendarRoot
   */
  @MBeanInfo("public Calendar Root - do not change")
  public String getPublicCalendarRoot();

  /** Set the user Calendar Root
   *
   * @param val    String
   */
  public void setUserCalendarRoot(final String val);

  /** Get the userCalendarRoot
   *
   * @return String   userCalendarRoot
   */
  @MBeanInfo("user Calendar Root - do not change")
  public String getUserCalendarRoot();

  /** Set the user default calendar
   *
   * @param val    String
   */
  public void setUserDefaultCalendar(final String val);

  /** Get the userDefaultCalendar
   *
   * @return String   userDefaultCalendar
   */
  @MBeanInfo("userDefaultCalendar - do not change")
  public String getUserDefaultCalendar();

  /** Set the defaultNotificationsName
   *
   * @param val
   */
  public void setDefaultNotificationsName(final String val);

  /** Get the defaultNotificationsName
   *
   * @return flag
   */
  @MBeanInfo("name of notifications collection - do not change")
  public String getDefaultNotificationsName();

  /** Set the defaultReferencesName
   *
   * @param val
   */
  public void setDefaultReferencesName(final String val);

  /** Get the defaultReferencesName
   *
   * @return flag
   */
  @MBeanInfo("name of default references collection - do not change")
  public String getDefaultReferencesName();

  /** Set the user inbox name
   *
   * @param val    String
   */
  public void setUserInbox(final String val);

  /** Get the user inbox name
   *
   * @return String   user inbox
   */
  @MBeanInfo("user inbox name - do not change")
  public String getUserInbox();

  /** Set the user outbox
   *
   * @param val    String
   */
  public void setUserOutbox(final String val);

  /** Get the user outbox
   *
   * @return String   user outbox
   */
  @MBeanInfo("user outbox - do not change")
  public String getUserOutbox();

  /** Set the public user
   *
   * @param val    String
   */
  public void setPublicUser(final String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("Account name for public entities - one not in the directory")
  public String getPublicUser();

  /** Set the path to the root for indexes
   *
   * @param val    String index root
   */
  public void setIndexRoot(final String val);

  /** Get the path to the root for indexes
   *
   * @return String
   */
  @MBeanInfo("path to the root for non-solr indexes")
  public String getIndexRoot();

  /** Set the global resources path
   *
   * @param val
   */
  public void setGlobalResourcesPath(final String val);

  /** Get the global resources path
   *
   * @return token
   */
  @MBeanInfo("The global resources path")
  public String getGlobalResourcesPath();
}
