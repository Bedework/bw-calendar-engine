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

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.ical.BwIcalPropertyInfo;
import org.bedework.calfacade.ical.BwIcalPropertyInfo.BwIcalPropertyInfoEntry;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.ArrayList;
import java.util.List;

/** Create Retrieval lists
 *
 * @author Mike Douglass
 * @version 1.0
 */

public class RetrieveList {
  public static List<BwIcalPropertyInfoEntry> getRetrieveList(
          final List<String> retrieveList) throws CalFacadeException {

    if (retrieveList == null) {
      return null;
    }

    // Convert property names to field names
    List<BwIcalPropertyInfoEntry> retrieveListFields =
            new ArrayList<>(retrieveList.size() +
                                    BwIcalPropertyInfo.requiredPindexes.size());

    for (String pname: retrieveList) {
      PropertyInfoIndex pi;

      try {
        pi = PropertyInfoIndex.valueOf(pname);
      } catch (Throwable t) {
        throw new CalFacadeException(CalFacadeException.unknownProperty,
                                     pname);
      }

      BwIcalPropertyInfoEntry ipie = BwIcalPropertyInfo.getPinfo(pi);

      if ((ipie == null) || (ipie.getMultiValued())) {
        // At this stage it seems better to be inefficient
        //warn("Bad property " + pname);
        return null;
      }

      retrieveListFields.add(ipie);
    }

    if (retrieveListFields != null) {
      for (PropertyInfoIndex pi: BwIcalPropertyInfo.requiredPindexes) {
        retrieveListFields.add(BwIcalPropertyInfo.getPinfo(pi));
      }
    }

    return retrieveListFields;
  }
}
