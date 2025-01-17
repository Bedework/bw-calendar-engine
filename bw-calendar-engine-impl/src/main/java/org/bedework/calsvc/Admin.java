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
package org.bedework.calsvc;

import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.BwEvent;
import org.bedework.calsvci.AdminI;

import java.util.Collection;

/** The Admin methods for bedework.
 *
 * @author douglm
 *
 */
public class Admin extends CalSvcDb implements AdminI {
  /** Constructor
  *
  * @param svci for interactions.
  */
 Admin(final CalSvc svci) {
   super(svci);
 }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                                final int start,
                                                final int count) {
    return getCal().getChildCollections(parentPath, start, count);
  }

  @Override
  public Collection<String> getChildEntities(final String parentPath,
                                             final int start,
                                             final int count) {
    return getCal().getChildEntities(parentPath, start, count);
  }

  /* ====================================================================
   *                   Alarms
   * ==================================================================== */

  @Override
  public Collection<BwAlarm> getUnexpiredAlarms(final long triggerTime) {
    return getCal().getUnexpiredAlarms(triggerTime);
  }

  @Override
  public Collection<BwEvent> getEventsByAlarm(final BwAlarm alarm) {
    return getCal().getEventsByAlarm(alarm);
  }
}
