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

import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.ToString;
import org.bedework.util.misc.Util;

import java.util.List;

/** Define a field we sort by
 *
 * @author Mike Douglass
 * @version 1.0
 */

public class SortTerm {
  private List<PropertyInfoIndex> properties;

  private boolean ascending;

  /** For a list of propa, propb we sort on propa.propb
   *
   * @param properties List of properties and subproperties to search on
   * @param ascending
   */
  public SortTerm(final List<PropertyInfoIndex> properties,
                  final boolean ascending) {
    this.properties = properties;
    this.ascending = ascending;
  }

  /**
   * @return property name
   */
  public List<PropertyInfoIndex> getProperties() {
    return properties;
  }

  public String getPropertyRef() {
    if (Util.isEmpty(properties)) {
      return null;
    }

    String delim = "";

    final StringBuilder sb = new StringBuilder();

    for (final PropertyInfoIndex pii : properties) {
      final BwIcalPropertyInfo.BwIcalPropertyInfoEntry ipie =
              BwIcalPropertyInfo.getPinfo(pii);

      if (ipie == null) {
        return null;
      }

      sb.append(delim);
      sb.append(ipie.getJname());
      delim = ".";
    }

    return sb.toString();
  }

  /**
   * @return true for acending.
   */
  public boolean isAscending() {
    return ascending;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append(getProperties());
    ts.append("ascending", isAscending());

    return ts.toString();
  }
}
