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
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.base.CategorisedEntity;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.List;

/** A filter that selects events that have a certain category
 *
 * @author Mike Douglass
 * @version 1.0
 */
public class BwCategoryFilter extends ObjectFilter<BwCategory> {
  /** Match on any of the categories.
   *
   * @param name - null one will be created
   */
  public BwCategoryFilter(final String name) {
    super(name, PropertyInfoIndex.CATEGORIES);
  }

  /** Match on any of the categories.
   *
   * @param name - null one will be created
   * @param propertyIndexes
   */
  public BwCategoryFilter(final String name,
                          final List<PropertyInfoIndex> propertyIndexes) {
    super(name, propertyIndexes);
  }

  /* ====================================================================
   *                   matching methods
   * ==================================================================== */

  /* (non-Javadoc)
   * @see org.bedework.caldav.util.filter.Filter#match(java.lang.Object)
   */
  @Override
  public boolean match(final Object o,
                       final String userHref) {
    if (!(o instanceof CategorisedEntity)) {
      return false;
    }

    CategorisedEntity ce = (CategorisedEntity)o;
    return ce.hasCategory(getEntity());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("(");

    sb.append(getPropertyIndex());
    stringOper(sb);
    sb.append(getEntity().getWordVal());

    sb.append(")");

    return sb.toString();
  }
}
