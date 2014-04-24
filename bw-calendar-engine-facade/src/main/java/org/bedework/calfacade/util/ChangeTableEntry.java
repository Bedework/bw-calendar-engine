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
import org.bedework.calfacade.base.Differable;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/** Entry class used by ChangeTable
 *
 * @author Mike Douglass
 */
public class ChangeTableEntry {
  private ChangeTable chg;

  /** Immutable: Index of the property */
  private PropertyInfoIndex index;

  private Object oldVal;
  private Object newVal;

  /** We need to hold the values and then adjust the entity collection
   * when we have them all.
   */
  private Collection newValues;

  /** Values added to entity property
   */
  private Collection addedValues;

  /** Values removed from entity property
   */
  private Collection removedValues;

  /** Values changed in the entity property - e.g. attendee PARTSTAT changed
   */
  private Collection changedValues;

  /** true if we saw a property value */
  private boolean present;

  /** true if we saw a change */
  private boolean changed;

  /** true if the field was deleted */
  private boolean deleted;

  /** true if the field was added */
  private boolean added;

  /** true if the field is a list */
  private boolean listField;

  /**
   * @param chg
   * @param index
   */
  public ChangeTableEntry(final ChangeTable chg,
                          final PropertyInfoIndex index) {
    this.chg = chg;
    this.index = index;
  }

  /** Add a value and mark as present.
   *
   * @param val
   */
  @SuppressWarnings("unchecked")
  public void addValue(final Object val) {
    if (!index.getMultiValued()) {
      throw new RuntimeException("org.bedework.icalendar.notmultivalued");
    }

    if (newValues == null) {
      // TEMP = this needs to be in BwPropertyInfo
      //if (listField) {
      if (index == PropertyInfoIndex.XPROP) {
        newValues = new ArrayList();
      } else {
        newValues = new TreeSet();
      }
    }

    present = true;
    changed = true;
    newValues.add(val);
  }

  /** Add a value and mark as present.
  *
   * @param val
   */
  @SuppressWarnings("unchecked")
  public void addValues(final Collection val) {
    if (!index.getMultiValued()) {
      throw new RuntimeException("org.bedework.icalendar.notmultivalued");
    }

    if (newValues == null) {
      if (listField) {
        newValues = new ArrayList();
      } else {
        newValues = new TreeSet();
      }
    }

    present = true;
    newValues.addAll(val);
  }

  /**
   * @return true for a multi-valued property
   */
  public PropertyInfoIndex getIndex() {
    return index;
  }

  /**
   * @return old value
   */
  public Object getOldVal() {
    return oldVal;
  }

  /**
   * @return new value
   */
  public Object getNewVal() {
    return newVal;
  }

  /**
   * @return true if it's an event property
   */
  public boolean getEventProperty() {
    return index.getEventProperty(); //eventProperty;
  }

  /**
   * @return true if it's a todo property
   */
  public boolean getTodoProperty() {
    return index.getTodoProperty();  //todoProperty;
  }

  /**
   * @return true if it's a freebusy property
   */
  public boolean getFreebusyProperty() {
    return index.getFreeBusyProperty();  //freebusyProperty;
  }

  /** True if it's a vcalendar property
   *
   * @return boolean
   */
  public boolean getVcalendarProperty() {
    return index.getVcalendarProperty();
  }

  /** True if it's a journal property
   *
   * @return boolean
   */
  public boolean getJournalProperty() {
    return index.getJournalProperty();
  }

  /** True if it's a freebusy property
   *
   * @return boolean
   */
  public boolean getFreeBusyProperty() {
    return index.getFreeBusyProperty();
  }

  /** True if it's a timezone property
   *
   * @return boolean
   */
  public boolean getTimezoneProperty() {
    return index.getTimezoneProperty();
  }

  /** True if it's an alarm property
   *
   * @return boolean
   */
  public boolean getAlarmProperty() {
    return index.getAlarmProperty();
  }

  /** True if it's a vavailability property
   *
   * @return boolean
   */
  public boolean getVavailabilityProperty() {
    return index.getVavailabilityProperty();
  }

  /** True if it's an available property
   *
   * @return boolean
   */
  public boolean getAvailableProperty() {
    return index.getAvailableProperty();
  }

