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
package org.bedework.dumprestore.restore.rules;

import org.bedework.dumprestore.Defs;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.apache.commons.digester.Digester;
import org.apache.commons.digester.ExtendedBaseRules;
import org.apache.commons.digester.RuleSetBase;

/**
 * @author Mike Douglass
 * @version 1.0
 */
public class RestoreRuleSet extends RuleSetBase implements Defs {
  protected RestoreGlobals globals;

  /** Constructor
   *
   * @param globals
   */
  public RestoreRuleSet(final RestoreGlobals globals) {
    super();
    this.globals = globals;
  }

  /* (non-Javadoc)
   * @see org.apache.commons.digester.RuleSetBase#addRuleInstances(org.apache.commons.digester.Digester)
   */
  @Override
  public void addRuleInstances(final Digester d) {
    BwStringRule strRule = new BwStringRule(globals);
    BwStringFieldRule strFrule = new BwStringFieldRule(globals);

    BwLongStringRule lstrRule = new BwLongStringRule(globals);
    BwLongStringFieldRule lstrFrule = new BwLongStringFieldRule(globals);

    PrincipalFieldRule principalField = new PrincipalFieldRule(globals);

    PushFieldRule uidField = new PushFieldRule(globals, "uid");

    PushFieldRule pathField = new PushFieldRule(globals, "path");

    d.setRules(new ExtendedBaseRules());

    /* ---------------- Universal rules ------------------------------ */

    d.addRule("!*/owner", new OwnerRule(globals));
    d.addRule("!*/owner/user", principalField);
    d.addRule("!*/owner/user/account", principalField);
    d.addRule("!*/owner/group", principalField);
    d.addRule("!*/owner/group/account", principalField);

    d.addRule("!*/groupOwner", new OwnerRule(globals));
    d.addRule("!*/groupOwner/user", principalField);
    d.addRule("!*/groupOwner/user/account", principalField);
    d.addRule("!*/groupOwner/group", principalField);
    d.addRule("!*/groupOwner/group/account", principalField);

    d.addRule("!*/creator", new CreatorRule(globals));
    d.addRule("!*/creator/user", principalField);
    d.addRule("!*/creator/user/account", principalField);
    d.addRule("!*/creator/group", principalField);
    d.addRule("!*/creator/group/account", principalField);

    d.addRule("!*/bwstring", strRule);
    d.addRule("!*/bwstring/lang", strFrule);
    d.addRule("!*/bwstring/value", strFrule);

    d.addRule("!*/bwlongstring", lstrRule);
    d.addRule("!*/bwlongstring/lang", lstrFrule);
    d.addRule("!*/bwlongstring/value", lstrFrule);

    d.addRule("!*/date-time", new DateTimeRule(globals));
    d.addRule("!*/date-time/?", new DateTimeFieldRule(globals));

    PropertiesRule pr = new PropertiesRule(globals);
    d.addRule("!*/properties", pr);
    d.addRule("!*/properties/property", pr);
    d.addRule("!*/properties/property/?", new PropertiesFieldRule(globals));

    d.addRule("!*/container/collection/path", new ContainerPathRule(globals));

    /* ---------------- Non-universal rules ------------------------------ */

    DumpPropertiesRule dpr = new DumpPropertiesRule(globals);
    d.addRule("caldata/majorVersion", dpr);
    d.addRule("caldata/minorVersion", dpr);
    d.addRule("caldata/updateVersion", dpr);
    d.addRule("caldata/patchLevel", dpr);
    d.addRule("caldata/dumpDate", dpr);
    d.addRule("caldata/version", dpr);

    d.addRule("caldata/syspars/system", new SysparsRule(globals));
    d.addRule("caldata/syspars/system/*", new SysparsFieldRule(globals));

    UserFieldRule ufr = new UserFieldRule(globals);
    d.addRule("caldata/users", new SectionRule(globals, "users"));
    d.addRule("caldata/users/user", new UserRule(globals));
    d.addRule("caldata/users/user/*", ufr);

    CalendarFieldRule colfr = new CalendarFieldRule(globals);
    d.addRule("caldata/collections", new SectionRule(globals, "calendars"));
    d.addRule("caldata/collections/collection", new CalendarRule(globals));
    d.addRule("caldata/collections/collection/?", colfr);
    d.addRule("caldata/collections/collection/col-lastmod",
              new CollectionLastmodRule(globals));
    d.addRule("caldata/collections/collection/col-lastmod/?",
              new LastmodFieldRule(globals));

    d.addRule("*/collection/categories/category", new CategoryUidRule(globals));
    d.addRule("*/collection/categories/category/uid", uidField);

    CalSuiteFieldRule cfr = new CalSuiteFieldRule(globals);
    d.addRule("caldata/cal-suites", new SectionRule(globals, "cal-suites"));
    d.addRule("caldata/cal-suites/cal-suite", new CalSuiteRule(globals));
    d.addRule("caldata/cal-suites/cal-suite/?", cfr);

    d.addRule("caldata/cal-suites/cal-suite/group/adminGroup/account", cfr);

    d.addRule("caldata/cal-suites/cal-suite/rootCalendar/collection/path", cfr);

    d.addRule("caldata/locations", new SectionRule(globals, "locations"));
    d.addRule("caldata/locations/location", new LocationRule(globals));
    d.addRule("caldata/locations/location/?", new LocationFieldRule(globals));

    d.addRule("caldata/contacts", new SectionRule(globals, "contacts"));
    d.addRule("caldata/contacts/contact", new ContactRule(globals));
    d.addRule("caldata/contacts/contact/?", new ContactFieldRule(globals));

    CategoryFieldRule catfr = new CategoryFieldRule(globals);
    d.addRule("caldata/categories", new SectionRule(globals, "categories"));
    d.addRule("caldata/categories/category", new CategoryRule(globals));
    d.addRule("caldata/categories/category/?", catfr);

    AdminGroupFieldRule agfr = new AdminGroupFieldRule(globals);
    d.addRule("caldata/adminGroups", new SectionRule(globals, "adminGroups"));
    d.addRule("caldata/adminGroups/adminGroup", new AdminGroupRule(globals));
    d.addRule("caldata/adminGroups/adminGroup/?", agfr);
    d.addRule("caldata/adminGroups/adminGroup/groupMembers/?", agfr);

    d.addRule("!*/groupMembers/member", new MemberRule(globals));
    d.addRule("!*/groupMembers/member/user", principalField);
    d.addRule("!*/groupMembers/member/user/account", principalField);
    d.addRule("!*/groupMembers/member/adminGroup", principalField);
    d.addRule("!*/groupMembers/member/adminGroup/account", principalField);

    AuthUserFieldRule aufr = new AuthUserFieldRule(globals);
    d.addRule("caldata/authusers", new SectionRule(globals, "authusers"));
    d.addRule("caldata/authusers/authuser", new AuthUserRule(globals));
    d.addRule("caldata/authusers/authuser/?", aufr);
    d.addRule("caldata/authusers/authuser/user/user/account", aufr);

    d.addRule("caldata/authusers/authuser/prefs/?", aufr);

    d.addRule("caldata/authusers/authuser/prefs/categoryPrefs/preferred/?", aufr);
    d.addRule("caldata/authusers/authuser/prefs/calendarPrefs/preferred/?", aufr);
    d.addRule("caldata/authusers/authuser/prefs/contactPrefs/preferred/?", aufr);
    d.addRule("caldata/authusers/authuser/prefs/locationPrefs/preferred/?", aufr);

    d.addRule("caldata/authusers/authuser/prefs/categoryPrefs/preferred/category",
              new CategoryUidRule(globals));
    d.addRule("caldata/authusers/authuser/prefs/categoryPrefs/preferred/category/?",
              uidField);

    d.addRule("caldata/authusers/authuser/prefs/calendarPrefs/preferred/collection", new CollectionPathRule(globals));
    d.addRule("caldata/authusers/authuser/prefs/calendarPrefs/preferred/collection/?", pathField);

    d.addRule("caldata/authusers/authuser/prefs/contactPrefs/preferred/contact", new ContactUidRule(globals));
    d.addRule("caldata/authusers/authuser/prefs/contactPrefs/preferred/contact/?", uidField);

    d.addRule("caldata/authusers/authuser/prefs/locationPrefs/preferred/location", new LocationUidRule(globals));
    d.addRule("caldata/authusers/authuser/prefs/locationPrefs/preferred/location/?", uidField);

    UserPrefsFieldRule upfr = new UserPrefsFieldRule(globals);
    d.addRule("caldata/user-preferences", new SectionRule(globals, "user-preferences"));
    d.addRule("caldata/user-preferences/preferences", new UserPrefsRule(globals));
    d.addRule("caldata/user-preferences/preferences/?", upfr);
    d.addRule("caldata/user-preferences/preferences/views/view", upfr);
    d.addRule("caldata/user-preferences/preferences/views/view/?", upfr);
    d.addRule("caldata/user-preferences/preferences/views/view/collectionPaths/?", upfr);

    EventFieldRule efr = new EventFieldRule(globals);
    d.addRule("caldata/events", new SectionRule(globals, "events"));
    d.addRule("caldata/events/event", new EventRule(globals, objectEvent));
    d.addRule("caldata/events/event/overrides/event", new EventRule(globals, objectOverride));
    d.addRule("caldata/event-annotations", new SectionRule(globals, "event annotations"));
    d.addRule("caldata/event-annotations/event", new EventRule(globals, objectEventAnnotation));

    EventStringKeyRule eskr = new EventStringKeyRule(globals);

    /* container/collection is caught by a universal rule above */
    d.addRule("caldata/events/event/overrides/event/target/event/uid", eskr);
    d.addRule("caldata/events/event/overrides/event/target/event/recurrenceId", eskr);
    d.addRule("caldata/events/event/overrides/event/master/event/uid", eskr);
    d.addRule("caldata/events/event/overrides/event/master/event/recurrenceId", eskr);

    d.addRule("caldata/event-annotations/event/target/event/uid", eskr);
    d.addRule("caldata/event-annotations/event/target/event/recurrenceId", eskr);
    d.addRule("caldata/event-annotations/event/master/event/uid", eskr);
    d.addRule("caldata/event-annotations/event/master/event/recurrenceId", eskr);

    d.addRule("*/event/?", efr);
    d.addRule("*/event/rdates/rdate", efr);
    d.addRule("*/event/exdates/exdate", efr);
    d.addRule("*/event/rrules/rrule", efr);
    d.addRule("*/event/exrules/exrule", efr);

    d.addRule("*/event/recipients/?", efr);

    d.addRule("*/event/categories/category", new CategoryUidRule(globals));
    d.addRule("*/event/categories/category/uid", uidField);

    d.addRule("*/event/location/location", new LocationUidRule(globals));
    d.addRule("*/event/location/location/uid", uidField);

    d.addRule("*/event/contacts/contact", new ContactUidRule(globals));
    d.addRule("*/event/contacts/contact/uid", uidField);

    d.addRule("*/event/geo", new GeoRule(globals));
    d.addRule("*/event/geo/?", new GeoFieldRule(globals));

    d.addRule("*/event/relatedTo", new RelatedToRule(globals));
    d.addRule("*/event/relatedTo/?", new RelatedToFieldRule(globals));

    d.addRule("*/event/organizer", new OrganizerRule(globals));
    d.addRule("*/event/organizer/?", new OrganizerFieldRule(globals));

    d.addRule("*/event/override/event/?", efr);

    d.addRule("*/event/attachments/attachment", new AttachmentRule(globals));
    d.addRule("*/event/attachments/attachment/?", new AttachmentFieldRule(globals));

    d.addRule("*/event/attendees/attendee", new AttendeeRule(globals));
    d.addRule("*/event/attendees/attendee/?", new AttendeeFieldRule(globals));

    d.addRule("*/event/comments/comment", strRule);
    d.addRule("*/event/comments/comment/?", strFrule);

    d.addRule("*/event/resources/resource", strRule);
    d.addRule("*/event/resources/resource/?", strFrule);

    d.addRule("*/event/request-statuses/request-status",
              new EventBwRequestStatusRule(globals));

    d.addRule("*/event/request-statuses/request-status/description",
              strRule);
    d.addRule("*/event/request-statuses/request-status/description/?",
              strFrule);

    d.addRule("*/event/request-statuses/request-status/?",
              new EventBwRequestStatusFieldRule(globals));

    d.addRule("*/event/xproperties/xproperty", new XpropertyRule(globals));
    d.addRule("*/event/xproperties/xproperty/?", new XpropertyFieldRule(globals));

    AlarmFieldRule alfr = new AlarmFieldRule(globals);
    d.addRule("*/event/alarms/alarm", new AlarmRule(globals));
    d.addRule("*/event/alarms/alarm/?", alfr);

    d.addRule("*/event/alarms/alarm/descriptions/description", strRule);
    d.addRule("*/event/alarms/alarm/descriptions/description/?", strFrule);

    d.addRule("*/event/alarms/alarm/summaries/summary", strRule);
    d.addRule("*/event/alarms/alarm/summaries/summary/?", strFrule);

    FilterFieldRule ffr = new FilterFieldRule(globals);
    d.addRule("caldata/filters", new SectionRule(globals, "filters"));
    d.addRule("caldata/filters/filter", new FilterRule(globals));

    d.addRule("*/filter/?", ffr);

    ResourceFieldRule rfr = new ResourceFieldRule(globals);
    d.addRule("caldata/resources", new SectionRule(globals, sectionResources));
    d.addRule("caldata/resources/resource", new ResourceRule(globals));
    d.addRule("*/resource/?", rfr);
    d.addRule("*/resource/content", new ResourceContentRule(globals));
    d.addRule("*/resource/content/?", new ResourceContentFieldRule(globals));

    d.addRule("*/event/summaries/summary", strRule);
    d.addRule("*/event/summaries/summary/?", strFrule);

    d.addRule("*/event/descriptions/description", lstrRule);
    d.addRule("*/event/descriptions/description/?", lstrFrule);

    d.addRule("*/filter/descriptions/description", lstrRule);
    d.addRule("*/filter/descriptions/description/?", lstrFrule);

    d.addRule("*/filter/display-names/display-name", strRule);
    d.addRule("*/filter/display-names/display-name/?", strFrule);

    d.addRule("*/event/alarm", new AlarmRule(globals));
    d.addRule("*/event/alarm/?", alfr);
  }
}

