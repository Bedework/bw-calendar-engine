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
package org.bedework.convert.ical;

import org.bedework.calfacade.BwAlarm.TriggerVal;
import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.BwXproperty.Xpar;
import org.bedework.calfacade.base.StartEndComponent;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ifs.IcalCallback;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.convert.Icalendar;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.calendar.ScheduleMethods;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.Timezones;

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.CalendarParserImpl;
import net.fortuna.ical4j.data.DefaultParameterFactorySupplier;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.data.UnfoldingReader;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Parameter;
import net.fortuna.ical4j.model.ParameterBuilder;
import net.fortuna.ical4j.model.ParameterFactory;
import net.fortuna.ical4j.model.ParameterList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.component.Participant;
import net.fortuna.ical4j.model.component.VPoll;
import net.fortuna.ical4j.model.parameter.AltRep;
import net.fortuna.ical4j.model.parameter.Cn;
import net.fortuna.ical4j.model.parameter.CuType;
import net.fortuna.ical4j.model.parameter.DelegatedFrom;
import net.fortuna.ical4j.model.parameter.DelegatedTo;
import net.fortuna.ical4j.model.parameter.Dir;
import net.fortuna.ical4j.model.parameter.Encoding;
import net.fortuna.ical4j.model.parameter.FmtType;
import net.fortuna.ical4j.model.parameter.Language;
import net.fortuna.ical4j.model.parameter.Member;
import net.fortuna.ical4j.model.parameter.PartStat;
import net.fortuna.ical4j.model.parameter.Role;
import net.fortuna.ical4j.model.parameter.Rsvp;
import net.fortuna.ical4j.model.parameter.ScheduleStatus;
import net.fortuna.ical4j.model.parameter.SentBy;
import net.fortuna.ical4j.model.parameter.StayInformed;
import net.fortuna.ical4j.model.parameter.Value;
import net.fortuna.ical4j.model.property.Attach;
import net.fortuna.ical4j.model.property.Attendee;
import net.fortuna.ical4j.model.property.CalendarAddress;
import net.fortuna.ical4j.model.property.DateListProperty;
import net.fortuna.ical4j.model.property.DateProperty;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Due;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.Organizer;
import net.fortuna.ical4j.model.property.PollItemId;
import net.fortuna.ical4j.model.property.Repeat;
import net.fortuna.ical4j.model.property.Trigger;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.Version;
import net.fortuna.ical4j.model.property.XProperty;

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Supplier;

/** Class to provide utility methods for ical4j classes
 *
 * @author Mike Douglass   douglm    rpi.edu
 */
public class IcalUtil {
  private final static BwLogger logger =
          new BwLogger().setLoggedClass(IcalUtil.class);

  private static final Supplier<List<ParameterFactory<?>>> parameterFactorySupplier =
          new DefaultParameterFactorySupplier();

  /* *
   * @param p ical4j Property
   * @return AbbreviatedValue
   * /
  public static AbbreviatedValue getAbbrevVal(final Property p) {
    ParameterList pars = p.getParameters();

    ParameterList abbrevPars = pars.getParameters(Parameter.ABBREV);

    Collection<String> abbrevs = new ArrayList<>();

    Iterator<Parameter> it = abbrevPars.iterator();
    while (it.hasNext()) {
      Parameter par = it.next();
      abbrevs.add(par.getValue());
    }

    return new AbbreviatedValue(abbrevs, p.getValue());
  }*/

