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

package org.bedework.calfacade.base;

import org.bedework.calfacade.BwDateTime;

import java.io.Serializable;
import java.util.Set;

/**
 * @author Mike Douglass douglm - rpi.edu
 *
 */
public interface RecurrenceEntity extends Serializable {
  /**
   * @param val
   */
  public void setRecurring(Boolean val);

  /**
   * @return Boolean true if a recurring event - only relevant for master event.
   */
  public Boolean getRecurring();

  /** Set the recurrence id
  *
  *  @param val     recurrence id
  */
 public void setRecurrenceId(String val);

 /** Get the recurrence id
  *
  * @return the event's recurrence id
  */
 public String getRecurrenceId();

  /** XXX Wrong - it should be a list
   * @param val
   */
  public void setRrules(Set<String> val);

  /**
   * @return   Set of String
   */
  public Set<String> getRrules();

  /**
   * @param val
   */
  public void setExrules(Set<String> val);

  /**
   * @return   Set of String
   */
  public Set<String> getExrules();

  /**
   * @param val
   */
  public void setRdates(Set<BwDateTime> val);

  /**
   * @return   Set of String
   */
  public Set<BwDateTime> getRdates();

  /**
   * @param val
   */
  public void setExdates(Set<BwDateTime> val);

  /**
   * @return    Set of String
   */
  public Set<BwDateTime> getExdates();

  /* *
   * @param val
   * /
  public void setLatestDate(String val);

  /**
   * @return    String latest date
   * /
  //public String getLatestDate();*/

  /* ====================================================================
   *                   Helper methods
   * ==================================================================== */

  /**
   * @return true if there is any recurring element.
   */
  public boolean isRecurringEntity();

  /** if (getRecurring() == null) {
   *    return false;
   *    }
   *
   *    return getRecurring();
   * @return true if this is a recurring entity.
   */
  public boolean testRecurring();

  /** True if we have rrules
   *
   * @return boolean
   */
  public boolean hasRrules();

  /**
   * @param val
   */
  public void addRrule(String val);

  /** True if we have exrules
   *
   * @return boolean
   */
  public boolean hasExrules();

  /**
   * @param val
   */
  public void addExrule(String val);

  /** True if we have rdates
   *
   * @return boolean
   */
  public boolean hasRdates();

  /**
   * @param val
   */
  public void addRdate(BwDateTime val);

  /** True if we have exdates
   *
   * @return boolean
   */
  public boolean hasExdates();

  /**
   * @param val
   */
  public void addExdate(BwDateTime val);
}
