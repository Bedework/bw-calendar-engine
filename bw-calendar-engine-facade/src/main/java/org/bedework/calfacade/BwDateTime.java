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
package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.ical.IcalProperties;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.base.BwCloneable;
import org.bedework.calfacade.base.DumpEntity;
import org.bedework.calfacade.exc.CalFacadeBadDateException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.XcalUtil;
import org.bedework.util.timezones.DateTimeUtil;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.timezones.TimezonesException;

import ietf.params.xml.ns.icalendar_2.DateDatetimePropertyType;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Comparator;

/** Class to represent an RFC2445 date and datetime type. These are not stored
 * in separate tables but as components of the including class.
 *
 * <p>DateTime values take 3 forms:
 * <br/>Floating time - no timezone e.g. DTSTART:19980118T230000
 * <br/>UTC time no timezone e.g. DTSTART:19980118T230000Z
 * <br/>Local time with timezone e.g. DTSTART;TZID=US-Eastern:19980119T020000
 *
 * @author Mike Douglass
 *  @version 1.0
 */
@Dump(elementName="date-time", keyFields={"dateType", "tzid", "dtval", "date"})
public class BwDateTime extends DumpEntity<BwDateTime>
    implements BwCloneable, Comparable<BwDateTime>, Comparator<BwDateTime>, Serializable {
  /** If true this represents a date - not datetime.
   */
  private boolean dateType;

  /** Non-null if one was specified.
   */
  private String tzid;

  private String dtval; // rfc2445 date or datetime value

  private String range; // Only for recurrence id

  /**   */
  public static Dur oneDayForward = new Dur(1, 0, 0, 0);
  /**   */
  public static Dur oneDayBack = new Dur(-1, 0, 0, 0);

  /** This is a UTC datetime value to make searching easier. There are a number of
   * complications to dates, the end date is specified as non-inclusive
   * but there are a number of boundary problems to watch out for.
   *
   * <p>For date only values this field has a zero time appended so that simple
   * string comparisons will work.
   */
  private String date; // For indexing

  private Boolean floatFlag;

  /** Constructor
   */
  private BwDateTime() {
  }

  /** Constructor
   *
   * @param dateType
   * @param date
   * @param tzid
   * @return initialised BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final boolean dateType,
                                          final String date,
                                          final String tzid) throws CalFacadeException {
    try {
      if (dateType) {
        if (!DateTimeUtil.isISODate(date)) {
          throw new CalFacadeException("org.bedework.datetime.expect.dateonly");
        }
      }

      BwDateTime bwd = new BwDateTime();
      bwd.setDateType(dateType);
      bwd.setDtval(date);
      bwd.setTzid(tzid);

      if (tzid == null) {
        if (DateTimeUtil.isISODateTime(date)) {
          bwd.setFloatFlag(true);
          bwd.setDate(date + "Z");
        }
      }

      if (!bwd.getFloating()) {
        bwd.setDate(Timezones.getUtc(date, tzid));
      }

      return bwd;
    } catch (TimezonesException tze) {
      if (tze.getMessage().equals(TimezonesException.unknownTimezone)) {
        throw new CalFacadeException(CalFacadeException.unknownTimezone,
                                     tze.getExtra());
      } else {
        throw new CalFacadeBadDateException();
      }
    } catch (Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }

  /** Make date time based on all properties
   *
   * @param dateType
   * @param date
   * @param utcDate
   * @param tzid
   * @param floating
   * @return initialised BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final boolean dateType,
                                          final String date,
                                          final String utcDate,
                                          final String tzid,
                                          final boolean floating) throws CalFacadeException {
    try {
      if (dateType) {
        if (!DateTimeUtil.isISODate(date)) {
          throw new CalFacadeException("org.bedework.datetime.expect.dateonly");
        }
      }

      BwDateTime bwd = new BwDateTime();
      bwd.setDateType(dateType);
      bwd.setDtval(date);
      bwd.setDate(utcDate);
      bwd.setTzid(tzid);
      bwd.setFloatFlag(floating);

      return bwd;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }

  /** Make date time based on ical property
   *
   * @param val
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final DateProperty val) throws CalFacadeException {
    Parameter par = getIcalParameter(val, "VALUE");

    BwDateTime bwdt = makeBwDateTime((par != null) && (par.equals(Value.DATE)),
                                     val.getValue(),
                                     getTzid(val));

    par = getIcalParameter(val, "RANGE");
    if (par != null) {
      /* XXX What do I do with it? */
      bwdt.range = par.getValue();
    }

    return bwdt;
  }

  /** Make date time based on the xcal property
   *
   * @param val
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final DateDatetimePropertyType val) throws CalFacadeException {
    XcalUtil.DtTzid dtTzid = XcalUtil.getDtTzid(val);

    BwDateTime bwdt = makeBwDateTime(dtTzid.dateOnly,
                                     dtTzid.dt,
                                     dtTzid.tzid);

    return bwdt;
  }

  /** Make date time based on the xcal property with tzid supplied
   *
   * @param val
   * @param tzid
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final DateDatetimePropertyType val,
                                          final String tzid) throws CalFacadeException {
    XcalUtil.DtTzid dtTzid = XcalUtil.getDtTzid(val);

    BwDateTime bwdt = makeBwDateTime(dtTzid.dateOnly,
                                     dtTzid.dt,
                                     tzid);

    return bwdt;
  }

  /**
   * @param val
   * @return initialised BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeBwDateTime(final Date val) throws CalFacadeException {
    String tzid = null;
    boolean dateType = true;

    if (val instanceof DateTime) {
      dateType = false;
      TimeZone tz = ((DateTime)val).getTimeZone();
      if (tz != null) {
        tzid = tz.getID();
      }
    }

    return makeBwDateTime(dateType, val.toString(), tzid);
  }

  /** Make a new date time value based on the dtStart value + the duration.
   *
   * @param dtStart
   * @param dateOnly
   * @param dur
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime makeDateTime(final DateProperty dtStart,
                                        final boolean dateOnly,
                                        final Dur dur) throws CalFacadeException {
    DtEnd dtEnd;
    java.util.Date endDt = dur.getTime(dtStart.getDate());

    Parameter tzid = getIcalParameter(dtStart, "TZID");

    if (dateOnly) {
      //dtEnd = new DtEnd(new Date(endDt));
      ParameterList parl =  new ParameterList();

      parl.add(Value.DATE);

      dtEnd = new DtEnd(parl, new Date(endDt));
      //addIcalParameter(dtEnd, Value.DATE);
      //if (tzid != null) {
      //  addIcalParameter(dtEnd, tzid);
      //}
    } else {
      DateTime d = new DateTime(endDt);
      if (tzid != null) {
        DateTime sd = (DateTime)dtStart.getDate();

        d.setTimeZone(sd.getTimeZone());
      }
//          dtEnd = new DtEnd(d, dtStart.isUtc());
      dtEnd = new DtEnd(d);
      if (tzid != null) {
        addIcalParameter(dtEnd, tzid);
      } else if (dtStart.isUtc()) {
        dtEnd.setUtc(true);
      }
    }

    return makeBwDateTime(dtEnd);
  }

  /** Create from utc time
   *
   * @param dateType
   * @param date
   * @return initialised BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime fromUTC(final boolean dateType,
                                   final String date) throws CalFacadeException {
    if (!dateType && !date.endsWith("Z")) {
      throw new CalFacadeBadDateException();
    }

    BwDateTime bwd = new BwDateTime();
    bwd.setDateType(dateType);
    bwd.setDtval(date);
    bwd.setTzid(null);

    if (dateType) {
      bwd.setDate(date + "T000000Z");
    } else {
      bwd.setDate(date);
    }

    return bwd;
  }

  /** Create from utc time with timezone
   *
   * @param dateType true for a date value
   * @param date the string UTC date/time or 8 character date
   * @param tzid  tzid for local time
   * @return initialised BwDateTime
   * @throws CalFacadeException
   */
  public static BwDateTime fromUTC(final boolean dateType,
                                   final String date,
                                   final String tzid) throws CalFacadeException {
    if (!dateType && !date.endsWith("Z")) {
      throw new CalFacadeBadDateException();
    }

    try {
      final BwDateTime bwd = new BwDateTime();
      bwd.setDateType(dateType);

      if (dateType | (tzid == null)) {
        bwd.setDtval(date);
        bwd.setTzid(null);
      } else {
        final java.util.Date dt = DateTimeUtil.fromISODateTimeUTC(date);

        bwd.setDtval(DateTimeUtil.isoDateTime(dt, Timezones.getTz(tzid)));
        bwd.setTzid(tzid);
      }

      if (dateType) {
        bwd.setDate(date + "T000000Z");
      } else {
        bwd.setDate(date);
      }

      return bwd;
    } catch (final Throwable t) {
      throw new CalFacadeBadDateException();
    }
  }

  /** Get the date/times dateType
   *
   * @return boolean    true for a date only type
   */
  public boolean getDateType() {
    return dateType;
  }

  /** Get the tzid
   *
   * @return String    tzid
   */
  @IcalProperties({
    @IcalProperty(pindex = PropertyInfoIndex.TZIDPAR,
                  dbFieldName = "tzid",
                  param = true),
    @IcalProperty(pindex = PropertyInfoIndex.TZID)
                  })

  public String getTzid() {
    return tzid;
  }

  /** Get the dtval - the rfc2445 date or datetime value
   *
   * @return String   dtval
   */
  public String getDtval() {
    return dtval;
  }

  /** Get the range for a query
   *
   * @return String range
   */
  public String getRange() {
    return range;
  }

  /** Set the date as a datetime value for comparisons.
   *
   * <p>This is a UTC datetime value to make searching easier. There are a number of
   * complications to dates, the end date is specified as non-inclusive
   * but there are a number of boundary problems to watch out for.
   *
   * <p>For date only values this field has a zero time appended so that simple
   * string comparisons will work.
   *
   * @param val
   */
  @IcalProperty(pindex = PropertyInfoIndex.UTC,
                jname = "utc")
  private void setDate(final String val) {
    date = val;
  }

  /** This is a UTC datetime value to make searching easier. There are a number of
   * complications to dates, the end date is specified as non-inclusive
   * but there are a number of boundary problems to watch out for.
   *
   * <p>For date only values this field has a zero time appended so that simple
   * string comparisons will work.
   *
   * <p>For floating time values this is the same as the dtval made to look like
   * a UTC value.
   *
   * @return String date
   */
  @IcalProperty(pindex = PropertyInfoIndex.LOCAL,
                jname = "local")
  public String getDate() {
    return date;
  }

  /** For non floating values this should be null rather than false. This is
   * sort of a db thing (maybe possible to hide it).
   *
   * <p>Virtually all events are expected to be non-floating but we need to index
   * floating v non-floating.
   *
   * <p>A null value will usually not be indexed so the index will consist of
   * only those that are flaoting time.
   *
   * @param val
   */
  @IcalProperty(pindex = PropertyInfoIndex.FLOATING,
                jname = "floating")
  private void setFloatFlag(final Boolean val) {
    floatFlag = val;
  }

  /**
   * @return Boolean or null
   */
  public Boolean getFloatFlag() {
    return floatFlag;
  }

  /* ====================================================================
   *                        Conversion methods
   * ==================================================================== */

  /**
   * @return boolean
   */
  public boolean getFloating() {
    if (getFloatFlag() == null) {
      return false;
    }
    return floatFlag;
  }

  /**
   * @return true if this represents a valid UTC date
   */
  public boolean isUTC() {
    if (getDateType()) {
      return false;
    }

    try {
      return DateTimeUtil.isISODateTimeUTC(getDtval());
    } catch (Throwable t) {
      return false;
    }
  }

  /** Make a DtEnd from this object
   *
   * @return DtEnd
   * @throws CalFacadeException
   */
  public DtEnd makeDtEnd() throws CalFacadeException {
    return makeDtEnd(Timezones.getTzRegistry());
  }

  /** Make a DtEnd from this object
   *
   * @param tzreg
   * @return DtEnd
   * @throws CalFacadeException
   */
  public DtEnd makeDtEnd(final TimeZoneRegistry tzreg) throws CalFacadeException {
    try {
      DtEnd dt = new DtEnd();

      initDateProp(dt, tzreg);

      return dt;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Make a Due from this object
   *
   * @param tzreg
   * @return Due
   * @throws CalFacadeException
   */
  public Due makeDue(final TimeZoneRegistry tzreg) throws CalFacadeException {
    try {
      Due dt = new Due();

      initDateProp(dt, tzreg);

      return dt;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Create a copy of this object
   *
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public BwDateTime copy() throws CalFacadeException {
    return makeBwDateTime(makeDtEnd());
  }

  /** Make a DtStart from this object
   *
   * @return DtStart
   * @throws CalFacadeException
   */
  public DtStart makeDtStart() throws CalFacadeException {
    return makeDtStart(Timezones.getTzRegistry());
  }

  /** Make a DtStart from this object
   *
   * @param tzreg
   * @return DtStart
   * @throws CalFacadeException
   */
  public DtStart makeDtStart(final TimeZoneRegistry tzreg) throws CalFacadeException {
    try {
      /*
      String tzid = null;
      ParameterList pl = new ParameterList();

      if (getDateType()) {
        pl.add(Value.DATE);
      } else if (!isUTC()) {
        tzid = getTzid();
        if (tzid != null) {
          pl.add(new TzId(tzid));
        }
      }

      return new DtStart(pl, getDtval());*/
      String tzid = getTzid();
      DtStart dt = new DtStart();

      ParameterList pl = dt.getParameters();

      if (getDateType()) {
        pl.add(Value.DATE);
      } else if (tzid != null) {
        dt.setTimeZone(tzreg.getTimeZone(tzid));
      }

      dt.setValue(getDtval());

      return dt;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Return an ical DateTime or Date object
   *
   * @return Date
   * @throws CalFacadeException
   */
  public Date makeDate() throws CalFacadeException {
    try {
      if (getDateType()) {
        return new Date(getDtval());
      }

      if (tzid != null) {
        return new DateTime(getDtval(), Timezones.getTz(tzid));
      }

      return new DateTime(getDtval());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Make an ical Dur from a start and end
   *
   * @param start
   * @param end
   * @return Dur
   * @throws CalFacadeException
   */
  public static Dur makeDuration(final BwDateTime start,
                                 final BwDateTime end) throws CalFacadeException {
    return new Dur(start.makeDate(),
                   end.makeDate());
  }

  /** Return a value based on this value plus a duration.
   *
   * @param val
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public BwDateTime addDuration(final BwDuration val) throws CalFacadeException {
    return addDuration(val.makeDuration().getDuration());
  }

  private static Calendar dateIncrementor =
      Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT"));

  /** For a date only object returns a date 1 day in advance of this date.
   * Used when moving between displayed and internal values and also when
   * breaking a collection of events up into days.
   *
   * @return BwDateTime tomorrow
   * @throws CalFacadeException
   */
  public BwDateTime getNextDay() throws CalFacadeException {
    if (!getDateType()) {
      throw new CalFacadeException("org.bedework.datetime.expect.dateonly");
    }

    try {
      Date dt = new Date(getDtval());

      synchronized (dateIncrementor) {
        dateIncrementor.setTime(dt);

        dateIncrementor.add(Calendar.DATE, 1);

        dt = new Date(dateIncrementor.getTime());
      }

      return makeBwDateTime(dt);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** For a date only object returns a date 1 day previous to this date.
   * Used when moving between displayed and internal values.
   *
   * @return BwDateTime yesterday
   * @throws CalFacadeException
   */
  public BwDateTime getPreviousDay() throws CalFacadeException {
    if (!getDateType()) {
      throw new CalFacadeException("Must be a date only value");
    }

    return makeDateTime(makeDtStart(Timezones.getTzRegistry()),
                        true, oneDayBack);
  }

  /** Add a duration and return the result
   *
   * @param d      Dur
   * @return BwDateTime
   * @throws CalFacadeException
   */
  public BwDateTime addDur(final Dur d) throws CalFacadeException {
    return makeDateTime(makeDtStart(Timezones.getTzRegistry()),
                        getDateType(), d);
  }

  /* ====================================================================
   *                        private methods
   * ==================================================================== */

  /** Set the dateType for this timezone
   *
   * @param val   boolean dateType
   */
  private void setDateType(final boolean val) {
    dateType = val;
  }

  /**  Set the tzid. This should not be used to change the timezone for a value
   * as we may need to recalculate other values based on that change.
   *
   * @param val    String tzid
   */
  private void setTzid(final String val) {
    tzid = val;
  }

  /** Set the dtval - the rfc2445 date or datetime value
   *
   * @param val    String dtval
   */
  private void setDtval(final String val) {
    dtval = val;
    if (val == null) {
      setDate(null);
    }
  }

  /** Add val to prop
   *
   * @param prop
   * @param val
   */
  private static void addIcalParameter(final Property prop, final Parameter val) {
    ParameterList parl =  prop.getParameters();

    parl.add(val);
  }

  /** Get named parameter from prop
   *
   * @param prop
   * @param name
   * @return Parameter
   */
  private static Parameter getIcalParameter(final Property prop, final String name) {
    ParameterList parl =  prop.getParameters();

    if (parl == null) {
      return null;
    }

    return parl.getParameter(name);
  }

  /** Return the timezone id if it is set for the property.
   *
   * @param val
   * @return String tzid or null.
   */
  private static String getTzid(final DateProperty val) {
    Parameter tzidPar = getIcalParameter(val, "TZID");

    String tzid = null;
    if (tzidPar != null) {
      tzid = tzidPar.getValue();
    }

    return tzid;
  }

  /* Init a date property for makeDtEnd, makeDue
   */
  private void initDateProp(final DateProperty dt,
                            final TimeZoneRegistry tzreg) throws CalFacadeException {
    try {
      String tzid = getTzid();

      ParameterList pl = dt.getParameters();

      if (getDateType()) {
        pl.add(Value.DATE);
      }

      if (tzid != null) {
        dt.setTimeZone(tzreg.getTimeZone(tzid));
      }

      dt.setValue(getDtval());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private BwDateTime addDuration(final Dur val) throws CalFacadeException {
    DtEnd dtEnd;

    java.util.Date endDt = val.getTime(makeDate());
    DtStart dtStart = makeDtStart(Timezones.getTzRegistry());

    if (getDateType()) {
      dtEnd = new DtEnd(new Date(endDt));
      addIcalParameter(dtEnd, Value.DATE);
    } else {
      DateTime d = new DateTime(endDt);

      Parameter tzid = getIcalParameter(dtStart, "TZID");
      if (tzid != null) {
        DateTime sd = (DateTime)dtStart.getDate();

        d.setTimeZone(sd.getTimeZone());
      }
//          dtEnd = new DtEnd(d, dtStart.isUtc());
      dtEnd = new DtEnd(d);
      if (tzid != null) {
        addIcalParameter(dtEnd, tzid);
      } else if (dtStart.isUtc()) {
        dtEnd.setUtc(true);
      }
    }

    return makeBwDateTime(dtEnd);
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public int compare(final BwDateTime dt1, final BwDateTime dt2) {
    if (dt1 == dt2) {
      return 0;
    }

    return dt1.getDate().compareTo(dt2.getDate());
  }

  @Override
  public int compareTo(final BwDateTime o2) {
    return compare(this, o2);
  }

  /** Return true if this is before val
   *
   * @param val
   * @return boolean this before val
   */
  public boolean before(final BwDateTime val) {
    return compare(this, val) < 0;
  }

  /** Return true if this is after val
   *
   * @param val
   * @return boolean this after val
   */
  public boolean after(final BwDateTime val) {
    return compare(this, val) > 0;
  }

  @Override
  public int hashCode() {
    int hc = 1;

    if (getDateType()) {
      hc = 3;
    }

    if (getTzid() != null) {
      hc *= getTzid().hashCode();
    }

    if (getDtval() != null) {
      hc *= getDtval().hashCode();
    }

    return hc;
  }

  /** */
  public final static int dtOk = 0;
  /** Should not have a tzid */
  public final static int dtBadTz = 1;
  /** */
  public final static int dtBadDtval = 2;

  /** Ensure the date time is a valid representation. Provide an indication of
   * reasons why not.
   *
   * @return validity code
   */
  public int validate() {
    if (getDtval().length() == 8) {
      if (!getDateType()) {
        return dtBadDtval;
      }
    }

    if (getDateType()) {
      if (getDtval().length() > 8) {
        return dtBadDtval;
      }

      if (getTzid() != null) {
        return dtBadTz;
      }

      return dtOk;
    }

    if (getDtval().length() == 16) {
      // Can only be UTC
      if (!getDtval().endsWith("Z")) {
        return dtBadDtval;
      }

      if (getTzid() != null) {
        return dtBadTz;
      }

      return dtOk;
    }

    // Floating or local time

    return dtOk;
  }


  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof BwDateTime)) {
      return false;
    }

    BwDateTime that = (BwDateTime)obj;

    if (getDateType() != that.getDateType()) {
      return false;
    }

    if (!CalFacadeUtil.eqObjval(getTzid(), that.getTzid())) {
      return false;
    }

    return CalFacadeUtil.eqObjval(getDtval(), that.getDtval());
  }

  @Override
  public Object clone() {
    BwDateTime ndt = new BwDateTime();

    ndt.setDateType(getDateType());
    ndt.setTzid(getTzid());
    ndt.setDtval(getDtval());
    ndt.setDate(getDate());
    ndt.setFloatFlag(getFloatFlag());

    return ndt;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append("BwDateTime{");
    if (getDateType()) {
      sb.append("DATE");
    } else {
      sb.append("DATETIME");
    }
    if (getTzid() != null) {
      sb.append(", tzid=");
      sb.append(getTzid());
    }
    sb.append(", dtval=");
    sb.append(getDtval());

    if (getFloating()) {
      sb.append(", floating");
    } else if (isUTC()) {
      sb.append(", UTC");
    } else {
      sb.append(", UTC=");
      sb.append(getDate());
    }
    sb.append("}");

    return sb.toString();
  }
}
