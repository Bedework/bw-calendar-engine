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

import edu.rpi.cmt.jmx.ConfBase;

import java.util.ArrayList;
import java.util.List;

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
      while (running) {
        try {
          getIndexer().listen();
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
          getIndexer().close();
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
        getIndexer().crawl();
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

  private BwIndexApp indexer;

  /**
   */
  public BwIndexCtl() {
    super(getServiceName(nm));

    setConfigName(nm);

    setConfigPname(confuriPname);
  }

  /**
   * @param name
   * @return service name for the mbean with this name
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
  public void setSolrCoreAdmin(final String val) {
    getConfig().setSolrCoreAdmin(val);
  }

  @Override
  public String getSolrCoreAdmin() {
    return getConfig().getSolrCoreAdmin();
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
    return getIndexer().getMessageCount();
  }

  public String listIndexes() {
    return getIndexer().listIndexes();
  }

  public long getEntitiesUpdated() {
    return getIndexer().getEntitiesUpdated();
  }

  public long getEntitiesDeleted() {
    return getIndexer().getEntitiesDeleted();
  }

  public long getCollectionsUpdated() {
    return getIndexer().getCollectionsUpdated();
  }

  public long getCollectionsDeleted() {
    return getIndexer().getCollectionsDeleted();
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public List<String> rebuildStatus() {
    List<String> res = new ArrayList<String>();

    if (crawler == null) {
      outLine(res, "No rebuild appears to have taken place");
      return res;
    }

    List<CrawlStatus> sts = getIndexer().getStatus();

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

    info("************************************************************");
    info(" * Starting indexer");
    info("************************************************************");

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

  private BwIndexApp getIndexer() {
    if (indexer != null) {
      return indexer;
    }

    indexer = new BwIndexApp(getConfig());

    return indexer;
  }
}
