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
package org.bedework.calfacade.exc;

/** Exception somewhere in the calendar facade
 *
 * @author Mike Douglass douglm   rpi.edu
 */
public class CalFacadeException extends Exception {
  /* Property names used as message value. These should be used to
   * retrieve a localized message and can also be used to identify the
   * cause of the exception.
   *
   * Every CalFacadeException should have one of these as the getMessage()
   * value.
   */

  /* ****************** properties and options ************************** */

  /** Not allowed to set values */
  public static final String envCannotSetValues =
      "org.bedework.exception.envcannotsetvalues";

  /** Multiple getters for property xxx */
  public static final String envMultipleGetters =
      "org.bedework.exception.envmultiplegetters";

  /** No getters for property xxx */
  public static final String envNoGetters =
      "org.bedework.exception.envnogetters";

  /* ********************** dump/restore **************************** */

  /** No key fields defined */
  public static final String noKeyFields =
      "org.bedework.exception.nokeyfields";

  /* ********************** security related **************************** */

  /** Bad stored password format */
  public static final String badPwFormat =
      "org.bedework.exception.security.badpwformat";

  /* ********************** config app ********************************** */

  /** No optiones defined */
  public static final String configNoOptions =
      "org.bedework.exception.confignooptions";

  /** Missing configurations */
  public static final String noconfigs =
      "org.bedework.config.error.noconfigs";

  /** No metadata */
  public static final String noMetadata =
      "org.bedework.config.error.nometadata";

  /** Missing required value */
  public static final String configMissingValue =
      "org.bedework.config.error.missingvalue";

  /* ****************** principals and ids ****************************** */

  /** principal does not exist */
  public static final String principalNotFound =
      "org.bedework.exception.principalnotfound";

  /** unknown principal type */
  public static final String unknownPrincipalType =
      "org.bedework.exception.unknownprincipaltype";

  /** bad calendar user address */
  public static final String badCalendarUserAddr =
    "org.bedework.caladdr.bad";

  /** null calendar user address */
  public static final String nullCalendarUserAddr =
      "org.bedework.caladdr.null";

  /* ****************** domains ****************************** */

  /** no default domain defined */
  public static final String noDefaultDomain =
      "org.bedework.domains.nodefault";

  /* ****************** (Admin) groups ****************************** */

  /** The admin group already exists */
  public static final String duplicateAdminGroup =
      "org.bedework.exception.duplicateadmingroup";

  /** The group is already on the path to the root (makes a loop) */
  public static final String alreadyOnGroupPath =
      "org.bedework.exception.alreadyonagrouppath";

  /** Group g does not exist */
  public static final String groupNotFound =
      "org.bedework.exception.groupnotfound";

  /* ****************** Calendar suites ****************************** */

  /** The calendar suite does not exist */
  public static final String unknownCalsuite =
      "org.bedework.svci.unknown.calsuite";

  /** */
  public static final String duplicateCalsuite =
      "org.bedework.svci.duplicate.calsuite";

  /** */
  public static final String calsuiteGroupNameTooLong =
      "org.bedework.svci.calsuite.group.name.too.long";

  /** */
  public static final String calsuiteUnknownRootCollection =
      "org.bedework.svci.calsuite.unknown.root.collection";

  /** */
  public static final String calsuiteUnknownSubmissionsCollection =
      "org.bedework.svci.calsuite.unknown.submissions.collection";

  /** No calendar suite defined for this group */
  public static final String noCalsuite =
      "org.bedework.svci.no.calsuite";

  /** Missing or invalid group owner */
  public static final String calsuiteBadowner =
      "org.bedework.svci.calsuite.badowner";

  /** group already assigned to (another) calsuite */
  public static final String calsuiteGroupAssigned =
      "org.bedework.svci.calsuite.group.assigned";

  /** group has no home */
  public static final String missingGroupOwnerHome =
      "org.bedework.svci.calsuite.missing.group.owner.home";

  /** No calendar suite resource collection defined */
  public static final String noCalsuiteResCol =
      "org.bedework.svci.no.calsuite.resource.collection";

