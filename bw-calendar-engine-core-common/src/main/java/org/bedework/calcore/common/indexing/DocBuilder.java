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
package org.bedework.calcore.common.indexing;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.FixNamesEntity;
import org.bedework.calfacade.base.XpropsEntity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.IndexKeys;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuitePrincipal;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.elasticsearch.DocBuilderBase;
import org.bedework.util.elasticsearch.EsDocInfo;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.misc.Util;
import org.bedework.util.timezones.DateTimeUtil;

import net.fortuna.ical4j.model.parameter.Related;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Build documents for ElasticSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class DocBuilder extends DocBuilderBase {
  private final BwPrincipal principal;

  // Only used for fixNames calls
  private final BasicSystemProperties basicSysprops;

  private final IndexKeys keys = new IndexKeys();

  static Map<String, String> interestingXprops = new HashMap<>();

  static {
    interestingXprops.put(BwXproperty.bedeworkTag,
                          getJname(PropertyInfoIndex.TAG));
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
    interestingXprops.put(BwXproperty.bedeworkEventRegWaitListLimit,
                          getJname(PropertyInfoIndex.EVENTREG_WAIT_LIST_LIMIT));

    interestingXprops.put(BwXproperty.bedeworkSuggestedTo,
                          getJname(PropertyInfoIndex.SUGGESTED_TO));
  }

  /**
   *
   * @param principal - only used for building fake non-public entity paths
   * @param basicSysprops -  Only used for fixNames calls
   */
  DocBuilder(final BwPrincipal principal,
             final BasicSystemProperties basicSysprops)
          throws CalFacadeException, IndexException {
    super();
    this.principal = principal;
    this.basicSysprops = basicSysprops;
  }

  /* ===================================================================
   *                   package private methods
   * =================================================================== */

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwPrincipal ent) throws CalFacadeException {
    try {
      startObject();
      makeHref(ent);

      makeField("account", ent.getAccount());
      makeTimestampField(getJname(PropertyInfoIndex.CREATED),
                         ent.getCreated());
      makeTimestampField("lastAccess", ent.getLastAccess());
      makeField(PropertyInfoIndex.DESCRIPTION, ent.getDescription());
      makeField("quota", ent.getQuota());
      makeField("categoryAccess", ent.getCategoryAccess());
      makeField("contactAccess", ent.getContactAccess());
      makeField("locationAccess", ent.getLocationAccess());

      if (ent instanceof BwGroup) {
        final BwGroup grp = (BwGroup)ent;

        if (!Util.isEmpty(grp.getGroupMembers())) {
          startArray("memberHref");

          for (final BwPrincipal mbr: grp.getGroupMembers()) {
            value(mbr.getPrincipalRef());
          }
          endArray();
        }
      }

      if (ent instanceof BwAdminGroup) {
        final BwAdminGroup grp = (BwAdminGroup)ent;

        makeField("groupOwnerHref", grp.getGroupOwnerHref());
        makeField("ownerHref", grp.getOwnerHref());
      }

      if (ent instanceof BwCalSuitePrincipal) {
        final BwCalSuitePrincipal cs = (BwCalSuitePrincipal)ent;

        makeField("rootCollectionPath", cs.getRootCollectionPath());
        makeField("submissionsRootPath", cs.getSubmissionsRootPath());
        makeField("groupHref", cs.getGroup().getHref());
      }

      endObject();

      return makeDocInfo(BwIndexer.docTypePrincipal, 0,
                         getHref(ent));
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private void makeTimestampField(
          @SuppressWarnings("SameParameterValue") final String name,
          final Timestamp val) throws IndexException {
    if (val == null) {
      return;
    }

    final String dt = val.toString();

    // in: 2014-03-05 09:35:51.0
    //     0    5  8  1  4  7
    //                1  1  1
    makeField(name,
              String.format("%s%s%sT%s%s%sZ", dt.substring(0, 4),
                            dt.substring(5, 7), dt.substring(8, 10),
                            dt.substring(11, 13), dt.substring(14, 16),
                            dt.substring(17, 19)));
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwPreferences ent) throws CalFacadeException {
    try {
      startObject();
      makeHref(ent);

      indexViews(ent.getViews());

      makeField("email", ent.getEmail());
      makeField("defaultCalendarPath", ent.getDefaultCalendarPath());
      makeField("skinName", ent.getSkinName());
      makeField("skinStyle", ent.getSkinStyle());
      makeField("preferredView", ent.getPreferredView());
      makeField("preferredViewPeriod", ent.getPreferredViewPeriod());
      makeField("pageSize", ent.getPageSize());
      makeField("workDays", ent.getWorkDays());
      makeField("workDayStart", ent.getWorkdayStart());
      makeField("workDayEnd", ent.getWorkdayEnd());
      makeField("preferredEndType", ent.getPreferredEndType());
      makeField("userMode", ent.getUserMode());
      makeField("hour24", ent.getHour24());
      makeField("scheduleAutoRespond", ent.getScheduleAutoRespond());
      makeField("scheduleAutoCancelAction", ent.getScheduleAutoCancelAction());
      makeField("scheduleDoubleBook", ent.getScheduleDoubleBook());
      makeField("scheduleAutoProcessResponses", ent.getScheduleAutoProcessResponses());

      indexProperties("userProperties",
                      ent.getProperties());

      endObject();

      return makeDocInfo(BwIndexer.docTypePreferences, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwResource ent) throws CalFacadeException {
    try {
      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());

      makeField("tombstoned", ent.getTombstoned());
      makeField(PropertyInfoIndex.SEQUENCE, ent.getSequence());
      makeField("contentType", ent.getContentType());
      makeField("encoding", ent.getEncoding());
      makeField("contentlength", ent.getContentLength());

      endObject();

      return makeDocInfo(BwIndexer.docTypeResource, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwResourceContent ent) throws CalFacadeException {
    try {
      startObject();
      makeHref(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      makeField(PropertyInfoIndex.COLLECTION, ent.getColPath());

      makeField("content", ent.getEncodedContent());

      return makeDocInfo(BwIndexer.docTypeResourceContent, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwCategory ent) throws CalFacadeException {
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

      makeField(PropertyInfoIndex.CATEGORIES, ent.getWord());
      makeField(PropertyInfoIndex.DESCRIPTION, ent.getDescription());

      endObject();

      return makeDocInfo(BwIndexer.docTypeCategory, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwContact ent) throws CalFacadeException {
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

      if (ent.getCn() != null) {
        makeField(PropertyInfoIndex.CN, ent.getCn().getValue());
      }
      makeField(PropertyInfoIndex.PHONE, ent.getPhone());
      makeField(PropertyInfoIndex.EMAIL, ent.getEmail());
      makeField(PropertyInfoIndex.URL, ent.getLink());

      endObject();

      return makeDocInfo(BwIndexer.docTypeContact, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwLocation ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      ent.fixNames(basicSysprops, principal);

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getAddressField());
      makeField(PropertyInfoIndex.UID, ent.getUid());

      // These 2 fields are composite fields
      makeField(PropertyInfoIndex.ADDRESS, ent.getAddress());
      makeField(PropertyInfoIndex.SUBADDRESS, ent.getSubaddress());

      // These fields are part of the address field
      makeField(PropertyInfoIndex.ADDRESS_FLD, ent.getAddressField());
      makeField(PropertyInfoIndex.ROOM_FLD, ent.getRoomField());
      makeField(PropertyInfoIndex.SUB1_FLD, ent.getSubField1());
      makeField(PropertyInfoIndex.SUB2_FLD, ent.getSubField2());
      makeField(PropertyInfoIndex.ACCESSIBLE_FLD, ent.getAccessible());
      makeField(PropertyInfoIndex.GEOURI_FLD, ent.getGeouri());

      makeField(PropertyInfoIndex.STATUS, ent.getStatus());
      makeField(PropertyInfoIndex.DELETED,
                BwEventProperty.statusDeleted.equals(ent.getStatus()));

      // These fields are part of the subaddress field
      makeField(PropertyInfoIndex.STREET_FLD, ent.getStreet());
      makeField(PropertyInfoIndex.CITY_FLD, ent.getCity());
      makeField(PropertyInfoIndex.STATE_FLD, ent.getState());
      makeField(PropertyInfoIndex.ZIP_FLD, ent.getZip());
      makeField(PropertyInfoIndex.ALTADDRESS_FLD, ent.getAlternateAddress());
      makeField(PropertyInfoIndex.CODEIDX_FLD, ent.getCode());
      makeField(PropertyInfoIndex.LOC_KEYS_FLD, ent.getKeys());

      makeField(PropertyInfoIndex.URL, ent.getLink());

      endObject();

      return makeDocInfo(BwIndexer.docTypeLocation, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwFilterDef ent) throws CalFacadeException {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      ent.fixNames(basicSysprops, principal);

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      indexBwStrings("displayName", ent.getDisplayNames());
      makeField(PropertyInfoIndex.FILTER_EXPR, ent.getDefinition());
      indexBwStrings(PropertyInfoIndex.DESCRIPTION, ent.getDescriptions());

      endObject();

      return makeDocInfo(BwIndexer.docTypeFilter, 0, ent.getHref());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwCalendar col) throws CalFacadeException {
    try {
      final long version = col.getMicrosecsVersion();

      startObject();

      makeShareableContained(col);

      makeField("tombstoned", col.getTombstoned());
      makeField(PropertyInfoIndex.LAST_MODIFIED,
                col.getLastmod().getTimestamp());
      makeField(PropertyInfoIndex.CREATED,
                col.getCreated());
      //makeField(PropertyInfoIndex.VERSION, version);

      makeField(PropertyInfoIndex.NAME, col.getName());

      makeField(PropertyInfoIndex.SUMMARY, col.getSummary());
      makeField(PropertyInfoIndex.DESCRIPTION,
                col.getDescription());

      makeField(PropertyInfoIndex.AFFECTS_FREE_BUSY,
                col.getAffectsFreeBusy());
      makeField(PropertyInfoIndex.ALIAS_URI,
                col.getAliasUri());
      makeField(PropertyInfoIndex.CALTYPE,
                col.getCalType());
      makeField(PropertyInfoIndex.DISPLAY,
                col.getDisplay());
      makeField(PropertyInfoIndex.FILTER_EXPR,
                col.getFilterExpr());
      makeField(PropertyInfoIndex.IGNORE_TRANSP,
                col.getIgnoreTransparency());
      makeField(PropertyInfoIndex.LAST_REFRESH,
                col.getLastRefresh());
      makeField(PropertyInfoIndex.LAST_REFRESH_STATUS,
                col.getLastRefreshStatus());
      makeField(PropertyInfoIndex.REFRESH_RATE,
                col.getRefreshRate());
      makeField(PropertyInfoIndex.REMOTE_ID,
                col.getRemoteId());
      makeField(PropertyInfoIndex.REMOTE_PW,
                col.getRemotePw());
      makeField(PropertyInfoIndex.UNREMOVEABLE,
                col.getUnremoveable());
                
      // mailListId

      indexProperties(getJname(PropertyInfoIndex.COL_PROPERTIES),
                      col.getProperties());
      indexCategories(col.getCategories());
      
      endObject();

      return makeDocInfo(BwIndexer.docTypeCollection,
                         version, col.getHref());
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
  EsDocInfo makeDoc(final EventInfo ei,
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

      makeField("tombstoned", ev.getTombstoned());
      makeField(PropertyInfoIndex.DELETED, ev.getDeleted());
      makeField(PropertyInfoIndex.NAME, ev.getName());
      makeField(PropertyInfoIndex.HREF, ev.getHref());
      makeField(PropertyInfoIndex.CALSUITE, ev.getCalSuite());

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

        makeField(PropertyInfoIndex.LOCATION_HREF, loc.getHref());

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
      } else {
        // Try the href
        final String locHref = ev.getLocationHref();
        if (locHref != null) {
          makeField(PropertyInfoIndex.LOCATION_HREF, locHref);
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

      indexAlarms(start, ev.getAlarms());

      /* Attachment */

      final boolean vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

      if (ev.getNumAttendees() > 0) {
        indexAttendees(ev.getAttendees(), vpoll);
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
        makeField(PropertyInfoIndex.POLL_WINNER, ev.getPollWinner());
        makeField(PropertyInfoIndex.POLL_PROPERTIES, ev.getPollProperties());
      } else {
        docType = BwIndexer.docTypeEvent;
      }

      endObject();

      return makeDocInfo(docType,
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

  String getHref(final BwUnversionedDbentity val) {
    if (val instanceof FixNamesEntity) {
      final FixNamesEntity ent = (FixNamesEntity)val;

      ent.fixNames(basicSysprops, principal);
    }

    if (val.getHref() == null) {
      warn("No href for " + val);
    }

    return val.getHref();
  }

  void makeHref(final BwUnversionedDbentity val) throws CalFacadeException {
    makeField(PropertyInfoIndex.HREF, getHref(val));
  }

  private void makeOwned(final BwOwnedDbentity ent)
          throws Throwable {
    makeHref(ent);
    makeField(PropertyInfoIndex.OWNER, ent.getOwnerHref());
    makeField(PropertyInfoIndex.PUBLIC, ent.getPublick());
  }

  private void makeShareable(final BwShareableDbentity ent)
          throws Throwable {
    makeOwned(ent);
    makeField(PropertyInfoIndex.CREATOR, ent.getCreatorHref());
    makeField(PropertyInfoIndex.ACL, ent.getAccess());
  }

  private void makeShareableContained(final BwShareableContainedDbentity ent)
          throws Throwable {
    makeShareable(ent);

    String colPath = ent.getColPath();
    if (colPath == null) {
      colPath = "";
    }

    makeField(PropertyInfoIndex.COLLECTION, colPath);
  }

  private void indexXprops(final XpropsEntity ent) throws CalFacadeException {
    try {
      if (Util.isEmpty(ent.getXproperties())) {
        return;
      }

      /* First output ones we know about with our own name */
      for (final String nm: interestingXprops.keySet()) {
        final List<BwXproperty> props = ent.getXproperties(nm);

        if (Util.isEmpty(props)) {
          continue;
        }

        startArray(interestingXprops.get(nm));

        for (final BwXproperty xp: props) {
          if (xp.getName().equals(BwXproperty.bedeworkSuggestedTo)) {
            final String val = xp.getValue();

            // Find the second ":" delimiter

            final int pos = val.indexOf(":", 2);

            if (pos < 0) {
              // Bad value
              continue;
            }
            value(val.substring(0, pos));
            continue;
          }

          if (xp.getName().equals(BwXproperty.bedeworkTag)) {
            final String val = xp.getValue();

            if (val == null) {
              // Bad value
              continue;
            }

            for (final String sval: val.split(",")) {
              if (sval == null) {
                continue;
              }

              final String svalTrim = sval.trim();
              if (svalTrim.length() == 0) {
                continue;
              }

              value(svalTrim);
            }

            continue;
          }

          String pars = xp.getPars();
          if (pars == null) {
            pars = "";
          }

          value(pars + "\t" + xp.getValue());
        }

        endArray();
      }

      /* Now ones we don't know or care about */

      startArray(getJname(PropertyInfoIndex.XPROP));

      for (final BwXproperty xp: ent.getXproperties()) {
        final String nm = interestingXprops.get(xp.getName());

        if (nm != null) {
          continue;
        }

        startObject();
        makeField(PropertyInfoIndex.NAME, xp.getName());

        if (xp.getPars() != null) {
          makeField(getJname(PropertyInfoIndex.PARAMETERS),
                        xp.getPars());
        }

        makeField(getJname(PropertyInfoIndex.VALUE),
                      xp.getValue());
        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexViews(final Collection<BwView> views) throws CalFacadeException {
    if (Util.isEmpty(views)) {
      return;
    }

    try {
      startArray("views");

      for (final BwView view: views) {
        startObject();
        makeField(PropertyInfoIndex.NAME, view.getName());

        indexStrings(getJname(PropertyInfoIndex.HREF),
                     view.getCollectionPaths());

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexProperties(final String name,
                               final Set<BwProperty> props) throws CalFacadeException {
    if (props == null) {
      return;
    }

    try {
      startArray(name);
      
      for (final BwProperty prop: props) {
        startObject();
        makeField(PropertyInfoIndex.NAME, prop.getName());
        makeField(PropertyInfoIndex.VALUE, prop.getValue());
        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexCategories(final Collection <BwCategory> cats) throws CalFacadeException {
    if (cats == null) {
      return;
    }

    try {
      startArray(getJname(PropertyInfoIndex.CATEGORIES));

      for (final BwCategory cat: cats) {
        startObject();

        cat.fixNames(basicSysprops, principal);

        makeField(PropertyInfoIndex.UID, cat.getUid());
        makeField(PropertyInfoIndex.HREF, cat.getHref());
        startArray(getJname(PropertyInfoIndex.VALUE));
        // Eventually may be more of these
        makeField((PropertyInfoIndex)null, cat.getWord());
        endArray();
        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexContacts(final Set<BwContact> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      // Only index enough to retrieve the actual contact

      startArray(getJname(PropertyInfoIndex.CONTACT));

      for (final BwContact c: val) {
        c.fixNames(basicSysprops, principal);

        startObject();
        makeField(PropertyInfoIndex.HREF, c.getHref());
        makeField(PropertyInfoIndex.CN, c.getCn());

        makeField(ParameterInfoIndex.UID.getJname(),
                  c.getUid());

        makeField(ParameterInfoIndex.ALTREP.getJname(),
                  c.getLink());

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexAlarms(final BwDateTime start,
                           final Set<BwAlarm> alarms) throws CalFacadeException {
    try {
      if (Util.isEmpty(alarms)) {
        return;
      }

      startArray(getJname(PropertyInfoIndex.VALARM));

      for (final BwAlarm al: alarms) {
        startObject();

        makeField(PropertyInfoIndex.OWNER, al.getOwnerHref());
        makeField(PropertyInfoIndex.PUBLIC, al.getPublick());

        final int atype = al.getAlarmType();
        final String action;

        if (atype != BwAlarm.alarmTypeOther) {
          action = BwAlarm.alarmTypes[atype];
        } else {
          final List<BwXproperty> xps = al.getXicalProperties("ACTION");

          action = xps.get(0).getValue();
        }

        makeField(PropertyInfoIndex.ACTION, action);
        
        try {
          final Set<String> triggerTimes = new TreeSet<>();
          Date dt = null;
          
          for (int i = 0; i < 100; i++) {  // Arb limit
            dt = al.getNextTriggerDate(start, dt);
            
            if (dt == null) {
              break;
            }
            
            triggerTimes.add(DateTimeUtil.isoDateTimeUTC(dt));
          }

          if (!Util.isEmpty(triggerTimes)) {
            makeField(PropertyInfoIndex.NEXT_TRIGGER_DATE_TIME,
                      triggerTimes);
          }
        } catch (final Throwable t) {
          error("Exception calculating next trigger");
          error(t);
        }
        makeField(PropertyInfoIndex.TRIGGER, al.getTrigger());

        if (al.getTriggerDateTime()) {
          makeField(PropertyInfoIndex.TRIGGER_DATE_TIME, true);
        } else if (!al.getTriggerStart()) {
          makeField(ParameterInfoIndex.RELATED.getJname(),
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
            indexAttendees(al.getAttendees(), false);
          }
        } else if (atype == BwAlarm.alarmTypeProcedure) {
          makeField(PropertyInfoIndex.ATTACH, al.getAttach());
          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
        } else {
          makeField(PropertyInfoIndex.DESCRIPTION, al.getDescription());
        }

        indexXprops(al);

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexReqStat(final Set<BwRequestStatus> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      startArray(getJname(PropertyInfoIndex.REQUEST_STATUS));

      for (final BwRequestStatus rs: val) {
        value(rs.strVal());
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexGeo(final BwGeo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      startObject(getJname(PropertyInfoIndex.GEO));
      makeField("lat", val.getLatitude().toPlainString());
      makeField("lon", val.getLongitude().toPlainString());
      endObject();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexRelatedTo(final BwRelatedTo val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      startObject(getJname(PropertyInfoIndex.RELATED_TO));
      makeField(ParameterInfoIndex.RELTYPE.getJname(),
                    val.getRelType());
      makeField(getJname(PropertyInfoIndex.VALUE), val.getValue());
      endObject();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexOrganizer(final BwOrganizer val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      startObject(getJname(PropertyInfoIndex.ORGANIZER));
      startObject(getJname(PropertyInfoIndex.PARAMETERS));
      makeField(ParameterInfoIndex.SCHEDULE_STATUS.getJname(),
                val.getScheduleStatus());

      makeField(ParameterInfoIndex.CN.getJname(), val.getCn());

      makeField(ParameterInfoIndex.DIR.getJname(), val.getDir());

      makeField(ParameterInfoIndex.LANGUAGE.getJname(), val.getLanguage());

      makeField(ParameterInfoIndex.SENT_BY.getJname(), val.getSentBy());

      endObject();

      makeField(getJname(PropertyInfoIndex.URI),
                    val.getOrganizerUri());
      endObject();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexAttendees(final Set<BwAttendee> atts,
                              final boolean vpoll) throws CalFacadeException {
    try {
      if (Util.isEmpty(atts)) {
        return;
      }

      if (vpoll) {
        startArray(getJname(PropertyInfoIndex.VOTER));
      } else {
        startArray(getJname(PropertyInfoIndex.ATTENDEE));
      }

      for (final BwAttendee val: atts) {
        startObject();
        startObject("pars");

        if (val.getRsvp()) {
          makeField(ParameterInfoIndex.RSVP.getJname(),
                        val.getRsvp());
        }

        if (vpoll && val.getStayInformed()) {
          makeField(ParameterInfoIndex.STAY_INFORMED.getJname(),
                        val.getStayInformed());
        }

        makeField(ParameterInfoIndex.CN.getJname(), val.getCn());

        String temp = val.getPartstat();
        if (temp == null) {
          temp = IcalDefs.partstatValNeedsAction;
        }
        makeField(ParameterInfoIndex.PARTSTAT.getJname(), temp);

        makeField(ParameterInfoIndex.SCHEDULE_STATUS.getJname(),
                  val.getScheduleStatus());

        makeField(ParameterInfoIndex.CUTYPE.getJname(), val.getCuType());

        makeField(ParameterInfoIndex.DELEGATED_FROM.getJname(),
                  val.getDelegatedFrom());

        makeField(ParameterInfoIndex.DELEGATED_TO.getJname(),
                  val.getDelegatedTo());

        makeField(ParameterInfoIndex.DIR.getJname(), val.getDir());

        makeField(ParameterInfoIndex.LANGUAGE.getJname(), 
                  val.getLanguage());

        makeField(ParameterInfoIndex.MEMBER.getJname(), val.getMember());

        makeField(ParameterInfoIndex.ROLE.getJname(), val.getRole());

        makeField(ParameterInfoIndex.SENT_BY.getJname(), val.getSentBy());
        
        endObject();

        makeField("uri", val.getAttendeeUri());
        endObject();
      }

      endArray();
    } catch (final IndexException e) {
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
        startObject();
      } else {
        startObject(getJname(dtype));
      }

      makeField(PropertyInfoIndex.UTC, dt.getDate());
      makeField(PropertyInfoIndex.LOCAL, dt.getDtval());
      makeField(PropertyInfoIndex.TZID, dt.getTzid());
      makeField(PropertyInfoIndex.FLOATING,
                String.valueOf(dt.getFloating()));

      endObject();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void indexBwStrings(final PropertyInfoIndex pi,
                              final Collection<? extends BwStringBase> val) throws CalFacadeException {
    indexBwStrings(getJname(pi), val);
  }

  private void indexBwStrings(String name,
                              final Collection<? extends BwStringBase> val) throws CalFacadeException {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      startArray(name);

      for (final BwStringBase s: val) {
        makeField((PropertyInfoIndex)null, s);
      }

      endArray();
    } catch (final IndexException e) {
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
        startObject();
      } else {
        startObject(getJname(pi));
      }
      makeField(PropertyInfoIndex.LANG, val.getLang());
      makeField(PropertyInfoIndex.VALUE, val.getValue());
      endObject();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final String val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      makeField(getJname(pi), val);
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Object val) throws CalFacadeException {
    if (val == null) {
      return;
    }

    try {
      makeField(getJname(pi), val);
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Collection<String> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      startArray(getJname(pi));

      for (final String s: vals) {
        value(s);
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeLocKeys(final List<BwLocation.KeyFld> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      startArray(getJname(PropertyInfoIndex.LOC_KEYS_FLD));

      for (final BwLocation.KeyFld kf: vals) {
        startObject();

        makeField(PropertyInfoIndex.NAME, kf.getKeyName());
        makeField(PropertyInfoIndex.VALUE, kf.getKeyVal());

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private void makeBwDateTimes(final PropertyInfoIndex pi,
                               final Set<BwDateTime> vals) throws CalFacadeException {
    try {
      if (Util.isEmpty(vals)) {
        return;
      }

      startArray(getJname(pi));

      for (final BwDateTime dt: vals) {
        indexDate(null, dt);
      }

      endArray();
    } catch (final IndexException e) {
      throw new CalFacadeException(e);
    }
  }

  private static String getJname(final PropertyInfoIndex pi) {
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
}
