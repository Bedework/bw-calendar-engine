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

import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.ScheduleMethods;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import ietf.params.xml.ns.icalendar_2.IcalendarType;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Property;

import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;

/** Class to handle jcal.
 *
 * @author Mike Douglass douglm rpi.edu
 * @version 1.0
 */
public class JcalHandler implements Serializable {
  private final static JsonFactory jsonFactory;

  static {
    jsonFactory = new JsonFactory();
    jsonFactory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    jsonFactory.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, true);
    jsonFactory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
  }


  public static String toJcal(final Collection<EventInfo> vals,
                              final int methodType,
                              final IcalendarType pattern,
                              final URIgen uriGen,
                              final String currentPrincipal,
                              final EventTimeZonesRegistry tzreg) throws CalFacadeException {
    StringWriter sw = new StringWriter();

    outJcal(sw, vals, methodType, pattern, uriGen, currentPrincipal, tzreg);

    return sw.toString();
  }

  public static void outJcal(final Writer wtr,
                             final Collection<EventInfo> vals,
                             final int methodType,
                             final IcalendarType pattern,
                             final URIgen uriGen,
                             final String currentPrincipal,
                             final EventTimeZonesRegistry tzreg) throws CalFacadeException {
    try {
      JsonGenerator jgen = jsonFactory.createJsonGenerator(wtr);

      jgen.writeStartArray();

      calendarProps(jgen, methodType);

      for (EventInfo ei: vals) {
        BwEvent ev = ei.getEvent();

        Component comp;
        if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
          comp = VFreeUtil.toVFreeBusy(ev);
        } else {
          comp = VEventUtil.toIcalComponent(ei, false, tzreg, uriGen,
                                            currentPrincipal);
        }

        outEv(jgen, comp);

        if (ei.getNumOverrides() > 0) {
          for (EventInfo oei: ei.getOverrides()) {
            ev = oei.getEvent();
            outEv(jgen, VEventUtil.toIcalComponent(oei,
                                                   true,
                                                   tzreg,
                                                   uriGen,
                                                   currentPrincipal));
          }
        }
      }

      jgen.writeEndArray();

      jgen.flush();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void outEv(final JsonGenerator jgen,
                              final Component comp) throws CalFacadeException {
    try {
      jgen.writeStartArray();

      jgen.writeString(comp.getName().toLowerCase());
      jgen.writeStartArray();

      for (Object o: comp.getProperties()) {
        JsonProperty.addFields(jgen, (Property)o);
      }

      jgen.writeEndArray(); // End event properties

      jgen.writeEndArray(); // end event
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void calendarProps(final JsonGenerator jgen,
                                    final int methodType) throws CalFacadeException {
    try {
      jgen.writeString("vcalendar");

      jgen.writeStartArray();

      jgen.writeFieldName("prodid");
      jgen.writeString(IcalTranslator.prodId);

      jgen.writeFieldName("version");
      jgen.writeString("2.0");

      if ((methodType > ScheduleMethods.methodTypeNone) &&
              (methodType < ScheduleMethods.methodTypeUnknown)) {
        jgen.writeFieldName("method");
        jgen.writeString(ScheduleMethods.methods[methodType]);
      }

      jgen.writeEndArray();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