  /* ****************** Filters ****************************** */

  /** Duplicate filter */
  public static final String duplicateFilter =
      "org.bedework.exception.duplicate.filter";

  /** The filter does not exist */
  public static final String unknownFilter =
      "org.bedework.exception.unknown.filter";

  /** Unexpected eof while parsing */
  public static final String unexpectedFilterEof =
      "org.bedework.exception.filter.unexpected.eof";

  /** expected uid */
  public static final String filterBadList =
      "org.bedework.exception.filter.bad.list";

  /** expected a word */
  public static final String filterExpectedWord =
      "org.bedework.exception.filter.expected.word";

  /** expected open paren */
  public static final String filterExpectedOpenParen =
      "org.bedework.exception.filter.expected.openparen";

  /** expected close paren */
  public static final String filterExpectedCloseParen =
      "org.bedework.exception.filter.expected.closeparen";

  /** expected property name */
  public static final String filterExpectedPropertyName =
      "org.bedework.exception.filter.expected.propertyname";

  /** expected ascending/descending */
  public static final String filterExpectedAscDesc =
          "org.bedework.exception.filter.expected.ascdesc";

  /** Bad sort expression */
  public static final String filterBadSort =
          "org.bedework.exception.filter.badsort";

  /** expected uid */
  public static final String filterExpectedUid =
      "org.bedework.exception.filter.expected.uid";

  /** Cannot mix logical operators */
  public static final String filterMixedLogicalOperators =
      "org.bedework.exception.filter.mixedlogicaloperators";

  /** Bad property value */
  public static final String filterBadProperty =
      "org.bedework.exception.filter.badproperty";

  /** Bad operator */
  public static final String filterBadOperator =
      "org.bedework.exception.filter.badoperator";

  /** Syntax error */
  public static final String filterSyntax =
      "org.bedework.exception.filter.syntax";

  /** Type requires andop */
  public static final String filterTypeNeedsAnd =
      "org.bedework.exception.filter.typeneedsand";

  /** Bad type value */
  public static final String filterBadType =
      "org.bedework.exception.filter.badtype";

  /** Type must come first */
  public static final String filterTypeFirst =
      "org.bedework.exception.filter.typefirst";

  /* ****************** Resources ****************************** */

  /** Missing resource content */
  public static final String missingResourceContent =
      "org.bedework.exception.missing.resource.content";

  /** Duplicate resource */
  public static final String duplicateResource =
      "org.bedework.exception.duplicate.resource";

  /** The resource does not exist */
  public static final String unknownResource =
      "org.bedework.exception.unknown.resource";

  /* ****************** Calendars ****************************** */

  /** Couldn't find calendar */
  public static final String collectionNotFound =
      "org.bedework.exception.calendarnotfound";

  /** Somebody tried to create a duplicate calendar */
  public static final String duplicateCalendar =
      "org.bedework.exception.duplicatecalendar";

  /** Somebody tried to delete a collection with children */
  public static final String collectionNotEmpty =
      "org.bedework.exception.calendarnotempty";

  /** */
  public static final String illegalCalendarCreation =
      "org.bedework.exception.illegalcalendarcreation";

  /** */
  public static final String cannotDeleteCalendarRoot =
      "org.bedework.exception.cannotdeletecalendarroot";

  /** */
  public static final String cannotDeleteDefaultCalendar =
      "org.bedework.exception.cannotdeletedefaultcalendar";

  /* ****************** Subscriptions ****************************** */

  /** Somebody tried to create a duplicate subscription */
  public static final String duplicateSubscription =
      "org.bedework.exception.duplicatesubscription";

  /** Subscription chain has a loop */
  public static final String subscriptionLoopDetected =
      "org.bedework.exception.subscriptionloop";

  /** Subscription failed */
  public static final String subscriptionFailed =
      "org.bedework.exception.subscriptionfailed";

  /* ****************** Ical translation **************************** */

