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

import org.bedework.calfacade.BwAttachment;

import java.util.Set;

/** An entity that can have one or more attachments will implement this interface.
 *
 * @author douglm
 */
public interface AttachmentsEntity {
  /** Set the attendees Set
   *
   * @param val    Set of attachments
   */
  public void setAttachments(Set<BwAttachment> val);

  /** Get the attendees
   *
   *  @return Set     attachments list
   */
  public Set<BwAttachment> getAttachments();

  /**
   * @return int number of attachments.
   */
  public int getNumAttachments();

  /**
   * @param val
   */
  public void addAttachment(BwAttachment val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeAttachment(BwAttachment val);

  /** Return a copy of the Set
   *
   * @return Set of BwAttachment
   */
  public Set<BwAttachment> copyAttachments();

  /** Return a clone of the Set
   *
   * @return Set of BwAttachment
   */
  public Set<BwAttachment> cloneAttachments();
}
