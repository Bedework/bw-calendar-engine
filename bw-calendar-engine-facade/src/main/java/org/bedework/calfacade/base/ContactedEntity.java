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

import org.bedework.calfacade.BwContact;

import java.util.Set;

/** An entity that can have one or more contacts will implement this interface.
 *
 * @author douglm
 */
public interface ContactedEntity {
  /** Set the contacts Set
   *
   * @param val    Set of contacts
   */
  public void setContacts(Set<BwContact> val);

  /** Get the contacts
   *
   *  @return Set of contacts
   */
  public Set<BwContact> getContacts();

  /**
   * @return int number of contacts.
   */
  public int getNumContacts();

  /**
   * @param val
   */
  public void addContact(BwContact val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeContact(BwContact val);

  /** Check the contacts for the same entity
   *
   * @param val        Contact to test
   * @return boolean   true if the event has a particular contact
   */
  public boolean hasContact(BwContact val);

  /** Return a copy of the Set
   *
   * @return Set of BwContact
   */
  public Set<BwContact> copyContacts();

  /** Return a clone of the Set
   *
   * @return Set of BwContact
   */
  public Set<BwContact> cloneContacts();
}