  /** Tried to specify attendees for publish */
  public static final String attendeesInPublish =
      "org.bedework.exception.ical.attendeesinpublish";

  /** Tried to specify end and duration for an event */
  public static final String endAndDuration =
      "org.bedework.exception.ical.endandduration";

  /** Must have a guid */
  public static final String noGuid =
      "org.bedework.exception.ical.noguid";

  /* ****************** Users ****************************** */

  /** No such account */
  public static final String noSuchAccount =
      "org.bedework.exception.nosuchaccount";

  /* ****************** Events ****************************** */

  /** unknown property */
  public static final String unknownProperty =
      "org.bedework.exception.unknown.property";

  /** No calendar for this event */
  public static final String noEventCalendar =
      "org.bedework.exception.noeventcalendar";

  /** No guid for this event */
  public static final String noEventGuid =
      "org.bedework.exception.noeventguid";

  /** Missing required property for this event */
  public static final String missingEventProperty =
      "org.bedework.exception.missingeventproperty";

  /** No name for this event */
  public static final String noEventName =
      "org.bedework.exception.noeventname";

  /** The guid for this event already exists in this collection */
  public static final String duplicateGuid =
      "org.bedework.exception.duplicateguid";

  /** The name for this event already exists in this collection */
  public static final String duplicateName =
      "org.bedework.exception.duplicatename";

  /** Cannot locate instances for ... */
  public static final String cannotLocateInstance =
      "org.bedework.exception.cannotlocateinstance";

  /** There are no instances for this recurring event. */
  public static final String noRecurrenceInstances =
      "org.bedework.exception.norecurrenceinstances";

  /** There is no instances for this override. */
  public static final String invalidOverride =
      "org.bedework.error.invalid.override";

  /** Cannot supply overrides for nonrecurring event. */
  public static final String overridesForNonRecurring =
      "org.bedework.exception.overridesfornonrecurring";

  /* ****************** Scheduling ****************************** */

  /** Access is disallowed to any attendee. */
  public static final String schedulingAttendeeAccessDisallowed =
      "org.bedework.error.scheduling.attendeeaccessdisallowed";

  /** Attendee bad */
  public static final String schedulingBadAttendees =
      "org.bedework.error.scheduling.badttendees";

  /** Entity had a  bad method set */
  public static final String schedulingBadMethod =
      "org.bedework.error.scheduling.badmethod";

  /** A bad response method was attempted */
  public static final String schedulingBadResponseMethod =
      "org.bedework.error.scheduling.badresponsemethod";

  /** event is not in inbox */
  public static final String schedulingBadSourceCalendar =
      "org.bedework.error.scheduling.badsourcecalendar";

  /** invalid destination calendar for event */
  public static final String schedulingBadDestinationCalendar =
      "org.bedework.error.scheduling.baddestinationcalendar";

  /** Duplicate uid found in the target calendar  */
  public static final String schedulingDuplicateUid =
      "org.bedework.error.scheduling.duplicateuid";

  /** Expected exactly one attendee for reply. */
  public static final String schedulingExpectOneAttendee =
      "org.bedework.error.scheduling.expectoneattendee";

  /** Attendee partStat is bad. */
  public static final String schedulingInvalidPartStatus =
      "org.bedework.error.scheduling.invalidpartstatus";

  /** Multiple events were found in the target calendar  */
  public static final String schedulingMultipleEvents =
      "org.bedework.error.scheduling.multipleevents";

  /** You are not an attendee of the meeting. */
  public static final String schedulingNotAttendee =
      "org.bedework.error.scheduling.notattendee";

  /** Entity required attendees but had none. */
  public static final String schedulingNoAttendees =
      "org.bedework.error.scheduling.noattendees";

  /** Entity required originator but had none. */
  public static final String schedulingNoOriginator =
      "org.bedework.error.scheduling.noOriginator";

  /** Entity required recipients but had none. */
  public static final String schedulingNoRecipients =
      "org.bedework.error.scheduling.norecipients";

