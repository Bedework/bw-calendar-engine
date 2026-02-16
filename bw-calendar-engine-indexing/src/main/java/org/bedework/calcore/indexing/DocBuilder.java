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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEvent.SuggestedTo;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwEventProxy;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwParticipant;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.SchedulingInfo;
import org.bedework.calfacade.base.BwOwnedDbentity;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.base.ConceptEntity;
import org.bedework.calfacade.base.XpropsEntity;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.indexing.IndexKeys;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.indexing.IndexException;
import org.bedework.util.misc.Util;
import org.bedework.util.opensearch.DocBuilderBase;
import org.bedework.util.opensearch.EsDocInfo;
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

import static org.bedework.calcore.indexing.EntityBuilder.getJname;

/** Build documents for OpenSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class DocBuilder extends DocBuilderBase {
  private final IndexKeys keys = new IndexKeys();

  static Map<String, String> interestingXprops = new HashMap<>();

  // Already processed as fields.
  static final Set<String> processedXprops = new TreeSet<>();

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
    interestingXprops.put(
            BwXproperty.bedeworkEventRegMaxTicketsPerUser,
            getJname(PropertyInfoIndex.EVENTREG_MAX_TICKETS_PER_USER));
    interestingXprops.put(BwXproperty.bedeworkEventRegStart,
                          getJname(PropertyInfoIndex.EVENTREG_START));
    interestingXprops.put(BwXproperty.bedeworkEventRegEnd,
                          getJname(PropertyInfoIndex.EVENTREG_END));
    interestingXprops.put(BwXproperty.bedeworkEventRegWaitListLimit,
                          getJname(PropertyInfoIndex.EVENTREG_WAIT_LIST_LIMIT));


    /* The suggestedTo field that gets built is losing the suggested by
       value. It's not clear that the suggested to value is used but we really
       need to build a structure that accurately reflects this property.

       Take it out of here and build the (broken) suggestedTo but also
       output the x-prop to preserve it.

       This has been going on for some time and may have left us with some
       issues - e.g. events not shoing up in the suggested queue.
     */
    //interestingXprops.put(BwXproperty.bedeworkSuggestedTo,
    //                      getJname(PropertyInfoIndex.SUGGESTED_TO));

    processedXprops.add(BwXproperty.bedeworkSuggestedTo);

    processedXprops.add(BwXproperty.bedeworkAttendeeSchedulingObject);

    processedXprops.add(BwXproperty.bedeworkOrganizerSchedulingObject);

    processedXprops.add(BwXproperty.bedeworkParticipant);

    processedXprops.add(BwXproperty.pollItemId);

    processedXprops.add(BwXproperty.pollAccceptResponse);

    processedXprops.add(BwXproperty.pollCompletion);

    processedXprops.add(BwXproperty.pollMode);

    processedXprops.add(BwXproperty.pollWinner);

    processedXprops.add(BwXproperty.pollProperties);

    processedXprops.add(BwXproperty.pollItem);
  }

  /**
   *
   */
  DocBuilder() {
    super();
  }

  /* ===================================================================
   *                   package private methods
   * =================================================================== */

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwPrincipal<?> ent) {
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

      if (ent instanceof final BwGroup<?> grp) {
        if (!Util.isEmpty(grp.getGroupMembers())) {
          startArray("memberHref");

          for (final var mbr: grp.getGroupMembers()) {
            value(mbr.getPrincipalRef());
          }
          endArray();
        }
      }

      if (ent instanceof final BwAdminGroup grp) {
        makeField("groupOwnerHref", grp.getGroupOwnerHref());
        makeField("ownerHref", grp.getOwnerHref());
      }

      if (ent instanceof final BwCalSuite cs) {
        makeField(PropertyInfoIndex.OWNER, cs.getOwnerHref());
        makeField(PropertyInfoIndex.PUBLIC, cs.getPublick());
        makeField(PropertyInfoIndex.CREATOR, cs.getCreatorHref());
        makeField(PropertyInfoIndex.ACL, cs.getAccess());
        makeField("rootCollectionPath", cs.getRootCollectionPath());
        makeField("groupHref", cs.getGroup().getHref());

        makeField("fields1", cs.getFields1());
        makeField("fields2", cs.getFields2());
      }

      endObject();

      return makeDocInfo(BwIndexer.docTypePrincipal, 0,
                         getHref(ent));
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
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
  EsDocInfo makeDoc(final BwPreferences ent) {
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
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwResource ent) {
    try {
      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      makeField(PropertyInfoIndex.LAST_MODIFIED,
                ent.getLastmod());
      makeField(PropertyInfoIndex.CREATED,
                ent.getCreated());

      makeField("tombstoned", ent.getTombstoned());
      makeField(PropertyInfoIndex.SEQUENCE, ent.getSequence());
      makeField("contentType", ent.getContentType());
      makeField("encoding", ent.getEncoding());
      makeField("contentLength", ent.getContentLength());

      endObject();

      return makeDocInfo(BwIndexer.docTypeResource, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwResourceContent ent) {
    try {
      startObject();
      makeHref(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      makeField(PropertyInfoIndex.COLLECTION, ent.getColPath());

      makeField("content", ent.getEncodedContent());

      endObject();

      return makeDocInfo(BwIndexer.docTypeResourceContent, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for resource content " +
                    ent.getHref());
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwCategory ent) {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      makeField(PropertyInfoIndex.UID, ent.getUid());

      makeField(PropertyInfoIndex.CATEGORIES, ent.getWord());
      makeField(PropertyInfoIndex.DESCRIPTION, ent.getDescription());
      makeField(PropertyInfoIndex.DELETED, ent.getArchived());
      makeField("tombstoned", false); // TODO ent.getTombstoned());

      endObject();

      return makeDocInfo(BwIndexer.docTypeCategory, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwContact ent) {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

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
      makeField(PropertyInfoIndex.DELETED,
                BwEventProperty.statusArchived.equals(ent.getStatus()));
      makeField("tombstoned", false); // TODO ent.getTombstoned());

      endObject();

      return makeDocInfo(BwIndexer.docTypeContact, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwLocation ent) {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

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
                BwEventProperty.statusArchived.equals(ent.getStatus()));
      makeField("tombstoned", false); // TODO ent.getTombstoned());

      // These fields are part of the subaddress field
      makeField(PropertyInfoIndex.STREET_FLD, ent.getStreet());
      makeField(PropertyInfoIndex.CITY_FLD, ent.getCity());
      makeField(PropertyInfoIndex.STATE_FLD, ent.getState());
      makeField(PropertyInfoIndex.ZIP_FLD, ent.getZip());
      makeField(PropertyInfoIndex.ALTADDRESS_FLD, ent.getAlternateAddress());
      makeField(PropertyInfoIndex.CODEIDX_FLD, ent.getCode());
      makeLocKeys(ent.getKeys());
      makeField(PropertyInfoIndex.LOC_COMBINED_VALUES, ent.getCombinedValues());

      makeField(PropertyInfoIndex.URL, ent.getLink());

      endObject();

      return makeDocInfo(BwIndexer.docTypeLocation, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwFilterDef ent) {
    try {
      /* We don't have real collections. It's been the practice to
         create "/" delimited names to emulate a hierarchy. Look out
         for these and try to create a real path based on them.
       */

      startObject();

      makeShareableContained(ent);

      makeField(PropertyInfoIndex.NAME, ent.getName());
      indexBwStrings("displayName", ent.getDisplayNames());
      makeField(PropertyInfoIndex.FILTER_EXPR, ent.getDefinition());
      indexBwStrings(PropertyInfoIndex.DESCRIPTION, ent.getDescriptions());

      endObject();

      return makeDocInfo(BwIndexer.docTypeFilter, 0, ent.getHref());
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + ent);
      throw new BedeworkException(t);
    }
  }

  /* Return the docinfo for the indexer */
  EsDocInfo makeDoc(final BwCollection col) {
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
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      error("Exception building doc for " + col);
      throw new BedeworkException(t);
    }
  }

  enum ItemKind {
    master,
    override,
    entity
  }

  static String getItemType(final EventInfo ei,
                            final ItemKind kind) {
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
                    final String recurid) {
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

      if (ev instanceof BwEventProxy) {
        final BwEventAnnotation ann = ((BwEventProxy)ev).getRef();

        makeField("emptyFlags", ann.getEmptyFlags());
      }

      makeField("tombstoned", ev.getTombstoned());
      makeField(PropertyInfoIndex.DELETED, ev.getDeleted());
      makeField(PropertyInfoIndex.NAME, ev.getName());
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
        makeField(PropertyInfoIndex.LOCATION_HREF, loc.getHref());

        makeField(PropertyInfoIndex.LOCATION_STR,
                  loc.getCombinedValues());
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

      indexOrganizer(ev.getOrganizer());
      indexSchedulingInfo(ev.getSchedulingInfo());

      indexRelatedTo(ev.getRelatedTo());

      indexXprops(ev);

      indexReqStat(ev.getRequestStatuses());
      makeField(PropertyInfoIndex.CTOKEN, ev.getCtoken());
      makeField(PropertyInfoIndex.RECURRING, ev.isRecurringEntity());

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

      indexAttachments(ev.getAttachments());

      final boolean vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

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

      docType = BwIndexer.docTypeEvent;

      if (vpoll) {
        if (!Util.isEmpty(ev.getPollItems())) {
          makeField(PropertyInfoIndex.POLL_ITEM, ev.getPollItems());
        }

        makeField(PropertyInfoIndex.POLL_MODE, ev.getPollMode());
        makeField(PropertyInfoIndex.POLL_WINNER,
                  ev.getPollWinner());
        makeField(PropertyInfoIndex.POLL_PROPERTIES,
                  ev.getPollProperties());
        makeField(PropertyInfoIndex.POLL_COMPLETION,
                  ev.getPollCompletion());
        makeField(PropertyInfoIndex.ACCEPT_RESPONSE,
                  ev.getPollAcceptResponse());
      }

      endObject();

      return makeDocInfo(docType,
                         version,
                         keys.makeKeyVal(getItemType(ei, kind),
                                         ei.getEvent().getHref(),
                                         recurid));
    } catch (final BedeworkException be) {
      throw be;
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  String getHref(final BwUnversionedDbentity<?> val) {
    if (val.getHref() == null) {
      warn("No href for " + val);
    }

    return val.getHref();
  }

  void makeHref(final BwUnversionedDbentity<?> val) {
    makeField(PropertyInfoIndex.HREF, getHref(val));
  }

  private void makeOwned(final BwOwnedDbentity<?> ent) {
    makeHref(ent);
    makeField(PropertyInfoIndex.OWNER, ent.getOwnerHref());
    makeField(PropertyInfoIndex.PUBLIC, ent.getPublick());
  }

  private void makeShareable(final BwShareableDbentity<?> ent) {
    makeOwned(ent);
    makeField(PropertyInfoIndex.CREATOR, ent.getCreatorHref());
    makeField(PropertyInfoIndex.ACL, ent.getAccess());
  }

  private void makeShareableContained(final BwShareableContainedDbentity<?> ent) {
    makeShareable(ent);

    final String colPath = ent.getColPath();
    if (colPath != null) {
      makeField(PropertyInfoIndex.COLLECTION, colPath);
    }
  }

  private void indexXprops(final XpropsEntity ent) {
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
              if (svalTrim.isEmpty()) {
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

      if (ent instanceof BwEvent) {
        /*
          Index suggested to so we can search for them. Does not need
          special treatment restoring it.
         */
        final List<SuggestedTo> suggs = ((BwEvent)ent).getSuggested();

        if (!Util.isEmpty(suggs)) {
          startArray(getJname(PropertyInfoIndex.SUGGESTED_TO));

          for (final SuggestedTo sugg: suggs) {
            value(String.valueOf(sugg.getStatus()) +
                          ':' +
                          sugg.getGroupHref());
          }

          endArray();
        }
      }

      if (ent instanceof ConceptEntity) {
        final var concepts =
                ((ConceptEntity)ent).getConcepts();

        if (!Util.isEmpty(concepts)) {
          startArray(getJname(PropertyInfoIndex.CONCEPT));

          for (final BwXproperty xp: concepts) {
            if (xp == null) {
              continue;
            }

            startObject();
            makeField(PropertyInfoIndex.VALUE, xp.getValue());
            endObject();
          }

          endArray();
        }
      }

      /* Now ones we don't know or care about */

      if (Util.isEmpty(ent.getXproperties())) {
        return;
      }

      startArray(getJname(PropertyInfoIndex.XPROP));

      for (final BwXproperty xp: ent.getXproperties()) {
        if (xp == null) {
          continue;
        }

        final String nm = interestingXprops.get(xp.getName());

        if ((nm != null) ||
           processedXprops.contains(xp.getName())) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexViews(final Collection<BwView> views) {
    if (Util.isEmpty(views)) {
      return;
    }

    try {
      startArray("views");

      for (final BwView view: views) {
        startObject();
        makeField(PropertyInfoIndex.NAME, view.getName());

        indexStrings(getJname(PropertyInfoIndex.COLLECTION),
                     view.getCollectionPaths());

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexProperties(final String name,
                               final Set<BwProperty> props) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexCategories(final Collection <BwCategory> cats) {
    if (cats == null) {
      return;
    }

    try {
      startArray(getJname(PropertyInfoIndex.CATEGORIES));

      for (final BwCategory cat: cats) {
        startObject();

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
      throw new BedeworkException(e);
    }
  }

  private void indexContacts(final Set<BwContact> val) {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      // Only index enough to retrieve the actual contact

      startArray(getJname(PropertyInfoIndex.CONTACT));

      for (final BwContact c: val) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexAlarms(final BwDateTime start,
                           final Set<BwAlarm> alarms) {
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
            indexAttendees(al.getAttendees());
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
      throw new BedeworkException(e);
    }
  }

  private void indexReqStat(final Set<BwRequestStatus> val) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexGeo(final BwGeo val) {
    try {
      if (val == null) {
        return;
      }

      startObject(getJname(PropertyInfoIndex.GEO));
      makeField("lat", val.getLatitude().toPlainString());
      makeField("lon", val.getLongitude().toPlainString());
      endObject();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexRelatedTo(final BwRelatedTo val) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexOrganizer(final BwOrganizer val) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexAttachments(
          final Set<BwAttachment> atts) {
    try {
      if (Util.isEmpty(atts)) {
        return;
      }

      startArray(getJname(PropertyInfoIndex.ATTACH));

      for (final BwAttachment val: atts) {
        startObject();

        makeField(ParameterInfoIndex.FMTTYPE.getJname(), val.getFmtType());
        makeField("valueType", val.getValueType());
        makeField(ParameterInfoIndex.ENCODING.getJname(), val.getEncoding());

        if (val.getEncoding() == null) {
          makeField("value", val.getUri());
        } else {
          makeField("value", val.getValue());
        }

        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexAttendees(final Set<BwAttendee> atts) {
    if (Util.isEmpty(atts)) {
      return;
    }
    try {
      startArray(getJname(PropertyInfoIndex.ATTENDEE));

      for (final var val: atts) {
        indexAttendee(val);
      }

      endArray();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexSchedulingInfo(final SchedulingInfo si) {
    try {
      startArray(getJname(PropertyInfoIndex.PARTICIPANT));

      for (final var val: si.getParticipants()) {
        startObject();
        
        if (val.getAttendee() != null) {
          indexAttendee(val.getAttendee());
        }
        
        // Participant fields.
        final var part = val.getBwParticipant();
        if (part != null) {
          indexParticipant(part);
        }
        
        endObject();
      }

      endArray();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexAttendee(final BwAttendee val) {
    try {
      startObject(getJname(PropertyInfoIndex.ATTENDEE));

      startObject("pars");

      if (val.getRsvp()) {
        makeField(ParameterInfoIndex.RSVP.getJname(),
                  val.getRsvp());
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

      makeField(ParameterInfoIndex.EMAIL.getJname(), val.getEmail());

      makeField(ParameterInfoIndex.LANGUAGE.getJname(),
                val.getLanguage());

      makeField(ParameterInfoIndex.MEMBER.getJname(), val.getMember());

      makeField(ParameterInfoIndex.ROLE.getJname(), val.getRole());

      makeField(ParameterInfoIndex.SENT_BY.getJname(), val.getSentBy());

      endObject();

      makeField("uri", val.getAttendeeUri());
      endObject();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexParticipant(final BwParticipant val) {
    try {
      startObject("bwparticipant");

      makeField("uri", val.getCalendarAddress());

      if (val.getExpectReply()) {
        makeField("expect-reply", val.getExpectReply());
      }

      makeField("name", val.getName());

      makeField(ParameterInfoIndex.PARTSTAT.getJname(),
                val.getParticipationStatus());

      makeField(ParameterInfoIndex.SCHEDULE_STATUS.getJname(),
                val.getScheduleStatus());

      makeField("kind", val.getKind());

      makeField(ParameterInfoIndex.DELEGATED_FROM.getJname(),
                val.getDelegatedFrom());

      makeField(ParameterInfoIndex.DELEGATED_TO.getJname(),
                val.getDelegatedTo());

      //makeField(ParameterInfoIndex.DIR.getJname(), val.getDir());

      makeField(ParameterInfoIndex.EMAIL.getJname(), val.getEmail());

      makeField(ParameterInfoIndex.LANGUAGE.getJname(),
                val.getLanguage());

      makeField("member-of", 
                val.getMemberOf());

      makeField(ParameterInfoIndex.ROLE.getJname(), 
                val.getParticipantType());

      makeField("invited-by", val.getInvitedBy());
      
      final var votes = val.getVotes();
      if (!votes.isEmpty()) {
        startArray("votes");
        for (final var vote: votes) {
          startObject();

          makeField("poll-item-id", vote.getPollItemId());
          makeField("response", vote.getResponse());
          
          endObject();
        }
        endArray();
      }

      endObject();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void indexDate(final PropertyInfoIndex dtype,
                         final BwDateTime dt) {
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
      throw new BedeworkException(e);
    }
  }

  private void indexBwStrings(final PropertyInfoIndex pi,
                              final Collection<? extends BwStringBase<?>> val) {
    indexBwStrings(getJname(pi), val);
  }

  private void indexBwStrings(final String name,
                              final Collection<? extends BwStringBase<?>> val) {
    try {
      if (Util.isEmpty(val)) {
        return;
      }

      startArray(name);

      for (final BwStringBase<?> s: val) {
        makeField((PropertyInfoIndex)null, s);
      }

      endArray();
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final BwStringBase<?> val) {
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
      throw new BedeworkException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final String val) {
    if (val == null) {
      return;
    }

    try {
      makeField(getJname(pi), val);
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Object val) {
    if (val == null) {
      return;
    }

    try {
      makeField(getJname(pi), val);
    } catch (final IndexException e) {
      throw new BedeworkException(e);
    }
  }

  private void makeField(final PropertyInfoIndex pi,
                         final Collection<String> vals) {
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
      throw new BedeworkException(e);
    }
  }

  private void makeLocKeys(final List<BwLocation.KeyFld> vals) {
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
      throw new BedeworkException(e);
    }
  }

  private void makeBwDateTimes(final PropertyInfoIndex pi,
                               final Set<BwDateTime> vals) {
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
      throw new BedeworkException(e);
    }
  }

  /*
  private XContentBuilder newBuilder() {
    try {
      XContentBuilder builder = XContentFactory.jsonBuilder();

      if (debug()) {
        builder = builder.prettyPrint();
      }

      return builder;
    } catch (Throwable t) {
      throw new BedeworkException(t);
    }
  }
  */
}
