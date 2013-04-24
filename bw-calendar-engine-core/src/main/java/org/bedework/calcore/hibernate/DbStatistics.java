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

import java.util.ArrayList;
import java.util.Collection;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwEventAnnotation;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwLocation;
import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwUser;
import org.bedework.calfacade.BwStats.StatsEntry;

import org.apache.log4j.Logger;

import org.hibernate.stat.CollectionStatistics;
import org.hibernate.stat.EntityStatistics;
import org.hibernate.stat.QueryStatistics;
import org.hibernate.stat.SecondLevelCacheStatistics;
import org.hibernate.stat.Statistics;

/** Class to help display statistics.
 *
 * @author Mike Douglass
 */
public class DbStatistics {
  /** Dump the statistics to the log
   *
   * @param dbStats
   */
  public static void dumpStats(Statistics dbStats) {
    if (dbStats == null) {
      return;
    }

    Logger log = Logger.getLogger(DbStatistics.class);

    log.debug(BwStats.toString(getStats(dbStats)));
  }

  /** Get the current statistics
   *
   * @param dbStats
   * @return Collection
   */
  public static Collection<StatsEntry> getStats(Statistics dbStats) {
    /* XXX this ought to be property driven to some extent. The cache stats in
     * particular.
     */
    ArrayList<StatsEntry> al = new ArrayList<StatsEntry>();

    if (dbStats == null) {
      return al;
    }

    al.add(new StatsEntry("Database statistics"));

    al.add(new StatsEntry("Number of connection requests", dbStats.getConnectCount()));
    al.add(new StatsEntry("Session flushes", dbStats.getFlushCount()));
    al.add(new StatsEntry("Transactions", dbStats.getTransactionCount()));
    al.add(new StatsEntry("Successful transactions", dbStats.getSuccessfulTransactionCount()));
    al.add(new StatsEntry("Sessions opened", dbStats.getSessionOpenCount()));
    al.add(new StatsEntry("Sessions closed", dbStats.getSessionCloseCount()));
    al.add(new StatsEntry("Queries executed", dbStats.getQueryExecutionCount()));
    al.add(new StatsEntry("Max query time", dbStats.getQueryExecutionMaxTime()));
    al.add(new StatsEntry("Max time query", dbStats.getQueryExecutionMaxTimeQueryString()));

    al.add(new StatsEntry("Collection statistics"));

    al.add(new StatsEntry("Collections fetched", dbStats.getCollectionFetchCount()));
    al.add(new StatsEntry("Collections loaded", dbStats.getCollectionLoadCount()));
    al.add(new StatsEntry("Collections rebuilt", dbStats.getCollectionRecreateCount()));
    al.add(new StatsEntry("Collections batch deleted", dbStats.getCollectionRemoveCount()));
    al.add(new StatsEntry("Collections batch updated", dbStats.getCollectionUpdateCount()));

    al.add(new StatsEntry("Object statistics"));

    al.add(new StatsEntry("Objects fetched", dbStats.getEntityFetchCount()));
    al.add(new StatsEntry("Objects loaded", dbStats.getEntityLoadCount()));
    al.add(new StatsEntry("Objects inserted", dbStats.getEntityInsertCount()));
    al.add(new StatsEntry("Objects deleted", dbStats.getEntityDeleteCount()));
    al.add(new StatsEntry("Objects updated", dbStats.getEntityUpdateCount()));

    al.add(new StatsEntry("Cache statistics"));

    double chit = dbStats.getQueryCacheHitCount();
    double cmiss = dbStats.getQueryCacheMissCount();

    al.add(new StatsEntry("Cache hit count", chit));
    al.add(new StatsEntry("Cache miss count", cmiss));
    al.add(new StatsEntry("Cache hit ratio", chit / (chit + cmiss)));

    entityStats(al, dbStats, BwCalendar.class);
    entityStats(al, dbStats, BwEventObj.class);
    entityStats(al, dbStats, BwEventAnnotation.class);
    entityStats(al, dbStats, BwCategory.class);
    entityStats(al, dbStats, BwLocation.class);
    entityStats(al, dbStats, BwContact.class);
    entityStats(al, dbStats, BwUser.class);

    collectionStats(al, dbStats, BwCalendar.class, "children");

    collectionStats(al, dbStats, BwEventObj.class, "attendees");
    collectionStats(al, dbStats, BwEventObj.class, "categories");
    collectionStats(al, dbStats, BwEventObj.class, "descriptions");
    collectionStats(al, dbStats, BwEventObj.class, "summaries");

    collectionStats(al, dbStats, BwEventObj.class, "rrules");
    collectionStats(al, dbStats, BwEventObj.class, "rdates");
    collectionStats(al, dbStats, BwEventObj.class, "exdates");

    collectionStats(al, dbStats, BwEventAnnotation.class, "attendees");
    collectionStats(al, dbStats, BwEventAnnotation.class, "categories");
    collectionStats(al, dbStats, BwEventAnnotation.class, "descriptions");
    collectionStats(al, dbStats, BwEventAnnotation.class, "summaries");

    collectionStats(al, dbStats, BwEventAnnotation.class, "rrules");
    collectionStats(al, dbStats, BwEventAnnotation.class, "rdates");
    collectionStats(al, dbStats, BwEventAnnotation.class, "exdates");

    String[] qs = dbStats.getQueries();

    for (String q: qs) {
      queryStats(al, dbStats, q);
    }

    String[] slcrn = dbStats.getSecondLevelCacheRegionNames();

    for (String s: slcrn) {
      secondLevelStats(al, dbStats, s);
    }

    return al;
  }

