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
package org.bedework.calsvc.client;

import org.bedework.caldav.util.filter.FilterBase;

import java.io.Serializable;

/** This class allows us to apply a color to an event in the result set.
 *
 * <p>If the filter is null then a match on the path is sufficient. Otherwise
 * an event must be passed through the filter to see if it matches.
 *
 * @author Mike Douglass
 *
 */
public class ClientCollectionInfo implements Serializable {
  private String path;

  private String color;

  private int calType;

  private FilterBase filter;

  /** Create an entry for a path
   *
   * @param path
   * @param color
   * @param calType
   * @param filter - null for unfiltered
   */
  public ClientCollectionInfo(String path, String color, int calType,
                              FilterBase filter) {
    this.path = path;
    this.color = color;
    this.calType = calType;
    this.filter = filter;
  }

  /** Get the path
   *
   * @return String   path
   */
  public String getPath() {
    return path;
  }

  /** Get the calendar color property
   *
   * @return String calendar color
   */
  public String getColor() {
    return color;
  }

  /** Get the type
   *
   *  @return int type
   */
  public int getCalType() {
    return calType;
  }

  /** Get the filter - null means an unfiltered reference
   *
   *  @return Filter to apply
   */
  public FilterBase getFilter() {
    return filter;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder("ClientCollectionInfo{");

    sb.append("path=");
    sb.append(getPath());
    sb.append(", color=");
    sb.append(getColor());
    sb.append(", calType=");
    sb.append(getCalType());
    sb.append(", filter=");
    sb.append(getFilter());

    sb.append("}");

    return sb.toString();
  }
}
