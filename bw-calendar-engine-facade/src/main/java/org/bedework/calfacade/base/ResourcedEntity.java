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

import org.bedework.calfacade.BwString;

import java.util.Set;

/** An entity that can have one or more resources will implement this interface.
 *
 * @author douglm
 */
public interface ResourcedEntity {

  /** Set the resources Set
   *
   * @param val    Set of (BwString)resources
   */
  public void setResources(Set<BwString> val);

  /** Get the resources
   *
   *  @return Set     resources set
   */
  public Set<BwString> getResources();

  /**
   * @return int number of resources.
   */
  public int getNumResources();

  /**
   * @param lang
   * @param val
   */
  public void addResource(String lang, String val);

  /**
   * @param val
   */
  public void addResource(BwString val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeResource(BwString val);
}
