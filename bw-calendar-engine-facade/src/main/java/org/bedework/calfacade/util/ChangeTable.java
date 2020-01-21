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
package org.bedework.calfacade.util;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwAttachment;
import org.bedework.calfacade.BwAttendee;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwDateTime;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwRequestStatus;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.BwXproperty;
import org.bedework.util.calendar.IcalDefs;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/** Class to track changes to calendar entities. CalDAV (and file uploads)
 * present a new copy of the event. From this we have to figure out what the
 * changes were and apply only those changes.
 *
 * <p>This is particularly important for multivalued fields where replacement of
 * the entire property can lead to a large number of deletions and reinsertions.
 *
 * @author Mike Douglass
 */
public class ChangeTable implements Logged, Serializable {
  private final HashMap<PropertyInfoIndex, ChangeTableEntry> map = 
          new HashMap<>();

  private boolean collectionSetChanged;

  private boolean significantPropertyChanged;

  private String userHref;

  /** List of properties considered insignificant for scheduling. This should be
   * a system configuration option.
   *
   * <p>If only these properties are modified then a scheduling message will
   * not be sent as a result of an update.
   */
  private static final List<PropertyInfoIndex> schedulingInsignificantProperties;

  /** List of properties iTip specifies require a SEQUENCE update when changed.
   */
  private static final List<PropertyInfoIndex> schedulingSequenceChangeProperties;

  static {
    final List<PropertyInfoIndex> sip = new ArrayList<>();

    sip.add(PropertyInfoIndex.CLASS);
    sip.add(PropertyInfoIndex.CREATED);
    sip.add(PropertyInfoIndex.DTSTAMP);
    sip.add(PropertyInfoIndex.LAST_MODIFIED);
    sip.add(PropertyInfoIndex.SEQUENCE);
    sip.add(PropertyInfoIndex.REQUEST_STATUS);

    /* non ical */
    sip.add(PropertyInfoIndex.CREATOR);
    sip.add(PropertyInfoIndex.OWNER);
    sip.add(PropertyInfoIndex.COST);

    schedulingInsignificantProperties = Collections.unmodifiableList(sip);

    final List<PropertyInfoIndex> sscp = new ArrayList<>();

    sscp.add(PropertyInfoIndex.DTSTART);
    sscp.add(PropertyInfoIndex.DTEND);
    sscp.add(PropertyInfoIndex.DURATION);
    sscp.add(PropertyInfoIndex.DUE);
    sscp.add(PropertyInfoIndex.RRULE);
    sscp.add(PropertyInfoIndex.RDATE);
    sscp.add(PropertyInfoIndex.EXDATE);
    sscp.add(PropertyInfoIndex.STATUS);

    schedulingSequenceChangeProperties = Collections.unmodifiableList(sscp);
  }

  /** Constructor
   * @param userHref principal href
   */
  public ChangeTable(final String userHref) {
    super();

    this.userHref = userHref;
  }

  /**
   * @return user we are acting for
   */
  public String getUserHref() {
    return userHref;
  }

  /** Clear all changes - will force an update
   */
  public void clear() {
    map.clear();
  }

  /**
   * @return true if no change information has been added.
   */
  public boolean isEmpty() {
    return map.isEmpty();
  }

  /** Get the collection set changed flag - true if any collection had entries
   * added or removed.
   *
   * @return boolean false if no change to any collection set
   */
  public boolean getSignificantChange() {
    return collectionSetChanged || significantPropertyChanged;
  }

  /**
   * @return true if a change requires the sequence be updated
   */
  public boolean getSequenceChangeNeeded() {
    for (final ChangeTableEntry cte: getEntries()) {
      if (cte.getChanged() &&
              schedulingSequenceChangeProperties.contains(cte.getIndex())) {
        return true;
      }
    }
    return false;
  }

  /** Set the present flag on the named entry.
   *
   * @param index
   * @return boolean false if entry not found
   */
  public boolean present(final PropertyInfoIndex index) {
    ChangeTableEntry ent = getEntry(index);

    if (ent != null) {
      ent.setPresent(true);
      return true;
    }

    return false;
  }

