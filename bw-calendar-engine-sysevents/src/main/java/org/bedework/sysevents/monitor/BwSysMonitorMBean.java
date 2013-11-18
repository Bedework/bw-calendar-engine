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

import org.bedework.calfacade.MonitorStat;
import org.bedework.util.jmx.BaseMBean;

import java.util.List;

/**
 * @author douglm
 *
 */
public interface BwSysMonitorMBean extends BaseMBean {
  static final String serviceName = "org.bedework.bwengine:service=BwSysMonitor";

  /** Display the current counts and values
   *
   * @return List of String
   */
  public List<String> showValues();

  /** Get the current stats
   *
   * @return List of MonitorStat
   */
  public List<MonitorStat> getStats();
}
