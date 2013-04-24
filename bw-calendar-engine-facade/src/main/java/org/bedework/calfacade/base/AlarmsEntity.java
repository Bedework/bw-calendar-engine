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

import org.bedework.calfacade.BwAlarm;

import java.util.Set;

/** An entity that can have one or more alarms will implement this interface.
 *
 * @author douglm
 */
public interface AlarmsEntity {
  /** Set the attendees Set
   *
   * @param val    Set of alarms
   */
  public void setAlarms(Set<BwAlarm> val);

  /** Get the attendees
   *
   *  @return Set     alarms list
   */
  public Set<BwAlarm> getAlarms();

  /**
   * @return int number of alarms.
   */
  public int getNumAlarms();

  /**
   * @param val
   */
  public void addAlarm(BwAlarm val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeAlarm(BwAlarm val);

  /** Return a clone of the Set
   *
   * @return Set of BwAlarm
   */
  public Set<BwAlarm> cloneAlarms();
}
