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
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.Available;
import net.fortuna.ical4j.model.component.VAvailability;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.component.VJournal;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;
import org.apache.log4j.Logger;

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

  public static String toJcal(final Calendar cal,
                              final IcalendarType pattern,
                              final EventTimeZonesRegistry tzreg) throws CalFacadeException {
    StringWriter sw = new StringWriter();

    outJcal(sw, cal, pattern, tzreg);

    return sw.toString();
  }

  public static void outJcal(final Writer wtr,
                             final Calendar cal,
                             final IcalendarType pattern,
                             final EventTimeZonesRegistry tzreg) throws CalFacadeException {
    try {
      JsonGenerator jgen = jsonFactory.createJsonGenerator(wtr);

      if (Logger.getLogger(JcalHandler.class).isDebugEnabled()) {
        jgen.useDefaultPrettyPrinter();
      }

      jgen.writeStartArray();

      jgen.writeString("vcalendar");
      jgen.writeStartArray();

      for (Object o: cal.getProperties()) {
        JsonProperty.addFields(jgen, (Property)o);
      }

      jgen.writeEndArray(); // End event properties

      /* Output subcomponents
       */
      jgen.writeStartArray();

      jgen.writeStartArray(); // for components

      for (Object o: cal.getComponents()) {
        Component comp = (Component)o;
        outComp(jgen, comp);
      }

      jgen.writeEndArray(); // for components

      jgen.writeEndArray();

      jgen.flush();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
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

      if (Logger.getLogger(JcalHandler.class).isDebugEnabled()) {
        jgen.useDefaultPrettyPrinter();
      }

      jgen.writeStartArray();

      calendarProps(jgen, methodType);

      jgen.writeStartArray(); // for components

      for (EventInfo ei: vals) {
        BwEvent ev = ei.getEvent();

        Component comp;
        if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
          comp = VFreeUtil.toVFreeBusy(ev);
        } else {
          comp = VEventUtil.toIcalComponent(ei, false, tzreg, uriGen,
                                            currentPrincipal);
        }

        outComp(jgen, comp);

        if (ei.getNumOverrides() > 0) {
          for (EventInfo oei: ei.getOverrides()) {
            ev = oei.getEvent();
            outComp(jgen, VEventUtil.toIcalComponent(oei,
                                                     true,
                                                     tzreg,
                                                     uriGen,
                                                     currentPrincipal));
          }
        }
      }

      jgen.writeEndArray(); // for components

      jgen.writeEndArray();

      jgen.flush();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static void outComp(final JsonGenerator jgen,
                              final Component comp) throws CalFacadeException {
    try {
      jgen.writeStartArray();

      jgen.writeString(comp.getName().toLowerCase());
      jgen.writeStartArray();

      for (Object o: comp.getProperties()) {
        JsonProperty.addFields(jgen, (Property)o);
      }

      jgen.writeEndArray(); // End event properties

      /* Output subcomponents
       */
      jgen.writeStartArray();

      ComponentList cl = null;
      if (comp instanceof VEvent) {
        cl = ((VEvent)comp).getAlarms();
      } else if (comp instanceof VToDo) {
        cl = ((VToDo)comp).getAlarms();
      } else if (comp instanceof VJournal) {
      } else if (comp instanceof VFreeBusy) {
      } else if (comp instanceof VAvailability) {
        cl = ((VAvailability)comp).getAvailable();
      } else if (comp instanceof Available) {
      } else if (comp instanceof VPoll) {
        cl = ((VPoll)comp).getCandidates();
      }

      if (cl != null) {
        for (Object o: cl) {
          outComp(jgen, (Component)o);
        }
      }

      jgen.writeEndArray(); // end subcomponents

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

      JsonProperty.addFields(jgen, new ProdId(IcalTranslator.prodId));
      JsonProperty.addFields(jgen, Version.VERSION_2_0);

      if ((methodType > ScheduleMethods.methodTypeNone) &&
              (methodType < ScheduleMethods.methodTypeUnknown)) {
        JsonProperty.addFields(jgen, new Method(
                ScheduleMethods.methods[methodType]));
      }

      jgen.writeEndArray();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
}
