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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwVersion;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.Icalendar.TimeZoneInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.JsonCalendarBuilder;
import org.bedework.util.calendar.PropertyIndex.DataType;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.calendar.WsXMLTranslator;
import org.bedework.util.calendar.XmlCalendarBuilder;
import org.bedework.util.timezones.Timezones;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.XmlEmit.NameSpace;
import org.bedework.util.xml.tagdefs.XcalTags;

import ietf.params.xml.ns.icalendar_2.ArrayOfComponents;
import ietf.params.xml.ns.icalendar_2.ArrayOfProperties;
import ietf.params.xml.ns.icalendar_2.BaseComponentType;
import ietf.params.xml.ns.icalendar_2.BasePropertyType;
import ietf.params.xml.ns.icalendar_2.IcalendarType;
import ietf.params.xml.ns.icalendar_2.MethodPropType;
import ietf.params.xml.ns.icalendar_2.ProdidPropType;
import ietf.params.xml.ns.icalendar_2.VcalendarType;
import ietf.params.xml.ns.icalendar_2.VersionPropType;
import ietf.params.xml.ns.icalendar_2.VeventType;
import ietf.params.xml.ns.icalendar_2.VfreebusyType;
import ietf.params.xml.ns.icalendar_2.VjournalType;
import ietf.params.xml.ns.icalendar_2.VtodoType;
import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.data.CalendarParserImpl;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.data.UnfoldingReader;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VAvailability;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.component.VFreeBusy;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.component.VToDo;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.Method;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.TzId;
import net.fortuna.ical4j.model.property.Version;
import org.apache.log4j.Logger;

import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

/** Object to provide translation between a bedework entity and an Icalendar format.
 *
 * @author Mike Douglass   douglm bedework.edu
 */
public class IcalTranslator implements Serializable {
  protected boolean debug;

  /** A class we use to indicate we are skipping stuff. Pushed on to teh stack.
   *
   */
  public static class SkipThis {
  }

  /** We'll use this to parameterize some of the behaviour
   */
  public static class Pars {
    /** Support simple location only. Many iCalendar-aware
     * applications only support a simple string valued location.
     *
     * <p>If this value is true we only pass the address part of the location
     * and provide an altrep which will allow (some) clients to retrieve the
     * full form of a location.
     */
    public boolean simpleLocation = true;

    /** Support simple contacts only.
     */
    public boolean simpleContact = true;
  }

  public static final String prodId = "//Bedework.org//BedeWork V" +
                                         BwVersion.bedeworkVersion + "//EN";

  protected transient Logger log;

  protected IcalCallback cb;

  protected Pars pars = new Pars();

  /** Constructor:
   *
   * @param cb     IcalCallback object for retrieval of entities
   */
  public IcalTranslator(final IcalCallback cb) {
    this.cb = cb;
    debug = getLog().isDebugEnabled();
  }

  /* ====================================================================
   *                     Translation methods
   * ==================================================================== */

  /** Make a new Calendar with default properties
   *
   * @param methodType
   * @return Calendar
   * @throws CalFacadeException
   */
  public static Calendar newIcal(final int methodType) throws CalFacadeException {
    Calendar cal = new Calendar();

    PropertyList pl = cal.getProperties();

    pl.add(new ProdId(prodId));
    pl.add(Version.VERSION_2_0);

    if ((methodType > ScheduleMethods.methodTypeNone) &&
        (methodType < ScheduleMethods.methodTypeUnknown)) {
      pl.add(new Method(ScheduleMethods.methods[methodType]));
    }

    return cal;
  }

  /** Make a new Calendar with the freebusy object
   *
   * @param methodType
   * @param ent
   * @return String representation
   * @throws CalFacadeException
   */
  public static String toIcalString(final int methodType,
                                    final BwEvent ent) throws CalFacadeException {
    Calendar cal = new Calendar();

    PropertyList pl = cal.getProperties();

    pl.add(new ProdId(prodId));
    pl.add(Version.VERSION_2_0);

    if ((methodType > ScheduleMethods.methodTypeNone) &&
        (methodType < ScheduleMethods.methodTypeUnknown)) {
      pl.add(new Method(ScheduleMethods.methods[methodType]));
    }

    if (ent.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
      VFreeBusy vfreeBusy = VFreeUtil.toVFreeBusy(ent);

      cal.getComponents().add(vfreeBusy);
    } else {
      throw new CalFacadeException("Unexpected entity type");
    }

    CalendarOutputter co = new CalendarOutputter(false, 74);

    Writer wtr =  new StringWriter();
    try {
      co.output(cal, wtr);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }

    return wtr.toString();
  }

