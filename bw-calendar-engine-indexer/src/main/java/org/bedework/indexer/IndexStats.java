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
package org.bedework.indexer;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Class to collect indexing statistics
 *
 * @author douglm
 *
 */
public class IndexStats {
  private String name;

  private transient Logger log;

  /** */
  public enum StatType {
    /** */
    users,

    /** */
    collections,

    /** */
    entities,

    /** */
    unreachableEntities
  }

  /**
   * @param name
   */
  public IndexStats(final String name) {
    this.name = name;
  }

  /**   */
  private long[] counts = new long[StatType.values().length];

  /** */
  public void stats() {
    info(name);

    for (StatType st: StatType.values()) {
      info(stat(st));
    }
  }

  /**
   * @return info as list
   */
  public List<String> statsList() {
    List<String> infoLines = new ArrayList<String>();

    infoLines.add(name + "\n");

    for (StatType st: StatType.values()) {
      infoLines.add(stat(st) + "\n");
    }

    return infoLines;
  }

  /**
   * @param st
   */
  public synchronized void inc(final StatType st) {
    counts[st.ordinal()]++;
  }

  private final String blanks = "                                    ";
  private final int paddedNmLen = 18;

  private String stat(final StatType st) {
    StringBuilder sb = new StringBuilder();

    String name = st.toString();

    if (name.length() < paddedNmLen) {
      sb.append(blanks.substring(0, paddedNmLen - name.length()));
    }

    sb.append(name);
    sb.append(": ");
    sb.append(counts[st.ordinal()]);

    return sb.toString();
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final String msg) {
    getLog().info(msg);
  }
}
