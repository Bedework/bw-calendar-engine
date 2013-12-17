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
package org.bedework.calcore.indexing;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwLongString;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.property.RequestStatus;
import org.apache.log4j.Logger;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Implementation of indexer for ElasticSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class EntityBuilder  {
  private transient Logger log;

  private boolean debug;

  private Deque<Map<String, Object>> fieldStack = new ArrayDeque<>();

  /** Constructor - 1 use per entity
   *
   */
  EntityBuilder(final Map<String, Object> fields) {
    debug = getLog().isDebugEnabled();

    fieldStack.push(fields);
  }

  /* ========================================================================
   *                   package private methods
   * ======================================================================== */

  BwCategory makeCat() throws CalFacadeException {
    BwCategory cat = new BwCategory();

    restoreSharedEntity(cat);

    cat.setName(getString(PropertyInfoIndex.NAME));
    cat.setUid(getString(PropertyInfoIndex.UID));

    cat.setWord(
            (BwString)restoreBwString(PropertyInfoIndex.CATEGORIES, false));
    cat.setDescription(
            (BwString)restoreBwString(PropertyInfoIndex.DESCRIPTION, false));

    return cat;
  }

  BwContact makeContact() throws CalFacadeException {
    BwContact ent = new BwContact();

    restoreSharedEntity(ent);

    ent.setUid(getString(PropertyInfoIndex.UID));

    ent.setCn((BwString)restoreBwString(PropertyInfoIndex.CN,
                                        false));
    ent.setPhone(getString(PropertyInfoIndex.PHONE));
    ent.setEmail(getString(PropertyInfoIndex.EMAIL));
    ent.setLink(getString(PropertyInfoIndex.URL));

    return ent;
  }

  BwLocation makeLocation() throws CalFacadeException {
    BwLocation ent = new BwLocation();

    restoreSharedEntity(ent);

    ent.setUid(getString(PropertyInfoIndex.UID));

    ent.setAddress((BwString)restoreBwString(
            PropertyInfoIndex.ADDRESS, false));
    ent.setSubaddress((BwString)restoreBwString(
            PropertyInfoIndex.SUBADDRESS, false));
    ent.setLink(getString(PropertyInfoIndex.URL));

    return ent;
  }

  BwCalendar makeCollection() throws CalFacadeException {
    BwCalendar col = new BwCalendar();

    restoreSharedEntity(col);

    col.setName(getString(PropertyInfoIndex.NAME));
    col.setPath(getString(PropertyInfoIndex.HREF));

    restoreCategories(col);

    col.setCreated(getString(PropertyInfoIndex.CREATED));
    col.setLastmod(new BwCollectionLastmod(col,
                                           getString(PropertyInfoIndex.LAST_MODIFIED)));
    col.setSummary(getString(PropertyInfoIndex.SUMMARY));
    col.setDescription(getString(PropertyInfoIndex.DESCRIPTION));

    return col;
  }

  EventInfo makeEvent() throws CalFacadeException {
    BwEvent ev = new BwEventObj();
    EventInfo ei = new  EventInfo(ev);

    /*
    Float score = (Float)sd.getFirstValue("score");

    if (score != null) {
      bwkey.setScore(score);
    }
    */

    restoreSharedEntity(ev);

    ev.setName(getString(PropertyInfoIndex.NAME));

    restoreCategories(ev);

    ev.setSummaries((Set<BwString>)restoreBwStringSet(
            PropertyInfoIndex.SUMMARY, false));
    ev.setDescriptions((Set<BwLongString>)restoreBwStringSet(
            PropertyInfoIndex.DESCRIPTION, true));

    ev.setEntityType(makeEntityType(getString(PropertyInfoIndex.ENTITY_TYPE)));

    ev.setClassification(getString(PropertyInfoIndex.CLASS));
    ev.setLink(getString(PropertyInfoIndex.URL));

    ev.setGeo(restoreGeo());

    ev.setStatus(getString(PropertyInfoIndex.STATUS));
    ev.setCost(getString(PropertyInfoIndex.COST));

    ev.setOrganizer(restoreOrganizer());

    ev.setDtstamp(getString(PropertyInfoIndex.DTSTAMP));
    ev.setLastmod(getString(PropertyInfoIndex.LAST_MODIFIED));
    ev.setCreated(getString(PropertyInfoIndex.CREATED));
    ev.setStag(getString(PropertyInfoIndex.SCHEDULE_TAG));
    ev.setPriority(getInteger(PropertyInfoIndex.PRIORITY));

    ev.setSequence(getInt(PropertyInfoIndex.SEQUENCE));

    ev.setLocationUid(getString(PropertyInfoIndex.LOCATION_UID));

    ev.setUid(getString(PropertyInfoIndex.UID));
    ev.setTransparency(getString(PropertyInfoIndex.TRANSP));
    ev.setPercentComplete(getInteger(
            PropertyInfoIndex.PERCENT_COMPLETE));
    ev.setCompleted(getString(PropertyInfoIndex.COMPLETED));
    ev.setScheduleMethod(getInt(PropertyInfoIndex.SCHEDULE_METHOD));
    ev.setOriginator(getString(PropertyInfoIndex.ORIGINATOR));
    ev.setScheduleState(getInt(PropertyInfoIndex.SCHEDULE_STATE));
    ev.setOrganizerSchedulingObject(
            getBoolean(PropertyInfoIndex.ORGANIZER_SCHEDULING_OBJECT));
    ev.setAttendeeSchedulingObject(
            getBoolean(PropertyInfoIndex.ATTENDEE_SCHEDULING_OBJECT));

    ev.setRelatedTo(restoreRelatedTo());

    ev.setXproperties(restoreXprops());
    restoreReqStat(ev);

    ev.setCtoken(getString(PropertyInfoIndex.CTOKEN));
    ev.setRecurring(getBoolean(PropertyInfoIndex.RECURRING));
    ev.setRecurrenceId(getString(PropertyInfoIndex.RECURRENCE_ID));

    ev.setRrules(getStringSet(PropertyInfoIndex.RRULE));
    ev.setExrules(getStringSet(PropertyInfoIndex.EXRULE));

    ev.setRdates(restoreBwDateTimeSet(PropertyInfoIndex.RDATE));
    ev.setExdates(restoreBwDateTimeSet(PropertyInfoIndex.EXDATE));

    ev.setDtstart(unindexDate(PropertyInfoIndex.DTSTART));
    ev.setDtend(unindexDate(PropertyInfoIndex.DTEND));

    ev.setNoStart(Boolean.parseBoolean(getString(PropertyInfoIndex.NO_START)));
    ev.setEndType(getString(PropertyInfoIndex.END_TYPE).charAt(0));
    ev.setDuration(getString(PropertyInfoIndex.DURATION));

    ev.setAlarms(restoreAlarms());
    /* uuu Attachment */

    ev.setAttendees(restoreAttendees());
    ev.setRecipients(getStringSet(PropertyInfoIndex.RECIPIENT));
    ev.setComments((Set<BwString>)restoreBwStringSet(
            PropertyInfoIndex.COMMENT, false));
    ev.setContacts(restoreContacts());
    ev.setResources((Set<BwString>)restoreBwStringSet(
            PropertyInfoIndex.RESOURCES, false));

    return ei;
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private boolean pushFields(PropertyInfoIndex pi) throws CalFacadeException {
    return pushFields(getFirstValue(pi));
  }

  private boolean pushFields(Object objFlds) throws CalFacadeException {
    if (objFlds == null) {
      return false;
    }

    /* Should be a Map of fields. */

    if (!(objFlds instanceof Map)) {
      throw new CalFacadeException(CalFacadeException.illegalObjectClass);
    }

    fieldStack.push((Map<String, Object>)objFlds);
    return true;
  }

  private BwGeo restoreGeo() throws CalFacadeException {
    if (!pushFields(PropertyInfoIndex.GEO)) {
      return null;
    }

    try {
      BwGeo geo = new BwGeo();

      geo.setLatitude(BigDecimal.valueOf(getLong("lat")));
      geo.setLongitude(BigDecimal.valueOf(getLong("lon")));

      return geo;
    } finally {
      fieldStack.pop();
    }
  }

  private BwOrganizer restoreOrganizer() throws CalFacadeException {
    if (!pushFields(PropertyInfoIndex.ORGANIZER)) {
      return null;
    }

    try {
      BwOrganizer org = new BwOrganizer();

      if (pushFields(PropertyInfoIndex.PARAMETERS)) {
        try {
          org.setScheduleStatus(getString(ParameterInfoIndex.SCHEDULE_STATUS));
          org.setCn(getString(ParameterInfoIndex.CN));
          org.setDir(getString(ParameterInfoIndex.DIR));
          org.setLanguage(getString(ParameterInfoIndex.LANGUAGE));
          org.setSentBy(getString(ParameterInfoIndex.SENT_BY));
        } finally {
          fieldStack.pop();
        }
      }

      org.setOrganizerUri(getString(PropertyInfoIndex.URI));

      return org;
    } finally {
      fieldStack.pop();
    }
  }

  private Set<BwAttendee> restoreAttendees() throws CalFacadeException {
    List<Object> vals = getFieldValues(PropertyInfoIndex.ATTENDEE);

    if (Util.isEmpty(vals)) {
      return null;
    }

    Set<BwAttendee> atts = new TreeSet<>();

    for (Object o: vals) {
      try {
        pushFields(o);

        BwAttendee att = new BwAttendee();

        if (pushFields(PropertyInfoIndex.PARAMETERS)) {
          try {
            att.setRsvp(getBool(ParameterInfoIndex.RSVP));
            att.setCn(getString(ParameterInfoIndex.CN));
            att.setPartstat(getString(ParameterInfoIndex.PARTSTAT));
            att.setScheduleStatus(getString(ParameterInfoIndex.SCHEDULE_STATUS));
            att.setCuType(getString(ParameterInfoIndex.CUTYPE));
            att.setDelegatedFrom(getString(ParameterInfoIndex.DELEGATED_FROM));
            att.setDelegatedTo(getString(ParameterInfoIndex.DELEGATED_TO));
            att.setDir(getString(ParameterInfoIndex.DIR));
            att.setLanguage(getString(ParameterInfoIndex.LANGUAGE));
            att.setMember(getString(ParameterInfoIndex.MEMBER));
            att.setRole(getString(ParameterInfoIndex.ROLE));
            att.setSentBy(getString(ParameterInfoIndex.SENT_BY));
          } finally {
            fieldStack.pop();
          }
        }

        att.setAttendeeUri(getString(PropertyInfoIndex.URI));

        atts.add(att);
      } finally {
        fieldStack.pop();
      }
    }

    return atts;
  }

  private BwRelatedTo restoreRelatedTo() throws CalFacadeException {
    if (!pushFields(PropertyInfoIndex.RELATED_TO)) {
      return null;
    }

    try {
      BwRelatedTo rt = new BwRelatedTo();

      rt.setRelType(getString(ParameterInfoIndex.RELTYPE));
      rt.setValue(getString(PropertyInfoIndex.VALUE));

      return rt;
    } finally {
      fieldStack.pop();
    }
  }

  private List<BwXproperty> restoreXprops() throws CalFacadeException {
    /* Convert our special fields back to xprops */
    Set<String> xpnames = DocBuilder.interestingXprops.keySet();

    List<BwXproperty> xprops = new ArrayList<>();

    if (!Util.isEmpty(xpnames)) {
      for (String xpname: xpnames) {
        @SuppressWarnings("unchecked")
        Collection<String> xvals =
                (Collection)getFieldValues(DocBuilder.interestingXprops.get(xpname));

        if (!Util.isEmpty(xvals)) {
          for (String xval: xvals) {
            int pos = xval.indexOf("\t");
            String pars = null;

            if (pos > 0) {
              pars = xval.substring(0, pos);
            }

            BwXproperty xp = new BwXproperty(xpname, pars, xval.substring(pos + 1));
            xprops.add(xp);
          }
        }
      }
    }

    /* Now restore the rest of the xprops */

    List<Object> xpropFields = getFieldValues(PropertyInfoIndex.XPROP);

    if (Util.isEmpty(xpropFields)) {
      return xprops;
    }

    for (Object o: xpropFields) {
      try {
        pushFields(o);

        BwXproperty xp = new BwXproperty();

        xp.setName(getString(PropertyInfoIndex.NAME));
        xp.setPars(getString(PropertyInfoIndex.PARAMETERS));
        xp.setValue(getString(PropertyInfoIndex.VALUE));

        xprops.add(xp);
      } finally {
        fieldStack.pop();
      }
    }

    return xprops;
  }

  private Set<BwContact> restoreContacts() throws CalFacadeException {
    List<Object> cFlds = getFieldValues(PropertyInfoIndex.CONTACT);

    if (Util.isEmpty(cFlds)) {
      return null;
    }

    Set<BwContact> cs = new TreeSet<>();

    for (Object o: cFlds) {
      try {
        pushFields(o);

        BwContact c = new BwContact();

        c.setCn((BwString)restoreBwString(PropertyInfoIndex.CN,
                                          false));
        c.setUid(getString(PropertyInfoIndex.UID));
        c.setLink(getString(ParameterInfoIndex.ALTREP));

        cs.add(c);
      } finally {
        fieldStack.pop();
      }
    }

    return cs;
  }

  private void restoreReqStat(BwEvent ev) throws CalFacadeException {
    Collection<String> vals =
            (Collection)getFieldValues(PropertyInfoIndex.REQUEST_STATUS);
    if (Util.isEmpty(vals)) {
      return;
    }

    for (String s: vals) {
      RequestStatus rs = new RequestStatus(null, s);

      ev.addRequestStatus(BwRequestStatus.fromRequestStatus(rs));
    }
  }

  private BwStringBase restoreBwString(final PropertyInfoIndex pi,
                                       final boolean longString) throws CalFacadeException {
    if (!pushFields(pi)) {
      return null;
    }

    try {
      return restoreBwString(longString);
    } finally {
      fieldStack.pop();
    }
  }

  private Set<? extends BwStringBase> restoreBwStringSet(
          final PropertyInfoIndex pi,
          final boolean longStrings)
          throws CalFacadeException {
    List<Object> vals = getFieldValues(pi);

    if (Util.isEmpty(vals)) {
      return null;
    }

    Set<BwStringBase> ss = new TreeSet<>();

    for (Object o: vals) {
      try {
        pushFields(o);

        ss.add(restoreBwString(longStrings));
      } finally {
        fieldStack.pop();
      }
    }

    return ss;
  }

  private BwStringBase restoreBwString(final boolean longString)
          throws CalFacadeException {
    BwStringBase sb;

    if (longString) {
      sb = new BwLongString();
    } else {
      sb = new BwString();
    }

    sb.setLang(getString(PropertyInfoIndex.LANG));
    sb.setValue(getString(PropertyInfoIndex.VALUE));

    return sb;
  }

  private Set<BwDateTime> restoreBwDateTimeSet(final PropertyInfoIndex pi)
          throws CalFacadeException {
    List<Object> vals = getFieldValues(pi);

    if (Util.isEmpty(vals)) {
      return null;
    }

    Set<BwDateTime> tms = new TreeSet<>();

    for (Object o: vals) {
      try {
        pushFields(o);

        String date = getString(PropertyInfoIndex.LOCAL);
        String utcDate = getString(PropertyInfoIndex.UTC);
        String tzid = getString(PropertyInfoIndex.TZID);
        boolean floating = getBoolean(PropertyInfoIndex.FLOATING);
        boolean dateType = date.length() == 8;

        BwDateTime tm = BwDateTime.makeBwDateTime(dateType,
                                                  date,
                                                  utcDate,
                                                  tzid,
                                                  floating);

        tms.add(tm);
      } finally {
        fieldStack.pop();
      }
    }

    return tms;
  }

  private Set<BwAlarm> restoreAlarms() throws CalFacadeException {
    final List<Object> vals = getFieldValues(PropertyInfoIndex.VALARM);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwAlarm> alarms = new TreeSet<>();

    for (Object o: vals) {
      try {
        pushFields(o);

        final BwAlarm alarm = new BwAlarm();

        alarm.setOwnerHref(getString(PropertyInfoIndex.OWNER));
        alarm.setPublick(getBooleanNotNull(PropertyInfoIndex.PUBLIC));

        final String action = getString(PropertyInfoIndex.ACTION);

        final String actionVal = action.toUpperCase();
        int atype = -1;

        for (int i = 0; i < BwAlarm.alarmTypes.length; i++) {
          if (actionVal.equals(BwAlarm.alarmTypes[i])) {
            atype = i;
            break;
          }
        }

        if (atype < 0) {
          alarm.setAlarmType(BwAlarm.alarmTypeOther);
          alarm.addXproperty(BwXproperty.makeIcalProperty("ACTION",
                                                          null,
                                                          action));
        } else {
          alarm.setAlarmType(atype);
        }
        alarm.setTrigger(getString(PropertyInfoIndex.TRIGGER));

        alarm.setTriggerDateTime(getBool(
                PropertyInfoIndex.TRIGGER_DATE_TIME));

        final String rel = getString(ParameterInfoIndex.RELATED);
        alarm.setTriggerStart(rel == null);

        alarm.setDuration(getString(PropertyInfoIndex.DURATION));
        alarm.setRepeat(getInt(PropertyInfoIndex.REPEAT));


        if (atype == BwAlarm.alarmTypeAudio) {
          alarm.setAttach(getString(PropertyInfoIndex.ATTACH));
        } else if (atype == BwAlarm.alarmTypeDisplay) {
          alarm.setDescription(getString(PropertyInfoIndex.DESCRIPTION));
        } else if (atype == BwAlarm.alarmTypeEmail) {
          alarm.setAttach(getString(PropertyInfoIndex.ATTACH));

          alarm.setDescription(getString(
                  PropertyInfoIndex.DESCRIPTION));
          alarm.setSummary(getString(PropertyInfoIndex.SUMMARY));

          alarm.setAttendees(restoreAttendees());
        } else if (atype == BwAlarm.alarmTypeProcedure) {
          alarm.setAttach(getString(PropertyInfoIndex.ATTACH));
          alarm.setDescription(getString(
                  PropertyInfoIndex.DESCRIPTION));
        } else {
          alarm.setDescription(getString(
                  PropertyInfoIndex.DESCRIPTION));
        }

        alarm.setXproperties(restoreXprops());

        alarms.add(alarm);
      } finally {
        fieldStack.pop();
      }
    }

    return alarms;
  }

  private static Map<String, Integer> entitytypeMap =
          new HashMap<>();

  static {
    entitytypeMap.put("event", IcalDefs.entityTypeEvent);
    entitytypeMap.put("alarm", IcalDefs.entityTypeAlarm);
    entitytypeMap.put("todo", IcalDefs.entityTypeTodo);
    entitytypeMap.put("journal", IcalDefs.entityTypeJournal);
    entitytypeMap.put("freeAndBusy", IcalDefs.entityTypeFreeAndBusy);
    entitytypeMap.put("vavailability", IcalDefs.entityTypeVavailability);
    entitytypeMap.put("available", IcalDefs.entityTypeAvailable);
  }

  private int makeEntityType(final String val) throws CalFacadeException {
    final Integer i = entitytypeMap.get(val);

    if (i == null) {
      return IcalDefs.entityTypeEvent;
    }

    return i;
  }

  private void restoreSharedEntity(BwShareableContainedDbentity ent) throws CalFacadeException {
    ent.setCreatorHref(getString(PropertyInfoIndex.CREATOR));
    ent.setOwnerHref(getString(PropertyInfoIndex.OWNER));
    ent.setColPath(getString(PropertyInfoIndex.COLLECTION));
    ent.setAccess(getString(PropertyInfoIndex.ACL));
    ent.setPublick(getBooleanNotNull(PropertyInfoIndex.PUBLIC));
  }

  private void restoreCategories(final CategorisedEntity ce) throws CalFacadeException {
    Collection<Object> vals = getFieldValues(PropertyInfoIndex.CATEGORIES);
    if (Util.isEmpty(vals)) {
      return;
    }

    Set<String> catUids = new TreeSet<>();

    for (Object o: vals) {
      pushFields(o);
      try {
        String uid = getString(PropertyInfoIndex.UID);
        catUids.add(uid);
      } finally {
        fieldStack.pop();
      }
    }

    ce.setCategoryUids(catUids);
  }

  private List<Object> getFieldValues(final PropertyInfoIndex id) {
    return getFieldValues(getJname(id));
  }

  private List<Object> getFieldValues(final String name) {
    Object val = fieldStack.peek().get(name);

    if (val == null) {
      return null;
    }

    if (val instanceof List) {
      return (List)val;
    }

    List<Object> vals = new ArrayList<>();
    vals.add(val);

    return vals;
  }

  private Set<String> getStringSet(final PropertyInfoIndex pi) {
    List<Object> l = getFieldValues(pi);

    if (Util.isEmpty(l)) {
      return null;
    }

    TreeSet<String> ts = new TreeSet<>();

    for (Object o: l) {
      ts.add((String)o);
    }

    return ts;
  }


  private Object getFirstValue(final PropertyInfoIndex id) {
    return getFirstValue(getJname(id));
  }

  private Object getFirstValue(final ParameterInfoIndex id) {
    return getFirstValue(id.getJname());
  }

  private static String getJname(PropertyInfoIndex pi) {
    BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  private Object getFirstValue(String id) {
    Object val = fieldStack.peek().get(id);

    if (val == null) {
      return null;
    }

    if (!(val instanceof List)) {
      return val;
    }

    List vals = (List)val;
    if (Util.isEmpty(vals)) {
      return null;
    }

    return vals.get(0);
  }

  private Boolean getBoolean(final PropertyInfoIndex id) {
    String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Boolean.valueOf(s);
  }

  private Boolean getBooleanNotNull(final PropertyInfoIndex id) {
    Boolean b = getBoolean(id);

    if (b == null) {
      return Boolean.FALSE;
    }

    return b;
  }

  private boolean getBool(final PropertyInfoIndex id) {
    Boolean b = getBoolean(id);

    if (b == null) {
      return false;
    }

    return b;
  }

  private Boolean getBoolean(final ParameterInfoIndex id) {
    String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Boolean.valueOf(s);
  }

  private boolean getBool(final ParameterInfoIndex id) {
    Boolean b = getBoolean(id);

    if (b == null) {
      return false;
    }

    return b;
  }

  private Integer getInteger(final PropertyInfoIndex id) {
    String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Integer.valueOf(s);
  }

  private Long getLong(final String name) {
    String s = (String)fieldStack.peek().get(name);

    if (s == null) {
      return null;
    }

    return Long.valueOf(s);
  }

  private int getInt(final PropertyInfoIndex id) {
    String s = (String)getFirstValue(id);

    if (s == null) {
      return 0;
    }

    return Integer.parseInt(s);
  }

  private String getString(final PropertyInfoIndex id) {
    return (String)getFirstValue(id);
  }

  private String getString(final ParameterInfoIndex id) {
    return (String)getFirstValue(id);
  }

  private BwDateTime unindexDate(final PropertyInfoIndex pi) throws CalFacadeException {
    String utc;
    String local;
    String tzid;
    boolean floating;

    if (!pushFields(pi)) {
      return null;
    }

    try {
      utc = getString(PropertyInfoIndex.UTC);
      local = getString(PropertyInfoIndex.LOCAL);
      tzid = getString(PropertyInfoIndex.TZID);
      floating = Boolean.parseBoolean(getString(PropertyInfoIndex.FLOATING));
    } finally {
      fieldStack.pop();
    }

    boolean dateType = (local != null) && (local.length() == 8);

    return BwDateTime.makeBwDateTime(dateType, local, utc, tzid,
                                     floating);
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }

  protected void debug(final String msg) {
    getLog().debug(msg);
  }

  protected void warn(final String msg) {
    getLog().warn(msg);
  }

  protected void error(final String msg) {
    getLog().error(msg);
  }

  protected void error(final Throwable t) {
    getLog().error(this, t);
  }
}
