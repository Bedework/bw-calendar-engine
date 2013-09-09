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

import org.bedework.synch.wsmessages.ArrayOfSynchConnectorInfo;
import org.bedework.synch.wsmessages.SynchConnectorInfoType;
import org.bedework.synch.wsmessages.SynchInfoType;
import org.bedework.util.misc.ToString;

import java.util.ArrayList;
import java.util.List;

/** Information about synch engine
 *
 *  @author Mike Douglass douglm   bedework.edu
 *  @version 1.0
 */
public class BwSynchInfo extends SynchInfoType {
  List<BwSynchConnectorInfo> conns = new ArrayList<BwSynchConnectorInfo>();

  /**
   * @param val
   * @return one of these initialized from the parameter
   */
  public static BwSynchInfo copy(final SynchInfoType val) {
    BwSynchInfo bsi = new BwSynchInfo();

    ArrayOfSynchConnectorInfo asci = val.getConnectors();

    if ((asci != null) && !asci.getConnector().isEmpty()) {
      for (SynchConnectorInfoType scit: asci.getConnector()) {
        bsi.getConns().add(BwSynchConnectorInfo.copy(scit));
      }
    }

    return bsi;
  }

  /**
   * @return possibly empty list of copied connector info.
   */
  public List<BwSynchConnectorInfo> getConns() {
    return conns;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("conns", getConns());

    return ts.toString();
  }
}