  /** True if it's a vpoll property
   *
   * @return boolean
   */
  public boolean getVpollProperty() {
    return index.getVpollProperty();
  }

  /** Mark a property as changed if old != new and save old and new values
   *
   * @param oldVal
   * @param newVal
   * @return true if it's a changed property
   */
  public boolean setChanged(final Object oldVal, final Object newVal) {
    boolean ch = isChanged(oldVal, newVal);

    present = true;
    if (!ch) {
      return false;
    }

    this.oldVal = oldVal;
    this.newVal = newVal;
    deleted = newVal == null;
    added = oldVal == null;
    changed = true;

    return true;
  }

  /**
   * @return true if it's a changed property
   */
  public boolean getChanged() {
    return changed;
  }

  /** Mark a property as added and provide new value
   *
   * @param newVal
   */
  public void setAdded(final Object newVal) {
    this.newVal = newVal;
    this.oldVal = null;
    changed = true;
    added = true;
  }

  /**
   * @param val true if it's a present property
   */
  public void setPresent(final boolean val) {
    present = val;
  }

  /**
   * @return true if it's a present property
   */
  public boolean getPresent() {
    return present;
  }

  /**
   * @return true if it's a added property
   */
  public boolean getAdded() {
    return added;
  }

  /** Mark a property as deleted and provide old value
   *
   * @param oldVal
   */
  public void setDeleted(final Object oldVal) {
    this.oldVal = oldVal;
    this.newVal = null;
    changed = true;
    deleted = true;
  }

  /**
   * @return true if it's a deleted property
   */
  public boolean getDeleted() {
    return deleted;
  }

  /**
   * @return The collection of new values for this property
   */
  public Collection getNewValues() {
    return newValues;
  }

  /**
   * @param val values added to entity property
   */
  public void setAddedValues(final Collection val) {
    addedValues = val;
  }

  /**
   * @return values added to entity property
   */
  public Collection getAddedValues() {
    return addedValues;
  }

  /** Add a value to the collection of added values and mark as changed.
   *
   * @param o
   */
  @SuppressWarnings("unchecked")
  public void addAddedValue(final Object o) {
    if (addedValues == null) {
      addedValues = new ArrayList();
    }

    addedValues.add(o);
    changed = true;
    chg.noteCollectionSetChanged();
  }

  /**
   * @param val Values removed from entity property
   */
  public void setRemovedValues(final Collection  val) {
    removedValues = val;
  }

  /**
   * @return Values removed from entity property
   */
  public Collection getRemovedValues() {
    return removedValues;
  }

  /** Add a value to the collection of removed values and mark as changed.
   *
   * @param o
   */
  @SuppressWarnings("unchecked")
  public void addRemovedValue(final Object o) {
    if (removedValues == null) {
      removedValues = new ArrayList();
    }

    removedValues.add(o);
    changed = true;
    chg.noteCollectionSetChanged();
  }

  /**
   * @param val Values changed in entity property
   */
  public void setChangedValues(final Collection val) {
    changedValues = val;
  }

  /**
   * @return Values changed in entity property
   */
  public Collection getChangedValues() {
    return changedValues;
  }

  /** Add a value to the collection of changed values and mark as changed.
   *
   * @param o
   */
  @SuppressWarnings("unchecked")
  public void addChangedValue(final Object o) {
    if (changedValues == null) {
      changedValues = new ArrayList();
    }

    changedValues.add(o);
    changed = true;
  }

