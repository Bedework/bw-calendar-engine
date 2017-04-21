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
 * @author Mike Douglass   douglm   rpi.edu
 * @version 1.0
 */
public interface Defs {
  /* ====================================================================
   *                Collection names for hierarchical dump
   * ==================================================================== */

  String adminGroupsDirName = "adminGroups";
  String categoriesDirName = "categories";
  String collectionsDirName = "collections";
  String contactsDirName = "contacts";
  String contentDirName = "content";
  String locationsDirName = "locations";
  String resourcesDirName = "resources";

  /* ====================================================================
   *                      Tag names for entire dump
   * ==================================================================== */

  /** */
  String dumpTag = "caldata";

  /** */
  String majorVersionTag = "majorVersion";

  /** */
  String minorVersionTag = "minorVersion";

  /** */
  String updateVersionTag = "updateVersion";

  /** */
  String patchLevelTag = "patchLevel";

  /** */
  String versionTag = "version";

  /** */
  String dumpDateTag = "dumpDate";

  /* ====================================================================
   *                      Tag names for each section
   * ==================================================================== */

  /** */
  String sectionSyspars = "syspars";
  /** */
  String sectionTimeZones = "timezones";
  /** */
  String sectionFilters = "filters";
  /** */
  String sectionUsers = "users";
  /** */
  String sectionUserInfo = "user-info";
  /** */
  String sectionCollections = "collections";
  /** */
  String sectionSubscriptions = "subscriptions";
  /** */
  String sectionViews = "views";
  /** */
  String sectionLocations = "locations";
  /** */
  String sectionContacts = "contacts";
  /** */
  String sectionOrganizers = "organizers";
  /** */
  String sectionAttendees = "attendees";
  /** */
  String sectionAlarms = "alarms";
  /** */
  String sectionCategories = "categories";
  /** */
  String sectionAuthUsers = "authusers";
  /** */
  String sectionEvents = "events";
  /** */
  String sectionEventAnnotations = "event-annotations";
  /** */
  String sectionAdminGroups = "adminGroups";
  /** */
  String sectionUserPrefs = "user-preferences";
  /** */
  String sectionResources = "resources";
  /** */
  String sectionDbLastmods = "dblastmods";
  /** */
  String sectionCalSuites = "cal-suites";

  /* ====================================================================
   *                      Tag names for each object
   * ==================================================================== */

  /** */
  String objectSystem = "system";
  /** */
  String objectUser = "user";
  /** */
  String objectTimeZone = "timezone";
  /** */
  String objectFilter = "filter";
  /** */
  String objectUserInfo = "user-info";
  /** */
  String objectCalendar = "calendar";  // PRE3.5
  /** */
  String objectCollection = "collection";
  /** */
  String objectCalSuite = "cal-suite";
  /** */
  String objectSubscription = "subscription";
  /** */
  String objectView = "view";
  /** */
  String objectLocation = "location";
  /** */
  String objectContact = "contact";
  /** */
  String objectOrganizer = "organizer";
  /** */
  String objectAttachment = "attachment";
  /** */
  String objectAttendee = "attendee";
  /** */
  String objectAlarm = "alarm";
  /** */
  String objectCategory = "category";
  /** */
  String objectAuthUser = "authuser";
  /** */
  String objectAdminGroup = "adminGroup";
  /** */
  String objectUserPrefs = "user-prefs"; // PRE3.5
  /** */
  String objectPreferences = "preferences";

  /** */
  String objectEvent = "event";
  /** */
  String objectOverride = "override";
  /** */
  String objectEventAnnotation = "event-annotation";

  /** */
  String objectGeo = "geo";

  /** */
  String objectRelatedTo = "relatedTo";

  /** */
  String objectResource = "resource";

  /** */
  String objectContent = "content";

  /** */
  String objectDateTime = "date-time";

  /** */
  String objectColLastmod = "col-lastmod";

  /* ====================================================================
   *                      Tag names for aliases dump
   * ==================================================================== */

  /** */
  String aliasInfoTag = "alias-info";

  /** */
  String aliasesTag = "aliases";

  /** */
  String extsubsTag = "extsubs";

}
