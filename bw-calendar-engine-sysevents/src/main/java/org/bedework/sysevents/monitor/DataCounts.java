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
package org.bedework.sysevents.monitor;

import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.SysEventBase.SysCode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Display some data as a name/value pair.
 *
 * @author douglm
 */
public class DataCounts {
  private Collection<DataValue> dls = new ArrayList<DataValue>();

  private Map<SysCode, DataValue> dlMap = new HashMap<SysCode, DataValue>();

  /**
   */
  public DataCounts() {
    super();

    //setLayout(new GridBagLayout());

    addDl("web requests", SysCode.WEB_IN);
    addDl("caldav requests", SysCode.CALDAV_IN);
    addDl("entities fetched", SysCode.ENTITY_FETCHED);
    addDl("entities added", SysCode.ENTITY_ADDED);
    addDl("entities updated", SysCode.ENTITY_UPDATED);
    addDl("entities tombstoned", SysCode.ENTITY_TOMBSTONED);
    addDl("entities deleted", SysCode.ENTITY_DELETED);

  }

  /**
   * @param ev
   */
  public void update(final SysEvent ev) {
    SysCode sc = ev.getSysCode();
    DataValue dl = dlMap.get(sc);

    if (dl != null) {
      dl.inc();
    }
  }

  /**
   * @param vals
   */
  public void getValues(final List<String> vals) {
    for (DataValue dv: dls) {
      vals.add(dv.toString());
    }
  }

  /**
   * @param stats
   */
  public void getStats(final List<MonitorStat> stats) {
    for (DataValue dv: dls) {
      stats.add(dv.getStat());
    }
  }

  private DataValue addDl(final String name,
                          final SysCode scode) {
    DataValue dl = new DataValue(name, scode);

    dls.add(dl);

    SysCode s = dl.getSysCode();

    if (s != null) {
      dlMap.put(s, dl);
    }

    return dl;
  }
}
