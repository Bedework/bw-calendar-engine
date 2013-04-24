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

import org.bedework.sysevents.events.HttpOutEvent;
import org.bedework.sysevents.events.StatsEvent;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.events.StatsEvent.StatType;
import org.bedework.sysevents.events.SysEventBase.SysCode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Display some data values.
 *
 * @author douglm
 */
public class DataValues {
  private Map<SysCode, DataAvg> dvMap = new HashMap<SysCode, DataAvg>();

  private DataAvg respTime;

  private DataAvg calDavRespTime;

  private Map<String, DataAvg> statMap = new HashMap<String, DataAvg>();

  /**
   */
  public DataValues() {
    super();

    respTime = addDv("Avg web response time", SysCode.WEB_OUT);
    calDavRespTime = addDv("Avg CalDAV response time", SysCode.CALDAV_OUT);
  }

  /**
   * @param ev
   */
  public void update(final SysEvent ev) {
    SysCode sc = ev.getSysCode();
    if (sc == SysCode.WEB_OUT) {
      respTime.inc(((HttpOutEvent)ev).getMillis());
      return;
    }

    if (sc == SysCode.CALDAV_OUT) {
      calDavRespTime.inc(((HttpOutEvent)ev).getMillis());
      return;
    }

    if (sc == SysCode.STATS) {
      StatsEvent se = (StatsEvent)ev;
      String sname = se.getName();
      DataAvg da = statMap.get(sname);

      if (da == null) {
        da = new DataAvg(sname, sc);

        statMap.put(sname, da);
      }

      StatType st = StatsEvent.getStatType(sname);

      if (st != StatType.lnum) {
        da.inc(1);
      } else if (se.getLongValue() != null) {
        da.inc(se.getLongValue());
      }

      return;
    }
  }

  /**
   * @param vals
   */
  public void getValues(final List<String> vals) {
    vals.add(respTime.toString());
    vals.add(calDavRespTime.toString());
  }

  /**
   * @param stats
   */
  public void getStats(final List<MonitorStat> stats) {
    stats.add(respTime.getStat());
    stats.add(calDavRespTime.getStat());

    for (DataAvg da: statMap.values()) {
      long val = (long)(da.getValue() / da.getCount());
      stats.add(new MonitorStat(da.getName(), (long)da.getCount(),
                                String.valueOf(val)));
    }
  }

  private DataAvg addDv(final String name,
                        final SysCode scode) {
    DataAvg dv = new DataAvg(name, scode);

    dvMap.put(scode, dv);

    return dv;
  }
}