  /** Return true if from is not the same as to and set the entry changed flag.
   *
   * @param index - the property index
   * @param from
   * @param to
   * @return boolean true if changed
   */
  public boolean changed(final PropertyInfoIndex index,
                         final Object from,
                         final Object to) {
    return getEntry(index).setChanged(from, to);
  }

  /**
   * @param index
   * @param val
   */
  public void addValue(final PropertyInfoIndex index,
                       final Object val) {
    ChangeTableEntry ent = getEntry(index);

    if (ent == null) {
      throw new RuntimeException("org.bedework.icalendar.notmultivalued");
    }

    ent.addValue(val);
  }

  /**
   * @param index
   * @param val
   */
  public void addValues(final PropertyInfoIndex index,
                        final Collection val) {
    ChangeTableEntry ent = getEntry(index);

    if (ent == null) {
      throw new RuntimeException("org.bedework.icalendar.notmultivalued");
    }

    ent.addValues(val);
  }

  /** Get the indexed entry
   *
   * @param index
   * @return Entry null if not found
   */
  public ChangeTableEntry getEntry(final PropertyInfoIndex index) {
    ChangeTableEntry ent = map.get(index);
    if (ent != null) {
      return ent;
    }

    ent = new ChangeTableEntry(this, index);
    map.put(index, ent);
    return ent;
  }

  /**
   * @return entries added to table.
   */
  public Collection<ChangeTableEntry> getEntries() {
    return map.values();
  }

