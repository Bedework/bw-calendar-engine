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

import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.List;

/** Interface for handling bedework user objects.
 *
 * @author Mike Douglass
 *
 */
public interface UsersI extends Serializable {
  /** Find the user with the given account name.
   *
   * @param val           String user id
   * @return User principal or null if not there
   */
  public BwPrincipal getUser(final String val);

  /** Find the user with the given account name. Create if not there.
   *
   * @param val           String user id
   * @return BwUser       representing the user
   * @throws CalFacadeException
   */
  public BwPrincipal getAlways(String val) throws CalFacadeException;

  /** Find the principal with the given account path.
   *
   * @param val           String principal hierarchy path
   * @return BwPrincipal  representing the principal or null if not there
   */
  public BwPrincipal getPrincipal(final String val);

  /** Add an entry for the user.
   *
   * @param account
   * @throws CalFacadeException
   */
  public void add(String account) throws CalFacadeException;

  /** Update a principal.
   *
   * @param principal
   * @throws CalFacadeException
   */
  public void update(BwPrincipal principal) throws CalFacadeException;

  /** Remove a principal. This will delete all traces of the principal from the system.
   *
   * @param pr
   * @throws CalFacadeException
   */
  public void remove(BwPrincipal pr) throws CalFacadeException;

  /** Can be called after init to flag the arrival of a user.
   *
   * @param val       principal logging on
   * @throws CalFacadeException
   */
  public void logon(BwPrincipal val) throws CalFacadeException;

  /** Set up collections and principal home.
   *
   * @param principal to init
   * @throws RuntimeException on fatal error
   */
  public void initPrincipal(BwPrincipal principal);

  /**
   * @return public entity owner
   */
  public BwPrincipal getPublicUser();

  /** Get a partial list of principal hrefs.
   *
   * @param start         Position to start
   * @param count         Number we want
   * @return list of hrefs - null for no more
   * @throws CalFacadeException
   */
  List<String> getPrincipalHrefs(int start,
                                 int count) throws CalFacadeException;
}
