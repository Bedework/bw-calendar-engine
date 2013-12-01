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

import org.bedework.access.Ace;
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
import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexKey;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.icalendar.RecurUtil;
import org.bedework.icalendar.RecurUtil.RecurPeriods;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.misc.Util;

import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.parameter.Related;
import org.apache.log4j.Logger;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.ArrayList;
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

  private BwIndexKey keyConverter = new BwIndexKey();

  private BwPrincipal principal;

  private AuthProperties authpars;
  private AuthProperties unauthpars;
  private BasicSystemProperties basicSysprops;

  static Map<String, String> interestingXprops = new HashMap<>();

  static {
    interestingXprops.put(BwXproperty.bedeworkImage, "imageUrl");
    interestingXprops.put(BwXproperty.bedeworkThumbImage, "thumbImageUrl");
    interestingXprops.put(BwXproperty.bedeworkAlias, "topicalArea");

    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTickets, "eventregMaxTickets");
    interestingXprops.put(BwXproperty.bedeworkEventRegMaxTicketsPerUser, "eventregMaxTicketsPerUser");
    interestingXprops.put(BwXproperty.bedeworkEventRegStart, "eventregStart");
    interestingXprops.put(BwXproperty.bedeworkEventRegEnd, "eventregEnd");
  }

  DocBuilder(final BwPrincipal principal,
             final AuthProperties authpars,
             final AuthProperties unauthpars,
             final BasicSystemProperties basicSysprops) {
    this.principal = principal;
    this.authpars = authpars;
    this.unauthpars = unauthpars;
    this.basicSysprops = basicSysprops;
  }

  /* ===================================================================
   *                   package private methods
   * =================================================================== */

  static class TypeId {
    String type;
    String id;

    TypeId(String type,
            String id) {
      this.type = type;
      this.id = id;
    }
  }

  List<TypeId> makeKeys(final Object rec) throws CalFacadeException {
    try {
      List<TypeId> res = new ArrayList<>();

      if (rec instanceof BwCalendar) {
        BwCalendar col = (BwCalendar)rec;

        res.add(new TypeId(BwIndexer.docTypeCollection, makeKeyVal(col)));

        return res;
      }

      if (rec instanceof BwCategory) {
        BwCategory ent = (BwCategory)rec;

        res.add(new TypeId(BwIndexer.docTypeCategory,
                           makeKeyVal(makeKeyVal(ent))));

        return res;
      }

      if (rec instanceof BwContact) {
        BwContact ent = (BwContact)rec;

        res.add(new TypeId(BwIndexer.docTypeContact,
                           makeKeyVal(makeKeyVal(ent))));

        return res;
      }

      if (rec instanceof BwLocation) {
        BwLocation ent = (BwLocation)rec;

        res.add(new TypeId(BwIndexer.docTypeLocation,
                           makeKeyVal(makeKeyVal(ent))));

        return res;
      }

      if (rec instanceof BwIndexKey) {
        /* Only used for deletion. The only key needed here is the
           path of the entity (+ the recurrence id for an instance)
         */
        BwIndexKey ik = (BwIndexKey)rec;

        res.add(new TypeId(ik.getItemType(), makeKeyVal(ik.getKey())));

        return res;
      }

      if (!(rec instanceof EventInfo)) {
        throw new CalFacadeException(new IndexException(
                IndexException.unknownRecordType,
                rec.getClass().getName()));
      }

      /* If it's not recurring or an override delete it */

      EventInfo ei = (EventInfo)rec;
      BwEvent ev = ei.getEvent();
      String type = IcalDefs.entityTypeNames[ev.getEntityType()];

      if (!ev.getRecurring() || (ev.getRecurrenceId() != null)) {
        res.add(new TypeId(type,
                           makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                     ev.getUid(),
                                                     ev.getRecurrenceId()))));

        return res;
      }

      /* Delete any possible non-recurring version */

      res.add(new TypeId(type,
                         makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                   ev.getUid(),
                                                   null))));

      /* Delete all instances. */

      int maxYears;
      int maxInstances;

      if (ev.getPublick()) {
        maxYears = unauthpars.getMaxYears();
        maxInstances = unauthpars.getMaxInstances();
      } else {
        maxYears = authpars.getMaxYears();
        maxInstances = authpars.getMaxInstances();
      }

      RecurPeriods rp = RecurUtil.getPeriods(ev, maxYears, maxInstances);

      if (rp.instances.isEmpty()) {
        // No instances for an alleged recurring event.
        return res;
        //throw new CalFacadeException(CalFacadeException.noRecurrenceInstances);
      }

      String stzid = ev.getDtstart().getTzid();

      int instanceCt = maxInstances;

      boolean dateOnly = ev.getDtstart().getDateType();

      for (Period p: rp.instances) {
        String dtval = p.getStart().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        BwDateTime rstart = BwDateTime.makeBwDateTime(dateOnly, dtval, stzid);

        String recurrenceId = rstart.getDate();

        dtval = p.getEnd().toString();
        if (dateOnly) {
          dtval = dtval.substring(0, 8);
        }

        res.add(new TypeId(type,
                           makeKeyVal(keyConverter.makeEventKey(ev.getColPath(),
                                                     ev.getUid(),
                                                     recurrenceId))));

        instanceCt--;
        if (instanceCt == 0) {
          // That's all you're getting from me
          break;
        }
      }

      return res;
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  static class DocInfo {
    String type;
    long version;
    String id;

    DocInfo(String type, long version, final String id) {
      this.type = type;
      this.version = version;
      this.id = id;
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final XContentBuilder builder,
                  final BwCategory ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

//      makeField(builder, "value", cat.getWord().getValue());

      setColPath(ent);

      makeShareableContained(builder, ent);

      makeField(builder, PropertyInfoIndex.NAME, ent.getName());
      makeField(builder, PropertyInfoIndex.UID, ent.getUid());

      makeField(builder, PropertyInfoIndex.HREF,
                Util.buildPath(false,
                               ent.getColPath(),
                               ent.getName()));

      makeField(builder, PropertyInfoIndex.CATEGORIES,
                ent.getWord());
      makeField(builder, PropertyInfoIndex.DESCRIPTION,
                ent.getDescription());

      return new DocInfo(BwIndexer.docTypeCategory, 0, makeKeyVal(ent));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final XContentBuilder builder,
                  final BwContact ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

//      makeField(builder, "value", cat.getWord().getValue());

      setColPath(ent);

      makeShareableContained(builder, ent);

      /* Use the uid as the name */
      makeField(builder, PropertyInfoIndex.NAME, ent.getUid());
      makeField(builder, PropertyInfoIndex.UID, ent.getUid());

      makeField(builder, PropertyInfoIndex.HREF,
                Util.buildPath(false,
                               ent.getColPath(),
                               ent.getUid()));

      makeField(builder, PropertyInfoIndex.CN,
                ent.getCn());
      makeField(builder, PropertyInfoIndex.PHONE,
                ent.getPhone());
      makeField(builder, PropertyInfoIndex.EMAIL,
                ent.getEmail());
      makeField(builder, PropertyInfoIndex.URL,
                ent.getLink());

      return new DocInfo(BwIndexer.docTypeContact, 0, makeKeyVal(ent));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final XContentBuilder builder,
                  final BwLocation ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

//      makeField(builder, "value", cat.getWord().getValue());

      setColPath(ent);

      makeShareableContained(builder, ent);

      makeField(builder, PropertyInfoIndex.NAME, ent.getAddress());
      makeField(builder, PropertyInfoIndex.UID, ent.getUid());

      makeField(builder, PropertyInfoIndex.HREF,
                Util.buildPath(false,
                               ent.getColPath(),
                               ent.getUid()));

      makeField(builder, PropertyInfoIndex.ADDRESS,
                ent.getAddress());
      makeField(builder, PropertyInfoIndex.SUBADDRESS,
                ent.getSubaddress());
      makeField(builder, PropertyInfoIndex.URL,
                ent.getLink());

      return new DocInfo(BwIndexer.docTypeLocation, 0, makeKeyVal(ent));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  DocInfo makeDoc(final XContentBuilder builder,
                  final Object rec,
                  final BwDateTime start,
                  final BwDateTime end,
                  final String recurid) throws CalFacadeException {
    try {
      BwCalendar col = null;
      EventInfo ei = null;
      BwEvent ev = null;
      BwShareableContainedDbentity sce;

      String path;
      Collection <BwCategory> cats;

      String name;
      String created;
      String lastmod;
      long version;
      String itemType;

      if (rec instanceof BwCalendar) {
        col = (BwCalendar)rec;
        sce = col;

        itemType = BwIndexer.docTypeCollection;

        name = col.getName();
        path = col.getPath();
        cats = col.getCategories();
        created = col.getCreated();
        lastmod = col.getLastmod().getTimestamp();
        version = col.getMicrosecsVersion();
      } else if (rec instanceof EventInfo) {
        ei = (EventInfo)rec;
        ev = ei.getEvent();
        sce = ev;

        name = ev.getName();
        path = ev.getColPath() + "/" + name;

        itemType = IcalDefs.fromEntityType(ev.getEntityType());

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

        cats = ev.getCategories();
        created = ev.getCreated();
        lastmod = ev.getLastmod();
        version = ev.getMicrosecsVersion();

        if (start == null) {
          warn("No start for " + ev);
          return null;
        }

        if (end == null) {
          warn("No end for " + ev);
          return null;
        }
      } else {
        throw new IndexException(IndexException.unknownRecordType,
                                 rec.getClass().getName());
      }

      /* Start doc and do common collection/event fields */

      makeShareableContained(builder, sce);

      makeField(builder, PropertyInfoIndex.NAME, name);
      makeField(builder, PropertyInfoIndex.HREF, path);

      indexCategories(builder, cats);

      if (col != null) {
        // Doing collection - we're almost done
        makeField(builder, PropertyInfoIndex.SUMMARY, col.getSummary());
        makeField(builder, PropertyInfoIndex.DESCRIPTION,
                  col.getDescription());

        return new DocInfo(itemType, version, makeKeyVal(rec));
      }

      makeField(builder, PropertyInfoIndex.ENTITY_TYPE,
                IcalDefs.entityTypeNames[ev.getEntityType()]);

      indexBwStrings(builder, PropertyInfoIndex.DESCRIPTION,
                     ev.getDescriptions());
      indexBwStrings(builder, PropertyInfoIndex.SUMMARY,
                     ev.getSummaries());
      makeField(builder, PropertyInfoIndex.CLASS, ev.getClassification());
      makeField(builder, PropertyInfoIndex.URL, ev.getLink());
      indexGeo(builder, ev.getGeo());
      makeField(builder, PropertyInfoIndex.STATUS, ev.getStatus());
      makeField(builder, PropertyInfoIndex.COST, ev.getCost());

      indexOrganizer(builder, ev.getOrganizer());

      makeField(builder, PropertyInfoIndex.DTSTAMP, ev.getDtstamp());
      makeField(builder, PropertyInfoIndex.LAST_MODIFIED, lastmod);
      makeField(builder, PropertyInfoIndex.CREATED, created);
      makeField(builder, PropertyInfoIndex.SCHEDULE_TAG, ev.getStag());
      makeField(builder, PropertyInfoIndex.PRIORITY, ev.getPriority());

      if (ev.getSequence() != 0) {
        makeField(builder, PropertyInfoIndex.SEQUENCE, ev.getSequence());
      }

      BwLocation loc = ev.getLocation();
      if (loc != null) {
        makeField(builder, PropertyInfoIndex.LOCATION_UID, loc.getUid());

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
          makeField(builder, PropertyInfoIndex.LOCATION_STR, s);
        }
      }

      makeField(builder, PropertyInfoIndex.UID, ev.getUid());
      makeField(builder, PropertyInfoIndex.TRANSP, ev.getTransparency());
      makeField(builder, PropertyInfoIndex.PERCENT_COMPLETE,
                ev.getPercentComplete());
      makeField(builder, PropertyInfoIndex.COMPLETED, ev.getCompleted());
      makeField(builder, PropertyInfoIndex.SCHEDULE_METHOD,
                ev.getScheduleMethod());
      makeField(builder, PropertyInfoIndex.ORIGINATOR, ev.getOriginator());
      makeField(builder, PropertyInfoIndex.SCHEDULE_STATE,
                ev.getScheduleState());
      makeField(builder, PropertyInfoIndex.ORGANIZER_SCHEDULING_OBJECT,
                ev.getOrganizerSchedulingObject());
      makeField(builder, PropertyInfoIndex.ATTENDEE_SCHEDULING_OBJECT,
                ev.getAttendeeSchedulingObject());
      indexRelatedTo(builder, ev.getRelatedTo());

      indexXprops(builder, ev.getXproperties());

      indexReqStat(builder, ev.getRequestStatuses());
      makeField(builder, PropertyInfoIndex.CTOKEN, ev.getCtoken());
      makeField(builder, PropertyInfoIndex.RECURRING, ev.getRecurring());

      if (recurid != null) {
        makeField(builder, PropertyInfoIndex.RECURRENCE_ID, recurid);
      }

      makeField(builder, PropertyInfoIndex.RRULE, ev.getRrules());
      makeField(builder, PropertyInfoIndex.EXRULE, ev.getExrules());
      makeBwDateTimes(builder, PropertyInfoIndex.RDATE,
                      ev.getRdates());
      makeBwDateTimes(builder, PropertyInfoIndex.EXDATE,
                      ev.getExdates());

      indexDate(builder, PropertyInfoIndex.DTSTART, start);
      indexDate(builder, PropertyInfoIndex.DTEND, end);

      makeField(builder, PropertyInfoIndex.START_PRESENT,
                String.valueOf(ev.getNoStart()));
      makeField(builder, PropertyInfoIndex.END_TYPE,
                String.valueOf(ev.getEndType()));

      makeField(builder, PropertyInfoIndex.DURATION, ev.getDuration());

      indexAlarms(builder, ev.getAlarms());

      /* Attachment */

      if (ev.getNumAttendees() > 0) {
        for (BwAttendee att: ev.getAttendees()) {
          indexAttendee(builder, att);
        }
      }

      makeField(builder, PropertyInfoIndex.RECIPIENT, ev.getRecipients());

      indexBwStrings(builder, PropertyInfoIndex.COMMENT,
                     ev.getComments());
      indexContacts(builder, ev.getContacts());
      indexBwStrings(builder, PropertyInfoIndex.RESOURCES,
                     ev.getResources());

      /* freebusy */
      /* Available */
      /* vpoll */

      return new DocInfo(itemType, version, makeKeyVal(rec));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private void setColPath(final BwCategory cat) throws CalFacadeException {
    if (cat.getColPath() != null) {
      return;
    }

    String extra = cat.getWordVal();
    String name;

    int pos = extra.lastIndexOf("/");

    if (pos < 0) {
      name = extra;
      extra = "";
    } else {
      name = extra.substring(pos + 1);
      extra = extra.substring(0, pos);
    }

    cat.setName(name);
    setColPath(cat, "categories", extra);
  }

  private void setColPath(final BwContact ent) throws CalFacadeException {
    if (ent.getColPath() != null) {
      return;
    }

    setColPath(ent, "contacts", null);
  }

  private void setColPath(final BwLocation ent) throws CalFacadeException {
    if (ent.getColPath() != null) {
      return;
    }

    setColPath(ent, "locations", null);
  }

  private void setColPath(final BwShareableContainedDbentity ent,
                          final String dir,
                          final String namePart) throws CalFacadeException {
    String path;

    if (ent.getPublick()) {
      path = Util.buildPath(true,
                            "/public",
                            "/",
                            basicSysprops.getBedeworkResourceDirectory(),
                            "/",
                            dir,
                            "/",
                            namePart);
    } else {
      String homeDir;

      if (principal.getKind() == Ace.whoTypeUser) {
        homeDir = basicSysprops.getUserCalendarRoot();
      } else {
        homeDir = Util.pathElement(1, principal.getPrincipalRef());
      }

      path = Util.buildPath(true,
                            "/",
                            homeDir,
                            "/",
                            principal.getAccount(),
                            "/",
                            basicSysprops.getBedeworkResourceDirectory(),
                            "/",
                            dir,
                            "/",
                            namePart);
    }

    ent.setColPath(path);
  }

  private void makeShareableContained(final XContentBuilder builder,
                                      final BwShareableContainedDbentity ent)
          throws Throwable {
    makeField(builder, PropertyInfoIndex.CREATOR, ent.getCreatorHref());
    makeField(builder, PropertyInfoIndex.OWNER, ent.getOwnerHref());
    String colPath = ent.getColPath();
    if (colPath == null) {
      colPath = "";
    }

    makeField(builder, PropertyInfoIndex.COLPATH, colPath);
    makeField(builder, PropertyInfoIndex.ACL, ent.getAccess());
  }

  private void indexXprops(final XContentBuilder builder,
                           final List<BwXproperty> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      boolean more = false;

      /* First output ones we know about with our own name */
      for (BwXproperty xp: val) {
        String nm = interestingXprops.get(xp.getName());

        if (nm == null) {
          more = true;
          continue;
        }

        String pars = xp.getPars();
        if (pars == null) {
          pars = "";
        }

        builder.field(nm, pars + "\t" + xp.getValue());
      }

      if (!more) {
        return;
      }

      /* Now ones we don't know or care about */

      builder.startArray(PropertyInfoIndex.XPROP.getJname());

      for (BwXproperty xp: val) {
        String nm = interestingXprops.get(xp.getName());

        if (nm != null) {
          continue;
        }

        builder.startObject();
        makeField(builder, PropertyInfoIndex.NAME, xp.getName());

        if (xp.getPars() != null) {
          builder.field(PropertyInfoIndex.PARAMETERS.getJname(),
                        xp.getPars());
        }

        builder.field(PropertyInfoIndex.VALUE.getJname(),
                      xp.getValue());
        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexContacts(final XContentBuilder builder,
                             final Set<BwContact> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(PropertyInfoIndex.CONTACT.getJname());

      for (BwContact c: val) {
        builder.startObject();
        makeField(builder, PropertyInfoIndex.CN, c.getCn());

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

  private void indexAlarms(final XContentBuilder builder,
                           final Set<BwAlarm> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(PropertyInfoIndex.VALARM.getJname());

      for (BwAlarm al: val) {
        builder.startObject();

        makeField(builder, PropertyInfoIndex.OWNER, al.getOwnerHref());
        makeField(builder, PropertyInfoIndex.PUBLIC, al.getPublick());

        int atype = al.getAlarmType();
        String action;

        if (atype != BwAlarm.alarmTypeOther) {
          action = BwAlarm.alarmTypes[atype];
        } else {
          List<BwXproperty> xps = al.getXicalProperties("ACTION");

          action = xps.get(0).getValue();
        }

        makeField(builder, PropertyInfoIndex.ACTION, action);
        makeField(builder, PropertyInfoIndex.TRIGGER, al.getTrigger());

        if (al.getTriggerDateTime()) {
          makeField(builder, PropertyInfoIndex.TRIGGER_DATE_TIME, true);
        } else if (!al.getTriggerStart()) {
          builder.field(ParameterInfoIndex.RELATED.getJname(),
                        Related.END.getValue());
        }

        if (al.getDuration() != null) {
          makeField(builder, PropertyInfoIndex.DURATION, al.getDuration());
          makeField(builder, PropertyInfoIndex.REPEAT, al.getRepeat());
        }

        if (atype == BwAlarm.alarmTypeAudio) {
          makeField(builder, PropertyInfoIndex.ATTACH, al.getAttach());
        } else if (atype == BwAlarm.alarmTypeDisplay) {
          /* This is required but somehow we got a bunch of alarms with no description
           * Is it possibly because of the rollback issue I (partially) fixed?
           */
          makeField(builder, PropertyInfoIndex.DESCRIPTION, al.getDescription());
        } else if (atype == BwAlarm.alarmTypeEmail) {
          makeField(builder, PropertyInfoIndex.ATTACH, al.getAttach());

          makeField(builder, PropertyInfoIndex.DESCRIPTION, al.getDescription());
          makeField(builder, PropertyInfoIndex.SUMMARY, al.getSummary());

          if (al.getNumAttendees() > 0) {
            for (BwAttendee att: al.getAttendees()) {
              indexAttendee(builder, att);
            }
          }
        } else if (atype == BwAlarm.alarmTypeProcedure) {
          makeField(builder, PropertyInfoIndex.ATTACH, al.getAttach());
          makeField(builder, PropertyInfoIndex.DESCRIPTION, al.getDescription());
        } else {
          makeField(builder, PropertyInfoIndex.DESCRIPTION, al.getDescription());
        }

        indexXprops(builder, al.getXproperties());

        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexReqStat(final XContentBuilder builder,
                            final Set<BwRequestStatus> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(PropertyInfoIndex.REQUEST_STATUS.getJname());

      for (BwRequestStatus rs: val) {
        builder.value(rs.strVal());
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexGeo(final XContentBuilder builder,
                        final BwGeo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(PropertyInfoIndex.GEO.getJname());
      builder.field("lat", val.getLatitude().toPlainString());
      builder.field("lon", val.getLongitude().toPlainString());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexRelatedTo(final XContentBuilder builder,
                              final BwRelatedTo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(PropertyInfoIndex.RELATED_TO.getJname());
      builder.field(ParameterInfoIndex.RELTYPE.getJname(),
                    val.getRelType());
      builder.field(PropertyInfoIndex.VALUE.getJname(), val.getValue());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexOrganizer(final XContentBuilder builder,
                              final BwOrganizer val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(PropertyInfoIndex.ORGANIZER.getJname());
      builder.startObject(PropertyInfoIndex.PARAMETERS.getJname());
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

      builder.field(PropertyInfoIndex.URI.getJname(),
                    val.getOrganizerUri());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexAttendee(final XContentBuilder builder,
                             final BwAttendee val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      builder.startObject(PropertyInfoIndex.ATTENDEE.getJname());
      builder.startObject("pars");

      if (val.getRsvp()) {
        builder.field(ParameterInfoIndex.RSVP.getJname(),
                      val.getRsvp());
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
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexDate(final XContentBuilder builder,
                         final PropertyInfoIndex dtype,
                         final BwDateTime dt) throws CalFacadeException {
    try {
      if (dt == null) {
        return;
      }

      if (dtype == null) {
        builder.startObject();
      } else {
        builder.startObject(dtype.getJname());
      }

      makeField(builder, PropertyInfoIndex.UTC, dt.getDate());
      makeField(builder, PropertyInfoIndex.LOCAL, dt.getDtval());
      makeField(builder, PropertyInfoIndex.TZID, dt.getTzid());
      makeField(builder, PropertyInfoIndex.FLOATING,
                String.valueOf(dt.getFloating()));

      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeId(final XContentBuilder builder,
                      final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field("_id", val);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexBwStrings(final XContentBuilder builder,
                              final PropertyInfoIndex pi,
                              final Set<? extends BwStringBase> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      builder.startArray(pi.getJname());

      for (BwStringBase s: val) {
        makeField(builder, null, s);
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  /** Called to make a key value for a record.
   *
   * @param   rec      The record
   * @return  String   String which uniquely identifies the record
   * @throws IndexException
   */
  private String makeKeyVal(final Object rec) throws IndexException {
    if (rec instanceof BwCalendar) {
      return ((BwCalendar)rec).getPath();
    }

    if (rec instanceof BwCategory) {
      return keyConverter.makeCategoryKey(((BwCategory)rec).getUid());
    }

    if (rec instanceof BwContact) {
      return keyConverter.makeContactKey(((BwContact)rec).getUid());
    }

    if (rec instanceof BwLocation) {
      return keyConverter.makeLocationKey(((BwLocation)rec).getUid());
    }

    BwEvent ev = null;
    if (rec instanceof BwEvent) {
      ev = (BwEvent)rec;
    } else if (rec instanceof EventInfo) {
      ev = ((EventInfo)rec).getEvent();
    }

    if (ev != null) {
      String path = ev.getColPath();
      String guid = ev.getUid();
      String recurid = ev.getRecurrenceId();

      return keyConverter.makeEventKey(path, guid, recurid);
    }

    throw new IndexException(IndexException.unknownRecordType,
                             rec.getClass().getName());
  }

  private void makeField(final XContentBuilder builder,
                         final PropertyInfoIndex pi,
                         final BwStringBase val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      if (pi == null) {
        builder.startObject();
      } else {
        builder.startObject(pi.getJname());
      }
      makeField(builder, PropertyInfoIndex.LANG, val.getLang());
      makeField(builder, PropertyInfoIndex.VALUE, val.getValue());
      builder.endObject();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XContentBuilder builder,
                         final PropertyInfoIndex pi,
                         final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field(pi.getJname(), val);
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XContentBuilder builder,
                         final PropertyInfoIndex pi,
                         final Object val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      builder.field(pi.getJname(), String.valueOf(val));
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final XContentBuilder builder,
                         final PropertyInfoIndex pi,
                         final Set<String> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      builder.startArray(pi.getJname());

      for (String s: vals) {
        builder.value(s);
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeBwDateTimes(final XContentBuilder builder,
                               final PropertyInfoIndex pi,
                               final Set<BwDateTime> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      builder.startArray(pi.getJname());

      for (BwDateTime dt: vals) {
        builder.startObject();
        indexDate(builder, null, dt);
        builder.endObject();
      }

      builder.endArray();
    } catch (IOException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexCategories(final XContentBuilder builder,
                               final Collection <BwCategory> cats) throws CalFacadeException {
    if (cats == null) {
      return;
    }

    for (BwCategory cat: cats) {
      setColPath(cat);
      makeField(builder, PropertyInfoIndex.CATEGORIES, cat.getWord());
      makeField(builder, PropertyInfoIndex.CATUID, cat.getUid());
      makeField(builder, PropertyInfoIndex.CATEGORY_PATH,
                Util.buildPath(false,
                               cat.getColPath(),
                               cat.getName()));
    }
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
