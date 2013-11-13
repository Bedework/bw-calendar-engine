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

import org.bedework.caldav.util.filter.FilterBase;
import org.bedework.caldav.util.filter.ObjectFilter;
import org.bedework.calfacade.svc.BwView;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import java.util.List;

/** A filter contains a reference to a view and to the actual filters.
 * This allows us to treat the underlying filter as special - we may
 * wish the search engine to cache it.
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class BwViewFilter extends ObjectFilter<BwView> {
  /** Filters based on a view.
   *
   * @param name - null one will be created
   */
  public BwViewFilter(final String name) {
    super(name, PropertyInfoIndex.VIEW);
  }

  /**  Set the filter
   *
   * @param   val   the filter for this view
   */
  public void setFilter(final FilterBase val) {
    if (getNumChildren() == 0) {
      addChild(val);
      return;
    }

    getChildren().set(0, val);
  }

  public FilterBase getFilter() {
    List<FilterBase> c = getChildren();
    if (Util.isEmpty(c)) {
      return null;
    }

    return c.get(0);
  }
}