  /** This is used to extract the full iCalendar representation from
   * the x-properties. This is used to avoid extending the schema to
   * preserve extra properties specifically on attendee and attachments.
   *
   * @param ev with possible xprops
   * @param compName we want
   * @return the Component or null
   */
  public static Component getXcomp(final BwEvent ev,
                                   final String compName) {
    final List<BwXproperty> xcompProps =
            ev.getXproperties(BwXproperty.bedeworkIcal);

    if (Util.isEmpty(xcompProps)) {
      return null;
    }

    final BwXproperty xcompProp = xcompProps.get(0);
    final String xcompPropVal = xcompProp.getValue();

    if (xcompPropVal == null) {
      return null;
    }

    final StringBuilder sb = new StringBuilder();
    final Icalendar ic = new Icalendar();

    try {
      sb.append("BEGIN:VCALENDAR\n");
      sb.append(Version.VERSION_2_0.toString());
      sb.append("\n");
      sb.append(xcompPropVal);
      if (!xcompPropVal.endsWith("\n")) {
        sb.append("\n");
      }
      sb.append("END:VCALENDAR\n");

      final CalendarBuilder bldr =
              new CalendarBuilder(new CalendarParserImpl(), ic);

      final UnfoldingReader ufrdr =
              new UnfoldingReader(new StringReader(sb.toString()),
                                  true);

      final Calendar cal = bldr.build(ufrdr);

      if (cal != null) {
        return cal.getComponent(compName);
      }
    } catch (final Throwable t) {
      logger.error(t);
      logger.error("Trying to parse:\n" + xcompPropVal);
    }

    return null;
  }

  /**
   * @param pl ical4j property list
   * @param xprops list of bw xprops
   */
  @SuppressWarnings("deprecation")
  public static void xpropertiesToIcal(final PropertyList<Property> pl,
                                       final List<BwXproperty> xprops) {
    for (final BwXproperty x: xprops) {
      final String xname = x.getName();

      if (xname.equals(BwXproperty.bedeworkIcalProp)) {
        // Some get squirreled away in here -mostly for alarms
        List<Xpar> params = x.getParameters();

        final String pname = params.get(0).getValue();

        if (params.size() == 1) {
          params = null;
        } else {
          params = new ArrayList<>(params);
          params.remove(0);
        }

        // XXX Need to be able to do this in ical4j - create property by name
        if (pname.equals("UID")) {
          pl.add(new Uid(makeXparlist(params), x.getValue()));
        }
      }

      if (x.getSkip()) {
        continue;
      }

      // Skip any timezone we saved in the event.
      if (xname.startsWith(BwXproperty.bedeworkTimezonePrefix)) {
        continue;
      }

      addXproperty(pl, xname, x.getParameters(), x.getValue());
    }
  }

  /**
   * @param pars List of Xpar
   * @return par list - always non-null
   */
  public static ParameterList makeXparlist(final List<BwXproperty.Xpar> pars) {
    final ParameterList xparl = new ParameterList();

    if (pars == null) {
      return xparl;
    }

    for (final BwXproperty.Xpar xpar: pars) {
      String xval = xpar.getValue();
      if ((xval.contains(":")) ||
          (xval.contains(";")) ||
          (xval.contains(","))) {
        xval = "\"" + xval + "\"";
      }

      try {
        xparl.add(new ParameterBuilder()
                .factories(parameterFactorySupplier.get())
                .name(xpar.getName().toUpperCase()).value(xval).build());

      } catch (final URISyntaxException e) {
        throw new RuntimeException(e);
      }
    }

    return xparl;
  }

  /**
   * @param pl ical4j PropertyList
   * @param name of xprop
   * @param pars List of Xpar
   * @param val new value
   */
  public static void addXproperty(final PropertyList<Property> pl,
                                  final String name,
                                  final List<BwXproperty.Xpar> pars,
                                  final String val) {
    if (val == null) {
      return;
    }
    pl.add(new XProperty(name, makeXparlist(pars), val));
  }

  /* *
   * @param p ical4j Property
   * @return TypedUrl
   * /
  public static TypedUrl getTypedUrl(final Property p) {
    TypedUrl tu = new TypedUrl();

    ParameterList pars = p.getParameters();

    Parameter par = pars.getParameter(Parameter.TYPE);

    if (par != null) {
      tu.setType(par.getValue());
    }

    tu.setValue(p.getValue());

    return tu;
  }*/

  /**
   * @param p ical4j Property
   * @return String
   */
  public static String getLang(final Property p) {
    final ParameterList pars = p.getParameters();

    final Parameter par = pars.getParameter(Parameter.LANGUAGE);

    if (par == null) {
      return null;
    }

    return par.getValue();
  }

