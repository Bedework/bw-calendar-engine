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
import org.bedework.calfacade.indexing.BwIndexer.IndexedType;
import org.bedework.calfacade.indexing.IndexStatistics;
import org.bedework.calfacade.indexing.IndexStatsResponse;
import org.bedework.calfacade.indexing.ReindexResponse;
import org.bedework.sysevents.NotificationException;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NameNotFoundException;

import static org.bedework.base.response.Response.Status.failed;

/**
 * @author douglm
 *
 */
@SuppressWarnings("unused")
public class BwIndexCtl extends ConfBase<IndexPropertiesImpl>
        implements BwIndexCtlMBean {
  /* Name of the directory holding the config data */
  private static final String confDirName = "bwengine";

  private class ProcessorThread extends Thread {
    private boolean running;

    long lastErrorTime = 0;
    long errorResetTime = 1000 * 60 * 5;  // 5 minutes since last error
    int errorCt = 0;
    final int maxErrorCt = 5;

    boolean showedTrace;

    /**
     * @param name - for the thread
     */
    public ProcessorThread(final String name) {
      super(name);
    }

    @Override
    public void run() {
      final BwIndexApp app = getIndexApp();

      info("************************************************************");
      info(" * Starting indexer");

    /* List the indexes in use - ensures we have an indexer early on */

      info(" * Current indexes: ");
      Set<IndexInfo> is = null;

      var ok = false;

      try {
        is = app.getIndexInfo();
        ok = true;
      } catch (final Throwable t) {
        error(t);
        info(" * Exception getting index info:");
        info(" * " + t.getLocalizedMessage());
      }

      info(listIndexes(is));

      info("************************************************************");

      if (ok) {
        new StatsThread().start();
      }

      while (running) {
        try {
          app.listen();
          running = false;
        } catch (final Throwable t) {
          if (!handleException(t)) {
            if (System.currentTimeMillis() - lastErrorTime > errorResetTime) {
              errorCt = 0;
            }

            if (errorCt > maxErrorCt) {
              error("Too many errors: stopping");
              running = false;
              break;
            }

            lastErrorTime = System.currentTimeMillis();
            errorCt++;

            if (!showedTrace) {
              error(t);
              //            showedTrace = true;
            } else {
              error(t.getMessage());
            }
          }
        } finally {
          getIndexApp().close();
        }
      }
    }

    private boolean handleException(final Throwable val) {
      if (!(val instanceof NotificationException)) {
        return false;
      }

      if (Util.causeIs(val, NameNotFoundException.class)) {
        // jmx shutting down?
        error("Looks like JMX shut down.");
        error(val);
        running = false;
        return true;
      }

      return false;
    }
  }

  private class CrawlThread extends Thread {
    boolean showedTrace;

    /**
     * @param name - for the thread
     */
    public CrawlThread(final String name) {
      super(name);
    }
    @Override
    public void run() {
      try {
        getIndexApp().crawl();
        setStatus(statusDone);
      } catch (final Throwable t) {
        setStatus(statusFailed);
        if (!showedTrace) {
          error(t);
          //            showedTrace = true;
        } else {
          error(t.getMessage());
        }
      }
    }
  }

  private class StatsThread extends Thread {
    boolean showedTrace;

    public StatsThread() {
      super("IndexStats");
    }

    @Override
    public void run() {
      try {
        while (!isInterrupted()) {
          final var cis = getIndexApp().getContextInfo();
          setStatus(statusDone);

          if (cis != null) {
            for (final var ci: cis) {
              final var sii = ci.getSearchIndexInfo();
              info(String.format("ContextInfo - " +
                                         "node: %s, " +
                                         "openContexts: %s, " +
                                         "scrollTotal: %s," +
                                         "scrollCurrent: %s",
                                 ci.getNodeName(),
                                 sii.getOpenContexts(),
                                 sii.getScrollTotal(),
                                 sii.getScrollCurrent()));
            }
          }
          synchronized (this) {
            long delay = getContextInfoDelay();
            if (delay <= 0) {
              delay = 1000 * 60 * 5;
            }

            this.wait(delay);
          }
        }
      } catch (final Throwable t) {
        setStatus(statusFailed);
        if (!showedTrace) {
          error(t);
          //            showedTrace = true;
        } else {
          error(t.getMessage());
        }
      }
    }
  }

  // The one we want to index
  private String entityDocType;

  /* Thread to index a particular entity type
   */
  private class EntityIndexThread extends Thread {
    boolean showedTrace;

    /**
     * @param name - for the thread
     */
    public EntityIndexThread(final String name) {
      super(name);
    }

    @Override
    public void run() {
      try {
        getIndexApp().indexEntity(entityDocType);
        setStatus(statusDone);
      } catch (final Throwable t) {
        setStatus(statusFailed);
        if (!showedTrace) {
          error(t);
          //            showedTrace = true;
        } else {
          error(t.getMessage());
        }
      }
    }
  }

  private ProcessorThread processor;

  private CrawlThread crawler;

  private EntityIndexThread entityProcessor;

  private final static String nm = "indexing";

  private BwIndexApp indexApp;

  /**
   */
  public BwIndexCtl() {
    super(getServiceName(nm),
          confDirName, nm);
  }

  /**
   * @param name of mbean
   * @return object name value for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  @Override
  public void setIndexerURL(final String val) {
    getConfig().setIndexerURL(val);
  }

  @Override
  public String getIndexerURL() {
    return getConfig().getIndexerURL();
  }

  @Override
  public void setIndexerToken(final String val) {
    getConfig().setIndexerToken(val);
  }

  @Override
  public String getIndexerToken() {
    return getConfig().getIndexerToken();
  }

  @Override
  public void setIndexerUser(final String val) {
    getConfig().setIndexerUser(val);
  }

  @Override
  public String getIndexerUser() {
    return getConfig().getIndexerUser();
  }

  @Override
  public void setIndexerPw(final String val) {
    getConfig().setIndexerPw(val);
  }

  @Override
  public String getIndexerPw() {
    return getConfig().getIndexerPw();
  }

  @Override
  public void setClusterName(final String val) {
    getConfig().setClusterName(val);
  }

  @Override
  public String getClusterName() {
    return getConfig().getClusterName();
  }

  @Override
  public void setNodeName(final String val) {
    getConfig().setNodeName(val);
  }

  @Override
  public String getNodeName() {
    return getConfig().getNodeName();
  }

  @Override
  public void setKeyStore(final String val) {
    getConfig().setKeyStore(val);
  }

  @Override
  public String getKeyStore() {
    return getConfig().getKeyStore();
  }

  @Override
  public void setKeyStorePw(final String val) {
    getConfig().setKeyStorePw(val);
  }

  @Override
  public String getKeyStorePw() {
    return getConfig().getKeyStorePw();
  }

  @Override
  public void setIndexerConfig(final String val) {
    getConfig().setIndexerConfig(val);
  }

  @Override
  public String getIndexerConfig() {
    return getConfig().getIndexerConfig();
  }

  @Override
  public void setAccount(final String val) {
    getConfig().setAccount(val);
  }

  @Override
  public String getAccount() {
    return getConfig().getAccount();
  }

  @Override
  public void setMaxEntityThreads(final int val) {
    getConfig().setMaxEntityThreads(val);
  }

  @Override
  public int getMaxEntityThreads() {
    return getConfig().getMaxEntityThreads();
  }

  @Override
  public void setMaxPrincipalThreads(final int val) {
    getConfig().setMaxPrincipalThreads(val);
  }

  @Override
  public int getMaxPrincipalThreads() {
    return getConfig().getMaxPrincipalThreads();
  }

  @Override
  public void setIndexPublic(final boolean val) {
    getConfig().setIndexPublic(val);
  }

  @Override
  public boolean getIndexPublic() {
    return getConfig().getIndexPublic();
  }

  @Override
  public void setIndexUsers(final boolean val) {
    getConfig().setIndexUsers(val);
  }

  @Override
  public boolean getIndexUsers() {
    return getConfig().getIndexUsers();
  }

  @Override
  public void setDiscardMessages(final boolean val) {
    getConfig().setDiscardMessages(val);
  }

  @Override
  public boolean getDiscardMessages() {
    return getConfig().getDiscardMessages();
  }

  @Override
  public void setSkipPaths(final String val) {
    getConfig().setSkipPaths(val);
  }

  @Override
  public String getSkipPaths() {
    return getConfig().getSkipPaths();
  }

  @Override
  public void setSkipPathsList(final List<String> val) {
    getConfig().setSkipPathsList(val);
  }

  @Override
  public List<String> getSkipPathsList() {
    return getConfig().getSkipPathsList();
  }

  @Override
  public void setContextInfoDelay(final long val) {
    getConfig().setContextInfoDelay(val);
  }

  @Override
  public long getContextInfoDelay() {
    return getConfig().getContextInfoDelay();
  }

  @Override
  public IndexProperties cloneIt() {
    return null;
  }

  @Override
  public long getMessageCount() {
    return getIndexApp().getMessageCount();
  }

  @Override
  public String listIndexes() {
    try {
      return listIndexes(getIndexApp().getIndexInfo());
    } catch (final Throwable t) {
      return t.getLocalizedMessage();
    }
  }

  @Override
  public String purgeIndexes() {
    return getIndexApp().purgeIndexes();
  }

  public long getEntitiesUpdated() {
    return getIndexApp().getEntitiesUpdated();
  }

  public long getEntitiesDeleted() {
    return getIndexApp().getEntitiesDeleted();
  }

  public long getCollectionsUpdated() {
    return getIndexApp().getCollectionsUpdated();
  }

  public long getCollectionsDeleted() {
    return getIndexApp().getCollectionsDeleted();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public List<String> rebuildStatus() {
    final List<String> res = new ArrayList<>();

    if (crawler == null) {
      outLine(res, "No rebuild appears to have taken place");
      return res;
    }

    final List<CrawlStatus> sts = getIndexApp().getStatus();

    if (sts != null) {
      for (final CrawlStatus st: sts) {
        outputStatus(st, res);
      }
    }

    return res;
  }

  @Override
  public String rebuildIndex() {
    try {
      setStatus(statusStopped);
      
      if ((crawler != null) && crawler.isAlive()) {
        error("Reindexer already started");
        return "Reindexer already started";
      }

      crawler = new CrawlThread(getServiceName());
      crawler.start();

      return "Started";
    } catch (final Throwable t) {
      setStatus(statusFailed);
      error("Error rebuilding indexes.");
      error(t);

      return t.getLocalizedMessage();
    }
  }

  @Override
  public String rebuildEntityIndex(final String docType) {
    try {
      setStatus(statusStopped);

      if ((entityProcessor != null) && entityProcessor.isAlive()) {
        error("Reindexer already started");
        return "Reindexer already started";
      }

      entityDocType = docType;
      entityProcessor = new EntityIndexThread(getServiceName());
      entityProcessor.start();

      return "Started";
    } catch (final Throwable t) {
      setStatus(statusFailed);
      error("Error rebuilding entity index.");
      error(t);

      return t.getLocalizedMessage();
    }
  }

  @Override
  public String newIndexes() {
    try {
      final Map<String, String> indexNames = getIndexApp().newIndexes();
      final StringBuilder sb = new StringBuilder();

      for (final String type: indexNames.keySet()) {
        sb.append(type);
        sb.append(": ");
        sb.append(indexNames.get(type));
        sb.append("\n");
      }

      return sb.toString();
    } catch (final Throwable t) {
      return "Failed: " + t.getLocalizedMessage();
    }
  }

  @Override
  public String reindex(final String docType) {
    try {
      final ReindexResponse resp = getIndexApp().reindex(docType);
      
      info(resp.toString());
      
      return "ok";
    } catch (final Throwable t) {
      return "Failed: " + t.getLocalizedMessage();
    }
  }

  @Override
  public String setProdAlias(final String indexName) {
    try {
      final int status = getIndexApp().setProdAlias(indexName);

      if (status == 0) {
        return "ok";
      }

      return "Failed with status " + status;
    } catch (final Throwable t) {
      return "Failed: " + t.getLocalizedMessage();
    }
  }

  @Override
  public String makeAllProd() {
    try {
      final int status = getIndexApp().makeAllProd();

      if (status == 0) {
        return "ok";
      }

      return "Failed with status " + status;
    } catch (final Throwable t) {
      return "Failed: " + t.getLocalizedMessage();
    }
  }

  @Override
  public IndexStatsResponse indexStats(final String indexName) {
    try {
      final IndexStatsResponse resp = getIndexApp().getIndexStats(indexName);

      info(resp.toString());

      return resp;
    } catch (final Throwable t) {
      final IndexStatsResponse resp = new IndexStatsResponse("Failed");
      resp.setStatus(failed);
      resp.setMessage(t.getLocalizedMessage());
      return resp;
    }
  }

  @Override
  public boolean isStarted() {
    return (processor != null) && processor.isAlive();
  }

  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    processor = new ProcessorThread(getServiceName());
    processor.running = true;
    processor.start();
  }

  @Override
  public synchronized void stop() {
    if (processor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping indexer");
    info("************************************************************");

    processor.running = false;
    //?? ProcessorThread.stopProcess(processor);

    processor.interrupt();
    try {
      processor.join(20 * 1000);
    } catch (final InterruptedException ignored) {
    } catch (final Throwable t) {
      error("Error waiting for processor termination");
      error(t);
    }

    processor = null;

    info("************************************************************");
    info(" * Indexer terminated");
    info("************************************************************");
  }

  @Override
  public String loadConfig() {
    return loadConfig(IndexPropertiesImpl.class);
  }

  /* ========================================================================
   * Private methods
   * ======================================================================== */

  private String listIndexes(final Set<IndexInfo> is) {
    if (Util.isEmpty(is)) {
      return "No indexes found";
    }

    final StringBuilder res = new StringBuilder("Indexes");

    res.append("------------------------\n");

    for (final IndexInfo ii: is) {
      res.append(ii.getIndexName());

      if (!Util.isEmpty(ii.getAliases())) {
        String delim = "<----";

        for (final String a: ii.getAliases()) {
          res.append(delim);
          res.append(a);
          delim = ", ";
        }
      }

      res.append("\n");
    }

    return res.toString();
  }

  private void outputStatus(final CrawlStatus status,
                            final List<String> res) {
    outLine(res, "--------------------------------------");

    outLine(res, status.name);

    outLine(res, "current status: " + status.currentStatus);

    if (status.stats != null) {
      res.addAll(status.stats.statsList());
    }

    res.add(IndexStats.makeStat(
            "skippedTombstonedEvents",
            status.skippedTombstonedEvents));
    res.add(IndexStats.makeStat(
            "skippedTombstonedCollections",
            status.skippedTombstonedCollections));
    res.add(IndexStats.makeStat(
            "skippedTombstonedResources",
            status.skippedTombstonedResources));

    outLine(res, "");

    if (!status.infoLines.isEmpty()) {
      res.addAll(status.infoLines);
    }
  }

  private void outputStatus(final ReindexResponse status,
                            final List<String> res) {
    outLine(res, "--------------------------------------");

    outLine(res, status.getIndexName());

    outLine(res, "    docType: " + status.getDocType());
    outLine(res, "     status: " + status.getStatus());

    if (status.getMessage() != null) {
      outLine(res, "    message: " + status.getMessage());
    }

    outLine(res, "processed: " + status.getProcessed());

    outLine(res, "recurring: " + status.getRecurring());

    outLine(res, "totalFailed: " + status.getTotalFailed());

    final IndexStatistics is = status.getStats();
    
    if (is != null) {
      for (final IndexedType type: IndexedType.values()) {
        outLine(res, type.toString() + ": " + is.getCount(type));
      }
    }

    outLine(res, "");
  }

  private void outLine(final List<String> res,
                       final String msg) {
    res.add(msg + "\n");
  }

  private BwIndexApp getIndexApp() {
    if (indexApp != null) {
      return indexApp;
    }

    indexApp = new BwIndexApp(getConfig());

    return indexApp;
  }
}
