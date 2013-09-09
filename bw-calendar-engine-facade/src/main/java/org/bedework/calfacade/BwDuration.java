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

import org.bedework.calfacade.exc.CalFacadeException;

import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.property.Duration;

import java.io.Serializable;

/** Class representing a duration.
 *
 * @author Mike Douglass   douglm@bedework.edu
 *  @version 1.0
 */
public class BwDuration implements Serializable {
  private int days;
  private int hours;
  private int minutes;
  private int seconds;

  /** The above or this
   */
  private int weeks;

  /** A duration can be negative, e.g. so many hours before an event, for alarms,
   * or positive, e.g. an event lasts x days.
   */
  private boolean negative = false;

  /** Constructor
   *
   */
  public BwDuration() {
  }

  /** Set the days
   *
   * @param val    int days
   */
  public void setDays(final int val) {
    days = val;
  }

  /** Get the days
   *
   * @return int    the days
   */
  public int getDays() {
    return days;
  }

  /** Set the hours
   *
   * @param val    int hours
   */
  public void setHours(final int val) {
    hours = val;
  }

  /** Get the hours
   *
   * @return int    the hours
   */
  public int getHours() {
    return hours;
  }

  /** Set the minutes
   *
   * @param val    int minutes
   */
  public void setMinutes(final int val) {
    minutes = val;
  }

  /** Get the minutes
   *
   * @return int    the minutes
   */
  public int getMinutes() {
    return minutes;
  }

  /** Set the seconds
   *
   * @param val    int seconds
   */
  public void setSeconds(final int val) {
    seconds = val;
  }

  /** Get the seconds
   *
   * @return int    the seconds
   */
  public int getSeconds() {
    return seconds;
  }

  /** Set the weeks
   *
   * @param val    int weeks
   */
  public void setWeeks(final int val) {
    weeks = val;
  }

  /** Get the weeks
   *
   * @return int    the weeks
   */
  public int getWeeks() {
    return weeks;
  }

  /** Flag a negative duration
   *
   * @param val    boolean negative
   */
  public void setNegative(final boolean val) {
    negative = val;
  }

  /** Get the negative flag
   *
   * @return boolean    the negative
   */
  public boolean getNegative() {
    return negative;
  }

  /* ====================================================================
   *                        Convenience methods
   * ==================================================================== */

  /** Return a BwDuration populated from the given String value.
   *
   * @param val    String
   * @return BwDuration
   * @throws CalFacadeException
   */
  public static BwDuration makeDuration(final String val) throws CalFacadeException {
    BwDuration db = new BwDuration();

    populate(db, val);

    return db;
  }

  /** Populate the bean from the given String value.
   *
   * @param db    BwDuration
   * @param val   String value
   * @throws CalFacadeException
   */
  public static void populate(final BwDuration db, final String val) throws CalFacadeException {
    try {
      if (val == null) {
        return;
      }

      Dur d = new Dur(val);

      if (d.getWeeks() != 0) {
        db.setWeeks(d.getWeeks());

        return;
      }

      db.setDays(d.getDays());
      db.setHours(d.getHours());
      db.setMinutes(d.getMinutes());
      db.setSeconds(d.getSeconds());
      db.setNegative(d.isNegative());
    } catch (Throwable t) {
      throw new CalFacadeException("Invalid duration");
    }
  }

  /** Make an ical Duration
   *
   * @return Duration
   */
  public Duration makeDuration() {
    Dur d;

    if (weeks != 0) {
      d = new Dur(getWeeks());
    } else {
      d = new Dur(getDays(), getHours(), getMinutes(), getSeconds());
    }

    return new Duration(d);
  }

  /** Return true if this represents a zero duration
   *
   * @return boolean
   */
  public boolean isZero() {
    if (getWeeks() != 0) {
      return false;
    }

    return ((getDays() == 0) &&
            (getHours() == 0) &&
            (getMinutes() == 0) &&
            (getSeconds() == 0));
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();

    if (negative) {
      sb.append("-");
    }

    sb.append("P");

    if (getWeeks() != 0) {
      sb.append(getWeeks());
      sb.append("W");
    } else {
      if (getDays() != 0) {
        sb.append(getDays());
        sb.append("D");
      }

      boolean addedT = false;

      addedT = addTimeComponent(sb, getHours(), "H", addedT);
      addedT = addTimeComponent(sb, getMinutes(), "M", addedT);
      addedT = addTimeComponent(sb, getSeconds(), "S", addedT);
    }

    return sb.toString();
  }

  private boolean addTimeComponent(final StringBuffer sb, final int val, final String flag, boolean addedT) {
    if (val == 0) {
      return addedT;
    }

    if (!addedT) {
      sb.append("T");
      addedT = true;
    }

    sb.append(val);
    sb.append(flag);

    return addedT;
  }
}

