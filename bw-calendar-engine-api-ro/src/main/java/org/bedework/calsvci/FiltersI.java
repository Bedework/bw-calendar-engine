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
import org.bedework.calfacade.filter.SimpleFilterParser.ParseResult;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.responses.GetFilterDefResponse;

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
  /** Parse the xml definition in the given filter object
   *
   * @param  val       BwFilterDef
   * @return result of parsing - check the ok flag.
   */
  ParseResult parse(BwFilterDef val);

  /** Validate a filter definition.
   *
   * @param  val       String xml filter definition
   * @throws CalFacadeException
   */
  void validate(String val) throws CalFacadeException;

  /** Validate and persist a new filter definition
   *
   * @param  val       filter definition
   * @throws CalFacadeException for errrors including duplicate name
   */
  void save(BwFilterDef val) throws CalFacadeException;

  /** Get a filter given the name
   *
   * @param  name     String internal name of filter
   * @return GetFilterDefResponse with status set
   */
  GetFilterDefResponse get(String name);

  /** Get filter definitions to which this user has access
   *
   * @return Collection     of BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  Collection<BwFilterDef> getAll() throws CalFacadeException;

  /** Update a filter definition.
   *
   * @param  val        filter definition
   * @throws CalFacadeException for errors including duplicate name
   */
  void update(BwFilterDef val) throws CalFacadeException;

  /** Delete a filter given the name
   *
   * @param  name     String name of filter
   * @throws CalFacadeException
   */
  void delete(String name) throws CalFacadeException;

  /** Parse the sort expression
   *
   * @param  val  String sort expression
   * @return result - check the status
   */
  ParseResult parseSort(String val);

  /** Reindex current users entities
   *
   * @param indexer to use for this operation
   * @return number of entities reindexed
   * @throws CalFacadeException on fatal error
   */
  int reindex(BwIndexer indexer) throws CalFacadeException;
}