  /** Compare the original and new collections. Update the removed and added
   * collections to reflect the changes that need to be made.
   *
   * <p>Alarms are the only peruser class we have to deal with.
   *
   * @param originalVals
   * @return true if collection set changed - that is, members were added or
   *              removed.
   */
  @SuppressWarnings("unchecked")
  public boolean diff(final Collection originalVals) {
    boolean alarms = index == PropertyInfoIndex.VALARM;

    Collection newVals = getNewValues();
    boolean collectionSetChanged = false;

    if (!Util.isEmpty(originalVals)) {
      if (!alarms && (newVals == null)) {
        // Remove everything
        setRemovedValues(getCollection(originalVals));
        getRemovedValues().addAll(originalVals);
        changed = true;
        collectionSetChanged = true;
      } else {
        for (Object o: originalVals) {
          if (alarms) {
            BwAlarm al = (BwAlarm)o;

            if (!al.getOwnerHref().equals(chg.getUserHref())) {
              continue;
            }
          }

          if ((newVals == null) || !newVals.contains(o)) {
            if (getRemovedValues() == null) {
              setRemovedValues(getCollection(originalVals));
            }

            getRemovedValues().add(o);
            changed = true;
            collectionSetChanged = true;
          }
        }
      }
    }

    if (newVals == null) {
      return collectionSetChanged;
    }

    if (Util.isEmpty(originalVals)) {
      // Add everything
      setAddedValues(getCollection(newVals));
      getAddedValues().addAll(newVals);
      added = true;
      changed = true;
      return true;
    }

    /* Here we also look for entries that have equals return true but are
     * in fact different in some way. These can be tested for if they
     * implement the Differable interface
     */

    List<Differable> differList = null;

    for (Object o: newVals) {
      if ((o instanceof Differable) && (differList == null)) {
        differList = new ArrayList<Differable>(originalVals);
      }

      if (!originalVals.contains(o)) {
        if (getAddedValues() == null) {
          setAddedValues(getCollection(newVals));
        }

        getAddedValues().add(o);
        changed = true;
        collectionSetChanged = true;
      } else if (differList != null) {
        // See if it changed
        Differable orig = differList.get(differList.indexOf(o));

        if (orig.differsFrom(o)) {
          if (getChangedValues() == null) {
            setChangedValues(getCollection(newVals));
          }

          getChangedValues().add(o);
          changed = true;
        }
      }
    }

    return collectionSetChanged;
  }

  /**
   * @param orig
   * @return an empty collection of the same type as the original
   */
  private Collection getCollection(final Collection orig) {
    if (orig instanceof List) {
      return new ArrayList();
    }

    return new TreeSet();
  }

  @SuppressWarnings("unchecked")
  private boolean isChanged(final Object from, final Object to) {
    if ((to instanceof Collection) &&
        (from instanceof Collection)) {
      return isChanged((Collection)from, (Collection)to);
    }

    if (from == null) {
      return to != null;
    }

    if (to == null) {
      return true;
    }

    if ((to instanceof Differable) &&
        (from instanceof Differable)) {
      return ((Differable)from).differsFrom(to);
    }

    return !from.equals(to);
  }

