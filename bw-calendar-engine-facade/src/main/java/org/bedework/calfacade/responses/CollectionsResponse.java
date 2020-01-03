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
package org.bedework.calfacade.responses;

import org.bedework.calfacade.BwCalendar;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.response.Response;

/** Container for fetching collections, e.g. calendars, virtual collections etc.
 *
 * @author Mike Douglass douglm - spherical cow
 */
public class CollectionsResponse extends Response {
  private BwCalendar collections;
  private BwCalendar publicCollections;
  private BwCalendar userCollections;

  /**
   *
   * @param val root of collections
   */
  public void setCollections(final BwCalendar val) {
    collections = val;
  }

  /**
   * @return root of collections
   */
  public BwCalendar getCollections() {
    return collections;
  }

  /**
   *
   * @param val root of public collections
   */
  public void setPublicCollections(final BwCalendar val) {
    publicCollections = val;
  }

  /**
   * @return root of public collections
   */
  public BwCalendar getPublicCollections() {
    return publicCollections;
  }

  /**
   *
   * @param val root of user collections
   */
  public void setUserCollections(final BwCalendar val) {
    userCollections = val;
  }

  /**
   * @return root of user collections
   */
  public BwCalendar getUserCollections() {
    return userCollections;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("collections", getCollections())
      .append("publicCollections", getPublicCollections())
      .append("userCollections", getUserCollections());
  }
}
