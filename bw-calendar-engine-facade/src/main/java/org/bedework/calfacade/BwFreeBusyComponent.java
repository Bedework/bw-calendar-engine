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

import org.bedework.calfacade.base.BwDbentity;

import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Dur;
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

/** Class representing a free busy time component. Used in icalendar objects
 *
 * @author Mike Douglass   douglm@bedework.edu
 *  @version 1.0
 */
public class BwFreeBusyComponent extends BwDbentity {
  /** busy time - default */
  public static final int typeBusy = 0;

  /** free time */
  public static final int typeFree = 1;

  /** unavailable time */
  public static final int typeBusyUnavailable = 2;

  /** tentative busy time */
  public static final int typeBusyTentative = 3;

  private int type = typeBusy;

  /** */
  public static final String[] fbtypes = {"BUSY",
                                          "FREE",
                                          "BUSY-UNAVAILABLE",
                                          "BUSY-TENTATIVE"};

  /** True if each period is start + duration. False for start + end
   */
  public static final boolean emitDurations = true;

  private String value;

  /** Collection of Period
   */
  private Collection<Period> periods;

  /** Constructor
   *
   */
  public BwFreeBusyComponent() {
  }

  /**
   * @param val
   */
  public void setType(final int val) {
    type = val;
  }

  /**
   * @return int type of time
   */
  public int getType() {
    return type;
  }

  /**
   * @param val
   */
  public void setValue(final String val) {
    value = val;
    periods = null;
  }

  /**
   * @return String representation of list
   */
  public String getValue() {
    if (value == null) {
      if ((periods == null) || periods.isEmpty()) {
        return null;
      }

      PeriodList pl = new PeriodList();

      for (Period p: getPeriods()) {
        pl.add(p);
      }

      value = pl.toString();
    }

    return value;
  }

  /** Get the free busy periods
   *
   * @return Collection    of Period
   */
  public Collection<Period> getPeriods() {
      if (periods == null) {
        periods = new TreeSet<>();

        if (getValue() != null) {
          final PeriodList pl;
          try {
            pl = new PeriodList(getValue());
          } catch (Throwable t) {
            throw new RuntimeException(t);
          }
          Iterator perit = pl.iterator();

          while (perit.hasNext()) {
            Period per = (Period)perit.next();

            periods.add(per);
          }
        }
      }
      return periods;
  }

  /* ====================================================================
   *                   Convenience methods
   * ==================================================================== */

  /** Merge in a period
   *
   * @param val
   */
  public void addPeriod(final Period val) {
    getPeriods().add(val);
    value = null;
  }

  /** Merge in a period
   *
   * @param start ical4j DateTime
   * @param end ical4j DateTime
   */
  public void addPeriod(final DateTime start, final DateTime end) {
    Period p;

    if (emitDurations) {
      p = new Period(start, new Dur(start, end));
    } else {
      p = new Period(start, end);
    }

    addPeriod(p);
  }

  /**
   * @return boolean true for empty
   */
  public boolean getEmpty() {
    return (getPeriods().size() == 0);
  }

  /**
   * @return rfc5545 value
   */
  public String getTypeVal() {
    return fbtypes[getType()];
  }

  /* ====================================================================
   *                        Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();

    sb.append("BwFreeBusyComponent{type=");
    sb.append(getTypeVal());
    sb.append(", ");

    try {
      for (Period p: getPeriods()) {
        sb.append(", (");
        sb.append(p.toString());
        sb.append(")");
      }
    } catch (Throwable t) {
      sb.append("Exception(");
      sb.append(t.getMessage());
      sb.append(")");
    }
    sb.append("}");

    return sb.toString();
  }

  @Override
  public Object clone() {
    BwFreeBusyComponent fbc = new BwFreeBusyComponent();

    try {
      fbc.setType(getType());
      fbc.setValue(getValue());
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }

    return fbc;
  }
}
