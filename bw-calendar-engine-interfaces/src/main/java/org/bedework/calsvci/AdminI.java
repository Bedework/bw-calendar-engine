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
package org.bedework.calsvci;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;

/** Methods defined here are for adminisitrative uses. They require superuser
 * access and support functions that involve crawling the system or usage
 * reporting.
 *
 * @author Mike Douglass
 *
 */
public interface AdminI extends Serializable {
  /** Obtain the next batch of children paths for the supplied path. A path of
   * null will return the system roots.
   *
   * @param parentPath
   * @param start start index in the batch - 0 for the first
   * @param count count of results we want
   * @return collection of String paths or null for no more
   * @throws CalFacadeException
   */
  public Collection<String> getChildCollections(String parentPath,
                                                int start,
                                                int count) throws CalFacadeException;

  /** Obtain the next batch of children names for the supplied path. A path of
   * null will return the system roots. These are the names of stored entities,
   * NOT the paths.
   *
   * @param parentPath
   * @param start start index in the batch - 0 for the first
   * @param count count of results we want
   * @return collection of String names or null for no more
   * @throws CalFacadeException
   */
  public Collection<String> getChildEntities(String parentPath,
                                             int start,
                                             int count) throws CalFacadeException;

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  /** Return all unexpired alarms before a given time. If time is 0 all
   * unexpired alarms will be retrieved.
   *
   * <p>Any cancelled alarms will be excluded from the result.
   *
   * <p>Typically the system will call this with a time set into the near future
   * and then queue up alarms that are near to triggering.
   *
   * @param triggerTime
   * @return Collection of unexpired alarms.
   * @throws CalFacadeException
   */
  public abstract Collection<BwAlarm> getUnexpiredAlarms(long triggerTime)
          throws CalFacadeException;

  /** Given an alarm return the associated event(s)
   *
   * @param alarm
   * @return an event.
   * @throws CalFacadeException
   */
  public abstract Collection<BwEvent> getEventsByAlarm(BwAlarm alarm)
          throws CalFacadeException;

}
