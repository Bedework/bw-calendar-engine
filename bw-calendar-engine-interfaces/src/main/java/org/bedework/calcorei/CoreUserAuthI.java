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

package org.bedework.calcorei;

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAuthUser;

import java.util.List;

/** Handle db interactions for user authorisation.
 *
 * @author Mike Douglass    douglm@rpi.edu
 * @version 1.0
 */
public interface CoreUserAuthI {
  /**
   * @param val auth user object
   * @throws CalFacadeException
   */
  void addAuthUser(BwAuthUser val) throws CalFacadeException;

  /**
   * @param href - principal href for the entry
   * @return auth user with preferences or null
   * @throws CalFacadeException
   */
  BwAuthUser getAuthUser(final String href) throws CalFacadeException;

  /**
   * @param val auth user object
   * @throws CalFacadeException
   */
  void updateAuthUser(BwAuthUser val) throws CalFacadeException;

  /**
   * @return list of all auth user entries
   * @throws CalFacadeException
   */
  List<BwAuthUser> getAll() throws CalFacadeException;
}
