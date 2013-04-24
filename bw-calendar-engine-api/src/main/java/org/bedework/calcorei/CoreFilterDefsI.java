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

import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;
import java.util.Collection;

/** Interface for handling bedework filters.
 *
 * @author Mike Douglass
 *
 */
public interface CoreFilterDefsI extends Serializable {
  /** Validate and persist a new filter definition
   *
   * @param  val       filter definition
   * @param owner
   * @throws CalFacadeException for errrors including duplicate name
   */
  void save(BwFilterDef val,
            final BwPrincipal owner) throws CalFacadeException;

  /** Get a filter given the name
   *
   * @param  name     String internal name of filter
   * @param owner
   * @return BwFilter null for unknown filter
   * @throws CalFacadeException
   */
  BwFilterDef getFilterDef(String name,
                           final BwPrincipal owner) throws CalFacadeException;

  /** Get filter definitions to which this user has access
   *
   * @param owner
   * @return Collection     of BwCalSuiteWrapper
   * @throws CalFacadeException
   */
  Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal owner) throws CalFacadeException;

  /** Update a filter definition.
   *
   * @param  val        filter definition
   * @throws CalFacadeException for errors including duplicate name
   */
  void update(BwFilterDef val) throws CalFacadeException;

  /** Delete a filter given the name
   *
   * @param  name     String name of filter
   * @param owner
   * @throws CalFacadeException
   */
  void deleteFilterDef(String name,
                    final BwPrincipal owner) throws CalFacadeException;
}