  /** Convert a Calendar to it's string form
   *
   * @param cal Calendar to convert
   * @return String representation
   * @throws CalFacadeException
   */
  public static String toIcalString(final Calendar cal) throws CalFacadeException {
    Writer wtr =  new StringWriter();
    writeCalendar(cal, wtr);

    return wtr.toString();
  }

  /** Write a Calendar
   *
   * @param cal Calendar to convert
   * @param wtr Writer for output
   * @throws CalFacadeException
   */
  public static void writeCalendar(final Calendar cal,
                                   final Writer wtr) throws CalFacadeException {
    CalendarOutputter co = new CalendarOutputter(false, 74);

    try {
      co.output(cal, wtr);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** turn a single event with possible overrides into a calendar
   *
   * @param val
   * @param methodType    int value fromIcalendar
   * @return Calendar
   * @throws CalFacadeException
   */
  public Calendar toIcal(final EventInfo val,
                         final int methodType) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    try {
      Calendar cal = newIcal(methodType);

      addToCalendar(cal, val, new TreeSet<String>());

      return cal;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Write a collection of calendar data as json
   *
   * @param vals
   * @param methodType    int value fromIcalendar
   * @param wtr for output
   * @throws CalFacadeException
   */
  public void writeJcal(final Collection<EventInfo> vals,
                        final int methodType,
                        final Writer wtr) throws CalFacadeException {

    String currentPrincipal = null;
    BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    JcalHandler.outJcal(wtr,
                        vals, methodType, null,
                        cb.getURIgen(),
                        currentPrincipal,
                        new EventTimeZonesRegistry(this, null));
  }

  /** Write a collection of calendar data as xml
   *
   * @param vals
   * @param methodType    int value fromIcalendar
   * @param xml for output
   * @throws CalFacadeException
   */
  public void writeXmlCalendar(final Collection vals,
                               final int methodType,
                               final XmlEmit xml) throws CalFacadeException {
    try {
      xml.addNs(new NameSpace(XcalTags.namespace, "X"), false);

      xml.openTag(XcalTags.icalendar);
      xml.openTag(XcalTags.vcalendar);

      xml.openTag(XcalTags.properties);

      xmlProp(xml, Property.PRODID, XcalTags.textVal, prodId);
      xmlProp(xml, Property.VERSION, XcalTags.textVal,
              Version.VERSION_2_0.getValue());

      xml.closeTag(XcalTags.properties);

      boolean componentsOpen = false;

      if (!cb.getTimezonesByReference()) {
        Calendar cal = newIcal(methodType); // To collect timezones

        addIcalTimezones(cal, vals);

        // Emit timezones
        for (Object o: cal.getComponents()) {
          if (!(o instanceof VTimeZone)) {
            continue;
          }

          if (!componentsOpen) {
            xml.openTag(XcalTags.components);
            componentsOpen = true;
          }

          xmlComponent(xml, (Component)o);
        }
      }

      URIgen uriGen = cb.getURIgen();
      String currentPrincipal = null;
      BwPrincipal principal = cb.getPrincipal();

      if (principal != null) {
        currentPrincipal = principal.getPrincipalRef();
      }

      Iterator it = vals.iterator();
      while (it.hasNext()) {
        Object o = it.next();

        if (o instanceof EventInfo) {
          EventInfo ei = (EventInfo)o;
          BwEvent ev = ei.getEvent();

          EventTimeZonesRegistry tzreg = new EventTimeZonesRegistry(this, ev);

          Component comp;
          if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
            comp = VFreeUtil.toVFreeBusy(ev);
          } else {
            comp = VEventUtil.toIcalComponent(ei, false, tzreg, uriGen,
                                              currentPrincipal);
          }

          if (!componentsOpen) {
            xml.openTag(XcalTags.components);
            componentsOpen = true;
          }

          xmlComponent(xml, comp);

          if (ei.getNumOverrides() > 0) {
            for (EventInfo oei: ei.getOverrides()) {
              ev = oei.getEvent();
              xmlComponent(xml, VEventUtil.toIcalComponent(oei,
                                                           true,
                                                           tzreg,
                                                           uriGen,
                                                           currentPrincipal));
            }
          }
        }
      }

      if (componentsOpen) {
        xml.closeTag(XcalTags.components);
      }

      xml.closeTag(XcalTags.vcalendar);
      xml.closeTag(XcalTags.icalendar);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlComponent(final XmlEmit xml,
                            final Component val) throws CalFacadeException {
    try {
      QName tag = openTag(xml, val.getName());

      PropertyList pl = val.getProperties();

      if (pl.size() > 0) {
        xml.openTag(XcalTags.properties);

        for (Object po: pl) {
          xmlProperty(xml, (Property)po);
        }
        xml.closeTag(XcalTags.properties);
      }

      ComponentList cl = null;

      if (val instanceof VTimeZone) {
        cl = ((VTimeZone)val).getObservances();
      } else if (val instanceof VEvent) {
        cl = ((VEvent)val).getAlarms();
      } else if (val instanceof VToDo) {
        cl = ((VToDo)val).getAlarms();
      }

      if ((cl != null) && (cl.size() > 0)){
        xml.openTag(XcalTags.components);

        for (Object o: cl) {
          xmlComponent(xml, (Component)o);
        }

        xml.closeTag(XcalTags.components);
      }

      xml.closeTag(tag);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlProperty(final XmlEmit xml,
                           final Property val) throws CalFacadeException {
    try {
      QName tag = openTag(xml, val.getName());

      ParameterList pl = val.getParameters();

      if (pl.size() > 0) {
        xml.openTag(XcalTags.parameters);

        Iterator pli = pl.iterator();
        while (pli.hasNext()) {
          xmlParameter(xml, (Parameter)pli.next());
        }
        xml.closeTag(XcalTags.parameters);
      }

      PropertyInfoIndex pii = PropertyInfoIndex.lookupPname(val.getName());

      QName ptype = XcalTags.textVal;

      if (pii != null) {
        DataType dtype = pii.getPtype();
        if (dtype != null) {
          ptype = dtype.getXcalType();
        }
      }

      if (ptype == null) {
        // Special processing I haven't done
        warn("Unimplemented value type for " + val.getName());
        ptype = XcalTags.textVal;
      }

      if (ptype.equals(XcalTags.recurVal)) {
        // Emit individual parts of recur rule

        xml.openTag(ptype);

        Recur r;

        if (val instanceof ExRule) {
          r = ((ExRule)val).getRecur();
        } else {
          r = ((RRule)val).getRecur();
        }

        xml.property(XcalTags.freq, r.getFrequency());
        xmlProp(xml, XcalTags.wkst, r.getWeekStartDay());
        if (r.getUntil() != null) {
          xmlProp(xml, XcalTags.until, r.getUntil().toString());
        }
        xmlProp(xml, XcalTags.count, String.valueOf(r.getCount()));
        xmlProp(xml, XcalTags.interval, String.valueOf(r.getInterval()));
        xmlProp(xml, XcalTags.bymonth, r.getMonthList());
        xmlProp(xml, XcalTags.byweekno, r.getWeekNoList());
        xmlProp(xml, XcalTags.byyearday, r.getYearDayList());
        xmlProp(xml, XcalTags.bymonthday, r.getMonthDayList());
        xmlProp(xml, XcalTags.byday, r.getDayList());
        xmlProp(xml, XcalTags.byhour, r.getHourList());
        xmlProp(xml, XcalTags.byminute, r.getMinuteList());
        xmlProp(xml, XcalTags.bysecond, r.getSecondList());
        xmlProp(xml, XcalTags.bysetpos, r.getSetPosList());

        xml.closeTag(ptype);
      } else {
        xml.property(ptype, val.getValue());
      }

      xml.closeTag(tag);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private QName openTag(final XmlEmit xml,
                        final String name) throws CalFacadeException {
    QName tag = new QName(XcalTags.namespace, name.toLowerCase());

    try {
      xml.openTag(tag);

      return tag;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlProp(final XmlEmit xml,
                       final QName tag,
                       final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      xml.property(tag, val);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlProp(final XmlEmit xml,
                       final QName tag,
                       final Collection val) throws CalFacadeException {
    if ((val == null) || val.isEmpty()) {
      return;
    }

    try {
      xml.property(tag, val.toString());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlProp(final XmlEmit xml,
                       final String pname,
                       final QName ptype,
                       final String val) throws CalFacadeException {
    QName tag = new QName(XcalTags.namespace, pname.toLowerCase());

    try {
      xml.openTag(tag);
      xml.property(ptype, val);
      xml.closeTag(tag);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void xmlParameter(final XmlEmit xml,
                            final Parameter val) throws CalFacadeException {
    try {
      ParameterInfoIndex pii = ParameterInfoIndex.lookupPname(val.getName());

      QName ptype = XcalTags.textVal;

      if (pii != null) {
        DataType dtype = pii.getPtype();
        if (dtype != null) {
          ptype = dtype.getXcalType();
        }
      }

      if (ptype.equals(XcalTags.textVal)) {
        QName tag = new QName(XcalTags.namespace, val.getName().toLowerCase());
        xml.property(tag, val.getValue());
      } else {
        QName tag = openTag(xml, val.getName());
        xml.property(ptype, val.getValue());
        xml.closeTag(tag);
      }
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Write a collection of calendar data as iCalendar
   *
   * @param vals
   * @param methodType    int value fromIcalendar
   * @param wtr Writer for output
   * @throws CalFacadeException
   */
  public void writeCalendar(final Collection vals,
                            final int methodType,
                            final Writer wtr) throws CalFacadeException {
    try {
      Calendar cal = toIcal(vals, methodType);
      writeCalendar(cal, wtr);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Turn a collection of events into a calendar
   *
   * @param vals
   * @param methodType    int value fromIcalendar
   * @return Calendar
   * @throws CalFacadeException
   */
  public Calendar toIcal(final Collection vals,
                         final int methodType) throws CalFacadeException {
    Calendar cal = newIcal(methodType);

    if ((vals == null) || (vals.size() == 0)) {
      return cal;
    }

    TreeSet<String> added = new TreeSet<String>();

    try {
      Iterator it = vals.iterator();
      while (it.hasNext()) {
        Object o = it.next();

        if (o instanceof EventInfo) {
          addToCalendar(cal, (EventInfo)o, added);
        } else {
          // XXX implement
          warn("Unimplemented toIcal for " + o.getClass().getName());
          continue;
        }
      }

      return cal;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Get a TimeZone object from a VCALENDAR string definition
   *
   * @param val
   * @return TimeZone
   * @throws CalFacadeException
   */
  public TimeZone tzFromTzdef(final String val) throws CalFacadeException {
    StringReader sr = new StringReader(val);

    Icalendar ic = fromIcal(null, sr);

    if ((ic == null) ||
        (ic.size() != 0) || // No components other than timezones
        (ic.getTimeZones().size() != 1)) {
      if (debug) {
        debugMsg("Not icalendar");
      }
      throw new CalFacadeException("Not icalendar");
    }

    /* This should be the only timezone ion the Calendar object
     */
    return ic.getTimeZones().iterator().next().tz;
  }

  /** Convert the Icalendar reader to a Collection of Calendar objects
   *
   * @param col      collection the entities will live in - possibly null
   * @param rdr
   * @return Icalendar
   * @throws CalFacadeException
   */
  public Icalendar fromIcal(final BwCalendar col,
                            final Reader rdr) throws CalFacadeException {
    return fromIcal(col, rdr, null,
                    true,  // diff the contents
                    false); // don't merge attendees
  }

  /** Convert the Icalendar object to a Collection of Calendar objects
   *
   * @param col      collection the entities will live in - possibly null
   * @param ical
   * @param diff     True if we should assume we are updating existing events.
   * @return Icalendar
   * @throws CalFacadeException
   */
  public Icalendar fromIcal(final BwCalendar col,
                            final IcalendarType ical,
                            final boolean diff) throws CalFacadeException {

    Icalendar ic = new Icalendar();

    setSystemProperties();

    WsXMLTranslator bldr = new WsXMLTranslator(ic);

    try {
      Calendar cal = bldr.fromXcal(ical);

      return makeIc(col, ic, cal, diff, false);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Convert the Icalendar reader to a Collection of Calendar objects
   *
   * @param col      collection the entities will live in - possibly null
   * @param rdr
   * @param contentType
   * @param diff     True if we should assume we are updating existing events.
   * @param mergeAttendees True if we should only update our own attendee.
   * @return Icalendar
   * @throws CalFacadeException
   */
  public Icalendar fromIcal(final BwCalendar col,
                            final Reader rdr,
                            final String contentType,
                            final boolean diff,
                            final boolean mergeAttendees) throws CalFacadeException {
    try {
      Icalendar ic = new Icalendar();

      setSystemProperties();

      Calendar cal;

      if ((contentType != null) &&
          contentType.equals("application/calendar+xml")) {
        XmlCalendarBuilder bldr = new XmlCalendarBuilder(ic);

        cal = bldr.build(rdr);
      } else if ((contentType != null) &&
              contentType.equals("application/calendar+json")) {
        JsonCalendarBuilder bldr = new JsonCalendarBuilder(ic);

        cal = bldr.build(rdr);
      } else {
        CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

        UnfoldingReader ufrdr = new UnfoldingReader(rdr, true);

        cal = bldr.build(ufrdr);
      }

      return makeIc(col, ic, cal, diff, mergeAttendees);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (ParserException pe) {
      throw new IcalMalformedException(pe.getMessage());
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Add an override as part of an update
   * @param ei
   * @param comp
   * @throws CalFacadeException
   */
  public void addOverride(final EventInfo ei,
                          final JAXBElement<? extends BaseComponentType> comp) throws CalFacadeException {
    try {
      Calendar cal = new WsXMLTranslator(new Icalendar()).fromXcomp(comp);

      if (cal == null) {
        return;
      }

      Icalendar ic = new Icalendar();

      ic.addComponent(ei);

      makeIc(null, ic, cal, true, false);
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private Icalendar makeIc(final BwCalendar col,
                           final Icalendar ic,
                           final Calendar cal,
                           final boolean diff,
                           final boolean mergeAttendees) throws CalFacadeException {
    try {
      if (cal == null) {
        return ic;
      }

      PropertyList pl = cal.getProperties();
      Property prop = pl.getProperty(Property.PRODID);
      if (prop != null) {
        ic.setProdid(prop.getValue());
      }

      prop = pl.getProperty(Property.VERSION);
      if (prop != null) {
        ic.setVersion(prop.getValue());
      }

      ic.setMethod(getMethod(cal));

      prop = pl.getProperty(Property.CALSCALE);
      if (prop != null) {
        ic.setCalscale(prop.getValue());
      }

      Collection<CalendarComponent> clist = orderedComponents(cal.getComponents());
      for (CalendarComponent comp: clist) {
        if (comp instanceof VFreeBusy) {
          EventInfo ei = BwEventUtil.toEvent(cb, col, ic, comp, diff, mergeAttendees);

          if (ei != null) {
            ic.addComponent(ei);
          }
        } else if (comp instanceof VTimeZone) {
          ic.addTimeZone(doTimeZone((VTimeZone)comp));
        } else if ((comp instanceof VEvent) ||
                   (comp instanceof VToDo) ||
                   (comp instanceof VPoll) ||
                   (comp instanceof VAvailability)) {
          EventInfo ei = BwEventUtil.toEvent(cb, col, ic, comp, diff,
                                             mergeAttendees);

          if (ei != null) {
            ic.addComponent(ei);
          }
        }
      }

      return ic;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Get the method for the calendar
   *
   * @param val
   * @return String method
   */
  public static String getMethod(final Calendar val) {
    Property prop = val.getProperties().getProperty(Property.METHOD);
    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }

  /** Temp - update ical4j * /
  private static class CalBuilder extends CalendarBuilder {
    public CalBuilder(final CalendarParser parser,
                      final TimeZoneRegistry registry) {
      super(parser, registry);
    }

    @Override
    public void parameter(final String name, final String value) throws URISyntaxException {
      /* Here we look to see if the parameter is the TZID parameter. If it is and it
       * has a value then we fetch the actual timezone from our registry.
       *
       * This fetch may actually fetch a differently named timezone as we may
       * do some alias matching.
       *
       * If the fetched timezone has the same name we are done. Otherwise we
       * update the parameter value to reflect the actual fetched timezone.
       * /
      super.parameter(name, value);

      if (!name.toUpperCase().equals(Parameter.TZID)) {
        return;
      }

      Parameter tzParam = property.getParameter(Parameter.TZID);

      if (tzParam == null) {
        return;
      }

      /* Have an id wit a value * /

      TimeZone timezone = null;

      if (property  instanceof DateProperty){
        timezone = ((DateProperty)property).getTimeZone();
      } else  if (property  instanceof DateListProperty){
        timezone = ((DateListProperty) property).getTimeZone();
      }

      if (timezone == null) {
        return;
      }

      if (timezone.getID().equals(tzParam.getValue())) {
        return;
      }

      /* Fetched timezone has a different id * /

      ParameterList pl = property.getParameters();

      tzParam = ParameterFactoryImpl.getInstance().createParameter(Parameter.TZID,
                                                                   timezone.getID());
      pl.replace(tzParam);

    }
  }
  */

  /** Create a Calendar object from the named timezone
   *
   * @param tzid       String timezone id
   * @return Calendar
   * @throws CalFacadeException
   */
  public Calendar getTzCalendar(final String tzid) throws CalFacadeException {
    try {
      Calendar cal = newIcal(ScheduleMethods.methodTypeNone);

      addIcalTimezone(cal, tzid, null, Timezones.getTzRegistry());

      return cal;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /** Create a Calendar object from the named timezone and convert to
   * a String representation
   *
   * @param tzid       String timezone id
   * @return String
   * @throws CalFacadeException
   */
  public String toStringTzCalendar(final String tzid) throws CalFacadeException {
    Calendar ical = getTzCalendar(tzid);

    CalendarOutputter calOut = new CalendarOutputter(true);

    StringWriter sw = new StringWriter();

    try {
      calOut.output(ical, sw);

      return sw.toString();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @param val
   * @param methodType
   * @param pattern
   * @return XML IcalendarType
   * @throws CalFacadeException
   */
  public IcalendarType toXMLIcalendar(final EventInfo val,
                                      final int methodType,
                                      final IcalendarType pattern) throws CalFacadeException {
    IcalendarType ical = new IcalendarType();
    VcalendarType vcal = new VcalendarType();

    ical.getVcalendar().add(vcal);

    vcal.setProperties(new ArrayOfProperties());
    List<JAXBElement<? extends BasePropertyType>> pl = vcal.getProperties().getBasePropertyOrTzid();

    ProdidPropType prod = new ProdidPropType();
    prod.setText(prodId);
    pl.add(Xutil.of.createProdid(prod));

    VersionPropType vers = new VersionPropType();
    vers.setText("2.0");
    pl.add(Xutil.of.createVersion(vers));

    if ((methodType > ScheduleMethods.methodTypeNone) &&
        (methodType < ScheduleMethods.methodTypeUnknown)) {
      MethodPropType m = new MethodPropType();

      m.setText(ScheduleMethods.methods[methodType]);
      pl.add(Xutil.of.createMethod(m));
    }

    ArrayOfComponents aoc = vcal.getComponents();

    if (aoc == null) {
      aoc = new ArrayOfComponents();
      vcal.setComponents(aoc);
    }

    BwEvent ev = val.getEvent();
    JAXBElement<? extends BaseComponentType> el = null;

    VcalendarType vc = null;

    if ((pattern != null) &&
        !pattern.getVcalendar().isEmpty()) {
      vc = pattern.getVcalendar().get(0);
    }

    BaseComponentType bc = matches(vc, ev.getEntityType());
    if ((vc != null) && (bc == null)) {
      return ical;
    }

    if (!ev.getSuppressed()) {
      if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
        el = ToXEvent.toComponent(ev, false, bc);
      } else {
        el = ToXEvent.toComponent(ev, false, bc);
      }

      if (el != null) {
        aoc.getBaseComponent().add(el);
      }
    }

    if (val.getNumOverrides() == 0) {
      return ical;
    }

    for (EventInfo oei: val.getOverrides()) {
      ev = oei.getEvent();
      el = ToXEvent.toComponent(ev, true, bc);

      if (el != null) {
        aoc.getBaseComponent().add(el);
      }
    }

    if (val.getNumContainedItems() > 0) {
      for (EventInfo aei: val.getContainedItems()) {
        ev = aei.getEvent();
        el = ToXEvent.toComponent(ev, true, bc);

        if (el != null) {
          aoc.getBaseComponent().add(el);
        }
      }
    }

    return ical;
  }

  private BaseComponentType matches(final VcalendarType vc,
                                    final int entityType) throws CalFacadeException {
    if ((vc == null) || (vc.getComponents() == null)) {
      return null;
    }

    String nm;

    if (entityType == IcalDefs.entityTypeEvent) {
      nm = VeventType.class.getName();
    } else if (entityType == IcalDefs.entityTypeTodo) {
      nm = VtodoType.class.getName();
    } else if (entityType == IcalDefs.entityTypeJournal) {
      nm = VjournalType.class.getName();
    } else if (entityType == IcalDefs.entityTypeFreeAndBusy) {
      nm = VfreebusyType.class.getName();
    } else {
      throw new CalFacadeException("org.bedework.invalid.entity.type",
                                   String.valueOf(entityType));
    }

    for (JAXBElement<? extends BaseComponentType> jbc:
                     vc.getComponents().getBaseComponent()) {
      BaseComponentType bc = jbc.getValue();

      if (nm.equals(bc.getClass().getName())) {
        return bc;
      }
    }

    return null;
  }

  /**
   * @param val
   * @param methodType
   * @param pattern
   * @return JSON jcal
   * @throws CalFacadeException
   */
  public String toJcal(final EventInfo val,
                       final int methodType,
                       final IcalendarType pattern) throws CalFacadeException {
    String currentPrincipal = null;
    BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    List<EventInfo> eis = new ArrayList<>();

    eis.add(val);
    return JcalHandler.toJcal(eis, methodType, pattern,
                              cb.getURIgen(),
                              currentPrincipal,
                              new EventTimeZonesRegistry(this, val.getEvent()));
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  private void addToCalendar(final Calendar cal,
                             final EventInfo val,
                             final TreeSet<String> added) throws CalFacadeException {
    URIgen uriGen = cb.getURIgen();
    String currentPrincipal = null;
    BwPrincipal principal = cb.getPrincipal();

    if (principal != null) {
      currentPrincipal = principal.getPrincipalRef();
    }

    BwEvent ev = val.getEvent();

    EventTimeZonesRegistry tzreg = new EventTimeZonesRegistry(this, ev);

    /* Add referenced timezones to the calendar */
    addIcalTimezones(cal, ev, added, tzreg);

    if (!ev.getSuppressed()) {
      /* Add it to the calendar */
      Component comp;

      if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
        comp = VFreeUtil.toVFreeBusy(ev);
      } else {
        comp = VEventUtil.toIcalComponent(val, false, tzreg, uriGen,
                                          currentPrincipal);
      }
      cal.getComponents().add(comp);
    }

    if (val.getNumOverrides() > 0) {
      for (EventInfo oei: val.getOverrides()) {
        cal.getComponents().add(VEventUtil.toIcalComponent(oei, true, tzreg,
                                                           uriGen,
                                                           currentPrincipal));
      }
    }
  }

  /* Order all the components so that they are in the following order:
   *
   * XXX should use a TreeSet with a wrapper round the object and sort them including
   * dates etc.
   *
   * 1. Timezones
   * 2. Events without recurrence id
   * 3. Events with recurrence id
   * 4. Anything else
   */
  private static Collection<CalendarComponent> orderedComponents(final ComponentList clist) {
    SubList<CalendarComponent> tzs = new SubList<CalendarComponent>();
    SubList<CalendarComponent> fbs = new SubList<CalendarComponent>();
    SubList<CalendarComponent> instances = new SubList<CalendarComponent>();
    SubList<CalendarComponent> masters = new SubList<CalendarComponent>();

    Iterator it = clist.iterator();

    while (it.hasNext()) {
      CalendarComponent c = (CalendarComponent)it.next();

      if (c instanceof VFreeBusy) {
        fbs.add(c);
      } else if (c instanceof VTimeZone) {
        tzs.add(c);
      } else if (IcalUtil.getProperty(c, Property.RECURRENCE_ID) != null) {
        instances.add(c);
      } else {
        masters.add(c);
      }
    }

    ArrayList<CalendarComponent> all = new ArrayList<CalendarComponent>();

    tzs.appendTo(all);
    masters.appendTo(all);
    instances.appendTo(all);
    fbs.appendTo(all);

    return all;
  }

  private static class SubList<T> {
    ArrayList<T> list;

    void add(final T o) {
      if (list == null) {
        list = new ArrayList<T>();
      }
      list.add(o);
    }

    void appendTo(final Collection<T> c) {
      if (list != null) {
        c.addAll(list);
      }
    }
  }

  private TimeZoneInfo doTimeZone(final VTimeZone vtz) throws CalFacadeException {
    TzId tzid = vtz.getTimeZoneId();

    if (tzid == null) {
      throw new CalFacadeException("Missing tzid property");
    }

    String id = tzid.getValue();

    //if (debug) {
    //  debugMsg("Got timezone: \n" + vtz.toString() + " with id " + id);
    //}

    try {
      TimeZone tz = Timezones.getTz(id);
      String tzSpec = null;

      if (tz == null) {
        tz = new TimeZone(vtz);
        tzSpec = vtz.toString();
      }

      return new TimeZoneInfo(id, tz, tzSpec);
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* If the start or end dates references a timezone, we retrieve the timezone definition
   * and add it to the calendar.
   */
  private void addIcalTimezones(final Calendar cal,
                                final Collection vals) throws CalFacadeException {
    TreeSet<String> added = new TreeSet<String>();

    Iterator it = vals.iterator();
    while (it.hasNext()) {
      Object o = it.next();

      if (o instanceof EventInfo) {
        EventInfo ei = (EventInfo)o;
        BwEvent ev = ei.getEvent();

        if (!ev.getSuppressed()) {
          /* Add referenced timezones to the calendar */
          addIcalTimezones(cal, ev, added,
                           new EventTimeZonesRegistry(this, ev));
        }

        if (ei.getNumOverrides() > 0) {
          for (EventInfo oei: ei.getOverrides()) {
            ev = oei.getEvent();
            addIcalTimezones(cal, ev, added,
                             new EventTimeZonesRegistry(this, ev));
          }
        }
      }
    }
  }

  /* If the start or end date references a timezone, we retrieve the timezone definition
   * and add it to the calendar.
   */
  private void addIcalTimezones(final Calendar cal, final BwEvent ev,
                                final TreeSet<String> added,
                                final TimeZoneRegistry tzreg) throws CalFacadeException {
    if (ev.getEntityType() == IcalDefs.entityTypeFreeAndBusy) {
      return;
    }

    if (!ev.getForceUTC()) {
      addIcalTimezone(cal, ev.getDtstart().getTzid(), added, tzreg);

      if (ev.getEndType() == StartEndComponent.endTypeDate) {
        addIcalTimezone(cal, ev.getDtend().getTzid(), added, tzreg);
      }

      //if (ev.getRecurrenceId() != null) {
      //  addIcalTimezone(cal, ev.getRecurrenceId().getTzid(), added, tzreg);
      //}
    }
  }

  private void addIcalTimezone(final Calendar cal, final String tzid,
                               final TreeSet<String> added,
                               final TimeZoneRegistry tzreg) throws CalFacadeException {
    VTimeZone vtz = null;

    if ((tzid == null) ||
        ((added != null) && added.contains(tzid))) {
      return;
    }

    if (debug) {
      debugMsg("Look for timezone with id " + tzid);
    }

    TimeZone tz = tzreg.getTimeZone(tzid);

    if (tz != null) {
      vtz = tz.getVTimeZone();
    }

    if (vtz != null) {
      if (debug) {
        debugMsg("found timezone with id " + tzid);
      }
      cal.getComponents().add(vtz);
    } else if (debug) {
      debugMsg("Didn't find timezone with id " + tzid);
    }

    if (added != null) {
      added.add(tzid);
    }
  }

  private static void setSystemProperties() throws CalFacadeException {
    try {
      System.setProperty("ical4j.unfolding.relaxed", "true");
      System.setProperty("ical4j.parsing.relaxed", "true");
      System.setProperty("ical4j.compatibility.outlook", "true");
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private Logger getLog() {
    if (log != null) {
      return log;
    }

    log = Logger.getLogger(this.getClass());
    return log;
  }

  private void warn(final String msg) {
    getLog().warn(msg);
  }

  private void debugMsg(final String msg) {
    getLog().debug(msg);
  }
}

