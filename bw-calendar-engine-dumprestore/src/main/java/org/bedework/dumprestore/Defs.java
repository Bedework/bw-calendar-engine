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
package org.bedework.dumprestore;

/** Some definitions for dumped data.
 *
 * <p>The data is dumped in one section per object class. Dumped objects
 * refer to other objects by their key (usually an integer).
 *
 * <p>Restoring the data will take a couple of passes over some of it. For
 * example, the user entries ref to subscriptions which refer to filters
 * which refer to locations and sponsors and categories.
 *
 * <p>Restore the users, save the subscriptions and then do them at the end.
 *
 * @author Mike Douglass   douglm@bedework.edu
 * @version 1.0
 */
public interface Defs {
  /* ====================================================================
   *                      Tag names for entire dump
   * ==================================================================== */

  /** */
  public static final String dumpTag = "caldata";

  /** */
  public static final String majorVersionTag = "majorVersion";

  /** */
  public static final String minorVersionTag = "minorVersion";

  /** */
  public static final String updateVersionTag = "updateVersion";

  /** */
  public static final String patchLevelTag = "patchLevel";

  /** */
  public static final String versionTag = "version";

  /** */
  public static final String dumpDateTag = "dumpDate";

  /* ====================================================================
   *                      Tag names for each section
   * ==================================================================== */

  /** */
  public static final String sectionSyspars = "syspars";
  /** */
  public static final String sectionTimeZones = "timezones";
  /** */
  public static final String sectionFilters = "filters";
  /** */
  public static final String sectionUsers = "users";
  /** */
  public static final String sectionUserInfo = "user-info";
  /** */
  public static final String sectionCollections = "collections";
  /** */
  public static final String sectionSubscriptions = "subscriptions";
  /** */
  public static final String sectionViews = "views";
  /** */
  public static final String sectionLocations = "locations";
  /** */
  public static final String sectionContacts = "contacts";
  /** */
  public static final String sectionOrganizers = "organizers";
  /** */
  public static final String sectionAttendees = "attendees";
  /** */
  public static final String sectionAlarms = "alarms";
  /** */
  public static final String sectionCategories = "categories";
  /** */
  public static final String sectionAuthUsers = "authusers";
  /** */
  public static final String sectionEvents = "events";
  /** */
  public static final String sectionEventAnnotations = "event-annotations";
  /** */
  public static final String sectionAdminGroups = "adminGroups";
  /** */
  public static final String sectionUserPrefs = "user-preferences";
  /** */
  public static final String sectionResources = "resources";
  /** */
  public static final String sectionDbLastmods = "dblastmods";
  /** */
  public static final String sectionCalSuites = "cal-suites";

  /* ====================================================================
   *                      Tag names for each object
   * ==================================================================== */

  /** */
  public static final String objectSystem = "system";
  /** */
  public static final String objectUser = "user";
  /** */
  public static final String objectTimeZone = "timezone";
  /** */
  public static final String objectFilter = "filter";
  /** */
  public static final String objectUserInfo = "user-info";
  /** */
  public static final String objectCalendar = "calendar";  // PRE3.5
  /** */
  public static final String objectCollection = "collection";
  /** */
  public static final String objectCalSuite = "cal-suite";
  /** */
  public static final String objectSubscription = "subscription";
  /** */
  public static final String objectView = "view";
  /** */
  public static final String objectLocation = "location";
  /** */
  public static final String objectContact = "contact";
  /** */
  public static final String objectOrganizer = "organizer";
  /** */
  public static final String objectAttachment = "attachment";
  /** */
  public static final String objectAttendee = "attendee";
  /** */
  public static final String objectAlarm = "alarm";
  /** */
  public static final String objectCategory = "category";
  /** */
  public static final String objectAuthUser = "authuser";
  /** */
  public static final String objectAdminGroup = "adminGroup";
  /** */
  public static final String objectUserPrefs = "user-prefs"; // PRE3.5
  /** */
  public static final String objectPreferences = "preferences";

  /** */
  public static final String objectEvent = "event";
  /** */
  public static final String objectOverride = "override";
  /** */
  public static final String objectEventAnnotation = "event-annotation";

  /** */
  public static final String objectGeo = "geo";

  /** */
  public static final String objectRelatedTo = "relatedTo";

  /** */
  public static final String objectResource = "resource";

  /** */
  public static final String objectContent = "content";

  /** */
  public static final String objectDateTime = "date-time";

  /** */
  public static final String objectColLastmod = "col-lastmod";
}
