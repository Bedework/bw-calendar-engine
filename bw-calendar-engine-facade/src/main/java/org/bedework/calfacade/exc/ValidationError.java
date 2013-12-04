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

import java.io.Serializable;

/**
 * Define error property codes emitted by validation code. Suggested English Language
 * is supplied in the comment for clarification only.
 *
 * @author Mike Douglass
 *
 */
public class ValidationError implements Serializable {
  /** Prefix for all these errors */
  public static final String prefix = "org.bedework.validation.error.";

  /** Cannot change schedule methode. */
  public static final String cannotChangeMethod = prefix + "cannot.change.method";

  /** Your information is incorrect: please supply exactly one attendee. */
  public static final String expectOneAttendee = prefix + "expectoneattendee";

  /** Error: in submissions calendar. */
  public static final String inSubmissionsCalendar = prefix + "insubmissionscalendar";

  /** Error: bad how. */
  public static final String invalidAccessHow = prefix + "invalid.how";

  /** Error: bad who type (user or group). */
  public static final String invalidAccessWhoType = prefix + "invalid.whotype";

  /** The attendee uri is invalid. */
  public static final String invalidAttendee = prefix + "invalid.attendee";

  /** Bad or out-of-range date.. */
  public static final String invalidDate = prefix + "invalid.date";

  /** <em>Invalid duration</em> - you may not have a zero-length duration
  for an all day event. */
  public static final String invalidDuration = prefix + "invalid.duration";

  /** The <em>entity type</em> is invalid. */
  public static final String invalidEntityType = prefix + "invalid.entitytype";

  /** The <em>end date type</em> is invalid. */
  public static final String invalidEndtype = prefix + "invalid.endtype";

  /** The organizer uri is invalid. */
  public static final String invalidOrganizer = prefix + "invalid.organizer";

  /** Error: invalid auto cancel preference. */
  public static final String invalidPrefAutoCancel = prefix + "invalid.invalidprefautocancel";

  /** Error: invalid auto process response preference. */
  public static final String invalidPrefAutoProcess = prefix + "invalid.invalidprefautoprocess";

  /** Error: invalid date range end type preference. */
  public static final String invalidPrefEndType = prefix + "invalid.prefendtype";

  /** Error: invalid user mode preference. */
  public static final String invalidPrefUserMode = prefix + "invalid.prefusermode";

  /** Error: invalid page size. */
  public static final String invalidPageSize = prefix + "invalid.pagesize";

  /** Error: invalid workday end. */
  public static final String invalidPrefWorkDayEnd = prefix + "invalid.prefworkdayend";

  /** Error: invalid working days: start after end */
  public static final String invalidPrefWorkDays = prefix + "invalid.prefworkdays";

  /** Error: invalid workday start. */
  public static final String invalidPrefWorkDayStart = prefix + "invalid.prefworkdaystart";

  /** The recipient uri is invalid. */
  public static final String invalidRecipient = prefix + "invalid.recipient";

  /** Error: bad value for recurrence count */
  public static final String invalidRecurCount = prefix + "invalid.recurcount";

  /** Error: Cannot specify count and until for recurrence */
  public static final String invalidRecurCountAndUntil = prefix + "invalid.recurcountanduntil";

  /** Error: bad value for recurrence interval */
  public static final String invalidRecurInterval = prefix + "invalid.recurinterval";

  /** Error: bad value for recurrence rule */
  public static final String invalidRecurRule = prefix + "invalid.recurrule";

  /** Error: bad value for recurrence until */
  public static final String invalidRecurUntil = prefix + "invalid.recuruntil";

  /** Error: bad scheduling data. */
  public static final String invalidSchedData = prefix + "invalid.scheddata";

  /** Error: bad scheduling method (should be request or publish). */
  public static final String invalidSchedMethod = prefix + "invalid.schedmethod";

  /** Error: bad scheduling part status . */
  public static final String invalidSchedPartStatus = prefix + "invalid.schedpartstatus";

  /** Error: bad scheduling method (should be reply). */
  public static final String invalidSchedReplyMethod = prefix + "invalid.schedreplymethod";

  /** Error: bad scheduling method (should be request). */
  public static final String invalidSchedRequestMethod = prefix + "invalid.schedrequestmethod";

  /** Error: bad scheduling method (should be COUNTER, REFRESH, REPLY). */
  public static final String invalidSchedRespondMethod = prefix + "invalid.schedrespondmethod";

  /** Error: Invalid status. */
  public static final String invalidStatus = prefix + "invalid.status";

  /** Error: Invalid transparency. */
  public static final String invalidTransparency = prefix + "invalid.transparency";

  /** Error: Invalid URI. */
  public static final String invalidUri = prefix + "invalid.uri";

  /** Error: Invalid user. */
  public static final String invalidUser = prefix + "invalid.user";

  /** Error: Invalid xprop. */
  public static final String invalidXprop = prefix + "invalid.xprop";

  /** Error: missing how.*/
  public static final String missingAccessHow = prefix + "missinghow";

