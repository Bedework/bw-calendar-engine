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

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;
import java.util.Locale;

/** Interface for handling bedework user preferences.
 *
 * @author Mike Douglass
 *
 */
public interface PreferencesI extends Serializable {
  /** Returns the current user preferences.
   *
   * @return BwPreferences   prefs for the current user
   * @throws CalFacadeException
   */
  public BwPreferences get() throws CalFacadeException;

  /** Returns the given user preferences.
   *
   * @param principal - representing a principal
   * @return BwPreferences   prefs for the given user
   * @throws CalFacadeException
   */
  public BwPreferences get(BwPrincipal principal) throws CalFacadeException;

  /** Update the current user preferences.
   *
   * @param  val     BwPreferences prefs for the current user
   * @throws CalFacadeException
   */
  public void update(BwPreferences val) throws CalFacadeException;

  /** delete a preferences object
   *
   * @param val
   * @throws CalFacadeException
   */
  public void delete(BwPreferences val) throws CalFacadeException;

  /** Update preferred entity list for admin user.
   *
   * @param remove - true if removing object
   * @param ent
   * @throws CalFacadeException
   */
  public abstract void updateAdminPrefs(boolean remove,
                                        BwEventProperty ent) throws CalFacadeException;

  /** Update preferred entity list for admin user.
   *
   * @param remove - true if removing object
   * @param cal
   * @param cat
   * @param loc
   * @param contact
   * @throws CalFacadeException
   */
  public abstract void updateAdminPrefs(boolean remove,
                                        BwCalendar cal,
                                        BwCategory cat,
                                        BwLocation loc,
                                        BwContact contact) throws CalFacadeException;

  /** Given a (possibly null) list of locales, and/or an explicitly requested locale,
   * figure out what locale to use based on user preferences and system defaults.
   * The order of preference is<ol>
   * <li>Explicitly requested.</li>
   * <li>User preference</li>
   * <li>If supported - last locale set for user</li>
   * <li>Matching entry from supplied list - see below</li>
   * <li>System default</li>
   * </ol>
   *
   * <p>The locale parameter is a possibly null explicily requested locale.
   *
   * <p>Matching locales is first attempted using Locale equality. If that fails
   * we try to match languages on the assumption that somebody requesting fr_FR
   * is likely to be happier with fr_CA than en_US.
   *
   * @param locales
   * @param locale
   * @return Collection of locales.
   * @throws CalFacadeException
   */
  public Locale getUserLocale(Collection<Locale> locales,
                              Locale locale) throws CalFacadeException;

  /** Get the path to the attachments directory
   *
   * @return String path.
   * @throws CalFacadeException
   */
  public String getAttachmentsPath() throws CalFacadeException;

  /** Set the path to the attachments directory
   *
   * @param val  String path.
   * @throws CalFacadeException
   */
  public void setAttachmentsPath(String val) throws CalFacadeException;
}
