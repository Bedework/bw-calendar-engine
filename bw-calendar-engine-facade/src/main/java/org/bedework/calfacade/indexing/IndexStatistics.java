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
package org.bedework.calfacade.indexing;

import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.util.misc.ToString;

import java.util.HashMap;
import java.util.Map;

/** Class to collect indexing statistics
 *
 * @author douglm
 *
 */
public class IndexStatistics {
  private String name;

  private final Map<IndexedType, Long> counts = 
          new HashMap<>(IndexedType.values().length);

  /**
   * @param name - name of the statistics
   */
  public IndexStatistics(final String name) {
    this.name = name;
  }
  
  public String getName() {
    return name;
  }
  
  public long getCount(final IndexedType type) {
    return counts.getOrDefault(type, 0L);
  }
  
  /**
   * @param type - type of count
   */
  public synchronized void inc(final IndexedType type) {
    counts.put(type, getCount(type) + 1);
  }

  /**
   * @param type - type of count
   * @param val - value to add
   */
  public synchronized void inc(final IndexedType type,
                               final long val) {
    counts.put(type, getCount(type) + val);
  }

  public void toStringSegment(final ToString ts) {
    ts.append("name", getName());

    for (final IndexedType it: counts.keySet()) {
      ts.append(it.name(), counts.get(it));
    }
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
