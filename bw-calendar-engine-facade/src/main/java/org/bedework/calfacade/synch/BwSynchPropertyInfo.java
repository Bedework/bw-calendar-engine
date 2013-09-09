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

import org.bedework.synch.wsmessages.SynchPropertyInfoType;
import org.bedework.util.misc.ToString;

/** Information about connector properties
 *
 *  @author Mike Douglass douglm  bedework.edu
 *  @version 1.0
 */
public class BwSynchPropertyInfo extends SynchPropertyInfoType {
  /**
   * @param val
   * @return one of these initialized from the parameter
   */
  public static BwSynchPropertyInfo copy(final SynchPropertyInfoType val) {
    BwSynchPropertyInfo bspi = new BwSynchPropertyInfo();

    bspi.setName(val.getName());
    bspi.setSecure(val.isSecure());
    bspi.setType(val.getType());
    bspi.setDescription(val.getDescription());
    bspi.setRequired(val.isRequired());

    return bspi;
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public String toString() {
    return new ToString(this).
        append("name", getName()).
        append("type", getType()).
        append("secure", isSecure()).
        append("description", getDescription()).
        append("required", isRequired()).
        toString();
  }
}
