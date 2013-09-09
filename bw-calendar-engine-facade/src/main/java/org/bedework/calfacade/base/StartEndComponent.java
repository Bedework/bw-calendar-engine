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

/** This interface is implemented by entities which have a start and an end.
 *
 * <p>They may also have a duration, though it's meaning differs.
 *
 * <p>For event and todo entities we may have an end specified with a duration
 * or with a date or date/time value, or o end date at all.
 *
 * <p>We always calculate an end date internally, so we do not need to use the
 * duration until we render the object.
 *
 * @author Mike Douglass   douglm - bedework.edu
 *
 */
public interface StartEndComponent {
  /** No end or duration */
  public static final char endTypeNone = 'N';
  /** End specified with a date(time) */
  public static final char endTypeDate = 'E';
  /** Duration specified */
  public static final char endTypeDuration = 'D';

  /** Set the start time for the entity
   *
   *  @param  val   Event's start
   */
  public void setDtstart(BwDateTime val);

  /** Get the start time for the entity
   *
   *  @return The start
   */
  public BwDateTime getDtstart();

  /** Set the end or due date for the entity
   *
   *  @param  val   end
   */
  public void setDtend(BwDateTime val);

  /** Get the event's end
   *
   *  @return The event's end
   */
  public BwDateTime getDtend();

  /** Set the endType flag for an event or todo
   *
   *  @param  val    char endType
   */
  public void setEndType(char val);

  /** get the endType flag for an event or todo
   *
   *  @return char    end Type
   */
  public char getEndType();

  /** Set the duration for the entity if an event or todo, or the requested
   * duration for a free/busy object.
   *
   *  @param val   string duration
   */
  public void setDuration(String val);

  /** Get the duration for the entity if an event or todo, or the requested
   * duration for a free/busy object.
   *
   * @return the event's duration
   */
  public String getDuration();

  /** A todo may have no start/end. If so it always appears in the current day
   * until completed.
   *
   * @param val
   */
  public void setNoStart(Boolean val);

  /** A todo may have no start/end. If so it always appears in the current day
   * until completed.
   *
   * @return true for no start/end
   */
  public Boolean getNoStart();
}