  private static void entityStats(Collection<StatsEntry> c, Statistics dbStats,
                                  Class cl) {
    String name = cl.getName();

    c.add(new StatsEntry("Statistics for " + name));

    EntityStatistics eStats = dbStats.getEntityStatistics(name);

    c.add(new StatsEntry("Fetched", eStats.getFetchCount()));
    c.add(new StatsEntry("Loaded", eStats.getLoadCount()));
    c.add(new StatsEntry("Inserted", eStats.getInsertCount()));
    c.add(new StatsEntry("Deleted", eStats.getDeleteCount()));
    c.add(new StatsEntry("Updated", eStats.getUpdateCount()));
  }

  private static void collectionStats(Collection<StatsEntry> c,
                                      Statistics dbStats, Class cl,
                                      String cname) {
    String name = cl.getName() + "." + cname;

    c.add(new StatsEntry("Statistics for " + name));

    CollectionStatistics cStats = dbStats.getCollectionStatistics(name);

    c.add(new StatsEntry("Fetched", cStats.getFetchCount()));
    c.add(new StatsEntry("Loaded", cStats.getLoadCount()));
    c.add(new StatsEntry("Recreated", cStats.getRecreateCount()));
    c.add(new StatsEntry("Removed", cStats.getRemoveCount()));
    c.add(new StatsEntry("Updated", cStats.getUpdateCount()));
  }

  private static void queryStats(Collection<StatsEntry> c,
                                 Statistics dbStats,
                                 String q) {
    c.add(new StatsEntry("Query statistics for " + q));

    QueryStatistics qStats = dbStats.getQueryStatistics(q);

    c.add(new StatsEntry("Execution ct", qStats.getExecutionCount()));
    c.add(new StatsEntry("Cache hits", qStats.getCacheHitCount()));
    c.add(new StatsEntry("Cache puts", qStats.getCachePutCount()));
    c.add(new StatsEntry("Cache misses", qStats.getCacheMissCount()));
    c.add(new StatsEntry("Execution row ct", qStats.getExecutionRowCount()));
    c.add(new StatsEntry("Execution avg millis", qStats.getExecutionAvgTime()));
    c.add(new StatsEntry("Execution max millis", qStats.getExecutionMaxTime()));
    c.add(new StatsEntry("Execution min millis", qStats.getExecutionMinTime()));
  }

  private static void secondLevelStats(Collection<StatsEntry> c,
                                       Statistics dbStats,
                                       String name) {
    c.add(new StatsEntry("Second level statistics for " + name));

    SecondLevelCacheStatistics slStats = dbStats.getSecondLevelCacheStatistics(name);

    c.add(new StatsEntry("Elements in memory", slStats.getElementCountInMemory()));
    c.add(new StatsEntry("Element on disk", slStats.getElementCountOnDisk()));
    //c.add(new StatsEntry("Entries", slStats.getEntries()));
    c.add(new StatsEntry("Hit count", slStats.getHitCount()));
    c.add(new StatsEntry("Miss count", slStats.getMissCount()));
    c.add(new StatsEntry("Put count", slStats.getPutCount()));
    c.add(new StatsEntry("Memory size", slStats.getSizeInMemory()));
  }
}
