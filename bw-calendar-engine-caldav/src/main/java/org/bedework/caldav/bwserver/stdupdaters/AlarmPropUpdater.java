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

package org.bedework.caldav.bwserver.stdupdaters;

import org.bedework.caldav.bwserver.PropertyUpdater;
import org.bedework.calfacade.BwAlarm;
import org.bedework.calfacade.util.ChangeTable;
import org.bedework.calfacade.util.ChangeTableEntry;

import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.ArrayList;
import java.util.List;

/** Base class for alarm properties. We may need to revalidate if we change
 * properties.
 *
 * @author douglm
 *
 */
public abstract class AlarmPropUpdater implements PropertyUpdater {
  /** Used to retain state of the alarm changes to an entity
   *
   * @author douglm
   */
  public static class AlarmsState {
    /** */
    public List<BwAlarm> alarms = new ArrayList<BwAlarm>();

    /** */
    public static final String stateName = "org.bedework.updstate.alarms";

    /**
     * @param val
     */
    public void add(final BwAlarm val) {
      alarms.add(val);
    }
  }

  protected void flagChange(final BwAlarm val,
                            final UpdateInfo ui) {
    ChangeTableEntry cte = ui.getCte();

    if (cte != null) {
      cte.setChanged(null,null);
    }

    ChangeTable ct = ui.getEi().getChangeset(ui.getUserHref());

    cte = ct.getEntry(PropertyInfoIndex.VALARM);

    if (cte != null) {
      cte.setChanged(null, val);
    }

    getAS(ui).add(val);
  }

  protected AlarmsState getAS(final UpdateInfo ui) {
    AlarmsState as = (AlarmsState)ui.getState(AlarmsState.stateName);
    if (as == null) {
      as = new AlarmsState();
      ui.saveState(AlarmsState.stateName, as);
    }

    return as;
  }
}