  /** Go through the change table entries removing fields that were not present
   * in the incoming data. This method is for the traditional update by
   * replacement approach. Do NOT call for the patch or selective update
   * approach as found in e.g. SOAP.
   *
   * @param ev to update
   * @param update
   * @param attendeeFromOrganizer true if we are updating an attendee
   *                              from the organizer message
   */
  @SuppressWarnings("unchecked")
  public void processChanges(final BwEvent ev,
                             final boolean update,
                             final boolean attendeeFromOrganizer) {
    HashMap<PropertyInfoIndex, ChangeTableEntry> fullmap =
      new HashMap<>(map);

    for (PropertyInfoIndex pii: PropertyInfoIndex.values()) {
      ChangeTableEntry ent = fullmap.get(pii);
      if (ent == null) {
        ent = new ChangeTableEntry(this, pii);
        fullmap.put(pii, ent);
      }
    }

    /* Single valued first */
    for (ChangeTableEntry ent: fullmap.values()) {
      if (ent.getPresent()) {
        continue;
      }

      switch (ev.getEntityType()) {
      case IcalDefs.entityTypeEvent:
        if (!ent.getEventProperty()) {
          continue;
        }
        break;

      case IcalDefs.entityTypeTodo:
        if (!ent.getTodoProperty()) {
          continue;
        }
        break;

      //case CalFacadeDefs.entityTypeJournal:

      case IcalDefs.entityTypeFreeAndBusy:
        if (!ent.getFreebusyProperty()) {
          continue;
        }
        break;

      case IcalDefs.entityTypeVavailability:
        // XXX Fake this one for the moment
        if (!ent.getEventProperty()) {
          continue;
        }
        break;

      case IcalDefs.entityTypeAvailable:
        // XXX Fake this one for the moment
        if (!ent.getEventProperty()) {
          continue;
        }
        break;

      case IcalDefs.entityTypeVpoll:
        if (!ent.getVpollProperty()) {
          continue;
        }
        break;

      default:
        warn("Unsupported entity type: " + ev.getEntityType());
        continue;
      }

      switch (ent.getIndex()) {
      case ACCEPT_RESPONSE:
        if (ev.getPollAcceptResponse() != null) {
          ent.setDeleted(ev.getPollAcceptResponse());
          if (update) {
            ev.setPollAcceptResponse(null);
          }
        }
        break;

      case CLASS:
        if (ev.getClassification() != null) {
          ent.setDeleted(ev.getClassification());
          if (update) {
            ev.setClassification(null);
          }
        }
        break;

      case COMPLETED:
        if (ev.getCompleted() != null) {
          ent.setDeleted(ev.getCompleted());
          if (update) {
            ev.setCompleted(null);
          }
        }
        break;

      case CREATED:
        // Leave
        break;

      case DESCRIPTION:
        if (ev.getDescription() != null) {
          ent.setDeleted(ev.getDescription());
          if (update) {
            ev.setDescription(null);
          }
        }
        break;

      case DTSTAMP:
        // Leave
        break;

      case DTSTART:
        // XXX Check this is handled elsewhere
        break;

      case DURATION:
        // XXX Check this is handled elsewhere
        break;

      case GEO:
        if (ev.getGeo() != null) {
          ent.setDeleted(ev.getGeo());
          if (update) {
            ev.setGeo(null);
          }
        }
        break;

      case LAST_MODIFIED:
        // Leave
        break;

      case LOCATION:
        if (ev.getLocation() != null) {
          ent.setDeleted(ev.getLocation());
          if (update) {
            ev.setLocation(null);
          }
        }
        break;

      case ORGANIZER:
        if (ev.getOrganizer() != null) {
          ent.setDeleted(ev.getOrganizer());
          if (update) {
            ev.setOrganizer(null);
          }
        }
        break;

      case PERCENT_COMPLETE:
        if (ev.getPercentComplete() != null) {
          ent.setDeleted(ev.getPercentComplete());
          if (update) {
            ev.setPercentComplete(null);
          }
        }
        break;

      case POLL_ITEM_ID:
        if (ev.getPollItemId() != null) {
          ent.setDeleted(ev.getPollItemId());
          if (update) {
            ev.setPollItemId(null);
          }
        }
        break;

      case POLL_MODE:
        if (ev.getPollMode() != null) {
          ent.setDeleted(ev.getPollMode());
          if (update) {
            ev.setPollMode(null);
          }
        }
        break;

      case POLL_PROPERTIES:
        if (ev.getPollProperties() != null) {
          ent.setDeleted(ev.getPollProperties());
          if (update) {
            ev.setPollProperties(null);
          }
        }
        break;

      case PRIORITY:
        if (ev.getPriority() != null) {
          ent.setDeleted(ev.getPriority());
          if (update) {
            ev.setPriority(null);
          }
        }
        break;

      case RECURRENCE_ID:
        // XXX Handled elsewhere?
        break;

      case RELATED_TO:
        if (ev.getRelatedTo() != null) {
          ent.setDeleted(ev.getRelatedTo());
          if (update) {
            ev.setRelatedTo(null);
          }
        }
        break;

      case SEQUENCE:
        // XXX Handled elsewhere?
        break;

      case STATUS:
        if (ev.getStatus() != null) {
          ent.setDeleted(ev.getStatus());
          if (update) {
            ev.setStatus(null);
          }
        }
        break;

      case SUMMARY:
        if (ev.getSummary() != null) {
          ent.setDeleted(ev.getSummary());
          if (update) {
            ev.setSummary(null);
          }
        }
        break;

      case UID:
        // Leave
        break;

      case URL:
        if (ev.getLink() != null) {
          ent.setDeleted(ev.getLink());
          if (update) {
            ev.setLink(null);
          }
        }
        break;

      case DTEND:
        // XXX Handled elsewhere?
        break;

      case TRANSP:
        /*
        if (ev.getPeruserTransparency(userHref) != null) {
          ent.setDeleted(ev.getPeruserTransparency(userHref));
          if (update) {
            ev.setPeruserTransparency(userHref, null);
          }
        }
        */
        if (ev.getTransparency() != null) {
          ent.setDeleted(ev.getTransparency());
          if (update) {
            ev.setTransparency(null);
          }
        }
        break;

      case ACTION:
        break;
      case BUSYTYPE:
        break;
      case COLLECTION:
        break;
      case COST:
        break;
      case DELETED:
        break;
      case DUE:
        break;
      case END_TYPE:
        break;
      case FREEBUSY:
        break;
      case HREF:
        break;
      case LANG:
        break;
      case REPEAT:
        break;
      case TRIGGER:
        break;
      case UNKNOWN_PROPERTY:
        break;
      case VALARM:
        break;
      case XBEDEWORK_COST:
        break;

        // following are multi
      case ATTACH:
        break;
      case ATTENDEE:
        break;
      case CATEGORIES:
        break;
      case COMMENT:
        break;
      case CONTACT:
        break;
      case EXDATE:
        break;
      case EXRULE:
        break;
      case RDATE:
        break;
      case REQUEST_STATUS:
        break;
      case RESOURCES:
        break;
      case RRULE:
        break;
      case VOTER:
        break;
      case XPROP:
        break;

        // following are Timezones - ignored
      case TZID:
        break;
      case TZIDPAR:
        break;
      case TZNAME:
        break;
      case TZOFFSETFROM:
        break;
      case TZOFFSETTO:
        break;
      case TZURL:
        break;

        // following are ignored
      case CALSCALE:
        break;
      case CREATOR:
        break;
      case CTAG:
        break;
      case ENTITY_TYPE:
        break;
      case ETAG:
        break;
      case METHOD:
        break;
      case OWNER:
        break;
      case PRODID:
        break;
      case VERSION:
        break;
      }
    }

    /* ---------------------------- Multi valued --------------- */

    for (ChangeTableEntry ent: fullmap.values()) {
      /* See if any change was significant */
      if (!schedulingInsignificantProperties.contains(ent.getIndex())) {
        if (ent.getAdded() || ent.getChanged() || ent.getDeleted()) {
          significantPropertyChanged = true;
        }
      }

      /* These can be present but we still need to delete members. */
      if (!ent.getEventProperty() && !ent.getVpollProperty()) {
        continue;
      }

      Collection<?> originalVals;

      switch (ent.getIndex()) {
      case ATTACH:
        originalVals = ev.getAttachments();
        if (checkMulti(ent, originalVals, update)) {
          ev.setAttachments((Set<BwAttachment>)ent.getAddedValues());
        }
        break;

      case ATTENDEE:
        if (ev.getEntityType() == IcalDefs.entityTypeVpoll) {
          // Skip so as not to disturb the attendees property - we deal with it as VOTER
          break;
        }
        originalVals = ev.getAttendees();

/*        diff(ent, originalVals);

        if (ev instanceof BwEventProxy) {
          // It's an override - we have to clone all the set if anything changes
          if (ent.changed && update) {
            Set<BwAttendee> orig = new TreeSet<BwAttendee>();

            for (Object o: originalVals) {
              BwAttendee att = (BwAttendee)o;

              orig.add((BwAttendee)att.clone());
            }
            checkMulti(ent, orig, update);
            ev.setAttendees(orig);
          }
        } else if (checkMulti(ent, originalVals, update)) {
          ev.setAttendees((Set)ent.getAddedValues());
        }*/
        if (checkMulti(ent, originalVals, update)) {
          ev.setAttendees((Set<BwAttendee>)ent.getAddedValues());
        }
        break;

      case CATEGORIES:
        originalVals = ev.getCategories();
        if (checkMulti(ent, originalVals, update)) {
          ev.setCategories((Set<BwCategory>)ent.getAddedValues());
        }
        break;

      case COMMENT:
        originalVals = ev.getComments();
        if (checkMulti(ent, originalVals, update)) {
          ev.setComments((Set<BwString>)ent.getAddedValues());
        }
        break;

      case CONTACT:
        originalVals = ev.getContacts();
        if (checkMulti(ent, originalVals, update)) {
          ev.setContacts((Set<BwContact>)ent.getAddedValues());
        }
        break;

      case REQUEST_STATUS:
        originalVals = ev.getRequestStatuses();
        if (checkMulti(ent, originalVals, update)) {
          ev.setRequestStatuses((Set<BwRequestStatus>)ent.getAddedValues());
        }
        break;

      case RELATED_TO:
        break;

      case RESOURCES:
        originalVals = ev.getResources();
        if (checkMulti(ent, originalVals, update)) {
          ev.setResources((Set<BwString>)ent.getAddedValues());
        }
        break;

      case VALARM:
        if (attendeeFromOrganizer) {
          // Don't touch
          break;
        }
        originalVals = ev.getAlarms();
        if (checkMulti(ent, originalVals, update)) {
          ev.setAlarms((Set<BwAlarm>)ent.getAddedValues());
        }
        break;

      case XPROP:
        originalVals = ev.getXproperties();
        if (checkMulti(ent, originalVals, update)) {
          ev.setXproperties((List<BwXproperty>)ent.getAddedValues());
        }
        break;

      /* ---------------------------- Recurrence --------------- */

      case EXDATE:
        if (ev.getRecurrenceId() == null) {
          originalVals = ev.getExdates();
          if (checkMulti(ent, originalVals, update)) {
            ev.setExdates((Set<BwDateTime>)ent.getAddedValues());
          }
        }
        break;

      case EXRULE:
        if (ev.getRecurrenceId() == null) {
          originalVals = ev.getExrules();
          if (checkMulti(ent, originalVals, update)) {
            ev.setExrules((Set<String>)ent.getAddedValues());
          }
        }
        break;

      case RDATE:
        if (ev.getRecurrenceId() == null) {
          originalVals = ev.getRdates();
          if (checkMulti(ent, originalVals, update)) {
            ev.setRdates((Set<BwDateTime>)ent.getAddedValues());
          }
        }
        break;

      case RRULE:
        if (ev.getRecurrenceId() == null) {
          originalVals = ev.getRrules();
          if (checkMulti(ent, originalVals, update)) {
            ev.setRrules((Set<String>)ent.getAddedValues());
          }
        }
        break;

      case VOTER:
        if (ev.getEntityType() != IcalDefs.entityTypeVpoll) {
          // Skip so as not to disturb the attendees property
          break;
        }
        originalVals = ev.getAttendees();

        if (checkMulti(ent, originalVals, update)) {
          ev.setAttendees((Set<BwAttendee>)ent.getAddedValues());
        }
        break;

      case ACCEPT_RESPONSE:
        break;
      case ACTION:
        break;
      case BUSYTYPE:
        break;
      case CALSCALE:
        break;
      case CLASS:
        break;
      case COLLECTION:
        break;
      case COMPLETED:
        break;
      case COST:
        break;
      case CREATED:
        break;
      case CREATOR:
        break;
      case CTAG:
        break;
      case DELETED:
        break;
      case DESCRIPTION:
        break;
      case DTEND:
        break;
      case DTSTAMP:
        break;
      case DTSTART:
        break;
      case DUE:
        break;
      case DURATION:
        break;
      case END_TYPE:
        break;
      case ENTITY_TYPE:
        break;
      case ETAG:
        break;
      case FREEBUSY:
        break;
      case GEO:
        break;
      case HREF:
        break;
      case LANG:
        break;
      case LAST_MODIFIED:
        break;
      case LOCATION:
        break;
      case METHOD:
        break;
      case ORGANIZER:
        break;
      case OWNER:
        break;
      case PERCENT_COMPLETE:
        break;
      case POLL_ITEM_ID:
        break;
      case POLL_MODE:
        break;
      case POLL_PROPERTIES:
        break;
      case PRIORITY:
        break;
      case PRODID:
        break;
      case RECURRENCE_ID:
        break;
      case REPEAT:
        break;
      case SEQUENCE:
        break;
      case STATUS:
        break;
      case SUMMARY:
        break;
      case TRANSP:
        break;
      case TRIGGER:
        break;
      case TZID:
        break;
      case TZIDPAR:
        break;
      case TZNAME:
        break;
      case TZOFFSETFROM:
        break;
      case TZOFFSETTO:
        break;
      case TZURL:
        break;
      case UID:
        break;
      case UNKNOWN_PROPERTY:
        break;
      case URL:
        break;
      case VERSION:
        break;
      case XBEDEWORK_COST:
        break;
      default:
        break;
      }
    }
    /* Added any deleted items to the change table. */
    for (ChangeTableEntry ent: fullmap.values()) {
      if (ent.getDeleted()) {
        ev.getChangeset(null).changed(ent.getIndex(), ent.getOldVal(), null);
      }
    }
  }

