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

import org.bedework.util.misc.Util;

/** Provides access to some of the basic configuration for the system. Most of
 * these values should not be changed. While their values do leak out into the
 * user interface they are intended to be independent of any language.
 *
 * <p>Display names should be used to localize the names of collections and paths.
 *
 * @author Mike Douglass
 */
public class BasicSystemProperties {
  /** Before 4.0 internal paths did not end with "/". 4.0 and above they will
   */
  public final static boolean colPathEndsWithSlash = false;

  public final static String publicCalendarRoot = "public";

  public final static String publicCalendarRootPath;

  public final static String globalResourcesPath = "/public/Resources/";

  public final static String userCalendarRoot = "user";

  public final static String userCalendarRootPath;

  public final static String userDefaultCalendar = "calendar";

  public final static String userInbox = "Inbox";

  public final static String userPendingInbox = ".pendingInbox";

  public final static String userOutbox = "Outbox";

  public final static String userDefaultTasksCalendar = "tasks";

  public final static String userDefaultPollsCalendar = "polls";

  public final static String defaultNotificationsName = "Notifications";

  public final static String defaultAttachmentsName = "Attachments";

  public final static String defaultReferencesName = "References";

  /** This directory allows us to hide away bedework specific resources
   * such as views and categories. We can hide this directory from
   * generic caldav and other protocols.
   */
  public final static String bedeworkResourceDirectory = ".bedework";

  static {
    publicCalendarRootPath = Util.buildPath(
            colPathEndsWithSlash, "/",
            BasicSystemProperties.publicCalendarRoot);

    userCalendarRootPath = Util.buildPath(
            colPathEndsWithSlash, "/",
            BasicSystemProperties.userCalendarRoot);
  }
}
