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

import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwEvent;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;

import java.util.Collection;
import java.util.Iterator;

/** Interface which defines the database functions needed to dump the
 * calendar data. It is intended that the implementation might cache
 * objects. Calling each method in the order below should be the most
 * efficient.
 *
 * <p>These methods return dummy objects for references. For example,
 * an event location is represented by a Location object with only the
 * id filled in.
 *
 * <p>If the implementing class discovers a discrepency, e.g. a missing
 * user entry, it is up to the caller to determine that is the case.
 *
 * <p>Error messages should be emitted by the implementing classes.
 *
 * <p>Classes to dump in the order they must appear are<ul>
 * <li>BwSystem</li>
 * <li>BwUser</li>
 * <li>BwCalendar</li>
 * <li>BwLocation</li>
 * <li>BwSponsor</li>
 * <li>BwOrganizer</li>
 * <li>BwAttendee</li>
 * <li>BwCategory</li>
 * <li>BwAuthUser</li>
 * <li>BwAuthUserPrefs</li>
 * <li>BwEvent</li>
 * <li>BwEventAnnotation</li>
 * <li>BwAdminGroup</li>
 * <li>BwPreferences + BwView</li>
 * <li>BwCalSuite</li>
 *
 * <li>BwFilter</li>
 * <li>BwUserInfo</li>
 * </ul>
 *
 * @author Mike Douglass   douglm rpi.edu
 * @version 1.0
 */
public interface DumpIntf {
  /** Will return an Iterator returning AdminGroup objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwAdminGroup> getAdminGroups() throws CalFacadeException;

  /** Will return an Iterator returning AuthUser objects. Preferences will
   * be attached - user objects will also be attached.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwAuthUser> getAuthUsers() throws CalFacadeException;

  /** Will return an Iterator returning the top level BwCalendar objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwCalendar> getCalendars() throws CalFacadeException;

  /**
   * @param val - the collection
   * @return Children of val
   * @throws CalFacadeException
   */
  public Collection<BwCalendar> getChildren(BwCalendar val) throws CalFacadeException;

  /** Will return an Iterator returning BwCalSuite objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwCalSuite> getCalSuites() throws CalFacadeException;

  /** Will return an Iterator returning Category objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwCategory> getCategories() throws CalFacadeException;

  /** Will return an Iterator returning BwEvent objects.
   * All relevent objects, categories, locations, sponsors, creators will
   * be attached.
   *
   * @return Iterator - events may have overrides attached.
   * @throws CalFacadeException
   */
  public Iterator<BwEvent> getEvents() throws CalFacadeException;

  /** Will return an Iterator returning BwEvent objects.
   * All relevent objects, categories, locations, sponsors, creators will
   * be attached.
   *
   * <p>Overrides are not included
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwEventAnnotation> getEventAnnotations() throws CalFacadeException;

  /** Will return an Iterator returning Filter objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwFilterDef> getFilters() throws CalFacadeException;

  /** Will return an Iterator returning Location objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwLocation> getLocations() throws CalFacadeException;

  /** Will return an Iterator returning BwPreferences objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwPreferences> getPreferences() throws CalFacadeException;

  /** Will return an Iterator returning BwContact objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwContact> getContacts() throws CalFacadeException;

  /** Will return an Iterator returning principal objects.
   * Subscriptions will be included
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwPrincipal> getAllPrincipals() throws CalFacadeException;

  /** Will return an Iterator returning User resources.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator<BwResource> getResources() throws CalFacadeException;

  /** Will return the resource content for the given resource.
   *
   * @param res on return resource content object will be implanted
   * @throws CalFacadeException
   */
  public void getResourceContent(BwResource res) throws CalFacadeException;

  /** Will return an Iterator returning view objects.
   *
   * @return Iterator over entities
   * @throws CalFacadeException
   */
  public Iterator getViews() throws CalFacadeException;
}

