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
package org.bedework.calcore.hibernate;

import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.CacheStats;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.wrappers.CalendarWrapper;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Class to encapsulate most of what we do with collections
 *
 * @author douglm
 *
 */
class CollectionCache implements Serializable {
  private static class CacheInfo {
    CalendarWrapper col;
    String token;
    boolean checked;

    CacheInfo(final CalendarWrapper col) {
      setCol(col);
    }

    void setCol(final CalendarWrapper col) {
      this.col = col;
      token = col.getLastmod().getTagValue();
      checked = true;
    }
  }

  private Map<String, CacheInfo> cache = new HashMap<>();

  private CoreCalendars cols;

  //BwStats stats;
  CacheStats cs;

  CollectionCache(final CoreCalendars cols,
                  final BwStats stats) {
    //this.stats = stats;
    this.cols = cols;
    cs = stats.getCollectionCacheStats();
  }

  void put(final CalendarWrapper col) {
    CacheInfo ci = cache.get(col.getPath());

    if (ci != null) {
      // A refetch
      ci.setCol(col);

      cs.incRefetches();
    } else {
      ci = new CacheInfo(col);
      cache.put(col.getPath(), ci);

      cs.incCached();
    }
  }

  void remove(final String path) {
    cache.remove(path);
  }

  CalendarWrapper get(final String path) throws CalFacadeException {
    CacheInfo ci = cache.get(path);

    if (ci == null) {
      cs.incMisses();
      return null;
    }

    if (ci.checked) {
      cs.incHits();
      return ci.col;
    }

    CollectionSynchInfo csi = cols.getSynchInfo(path, ci.token);

    if (csi == null) {
      // Collection deleted?
      cs.incMisses();
      return null;
    }

    if (!csi.changed) {
      ci.checked = true;

      cs.incHits();
      return ci.col;
    }

    return null;  // force refetch
  }

  CalendarWrapper get(final String path, final String token) throws CalFacadeException {
    CacheInfo ci = cache.get(path);

    if (ci == null) {
      cs.incMisses();
      return null;
    }

    if (!ci.token.equals(token)) {
      return null;
    }

    cs.incHits();
    return ci.col;
  }

  void flushAccess(final CoreCalendars cc) throws CalFacadeException {
    for (CacheInfo ci: cache.values()) {

      Set<Integer> accesses = ci.col.evaluatedAccesses();
      if (accesses == null) {
        continue;
      }

      Set<Integer> evaluated = new TreeSet<>(ci.col.evaluatedAccesses());
      ci.col.clearCurrentAccess();

      for (Integer acc: evaluated) {
        cc.checkAccess(ci.col, acc, true);
      }
    }

//      cs.incAccessFlushes();
  }

  void flush() {
    for (CacheInfo ci: cache.values()) {
      ci.checked = false;
    }

    cs.incFlushes();
  }

  void clear() {
    cache.clear();

    cs.incFlushes();
  }
}

