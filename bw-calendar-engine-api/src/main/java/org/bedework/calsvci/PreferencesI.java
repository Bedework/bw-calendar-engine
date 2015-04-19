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

import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;

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
  BwPreferences get() throws CalFacadeException;

  /** Returns the given user preferences.
   *
   * @param principal - representing a principal
   * @return BwPreferences   prefs for the given user
   * @throws CalFacadeException
   */
  BwPreferences get(BwPrincipal principal) throws CalFacadeException;

  /** Update the current user preferences.
   *
   * @param  val     BwPreferences prefs for the current user
   * @throws CalFacadeException
   */
  void update(BwPreferences val) throws CalFacadeException;

  /** delete a preferences object
   *
   * @param val
   * @throws CalFacadeException
   */
  void delete(BwPreferences val) throws CalFacadeException;

  /** Get the path to the attachments directory
   *
   * @return String path.
   * @throws CalFacadeException
   */
  String getAttachmentsPath() throws CalFacadeException;

  /** Set the path to the attachments directory
   *
   * @param val  String path.
   * @throws CalFacadeException
   */
  void setAttachmentsPath(String val) throws CalFacadeException;
}
