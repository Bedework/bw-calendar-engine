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

import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.configs.CardDavInfo;
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.calfacade.configs.SynchConfig;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.mail.MailConfigProperties;

import edu.rpi.cmt.config.ConfigurationStore;
import edu.rpi.cmt.jmx.ConfBase;

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

  private static String basicPropsNamePart = "basicSystem";

  private static String unauthSystemPropsNamePart = "unauthSystem";

  private static String authSystemPropsNamePart = "authSystem";

  private static String unauthCardDavInfoNamePart = "unauthCardDav";

  private static String authCardDavInfoNamePart = "authCardDav";

  private static BasicSystemProperties basicProps;

  private static SystemProperties authSysProperties;

  private static SystemProperties unAuthSysProperties;

  private static MailConfigProperties mailProps;

  private static SynchConfig synchProps;

  private static CardDavInfo unauthCardDavInfo;

  private static CardDavInfo authCardDavInfo;

  private Map<String, DirConfigProperties> dirConfigs = new HashMap<String, DirConfigProperties>();

  /**
   * @throws CalFacadeException
   */
  public ConfigurationsImpl() throws CalFacadeException {
    super("org.bedework.bwengine:service=System");

    /* This class acts as the mbean for the basic properties */

    setConfigName(basicPropsNamePart);
    setConfigPname(SystemConf.confuriPname);

    try {
      checkMbeansInstalled();

      authSysProperties = new ROSystemProperties(getSystemProps(true));

      unAuthSysProperties = new ROSystemProperties(getSystemProps(false));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public BasicSystemProperties getBasicSystemProperties() throws CalFacadeException {
    return basicProps;
  }

  @Override
  public SystemProperties getSystemProperties(final boolean auth) throws CalFacadeException {
    if (auth) {
      return authSysProperties;
    }
    return unAuthSysProperties;
  }

  @Override
  public MailConfigProperties getMailConfigProperties() throws CalFacadeException {
    return mailProps;
  }

  @Override
  public SynchConfig getSynchConfig() throws CalFacadeException {
    return synchProps;
  }

  @Override
  public DirConfigProperties getDirConfig(final String name) throws CalFacadeException {
    return dirConfigs.get(name);
  }

  @Override
  public CardDavInfo getCardDavInfo(final boolean auth) throws CalFacadeException {
    if (auth) {
      return authCardDavInfo;
    }

    return unauthCardDavInfo;
  }

  /**
   * @return name for unauthenticated system properties mbean
   * @throws CalFacadeException
   */
  public static ObjectName getUnauthSyspropsName() throws CalFacadeException {
    try {
      return new ObjectName(SystemConf.getServiceName(unauthSystemPropsNamePart));
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @return name for autenticated system properties mbean
   * @throws CalFacadeException
   */
  public static ObjectName getAuthSyspropsName() throws CalFacadeException {
    try {
      return new ObjectName(SystemConf.getServiceName(authSystemPropsNamePart));
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
      basicProps = new ROBasicSystemProperties(cfg);
      saveConfig();

      /* ------------- System properties -------------------- */
      SystemConf conf = new SystemConf(unauthSystemPropsNamePart);
      register(getUnauthSyspropsName(), conf);
      conf.loadConfig();
      conf.saveConfig();

      conf = new SystemConf(authSystemPropsNamePart);
      register(getAuthSyspropsName(), conf);
      conf.loadConfig();
      conf.saveConfig();

      /* ------------- Mailer properties -------------------- */
      MailerConf mc = new MailerConf();
      register(new ObjectName(mc.getServiceName()), mc);
      mc.loadConfig();
      mailProps = mc.getConfig();

      /* ------------- Synch properties -------------------- */
      SynchConf sc = new SynchConf();
      register(new ObjectName(sc.getServiceName()), sc);
      sc.loadConfig();
      synchProps = sc.getConfig();

      /* ------------- Directory interface properties -------------------- */
      loadDirConfigs();

      /* ------------- System properties -------------------- */
      CardDavInfoConf ci = new CardDavInfoConf(unauthCardDavInfoNamePart);
      register(new ObjectName(ci.getServiceName()), ci);
      ci.loadConfig();
      ci.saveConfig();
      unauthCardDavInfo = ci.getConfig();

      ci = new CardDavInfoConf(authCardDavInfoNamePart);
      register(new ObjectName(ci.getServiceName()), ci);
      ci.loadConfig();
      ci.saveConfig();
      authCardDavInfo = ci.getConfig();

      configured = true;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      if (!configured) {
        stop();
      }
    }
  }

  private void loadDirConfigs() throws Throwable {
    ConfigurationStore cs = getStore().getStore("dirconfigs");

    List<String> names = cs.getConfigs();

    for (String dn: names) {
      ObjectName objectName = createObjectName("dirconfig", dn);

      /* Read the config so we can get the mbean class name. */

      DirConfigPropertiesImpl dCfg = (DirConfigPropertiesImpl)cs.getConfig(dn);

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
      DirConf<DirConfigPropertiesImpl> dc = (DirConf<DirConfigPropertiesImpl>)makeObject(mbeanClassName);
      dc.init(cs, objectName.toString(), dCfg);

      dc.saveConfig();
      dirConfigs.put(dn, dc);

      register(new ObjectName(dc.getServiceName()), dc);
    }
  }

  private SystemProperties getSystemProps(final boolean auth) throws CalFacadeException {
    try {
      ObjectName mbeanName;

      if (!auth) {
        mbeanName = getUnauthSyspropsName();
      } else {
        mbeanName = getAuthSyspropsName();
      }

      return (SystemProperties)getManagementContext().getAttribute(mbeanName, "Config");
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  void stop() {
    try {
      getManagementContext().stop();
    } catch (Throwable t){
      t.printStackTrace();
    }
  }
}