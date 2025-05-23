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
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwDateTimeExdate;
import org.bedework.calfacade.BwDateTimeRdate;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwGeo;
import org.bedework.calfacade.BwGroup;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwLongString;
import org.bedework.calfacade.BwOrganizer;
import org.bedework.calfacade.BwParticipant;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwProperty;
import org.bedework.calfacade.BwRelatedTo;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwVote;
import org.bedework.calfacade.BwXproperty;
import org.bedework.calfacade.SchedulingInfo;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwStringBase;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.BwView;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.ParameterInfoIndex;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;
import org.bedework.util.opensearch.DocBuilderBase;
import org.bedework.util.opensearch.EntityBuilderBase;

import net.fortuna.ical4j.model.property.RequestStatus;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Implementation of indexer for OpenSearch
 *
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public class EntityBuilder extends EntityBuilderBase {
  private final boolean publick;

  /** Constructor - 1 use per entity
   *
   * @param fields map of fields from index
   */
  EntityBuilder(final boolean publick,
                final Map<String, ?> fields) {
    super(fields, 0);
    this.publick = publick;
    pushFields(fields);
  }

  /* ========================================================================
   *                   package private methods
   * ======================================================================== */

  DocBuilderBase.UpdateInfo makeUpdateInfo() {
    Long l = getLong("esUpdateCount");
    if (l == null) {
      l = 0L;
    }

    return new DocBuilderBase.UpdateInfo(l);
  }

  BwPrincipal<?> makePrincipal() {
    final String href = getString(PropertyInfoIndex.HREF);

    final BwPrincipal<?> pr = BwPrincipal.makePrincipal(href);

    if (pr == null) {
      return null;
    }

    pr.setCreated(getTimestamp(getJname(PropertyInfoIndex.CREATED)));
    pr.setLastAccess(getTimestamp("lastAccess"));
    pr.setDescription(getString(PropertyInfoIndex.DESCRIPTION));
    pr.setQuota(getLongVal("quota"));
    pr.setCategoryAccess(getString("categoryAccess"));
    pr.setContactAccess(getString("contactAccess"));
    pr.setLocationAccess(getString("locationAccess"));

    if (pr instanceof BwGroup) {
      final BwGroup<?> grp = (BwGroup<?>)pr;

      grp.setMemberHrefs(getStringSet("memberHref"));
    }

    if (pr instanceof BwAdminGroup) {
      final BwAdminGroup grp = (BwAdminGroup)pr;

      grp.setGroupOwnerHref(getString("groupOwnerHref"));
      grp.setOwnerHref(getString("ownerHref"));
    }

    if (pr instanceof BwCalSuite) {
      final BwCalSuite cs = (BwCalSuite)pr;

      cs.setCreatorHref(getString(PropertyInfoIndex.CREATOR));
      cs.setOwnerHref(getString(PropertyInfoIndex.OWNER));
      cs.setPublick(getBooleanNotNull(PropertyInfoIndex.PUBLIC));
      cs.setAccess(getString(PropertyInfoIndex.ACL));
      cs.setGroupHref(getString("groupHref"));

      cs.setFields1(getString("fields1"));
      cs.setFields2(getString("fields2"));
    }

    return pr;
  }

  BwPreferences makePreferences() {
    final BwPreferences ent = new BwPreferences();

    ent.setOwnerHref(getString(PropertyInfoIndex.OWNER));

    ent.setViews(restoreViews());

    ent.setEmail(getString("email"));
    ent.setDefaultCalendarPath(getString("defaultCalendarPath"));
    ent.setSkinName(getString("skinName"));
    ent.setSkinStyle(getString("skinStyle"));
    ent.setPreferredView(getString("preferredView"));
    ent.setPreferredViewPeriod(getString("preferredViewPeriod"));
    ent.setPageSize(getInt("pageSize"));
    ent.setWorkDays(getString("workDays"));
    ent.setWorkdayStart(getInt("workDayStart"));
    ent.setWorkdayEnd(getInt("workDayEnd"));
    ent.setPreferredEndType(getString("preferredEndType"));
    ent.setUserMode(getInt("userMode"));
    ent.setHour24(getBool("hour24"));
    ent.setScheduleAutoRespond(getBool("scheduleAutoRespond"));
    ent.setScheduleAutoCancelAction(getInt("scheduleAutoCancelAction"));
    ent.setScheduleDoubleBook(getBool("scheduleDoubleBook"));
    ent.setScheduleAutoProcessResponses(getInt("scheduleAutoProcessResponses"));

    ent.setProperties(restoreProperties("userProperties"));

    return ent;
  }

  BwFilterDef makefilter() {
    final BwFilterDef ent = new BwFilterDef();

    restoreSharedEntity(ent);

    ent.setName(getString(PropertyInfoIndex.NAME));

    ent.setDisplayNames(restoreBwStringSet(
            "displayName", BwString.class));
    ent.setDefinition(getString(PropertyInfoIndex.FILTER_EXPR));
    ent.setDescriptions(restoreBwStringSet(
            PropertyInfoIndex.DESCRIPTION, BwLongString.class));

    return ent;
  }

  BwResource makeResource() {
    final BwResource ent = new BwResource();

    restoreSharedEntity(ent);

    ent.setName(getString(PropertyInfoIndex.NAME));
    ent.setCreated(getString(PropertyInfoIndex.CREATED));
    ent.setLastmod(getString(PropertyInfoIndex.LAST_MODIFIED));

    ent.setSequence(getInt(PropertyInfoIndex.SEQUENCE));
    ent.setContentType(getString("contentType"));
    ent.setEncoding(getString("encoding"));
    ent.setContentLength(getLongVal("contentLength"));

    return ent;
  }

  /* Return the docinfo for the indexer */
  BwResourceContent makeResourceContent() {
    final BwResourceContent ent = new BwResourceContent();

    ent.setName(getString(PropertyInfoIndex.NAME));
    ent.setColPath(getString(PropertyInfoIndex.COLLECTION));

    final var content = getString("content");

    if (content != null) {
      ent.setByteValue(Base64.getMimeDecoder().decode(content));
    }

    return ent;
  }

  BwCategory makeCat() {
    final BwCategory cat = new BwCategory();

    restoreSharedEntity(cat);

    cat.setName(getString(PropertyInfoIndex.NAME));
    cat.setUid(getString(PropertyInfoIndex.UID));

    cat.setWord(restoreBwString(PropertyInfoIndex.CATEGORIES,
                                BwString.class));
    cat.setDescription(restoreBwString(PropertyInfoIndex.DESCRIPTION,
                                       BwString.class));

    return cat;
  }

  BwContact makeContact() {
    final BwContact ent = new BwContact();

    restoreSharedEntity(ent);

    ent.setUid(getString(PropertyInfoIndex.UID));

    ent.setCn(restoreBwStringValue(PropertyInfoIndex.CN,
                              BwString.class));
    ent.setPhone(getString(PropertyInfoIndex.PHONE));
    ent.setEmail(getString(PropertyInfoIndex.EMAIL));
    ent.setLink(getString(PropertyInfoIndex.URL));

    return ent;
  }

  BwLocation makeLocation() {
    final BwLocation ent = new BwLocation();

    restoreSharedEntity(ent);

    ent.setUid(getString(PropertyInfoIndex.UID));
    ent.setLink(getString(PropertyInfoIndex.URL));

    // All the field values are stored within these 2 fields 
    ent.setAddress(restoreBwString(
            PropertyInfoIndex.ADDRESS, BwString.class));
    ent.setSubaddress(restoreBwString(
            PropertyInfoIndex.SUBADDRESS, BwString.class));

    return ent;
  }

  BwCollection makeCollection() {
    final BwCollection col = new BwCollection();

    restoreSharedEntity(col);

    col.setName(getString(PropertyInfoIndex.NAME));
    col.setPath(getString(PropertyInfoIndex.HREF));

    col.setCreated(getString(PropertyInfoIndex.CREATED));
    col.setLastmod(new BwCollectionLastmod(col,
                                           getString(PropertyInfoIndex.LAST_MODIFIED)));
    col.setSummary(getString(PropertyInfoIndex.SUMMARY));
    col.setDescription(getString(PropertyInfoIndex.DESCRIPTION));

    col.setAffectsFreeBusy(getBool(PropertyInfoIndex.AFFECTS_FREE_BUSY));
    col.setAliasUri(getString(PropertyInfoIndex.ALIAS_URI));
    col.setCalType(getInt(PropertyInfoIndex.CALTYPE));
    col.setDisplay(getBool(PropertyInfoIndex.DISPLAY));
    col.setFilterExpr(getString(PropertyInfoIndex.FILTER_EXPR));
    col.setIgnoreTransparency(getBool(PropertyInfoIndex.IGNORE_TRANSP));
    col.setLastRefresh(getString(PropertyInfoIndex.LAST_REFRESH));
    col.setLastRefreshStatus(getString(PropertyInfoIndex.LAST_REFRESH_STATUS));
    col.setRefreshRate(getInt(PropertyInfoIndex.REFRESH_RATE));
    col.setRemoteId(getString(PropertyInfoIndex.REMOTE_ID));
    col.setRemotePw(getString(PropertyInfoIndex.REMOTE_PW));
    col.setUnremoveable(getBool(PropertyInfoIndex.UNREMOVEABLE));

    col.setProperties(restoreProperties(getJname(PropertyInfoIndex.COL_PROPERTIES)));
    restoreCategories(col);
    
    return col;
  }

  static long purgeTime = 15 * 60 * 1000;
  static long lastPurge = System.currentTimeMillis();
  
  private static class EventCacheEntry {
    long lastRef; // millis when last used
    long usect; // How many times
    final String key;
    final EventInfo ei;

    EventCacheEntry(final String key,
                    final EventInfo ei) {
      this.key = key;
      this.ei = ei;
      update();
    }
    
    void update() {
      lastRef = System.currentTimeMillis();
      usect++;
    }
    
    boolean old(final long curTime) {
      return (curTime - lastRef) > purgeTime;
    }
  }

  private static String currentChangeToken;
  private final static Map<String, EventCacheEntry> eventCache = new HashMap<>();
  private static long retrievals;
  private static long hits;
  private static long purges;
  private static long flushes;

  static void checkPurge() {
    final long now = System.currentTimeMillis();
    
    if (now - lastPurge < purgeTime) {
      return;
    }

    synchronized (eventCache) {
      final List<String> toPurge = new ArrayList<>(
              eventCache.size() / 2);

      for (final EventCacheEntry ece : eventCache.values()) {
        if (ece.old(now)) {
          toPurge.add(ece.key);
        }
      }

      for (final String key : toPurge) {
        eventCache.remove(key);
      }

      lastPurge = now;
      purges++;
    }
  }
  
  static void checkFlushCache(final String changeToken) {
    synchronized (eventCache) {
      if (changeToken == null) {
        return;
      }

      if (currentChangeToken == null) {
        currentChangeToken = changeToken;
        return;
      }

      if (!currentChangeToken.equals(changeToken)) {
        currentChangeToken = changeToken;
        eventCache.clear();
        flushes++;
      }
    }
  }
  
  /**
   * @param expanded true if we are doing this for an expanded retrieval
   *                 that is, treat everything as instances.
   * @return an event object
   */
  EventInfo makeEvent(final String id,
                      final boolean expanded) {
    final boolean override = !expanded &&
            getBool(PropertyInfoIndex.OVERRIDE);

    final boolean instance = !expanded &&
            getBool(PropertyInfoIndex.INSTANCE);

    final String cacheKey = id + override + instance;

    retrievals++;
    
    if (publick) {
      checkPurge();
      final EventCacheEntry ece = eventCache.get(cacheKey);
      if (ece != null) {
        hits++;
        ece.update();
        
        if (debug() && ((retrievals % 500) == 0)) {
          debug("Retrievals: " + retrievals + 
                        " hits: " + hits +
                        " purges: " + purges +
                        " flushes: " + flushes +
                        " size: " + eventCache.size());
        }
        
        return ece.ei;
      }
    }
    
    final BwEvent ev;

    if (override || instance) {
      ev = new BwEventAnnotation();

      final BwEventAnnotation ann = (BwEventAnnotation)ev;
      ann.setOverride(true);
      ann.setEmptyFlags(getString("emptyFlags"));
    } else {
      ev= new BwEventObj();
    }

    final EventInfo ei = new EventInfo(ev);

    /*
    Float score = (Float)sd.getFirstValue("score");

    if (score != null) {
      bwkey.setScore(score);
    }
    */

    restoreSharedEntity(ev);

    ev.setDeleted(getBool(PropertyInfoIndex.DELETED));
    ev.setName(getString(PropertyInfoIndex.NAME));
    ev.setCalSuite(getString(PropertyInfoIndex.CALSUITE));

    restoreCategories(ev);

    ev.setSummaries(restoreBwStringSet(PropertyInfoIndex.SUMMARY,
                                       BwString.class));
    ev.setDescriptions(restoreBwStringSet(PropertyInfoIndex.DESCRIPTION,
                                          BwLongString.class));

    ev.setEntityType(makeEntityType(getString(PropertyInfoIndex.ENTITY_TYPE)));

    ev.setClassification(getString(PropertyInfoIndex.CLASS));
    ev.setLink(getString(PropertyInfoIndex.URL));

    ev.setGeo(restoreGeo());

    ev.setStatus(getString(PropertyInfoIndex.STATUS));
    ev.setCost(getString(PropertyInfoIndex.COST));

    ev.setDtstamp(getString(PropertyInfoIndex.DTSTAMP));
    ev.setLastmod(getString(PropertyInfoIndex.LAST_MODIFIED));
    ev.setCreated(getString(PropertyInfoIndex.CREATED));
    ev.setStag(getString(PropertyInfoIndex.SCHEDULE_TAG));
    ev.setPriority(getInteger(PropertyInfoIndex.PRIORITY));

    ev.setSequence(getInt(PropertyInfoIndex.SEQUENCE));

    ev.setLocationHref(getString(PropertyInfoIndex.LOCATION_HREF));

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

    ev.setOrganizer(restoreOrganizer());
    restoreParticipants(ev);

    ev.setRelatedTo(restoreRelatedTo());

    final var xps = restoreXprops();
    if (ev.getXproperties() != null) {
      ev.getXproperties().addAll(xps);
    } else {
      ev.setXproperties(xps);
    }

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
    ev.setAttachments(restoreAttachments());

    final boolean vpoll = ev.getEntityType() == IcalDefs.entityTypeVpoll;

    ev.setRecipients(getStringSet(PropertyInfoIndex.RECIPIENT));
    ev.setComments(restoreBwStringSet(PropertyInfoIndex.COMMENT,
                                      BwString.class));
    restoreContacts(ev);
    ev.setResources(restoreBwStringSet(PropertyInfoIndex.RESOURCES,
                                       BwString.class));

    if (vpoll) {
      final Set<String> pollItems = getStringSet(PropertyInfoIndex.POLL_ITEM);

      if (!Util.isEmpty(pollItems)) {
        for (final String s: pollItems) {
          ev.addPollItem(s);
        }
      }

      ev.setPollMode(getString(PropertyInfoIndex.POLL_MODE));
      ev.setPollWinner(getInteger(PropertyInfoIndex.POLL_WINNER));
      ev.setPollProperties(getString(PropertyInfoIndex.POLL_PROPERTIES));
    }
    
    if (publick) {
      synchronized (eventCache) {
        eventCache.put(cacheKey, new EventCacheEntry(cacheKey, ei));
      }
    }

    return ei;
  }

  /* ========================================================================
   *                   private methods
   * ======================================================================== */

  private boolean pushFields(final PropertyInfoIndex pi) {
    return pushFields(getFirstValue(pi));
  }

  private BwGeo restoreGeo() {
    if (!pushFields(PropertyInfoIndex.GEO)) {
      return null;
    }

    try {
      final BwGeo geo = new BwGeo();

      geo.setLatitude(getBigDecimal("lat"));
      geo.setLongitude(getBigDecimal("lon"));

      return geo;
    } finally {
      popFields();
    }
  }

  private BwOrganizer restoreOrganizer() {
    if (!pushFields(PropertyInfoIndex.ORGANIZER)) {
      return null;
    }

    try {
      final BwOrganizer org = new BwOrganizer();

      if (pushFields(PropertyInfoIndex.PARAMETERS)) {
        try {
          org.setScheduleStatus(getString(ParameterInfoIndex.SCHEDULE_STATUS));
          org.setCn(getString(ParameterInfoIndex.CN));
          org.setDir(getString(ParameterInfoIndex.DIR));
          org.setLanguage(getString(ParameterInfoIndex.LANGUAGE));
          org.setSentBy(getString(ParameterInfoIndex.SENT_BY));
        } finally {
          popFields();
        }
      }

      org.setOrganizerUri(getString(PropertyInfoIndex.URI));

      return org;
    } finally {
      popFields();
    }
  }

  private Set<BwAttachment> restoreAttachments() {
    final List<Object> vals = getFieldValues(PropertyInfoIndex.ATTACH);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwAttachment> atts = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);

        final BwAttachment att = new BwAttachment();

        att.setFmtType(getString(ParameterInfoIndex.FMTTYPE));
        att.setValueType(getString("valueType"));
        att.setEncoding(getString(ParameterInfoIndex.ENCODING));

        if (att.getEncoding() == null) {
          att.setUri(getString("value"));
        } else {
          att.setValue(getString("value"));
        }

        atts.add(att);
      } finally {
        popFields();
      }
    }

    return atts;
  }

  private Set<BwAttendee> restoreAttendees() {
    final List<Object> vals = getFieldValues(PropertyInfoIndex.ATTENDEE);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwAttendee> atts = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);
        atts.add(restoreAttendee());
      } finally {
        popFields();
      }
    }

    return atts;
  }

  private void restoreParticipants(final BwEvent ev) {
    final List<Object> vals = getFieldValues(PropertyInfoIndex.PARTICIPANT);

    if (Util.isEmpty(vals)) {
      return;
    }

    final SchedulingInfo si = ev.getSchedulingInfo();

    for (final Object o: vals) {
      try {
        pushFields(o);

        BwAttendee att = null;
        if (pushFields(PropertyInfoIndex.ATTENDEE)) {
          try {
            att = restoreAttendee();
          } finally {
            popFields();
          }
        }

        // Restore participant fields.

        BwParticipant part = null;
        if (pushFields("bwparticipant")) {
          try {
            part = restoreParticipant(si);
          } finally {
            popFields();
          }
        }

        si.makeParticipant(att, part);
      } finally {
        popFields();
      }
    }
  }

  private BwAttendee restoreAttendee() {
    final BwAttendee att = new BwAttendee();

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
        att.setEmail(getString(ParameterInfoIndex.EMAIL));
        att.setLanguage(getString(ParameterInfoIndex.LANGUAGE));
        att.setMember(getString(ParameterInfoIndex.MEMBER));
        att.setRole(getString(ParameterInfoIndex.ROLE));
        att.setSentBy(getString(ParameterInfoIndex.SENT_BY));
      } finally {
        popFields();
      }
    }

    att.setAttendeeUri(getString(PropertyInfoIndex.URI));

    return att;
  }

  private BwParticipant restoreParticipant(final SchedulingInfo si) {
    final BwParticipant part = new BwParticipant(si);

    part.setCalendarAddress(getString(PropertyInfoIndex.URI));
    part.setExpectReply(getBool("expect-reply"));
    part.setName(getString("name"));
    part.setParticipationStatus(
            getString(ParameterInfoIndex.PARTSTAT));
    part.setScheduleStatus(getString(ParameterInfoIndex.SCHEDULE_STATUS));
    part.setKind(getString("kind"));
    part.setDelegatedFrom(getString(ParameterInfoIndex.DELEGATED_FROM));
    part.setDelegatedTo(getString(ParameterInfoIndex.DELEGATED_TO));
    //part.setDir(getString(ParameterInfoIndex.DIR));
    part.setEmail(getString(ParameterInfoIndex.EMAIL));
    part.setLanguage(getString(ParameterInfoIndex.LANGUAGE));
    part.setMemberOf(getString("member-of"));
    part.setParticipantType(getString(ParameterInfoIndex.ROLE));
    part.setInvitedBy(getString("invited-by"));

    restoreVotes(part);

    return part;
  }

  private void restoreVotes(final BwParticipant part) {
    final List<Object> vals = getFieldValues("votes");

    if (Util.isEmpty(vals)) {
      return;
    }

    for (final Object o: vals) {
      try {
        pushFields(o);
        final var vote = new BwVote(part, null);

        vote.setPollItemId(getInt("poll-item-id"));
        vote.setResponse(getInt("response"));

        part.addVote(vote);
      } finally {
        popFields();
      }
    }
  }

  private BwRelatedTo restoreRelatedTo() {
    if (!pushFields(PropertyInfoIndex.RELATED_TO)) {
      return null;
    }

    try {
      final BwRelatedTo rt = new BwRelatedTo();

      rt.setRelType(getString(ParameterInfoIndex.RELTYPE));
      rt.setValue(getString(PropertyInfoIndex.VALUE));

      return rt;
    } finally {
      popFields();
    }
  }

  private List<BwXproperty> restoreXprops() {
    /* Convert our special fields back to xprops */
    final Set<String> xpnames = DocBuilder.interestingXprops.keySet();

    final List<BwXproperty> xprops = new ArrayList<>();

    if (!Util.isEmpty(xpnames)) {
      for (final String xpname: xpnames) {
        @SuppressWarnings("unchecked")
        final List<String> xvals =
                (List)getFieldValues(DocBuilder.interestingXprops.get(xpname));

        if (!Util.isEmpty(xvals)) {
          for (final String xval: xvals) {
            final int pos = xval.indexOf("\t");
            String pars = null;
            final String val;

            if (pos > 0) {
              pars = xval.substring(0, pos);
              val = xval.substring(pos + 1);
            } else {
              // Strip leading /t
              val = xval.substring(1);;
            }

            final BwXproperty xp = new BwXproperty(xpname, pars, val);
            xprops.add(xp);
          }
        }
      }
    }

    /* Not needed - they are still there as x-props
    final List<Object> concepts =
            getFieldValues(PropertyInfoIndex.CONCEPT);

    if (!Util.isEmpty(concepts)) {
      for (final Object o: concepts) {
        try {
          pushFields(o);

          xprops.add(
                  BwXproperty.makeIcalProperty(
                          "CONCEPT",
                          null,
                          getString(PropertyInfoIndex.VALUE)));
        } finally {
          popFields();
        }
      }
    }
     */

    /* Now restore the rest of the xprops */

    final List<Object> xpropFields = getFieldValues(PropertyInfoIndex.XPROP);

    if (Util.isEmpty(xpropFields)) {
      return xprops;
    }

    for (final Object o: xpropFields) {
      try {
        pushFields(o);

        final BwXproperty xp = new BwXproperty();

        xp.setName(getString(PropertyInfoIndex.NAME));
        xp.setPars(getString(PropertyInfoIndex.PARAMETERS));
        xp.setValue(getString(PropertyInfoIndex.VALUE));

        xprops.add(xp);
      } finally {
        popFields();
      }
    }

    return xprops;
  }

  private void restoreReqStat(final BwEvent ev) {
    final List<String> vals =
            getStringList(getJname(PropertyInfoIndex.REQUEST_STATUS));
    if (Util.isEmpty(vals)) {
      return;
    }

    for (final String s: vals) {
      final RequestStatus rs = new RequestStatus(null, s);

      ev.addRequestStatus(BwRequestStatus.fromRequestStatus(rs));
    }
  }

  private <T extends BwStringBase<?>> T restoreBwStringValue(
          final PropertyInfoIndex pi,
          final Class<T> resultType) {
    final String val = getString(pi);
    if (val == null) {
      return null;
    }
    final T sb;
    try {
      sb = resultType.newInstance();
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    sb.setLang(null);
    sb.setValue(val);

    return sb;
  }

  private <T extends BwStringBase<?>> T restoreBwString(
          final PropertyInfoIndex pi,
          final Class<T> resultType) {
    if (!pushFields(pi)) {
      return null;
    }

    try {
      return restoreBwString(resultType);
    } finally {
      popFields();
    }
  }

  private <T extends BwStringBase<?>> Set<T> restoreBwStringSet(
          final PropertyInfoIndex pi,
          final Class<T> resultType) {
    return restoreBwStringSet(getJname(pi), resultType);
  }

  private <T extends BwStringBase<?>> Set<T> restoreBwStringSet(
          final String name,
          final Class<T> resultType) {
    final List<Object> vals = getFieldValues(name);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<T> ss = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);

        ss.add(restoreBwString(resultType));
      } finally {
        popFields();
      }
    }

    return ss;
  }

  private <T extends BwStringBase<?>> T restoreBwString(
          final Class<T> resultType) {
    final T sb;
    try {
      sb = resultType.getDeclaredConstructor().newInstance();
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    sb.setLang(getString(PropertyInfoIndex.LANG));
    sb.setValue(getString(PropertyInfoIndex.VALUE));

    return sb;
  }

  private Set<BwDateTime> restoreBwDateTimeSet(final PropertyInfoIndex pi) {
    final List<Object> vals = getFieldValues(pi);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwDateTime> tms = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);

        final String date = getString(PropertyInfoIndex.LOCAL);
        final String utcDate = getString(PropertyInfoIndex.UTC);
        final String tzid = getString(PropertyInfoIndex.TZID);
        final boolean floating = getBool(PropertyInfoIndex.FLOATING);
        final boolean dateType = date.length() == 8;

        final BwDateTime tm = BwDateTime.makeBwDateTime(dateType,
                                                        date,
                                                        utcDate,
                                                        tzid,
                                                        floating);

        if (pi == PropertyInfoIndex.EXDATE) {
          tms.add(BwDateTimeExdate.make(tm));
        } else {
          tms.add(BwDateTimeRdate.make(tm));
        }
        tms.add(tm);
      } catch (final Throwable t) {
        throw new BedeworkException(t);
      } finally {
        popFields();
      }
    }

    return tms;
  }

  private Set<BwAlarm> restoreAlarms() {
    final List<Object> vals = getFieldValues(PropertyInfoIndex.VALARM);

    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwAlarm> alarms = new TreeSet<>();

    for (final Object o: vals) {
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
        popFields();
      }
    }

    return alarms;
  }

  private static final Map<String, Integer> entitytypeMap =
          new HashMap<>();

  static {
    entitytypeMap.put("event", IcalDefs.entityTypeEvent);
    entitytypeMap.put("alarm", IcalDefs.entityTypeAlarm);
    entitytypeMap.put("todo", IcalDefs.entityTypeTodo);
    entitytypeMap.put("journal", IcalDefs.entityTypeJournal);
    entitytypeMap.put("freeAndBusy", IcalDefs.entityTypeFreeAndBusy);
    entitytypeMap.put("vavailability", IcalDefs.entityTypeVavailability);
    entitytypeMap.put("available", IcalDefs.entityTypeAvailable);
    entitytypeMap.put("vpoll", IcalDefs.entityTypeVpoll);
  }

  private int makeEntityType(final String val) {
    final Integer i = entitytypeMap.get(val);

    if (i == null) {
      return IcalDefs.entityTypeEvent;
    }

    return i;
  }

  private void restoreSharedEntity(final BwShareableContainedDbentity<?> ent) {
    ent.setCreatorHref(getString(PropertyInfoIndex.CREATOR));
    ent.setOwnerHref(getString(PropertyInfoIndex.OWNER));
    ent.setColPath(getString(PropertyInfoIndex.COLLECTION));
    ent.setAccess(getString(PropertyInfoIndex.ACL));
    ent.setPublick(getBooleanNotNull(PropertyInfoIndex.PUBLIC));
  }

  private Set<BwView> restoreViews() {
    final Collection<Object> vals = getFieldValues("views");
    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwView> views = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);

        final BwView view = new BwView();

        view.setName(getString(PropertyInfoIndex.NAME));
        final Set<String> hrefs = getStringSet(PropertyInfoIndex.COLLECTION);

        if (!Util.isEmpty(hrefs)) {
          view.setCollectionPaths(new ArrayList<>(hrefs));
        }

        views.add(view);
      } finally {
        popFields();
      }
    }

    return views;
  }

  private Set<BwProperty> restoreProperties(final String name) {
    final Collection<Object> vals = getFieldValues(name);
    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<BwProperty> props = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);
        final String pname = getString(PropertyInfoIndex.NAME);
        final String val = getString(PropertyInfoIndex.VALUE);
        props.add(new BwProperty(pname, val));
      } finally {
        popFields();
      }
    }

    return props;
  }

  private void restoreContacts(final BwEvent ev) {
    ev.setContactHrefs(getHrefs(getFieldValues(PropertyInfoIndex.CONTACT)));
  }

  private void restoreCategories(final CategorisedEntity ce) {
    ce.setCategoryHrefs(getHrefs(getFieldValues(PropertyInfoIndex.CATEGORIES)));
  }

  private Set<String> getHrefs(final List<Object> vals) {
    if (Util.isEmpty(vals)) {
      return null;
    }

    final Set<String> hrefs = new TreeSet<>();

    for (final Object o: vals) {
      try {
        pushFields(o);
        final String uid = getString(PropertyInfoIndex.HREF);
        hrefs.add(uid);
      } finally {
        popFields();
      }
    }

    return hrefs;
  }

  private List<Object> getFieldValues(final PropertyInfoIndex id) {
    return getFieldValues(getJname(id));
  }

  private Set<String> getStringSet(final PropertyInfoIndex pi) {
    return getStringSet(getJname(pi));
  }


  private Object getFirstValue(final PropertyInfoIndex id) {
    return getFirstValue(getJname(id));
  }

  private Object getFirstValue(final ParameterInfoIndex id) {
    return getFirstValue(id.getJname());
  }

  public static String getJname(final PropertyInfoIndex pi) {
    final BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

    if (ipie == null) {
      return null;
    }

    return ipie.getJname();
  }

  private Boolean getBoolean(final PropertyInfoIndex id) {
    final String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Boolean.valueOf(s);
  }

  private Boolean getBooleanNotNull(final PropertyInfoIndex id) {
    final Boolean b = getBoolean(id);

    if (b == null) {
      return Boolean.FALSE;
    }

    return b;
  }

  private boolean getBool(final PropertyInfoIndex id) {
    final Boolean b = getBoolean(id);

    if (b == null) {
      return false;
    }

    return b;
  }

  private Boolean getBoolean(final ParameterInfoIndex id) {
    final Object o = getFirstValue(id);

    if (o instanceof Boolean) {
      return (Boolean)o;
    }

    final String s = (String)o;

    if (s == null) {
      return null;
    }

    return Boolean.valueOf(s);
  }

  protected Timestamp getTimestamp(final String name) {
    final String s = (String)getFirstValue(name);
    if ((s == null) || (s.length() != 16)) {
      return null;
    }

    // in: 20140305T093551Z
    //     0   4 6 89 1 3 5
    //                1 1 1

    return  Timestamp.valueOf(
            String.format("%s-%s-%s %s:%s:%s.0",
                          s.substring(0, 4),
                          s.substring(4, 6), s.substring(6, 8),
                          s.substring(9, 11), s.substring(11, 13),
                          s.substring(13, 15)));
  }

  private boolean getBool(final ParameterInfoIndex id) {
    final Boolean b = getBoolean(id);

    if (b == null) {
      return false;
    }

    return b;
  }

  private Integer getInteger(final PropertyInfoIndex id) {
    final String s = (String)getFirstValue(id);

    if (s == null) {
      return null;
    }

    return Integer.valueOf(s);
  }

  private long getLongVal(final String name) {
    final Long l = getLong(name);
    
    if (l == null) {
      return 0;
    }
    
    return l;
  }

  private int getInt(final PropertyInfoIndex id) {
    final String s = (String)getFirstValue(id);

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

  private BwDateTime unindexDate(final PropertyInfoIndex pi) {
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
      popFields();
    }

    final boolean dateType = (local != null) && (local.length() == 8);

    return BwDateTime.makeBwDateTime(dateType, local, utc, tzid,
                                     floating);
  }
}