  /** Error: missing who (principal name).*/
  public static final String missingAccessWho = prefix + "missingwho";

  /** Your information is incomplete: please supply an address. */
  public static final String missingAddress = prefix + "missingaddress";

  /** Your information is incomplete: please supply a calendar. */
  public static final String missingCalendar = prefix + "missingcalendar";

  /** Your information is incomplete: please supply at least one topical area. */
  public static final String missingTopic = prefix + "missingtopicalarea";

  /** Your information is incomplete: please supply a calendar path. */
  public static final String missingCalendarPath = prefix + "missingcalendarpath";

  /** Your information is incomplete: please supply a root calendar path. */
  public static final String missingCalsuiteCalendar = prefix + "missingcalsuitecalendar";

  /** Your information is incomplete: please supply a category keyword. */
  public static final String missingCategoryKeyword = prefix + "missingcategorykeyword";

  /** Your information is incomplete: please supply a contact. */
  public static final String missingContact = prefix + "missingcontact";

  /** You must enter a contact <em>name</em>. */
  public static final String missingContactName = prefix + "missingcontactname";

  /** Your information is incomplete: please supply a description. */
  public static final String missingDescription = prefix + "missingdescription";

  /** Your information is incomplete: please supply an event owner. */
  public static final String missingEventOwner = prefix + "missingeventowner";

  /** Your information is incomplete: please supply a filter definition. */
  public static final String missingFilterDef = prefix + "missingfilterdef";

  /** Your information is incomplete: please supply a group name. */
  public static final String missingGroupName = prefix + "missinggroupname";

  /** Your information is incomplete: please supply a group owner. */
  public static final String missingGroupOwner = prefix + "missinggroupowner";

  /** group owner home inaccessible. */
  public static final String missingGroupOwnerHome = prefix + "missinggroupownerhome";

  /** Your information is incomplete: please supply a location. */
  public static final String missingLocation = prefix + "missinglocation";

  /** The location is not flagged as public. */
  public static final String locationNotPublic = prefix + "locationnotpublic";

  /** Your information is incomplete: please supply a name. */
  public static final String missingName = prefix + "missingname";

  /** Your information is incomplete: please supply a class. */
  public static final String missingClass = prefix + "missingclass";

  /** Your information is incomplete: please supply a content type. */
  public static final String missingContentType = prefix + "missingcontenttype";

  /** Your information is incomplete: please supply a type. */
  public static final String missingType = prefix + "missingtype";

  /** Your event is missing the originator */
  public static final String missingOriginator = prefix + "missingoriginator";

  /** Your event is missing the organizer */
  public static final String missingOrganizer = prefix + "missingorganizer";

  /** Cannot change this into a scheduling message */
  public static final String invalidSchedulingObject = prefix + "invalidschedulingobject";

  /** You must supply a recipient. */
  public static final String missingRecipients = prefix + "missingrecipients";

  /** Your information is incomplete: please supply a subscription id. */
  public static final String missingSubscriptionid = prefix + "missingsubscriptionid";

  /** Your information is incomplete: please supply a title. */
  public static final String missingTitle = prefix + "missingtitle";

  /** Your information is incomplete: please supply a uri for the subscription. */
  public static final String missingSubUri = prefix + "missingsuburi";

  /** Error: not in submissions calendar. */
  public static final String notSubmissionsCalendar = prefix + "notsubmissionscalendar";

  /** The <em>end date</em> occurs before the <em>start date</em>. */
  public static final String startAfterEnd = prefix + "startafterend";

  /** The object is too large. size is ?, maximum allowable is ? */
  public static final String tooLarge = prefix + "toolarge";

  /** Your description is too long.  Please limit your entry to
      characters.  You may also wish to
      point the event entry at a supplemental web page by entering a <em>URL</em>. */
  public static final String tooLongDescription = prefix + "toolong.description";

  /** Your name is too long.  Please limit your entry to  */
  public static final String tooLongName = prefix + "toolong.name";

  /** Your summary is too long.  Please limit your entry to
      characters.  You may also wish to
      point the event entry at a supplemental web page by entering a <em>URL</em>. */
  public static final String tooLongSummary = prefix + "toolong.summary";

  /** Too many attendees. */
  public static final String tooManyAttendees = prefix + "toomany.attendees";

  private String errorCode;

  private String extra;

  /**
   * @param errorCode
   */
  public ValidationError(final String errorCode) {
    this(errorCode, null);
  }

  /**
   * @param errorCode
   * @param extra
   */
  public ValidationError(final String errorCode, final String extra) {
    this.errorCode = errorCode;
    this.extra = extra;
  }

  /**
   * @param val
   */
  public void setErrorCode(final String val) {
    errorCode = val;
  }

  /**
   * @return String
   */
  public String getErrorCode() {
    return errorCode;
  }

  /**
   * @param val
   */
  public void setExtra(final String val) {
    extra = val;
  }

  /**
   * @return String
   */
  public String getExtra() {
    return extra;
  }
}