  /** Attendee for reply not in event. */
  public static final String schedulingUnknownAttendee =
      "org.bedework.error.scheduling.unknownattendee";

  /** Unknown event - organizer possibly deleted it?. */
  public static final String schedulingUnknownEvent =
      "org.bedework.error.scheduling.unknownevent";

  /** Invalid scheduling response. */
  public static final String schedulingBadResponse =
      "org.bedework.error.scheduling.badresponse";

  /** Invalid scheduling action. */
  public static final String schedulingBadAction =
      "org.bedework.error.scheduling.badaction";

  /** Invalid recipients for scheduling request. */
  public static final String schedulingBadRecipients =
      "org.bedework.error.scheduling.badrecipients";

  /** System error: Invalid freebusy granulator date limit - Must be DATETIME. */
  public static final String schedulingBadGranulatorDt =
      "org.bedework.error.scheduling.badgranulatordt";

  /** No default scheduling calendar. */
  public static final String schedulingNoCalendar =
      "org.bedework.error.scheduling.nocalendar";

  /* ****************** Timezones ****************************** */

  /** Error reading timezones */
  public static final String timezonesReadError =
      "org.bedework.error.timezones.readerror";

  /** Unknown timezones */
  public static final String unknownTimezone =
      "org.bedework.error.unknown.timezone";

  /** Bad date */
  public static final String badDate =
      "org.bedework.error.bad.date";

  /** No thread local timezones set */
  public static final String noThreadLocalTimezones =
      "org.bedework.error.nothreadlocaltimezones";

  /* ****************** Indexing ****************************** */

  /** */
  public static final String unindexableObjectClass =
      "org.bedework.exception.unindexableobjectclass";

  /** */
  public static final String notIndexPrincipal =
      "org.bedework.error.indexing.notprincipal";

  /** */
  public static final String notIndexDirectory =
      "org.bedework.error.lucene.notdirectory";

  /** */
  public static final String indexCreateFailed =
      "org.bedework.error.lucene.createfailed";

  /* ****************** Notifications ********************* */

  /** */
  public static final String noInvite =
      "org.bedework.notification.noinvite";

  /** */
  public static final String noInviteeInUsers =
      "org.bedework.notification.noinviteeinusers";

  /** */
  public static final String shareTargetNotFound =
      "org.bedework.notification.sharetargetnotfound";

  /* ****************** Misc ****************************** */

  /** */
  public static final String badSystemLocaleList =
      "org.bedework.exception.badsystemlocalelist";

  /** */
  public static final String badLocale =
      "org.bedework.exception.badlocale";

  /** */
  public static final String badRootUsersList =
      "org.bedework.exception.badrootuserslist";

  /** */
  public static final String illegalObjectClass =
      "org.bedework.exception.illegalobjectclass";

  /** */
  public static final String targetExists =
      "org.bedework.exception.targetexists";

  /** */
  public static final String badRequest = "org.bedework.exception.badrequest";

  /** */
  public static final String badResponse = "org.bedework.exception.badresponse";

  /** Used to indicate something you're not allowed to do -
   * not an access exception
   */
  public static final String forbidden = "org.bedework.exception.forbidden";

  /** */
  public static final String staleState =
      "org.bedework.exception.stalestate";

  private String extra;

  /** Constructor
   *
   */
  public CalFacadeException() {
    super();
  }

  /**
   * @param t
   */
  public CalFacadeException(final Throwable t) {
    super(t);
  }

  /**
   * @param s
   */
  public CalFacadeException(final String s) {
    super(s);
  }

  /**
   * @param s  - retrieve with getMessage(), property ame
   * @param extra String extra text
   */
  public CalFacadeException(final String s, final String extra) {
    super(s);
    this.extra = extra;
  }

  /**
   * @return String extra text
   */
  public String getExtra() {
    return extra;
  }

  /**
   * @return String message and 'extra'
   */
  @Override
  public String getMessage() {
    if (getExtra() != null) {
      return super.getMessage() + "\t" + getExtra();
    }

    return super.getMessage();
  }
}
