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

import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;

/** Interface for handling bedework filters.
 *
 * <p>A filter can be regarded as an extension of CalDAV filters and a subset
 * of bedework filters is indeed equivalent to those defined in the CalDAV rfc.
 *
 * <p>We can define a filter by creating an xml definition and having the create
 * filter method parse that definition and create a filter.
 *
 * <p>We can save the xml definition and also retrieve it and create the internal
 * form.
 *
 * @author Mike Douglass
 *
 */
public interface FiltersI extends Serializable {
  /** Parse an xml definition and create a filter
   *
   * @param  val       String xml filter definition
   * @return BwFilterDef object
   * @throws CalFacadeException
  public BwFilterDef parse(String val) throws CalFacadeException;
   */

  /** Parse the xml definition in the given filter object
   *
   * @param  val       BwFilterDef
   * @throws CalFacadeException
   */
  public void parse(BwFilterDef val) throws CalFacadeException;

  /** Validate a filter definition.
   *
   * @param  val       String xml filter definition
   * @throws CalFacadeException
   */
  public void validate(String val) throws CalFacadeException;

  /** Validate and persist a new filter definition
   *
   * @param  val       filter definition
   * @throws CalFacadeException for errrors including duplicate name
   */
  public void save(BwFilterDef val) throws CalFacadeException;

  /** Get a filter given the name
   *
   * @param  name     String internal name of filter
   * @return BwFilter null for unknown filter
   * @throws CalFacadeException
   */
  public BwFilterDef get(String name) throws CalFacadeException;

  /** Get filter definitions to which this user has access
   *
   * @return Collection     of BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  public Collection<BwFilterDef> getAll() throws CalFacadeException;

  /** Update a filter definition.
   *
   * @param  val        filter definition
   * @throws CalFacadeException for errors including duplicate name
   */
  public void update(BwFilterDef val) throws CalFacadeException;

  /** Delete a filter given the name
   *
   * @param  name     String name of filter
   * @throws CalFacadeException
   */
  public void delete(String name) throws CalFacadeException;
}
