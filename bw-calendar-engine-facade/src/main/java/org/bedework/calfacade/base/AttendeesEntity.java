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

import org.bedework.calfacade.BwAttendee;

import java.util.Set;

/** An entity that can have one or more attendees will implement this interface.
 *
 * <p>Where we have attendees we have recipients.
 *
 * @author Mike Douglass
 */
public interface AttendeesEntity {
  /** Set the attendees Set
   *
   * @param val    Set of attendees
   */
  public void setAttendees(Set<BwAttendee> val);

  /** Get the attendees
   *
   *  @return Set     attendees list
   */
  public Set<BwAttendee> getAttendees();

  /**
   * @return int number of attendees.
   */
  public int getNumAttendees();

  /**
   * @param val
   */
  public void addAttendee(BwAttendee val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeAttendee(BwAttendee val);

  /** Return a copy of the Set
   *
   * @return Set of BwAttendee
   */
  public Set<BwAttendee> copyAttendees();

  /** Return a clone of the Set
   *
   * @return Set of BwAttendee
   */
  public Set<BwAttendee> cloneAttendees();

  /** Set the recipients Set
   *
   * @param val    Set of (String)recipients
   */
  public void setRecipients(Set<String> val);

  /** Get the recipients
   *
   *  @return Set     recipients set
   */
  public Set<String> getRecipients();

  /**
   * @return int number of recipients.
   */
  public int getNumRecipients();

  /**
   * @param val
   */
  public void addRecipient(String val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeRecipient(String val);
}
