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

package org.bedework.calfacade;

import org.bedework.calfacade.annotations.Dump;

import edu.rpi.sss.util.ToString;

import java.util.List;

/** An Event Annotation in Bedework
 *
 *  @author Mike Douglass
 *  @version 1.0
 */
@Dump(elementName="event", keyFields={"colPath", "uid", "recurrenceId"},
      firstFields = {"ownerHref"})
public class BwEventAnnotation extends BwEvent {
  /** The event this one annotates. (which may itself be an annotation)
   */
  private BwEvent target;

  private BwEvent master;

  private Boolean override;

  /* A sequence of 'T' or 'F'
   *
   * This SHOULD be a char[] value. However, hibernate has a long standing bug
   * which uses equals() to compare teh old/new values resulting in a dirty field
   * even when no update has taken place.
   */
  private String emptyFlags;

  private static final int flagsLen = ProxiedFieldIndex.values().length;

  private static final char[] initCflags = new char[flagsLen];

  static {
    for (int i = 0; i < flagsLen; i++) {
      initCflags[i] = 'F';
    }
  }

  /** Constructor
   */
  public BwEventAnnotation() {
    super();
  }

  /* ====================================================================
   *                      Temp overrides
   * The following is here because we don't have these columns in the
   * schema. We'll save them in the x-props but need to move them out at a
   * later stage.
   * ==================================================================== */

  @Override
  public void setOrganizerSchedulingObject(final Boolean val) {
    List<BwXproperty> props = getXproperties(BwXproperty.bedeworkOrganizerSchedulingObject);
    BwXproperty xp = null;
    if ((props != null) && (props.size() > 0)) {
      xp = props.get(0);
    }

    if (xp != null) {
      xp.setValue(String.valueOf(val));
    } else {
      addXproperty(new BwXproperty(BwXproperty.bedeworkOrganizerSchedulingObject,
                                   null,
                                   String.valueOf(val)));
    }
  }

  @Override
  public Boolean getOrganizerSchedulingObject() {
    return Boolean.valueOf(getXproperty(BwXproperty.bedeworkOrganizerSchedulingObject));
  }

  @Override
  public void setAttendeeSchedulingObject(final Boolean val) {
    List<BwXproperty> props = getXproperties(BwXproperty.bedeworkAttendeeSchedulingObject);
    BwXproperty xp = null;
    if ((props != null) && (props.size() > 0)) {
      xp = props.get(0);
    }

    if (xp != null) {
      xp.setValue(String.valueOf(val));
    } else {
      addXproperty(new BwXproperty(BwXproperty.bedeworkAttendeeSchedulingObject,
                                   null,
                                   String.valueOf(val)));
    }
  }

  @Override
  public Boolean getAttendeeSchedulingObject() {
    return Boolean.valueOf(getXproperty(BwXproperty.bedeworkAttendeeSchedulingObject));
  }

  /* ====================================================================
   *                      Bean methods
   * ==================================================================== */

  /**
   * @param val
   */
  public void setTarget(final BwEvent val) {
    target = val;
  }

  /**
   * @return BwEvent target of this reference
   */
  public BwEvent getTarget() {
    return target;
  }

  /** The ultimate master event. This is always a real event. For
   * recurring events it is the master event - for non recurring it is
   * the unannotated original event.
   *
   * <p>This allows us to do a single fetch of all related annotations
   *
   * @param val
   */
  public void setMaster(final BwEvent val) {
    master = val;
  }

  /**
   * @return BwEvent master for this reference
   */
  public BwEvent getMaster() {
    return master;
  }

  /** Set the override flag. True if ths is an override for a recurring event,
   * otherwise it is an annotation to an entity or an entity instance
   *
   *  @param val    Boolean true if the event is deleted
   */
  public void setOverride(final Boolean val) {
    override = val;
  }

  /** Get the override flag
   *
   *  @return Boolean    true if this is an override
   */
  public Boolean getOverride() {
    return override;
  }

  /** Test the override flag
   *
   *  @return boolean    true if this is an override
   */
  public boolean testOverride() {
    return (getOverride() != null) && getOverride();
  }

  /** Set the empty flags
   *
   *  @param val     String set of empty flags
   */
  public void setEmptyFlags(final String val) {
    emptyFlags = val;
  }

  /** Get empty flags
   *
   *  @return String
   */
  public String getEmptyFlags() {
    return emptyFlags;
  }

  /** Set an empty value flag
   *
   *  @param fieldIndex    index of field we are interested in
   *  @param val           true if the field is empty
   */
  public void setEmptyFlag(final ProxiedFieldIndex fieldIndex, final boolean val) {
    char[] fs = null;

    if (getEmptyFlags() != null) {
      fs = getEmptyFlags().toCharArray();
    }

    if (fs == null) {
      fs = initCflags.clone();
    } else if (fs.length < flagsLen) {
      char[] newFs = initCflags.clone();
      for (int i = 0; i < fs.length; i++) {
        newFs[i] = fs[i];
      }
      fs = newFs;
    }

    int fsi = fieldIndex.ordinal();
    char newF;
    if (val) {
      newF = 'T';
    } else {
      newF = 'F';
    }

    if (fs[fsi] != newF) {
      fs[fsi] = newF;
    }

    setEmptyFlags(new String(fs));
  }

  /** Get empty flag
   *
   *  @param fieldIndex    index of field we are interested in
   *  @return boolean    true if empty
   */
  public Boolean getEmptyFlag(final ProxiedFieldIndex fieldIndex) {
    char[] fs = null;

    if (getEmptyFlags() != null) {
      fs = getEmptyFlags().toCharArray();
    }

    if (fs == null) {
      return false;
    }

    int fsi = fieldIndex.ordinal();
    if (fs.length <= fsi) {
      return false;
    }

    return fs[fsi] == 'T';
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);
    ts.append("target", getTarget().getId());
    ts.append("master", getMaster().getId());
    ts.append("override", getOverride());

    return ts.toString();
  }

  @Override
  public Object clone() {
    BwEventAnnotation ev = new BwEventAnnotation();

    copyTo(ev);
    ev.setTarget(getTarget());
    ev.setMaster(getMaster());

    return ev;
  }
}
