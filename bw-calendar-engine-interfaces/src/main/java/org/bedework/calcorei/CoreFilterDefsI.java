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
   * @param owner of the filter
   */
  void add(BwFilterDef val,
           BwPrincipal<?> owner);

  /** Get a filter given the name
   *
   * @param  name     String internal name of filter
   * @param owner of the filter
   * @return BwFilter null for unknown filter
   */
  BwFilterDef getFilterDef(String name,
                           BwPrincipal<?> owner);

  /** Get filter definitions to which this user has access
   *
   * @param owner of the filter
   * @return Collection     of BwCalSuiteWrapper
   */
  Collection<BwFilterDef> getAllFilterDefs(BwPrincipal<?> owner);

  /** Update a filter definition.
   *
   * @param  val        filter definition
   */
  void update(BwFilterDef val);

  /** Delete a filter given the name
   *
   * @param  name     String name of filter
   * @param owner of the filter
   */
  void deleteFilterDef(String name,
                       BwPrincipal<?> owner);
}
