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

import java.util.Set;

/** An entity that can have one or more descriptions will implement this interface.
 *
 * @author douglm
 *
 * @param <T>
 */
public interface DescriptionEntity<T extends BwStringBase> {

  /** Set the descriptions Set
   *
   * @param val    Set of (BwString)descriptions
   */
  public void setDescriptions(Set<T> val);

  /** Get the descriptions
   *
   *  @return Set     descriptions set
   */
  public Set<T> getDescriptions();

  /**
   * @return int number of descriptions.
   */
  public int getNumDescriptions();

  /**
   * @param lang
   * @param val
   */
  public void addDescription(String lang, String val);

  /** If description with given lang is present updates the value, otherwise adds it.
   * @param lang
   * @param val
   */
  public void updateDescriptions(String lang, String val);

  /**
   * @param lang
   * @return BwString with language code or default
   */
  public T findDescription(String lang);

  /**
   * @param val  String default
   * @deprecated
   */
  public void setDescription(String val);

  /**
   * @return String default
   * @deprecated
   */
  public String getDescription();

  /**
   * @param val
   */
  public void addDescription(T val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeDescription(T val);
}
