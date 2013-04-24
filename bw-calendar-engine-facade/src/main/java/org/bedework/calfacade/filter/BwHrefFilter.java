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
package org.bedework.calfacade.filter;

import org.bedework.caldav.util.filter.ObjectFilter;

import edu.rpi.cmt.calendar.PropertyIndex.PropertyInfoIndex;

/** A filter that selects events that have a given href
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class BwHrefFilter extends ObjectFilter<String> {
  /** Match on an event href.
   *
   * @param name - null one will be created
   * @param propertyIndex
   */
  public BwHrefFilter(final String name, final PropertyInfoIndex propertyIndex) {
    super(name, propertyIndex);
  }

  /**
   * @return the name part of the href - that bit after the last "/"
   */
  public String getNamePart() {
    String s = getEntity();

    s = s.substring(s.lastIndexOf("/") + 1);

    /* Remove any appended recurrenceid */

    int pos = s.lastIndexOf("#");

    if (pos < 0) {
      return s;
    }

    return s.substring(0, pos);
  }

  /**
   * @return the path part of the href - that bit before the last "/"
   */
  public String getPathPart() {
    String s = getEntity();

    return s.substring(0, s.lastIndexOf("/"));
  }

  /**
   * @return the recurrenceid part of the href - that bit after the last "#"
   */
  public String getRecurrencePart() {
    String s = getEntity();

    int hpos = s.lastIndexOf("#");

    if (hpos < 0) {
      return null;
    }

    int spos = s.lastIndexOf("/");

    if (hpos < spos) {
      return null;
    }

    return s.substring(hpos + 1);
  }
}
