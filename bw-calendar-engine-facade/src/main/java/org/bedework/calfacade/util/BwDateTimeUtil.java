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
package org.bedework.calfacade.util;

import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.base.BwTimeRange;
import org.bedework.calfacade.exc.CalFacadeBadDateException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.locale.BwLocale;
import org.bedework.util.timezones.DateTimeUtil;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.timezones.TimezonesException;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date and time utilities
 *
 * @author Mike Douglass     douglm - bedework.edu
 *  @version 1.0
 */
public class BwDateTimeUtil {
  private static final DateFormat isoDateTimeUTCFormat =
      new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");

  private static final DateFormat rfcDateTimeUTCFormat =
    new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'");

  private static final DateFormat rfc822GMTFormat =
    new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");

  static {
    isoDateTimeUTCFormat.setTimeZone(java.util.TimeZone.getTimeZone(
                                 net.fortuna.ical4j.util.TimeZones.UTC_ID));
    isoDateTimeUTCFormat.setLenient(false);

    rfcDateTimeUTCFormat.setTimeZone(java.util.TimeZone.getTimeZone(
                                 net.fortuna.ical4j.util.TimeZones.UTC_ID));
    rfcDateTimeUTCFormat.setLenient(false);

    rfc822GMTFormat.setTimeZone(java.util.TimeZone.getTimeZone(
                                 net.fortuna.ical4j.util.TimeZones.UTC_ID));
  }

  private BwDateTimeUtil() {
  }

  /** Get a java.util.Date object from the value
   * XXX - this will neeed to be supplied with a tz repository
   *
   * @param val bw date time object
   * @return Date object representing the date
   * @throws CalFacadeBadDateException on bad date
   */
  public static Date getDate(final BwDateTime val)
          throws CalFacadeBadDateException {
    return getDate(val, Timezones.getTzRegistry());
  }

  /** Get a java.util.Date object from the value
   *
   * @param val bw date time object
   * @param tzreg registry
   * @return Date object representing the date
   * @throws CalFacadeBadDateException on bad date
   */
  public static Date getDate(final BwDateTime val,
                             final TimeZoneRegistry tzreg)
          throws CalFacadeBadDateException {
    String dtval = val.getDtval();

    try {
      if (val.getDateType()) {
        return DateTimeUtil.fromISODate(dtval);
      }

      if (dtval.endsWith("Z")) {
        return DateTimeUtil.fromISODateTimeUTC(dtval);
      }

      String tzid = val.getTzid();
      if (tzid == null) {
        return DateTimeUtil.fromISODateTime(dtval);
      }

      return DateTimeUtil.fromISODateTime(dtval,
                                          tzreg.getTimeZone(tzid));
    } catch (Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }

  /** Get a date/time object representing the given date and time
   *
   * @param date       Java Date object
   * @return BwDateTime object representing the date
   */
  public static BwDateTime getDateTime(final Date date) {
    String dtval = DateTimeUtil.isoDateTime(date);
    try {
      return getDateTime(dtval, false, false, null);
    } catch (CalFacadeException e) {
      // Shoudl never happen
      throw new RuntimeException(e);
    }
  }

  /** Get a date object representing the given date and flags
   *
   * @param date       String iso date or date/time
   * @param dateOnly   true for date-only value
   * @param floating    boolean true if this is a floating time
   * @param tzid - String tzid or null for default, UTC or floating.
   * @return Date object representing the date
   * @throws CalFacadeException on timezone error or bad date
   */
  public static BwDateTime getDateTime(String date, final boolean dateOnly,
                                       final boolean floating,
                                       String tzid) throws CalFacadeException {
    try {
      TimeZone tz = null;

      if (dateOnly || floating) {
        tzid = null;
      }

      if (tzid != null) {
        tz = Timezones.getTz(tzid);
        if (tz == null) {
          throw new CalFacadeException(CalFacadeException.unknownTimezone, tzid);
        }
      } else if (!floating) {
        // Asking for default
        tzid = Timezones.getThreadDefaultTzid();
        tz = Timezones.getDefaultTz();
      }

      if (DateTimeUtil.isISODateTimeUTC(date)) {
        // Convert to local time (relative to supplied timezone)

        Date dt = DateTimeUtil.fromISODateTimeUTC(date);
        date = DateTimeUtil.isoDateTime(dt, tz);
      }

      return BwDateTime.makeBwDateTime(dateOnly, date, tzid);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (TimezonesException tze) {
      throw new CalFacadeException(tze);
    } catch (Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }

  /** Get a date object representing the given String UTC date/time
   *
   * @param date UTC date time value
   * @return BwDateTime object representing the date
   */
  public static BwDateTime getDateTimeUTC(final String date) {
    return BwDateTime.fromUTC(false, date);
  }

  /** Get a date/time range given by the rfc formatted parameters and limited to
   * the given max range
   *
   * @param start date or date/time
   * @param end date or date/time
   * @param defaultField Calendar field id
   * @param defaultVal value for field
   * @param maxField Calendar field id
   * @param maxVal - 0 for no max
   * @return TimeRange or null for bad request
   * @throws CalFacadeException on bad date
   */
  public static BwTimeRange getPeriod(final String start,
                                      final String end,
                                      final int defaultField,
                                      final int defaultVal,
                                      final int maxField,
                                      final int maxVal) throws CalFacadeException {
    Locale loc = BwLocale.getLocale();
    Calendar startCal = Calendar.getInstance(loc);
    startCal.set(Calendar.HOUR_OF_DAY, 0);
    startCal.set(Calendar.MINUTE, 0);
    startCal.set(Calendar.SECOND, 0);

    Calendar endCal = Calendar.getInstance(loc);
    endCal.set(Calendar.HOUR_OF_DAY, 0);
    endCal.set(Calendar.MINUTE, 0);
    endCal.set(Calendar.SECOND, 0);

    if (start != null) {
      startCal.setTime(fromDate(start));
    }

    if (end == null) {
      endCal.setTime(startCal.getTime());
      endCal.add(defaultField, defaultVal);
    } else {
      endCal.setTime(fromDate(end));
    }

    // Don't allow more than the max
    if (maxVal > 0) {
      Calendar check = Calendar.getInstance(loc);
      check.setTime(startCal.getTime());
      check.add(maxField, maxVal);

      if (check.before(endCal)) {
        return null;
      }
    }

    return new BwTimeRange(
        BwDateTimeUtil.getDateTime(
               DateTimeUtil.isoDateTime(startCal.getTime()),
                                        false,
                                        false,   // floating
                                        null),   // tzid
        BwDateTimeUtil.getDateTime(
             DateTimeUtil.isoDateTime(endCal.getTime()),
                                      false,
                                      false,   // floating
                                      null));   // tzid
  }

  private static Date fromDate(final String dt) throws CalFacadeException {
    try {
      if (dt.contains("T")) {
        if (!dt.contains("-")) {
          return DateTimeUtil.fromISODateTimeUTC(dt);
        }

        return DateTimeUtil.fromRfcDateTimeUTC(dt);
      }

      if (!dt.contains("-")) {
        return DateTimeUtil.fromISODate(dt);
      }

      return DateTimeUtil.fromRfcDate(dt);
    } catch (Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }
}
