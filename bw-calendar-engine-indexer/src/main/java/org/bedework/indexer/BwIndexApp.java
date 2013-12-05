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
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.sysevents.listeners.JmsSysEventListener;
import org.bedework.util.misc.Util;

import java.util.List;
import java.util.Set;

/** The crawler program for the bedework calendar system.
 *
 * @author douglm
 *
 */
public class BwIndexApp extends JmsSysEventListener {
  private IndexProperties props;

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
   * @throws Throwable
   */
  public Set<IndexInfo> getIndexInfo() throws Throwable {
    return getCrawler().getIndexInfo();
  }

  /**
   * @return list of purged indexes.
   * @throws Throwable
   */
  public String purgeIndexes() {
    try {
      List<String> is = getCrawler().purgeIndexes();

      if (Util.isEmpty(is)) {
        return "No indexes purged";
      }

      StringBuilder res = new StringBuilder("Purged indexes");

      res.append("------------------------\n");

      for (String i: is) {
        res.append(i);
        res.append("\n");
      }

      return res.toString();
    } catch (Throwable t) {
      error(t);

      return t.getLocalizedMessage();
    }
  }

  void crawl() throws Throwable {
    Crawl c = getCrawler();

    c.crawl();

    c.checkThreads();
  }

  private Crawl getCrawler() throws Throwable {
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
    open(crawlerQueueName);

    msgProc = new MessageProcessor(props);

    process(false);
  }

  /* (non-Javadoc)
   * @see org.bedework.sysevents.listeners.JmsSysEventListener#action(org.bedework.sysevents.events.SysEvent)
   */
  @Override
  public void action(final SysEvent ev) throws NotificationException {
    if (ev == null) {
      return;
    }

    try {
      messageCount++;

      if (props.getDiscardMessages()) {
        return;
      }

      msgProc.processMessage(ev);
    } catch (Throwable t) {
      throw new NotificationException(t);
    }
  }
}
