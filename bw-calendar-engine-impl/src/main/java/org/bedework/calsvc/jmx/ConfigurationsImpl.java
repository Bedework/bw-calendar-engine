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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.AuthProperties;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.calfacade.configs.CmdUtilProperties;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.configs.DumpRestoreProperties;
import org.bedework.calfacade.configs.IndexProperties;
import org.bedework.calfacade.configs.NotificationProperties;
import org.bedework.calfacade.configs.SynchConfig;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.mail.MailConfigProperties;
import org.bedework.indexer.BwIndexCtlMBean;
import org.bedework.sysevents.listeners.BwSysevLogger;
import org.bedework.sysevents.monitor.BwSysMonitor;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.config.ConfigurationStore;
import org.bedework.util.http.service.HttpConfig;
import org.bedework.util.http.service.HttpOut;
import org.bedework.util.jmx.BaseMBean;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.security.keys.GenKeys;
import org.bedework.util.servlet.io.PooledBuffers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;

/** All the configuration objects used by CalSvc and its callers.
 *
 */
public final class ConfigurationsImpl extends ConfBase<BasicSystemPropertiesImpl>
        implements Configurations {
  private static boolean configured;

  private static BasicSystemProperties basicProps;

  private static AuthProperties authProperties;

  private static AuthProperties unAuthProperties;

  private static SystemProperties sysProperties;

  private static MailConfigProperties mailProps;

  private static NotificationProperties notificationProps;

  private static SynchConfig synchProps;

  private static CardDavInfo unauthCardDavInfo;

  private static CardDavInfo authCardDavInfo;

  private static HttpConfig httpConfig;

  private static PooledBuffers pooledBuffers;

  private final Map<String, DirConfigProperties> dirConfigs = new HashMap<>();

  private static final String cmdUtilClass =
          "org.bedework.tools.cmdutil.CmdUtil";

  private static final String dumpRestoreClass =
          "org.bedework.dumprestore.BwDumpRestore";

  private static ConfBase dumpRestore;

  private static CmdUtilProperties cmdUtilProperties;

  private static DumpRestoreProperties dumpRestoreProperties;

  private static final String indexerCtlClass =
          "org.bedework.indexer.BwIndexCtl";

  private static BwIndexCtlMBean indexCtl;

  private static IndexProperties indexProperties;

  private static final String chgnoteClass =
          "org.bedework.chgnote.BwChgNote";

  private static final String inoutClass =
          "org.bedework.inoutsched.BwInoutSched";

  /**
   * @throws CalFacadeException on error
   */
  public ConfigurationsImpl() throws CalFacadeException {
    super("org.bedework.bwengine:service=Conf");

    /* This class acts as the mbean for the basic properties */

    setConfigName(basicPropsNamePart);
    setConfigPname(SystemConf.confuriPname);

    try {
      checkMbeansInstalled();

      authProperties = new ROAuthProperties(getAuthProps(true));

      unAuthProperties = new ROAuthProperties(getAuthProps(false));

      sysProperties = new ROSystemProperties(getSystemProps());
    } catch (final CalFacadeException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public String loadConfig() {
    return null;
  }

  @Override
  public BasicSystemProperties getBasicSystemProperties() {
    return basicProps;
  }

  @Override
  public AuthProperties getAuthProperties(final boolean auth) {
    if (auth) {
      return authProperties;
    }
    return unAuthProperties;
  }

  @Override
  public SystemProperties getSystemProperties() {
    return sysProperties;
  }

  @Override
  public HttpConfig getHttpConfig() {
    return httpConfig;
  }

  @Override
  public DumpRestoreProperties getDumpRestoreProperties() {
    return dumpRestoreProperties;
  }

  @Override
  public IndexProperties getIndexProperties() {
    return indexProperties;
  }

  @Override
  public MailConfigProperties getMailConfigProperties() {
    return mailProps;
  }

  @Override
  public NotificationProperties getNotificationProps() {
    return notificationProps;
  }

  @Override
  public SynchConfig getSynchConfig() {
    return synchProps;
  }

  @Override
  public DirConfigProperties getDirConfig(final String name) {
    return dirConfigs.get(name);
  }

  @Override
  public CardDavInfo getCardDavInfo(final boolean auth) {
    if (auth) {
      return authCardDavInfo;
    }

    return unauthCardDavInfo;
  }

  /**
   * @return name for unauthenticated properties mbean
   * @throws CalFacadeException on error
   */
  public static ObjectName getUnauthpropsName() throws CalFacadeException {
    try {
      return new ObjectName(SystemConf.getServiceName(unauthPropsNamePart));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @return name for authenticated properties mbean
   * @throws CalFacadeException on error
   */
  public static ObjectName getAuthpropsName() throws CalFacadeException {
    try {
      return new ObjectName(SystemConf.getServiceName(authPropsNamePart));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @return name for system properties mbean
   * @throws CalFacadeException on error
   */
  public static ObjectName getSyspropsName() throws CalFacadeException {
    try {
      return new ObjectName(SystemConf.getServiceName(systemPropsNamePart));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private synchronized void checkMbeansInstalled() throws CalFacadeException {
    if (configured) {
      return;
    }

    try {
      /* ------------- Basic system properties -------------------- */
      getManagementContext().start();

      loadConfig(BasicSystemPropertiesImpl.class);

      cfg.setTestMode(Boolean.getBoolean("org.bedework.testmode"));
      basicProps = new ROBasicSystemProperties(cfg);

      /* ------------- Auth properties -------------------- */
      AuthConf conf = new AuthConf(unauthPropsNamePart);
      register(getUnauthpropsName(), conf);
      conf.loadConfig();

      conf = new AuthConf(authPropsNamePart);
      register(getAuthpropsName(), conf);
      conf.loadConfig();

      /* ------------- System properties -------------------- */
      final SystemConf sconf = new SystemConf(systemPropsNamePart);
      register(getSyspropsName(), sconf);
      sconf.loadConfig();

      /* ------------- Notification properties -------------------- */
      final NotificationConf nc = new NotificationConf();
      register(new ObjectName(nc.getServiceName()), nc);
      nc.loadConfig();
      notificationProps = nc.getConfig();

      /* ------------- Mailer properties -------------------- */
      final MailerConf mc = new MailerConf();
      register(new ObjectName(mc.getServiceName()), mc);
      mc.loadConfig();
      mailProps = mc.getConfig();

      /* ------------- Http properties -------------------- */
      HttpOut ho = new HttpOut("org.bedework.bwengine.confuri",
                               "org.bedework.bwengine",
                               "httpConfig");
      register(new ObjectName(ho.getServiceName()), ho);
      ho.loadConfig();
      httpConfig = ho.getConfig();

      /* ------------- Sysevents -------------------- */
      BwSysevLogger sysev = new BwSysevLogger();
      register(new ObjectName(BwSysevLogger.serviceName), sysev);
      sysev.start();

      /* ------------- Monitor -------------------- */
      final BwSysMonitor sysmon = new BwSysMonitor();
      register(new ObjectName(BwSysMonitor.serviceName), sysmon);
      sysmon.start();

      /* ------------- GenKeys -------------------- */
      final GenKeys gk = new GenKeys("org.bedework.bwengine.confuri");
      register(new ObjectName(GenKeys.serviceName), gk);
      gk.loadConfig();

      /* ------------- Pooled buffers -------------------- */
      final PooledBuffers pb = new PooledBuffers();
      register(new ObjectName(pb.getServiceName()), pb);

      /* ------------- Synch properties -------------------- */
      final SynchConf sc = new SynchConf();
      register(new ObjectName(sc.getServiceName()), sc);
      sc.loadConfig();
      synchProps = sc.getConfig();

      /* ------------- Directory interface properties ------------- */
      loadDirConfigs();

      /* At this point we can call ourselves usable */
      configured = true;

      /* ------------- Change notifications  -------------------- */
      startChgNote();

      /* ------------- Carddav ------------------------------------ */
      loadCardDav();

      /* ------------- CmdUtil --------------------- */
      loadCmdUtil();

      /* ------------- DumpRestore properties --------------------- */
      loadDumpRestore();

      /* ------------- Indexer properties ------------------------- */
      startIndexing();

      /* ------------- InoutSched --------------------------------- */
      startScheduling();
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      if (!configured) {
        stop();
      }
    }
  }

  private void startChgNote() throws Throwable {
    ConfBase cb = loadInstance(chgnoteClass);

    register(new ObjectName(cb.getServiceName()), cb);
    cb.start();
  }

  private void loadCardDav() throws Throwable {
    unauthCardDavInfo = (CardDavInfo)load(
            new CardDavInfoConf(unauthCardDavInfoNamePart), false);

    authCardDavInfo = (CardDavInfo)load(
            new CardDavInfoConf(authCardDavInfoNamePart), false);
  }

  private void loadCmdUtil() throws Throwable {
    cmdUtilProperties = (CmdUtilProperties)load(
            loadInstance(cmdUtilClass), false);
  }

  private void loadDumpRestore() throws Throwable {
    dumpRestoreProperties = (DumpRestoreProperties)load(
            loadInstance(dumpRestoreClass), false);
  }

  private void startIndexing() throws Throwable {
    indexProperties = (IndexProperties)load(
            loadInstance(indexerCtlClass), true);
  }

  private void startScheduling() throws Throwable {
    load(loadInstance(inoutClass), true);
  }

  private ConfigBase load(final ConfBase cb,
                          final boolean start) throws Throwable {
    register(new ObjectName(cb.getServiceName()), cb);

    cb.loadConfig();

    if (start) {
      cb.start();
    }

    return cb.getConfig();
  }

  private void loadDirConfigs() throws Throwable {
    final ConfigurationStore cs = getStore().getStore("dirconfigs");

    final List<String> names = cs.getConfigs();

    for (final String dn: names) {
      final ObjectName objectName = createObjectName("dirconfig", dn);

      /* Read the config so we can get the mbean class name. */

      final DirConfigPropertiesImpl dCfg = (DirConfigPropertiesImpl)cs.getConfig(dn);

      if (dCfg == null) {
        error("Unable to read directory configuration " + dn);
        continue;
      }

      String mbeanClassName = dCfg.getMbeanClassName();

      if (mbeanClassName == null) {
        error("Must set the mbean class name for connector " + dn);
        error("Falling back to base class for " + dn);

        mbeanClassName = DirConf.class.getCanonicalName();
      }

      @SuppressWarnings("unchecked")
      final DirConf<DirConfigPropertiesImpl> dc =
              (DirConf<DirConfigPropertiesImpl>)makeObject(mbeanClassName);
      dc.init(cs, objectName.toString(), dCfg, dn);

      dirConfigs.put(dn, dc);

      register(new ObjectName(dc.getServiceName()), dc);
    }
  }

  private AuthProperties getAuthProps(final boolean auth) throws CalFacadeException {
    try {
      ObjectName mbeanName;

      if (!auth) {
        mbeanName = getUnauthpropsName();
      } else {
        mbeanName = getAuthpropsName();
      }

      return (AuthProperties)getManagementContext().getAttribute(mbeanName, "Config");
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  private static ConfBase loadInstance(final String cname) {
    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Class cl = loader.loadClass(cname);

      if (cl == null) {
        throw new CalFacadeException("Class " + cname + " not found");
      }

      Object o = cl.newInstance();

      if (o == null) {
        throw new CalFacadeException("Unable to instantiate class " + cname);
      }

      return (ConfBase)o;
    } catch (Throwable t) {
      t.printStackTrace();
      throw new RuntimeException(t);
    }
  }

  private SystemProperties getSystemProps() throws CalFacadeException {
    try {
      ObjectName mbeanName = getSyspropsName();

      return (SystemProperties)getManagementContext().getAttribute(mbeanName, "Config");
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public void stop() {
    for (Object o: getRegisteredMBeans()) {
      if (o instanceof BaseMBean) {
        try {
          ((BaseMBean)o).stop();
        } catch (Throwable t){
          t.printStackTrace();
        }
      }
    }

    try {
      getManagementContext().stop();
    } catch (Throwable t){
      t.printStackTrace();
    }
  }
}