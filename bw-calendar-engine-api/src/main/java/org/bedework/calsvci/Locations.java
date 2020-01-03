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

import org.bedework.calfacade.BwLocation;
import org.bedework.util.misc.response.GetEntityResponse;

/** Interface which handles manipulation of location entities.
 *
 * <p>These are maintained in the database but completely cached by the indexer
 * and in memory.
 * There are methods to retrieve only the cached objects for the many cases where
 * speed is important.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 */
public interface Locations extends EventProperties<BwLocation> {
  /** Initialize the object
   *
   * @param adminCanEditAllPublic   True if administrators can edit all public entities
   */
  void init(boolean adminCanEditAllPublic);

  /** Uses named keys to locate the location. These are used to map a
   * String location value onto a location entity. This allows us to map
   * locations in e.g. an OrgSync event onto better specified bedework
   * entities.
   *
   * @param keyName name of key
   * @param keyVal the value
   * @return response with possible BwLocation object.
   */
  GetEntityResponse<BwLocation> fetchLocationByKey(String keyName,
                                                   String keyVal);

  /** Find the location given the combined address values.
   *
   * @param val - address, room, city, state, zip
   * @param persisted - true if we want the db copy
   * @return Location object
   */
  GetEntityResponse<BwLocation> fetchLocationByCombined(String val,
                                                        boolean persisted);
}