  /** mark the addition or removal of members of a collection
   *
   */
  public void noteCollectionSetChanged() {
    collectionSetChanged = true;
  }

  /** True if any recurrence property changed.
   *
   * @return boolean true if changed
   */
  public boolean recurrenceChanged() {
    return getEntry(PropertyInfoIndex.DTSTART).getChanged() ||
           getEntry(PropertyInfoIndex.DTEND).getChanged() ||
           getEntry(PropertyInfoIndex.DURATION).getChanged() ||
           getEntry(PropertyInfoIndex.DUE).getChanged() ||
           getEntry(PropertyInfoIndex.EXDATE).getChanged() ||
           getEntry(PropertyInfoIndex.EXRULE).getChanged() ||
           getEntry(PropertyInfoIndex.RDATE).getChanged() ||
           getEntry(PropertyInfoIndex.RRULE).getChanged();
  }

  /** True if any recurrence rules property changed.
   *
   * @return boolean true if changed
   */
  public boolean recurrenceRulesChanged() {
    return getEntry(PropertyInfoIndex.DTSTART).getChanged() ||
           getEntry(PropertyInfoIndex.DTEND).getChanged() ||
           getEntry(PropertyInfoIndex.DURATION).getChanged() ||
           getEntry(PropertyInfoIndex.DUE).getChanged() ||
           getEntry(PropertyInfoIndex.EXRULE).getChanged() ||
           getEntry(PropertyInfoIndex.RRULE).getChanged();
  }

