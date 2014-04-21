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
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.FixNamesEntity;
import org.bedework.calfacade.base.XpropsEntity;
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.IndexKeys;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.parameter.Related;
import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Build documents for ElasticSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class DocBuilder {
  private transient Logger log;

  private boolean debug;

  private XContentBuilder builder;

  private BwPrincipal principal;

  private AuthProperties authpars;
  private AuthProperties unauthpars;
  private BasicSystemProperties basicSysprops;

  private IndexKeys keys = new IndexKeys();

  static Map<String, String> interestingXprops = new HashMap<>();

  static {
    interestingXprops.put(BwXproperty.bedeworkImage,
                          getJname(PropertyInfoIndex.IMAGE));
    interestingXprops.put(BwXproperty.bedeworkThumbImage,
                          getJname(PropertyInfoIndex.THUMBIMAGE));
    interestingXprops.put(BwXproperty.bedeworkAlias,
                          getJname(PropertyInfoIndex.TOPICAL_AREA));

    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTickets,
                          getJname(PropertyInfoIndex.EVENTREG_MAX_TICKETS));
    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTicketsPerUser,
                          getJname(PropertyInfoIndex.EVENTREG_MAX_TICKETS_PER_USER));
    interestingXprops.put(BwXproperty.bedeworkEventRegStart,
                          getJname(PropertyInfoIndex.EVENTREG_START));
    interestingXprops.put(BwXproperty.bedeworkEventRegEnd,
                          getJname(PropertyInfoIndex.EVENTREG_END));
  }

  /**
   *
   * @param principal - only used for building fake non-public entity paths
   * @param authpars
   * @param unauthpars
   * @param basicSysprops
   */
  DocBuilder(final BwPrincipal principal,
             final AuthProperties authpars,
             final AuthProperties unauthpars,
             final BasicSystemProperties basicSysprops) throws CalFacadeException {
    debug = getLog().isDebugEnabled();

    this.principal = principal;
    this.authpars = authpars;
    this.unauthpars = unauthpars;
    this.basicSysprops = basicSysprops;

    builder = newBuilder();
  }

  /* ===================================================================
   *                   package private methods
   * =================================================================== */

  private XContentBuilder newBuilder() throws CalFacadeException {
    try {
      XContentBuilder builder = XContentFactory.jsonBuilder();

      if (debug) {
        builder = builder.prettyPrint();
      }

      return builder;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  static class UpdateInfo {
    private String dtstamp;
    private Long count = 0l;

    /* Set this true if we write something to the index */
    private boolean update;

    UpdateInfo() {
    }

    UpdateInfo(final String dtstamp,
               final Long count) {
      this.dtstamp = dtstamp;
      this.count = count;
    }

    /**
     * @return dtstamp last time this object type saved
     */
    public String getDtstamp() {
      return dtstamp;
    }

    /**
     * @return count of updates
     */
    public Long getCount() {
      return count;
    }

    /**
     * @param update true to indicate update occurred
     */
    public void setUpdate(final boolean update) {
      this.update = update;
    }

    /**
     * @return true to indicate update occurred
     */
    public boolean isUpdate() {
      return update;
    }

    /**
     * @return a change token for the index.
     */
    public String getChangeToken() {
      return dtstamp + ";" + count;
    }
  }

  static class DocInfo {
    XContentBuilder source;
    String type;
    long version;
    String id;

    DocInfo(final XContentBuilder source,
            final String type,
            final long version,
            final String id) {
      this.source = source;
      this.type = type;
      this.version = version;
      this.id = id;
    }

    public String toString() {
      ToString ts = new ToString(this);

      ts.append("type", type);
      ts.append("version", version);
      ts.append("id", id);

      return ts.toString();
    }
  }

  String getHref(final BwShareableContainedDbentity val) throws CalFacadeException {
    if (val instanceof BwEvent) {
      return ((BwEvent)val).getHref();
    }

    if (val instanceof BwCalendar) {
      return ((BwCalendar)val).getPath();
    }

    if (val instanceof FixNamesEntity) {
      FixNamesEntity ent = (FixNamesEntity)val;

      ent.fixNames(basicSysprops, principal);
      return ent.getHref();
    }

    throw new CalFacadeException("Unhandled class " + val);
  }

  private void startObject() throws CalFacadeException {
    try {
      builder.startObject();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void endObject() throws CalFacadeException {
    try {
      builder.endObject();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final UpdateInfo ent) throws CalFacadeException {
    try {
      startObject();

      builder.field("count", ent.getCount());

      endObject();

      return new DocInfo(builder,
                         BwIndexer.docTypeUpdateTracker, 0,
                         BwIndexer.updateTrackerId);
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final BwCategory ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      ent.fixNames(basicSysprops, principal);

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      makeField(PropertyInfoIndex.UID, ent.getUid());

      makeField(PropertyInfoIndex.HREF, ent.getHref());

      makeField(PropertyInfoIndex.CATEGORIES, ent.getWord());
      makeField(PropertyInfoIndex.DESCRIPTION, ent.getDescription());

      endObject();

      return new DocInfo(builder,
                         BwIndexer.docTypeCategory, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final BwContact ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      ent.fixNames(basicSysprops, principal);

      startObject();

      makeShareableContained(ent);

      /* Use the uid as the name */
      makeField(PropertyInfoIndex.NAME, ent.getUid());
      makeField(PropertyInfoIndex.UID, ent.getUid());

      makeField(PropertyInfoIndex.HREF, ent.getHref());

      makeField(PropertyInfoIndex.CN, ent.getCn());
      makeField(PropertyInfoIndex.PHONE, ent.getPhone());
      makeField(PropertyInfoIndex.EMAIL, ent.getEmail());
      makeField(PropertyInfoIndex.URL, ent.getLink());

      endObject();

      return new DocInfo(builder,
                         BwIndexer.docTypeContact, 0, ent.getHref());
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final BwLocation ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      ent.fixNames(basicSysprops, principal);

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getAddress());
      makeField(PropertyInfoIndex.UID, ent.getUid());

      makeField(PropertyInfoIndex.HREF, ent.getHref());

      makeField(PropertyInfoIndex.ADDRESS, ent.getAddress());
      makeField(PropertyInfoIndex.SUBADDRESS, ent.getSubaddress());
      makeField(PropertyInfoIndex.URL, ent.getLink());

      endObject();

      return new DocInfo(builder,
                         BwIndexer.docTypeLocation, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final BwCalendar col) throws CalFacadeException {
    try {
      final long version = col.getMicrosecsVersion();

      startObject();

      makeShareableContained(col);

      makeField(PropertyInfoIndex.LAST_MODIFIED,
                col.getLastmod().getTimestamp());
      makeField(PropertyInfoIndex.CREATED,
                col.getCreated());
      //makeField(PropertyInfoIndex.VERSION, version);

      makeField(PropertyInfoIndex.NAME, col.getName());
      makeField(PropertyInfoIndex.HREF, col.getPath());

      indexCategories(col.getCategories());

      // Doing collection - we're almost done
      makeField(PropertyInfoIndex.SUMMARY, col.getSummary());
      makeField(PropertyInfoIndex.DESCRIPTION,
                col.getDescription());

      endObject();

      return new DocInfo(builder,
                         BwIndexer.docTypeCollection,
                         version, col.getPath());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  enum ItemKind {
    master,
    override,
    entity
  }

  static String getItemType(final EventInfo ei,
                            final ItemKind kind) throws CalFacadeException {
    final BwEvent ev = ei.getEvent();

    if (kind == ItemKind.entity) {
      return IcalDefs.fromEntityType(ev.getEntityType());
    }

    if (kind == ItemKind.master) {
      return BwIndexer.masterDocTypes[ev.getEntityType()];
    }

    return BwIndexer.overrideDocTypes[ev.getEntityType()];
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final EventInfo ei,
                  final ItemKind kind,
                  final BwDateTime start,
                  final BwDateTime end,
                  final String recurid) throws CalFacadeException {
    try {
      final BwEvent ev = ei.getEvent();
      final long version = ev.getMicrosecsVersion();

      /*
        if (ev instanceof BwEventProxy) {
          // Index with the master key
          rec.addField(BwIndexLuceneDefs.keyEventMaster,
                       makeKeyVal(((BwEventProxy)ev).getTarget()));
        } else {
          rec.addField(BwIndexLuceneDefs.keyEventMaster,
                       makeKeyVal(ev));
        }
        */

      if (start == null) {
        warn("No start for " + ev);
        return null;
      }

      if (end == null) {
        warn("No end for " + ev);
        return null;
      }

      /* Start doc and do common collection/event fields */

      startObject();

      makeShareableContained(ev);

      makeField(PropertyInfoIndex.NAME, ev.getName());
      makeField(PropertyInfoIndex.HREF, ev.getHref());

      indexCategories(ev.getCategories());

      makeField(PropertyInfoIndex.ENTITY_TYPE,
                IcalDefs.entityTypeNames[ev.getEntityType()]);

      indexBwStrings(PropertyInfoIndex.DESCRIPTION,
                     ev.getDescriptions());
      indexBwStrings(PropertyInfoIndex.SUMMARY,
                     ev.getSummaries());
      makeField(PropertyInfoIndex.CLASS, ev.getClassification());
      makeField(PropertyInfoIndex.URL, ev.getLink());
      indexGeo(ev.getGeo());
      makeField(PropertyInfoIndex.STATUS, ev.getStatus());
      makeField(PropertyInfoIndex.COST, ev.getCost());

      indexOrganizer(ev.getOrganizer());

      makeField(PropertyInfoIndex.DTSTAMP, ev.getDtstamp());
      makeField(PropertyInfoIndex.LAST_MODIFIED, ev.getLastmod());
      makeField(PropertyInfoIndex.CREATED, ev.getCreated());
      makeField(PropertyInfoIndex.SCHEDULE_TAG, ev.getStag());
      makeField(PropertyInfoIndex.PRIORITY, ev.getPriority());

      if (ev.getSequence() != 0) {
        makeField(PropertyInfoIndex.SEQUENCE, ev.getSequence());
      }

      final BwLocation loc = ev.getLocation();
      if (loc != null) {
        loc.fixNames(basicSysprops, principal);

        makeField(PropertyInfoIndex.LOCATION_UID, loc.getUid());

        String s = null;

        if (loc.getAddress() != null) {
          s = loc.getAddress().getValue();
        }

        if (loc.getSubaddress() != null) {
          if (s == null) {
            s = loc.getSubaddress().getValue();
          } else {
            s = s + " " + loc.getSubaddress().getValue();
          }
        }

        if (s != null) {
          makeField(PropertyInfoIndex.LOCATION_STR, s);
        }
      }

      makeField(PropertyInfoIndex.UID, ev.getUid());
      makeField(PropertyInfoIndex.TRANSP, ev.getTransparency());
      makeField(PropertyInfoIndex.PERCENT_COMPLETE,
                ev.getPercentComplete());
      makeField(PropertyInfoIndex.COMPLETED, ev.getCompleted());
      makeField(PropertyInfoIndex.SCHEDULE_METHOD,
                ev.getScheduleMethod());
      makeField(PropertyInfoIndex.ORIGINATOR, ev.getOriginator());
      makeField(PropertyInfoIndex.SCHEDULE_STATE,
                ev.getScheduleState());
      makeField(PropertyInfoIndex.ORGANIZER_SCHEDULING_OBJECT,
                ev.getOrganizerSchedulingObject());
      makeField(PropertyInfoIndex.ATTENDEE_SCHEDULING_OBJECT,
                ev.getAttendeeSchedulingObject());
      indexRelatedTo(ev.getRelatedTo());

      indexXprops(ev);

      indexReqStat(ev.getRequestStatuses());
      makeField(PropertyInfoIndex.CTOKEN, ev.getCtoken());
      makeField(PropertyInfoIndex.RECURRING, ev.getRecurring());

      if (recurid != null) {
        makeField(PropertyInfoIndex.RECURRENCE_ID, recurid);
      }

      makeField(PropertyInfoIndex.RRULE, ev.getRrules());
      makeField(PropertyInfoIndex.EXRULE, ev.getExrules());
      makeBwDateTimes(PropertyInfoIndex.RDATE,
                      ev.getRdates());
      makeBwDateTimes(PropertyInfoIndex.EXDATE,
                      ev.getExdates());

      if (kind == ItemKind.entity) {
        indexDate(PropertyInfoIndex.DTSTART, start);
        indexDate(PropertyInfoIndex.DTEND, end);

        indexDate(PropertyInfoIndex.INDEX_START, start);
        indexDate(PropertyInfoIndex.INDEX_END, end);
        makeField(PropertyInfoIndex.INSTANCE, true);
      } else {
        if (kind == ItemKind.override) {
          makeField(PropertyInfoIndex.OVERRIDE, true);
        } else {
          makeField(PropertyInfoIndex.MASTER, true);
        }

        indexDate(PropertyInfoIndex.DTSTART, ev.getDtstart());
        indexDate(PropertyInfoIndex.DTEND, ev.getDtend());

        indexDate(PropertyInfoIndex.INDEX_START, start);
        indexDate(PropertyInfoIndex.INDEX_END, end);
      }

      makeField(PropertyInfoIndex.NO_START,
                String.valueOf(ev.getNoStart()));
      makeField(PropertyInfoIndex.END_TYPE,
                String.valueOf(ev.getEndType()));

      makeField(PropertyInfoIndex.DURATION, ev.getDuration());

      indexAlarms(ev.getAlarms());

      /* Attachment */

      final boolean vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

      if (ev.getNumAttendees() > 0) {
        for (final BwAttendee att: ev.getAttendees()) {
          indexAttendee(att, vpoll);
        }
      }

      makeField(PropertyInfoIndex.RECIPIENT, ev.getRecipients());

      indexBwStrings(PropertyInfoIndex.COMMENT,
                     ev.getComments());
      indexContacts(ev.getContacts());
      indexBwStrings(PropertyInfoIndex.RESOURCES,
                     ev.getResources());

      /* freebusy */
      /* Available */
      /* vpoll */

      final String docType;

      if (vpoll) {
        docType = BwIndexer.docTypePoll;

        if (!Util.isEmpty(ev.getPollItems())) {
          makeField(PropertyInfoIndex.POLL_ITEM, ev.getPollItems());
        }

        makeField(PropertyInfoIndex.POLL_MODE, ev.getPollMode());
        makeField(PropertyInfoIndex.POLL_PROPERTIES, ev.getPollProperties());
      } else {
        docType = BwIndexer.docTypeEvent;
      }

      endObject();

      return new DocInfo(builder,
                         docType,
                         version,
                         keys.makeKeyVal(getItemType(ei, kind),
                                         ei.getEvent().getHref(),
                                         recurid));
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private void makeShareableContained(final BwShareableContainedDbentity ent)
          throws Throwable {
    makeField(PropertyInfoIndex.CREATOR, ent.getCreatorHref());
    makeField(PropertyInfoIndex.OWNER, ent.getOwnerHref());
    String colPath = ent.getColPath();
    if (colPath == null) {
      colPath = "";
    }

    makeField(PropertyInfoIndex.COLLECTION, colPath);
    makeField(PropertyInfoIndex.ACL, ent.getAccess());
    makeField(PropertyInfoIndex.PUBLIC, ent.getPublick());
  }

  private void indexXprops(final XpropsEntity ent) throws CalFacadeException {
    try {
      if (Util.isEmpty(ent.getXproperties())) {
        return;
      }

      /* First output ones we know about with our own name */
      for (String nm: interestingXprops.keySet()) {
        List<BwXproperty> props = ent.getXproperties(nm);

        if (Util.isEmpty(props)) {
          continue;
        }

        builder.startArray(interestingXprops.get(nm));

        for (final BwXproperty xp: props) {
          String pars = xp.getPars();
          if (pars == null) {
            pars = "";
          }

          builder.value(pars + "\t" + xp.getValue());
        }

        builder.endArray();
      }

      /* Now ones we don't know or care about */

      builder.startArray(getJname(PropertyInfoIndex.XPROP));

      for (final BwXproperty xp: ent.getXproperties()) {
        String nm = interestingXprops.get(xp.getName());

        if (nm != null) {
          continue;
        }

        builder.startObject();
        makeField(PropertyInfoIndex.NAME, xp.getName());

        if (xp.getPars() != null) {
          builder.field(getJname(PropertyInfoIndex.PARAMETERS),
                        xp.getPars());
        }

        builder.field(getJname(PropertyInfoIndex.VALUE),
                      xp.getValue());
        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexCategories(final Collection <BwCategory> cats) throws CalFacadeException {
    if (cats == null) {
      return;
    }

    try {
      builder.startArray(getJname(PropertyInfoIndex.CATEGORIES));

      for (BwCategory cat: cats) {
        builder.startObject();

        cat.fixNames(basicSysprops, principal);

        makeField(PropertyInfoIndex.UID, cat.getUid());
        makeField(PropertyInfoIndex.HREF, cat.getHref());
        builder.startArray(getJname(PropertyInfoIndex.VALUE));
        // Eventually may be more of these
        makeField(null, cat.getWord());
        builder.endArray();
        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexContacts(final Set<BwContact> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(getJname(PropertyInfoIndex.CONTACT));

      for (BwContact c: val) {
        c.fixNames(basicSysprops, principal);

        builder.startObject();
        makeField(PropertyInfoIndex.HREF, c.getHref());
        makeField(PropertyInfoIndex.CN, c.getCn());

        if (c.getUid() != null) {
          builder.field(ParameterInfoIndex.UID.getJname(),
                        c.getUid());
        }

        if (c.getLink() != null) {
          builder.field(ParameterInfoIndex.ALTREP.getJname(),
                        c.getLink());
        }

        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexAlarms(final Set<BwAlarm> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(getJname(PropertyInfoIndex.VALARM));

      for (BwAlarm al: val) {
        builder.startObject();

        makeField(PropertyInfoIndex.OWNER, al.getOwnerHref());
        makeField(PropertyInfoIndex.PUBLIC, al.getPublick());

        int atype = al.getAlarmType();
        String action;

        if (atype != BwAlarm.alarmTypeOther) {
          action = BwAlarm.alarmTypes[atype];
        } else {
          List<BwXproperty> xps = al.getXicalProperties("ACTION");

          action = xps.get(0).getValue();
        }

        makeField(PropertyInfoIndex.ACTION, action);
        makeField(PropertyInfoIndex.TRIGGER, al.getTrigger());

        if (al.getTriggerDateTime()) {
          makeField(PropertyInfoIndex.TRIGGER_DATE_TIME, true);
        } else if (!al.getTriggerStart()) {
          builder.field(ParameterInfoIndex.RELATED.getJname(),
                        Related.END.getValue());
        }

        if (al.getDuration() != null) {
          makeField(PropertyInfoIndex.DURATION, al.getDuration());
          makeField(PropertyInfoIndex.REPEAT, al.getRepeat());
        }

        if (atype == BwAlarm.alarmTypeAudio) {
          makeField(PropertyInfoIndex.ATTACH, al.getAttach());
        } else if (atype == BwAlarm.alarmTypeDisplay) {
          /* This is required but somehow we got a bunch of alarms with no description
           * Is it possibly because of the rollback issue I (partially) fixed?
           */
          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
        } else if (atype == BwAlarm.alarmTypeEmail) {
          makeField(PropertyInfoIndex.ATTACH, al.getAttach());

          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
          makeField(PropertyInfoIndex.SUMMARY, al.getSummary());

          if (al.getNumAttendees() > 0) {
            for (BwAttendee att: al.getAttendees()) {
              indexAttendee(att, false);
            }
          }
        } else if (atype == BwAlarm.alarmTypeProcedure) {
          makeField(PropertyInfoIndex.ATTACH, al.getAttach());
          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
        } else {
          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
        }

        indexXprops(al);

        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexReqStat(final Set<BwRequestStatus> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(getJname(PropertyInfoIndex.REQUEST_STATUS));

      for (BwRequestStatus rs: val) {
        builder.value(rs.strVal());
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexGeo(final BwGeo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(getJname(PropertyInfoIndex.GEO));
      builder.field("lat", val.getLatitude().toPlainString());
      builder.field("lon", val.getLongitude().toPlainString());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexRelatedTo(final BwRelatedTo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(getJname(PropertyInfoIndex.RELATED_TO));
      builder.field(ParameterInfoIndex.RELTYPE.getJname(),
                    val.getRelType());
      builder.field(getJname(PropertyInfoIndex.VALUE), val.getValue());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexOrganizer(final BwOrganizer val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(getJname(PropertyInfoIndex.ORGANIZER));
      builder.startObject(getJname(PropertyInfoIndex.PARAMETERS));
      String temp = val.getScheduleStatus();
      if (temp != null) {
        builder.field(ParameterInfoIndex.SCHEDULE_STATUS.getJname(),
                      temp);
      }

      temp = val.getCn();
      if (temp != null) {
        builder.field(ParameterInfoIndex.CN.getJname(), temp);
      }

      temp = val.getDir();
      if (temp != null) {
        builder.field(ParameterInfoIndex.DIR.getJname(), temp);
      }

      temp = val.getLanguage();
      if (temp != null) {
        builder.field(ParameterInfoIndex.LANGUAGE.getJname(), temp);
      }

      temp = val.getSentBy();
      if (temp != null) {
        builder.field(ParameterInfoIndex.SENT_BY.getJname(), temp);
      }

      builder.endObject();

      builder.field(getJname(PropertyInfoIndex.URI),
                    val.getOrganizerUri());
      builder.endObject();
    } catch (final IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexAttendee(final BwAttendee val,
                             final boolean vpoll) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      if (vpoll) {
        builder.startObject(getJname(PropertyInfoIndex.VOTER));
      } else {
        builder.startObject(getJname(PropertyInfoIndex.ATTENDEE));
      }
      builder.startObject("pars");

      if (val.getRsvp()) {
        builder.field(ParameterInfoIndex.RSVP.getJname(),
                      val.getRsvp());
      }

      if (vpoll && val.getStayInformed()) {
        builder.field(ParameterInfoIndex.STAY_INFORMED.getJname(),
                      val.getStayInformed());
      }

      String temp = val.getCn();
      if (temp != null) {
        builder.field(ParameterInfoIndex.CN.getJname(), temp);
      }

      temp = val.getPartstat();
      if (temp == null) {
        temp = IcalDefs.partstatValNeedsAction;
      }
      builder.field(ParameterInfoIndex.PARTSTAT.getJname(), temp);

      temp = val.getScheduleStatus();
      if (temp != null) {
        builder.field(ParameterInfoIndex.SCHEDULE_STATUS.getJname(),
                      temp);
      }

      temp = val.getCuType();
      if (temp != null) {
        builder.field(ParameterInfoIndex.CUTYPE.getJname(), temp);
      }

      temp = val.getDelegatedFrom();
      if (temp != null) {
        builder.field(ParameterInfoIndex.DELEGATED_FROM.getJname(),
                      temp);
      }

      temp = val.getDelegatedTo();
      if (temp != null) {
        builder.field(ParameterInfoIndex.DELEGATED_TO.getJname(),
                      temp);
      }

      temp = val.getDir();
      if (temp != null) {
        builder.field(ParameterInfoIndex.DIR.getJname(), temp);
      }

      temp = val.getLanguage();
      if (temp != null) {
        builder.field(ParameterInfoIndex.LANGUAGE.getJname(), temp);
      }

      temp = val.getMember();
      if (temp != null) {
        builder.field(ParameterInfoIndex.MEMBER.getJname(), temp);
      }

      temp = val.getRole();
      if (temp != null) {
        builder.field(ParameterInfoIndex.ROLE.getJname(), temp);
      }

      temp = val.getSentBy();
      if (temp != null) {
        builder.field(ParameterInfoIndex.SENT_BY.getJname(), temp);
      }

      builder.endObject();

      builder.field("uri", val.getAttendeeUri());
      builder.endObject();
    } catch (final IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexDate(final PropertyInfoIndex dtype,
                         final BwDateTime dt) throws CalFacadeException {
    try {
      if (dt == null) {
        return;
      }

      if (dtype == null) {
        builder.startObject();
      } else {
        builder.startObject(getJname(dtype));
      }

      makeField(PropertyInfoIndex.UTC, dt.getDate());
      makeField(PropertyInfoIndex.LOCAL, dt.getDtval());
      makeField(PropertyInfoIndex.TZID, dt.getTzid());
      makeField(PropertyInfoIndex.FLOATING,
                String.valueOf(dt.getFloating()));

      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexBwStrings(final PropertyInfoIndex pi,
                              final Set<? extends BwStringBase> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(getJname(pi));

      for (final BwStringBase s: val) {
        makeField(null, s);
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final BwStringBase val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      if (pi == null) {
        builder.startObject();
      } else {
        builder.startObject(getJname(pi));
      }
      makeField(PropertyInfoIndex.LANG, val.getLang());
      makeField(PropertyInfoIndex.VALUE, val.getValue());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field(getJname(pi), val);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Object val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field(getJname(pi), String.valueOf(val));
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Set<String> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      builder.startArray(getJname(pi));

      for (String s: vals) {
        builder.value(s);
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeBwDateTimes(final PropertyInfoIndex pi,
                               final Set<BwDateTime> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      builder.startArray(getJname(pi));

      for (BwDateTime dt: vals) {
        indexDate(null, dt);
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private static String getJname(PropertyInfoIndex pi) {
    final BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  /*
  private XContentBuilder newBuilder() throws CalFacadeException {
    try {
      XContentBuilder builder = XContentFactory.jsonBuilder();

      if (debug) {
        builder = builder.prettyPrint();
      }

      return builder;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }
  */

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
