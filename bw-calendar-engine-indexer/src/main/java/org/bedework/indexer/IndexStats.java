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

import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.indexing.IndexStatistics;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.util.ArrayList;
import java.util.List;

/** Class to collect indexing statistics
 *
 * @author douglm
 *
 */
public class IndexStats extends IndexStatistics implements Logged {
  /**
   * @param name - name of the statistics
   */
  public IndexStats(final String name) {
    super(name);
  }

  /** */
  public void stats() {
    info(getName());

    for (final IndexedType st: IndexedType.values()) {
      info(stat(st));
    }
  }

  /**
   * @return info as list
   */
  public List<String> statsList() {
    final List<String> infoLines = new ArrayList<>();

    infoLines.add(getName() + "\n");

    for (final IndexedType st: IndexedType.values()) {
      infoLines.add(stat(st) + "\n");
    }

    return infoLines;
  }

  private static final String blanks = "                                    ";
  private static final int paddedNmLen = 18;

  private String stat(final IndexedType st) {
    final StringBuilder sb = new StringBuilder();
    final String name = st.toString();

    if (name.length() < paddedNmLen) {
      sb.append(blanks.substring(0, paddedNmLen - name.length()));
    }

    sb.append(name);
    sb.append(": ");
    sb.append(getCount(st));

    return sb.toString();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
