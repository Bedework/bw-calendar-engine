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

import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;
import edu.rpi.sss.util.ToString;
import edu.rpi.sss.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

/** Entry class used by ChangeTable
 *
 * @author Mike Douglass
 */
public class ChangeTableEntry {
  private ChangeTable chg;

  /** Immutable */
  private boolean multiValued;

  /** Immutable: Index of the property */
  private PropertyInfoIndex index;

  /** Immutable: Name of the property */
  private String name;

  /** Immutable: True if it's an event property */
  private boolean eventProperty;

  /** Immutable: True if it's a todo property */
  private boolean todoProperty;

  /** Immutable: True if it's a freebusy property */
  private boolean freebusyProperty;

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

  /* Used to supply new entries */
  private static HashMap<String, ChangeTableEntry> map =
    new HashMap<String, ChangeTableEntry>();

  static {
    initMap();
  }

  /**
   * @param chg
   * @param multiValued
   * @param index
   * @param eventProperty
   * @param todoProperty
   * @param freebusyProperty
   */
  public ChangeTableEntry(final ChangeTable chg,
                          final boolean multiValued, final PropertyInfoIndex index,
                          final boolean eventProperty,
                          final boolean todoProperty,
                          final boolean freebusyProperty) {
    this.chg = chg;
    this.multiValued = multiValued;
    this.index = index;
    name = index.getPname();
    this.eventProperty = eventProperty;
    this.todoProperty = todoProperty;
    this.freebusyProperty = freebusyProperty;
  }

  /**
   * @param chg
   * @param multiValued
   * @param name
   * @param eventProperty
   * @param todoProperty
   */
  public ChangeTableEntry(final ChangeTable chg,
                          final boolean multiValued, final String name,
                          final boolean eventProperty,
                          final boolean todoProperty) {
    this.chg = chg;
    this.multiValued = multiValued;
    index = PropertyInfoIndex.UNKNOWN_PROPERTY;
    this.name = name;
    this.eventProperty = eventProperty;
    this.todoProperty = todoProperty;
  }

  /**
   * @param chg
   * @param index
   * @param eventProperty
   * @param todoProperty
   * @param freebusyProperty
   */
  public ChangeTableEntry(final ChangeTable chg,
                          final PropertyInfoIndex index, final boolean eventProperty,
                          final boolean todoProperty,
                          final boolean freebusyProperty) {
    this(chg, false, index, eventProperty, todoProperty, freebusyProperty);
  }

  /** Factory method
   *
   * @param multiValued
   * @param index
   * @return ChangeTableEntry
   */
  private static ChangeTableEntry EventTodoEntry(final boolean multiValued,
                                                final PropertyInfoIndex index) {
    return new ChangeTableEntry(null, multiValued, index, true, true, false);
  }

  /** Factory method
   *
   * @param multiValued
   * @param index
   * @return ChangeTableEntry
   */
  private static ChangeTableEntry EventTodoFbEntry(final boolean multiValued,
                                                  final PropertyInfoIndex index) {
    return new ChangeTableEntry(null, multiValued, index, true, true, true);
  }

  /** Factory method
   *
   * @param index
   * @return ChangeTableEntry
   */
  private static ChangeTableEntry EventTodoEntry(final PropertyInfoIndex index) {
    return new ChangeTableEntry(null, index, true, true, false);
  }

  /** Factory method
   *
   * @param index
   * @return ChangeTableEntry
   */
  private static ChangeTableEntry EventTodoFbEntry(final PropertyInfoIndex index) {
    return new ChangeTableEntry(null, index, true, true, true);
  }

  /** Return a new entry based on this entry.
   *
   * @param chg - the change table
   * @return ChangeTableEntry
   */
  public ChangeTableEntry newEntry(final ChangeTable chg) {
    ChangeTableEntry cte = new ChangeTableEntry(chg, multiValued, index,
                                                eventProperty, todoProperty,
                                                freebusyProperty);
    // TODO - fix for lists
    cte.listField = listField;

    return cte;
  }

  /** Return a new entry given the property name.
   *
   * @param chg - the change table
   * @param name
   * @return ChangeTableEntry
   */
  public static ChangeTableEntry newEntry(final ChangeTable chg,
                                          final String name) {
    ChangeTableEntry ent = map.get(name);
    if (ent == null) {
      return null;
    }
    return ent.newEntry(chg);
  }

