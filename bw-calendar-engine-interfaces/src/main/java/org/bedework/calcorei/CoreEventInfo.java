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

package org.bedework.calcorei;

import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwEvent;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/** This class provides information about an event for a specific user and
 * session.
 *
 * @author Mike Douglass       douglm @ rpi.edu
 */
public class CoreEventInfo implements Comparable, Comparator, Serializable {
  protected BwEvent event;

  /* This object contains information giving the current users access rights to
   * the entity.
   */
  private CurrentAccess currentAccess;

  /** If the event is a master recurring event and we asked for the master +
   * overides or for fully expanded, this will hold all the overrides for that
   * event in the form of CoreEventInfo objects.
   */
  private Collection<CoreEventInfo> overrides;

  /** If the event is a master recurring event and we asked for a full
   * expansion, this will hold all the instances for that event in the form of
   * CoreEventInfo objects.
   */
  private Collection<CoreEventInfo> instances;

  /* Collection of EventInfo representing contained items. For
   * entityTypeVavailability this would be AVAILABLE components. For Vpoll it
   * will be candidates.
   */
  private Set<CoreEventInfo> containedItems;

  /** Constructor
   *
   */
  public CoreEventInfo() {
  }

  /** Constructor
   *
   * @param event
   * @param currentAccess
   */
  public CoreEventInfo(final BwEvent event,
                       final CurrentAccess currentAccess) {
    this.event = event;
    this.currentAccess = currentAccess;
  }

  /**
   * @param val
   */
  public void setEvent(final BwEvent val) {
    event = val;
  }

  /**
   * @return BwEvent associated with this object
   */
  public BwEvent getEvent() {
    return event;
  }

  /** Get the current users access rights.
   *
   * @return  CurrentAccess
   */
  public CurrentAccess getCurrentAccess() {
    return currentAccess;
  }

  /* ====================================================================
   *                   Overrides methods
   * ==================================================================== */

  /** Set the overrides collection
   *
   * @param val    Collection of overrides
   */
  public void setOverrides(final Collection<CoreEventInfo> val) {
    overrides = val;
  }

  /** Get the overrides
   *
   *  @return Collection     overrides list
   */
  public Collection<CoreEventInfo> getOverrides() {
    return overrides;
  }

  /**
   * @return int number of overrides.
   */
  public int getNumOverrides() {
    Collection as = getOverrides();
    if (as == null) {
      return 0;
    }

    return as.size();
  }

  /**
   * @param val
   */
  public void addOverride(final CoreEventInfo val) {
    Collection<CoreEventInfo> as = getOverrides();
    if (as == null) {
      as = new TreeSet<CoreEventInfo>();
      setOverrides(as);
    }

    if (!as.contains(val)) {
      as.add(val);
    }
  }

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeOverride(final CoreEventInfo val) {
    Collection<CoreEventInfo> as = getOverrides();
    if (as == null) {
      return false;
    }

    return as.remove(val);
  }

  /* ====================================================================
   *                   Instances methods
   * ==================================================================== */

  /** Set the instances collection
   *
   * @param val    Collection of instances
   */
  public void setInstances(final Collection<CoreEventInfo> val) {
    instances = val;
  }

  /** Get the instances
   *
   *  @return Collection     instances list
   */
  public Collection<CoreEventInfo> getInstances() {
    return instances;
  }

  /* ====================================================================
   *                   Contained item methods
   * ==================================================================== */

  /** set the contained items
   *
   * @param val     Collection    of CoreEventInfo
   */
  public void setContainedItems(final Set<CoreEventInfo> val) {
    containedItems = val;
  }

  /** Get the contained items
   *
   * @return Collection    of CoreEventInfo
   */
  public Set<CoreEventInfo> getContainedItems() {
    return containedItems;
  }

  /** Add a contained item
   *
   * @param val
   */
  public void addContainedItem(final CoreEventInfo val) {
    Set<CoreEventInfo> cis = getContainedItems();

    if (cis == null) {
      cis = new TreeSet<CoreEventInfo>();
      setContainedItems(cis);
    }

    cis.add(val);
  }

  /**
   * @return int number of contained items.
   */
  public int getNumContainedItems() {
    Set<CoreEventInfo> cis = getContainedItems();
    if (cis == null) {
      return 0;
    }

    return cis.size();
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compare(final Object o1, final Object o2) {
    if (!(o1 instanceof CoreEventInfo)) {
      return -1;
    }

    if (!(o2 instanceof CoreEventInfo)) {
      return 1;
    }

    if (o1 == o2) {
      return 0;
    }

    CoreEventInfo e1 = (CoreEventInfo)o1;
    CoreEventInfo e2 = (CoreEventInfo)o2;

    return e1.getEvent().compare(e1.getEvent(), e2.getEvent());
  }

  @Override
  public int compareTo(final Object o2) {
    return compare(this, o2);
  }

  @Override
  public int hashCode() {
    return getEvent().hashCode();
  }

  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    if (!(obj instanceof CoreEventInfo)) {
      return false;
    }

    return compareTo(obj) == 0;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();

    sb.append("EventInfo{eventid=");

    if (getEvent() == null) {
      sb.append("UNKNOWN");
    } else {
      sb.append(getEvent().getId());
    }

    return sb.toString();
  }
}
