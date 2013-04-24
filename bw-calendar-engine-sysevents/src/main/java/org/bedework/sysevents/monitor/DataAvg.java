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

import org.bedework.sysevents.events.SysEventBase.SysCode;

/** Values for an average.
 *
 * @author douglm
 */
public class DataAvg extends DataValue {
  /* Count of values */
  private double count;

  /**
   * @param name
   * @param sysCode - non null if this object is associated with a code
   */
  public DataAvg(final String name,
                  final SysCode sysCode) {
    super(name, sysCode);
  }

  /**
   * @return total
   */
  public double getCount() {
    return count;
  }

  /**
   * @return a statistic
   */
  @Override
  public MonitorStat getStat() {
    long val = (long)(getValue() / getCount());

    return new MonitorStat(getName(),
                           String.valueOf(val));
  }

  /**
   *
   */
  @Override
  public void inc(final double val) {
    super.inc(val);
    count++;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    sb.append(getName());
    sb.append(" = ");
    sb.append(getValue() / getCount());
    sb.append("\n");

    return sb.toString();
  }
}
