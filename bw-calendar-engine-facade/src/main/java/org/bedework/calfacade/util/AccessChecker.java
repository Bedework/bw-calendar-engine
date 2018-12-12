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
package org.bedework.calfacade.util;

import org.bedework.access.CurrentAccess;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.wrappers.CalendarWrapper;

import java.io.Serializable;

/** An access helper interface.
 *
 *
 * @author Mike Douglass   douglm  bedework.edu
 */
public interface AccessChecker extends Serializable {
  /** Check the access for the given entity. Returns the current access
   * or null or optionally throws a no access exception.
   *
   * @param ent to check
   * @param desiredAccess access we want
   * @param returnResult true for a result even if access denied
   * @return CurrentAccess
   * @throws CalFacadeException if returnResult false and no access
   */
  CurrentAccess checkAccess(BwShareableDbentity ent,
                            int desiredAccess,
                            boolean returnResult)
          throws CalFacadeException;

  /**
   * 
   * @param val to be checked
   * @return null if no access
   * @throws CalFacadeException
   */
  CalendarWrapper checkAccess(final BwCalendar val)
          throws CalFacadeException;

  /**
   *
   * @param val to be checked
   * @param desiredAccess access we want
   * @return null if no access
   * @throws CalFacadeException
   */
  CalendarWrapper checkAccess(final BwCalendar val,
                              int desiredAccess)
          throws CalFacadeException;

    AccessUtilI getAccessUtil();
}
