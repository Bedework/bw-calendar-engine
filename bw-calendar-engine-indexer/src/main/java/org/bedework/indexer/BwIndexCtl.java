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
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author douglm
 *
 */
public class BwIndexCtl extends ConfBase<IndexPropertiesImpl>
        implements BwIndexCtlMBean {
  /* Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  private boolean running;

  private class ProcessorThread extends Thread {
    boolean showedTrace;

    /**
     * @param name - for the thread
     */
    public ProcessorThread(final String name) {
      super(name);
    }
    @Override
    public void run() {
      BwIndexApp app = getIndexApp();

      info("************************************************************");
      info(" * Starting indexer");

    /* List the indexes in use - ensures we have an indexer early on */

      info(" * Current indexes: ");
      Set<IndexInfo> is = null;

      try {
        is = app.getIndexInfo();
      } catch (Throwable t) {
        info(" * Exception getting index info:");
        info(" * " + t.getLocalizedMessage());
      }

      info(listIndexes(is));

      info("************************************************************");

      while (running) {
        try {
          app.listen();
        } catch (InterruptedException ie) {
          break;
        } catch (Throwable t) {
          if (!showedTrace) {
            error(t);
//            showedTrace = true;
          } else {
            error(t.getMessage());
          }
        } finally {
          getIndexApp().close();
        }
      }
    }
  }

  private ProcessorThread processor;

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
      } catch (InterruptedException ie) {
      } catch (Throwable t) {
        if (!showedTrace) {
          error(t);
          //            showedTrace = true;
        } else {
          error(t.getMessage());
        }
      }
    }
  }

  private CrawlThread crawler;

  private final static String nm = "indexing";

  private BwIndexApp indexApp;

  /**
   */
  public BwIndexCtl() {
    super(getServiceName(nm));

    setConfigName(nm);

    setConfigPname(confuriPname);
  }

  /**
   * @param name
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
  public void setEmbeddedIndexer(final boolean val) {
    getConfig().setEmbeddedIndexer(val);
  }

  @Override
  public boolean getEmbeddedIndexer() {
    return getConfig().getEmbeddedIndexer();
  }

  @Override
  public void setHttpEnabled(final boolean val) {
    getConfig().setHttpEnabled(val);
  }

  @Override
  public boolean getHttpEnabled() {
    return getConfig().getHttpEnabled();
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
  public void setDataDir(final String val) {
    getConfig().setDataDir(val);
  }

  @Override
  public String getDataDir() {
    return getConfig().getDataDir();
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
  public void setPublicIndexName(final String val) {
    getConfig().setPublicIndexName(val);
  }

  @Override
  public String getPublicIndexName() {
    return getConfig().getPublicIndexName();
  }

  @Override
  public void setUserIndexName(final String val) {
    getConfig().setUserIndexName(val);
  }

  @Override
  public String getUserIndexName() {
    return getConfig().getUserIndexName();
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
    } catch (Throwable t) {
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
    List<String> res = new ArrayList<>();

    if (crawler == null) {
      outLine(res, "No rebuild appears to have taken place");
      return res;
    }

    List<CrawlStatus> sts = getIndexApp().getStatus();

    if (sts != null) {
      for (CrawlStatus st: sts) {
        outputStatus(st, res);
      }
    }

    return res;
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexCtlMBean#rebuildIndex()
   */
  @Override
  public String rebuildIndex() {
    try {
      if ((crawler != null) && crawler.isAlive()) {
        error("Reindexer already started");
        return "Reindexer already started";
      }

      crawler = new CrawlThread(getServiceName());
      crawler.start();

      return "Started";
    } catch (Throwable t) {
      List<String> infoLines = new ArrayList<String>();

      infoLines.add("***********************************\n");
      infoLines.add("Error rebuilding indexes.\n");
      infoLines.add("***********************************\n");
      error("Error rebuilding indexes.");
      error(t);

      return t.getLocalizedMessage();
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexCtlMBean#isStarted()
   */
  @Override
  public boolean isStarted() {
    return (processor != null) && processor.isAlive();
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexCtlMBean#start()
   */
  @Override
  public synchronized void start() {
    if (processor != null) {
      error("Already started");
      return;
    }

    running = true;

    processor = new ProcessorThread(getServiceName());
    processor.start();
  }

  /* (non-Javadoc)
   * @see org.bedework.indexer.BwIndexCtlMBean#stop()
   */
  @Override
  public synchronized void stop() {
    if (processor == null) {
      error("Already stopped");
      return;
    }

    info("************************************************************");
    info(" * Stopping indexer");
    info("************************************************************");

    running = false;

    processor.interrupt();
    try {
      processor.join(20 * 1000);
    } catch (InterruptedException ie) {
    } catch (Throwable t) {
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

  private String listIndexes(Set<IndexInfo> is) {
    if (Util.isEmpty(is)) {
      return "No indexes found";
    }

    StringBuilder res = new StringBuilder("Indexes");

    res.append("------------------------\n");

    for (IndexInfo ii: is) {
      res.append(ii.getIndexName());

      if (!Util.isEmpty(ii.getAliases())) {
        String delim = "<----";

        for (String a: ii.getAliases()) {
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

    outLine(res, "");

    if (!status.infoLines.isEmpty()) {
      res.addAll(status.infoLines);
    }
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
