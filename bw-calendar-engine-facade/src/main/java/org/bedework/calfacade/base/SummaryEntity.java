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

/** An entity that can have one or more summaries will implement this interface.
 *
 * @author douglm
 */
public interface SummaryEntity {

  /** Set the summaries Set
   *
   * @param val    Set of (BwString)summaries
   */
  public void setSummaries(Set<BwString> val);

  /** Get the summaries
   *
   *  @return Set     summaries set
   */
  public Set<BwString> getSummaries();

  /**
   * @return int number of summaries.
   */
  public int getNumSummaries();

  /** If summary with given lang is present updates the value, otherwise adds it.
   * @param lang
   * @param val
   */
  public void updateSummaries(String lang, String val);

  /**
   * @param lang
   * @return BwString with language code or default
   */
  public BwString findSummary(String lang);

  /**
   * @param val  String default
   * @deprecated
   */
  @Deprecated
  public void setSummary(String val);

  /**
   * @return String default
   * @deprecated
   */
  @Deprecated
  public String getSummary();

  /**
   * @param val
   */
  public void addSummary(BwString val);

  /**
   * @param val
   * @return boolean true if removed.
   */
  public boolean removeSummary(BwString val);
}
