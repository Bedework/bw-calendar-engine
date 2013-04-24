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
package org.bedework.sysevents.events;

import java.util.HashMap;
import java.util.Map;

/** Event to allow us to drop statistics into jms.
 *
 * <p>Events can be valueless, something just happened we want to count, or can
 * take a String value or can take a Long value which can be accumulated.
 *
 * @author douglm
 *
 */
public class StatsEvent extends NamedEvent {
  private static final long serialVersionUID = 1L;

  private Long longValue;
  private String strValue;

  /** */
  public static enum StatType {
    /** */
    valueLess,
    /** */
    str,
    /** */
    lnum
  }

  /** Event delete elapsed millis */
  public static final String deleteTime = "DELTIME";

  /** Event create elapsed millis */
  public static final String createTime = "CRETIME";

  /** Event create/mod check for name uniqueness elapsed millis */
  public static final String checkNameTime = "CNAMETIME";

  /** Event create/mod check for uid uniqueness elapsed millis */
  public static final String checkUidTime = "CUIDTIME";

  /** Calendar refresh elapsed millis */
  public static final String refreshTime = "REFRESHTIME";

  private static Map<String, StatType> statTypes = new HashMap<String, StatType>();

  static {
    statTypes.put(deleteTime, StatType.lnum);
    statTypes.put(createTime, StatType.lnum);
    statTypes.put(checkNameTime, StatType.lnum);
    statTypes.put(checkUidTime, StatType.lnum);
  }

  /**
   * @param name
   * @param strValue
   */
  public StatsEvent(final String name,
                    final String strValue) {
    super(SysCode.STATS, name);

    this.strValue = strValue;
  }

  /**
   * @param name
   * @param longValue
   */
  public StatsEvent(final String name,
                    final Long longValue) {
    super(SysCode.STATS, name);

    this.longValue = longValue;
  }

  /**
   *
   * @return Long    value
   */
  public Long getLongValue() {
    return longValue;
  }

  /**
   *
   * @return String    value
   */
  public String getStrValue() {
    return strValue;
  }

  /**
   * @param name
   * @return type or null
   */
  public static StatType getStatType(final String name) {
    return statTypes.get(name);
  }

  /** Add our stuff to the StringBuilder
   *
   * @param sb    StringBuilder for result
   */
  @Override
  public void toStringSegment(final StringBuilder sb) {
    super.toStringSegment(sb);

    if (strValue == null) {
      sb.append(", longValue=");
      sb.append(getLongValue());
    } else {
      sb.append(", strValue=");
      sb.append(getStrValue());
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("StatsEvent{");

    toStringSegment(sb);

    sb.append("}");

    return sb.toString();
  }
}