  @SuppressWarnings("unchecked")
  private boolean isChanged(final Collection from, final Collection to) {
    if (Util.isEmpty(from)) {
       return !Util.isEmpty(to);
    }

    if (Util.isEmpty(to)) {
      return true;
    }

    if (from.size() != to.size()) {
      return true;
    }

    List<Differable> differList = null;

    for (Object o: from) {
      if ((o instanceof Differable) && (differList == null)) {
        differList = new ArrayList<Differable>(to);
      }

      if (!to.contains(o)) {
        return true;
      }

      if (!(o instanceof Differable)) {
        // No further check
        continue;
      }

      if (differList == null) {
        differList = new ArrayList<Differable>(to);
      }

      // See if it changed
      Differable orig = differList.get(differList.indexOf(o));

      if (orig.differsFrom(o)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("index", index.toString());
    ts.append("added", added);
    ts.append("deleted", deleted);
    ts.append("changed", changed);
    if (!added) {
      ts.append("oldVal", String.valueOf(oldVal));
    }

    if (!deleted) {
      ts.append("newVal", String.valueOf(newVal));
    }

    return ts.toString();
  }

  /*
  private static void initMap() {
    / * ---------------------------- Single valued --------------- * /

    put(new ChangeTableEntry(PropertyInfoIndex.ACCEPT_RESPONSE));

    put(new ChangeTableEntry(PropertyInfoIndex.BUSYTYPE));

    put(new ChangeTableEntry(PropertyInfoIndex.CLASS));

    put(new ChangeTableEntry(PropertyInfoIndex.COMPLETED));

    put(new ChangeTableEntry(PropertyInfoIndex.CREATED));

    put(new ChangeTableEntry(PropertyInfoIndex.DESCRIPTION));

    put(new ChangeTableEntry(PropertyInfoIndex.DTEND));

    put(new ChangeTableEntry(PropertyInfoIndex.DTSTAMP));

    put(new ChangeTableEntry(PropertyInfoIndex.DTSTART));

    put(new ChangeTableEntry(PropertyInfoIndex.DUE));

    put(new ChangeTableEntry(PropertyInfoIndex.DURATION));

    put(new ChangeTableEntry(PropertyInfoIndex.GEO));

    put(new ChangeTableEntry(PropertyInfoIndex.LAST_MODIFIED));

    put(new ChangeTableEntry(PropertyInfoIndex.LOCATION));

    put(new ChangeTableEntry(PropertyInfoIndex.ORGANIZER));

    put(new ChangeTableEntry(PropertyInfoIndex.PERCENT_COMPLETE));

    put(new ChangeTableEntry(PropertyInfoIndex.POLL_ITEM_ID));

    put(new ChangeTableEntry(PropertyInfoIndex.POLL_MODE));

    put(new ChangeTableEntry(PropertyInfoIndex.POLL_PROPERTIES));

    put(new ChangeTableEntry(PropertyInfoIndex.PRIORITY));

    put(new ChangeTableEntry(PropertyInfoIndex.RECURRENCE_ID));

    put(new ChangeTableEntry(PropertyInfoIndex.SEQUENCE));

    put(new ChangeTableEntry(PropertyInfoIndex.STATUS));

    put(new ChangeTableEntry(PropertyInfoIndex.SUMMARY));

    put(new ChangeTableEntry(PropertyInfoIndex.TRANSP));

    put(new ChangeTableEntry(PropertyInfoIndex.UID));

    put(new ChangeTableEntry(PropertyInfoIndex.URL));

    / * ---------------------------- Multi valued --------------- * /

    // TODO - this should be dealt with by annotations.
    ChangeTableEntry xpropEntry = new ChangeTableEntry(true, PropertyInfoIndex.XPROP);
    xpropEntry.listField = true;
    put(xpropEntry);

    / * Event, Todo and Freebusy * /

    put(new ChangeTableEntry(true, PropertyInfoIndex.ATTENDEE));

    put(new ChangeTableEntry(true, PropertyInfoIndex.COMMENT));

    put(new ChangeTableEntry(true, PropertyInfoIndex.CONTACT));

    put(new ChangeTableEntry(true, PropertyInfoIndex.REQUEST_STATUS));

    / * Event and Todo * /

    put(new ChangeTableEntry(true, PropertyInfoIndex.ATTACH));

    put(new ChangeTableEntry(true, PropertyInfoIndex.CATEGORIES));

    put(new ChangeTableEntry(true, PropertyInfoIndex.RELATED_TO));

    put(new ChangeTableEntry(true, PropertyInfoIndex.RESOURCES));

    put(new ChangeTableEntry(true, PropertyInfoIndex.VALARM));

    / * -------------Recurrence (also multi valued) --------------- * /

    put(new ChangeTableEntry(true, PropertyInfoIndex.EXDATE));

    put(new ChangeTableEntry(true, PropertyInfoIndex.EXRULE));

    put(new ChangeTableEntry(true, PropertyInfoIndex.RDATE));

    put(new ChangeTableEntry(true, PropertyInfoIndex.RRULE));

    / * -------------- Other non-event, non-todo ---------------- * /

    put(new ChangeTableEntry(null, PropertyInfoIndex.FREEBUSY));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZID));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZNAME));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZOFFSETFROM));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZOFFSETTO));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZURL));

    put(new ChangeTableEntry(true, PropertyInfoIndex.VOTER));

    put(new ChangeTableEntry(null, PropertyInfoIndex.ACTION));

    put(new ChangeTableEntry(null, PropertyInfoIndex.REPEAT));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TRIGGER));

    / * ----------------------------- Non-ical ---------------- * /

    put(new ChangeTableEntry(PropertyInfoIndex.COLLECTION));

    put(new ChangeTableEntry(PropertyInfoIndex.COST));

    put(new ChangeTableEntry(PropertyInfoIndex.DELETED));

    put(new ChangeTableEntry(PropertyInfoIndex.END_TYPE));
  }

  private static void put(final ChangeTableEntry ent) {
    map.put(ent.index, ent);
  }
  */
}
