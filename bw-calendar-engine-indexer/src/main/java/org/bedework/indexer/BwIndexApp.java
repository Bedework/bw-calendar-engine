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

import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.indexing.BwIndexer.IndexInfo;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.calfacade.indexing.ReindexResponse;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;
import org.bedework.util.indexing.ContextInfo;
import org.bedework.util.misc.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.bedework.calfacade.indexing.BwIndexer.allDocTypes;

/** The crawler program for the bedework calendar system.
 *
 * @author douglm
 *
 */
public class BwIndexApp extends JmsSysEventListener {
  private final IndexProperties props;

  private long messageCount;

  private Crawl crawler;

  private MessageProcessor msgProc;

  BwIndexApp(final IndexProperties props) {
    this.props = props;
  }

  /**
   * @return number of messages processed
   */
  public long getMessageCount() {
    return messageCount;
  }

  /**
   * @return count processed
   */
  public long getCollectionsUpdated() {
    if (msgProc == null) {
      return 0;
    }

    return msgProc.getEntitiesUpdated();
  }

  /**
   * @return count processed
   */
  public long getCollectionsDeleted() {
    if (msgProc == null) {
      return 0;
    }

    return msgProc.getCollectionsDeleted();
  }

  /**
   * @return count processed
   */
  public long getEntitiesUpdated() {
    if (msgProc == null) {
      return 0;
    }

    return msgProc.getEntitiesUpdated();
  }

  /**
   * @return count processed
   */
  public long getEntitiesDeleted() {
    if (msgProc == null) {
      return 0;
    }

    return msgProc.getEntitiesDeleted();
  }

  /**
   * @return info on indexes maintained by indexer.
   */
  public Set<IndexInfo> getIndexInfo() {
    return getCrawler().getIndexInfo();
  }

  /**
   * @return list of purged indexes.
   */
  public String purgeIndexes() {
    try {
      final List<String> is = getCrawler().purgeIndexes();

      if (Util.isEmpty(is)) {
        return "No indexes purged";
      }

      final StringBuilder res = new StringBuilder("Purged indexes");

      res.append("------------------------\n");

      for (final String i: is) {
        res.append(i);
        res.append("\n");
      }

      return res.toString();
    } catch (final Throwable t) {
      error(t);

      return t.getLocalizedMessage();
    }
  }

  public Map<String, String> newIndexes() {
    return getCrawler().newIndexes();
  }

  public ReindexResponse reindex(final String docType) {
    return getCrawler().reindex(docType);
  }

  public IndexStatsResponse getIndexStats(final String indexName) {
    return getCrawler().getIndexStats(indexName);
  }

  public List<ContextInfo> getContextInfo() {
    return getCrawler().getContextInfo();
  }

  /** Move the production index alias to the given index
   *
   * @param indexName name of index to be aliased
   * @return status code- 0 for OK
   */
  public int setProdAlias(final String indexName) {
    return getCrawler().setProdAlias(indexName);
  }

  /** Move the production index aliases to the latest indexes
   *
   * @return status code- 0 for OK
   */
  public int makeAllProd() {
    final Set<IndexInfo> is = getIndexInfo();
    final Map<String, String> names = new HashMap<>();

    for (final IndexInfo ii: is) {
      final String indexName = ii.getIndexName();

      for (final String type: allDocTypes) {
        final String lctype = type.toLowerCase();

        if (indexName.startsWith("bw" + lctype + "2")) {
          final String tname = names.get(type);

          if ((tname == null) || (tname.compareTo(indexName) < 0)) {
            names.put(type, indexName);
          }
          break;
        }
      }
    }

    for (final String iname: names.values()) {
      final int res = getCrawler().setProdAlias(iname);
      if (res != 0) {
        return res;
      }
    }

    return 0;
  }

  void crawl() {
    final Crawl c = getCrawler();

    c.crawl();

    c.checkThreads();
  }

  void indexEntity(final String docType) {
    final Crawl c = getCrawler();

    c.setDocType(docType);
    //c.sameIndex();

    c.crawl();

    c.checkThreads();
  }

  private Crawl getCrawler() {
    if (crawler != null) {
      return crawler;
    }

    crawler = new Crawl(props);

    return crawler;
  }

  /**
   * @return status or null
   */
  public List<CrawlStatus> getStatus() {
    if (crawler == null) {
      return null;
    }
    return crawler.getStatus();
  }

  void listen() throws Throwable {
    try (final JmsSysEventListener ignored =
                 open(crawlerQueueName,
                      CalSvcFactoryDefault.getPr())) {
      msgProc = new MessageProcessor(props);

      process(false);
    }
  }

  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      messageCount++;

      /*
      if (props.getDiscardMessages()) {
        return;
      }*/

      msgProc.processMessage(ev);
    } catch (final Throwable t) {
      throw new NotificationException(t);
    }
  }
}
