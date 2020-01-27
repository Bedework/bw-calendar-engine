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
package org.bedework.calfacade.synch;

import org.bedework.synch.wsmessages.ArrayOfSynchPropertyInfo;
import org.bedework.synch.wsmessages.SynchConnectorInfoType;
import org.bedework.synch.wsmessages.SynchPropertyInfoType;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/** Information about a synch connector
 *
 *  @author Mike Douglass douglm@bedework.edu
 *  @version 1.0
 */
public class BwSynchConnectorInfo extends SynchConnectorInfoType {
  List<BwSynchPropertyInfo> props = new ArrayList<>();

  /**
   * @param val XML synch connector info
   * @return one of these initialized from the parameter
   */
  public static BwSynchConnectorInfo copy(final SynchConnectorInfoType val) {
    BwSynchConnectorInfo bsci = new BwSynchConnectorInfo();

    bsci.setName(val.getName());
    bsci.setManager(val.isManager());
    bsci.setReadOnly(val.isReadOnly());

    ArrayOfSynchPropertyInfo aspi = val.getProperties();

    if ((aspi != null) && !aspi.getProperty().isEmpty()) {
      for (SynchPropertyInfoType spit: aspi.getProperty()) {
        bsci.getProps().add(BwSynchPropertyInfo.copy(spit));
      }
    }

    return bsci;
  }

  /**
   * @return possibly empty list of copied properties.
   */
  public List<BwSynchPropertyInfo> getProps() {
    return props;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);
    ts.append("name", getName());
    ts.append("manager", isManager());
    ts.append("readOnly", isReadOnly());
    ts.append("\n     ");

    ts.append("props", getProps());

    return ts.toString();
  }
}
