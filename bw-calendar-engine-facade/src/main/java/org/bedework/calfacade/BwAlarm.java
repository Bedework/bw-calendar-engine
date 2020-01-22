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

import org.bedework.calfacade.BwXproperty.Xpar;
import org.bedework.calfacade.annotations.Dump;
import org.bedework.calfacade.annotations.NoDump;
import org.bedework.calfacade.annotations.ical.IcalProperties;
import org.bedework.calfacade.annotations.ical.IcalProperty;
import org.bedework.calfacade.annotations.ical.NoProxy;
import org.bedework.calfacade.base.AttendeesEntity;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.DescriptionEntity;
import org.bedework.calfacade.base.Differable;
import org.bedework.calfacade.base.SummaryEntity;
import org.bedework.calfacade.base.XpropsEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.util.BwDateTimeUtil;
import org.bedework.calfacade.util.CalFacadeUtil;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;

import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.property.Duration;
import net.fortuna.ical4j.model.property.Trigger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** An alarm in bedework representing an rfc2445 valarm object.
 *
 * <p>The same alarm entity may be referred to by some or all instances of a
 * recurring event. The alarm entity contains the real time (UTC) at which it
 * will next trigger. This is derived from the event which refers to it.
 *
 * <p>When an event is triggered we determine if that event is now expired or
 * if it now has a new trigger time based on another instance for that event.
 * We update the entity to reflect that.
 *
 * <p>Expired events are explicitly flagged as such. We don't use time as the
 * indicator as system down time could lead to alarms never being triggered.
 * This could lead to an alarm storm if there is a long down time.
 *
 *  @version 1.0
 *  @author Mike Douglass   douglm . rpi.edu
 */
