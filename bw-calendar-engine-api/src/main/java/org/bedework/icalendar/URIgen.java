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

package org.bedework.icalendar;

import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.net.URI;

/** This interface defines methods used to return uris which will provide access
 * to entities located somewhere in the implementing calendar system.
 *
 * <p>For example, a call to getLocationURI(loc) might return something like<br/>
 *     "http://cal.myplace.edu/ucal/locations.do?id=1234"
 *
 * <p>Implementing classes will be used by services like the synch process
 * which needs to embed usable urls in the generated Icalendar objects.
 *
 * <p>In addition, there is a corresponding method which will return a
 * dummy object representing the referenced entity. This allows services,
 * when presented with such a uri, to retrieve the actual entity without a
 * web interaction.
 *
 * @author Mike Douglass   douglm@bedework.edu
 */
public interface URIgen extends Serializable {
  /** Get a uri for the location
   *
   * @param val
   * @return URI
   * @throws CalFacadeException
   */
  public URI getLocationURI(BwLocation val) throws CalFacadeException;

  /** Attempt to create a dummy object representing the given URI.
   * Throw an exception if this is not a URI representing a location.
   *
   * <p>No web or network interactions need take place. - some implementations
   * may choose to return a real object.
   *
   * @param val     URI referencing a single location
   * @return LocationVO object with id filled in
   * @throws CalFacadeException
   */
  public BwLocation getLocation(URI val) throws CalFacadeException;

  /** Get a uri for the sponsor
   *
   * @param val
   * @return URI
   * @throws CalFacadeException
   */
  public URI getSponsorURI(BwContact val) throws CalFacadeException;

  /** Attempt to create a dummy object representing the given URI.
   * Throw an exception if this is not a URI representing a sponsor.
   *
   * <p>No web or network interactions need take place. - some implementations
   * may choose to return a real object.
   *
   * @param val     URI referencing a single sponsor
   * @return SponsorVO object with id filled in
   * @throws CalFacadeException
   */
  public BwContact getSponsor(URI val) throws CalFacadeException;
}