  /** Dump the entries.
   *
   */
  public void dumpEntries() {
    debug("ChangeTable: ----------------------------");
    for (final ChangeTableEntry cte: getEntries()) {
      debug(cte.toString());
    }
    debug("end ChangeTable -------------------------");
  }

  /* ====================================================================
                      Private methods
     ==================================================================== */

  /* Return true if Collection needs to be set in the entity. adds and removes
   * are done here.
   */
  @SuppressWarnings("unchecked")
  private boolean checkMulti(final ChangeTableEntry ent,
                             final Collection originalVals,
                             final boolean update) {
    if (ent.diff(originalVals)) {
      collectionSetChanged = true;
    }

    if (ent.getChanged()) {
      map.put(ent.getIndex(), ent);
    }

    if (!ent.getChanged() || !update) {
      return false;
    }

    /* If we started with no values return true if we need to set the new values
     */
    if (Util.isEmpty(originalVals)) {
      if (originalVals == null) {
        return !Util.isEmpty(ent.getAddedValues());
      }

      if (ent.getAddedValues() != null) {
        originalVals.addAll(ent.getAddedValues());
      }

      return false;
    }

    /* We had some values - do we need to remove any? */
    if (ent.getRemovedValues() != null) {
      for (Object o: ent.getRemovedValues()) {
        originalVals.remove(o);
      }
    }

    /* We had some values - do we need to add any? */
    if (ent.getAddedValues() != null) {
      originalVals.addAll(ent.getAddedValues());
    }

    /* Any changes? */
    if (ent.getChangedValues() != null) {
      for (Object o: ent.getChangedValues()) {
        Object orig = originalVals.remove(o);

        // XXX This should be an object method
        // Don't allow cn changes - this may be a problem...
        if (orig instanceof BwAttendee) {
          ((BwAttendee)o).setCn(((BwAttendee)orig).getCn());
        }

        originalVals.add(o);
      }
//      originalVals.addAll(ent.getChangedValues());
    }

    return false;
  }

  /* ====================================================================
                      Object methods
     ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    for (ChangeTableEntry ent: map.values()) {
      if (!ent.getPresent()) {
        continue;
      }

      ts.newLine();
      ts.append(ent);
    }

    return ts.toString();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}

