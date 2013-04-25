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
import org.bedework.calfacade.BwSystem;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Locale;

/** Interface for handling bedework system parameters.
 *
 * @author Mike Douglass
 *
 */
public interface SysparsI extends Serializable {
  /** Retrieve systemName supplied at init
   *
   * @return String name
   */
  public String getSystemName();

  /** Get the (possibly cached) system pars using name supplied at init
   *
   * @return BwSystem object
   * @throws CalFacadeException if not admin
   */
  public BwSystem get() throws CalFacadeException;

  /** Get the system pars given name - will update cache object if the name is
   * the current system name.
   *
   * @param name
   * @return BwSystem object
   * @throws CalFacadeException if not admin
   */
  public BwSystem get(String name) throws CalFacadeException;

  /** Update the system pars
   *
   * @param val BwSystem object
   * @throws CalFacadeException if not admin
   */
  public void update(BwSystem val) throws CalFacadeException;

  /** Get a name uniquely.identifying this system. This should take the form <br/>
   *   name@host
   * <br/>where<ul>
   * <li>name identifies the particular calendar system at the site</li>
   * <li>host part identifies the domain of the site.</li>..
   * </ul>
   *
   * @return String    globally unique system identifier.
   * @throws CalFacadeException
   */
  public String getSysid() throws CalFacadeException;

  /** Get the locales supported by bedework.
   *
   * @return Collection of locale
   * @throws CalFacadeException
   */
  public Collection<Locale> getSupportedLocales() throws CalFacadeException;

  /** Add a locale to the supported locales list.
   *
   * @param loc
   * @throws CalFacadeException
   */
  public void addLocale(Locale loc) throws CalFacadeException;

  /** Remove a locale from the supported locales list.
   *
   * @param loc
   * @throws CalFacadeException
   */
  public void removeLocale(Locale loc) throws CalFacadeException;

  /** Get the list of root accounts.
   *
   * @return Collection of String
   * @throws CalFacadeException
   */
  public Collection<String> getRootUsers() throws CalFacadeException;

  /** Add an account to the list of root accounts.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void addRootUser(String val) throws CalFacadeException;

  /** Remove an account from the list of root accounts.
   *
   * @param val
   * @throws CalFacadeException
   */
  public void removeRootUser(String val) throws CalFacadeException;

  /** See if this is a calendar super user
   *
   * @param val
   * @return boolean true for a super user
   * @throws CalFacadeException
   */
  public boolean isRootUser(BwPrincipal val) throws CalFacadeException;
}
