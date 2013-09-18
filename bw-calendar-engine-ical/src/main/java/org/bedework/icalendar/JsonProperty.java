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

import com.fasterxml.jackson.core.JsonGenerator;
import net.fortuna.ical4j.model.CategoryList;
import net.fortuna.ical4j.model.NumberList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.WeekDayList;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Categories;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Geo;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.RequestStatus;
import net.fortuna.ical4j.model.property.Trigger;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author Mike Douglass douglm rpi.edu
 * @version 1.0
 */
public class JsonProperty implements Serializable {
  public static void addFields(final JsonGenerator jgen,
                               final Property prop) throws CalFacadeException {
    try {
      jgen.writeStartArray();

      jgen.writeString(prop.getName().toLowerCase());

      JsonParameters.addFields(jgen, prop);

      jgen.writeString(getType(prop));

      jgen.writeEndArray();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* An entry in here if the value may have different types..
   */
  private static Map<String, String> typeMap = new HashMap<>();

  static {
    typeMap.put("attach", "");
    typeMap.put("dtend", "");
    typeMap.put("dtstart", "");
    typeMap.put("due", "");
    typeMap.put("exdate", "");
    typeMap.put("rdate", "");
    typeMap.put("recurrence-id", "");
    typeMap.put("trigger", "");
  }

  private static String getType(final Property prop) {
    String nm = prop.getName().toLowerCase();
    PropertyIndex.PropertyInfoIndex pii = PropertyIndex
            .PropertyInfoIndex.lookupPname(nm);

    if (pii == null) {
      return "text";
    }

    PropertyIndex.DataType dtype = pii.getPtype();
    if (dtype == null) {
      return "text";
    }

    if (dtype != PropertyIndex.DataType.SPECIAL) {
      String type = typeMap.get(nm);

      if (type == null) {
        return dtype.getXcalType().getLocalPart();
      }
    }

    if (prop instanceof DateProperty) {
      // dtend, dtstart, due

      DateProperty dp = (DateProperty)prop;

      if (dp.getValue().length() > 8) {
        return "date";
      }

      return "date-time";
    }

    if (prop instanceof DateListProperty) {
      // exdate, rdate

      DateListProperty dlp = (DateListProperty)prop;

      return dlp.getDates().getType().getValue().toLowerCase();
    }

    if ("attach".equals(nm)) {
      Attach att = (Attach)prop;
      if (att.getUri() !=null) {
        return "uri";
      }

      return "binary";
    }

    if ("trigger".equals(nm)) {
      Trigger tr = (Trigger)prop;
      if (tr.getDuration() !=null) {
        return "duration";
      }

      return "date-time";
    }

    // in the absence of anything else callit text
    return "text";
  }

  private abstract static class PropertyValueEmitter {
    abstract void emitValue(JsonGenerator jgen,
                            Property prop) throws Throwable;

    protected void outString(final JsonGenerator jgen,
                             final String val) throws Throwable {
      if (val == null) {
        return;
      }

      jgen.writeString(val);
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

      for (Object o: val) {
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

      for (Object o: val) {
        jgen.writeString(((WeekDay)o).getDay().toLowerCase());
      }

      jgen.writeEndArray();
    }
  }

  /* An entry in here if we special case the value..
   */
  private static Map<String, PropertyValueEmitter> valMap = new HashMap<>();

  static {
    valMap.put("categories", new CategoriesValueEmitter());
    valMap.put("exrule", new RecurValueEmitter());
    valMap.put("geo", new GeoValueEmitter());
    valMap.put("request-status", new ReqStatValueEmitter());
    valMap.put("rrule", new RecurValueEmitter());
  }

  private static class SingleValueEmitter extends PropertyValueEmitter {
    public void emitValue(JsonGenerator jgen,
                          Property prop) throws Throwable {
      jgen.writeString(prop.getValue());
    }
  }

  private static class CategoriesValueEmitter extends PropertyValueEmitter {
    public void emitValue(JsonGenerator jgen,
                          Property prop) throws Throwable {
      Categories p = (Categories)prop;

      jgen.writeStartArray();

      CategoryList cl = p.getCategories();
      Iterator it = cl.iterator();
      while (it.hasNext()){
        jgen.writeString(it.next().toString());
      }

      jgen.writeEndArray();
    }
  }

  private static class GeoValueEmitter extends PropertyValueEmitter {
    public void emitValue(JsonGenerator jgen,
                          Property prop) throws Throwable {
      Geo p = (Geo)prop;

      jgen.writeStartArray();

      jgen.writeNumber(p.getLatitude());
      jgen.writeNumber(p.getLongitude());

      jgen.writeEndArray();
    }
  }

  private static class ReqStatValueEmitter extends PropertyValueEmitter {
    public void emitValue(JsonGenerator jgen,
                          Property prop) throws Throwable {
      RequestStatus p = (RequestStatus)prop;

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
    public void emitValue(JsonGenerator jgen,
                          Property prop) throws Throwable {
      Recur r = null;

      if (prop instanceof RRule) {
        r = ((RRule)prop).getRecur();
      } else if (prop instanceof ExRule) {
        r = ((ExRule)prop).getRecur();
      }

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
    }
  }

  private static PropertyValueEmitter defValEmitter = new SingleValueEmitter();

  private static void outValue(final JsonGenerator jgen,
                               final Property prop) throws Throwable {
    String nm = prop.getName().toLowerCase();

    PropertyValueEmitter pve = valMap.get(nm);

    if (pve == null) {
      defValEmitter.emitValue(jgen, prop);
      return;
    }

    pve.emitValue(jgen, prop);
  }
}