  /**
   * @param val bw organizer object
   * @return Organizer
   */
  public static Organizer setOrganizer(final BwOrganizer val) {
    final ParameterList pars = new ParameterList();

    try {
      String temp = val.getScheduleStatus();
      if (temp != null) {
        pars.add(new ScheduleStatus(temp));
      }

      temp = val.getCn();
      if (temp != null) {
        pars.add(new Cn(temp));
      }
      temp = val.getDir();
      if (temp != null) {
        pars.add(new Dir(temp));
      }
      temp = val.getLanguage();
      if (temp != null) {
        pars.add(new Language(temp));
      }
      temp = val.getSentBy();
      if (temp != null) {
        pars.add(new SentBy(temp));
      }

      return new Organizer(pars, val.getOrganizerUri());
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  /** Make an organizer
   *
   * @param cb          IcalCallback object
   * @param orgProp ical4j Organizer
   * @return BwOrganizer
   */
  public static BwOrganizer getOrganizer(final IcalCallback cb,
                                         final Organizer orgProp) {
    final BwOrganizer org = new BwOrganizer();

    org.setOrganizerUri(cb.getCaladdr(orgProp.getValue()));

    final ParameterList pars = orgProp.getParameters();

    org.setCn(IcalUtil.getOptStr(pars, "CN"));
    org.setDir(getOptStr(pars, "DIR"));
    org.setLanguage(getOptStr(pars, "LANGUAGE"));
    org.setScheduleStatus(getOptStr(pars, "SCHEDULE-STATUS"));
    org.setSentBy(getOptStr(pars, "SENT-BY"));

    return org;
  }

  /** make an attachment
   *
   * @param val bw attachment object
   * @return Attendee
   */
  public static Attach setAttachment(final BwAttachment val) {
    final ParameterList pars = new ParameterList();

    String temp = val.getFmtType();
    if (temp != null) {
      pars.add(new FmtType(temp));
    }

    temp = val.getEncoding();
    final String uri;

    if (temp == null) {
      uri = val.getUri();
    } else {
      pars.add(new Encoding(temp));

      temp = val.getValueType();
      if (temp != null) {
        pars.add(new Value(temp));
      }

      uri = val.getValue();
    }
    try {
      return new Attach(pars, uri);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * @param attProp Attach
   * @return BwAttachment
   */
  public static BwAttachment getAttachment(final Attach attProp) {
    BwAttachment att = new BwAttachment();

    ParameterList pars = attProp.getParameters();

    att.setFmtType(getOptStr(pars, "FMTTYPE"));
    att.setValueType(getOptStr(pars, "VALUE"));
    att.setEncoding(getOptStr(pars, "ENCODING"));

    if (att.getEncoding() == null) {
      att.setUri(attProp.getValue());
    } else {
      att.setValue(attProp.getValue());
    }

    return att;
  }

  /** make an attendee
   *
   * @param val BwAttendee to build from
   * @return Attendee
   */
  public static Attendee setAttendee(final BwAttendee val) {
    final Attendee prop;
    try {
      prop = new Attendee(val.getAttendeeUri());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }

    final ParameterList pars = prop.getParameters();

    setAttendeeVoter(val, pars);

    final String temp = val.getPartstat();

    if ((temp != null) && !temp.equals(IcalDefs.partstatValNeedsAction)) {
      // Not default value.
      pars.add(new PartStat(temp));
    }

    return prop;
  }

  /** make a voter
   *
   * @param val BwAttendee to build from
   * @return Attendee
   */
  public static Participant setVoter(final BwAttendee val) {
    final Participant part = new Participant();

    final PropertyList props = part.getProperties();

    final CalendarAddress ca;
    try {
      ca = new CalendarAddress(val.getAttendeeUri());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    props.add(ca);

    final ParameterList pars = ca.getParameters();

    setAttendeeVoter(val, pars);

    final String temp = val.getPartstat();

    pars.add(new PartStat(temp));
    /*
    if ((temp != null) && !temp.equals(IcalDefs.partstatValNeedsAction)) {
      // Not default value.
      pars.add(new PartStat(temp));
    }*/

    return part;
  }

  /**
   * @param poll the poll entity
   * @return Parsed PARTICPANT components map - key is voter cua.
   */
  public static Map<String, Participant> parseVpollVoters(final BwEvent poll) {
    final StringBuilder sb = new StringBuilder();

    // Better if ical4j supported sub-component parsing

    sb.append("BEGIN:VCALENDAR\n");
    sb.append("PRODID://Bedework.org//BedeWork V3.9//EN\n");
    sb.append("VERSION:2.0\n");
    sb.append("BEGIN:VPOLL\n");
    sb.append("UID:0123\n");

    if (!Util.isEmpty(poll.getVoters())) {
      for (final String s: poll.getVoters()) {
        sb.append(s);
      }
    }

    sb.append("END:VPOLL\n");
    sb.append("END:VCALENDAR\n");

    final StringReader sr = new StringReader(sb.toString());

    final Icalendar ic = new Icalendar();

    final CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

    final UnfoldingReader ufrdr = new UnfoldingReader(sr, true);

    final Calendar ical;
    try {
      ical = bldr.build(ufrdr);
    } catch (IOException | ParserException e) {
      throw new RuntimeException(e);
    }

    final Map<String, Participant> voters = new HashMap<>();

    /* Should be one vpoll object */

    final VPoll vpoll = (VPoll)ical.getComponent(Component.VPOLL);
    for (final Object o: vpoll.getVoters()) {
      final Participant voter = (Participant)o;

      final CalendarAddress ca =
              (CalendarAddress)voter.getProperty(Property.CALENDAR_ADDRESS);
      if (ca == null) {
        continue;
      }

      voters.put(ca.getValue(), voter);
    }

    return voters;
  }

  /**
   * @param poll the poll entity
   * @return Parsed components.
   */
  public static Map<Integer, Component> parseVpollCandidates(final BwEvent poll) {
    final StringBuilder sb = new StringBuilder();

    sb.append("BEGIN:VCALENDAR\n");
    sb.append("PRODID://Bedework.org//BedeWork V3.9//EN\n");
    sb.append("VERSION:2.0\n");

    if (!Util.isEmpty(poll.getPollItems())) {
      for (final String s: poll.getPollItems()) {
        sb.append(s);
      }
    }

    sb.append("END:VCALENDAR\n");

    try {
      final StringReader sr = new StringReader(sb.toString());

      final Icalendar ic = new Icalendar();

      final CalendarBuilder bldr = new CalendarBuilder(new CalendarParserImpl(), ic);

      final UnfoldingReader ufrdr = new UnfoldingReader(sr, true);

      final Calendar ical = bldr.build(ufrdr);

      final Map<Integer, Component> comps = new HashMap<>();

      for (final Object o: ical.getComponents()) {
        final Component comp = (Component)o;

        final PollItemId pid = (PollItemId)comp.getProperty(Property.POLL_ITEM_ID);
        if (pid == null) {
          continue;
        }

        comps.put(pid.getPollitemid(), comp);
      }

      return comps;
    } catch (final ParserException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** copy pars from attendee
   *
   * @param val bw attendee
   */
  private static void setAttendeeVoter(final BwAttendee val,
                                       final ParameterList pars) {
    try {
      if (val.getRsvp()) {
        pars.add(Rsvp.TRUE);
      }

      String temp = val.getCn();
      if (temp != null) {
        pars.add(new Cn(temp));
      }

      temp = val.getScheduleStatus();
      if (temp != null) {
        pars.add(new ScheduleStatus(temp));
      }

      temp = val.getCuType();
      if (temp != null) {
        pars.add(new CuType(temp));
      }
      temp = val.getDelegatedFrom();
      if (temp != null) {
        pars.add(new DelegatedFrom(temp));
      }
      temp = val.getDelegatedTo();
      if (temp != null) {
        pars.add(new DelegatedTo(temp));
      }
      temp = val.getDir();
      if (temp != null) {
        pars.add(new Dir(temp));
      }
      temp = val.getLanguage();
      if (temp != null) {
        pars.add(new Language(temp));
      }
      temp = val.getMember();
      if (temp != null) {
        pars.add(new Member(temp));
      }
      temp = val.getRole();
      if (temp != null) {
        pars.add(new Role(temp));
      }

      temp = val.getSentBy();
      if (temp != null) {
        pars.add(new SentBy(temp));
      }
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /**
   * @param cb          IcalCallback object
   * @param attProp ical4j attendee
   * @return BwAttendee
   */
  public static BwAttendee getAttendee(final IcalCallback cb,
                                       final Attendee attProp) {
    ParameterList pars = attProp.getParameters();

    BwAttendee att = initAttendeeVoter(cb, attProp.getValue(),
                                       pars);

    att.setPartstat(getOptStr(pars, "PARTSTAT"));
    if (att.getPartstat() == null) {
      att.setPartstat(IcalDefs.partstatValNeedsAction);
    }

    att.setRole(getOptStr(pars, "ROLE"));

    return att;
  }

  /**
   * @param cb          IcalCallback object
   * @param ca - identifies the voter
   * @return BwAttendee
   */
  public static BwAttendee getVoter(final IcalCallback cb,
                                    final CalendarAddress ca) {
    ParameterList pars = ca.getParameters();

    BwAttendee att = initAttendeeVoter(cb, ca.getValue(),
                                       pars);

    att.setType(BwAttendee.typeVoter);

    Parameter par = pars.getParameter("STAY-INFORMED");
    if (par != null) {
      att.setStayInformed(((StayInformed)par).getStayInformed());
    }

    return att;
  }

  /**
   * @param cb          IcalCallback object
   * @param val        init attendee
   * @param pars        par list
   * @return BwAttendee
   */
  public static BwAttendee initAttendeeVoter(final IcalCallback cb,
                                             final String val,
                                             final ParameterList pars) {
    BwAttendee att = new BwAttendee();

    att.setAttendeeUri(cb.getCaladdr(val));

    att.setCn(getOptStr(pars, "CN"));
    att.setCuType(getOptStr(pars, "CUTYPE"));
    att.setDelegatedFrom(getOptStr(pars, "DELEGATED-FROM"));
    att.setDelegatedTo(getOptStr(pars, "DELEGATED-TO"));
    att.setDir(getOptStr(pars, "DIR"));
    att.setLanguage(getOptStr(pars, "LANGUAGE"));
    att.setMember(getOptStr(pars, "MEMBER"));
    att.setScheduleStatus(getOptStr(pars, "SCHEDULE-STATUS"));
    att.setSentBy(getOptStr(pars, "SENT-BY"));

    Parameter par = pars.getParameter("RSVP");
    if (par != null) {
      att.setRsvp(((Rsvp)par).getRsvp());
    }

    return att;
  }

  /**
   * @param pl ical4j property list
   * @param absentOk - if true and absent returns an empty result.
   * @return TriggerVal
   */
  public static TriggerVal getTrigger(final PropertyList pl,
                                      final boolean absentOk) {
    Trigger prop = (Trigger)pl.getProperty(Property.TRIGGER);
    TriggerVal tr = new TriggerVal();

    if (prop == null) {
      if (!absentOk) {
        throw new RuntimeException("Invalid alarm - no trigger");
      }

      return tr;
    }

    tr.trigger = prop.getValue();

    if (prop.getDateTime() != null) {
      tr.triggerDateTime = true;
      return tr;
    }

    ParameterList pars = prop.getParameters();

    if (pars == null) {
      tr.triggerStart = true;
      return tr;
    }

    Parameter par = pars.getParameter("RELATED");
    if (par == null) {
      tr.triggerStart = true;
      return tr;
    }

    tr.triggerStart = "START".equals(par.getValue());
    return tr;
  }

  static class DurationRepeat {
    String duration;
    int repeat;
  }

  /** Both or none appear once only
   *
   * @param pl ical4j property list
   * @return DurationRepeat
   */
  public static DurationRepeat getDurationRepeat(final PropertyList<Property> pl) {
    final DurationRepeat dr = new DurationRepeat();

    Property prop = pl.getProperty(Property.DURATION);
    if (prop == null) {
      return dr;
    }

    dr.duration = prop.getValue();

    prop = pl.getProperty(Property.REPEAT);
    if (prop == null) {
      throw new RuntimeException("Invalid alarm - no repeat");
    }

    dr.repeat = ((Repeat)prop).getCount();

    return dr;
  }

  /**
   * @param val ical4j DateListProperty
   * @return Collection
   */
  public static Collection<BwDateTime> makeDateTimes(final DateListProperty val) {
    DateList dl = val.getDates();
    TreeSet<BwDateTime> ts = new TreeSet<>();
    Parameter par = getParameter(val, "VALUE");
    boolean isDateType = (par != null) && (par.equals(Value.DATE));
    String tzidval = null;
    Parameter tzid = getParameter(val, "TZID");
    if (tzid != null) {
      tzidval = tzid.getValue();
    }

    for (final Date dt : dl) {
      ts.add(BwDateTime.makeBwDateTime(isDateType,
                                       dt.toString(),
                                       tzidval));
    }

    return ts;
  }

  /* *
   * @param cal ical4j calendar
   * @param comp ical4j Component
   * /
  public static void addComponent(final Calendar cal,
                                  final Component comp) {
    cal.getComponents().add((CalendarComponent)comp);
  }*/

  /**
   * @param comp ical4j Component
   * @param val ical4j property
   */
  public static void addProperty(final Component comp,
                                 final Property val) {
    PropertyList props =  comp.getProperties();

    props.add(val);
  }

  /**
   * @param prop ical4j property
   * @param val ical4j parameter
   */
  public static void addParameter(final Property prop,
                                  final Parameter val) {
    ParameterList parl =  prop.getParameters();

    parl.add(val);
  }

  /**
   * @param prop ical4j property
   * @param name of property
   * @return Parameter
   */
  public static Parameter getParameter(final Property prop,
                                       final String name) {
    ParameterList parl =  prop.getParameters();

    if (parl == null) {
      return null;
    }

    return parl.getParameter(name);
  }

  /**
   * @param prop ical4j property
   * @param name of property
   * @return Parameter value
   */
  public static String getParameterVal(final Property prop,
                                       final String name) {
    ParameterList parl =  prop.getParameters();

    if (parl == null) {
      return null;
    }

    Parameter par = parl.getParameter(name);

    if (par == null) {
      return null;
    }

    return par.getValue();
  }

  /**
   * @param comp ical4j Component
   * @param name of property
   * @return Property
   */
  public static Property getProperty(final Component comp,
                                     final String name) {
    final PropertyList<Property> props =  comp.getProperties();

    return props.getProperty(name);
  }

  /**
   * @param comp ical4j Component
   * @param name of property
   * @return PropertyList
   */
  public static PropertyList<Property> getProperties(
          final Component comp,
          final String name) {
    PropertyList<Property> props =  comp.getProperties();

    props = props.getProperties(name);
    if (props.size() == 0) {
      return null;
    }

    return props;
  }

  /** Return an Iterator over required String attributes
   *
   * @param pl ical4j property list
   * @param name of property
   * @return Iterator over required String attributes
   */
  public static Iterator<?> getReqStrs(final PropertyList<Property> pl,
                                       final String name) {
   final PropertyList<Property> props = pl.getProperties(name);

   if (props.isEmpty()) {
      throw new RuntimeException("Missing required property " + name);
    }

    return props.iterator();
  }

  /** Return required string property
   *
   * @param pl ical4j property list
   * @param name of property
   * @return String
   */
  public static String getReqStr(final PropertyList<Property> pl,
                                 final String name) {
    final Property prop = pl.getProperty(name);
    if (prop == null) {
      throw new RuntimeException("Missing required property " + name);
    }

    return prop.getValue();
  }

  /** Return optional string property
   *
   * @param pl ical4j property list
   * @param name of property
   * @return String or null
   */
  public static String getOptStr(final PropertyList<Property> pl,
                                 final String name) {
    final Property prop = pl.getProperty(name);
    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }

  /* *
   * @param comp ical4j Component
   * @param name of property
   * @return String
   * /
  public static String getPropertyVal(final Component comp,
                                      final String name) {
    Property prop =  getProperty(comp, name);
    if (prop == null) {
      return null;
    }

    return prop.getValue();
  }*/

  /** Return optional string parameter
   *
   * @param pl ical4j property list
   * @param name of property
   * @return String
   */
  public static String getOptStr(final ParameterList pl,
                                 final String name) {
    Parameter par = pl.getParameter(name);
    if (par == null) {
      return null;
    }

    return par.getValue();
  }

  /** Return the AltRep parameter if it exists
   *
   * @param prop ical4j property
   * @return AltRep
   */
  public static AltRep getAltRep(final Property prop) {
    return (AltRep)prop.getParameters().getParameter("ALTREP");
  }

  /** Make a DtEnd from the object
   *
   * @param val a BwDateTime
   * @return DtEnd ical4j dtend object
   */
  public static  DtEnd makeDtEnd(final BwDateTime val) {
    return makeDtEnd(val, Timezones.getTzRegistry());
  }

  /** Make a DtEnd from the object
   *
   * @param val a BwDateTime
   * @param tzreg timezone registry
   * @return DtEnd
   */
  public static  DtEnd makeDtEnd(final BwDateTime val,
                                 final TimeZoneRegistry tzreg) {
    DtEnd dt = new DtEnd();

    initDateProp(val, dt, tzreg);

    return dt;
  }

  /** Make a Due from the object
   *
   * @param val a BwDateTime
   * @param tzreg timezone registry
   * @return Due
   */
  public static Due makeDue(final BwDateTime val,
                            final TimeZoneRegistry tzreg) {
    Due dt = new Due();

    initDateProp(val, dt, tzreg);

    return dt;
  }

  /** Always return a DateTime object
   *
   * @param dt bw date time
   * @return DateTime
   */
  public static DateTime makeDateTime(final BwDateTime dt) {
    /* Ignore tzid for the moment */
    try {
      return new DateTime(dt.getDtval());
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }

  /** Set the dates in an event given a start and one or none of end and
   *  duration.
   *
   * @param userHref for changeset
   * @param ei the event
   * @param dtStart start
   * @param dtEnd end
   * @param duration duration
   */
  public static void setDates(final String userHref,
                              final EventInfo ei,
                              DtStart dtStart, DtEnd dtEnd,
                              final Duration duration) {
    BwEvent ev = ei.getEvent();
    ChangeTable chg = ei.getChangeset(userHref);
    boolean scheduleReply = ev.getScheduleMethod() == ScheduleMethods.methodTypeReply;
    boolean todo = ev.getEntityType() == IcalDefs.entityTypeTodo;
    boolean vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

    // No dates valid for reply

    if (dtStart == null) {
      if (!scheduleReply && !todo && !vpoll) {
        throw new RuntimeException("org.bedework.error.nostartdate");
      }

      /* A task or vpoll can have no date and time. set start to now, end to
         * many years from now and the noStart flag.
         *
         * A todo without dates has to appear only on the current day.
         */
      if (dtEnd != null) {
        try {
          dtStart = new DtStart(dtEnd.getParameters(), dtEnd.getValue());
        } catch (ParseException e) {
          throw new RuntimeException(e);
        }
      } else {
        Date now = new Date(new java.util.Date().getTime());
        dtStart = new DtStart(now);
        dtStart.getParameters().add(Value.DATE);
      }

      ev.setNoStart(true);
    } else {
      ev.setNoStart(false);
    }

    BwDateTime bwDtStart = BwDateTime.makeBwDateTime(dtStart);
    if (!CalFacadeUtil.eqObjval(ev.getDtstart(), bwDtStart)) {
      chg.changed(PropertyInfoIndex.DTSTART, ev.getDtstart(), bwDtStart);
      ev.setDtstart(bwDtStart);
    }

    char endType = StartEndComponent.endTypeNone;

    if (dtEnd != null) {
      endType = StartEndComponent.endTypeDate;
    } else if (scheduleReply || todo || vpoll) {
      Dur years = new Dur(520); // about 10 years
      Date now = new Date(new java.util.Date().getTime());
      dtEnd = new DtEnd(new Date(years.getTime(now)));
      dtEnd.getParameters().add(Value.DATE);
    }

    if (dtEnd != null) {
      BwDateTime bwDtEnd = BwDateTime.makeBwDateTime(dtEnd);
      if (!CalFacadeUtil.eqObjval(ev.getDtend(), bwDtEnd)) {
        chg.changed(PropertyInfoIndex.DTEND, ev.getDtend(), bwDtEnd);
        ev.setDtend(bwDtEnd);
      }
    }

    /* If we were given a duration store it in the event and calculate
          an end to the event - which we should not have been given.
       */
    if (duration != null) {
      if (endType != StartEndComponent.endTypeNone) {
        if (ev.getEntityType() != IcalDefs.entityTypeFreeAndBusy) {
          // Apple is sending both - duration indicates the minimum
          // freebusy duration. Ignore for now.
          throw new RuntimeException(CalFacadeException.endAndDuration);
        }
      }

      endType = StartEndComponent.endTypeDuration;
      String durVal = duration.getValue();
      if (!durVal.equals(ev.getDuration())) {
        chg.changed(PropertyInfoIndex.DURATION, ev.getDuration(), durVal);
        ev.setDuration(durVal);
      }

      ev.setDtend(BwDateTime.makeDateTime(dtStart,
                                          ev.getDtstart().getDateType(),
                                          durVal));
    } else if (!scheduleReply &&
            (endType == StartEndComponent.endTypeNone) && !todo) {
      /* No duration and no end specified.
         * Set the end values to the start values + 1 for dates
         */
      boolean dateOnly = ev.getDtstart().getDateType();
      final String dur;

      if (dateOnly) {
        dur = BwDateTime.oneDayForward;
      } else {
        dur = "P0D"; // No duration
      }
      BwDateTime bwDtEnd = BwDateTime.makeDateTime(dtStart, dateOnly, dur);
      if (!CalFacadeUtil.eqObjval(ev.getDtend(), bwDtEnd)) {
        chg.changed(PropertyInfoIndex.DTEND, ev.getDtend(), bwDtEnd);
        ev.setDtend(bwDtEnd);
      }
    }

    if ((endType != StartEndComponent.endTypeDuration) &&
            (ev.getDtstart() != null) &&
            (ev.getDtend() != null)) {
      // Calculate a duration
      String durVal = BwDateTime.makeDuration(ev.getDtstart(),
                                              ev.getDtend()).toString();
      if (!durVal.equals(ev.getDuration())) {
        chg.changed(PropertyInfoIndex.DURATION, ev.getDuration(), durVal);
        ev.setDuration(durVal);
      }
    }

    ev.setEndType(endType);
//    } catch (CalFacadeException cfe) {
//      throw cfe;
//    } catch (Throwable t) {
//      throw new CalFacadeException(t);
//    }
  }

  /* Init a date property for makeDtEnd, makeDue
   */
  private static void initDateProp(final BwDateTime val,
                                   final DateProperty dt,
                                   final TimeZoneRegistry tzreg) {
    String tzid = val.getTzid();

    ParameterList pl = dt.getParameters();

    if (val.getDateType()) {
      pl.add(Value.DATE);
    }

    if (tzid != null) {
      dt.setTimeZone(tzreg.getTimeZone(tzid));
    }

    try {
      dt.setValue(val.getDtval());
    } catch (final ParseException pe) {
      throw new RuntimeException(pe);
    }
  }
}