@Dump(elementName="alarm", keyFields={"event"})
public class BwAlarm extends BwOwnedDbentity<BwAlarm>
        implements AttendeesEntity, DescriptionEntity<BwString>, SummaryEntity,
                   Differable<BwAlarm>, XpropsEntity, Serializable {
  /** audio */
  public final static int alarmTypeAudio = 0;

  /** display*/
  public final static int alarmTypeDisplay = 1;

  /** email */
  public final static int alarmTypeEmail = 2;

  /** procedure */
  public final static int alarmTypeProcedure = 3;

  /** none */
  public final static int alarmTypeNone = 4;

  /** other - name in x-props */
  public final static int alarmTypeOther = 5;

  /** Names for type of alarm */
  public final static String[] alarmTypes = {
    "AUDIO",
    "DISPLAY",
    "EMAIL",
    "PROCEDURE",
    "NONE",
    "OTHER"};

  protected int alarmType;

  protected String trigger;
  protected boolean triggerStart;
  protected boolean triggerDateTime;
  protected String duration;
  protected int repeat;

  protected int repeatCount;
  protected boolean expired;

  protected String attach;

  private Set<BwString> summaries;

  private Set<BwString> descriptions;

  protected Set<BwAttendee> attendees;

  /** Collection of BwXproperty
   */
  private List<BwXproperty> xproperties;

  /* ------------------------- Non-db fields ---------------------------- */

  /** Calculated on a call to getTriggerDate()
   */
  protected Date triggerDate;

  /** Used fpr constructors
   */
  public static class TriggerVal {
    /** This specifies the time for the alarm in rfc format */
    public String trigger;
    /** true if we trigger off the start */
    public boolean triggerStart;
    /** true if trigger is a date time value */
    public boolean triggerDateTime;
  }


  /** Constructor
   *
   */
  public BwAlarm() {
  }

  /** Constructor for all fields
   *
   * @param owner         Owner of alarm
   * @param alarmType     type of alarm
   * @param trigger       Trigger info
   * @param duration      External form of duration
   * @param repeat        number of repetitions
   * @param repeatCount   Repetition we are currently handling
   * @param expired       Set to true when we're done
   * @param attach        String audio file or attachment or exec
   * @param description   String description
   * @param summary       String summary (email)
   * @param attendees     Set of attendees
   */
  private BwAlarm(final String owner,
                  final int alarmType,
                  final TriggerVal trigger,
                  final String duration,
                  final int repeat,
                  final int repeatCount,
                  final boolean expired,
                  final String attach,
                  final String description,
                  final String summary,
                  final Set<BwAttendee> attendees) {
    super();
    setOwnerHref(owner);
    setPublick(false);
    this.alarmType = alarmType;
    this.trigger = trigger.trigger;
    this.triggerStart = trigger.triggerStart;
    this.triggerDateTime = trigger.triggerDateTime;
    this.duration = duration;
    this.repeat = repeat;
    this.repeatCount = repeatCount;
    this.expired = expired;
    this.attach = attach;
    addDescription(new BwString(null, description));
    addSummary(new BwString(null, summary));
    setAttendees(attendees);
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /** Set the alarmType for this event
   *
   * <p>This corrresponds to the ACTION property but perhaps not close enough
   *
   * @param val    alarmType
   */
  @IcalProperty(pindex = PropertyInfoIndex.ACTION,
                alarmProperty = true)
  public void setAlarmType(final int val) {
    alarmType = val;
  }

  /** Get the alarmType
   *
   * @return int    alarmType
   */
  public int getAlarmType() {
    return alarmType;
  }

  /** Set the trigger - rfc format
   *
   * @param val    String trigger value
   */
  @IcalProperty(pindex = PropertyInfoIndex.TRIGGER,
                alarmProperty = true)
  public void setTrigger(final String val) {
    trigger = val;
  }

  /** Get the trigger in rfc format
   *
   *  @return String   trigger value
   */
  public String getTrigger() {
    return trigger;
  }

  /** Set the triggerStart flag
   *
   *  @param val    boolean true if we trigger off start
   */
  public void setTriggerStart(final boolean val) {
    triggerStart = val;
  }

  /** Get the triggerStart flag
   *
   *  @return boolean    true if we trigger off start
   */
  public boolean getTriggerStart() {
    return triggerStart;
  }

  /** Set the triggerDateTime flag
   *
   *  @param val    boolean true if we trigger off DateTime
   */
  public void setTriggerDateTime(final boolean val) {
    triggerDateTime = val;
  }

  /** Get the triggerDateTime flag
   *
   *  @return boolean    true if we trigger off DateTime
   */
  @IcalProperties({
          @IcalProperty(pindex = PropertyInfoIndex.TRIGGER_DATE_TIME,
                  jname = "triggerDateTime",
                  eventProperty = true,
                  todoProperty = true,
                  journalProperty = true,
                  freeBusyProperty = true,
                  timezoneProperty = true),
          @IcalProperty(pindex = PropertyInfoIndex.NEXT_TRIGGER_DATE_TIME,
                  jname = "nextTrigger",
                  alarmProperty = true)
  })
  public boolean getTriggerDateTime() {
    return triggerDateTime;
  }

  /** Set the duration - rfc format
   *
   * @param val    String duration value
   */
  @IcalProperty(pindex = PropertyInfoIndex.DURATION,
                alarmProperty = true
                )
  public void setDuration(final String val) {
    duration = val;
  }

  /** Get the duration in rfc format
   *
   *  @return String   duration value
   */
  public String getDuration() {
    return duration;
  }

  /** Set the repetition count for this alarm, 0 means no repeat, 1 means
   * 2 alarms will be sent etc.
   *
   * @param val   repetition count
   */
  @IcalProperty(pindex = PropertyInfoIndex.REPEAT,
                alarmProperty = true)
  public void setRepeat(final int val) {
    repeat = val;
  }

  /** Get the repetition count
   *
   * @return int    the repetition count
   */
  public int getRepeat() {
    return repeat;
  }

  /** Set the current repetition count for this alarm.
   *
   * @param val   repetition count
   */
  public void setRepeatCount(final int val) {
    repeatCount = val;
  }

  /** Get the current repetition count
   *
   * @return int    the repetition count
   */
  public int getRepeatCount() {
    return repeatCount;
  }

  /** Set the expired flag
   *
   *  @param val    boolean true if the alarm has expired
   */
  public void setExpired(final boolean val) {
    expired = val;
  }

  /** Get the expired flag
   *
   *  @return boolean    true if expired
   */
  public boolean getExpired() {
    return expired;
  }

  /* May force a change - attach only allowed once on an alarm
  @IcalProperty(pindex = PropertyInfoIndex.ATTACH,
                alarmProperty = true)
   */
  /** Set the attachment
   *
   * @param val    String attachment name
   */
  public void setAttach(final String val) {
    attach = val;
  }

  /** Get the attachment
   *
   *  @return String   attachment name
   */
  public String getAttach() {
    return attach;
  }

  /**
   * @return type of entity
   */
  @NoDump
  public int getEntityType() {
    return IcalDefs.entityTypeAlarm;
  }

  /* ====================================================================
   *                   AttendeesEntity interface methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.AttendeesEntity#setAttendees(java.util.Set)
   */
  @Override
  @IcalProperty(pindex = PropertyInfoIndex.ATTENDEE,
                jname = "attendee",
                adderName = "attendee",
                alarmProperty = true)
  public void setAttendees(final Set<BwAttendee> val) {
    attendees = val;
  }

  /* (non-Javadoc)
   * @see org.bedework.calfacade.base.AttendeesEntity#getAttendees()
   */
  @Override
  @Dump(collectionElementName = "attendee", compound = true)
  public Set<BwAttendee> getAttendees() {
    return attendees;
  }

  @Override
  @NoDump
  public int getNumAttendees() {
    Set<BwAttendee> as = getAttendees();
    if (as == null) {
      return 0;
    }

    return as.size();
  }

  @Override
  public void addAttendee(final BwAttendee val) {
    Set<BwAttendee> as = getAttendees();
    if (as == null) {
      as = new TreeSet<>();
      setAttendees(as);
    }

    as.add(val);
  }

  @Override
  public boolean removeAttendee(final BwAttendee val) {
    Set<BwAttendee> as = getAttendees();
    if (as == null) {
      return false;
    }

    return as.remove(val);
  }

  @Override
  public Set<BwAttendee> copyAttendees() {
    if (getNumAttendees() == 0) {
      return null;
    }

    return new TreeSet<>(getAttendees());
  }

  @Override
  public Set<BwAttendee> cloneAttendees() {
    if (getNumAttendees() == 0) {
      return null;
    }
    TreeSet<BwAttendee> ts = new TreeSet<>();

    for (BwAttendee att: getAttendees()) {
      ts.add((BwAttendee)att.clone());
    }

    return ts;
  }

  @Override
  public void setRecipients(final Set<String> val) {
  }

  @Override
  public Set<String> getRecipients() {
    return null;
  }

  @Override
  @NoDump
  public int getNumRecipients() {
    return 0;
  }

  @Override
  public void addRecipient(final String val) {
  }

  @Override
  public boolean removeRecipient(final String val) {
    return false;
  }

  /* ====================================================================
   *               DescriptionEntity interface methods
   * ==================================================================== */

  @Override
  @IcalProperty(pindex = PropertyInfoIndex.DESCRIPTION,
                jname = "description",
                adderName = "description",
                alarmProperty = true)
  public void setDescriptions(final Set<BwString> val) {
    descriptions = val;
  }

  @Override
  @Dump(collectionElementName = "description")
  public Set<BwString> getDescriptions() {
    return descriptions;
  }

  @Override
  @NoDump
  public int getNumDescriptions() {
    Set<BwString> rs = getDescriptions();
    if (rs == null) {
      return 0;
    }

    return rs.size();
  }

  @Override
  public void addDescription(final String lang, final String val) {
    addDescription(new BwString(lang, val));
  }

  @Override
  public void addDescription(final BwString val) {
    Set<BwString> rs = getDescriptions();
    if (rs == null) {
      rs = new TreeSet<>();
      setDescriptions(rs);
    }

    rs.add(val);
  }

  @Override
  public boolean removeDescription(final BwString val) {
    Set<BwString> rs = getDescriptions();
    if (rs == null) {
      return false;
    }

    return rs.remove(val);
  }

  @Override
  public void updateDescriptions(final String lang, final String val) {
    BwString s = findDescription(lang);
    if (val == null) {
      // Removing
      if (s!= null) {
        removeDescription(s);
      }
    } else if (s == null) {
      addDescription(lang, val);
    } else if ((CalFacadeUtil.cmpObjval(val, s.getValue()) != 0)) {
      // XXX Cannot change value in case this is an override collection.

      //s.setValue(val);
      removeDescription(s);
      addDescription(lang, val);
    }
  }

  @Override
  public BwString findDescription(final String lang) {
    return BwString.findLang(lang, getDescriptions());
  }

  @Override
  public void setDescription(final String val) {
    updateDescriptions(null, val);
  }

  @Override
  @NoDump
  public String getDescription() {
    BwString s = findDescription(null);
    if (s == null) {
      return null;
    }
    return s.getValue();
  }

  /* ====================================================================
   *               SummaryEntity interface methods
   * ==================================================================== */

  @Override
  public void setSummaries(final Set<BwString> val) {
    summaries = val;
  }

  @Override
  @Dump(collectionElementName = "summary")
  public Set<BwString> getSummaries() {
    return summaries;
  }

  @Override
  @NoDump
  public int getNumSummaries() {
    Set<BwString> rs = getSummaries();
    if (rs == null) {
      return 0;
    }

    return rs.size();
  }

  @Override
  public void addSummary(final BwString val) {
    Set<BwString> rs = getSummaries();
    if (rs == null) {
      rs = new TreeSet<>();
      setSummaries(rs);
    }

    rs.add(val);
  }

  @Override
  public boolean removeSummary(final BwString val) {
    Set<BwString> c = getSummaries();
    if (c == null) {
      return false;
    }

    return c.remove(val);
  }

  @Override
  public void updateSummaries(final String lang, final String val) {
    BwString s = findSummary(lang);
    if (val == null) {
      // Removing
      if (s!= null) {
        removeSummary(s);
      }
    } else if (s == null) {
      addSummary(new BwString(lang, val));
    } else if ((CalFacadeUtil.cmpObjval(val, s.getValue()) != 0)) {
      // XXX Cannot change value in case this is an override collection.

      //s.setValue(val);
      removeSummary(s);
      addSummary(new BwString(lang, val));
    }
  }

  @Override
  public BwString findSummary(final String lang) {
    return BwString.findLang(lang, getSummaries());
  }

  @Override
  public void setSummary(final String val) {
    updateSummaries(null, val);
  }

  @Override
  @NoProxy
  @NoDump
  public String getSummary() {
    BwString s = findSummary(null);
    if (s == null) {
      return null;
    }
    return s.getValue();
  }

  /* ====================================================================
   *                      X-prop methods
   * ==================================================================== */

  /**
   * @param val list of x-props
   */
  @IcalProperty(pindex = PropertyInfoIndex.XPROP,
                jname = "xprop",
                adderName = "xproperty",
                nested = true,
                keyindex = PropertyInfoIndex.NAME,
                eventProperty = true,
                todoProperty = true,
                journalProperty = true,
                freeBusyProperty = true,
                timezoneProperty = true)
  public void setXproperties(final List<BwXproperty> val) {
    xproperties = val;
  }

  /**
   * @return List<BwXproperty>
   */
  @Dump(collectionElementName = "xproperty", compound = true)
  public List<BwXproperty> getXproperties() {
    return xproperties;
  }

  /**
   * @return int
   */
  @NoProxy
  @NoDump
  public int getNumXproperties() {
    List<BwXproperty> c = getXproperties();
    if (c == null) {
      return 0;
    }

    return c.size();
  }

  /**
   *
   * @param val - name to match
   * @return list of matching properties - never null
   */
  @NoProxy
  @NoDump
  public List<BwXproperty> getXproperties(final String val) {
    List<BwXproperty> res = new ArrayList<>();
    List<BwXproperty> xs = getXproperties();
    if (xs == null) {
      return res;
    }

    for (BwXproperty x: xs) {
      if (x.getName().equals(val)) {
        res.add(x);
      }
    }

    return res;
  }

  /** Find x-properties storing the value of the named ical property
   *
   * @param val - name to match
   * @return list of matching properties - never null
   */
  @NoProxy
  @NoDump
  public List<BwXproperty> getXicalProperties(final String val) {
    List<BwXproperty> res = new ArrayList<>();
    List<BwXproperty> xs = getXproperties();
    if (xs == null) {
      return res;
    }

    for (BwXproperty x: xs) {
      if (x.getName().equals(BwXproperty.bedeworkIcalProp)) {
        List<Xpar> xpars = x.getParameters();

        Xpar xp = xpars.get(0);
        if (xp.getName().equals(val)) {
          res.add(x);
        }
      }
    }

    return res;
  }

  /** Remove all instances of the named property.
   *
   * @param val - name to match
   * @return number of removed proeprties
   */
  @NoProxy
  @NoDump
  public int removeXproperties(final String val) {
    List<BwXproperty> xs = getXproperties(val);

    if (xs.size() == 0) {
      return 0;
    }

    for (BwXproperty x: xs) {
      removeXproperty(x);
    }

    return xs.size();
  }

  /**
   * @param val an x-prop
   */
  @NoProxy
  public void addXproperty(final BwXproperty val) {
    List<BwXproperty> c = getXproperties();
    if (c == null) {
      c = new ArrayList<>();
      setXproperties(c);
    }

    if (!c.contains(val)) {
      c.add(val);
    }
  }

  /**
   * @param val an x-prop
   */
  @NoProxy
  public void removeXproperty(final BwXproperty val) {
    List<BwXproperty> c = getXproperties();
    if (c == null) {
      return;
    }

    c.remove(val);
  }

  /**
   * @return List of x-properties
   */
  @NoProxy
  public List<BwXproperty> cloneXproperty() {
    if (getNumXproperties() == 0) {
      return null;
    }
    ArrayList<BwXproperty> xs = new ArrayList<>();

    for (BwXproperty x: getXproperties()) {
      xs.add((BwXproperty)x.clone());
    }

    return xs;
  }

  /* ====================================================================
   *                      Convenience methods
   * ==================================================================== */

  /** Get the next trigger Date value. This is the next time for the
   * alarm.
   *
   * <p>This is based on the previous time which will have been set when the
   * alarm was last triggered.
   *
   * <p>Returns null for no more triggers.
   *
   * <p>Can be called repeatedly for the same result. To move to the next
   * trigger time, update repeatCount.
   *
   * @param start for instance to base this on 
   * @param previousTrigger null for first 
   *  @return Date   next trigger time as a date object
   *  @throws CalFacadeException on error
   */
  @NoDump
  public Date getNextTriggerDate(final BwDateTime start,
                                 final Date previousTrigger) throws CalFacadeException {
    if (previousTrigger == null) {
      // First time
      triggerDate = null;
      return getTriggerDate(start);
    }

    if (repeat == 0) {
      // No next trigger
      return null;
    }

    if (repeatCount == repeat) {
      // No next trigger
      return null;
    }

    repeatCount++;
    
    final Dur dur = new Duration(null, duration).getDuration();
    triggerDate = dur.getTime(previousTrigger);
    return triggerDate;
  }

  /** Get the trigger Date value. This is the earliest time for the
   * alarm.
   *
   *  @return Date   trigger time as a date object
   *  @throws CalFacadeException on error
   */
  @NoDump
  public Date getTriggerDate(final BwDateTime start) throws CalFacadeException {
    try {
      if (triggerDate != null) {
        return triggerDate;
      }

      final Trigger tr = new Trigger();
      tr.setValue(getTrigger());

      /* if dt is null then it's a duration????
       */
      Date dt = tr.getDateTime();
      if (dt == null) {
        final Dur dur = tr.getDuration();

        if (start == null) {
          throw new RuntimeException("No start date for alarm " + this);
        }

        dt = dur.getTime(BwDateTimeUtil.getDate(start));
      }

      triggerDate = dt;

      return dt;
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ====================================================================
   *                      Factory methods
   * ==================================================================== */

  /** Make an audio alarm
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param attach attachment
   * @return BwEventAlarm
   */
  public static BwAlarm audioAlarm(final String owner,
                                   final TriggerVal trigger,
                                   final String duration,
                                   final int repeat,
                                   final String attach) {
    return new BwAlarm(owner, alarmTypeAudio,
                       trigger,
                       duration, repeat,
                       0, false,
                       attach,
                       null, null, null);
  }

  /** Make a display alarm
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param description text
   * @return BwEventAlarm
   */
  public static BwAlarm displayAlarm(final String owner,
                                     final TriggerVal trigger,
                                     final String duration,
                                     final int repeat,
                                     final String description) {
    return new BwAlarm(owner, alarmTypeDisplay,
                       trigger,
                       duration, repeat,
                       0, false,
                       null, description, null, null);
  }

  /** Make an email alarm
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param attach to send
   * @param description text
   * @param summary text
   * @param attendees recipients
   * @return BwEventAlarm
   */
  public static BwAlarm emailAlarm(final String owner,
                                   final TriggerVal trigger,
                                   final String duration,
                                   final int repeat,
                                   final String attach,
                                   final String description,
                                   final String summary,
                                   final Set<BwAttendee> attendees) {
    return new BwAlarm(owner, alarmTypeEmail,
                       trigger,
                       duration, repeat,
                       0, false,
                       attach,
                       description, summary, attendees);
  }

  /** Make a procedure alarm
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param attach to send
   * @param description text
   * @return BwEventAlarm
   */
  public static BwAlarm procedureAlarm(final String owner,
                                       final TriggerVal trigger,
                                       final String duration,
                                       final int repeat,
                                       final String attach,
                                       final String description) {
    return new BwAlarm(owner, alarmTypeProcedure,
                       trigger,
                       duration, repeat,
                       0, false,
                       attach,
                       description, null, null);
  }

  /** Make a "ACTION:NONE" alarm
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param description text
   * @return BwEventAlarm
   */
  public static BwAlarm noneAlarm(final String owner,
                                  final TriggerVal trigger,
                                  final String duration,
                                  final int repeat,
                                  final String description) {
    return new BwAlarm(owner, alarmTypeNone,
                       trigger,
                       duration, repeat,
                       0, false,
                       null,
                       description, null, null);
  }

  /** Make an alarm for an unrecognizd action
   *
   * @param owner the href
   * @param trigger a TriggerVal
   * @param duration String dur value
   * @param repeat count
   * @param description text
   * @return BwEventAlarm
   */
  public static BwAlarm otherAlarm(final String owner,
                                   final String action,
                                   final TriggerVal trigger,
                                   final String duration,
                                   final int repeat,
                                   final String description) {
    BwAlarm al = new BwAlarm(owner, alarmTypeOther,
                             trigger,
                             duration, repeat,
                             0, false,
                             null,
                             description, null, null);
    al.addXproperty(BwXproperty.makeIcalProperty("ACTION",
                                                 null,
                                                 action));

    return al;
  }

  /*
----------------------------------------------------
     dur-value  = (["+"] / "-") "P" (dur-date / dur-time / dur-week)

     dur-date   = dur-day [dur-time]
     dur-time   = "T" (dur-hour / dur-minute / dur-second)
     dur-week   = 1*DIGIT "W"
     dur-hour   = 1*DIGIT "H" [dur-minute]
     dur-minute = 1*DIGIT "M" [dur-second]
     dur-second = 1*DIGIT "S"
     dur-day    = 1*DIGIT "D"


Description

    If the property permits, multiple "duration" values are
   specified by a COMMA character (US-ASCII decimal 44) separated list
   of values. The format is expressed as the [ISO 8601] basic format for
   the duration of time. The format can represent durations in terms of
   weeks, days, hours, minutes, and seconds.
   No additional content value encoding (i.e., BACKSLASH character
   encoding) are defined for this value type.


Example

    A duration of 15 days, 5 hours and 20 seconds would be:

     P15DT5H0M20S

   A duration of 7 weeks would be:

     P7W
--------------------------------------------------------*/

  @Override
  protected void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("type", alarmTypes[getAlarmType()]);

    if (getTriggerStart()) {
      ts.append("trigger(START)", getTrigger());
    } else {
      ts.append("trigger(END)", getTrigger());
    }

    if (getDuration() != null) {
      ts.append("duration", getDuration());
      ts.append("repeat", getRepeat());
    }

    if (getAlarmType() == alarmTypeAudio) {
      if (getAttach() != null) {
        ts.append("attach", getAttach());
      }
    } else if (getAlarmType() == alarmTypeDisplay) {
      ts.append("description", getDescription());
    } else if (getAlarmType() == alarmTypeEmail) {
      ts.append("description", getDescription());
      ts.append("summary", getSummary());
      ts.append("attendees", getAttendees());
      ts.append("attach", getAttach());
    } else if (getAlarmType() == alarmTypeProcedure) {
      ts.append("attach", getAttach());
      ts.append("description", getDescription());
    }
  }

  /** Here we attempt to see if an incomplete object matches this one. We match
   * in order:<br/>
   * action, trigger, duration, repeat
   *
   * <p>The absence of duration and repeat in the pattern is a problem if there
   * is one in the event set that has no duration/repeat and one that does.
   *
   * @param that alarm to match
   * @return true if it matches.
   */
  public boolean matches(final BwAlarm that)  {
    if (getAlarmType() != that.getAlarmType()) {
      return false;
    }

    if (that.getTrigger() == null) {
      return true;
    }

    if (getTriggerStart() != that.getTriggerStart()) {
      return false;
    }

    if (getTriggerDateTime() != that.getTriggerDateTime()) {
      return false;
    }

    if (!getTrigger().equals(that.getTrigger())) {
      return false;
    }

    if (that.getDuration() == null) {
      return true;
    }

    if (!getDuration().equals(that.getDuration())) {
      return false;
    }

    return getRepeat() == that.getRepeat();
  }

  /** An alarm is equal for same event, owner, action and trigger.
   *
   * <p>However, we also need to know if a particular alarm has been changed.
   *
   * @param that alarm to check
   * @return boolean true if this alarm is changed with respect to that
   */
  public boolean changed(final BwAlarm that)  {
    if (compareEqFields(that) != 0) {
      return true;
    }

    if (CalFacadeUtil.cmpObjval(getTrigger(), that.getTrigger()) != 0) {
      return true;
    }

    if (CalFacadeUtil.cmpBoolval(getTriggerStart(), that.getTriggerStart()) != 0) {
      return true;
    }

    if (CalFacadeUtil.cmpBoolval(getTriggerDateTime(), that.getTriggerDateTime()) != 0) {
      return true;
    }

    if (CalFacadeUtil.cmpObjval(getDuration(), that.getDuration()) != 0) {
      return true;
    }

    if (CalFacadeUtil.cmpIntval(getRepeat(), getRepeat()) != 0) {
      return true;
    }

    if (getAlarmType() == alarmTypeAudio) {
      return CalFacadeUtil.cmpObjval(getAttach(), that.getAttach()) != 0;
    }

    if (getAlarmType() == alarmTypeDisplay) {
      return CalFacadeUtil.cmpObjval(getDescription(), that.getDescription()) != 0;
    }

    if (getAlarmType() == alarmTypeEmail) {
      if (CalFacadeUtil.cmpObjval(getDescription(), that.getDescription()) != 0) {
        return true;
      }

      if (CalFacadeUtil.cmpObjval(getSummary(), that.getSummary()) != 0) {
        return true;
      }

      if (CalFacadeUtil.cmpObjval(getAttendees(), that.getAttendees()) != 0) {
        return true;
      }

      return CalFacadeUtil.cmpObjval(getAttach(), that.getAttach()) != 0;
    }

    if (getAlarmType() == alarmTypeProcedure) {
      if (CalFacadeUtil.cmpObjval(getAttach(), that.getAttach()) != 0) {
        return true;
      }

      return CalFacadeUtil.cmpObjval(getDescription(), that.getDescription()) != 0;
    }

    return false;
  }

  /** Compare fields that define equality
   *
   * @param that alarm to compare
   * @return int
   */
  public int compareEqFields(final BwAlarm that)  {
    int /*res = CalFacadeUtil.cmpObjval(getEvent(), that.getEvent());
    if (res != 0) {
      return res;
    }*/

    res = CalFacadeUtil.cmpObjval(getOwnerHref(), that.getOwnerHref());
    if (res != 0) {
      return res;
    }

    res = CalFacadeUtil.cmpIntval(getAlarmType(), getAlarmType());
    if (res != 0) {
      return res;
    }

    res = CalFacadeUtil.cmpObjval(getTrigger(), getTrigger());
    if (res != 0) {
      return res;
    }

    res = CalFacadeUtil.cmpBoolval(getTriggerDateTime(), getTriggerDateTime());
    if (res != 0) {
      return res;
    }

    return CalFacadeUtil.cmpBoolval(getTriggerStart(), that.getTriggerStart());
  }

  /* ====================================================================
   *                   Differable methods
   * ==================================================================== */

  @Override
  public boolean differsFrom(final BwAlarm val) {
    return changed(val);
  }

  /* ====================================================================
   *                      Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final BwAlarm that)  {
    if (this == that) {
      return 0;
    }

    return compareEqFields(that);
  }

  @Override
  public int hashCode() {
    int hc = 31 * getAlarmType();

    if (getOwnerHref() != null) {
      hc *= getOwnerHref().hashCode();
    }

    hc *= getTrigger().hashCode();
    if (getTriggerStart()) {
      hc *= 2;
    }

    return hc;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  @Override
  public Object clone() {
    try {
      final TriggerVal trigger = new TriggerVal();
      trigger.trigger = getTrigger();
      trigger.triggerStart = getTriggerStart();
      trigger.triggerDateTime = getTriggerDateTime();

      final BwAlarm a = new BwAlarm(null, // user
                                    getAlarmType(),
                                    trigger,
                                    getDuration(),
                                    getRepeat(),
                                    getRepeatCount(),
                                    getExpired(),
                                    getAttach(),
                                    getDescription(),
                                    getSummary(),
                                    cloneAttendees());

      // Don't clone event , they are cloning us

      a.setOwnerHref(getOwnerHref());

      return a;
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }
}

