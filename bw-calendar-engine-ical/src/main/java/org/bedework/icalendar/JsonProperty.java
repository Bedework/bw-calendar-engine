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
package org.bedework.icalendar;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.XcalUtil;

import com.fasterxml.jackson.core.JsonGenerator;
import net.fortuna.ical4j.model.CategoryList;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.NumberList;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.WeekDayList;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.FreeBusy;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.RDate;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RequestStatus;
import net.fortuna.ical4j.model.property.Trigger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 *
 * @author Mike Douglass douglm rpi.edu
 * @version 1.0
 */
@SuppressWarnings("ConstantConditions")
public class JsonProperty implements Serializable {
  public static void addFields(final JsonGenerator jgen,
                               final Property prop) throws CalFacadeException {
    try {
      jgen.writeStartArray();

      jgen.writeString(prop.getName().toLowerCase());

      JsonParameters.addFields(jgen, prop);

      final DataType type = getType(prop);
      jgen.writeString(type.getJsonType());

      outValue(jgen, prop, type);

      jgen.writeEndArray();
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* An entry in here if the value may have different types..
   */
  private final static Set<String> types = new TreeSet<>();

  static {
    types.add("attach");
    types.add("dtend");
    types.add("dtstart");
    types.add("due");
    types.add("exdate");
    types.add("rdate");
    types.add("recurrence-id");
    types.add("trigger");
  }

  private static DataType getType(final Property prop) {
    final PropertyInfoIndex pii = PropertyInfoIndex.fromName(prop.getName());

    if (pii == null) {
      return DataType.TEXT;
    }

    final DataType dtype = pii.getPtype();
    if (dtype == null) {
      return DataType.TEXT;
    }

    final String nm = prop.getName().toLowerCase();

    if ((dtype != DataType.SPECIAL) && (!types.contains(nm))) {
      return dtype;
    }

    if (prop instanceof DateProperty) {
      // dtend, dtstart, due

      final DateProperty dp = (DateProperty)prop;

      if (Value.DATE.equals(dp.getParameter(Parameter.VALUE))) {
        return DataType.DATE;
      }

      return DataType.DATE_TIME;
    }

    if (prop instanceof DateListProperty) {
      // exdate, rdate

      final DateListProperty dlp = (DateListProperty)prop;

      if (Value.DATE.equals(dlp.getDates().getType())) {
        return DataType.DATE;
      }

      return DataType.DATE_TIME;
    }

    if ("attach".equals(nm)) {
      final Attach att = (Attach)prop;
      if (att.getUri() !=null) {
        return DataType.URI;
      }

      return DataType.BINARY;
    }

    if ("trigger".equals(nm)) {
      final Trigger tr = (Trigger)prop;
      if (tr.getDuration() !=null) {
        return DataType.DURATION;
      }

      return DataType.DATE_TIME;
    }

    // in the absence of anything else callit text
    return DataType.TEXT;
  }

  private abstract static class PropertyValueEmitter {
    abstract void emitValue(JsonGenerator jgen,
                            Property prop) throws Throwable;

    protected void emitValue(final JsonGenerator jgen,
                             final Property prop,
                             final DataType type) throws Throwable {
      throw new RuntimeException("Unimplemented");
    }

    protected void outField(final JsonGenerator jgen,
                            final String name,
                            final String val) throws Throwable {
      if (val == null) {
        return;
      }

      jgen.writeFieldName(name);
      jgen.writeString(val);
    }

    protected void outField(final JsonGenerator jgen,
                            final String name,
                            final int val) throws Throwable {
      jgen.writeFieldName(name);
      jgen.writeNumber(val);
    }

    protected void outField(final JsonGenerator jgen,
                            final String name,
                            final NumberList val) throws Throwable {
      if (val == null) {
        return;
      }

      jgen.writeFieldName(name);

      if (val.size() == 1) {
        jgen.writeNumber((Integer)val.iterator().next());
        return;
      }

      jgen.writeStartArray();

      for (final Object o: val) {
        jgen.writeNumber((Integer)o);
      }

      jgen.writeEndArray();
    }

    protected void outField(final JsonGenerator jgen,
                            final String name,
                            final WeekDayList val) throws Throwable {
      if (val == null) {
        return;
      }

      jgen.writeFieldName(name);

      if (val.size() == 1) {
        jgen.writeNumber((Integer)val.iterator().next());
        return;
      }

      jgen.writeStartArray();

      for (final Object o: val) {
        jgen.writeString(((WeekDay)o).getDay().toLowerCase());
      }

      jgen.writeEndArray();
    }
  }

  /* An entry in here if we special case the value..
   */
  private final static Map<String, PropertyValueEmitter> valMap = new HashMap<>();

  static {
    valMap.put("categories", new CategoriesValueEmitter());
    valMap.put("exdate", new ExdateValueEmitter());
    valMap.put("exrule", new RecurValueEmitter());
    valMap.put("freebusy", new PeriodListValueEmitter());
    valMap.put("geo", new GeoValueEmitter());
    valMap.put("rdate", new RdateValueEmitter());
    valMap.put("request-status", new ReqStatValueEmitter());
    valMap.put("rrule", new RecurValueEmitter());
  }

  private static class SingleValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      emitValue(jgen, prop, DataType.TEXT);
      jgen.writeString(prop.getValue());
    }

    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop,
                          final DataType type) throws Throwable {
      switch (type) {
        case BOOLEAN:
          jgen.writeBoolean(Boolean.valueOf(prop.getValue()));
          break;
        case DATE:
        case DATE_TIME:
          jgen.writeString(XcalUtil.getXmlFormatDateTime(prop.getValue()));
          break;
        case FLOAT:
          jgen.writeNumber(Float.valueOf(prop.getValue()));
          break;
        case INTEGER:
          jgen.writeNumber(Integer.valueOf(prop.getValue()));
          break;
        case PERIOD:
          // Should not get here - just write something out
          jgen.writeString(prop.getValue());
          break;
        case RECUR:
          // Should not get here - just write something out
          jgen.writeString(prop.getValue());
          break;
        case BINARY:
        case CUA:
        case DURATION:
        case TEXT:
        case URI:
          jgen.writeString(prop.getValue());
          break;
        case TIME:
          jgen.writeString(XcalUtil.getXmlFormatTime(prop.getValue()));
          break;
        case UTC_OFFSET:
          jgen.writeString(XcalUtil.getXmlFormatUtcOffset(
                  prop.getValue()));
          break;
        case SPECIAL:
          break;
        case HREF:
          break;
      }
    }
  }

  private static class ExdateValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final ExDate p = (ExDate)prop;

      jgen.writeStartArray();
      final DateList dl = p.getDates();
      for (final Object o: dl) {
        jgen.writeString(XcalUtil.getXmlFormatDateTime(o.toString()));
      }

      jgen.writeEndArray();
    }
  }

  private static class PeriodListValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final PeriodList pl;

      if (prop instanceof RDate) {
        final RDate p = (RDate)prop;
        pl = p.getPeriods();
      } else if (prop instanceof FreeBusy) {
        final FreeBusy p = (FreeBusy)prop;
        pl = p.getPeriods();
      } else {
        throw new RuntimeException("Unknown property " + prop);
      }

      jgen.writeStartArray();

      for (final Object o: pl) {
        final Period per = (Period)o;

        final StringBuilder sb = new StringBuilder(XcalUtil.getXmlFormatDateTime(
                per.getStart().toString()));
        sb.append("/");

        if (per.getDuration() != null) {
          sb.append(per.getDuration());
        } else {
          sb.append(XcalUtil.getXmlFormatDateTime(
                  per.getEnd().toString()));
        }

        jgen.writeString(sb.toString());
      }

      jgen.writeEndArray();
    }
  }

  private static class RdateValueEmitter extends PeriodListValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final RDate p = (RDate)prop;

      final DateList dl = p.getDates();

      if (dl != null) {
        jgen.writeStartArray();

        for (final Object o: dl) {
          jgen.writeString(XcalUtil.getXmlFormatDateTime(o.toString()));
        }
        jgen.writeEndArray();
      } else {
        super.emitValue(jgen, prop);
      }
    }
  }

  private static class CategoriesValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final Categories p = (Categories)prop;

      jgen.writeStartArray();

      final CategoryList cl = p.getCategories();
      final Iterator it = cl.iterator();
      while (it.hasNext()){
        jgen.writeString(it.next().toString());
      }

      jgen.writeEndArray();
    }
  }

  private static class GeoValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final Geo p = (Geo)prop;

      jgen.writeStartArray();

      jgen.writeNumber(p.getLatitude());
      jgen.writeNumber(p.getLongitude());

      jgen.writeEndArray();
    }
  }

  private static class ReqStatValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      final RequestStatus p = (RequestStatus)prop;

      jgen.writeStartArray();

      jgen.writeString(p.getStatusCode());
      jgen.writeString(p.getDescription());

      if (p.getExData() != null) {
        jgen.writeString(p.getExData());
      }

      jgen.writeEndArray();
    }
  }

  private static class RecurValueEmitter extends PropertyValueEmitter {
    @Override
    public void emitValue(final JsonGenerator jgen,
                          final Property prop) throws Throwable {
      Recur r = null;

      if (prop instanceof RRule) {
        r = ((RRule)prop).getRecur();
      } else if (prop instanceof ExRule) {
        r = ((ExRule)prop).getRecur();
      }

      jgen.writeStartObject();

      //noinspection ConstantConditions
      outField(jgen, "freq", r.getFrequency());
      outField(jgen, "wkst", r.getWeekStartDay());
      if (r.getUntil() != null) {
        outField(jgen, "until", r.getUntil().toString());
      }
      outField(jgen, "count", r.getCount());
      outField(jgen, "interval", r.getInterval());
      outField(jgen, "bymonth", r.getMonthList());
      outField(jgen, "byweekno", r.getWeekNoList());
      outField(jgen, "byyearday", r.getYearDayList());
      outField(jgen, "bymonthday", r.getMonthDayList());
      outField(jgen, "byday", r.getDayList());
      outField(jgen, "byhour", r.getHourList());
      outField(jgen, "byminute", r.getMinuteList());
      outField(jgen, "bysecond", r.getSecondList());
      outField(jgen, "bysetpos", r.getSetPosList());

      jgen.writeEndObject();
    }
  }

  private final static PropertyValueEmitter defValEmitter = new SingleValueEmitter();

  private static void outValue(final JsonGenerator jgen,
                               final Property prop,
                               final DataType type) throws Throwable {
    final String nm = prop.getName().toLowerCase();

    final PropertyValueEmitter pve = valMap.get(nm);

    if (pve == null) {
      defValEmitter.emitValue(jgen, prop, type);
      return;
    }

    pve.emitValue(jgen, prop);
  }
}
