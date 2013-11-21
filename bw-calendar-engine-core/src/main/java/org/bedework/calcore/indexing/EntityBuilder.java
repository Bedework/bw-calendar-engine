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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import org.apache.log4j.Logger;

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

    cat.setWord(new BwString(null,
                             getString(PropertyInfoIndex.CATEGORIES)));
    cat.setDescription(new BwString(null,
                                    getString(PropertyInfoIndex.DESCRIPTION)));

    return cat;
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

    ev.setSummary(getString(PropertyInfoIndex.SUMMARY));
    ev.setDescription(getString(PropertyInfoIndex.DESCRIPTION));

    ev.setEntityType(makeEntityType(getString(PropertyInfoIndex.ENTITY_TYPE)));

    ev.setClassification(getString(PropertyInfoIndex.CLASS));
    ev.setLink(getString(PropertyInfoIndex.URL));
    /* UUU geo */
    ev.setStatus(getString(PropertyInfoIndex.STATUS));
    ev.setCost(getString(PropertyInfoIndex.COST));

    /* UUU organizer */

    ev.setDtstamp(getString(PropertyInfoIndex.DTSTAMP));
    ev.setLastmod(getString(PropertyInfoIndex.LAST_MODIFIED));
    ev.setCreated(getString(PropertyInfoIndex.CREATED));
    ev.setStag(getString(PropertyInfoIndex.SCHEDULE_TAG));
    ev.setPriority(getInteger(PropertyInfoIndex.PRIORITY));

    ev.setSequence(getInt(PropertyInfoIndex.SEQUENCE));

    ev.setLocationUid(getString(PropertyInfoIndex.LOCATION_UID));

    ev.setUid(getString(PropertyInfoIndex.UID));
    ev.setTransparency(getString(PropertyInfoIndex.TRANSP));
    ev.setPercentComplete(getInteger(PropertyInfoIndex.PERCENT_COMPLETE));
    ev.setCompleted(getString(PropertyInfoIndex.COMPLETED));
    ev.setScheduleMethod(getInt(PropertyInfoIndex.SCHEDULE_METHOD));
    ev.setOriginator(getString(PropertyInfoIndex.ORIGINATOR));
    ev.setScheduleState(getInt(PropertyInfoIndex.SCHEDULE_STATE));
    ev.setOrganizerSchedulingObject(
            getBoolean(PropertyInfoIndex.ORGANIZER_SCHEDULING_OBJECT));
    ev.setAttendeeSchedulingObject(
            getBoolean(PropertyInfoIndex.ATTENDEE_SCHEDULING_OBJECT));

    /* UUU related to */

    /* UUU fix these */
    Set<String> xpnames = DocBuilder.interestingXprops.keySet();

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
            ev.addXproperty(xp);
          }
        }
      }
    }

    /* UUU reqstat */

    ev.setCtoken(getString(PropertyInfoIndex.CTOKEN));
    ev.setRecurring(getBoolean(PropertyInfoIndex.RECURRING));
    ev.setRecurrenceId(getString(PropertyInfoIndex.RECURRENCE_ID));

    /* UUU rrule */
    /* UUU exrule */
    /* UUU rdate */
    /* UUU exdate */

    ev.setDtstart(unindexDate(PropertyInfoIndex.DTSTART));
    ev.setDtend(unindexDate(PropertyInfoIndex.DTEND));

    ev.setNoStart(Boolean.parseBoolean(getString(PropertyInfoIndex.START_PRESENT)));
    ev.setEndType(getString(PropertyInfoIndex.END_TYPE).charAt(0));
    ev.setDuration(getString(PropertyInfoIndex.DURATION));

    /* uuu alarms */
    /* uuu recipient */
    /* UUU comment */
    /* UUU contact */
    /* UUU resources */

    return ei;
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private static Map<String, Integer> entitytypeMap =
          new HashMap<String, Integer>();

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
    Integer i = entitytypeMap.get(val);

    if (i == null) {
      return IcalDefs.entityTypeEvent;
    }

    return i;
  }

  private void restoreSharedEntity(BwShareableContainedDbentity ent) throws CalFacadeException {
    ent.setCreatorHref(getString(PropertyInfoIndex.CREATOR));
    ent.setOwnerHref(getString(PropertyInfoIndex.OWNER));
    ent.setColPath(getString(PropertyInfoIndex.COLPATH));
    ent.setAccess(getString(PropertyInfoIndex.ACL));
  }

  private void restoreCategories(final CategorisedEntity ce) throws CalFacadeException {
    Collection<Object> vals = getFieldValues(PropertyInfoIndex.CATUID);
    if (Util.isEmpty(vals)) {
      return;
    }

    Set<String> catUids = new TreeSet<>();

    for (Object o: vals) {
      String uid = (String)o;
      catUids.add(uid);
    }

    ce.setCategoryUids(catUids);
  }

  private List<Object> getFieldValues(final PropertyInfoIndex id) {
    return getFieldValues(id.getJname());
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

  private Object getFirstValue(final PropertyInfoIndex id) {
    Object val = fieldStack.peek().get(id.getJname());

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

  private Integer getInteger(final PropertyInfoIndex id) {
    String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Integer.valueOf(s);
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

  private BwDateTime unindexDate(final PropertyInfoIndex pi) throws CalFacadeException {
    String utc;
    String local;
    String tzid;
    boolean floating;

    Object dto = getFirstValue(pi);
    if (dto == null) {
      return null;
    }

    /* Should be a Map of fields. */

    if (!(dto instanceof Map)) {
      return null;
    }

    try {
      fieldStack.push((Map<String, Object>)dto);

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