  /** Add a value and mark as present.
   *
   * @param val
   */
  @SuppressWarnings("unchecked")
  public void addValue(final Object val) {
    if (!multiValued) {
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
    newValues.add(val);
  }

  /** Add a value and mark as present.
  *
   * @param val
   */
  @SuppressWarnings("unchecked")
  public void addValues(final Collection val) {
    if (!multiValued) {
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
  public boolean getMultiValued() {
    return multiValued;
  }

  /**
   * @return true for a multi-valued property
   */
  public PropertyInfoIndex getIndex() {
    return index;
  }

  /**
   * @return Name of the property
   */
  public String getName() {
    return name;
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
    return eventProperty;
  }

  /**
   * @return true if it's a todo property
   */
  public boolean getTodoProperty() {
    return todoProperty;
  }

  /**
   * @return true if it's a freebusy property
   */
  public boolean getFreebusyProperty() {
    return freebusyProperty;
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
    ts.append("name", name);
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

  private static void initMap() {
    /* ---------------------------- Single valued --------------- */

    /* Event, Todo and freebusy */

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.DTSTAMP));

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.DTSTART));

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.DURATION));

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.ORGANIZER));

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.UID));

    put(ChangeTableEntry.EventTodoFbEntry(PropertyInfoIndex.URL));

    /* Event and Todo */
    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.CLASS));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.CREATED));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.DESCRIPTION));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.GEO));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.LAST_MODIFIED));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.LOCATION));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.PRIORITY));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.RECURRENCE_ID));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.SEQUENCE));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.STATUS));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.SUMMARY));

    /* Event + fb */

    put(new ChangeTableEntry(null, PropertyInfoIndex.DTEND, true, false, true));

    /* Event only */

    put(new ChangeTableEntry(null, PropertyInfoIndex.TRANSP, true, false, false));

    /* Todo only */

    put(new ChangeTableEntry(null, PropertyInfoIndex.COMPLETED, false, true, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.DUE, false, true, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.PERCENT_COMPLETE, false, true, false));

    /* ---------------------------- Multi valued --------------- */

    // TODO - this should be dealt with by annotations.
    ChangeTableEntry xpropEntry = ChangeTableEntry.EventTodoFbEntry(true,
                                                                    PropertyInfoIndex.XPROP);
    xpropEntry.listField = true;
    put(xpropEntry);

    /* Event, Todo and Freebusy */

    put(ChangeTableEntry.EventTodoFbEntry(true, PropertyInfoIndex.ATTENDEE));

    put(ChangeTableEntry.EventTodoFbEntry(true, PropertyInfoIndex.COMMENT));

    put(ChangeTableEntry.EventTodoFbEntry(true, PropertyInfoIndex.CONTACT));

    put(ChangeTableEntry.EventTodoFbEntry(true, PropertyInfoIndex.REQUEST_STATUS));

    /* Event and Todo */

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.ATTACH));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.CATEGORIES));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.RELATED_TO));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.RESOURCES));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.VALARM));

    /* -------------Recurrence (also multi valued) --------------- */

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.EXDATE));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.EXRULE));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.RDATE));

    put(ChangeTableEntry.EventTodoEntry(true, PropertyInfoIndex.RRULE));

    /* -------------- Other non-event, non-todo ---------------- */

    put(new ChangeTableEntry(null, PropertyInfoIndex.FREEBUSY, false, false, true));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZID, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZNAME, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZOFFSETFROM, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZOFFSETTO, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TZURL, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.ACTION, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.REPEAT, false, false, false));

    put(new ChangeTableEntry(null, PropertyInfoIndex.TRIGGER, false, false, false));

    /* ----------------------------- Non-ical ---------------- */

    put(new ChangeTableEntry(null, PropertyInfoIndex.COLLECTION,
                             true, true, true));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.COST));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.DELETED));

    put(ChangeTableEntry.EventTodoEntry(PropertyInfoIndex.END_TYPE));
  }

  private static void put(final ChangeTableEntry ent) {
    map.put(ent.name, ent);
  }
}
