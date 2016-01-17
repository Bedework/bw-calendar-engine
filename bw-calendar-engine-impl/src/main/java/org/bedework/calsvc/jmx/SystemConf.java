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

import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.ConfBase;
import org.bedework.util.jmx.ConfigHolder;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author douglm
 *
 */
public class SystemConf extends ConfBase<SystemPropertiesImpl>
        implements SystemConfMBean, ConfigHolder<SystemPropertiesImpl> {
  /** Name of the property holding the location of the config data */
  public static final String confuriPname = "org.bedework.bwengine.confuri";

  private CalSvcI svci;

  private boolean running;

  private static final Set<String> dontKill = new TreeSet<>();

  static {
    dontKill.add(CalSvcIPars.logIdIndexer);
    dontKill.add(CalSvcIPars.logIdRestore);
  }

  private class AutoKillThread extends Thread {
    int ctr;
    boolean showedTrace;

    int terminated;

    int failedTerminations;

    AutoKillThread() {
      super("Bedework AutoKill");
    }

    @Override
    public void run() {
      while (running) {
        final int waitMins = getAutoKillMinutes();

        try {
          if (waitMins == 0) {
            // Display every 10 mins
            ctr++;
            if (ctr == 10) {
              info(listOpenIfs());
              ctr = 0;
            }
          } else {
            if (debug) {
              debug("About to check interfaces");
            }

            checkIfs(waitMins * 60);
          }
        } catch (final Throwable t) {
          if (!showedTrace) {
            error(t);
            showedTrace = true;
          } else {
            error(t.getMessage());
          }
        }

        if (running) {
          // Wait a bit before restarting
          try {
            synchronized (this) {
              final int wait;

              if (waitMins == 0) {
                // Autokill disabled - go to sleep for a minute
                wait = 60 * 1000;
              } else {
                wait = waitMins * 60 * 1000;
              }
              this.wait(wait);
            }
          } catch (final Throwable t) {
            error(t.getMessage());
          }
        }
      }
    }

    private void checkIfs(final int waitSecs) {
      try {
        getSvci();

        if (svci != null) {
          for (final CalSvcI.IfInfo ifInfo: svci.getIfInfo()) {
            if (ifInfo.getSeconds() > waitSecs) {
              if (dontKill.contains(ifInfo.getLogid())) {
                warn("Skipping long running task: " + ifInfo.getId());
                continue;
              }

              try {
                if (debug) {
                  debug("About to shut down interface " +
                                ifInfo.getId());
                }
                svci.kill(ifInfo.getId());
                terminated++;
              } catch (final Throwable t) {
                warn("Failed to terminate " +
                             ifInfo.getId());
                error(t);
                failedTerminations++;
              }
            }
          }
        }
      } catch (final Throwable t) {
        error(t);
      } finally {
        closeSvci();
      }
    }
  }

  private AutoKillThread autoKiller;

  /**
   * @param name of config
   */
  public SystemConf(final String name) {
    super(getServiceName(name));

    setConfigName(name);

    setConfigPname(confuriPname);
  }

  /**
   * @param name of config
   * @return service name for the mbean with this name
   */
  public static String getServiceName(final String name) {
    return "org.bedework.bwengine:service=" + name;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public void setTzid(final String val) {
    getConfig().setTzid(val);
  }

  @Override
  public String getTzid() {
    return getConfig().getTzid();
  }

  @Override
  public void setTzServeruri(final String val) {
    getConfig().setTzServeruri(val);
  }

  @Override
  public String getTzServeruri() {
    return getConfig().getTzServeruri();
  }

  @Override
  public void setSystemid(final String val) {
    getConfig().setSystemid(val);
  }

  @Override
  public String getSystemid() {
    return getConfig().getSystemid();
  }

  @Override
  public void setRootUsers(final String val) {
    getConfig().setRootUsers(val);
  }

  @Override
  public String getRootUsers() {
    return getConfig().getRootUsers();
  }

  @Override
  public void setFeatureFlags(final String val) {
    getConfig().setFeatureFlags(val);
  }

  @Override
  public String getFeatureFlags() {
    return getConfig().getFeatureFlags();
  }

  @Override
  public void setAdminContact(final String val) {
    getConfig().setAdminContact(val);
  }

  @Override
  public String getAdminContact() {
    return getConfig().getAdminContact();
  }

  @Override
  public void setIscheduleURI(final String val) {
    getConfig().setIscheduleURI(val);
  }

  @Override
  public String getIscheduleURI() {
    return getConfig().getIscheduleURI();
  }

  @Override
  public void setFburlServiceURI(final String val) {
    getConfig().setFburlServiceURI(val);
  }

  @Override
  public String getFburlServiceURI() {
    return getConfig().getFburlServiceURI();
  }

  /** Set the web calendar service uri - null for no web calendar service
   *
   * @param val    String
   */
  @Override
  public void setWebcalServiceURI(final String val) {
    getConfig().setWebcalServiceURI(val);
  }

  /** get the web calendar service uri - null for no web calendar service
   *
   * @return String
   */
  @Override
  public String getWebcalServiceURI() {
    return getConfig().getWebcalServiceURI();
  }

  /** Set the calws soap web service uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsURI(final String val) {
    getConfig().setCalSoapWsURI(val);
  }

  /** Get the calws soap web service uri - null for no service
   *
   * @return String
   */
  @Override
  public String getCalSoapWsURI() {
    return getConfig().getCalSoapWsURI();
  }

  /** Set the calws soap web service WSDL uri - null for no service
   *
   * @param val    String
   */
  @Override
  public void setCalSoapWsWSDLURI(final String val) {
    getConfig().setCalSoapWsWSDLURI(val);
  }

  @Override
  public String getCalSoapWsWSDLURI() {
    return getConfig().getCalSoapWsWSDLURI();
  }

  @Override
  public void setTimezonesByReference(final boolean val) {
    getConfig().setTimezonesByReference(val);
  }

  @Override
  public boolean getTimezonesByReference() {
    return getConfig().getTimezonesByReference();
  }

  @Override
  public void setUserauthClass(final String val) {
    getConfig().setUserauthClass(val);
  }

  @Override
  public String getUserauthClass() {
    return getConfig().getUserauthClass();
  }

  @Override
  public void setMailerClass(final String val) {
    getConfig().setMailerClass(val);
  }

  /**
   *
   * @return String
   */
  @Override
  public String getMailerClass() {
    return getConfig().getMailerClass();
  }

  /** Set the admingroups class
   *
   * @param val    String admingroups class
   */
  @Override
  public void setAdmingroupsClass(final String val) {
    getConfig().setAdmingroupsClass(val);
  }

  @Override
  public String getAdmingroupsClass() {
    return getConfig().getAdmingroupsClass();
  }

  @Override
  public void setUsergroupsClass(final String val) {
    getConfig().setUsergroupsClass(val);
  }

  @Override
  public String getUsergroupsClass() {
    return getConfig().getUsergroupsClass();
  }

  @Override
  public void setLocaleList(final String val) {
    getConfig().setLocaleList(val);
  }

  @Override
  public String getLocaleList() {
    return getConfig().getLocaleList();
  }

  @Override
  public void setEventregAdminToken(final String val) {
    getConfig().setEventregAdminToken(val);
  }

  @Override
  public String getEventregAdminToken() {
    return getConfig().getEventregAdminToken();
  }

  @Override
  public void setEventregUrl(final String val) {
    getConfig().setEventregUrl(val);
  }

  @Override
  public String getEventregUrl() {
    return getConfig().getEventregUrl();
  }

  @Override
  public void setCacheUrlPrefix(final String val) {
    getConfig().setCacheUrlPrefix(val);
  }

  @Override
  public String getCacheUrlPrefix() {
    return getConfig().getCacheUrlPrefix();
  }

  @Override
  public void setAutoKillMinutes(final int val) {
    getConfig().setAutoKillMinutes(val);
  }

  @Override
  public int getAutoKillMinutes() {
    return getConfig().getAutoKillMinutes();
  }

  @Override
  public void setSuggestionEnabled(final boolean val) {
    getConfig().setSuggestionEnabled(val);
  }

  @Override
  public boolean getSuggestionEnabled() {
    return getConfig().getSuggestionEnabled();
  }

  @Override
  public void setWorkflowEnabled(final boolean val) {
    getConfig().setWorkflowEnabled(val);
  }

  @Override
  public boolean getWorkflowEnabled() {
    return getConfig().getWorkflowEnabled();
  }

  @Override
  public void setWorkflowRoot(final String val) {
    getConfig().setWorkflowRoot(val);
  }

  @Override
  public String getWorkflowRoot() {
    return getConfig().getWorkflowRoot();
  }

  @Override
  public int getAutoKillTerminated() {
    return autoKiller.terminated;
  }

  @Override
  public int getAutoKillFailedTerminations() {
    return autoKiller.failedTerminations;
  }

  @Override
  public void setVpollMaxItems(final Integer val) {
    getConfig().setVpollMaxItems(val);
  }

  @Override
  public Integer getVpollMaxItems() {
    return getConfig().getVpollMaxItems();
  }

  @Override
  public void setVpollMaxActive(final Integer val) {
    getConfig().setVpollMaxActive(val);
  }

  @Override
  public Integer getVpollMaxActive() {
    return getConfig().getVpollMaxActive();
  }

  @Override
  public void setVpollMaxVoters(final Integer val) {
    getConfig().setVpollMaxVoters(val);
  }

  @Override
  public Integer getVpollMaxVoters() {
    return getConfig().getVpollMaxVoters();
  }

  @Override
  public void setSyseventsProperties(final List<String> val) {
    getConfig().setSyseventsProperties(val);
  }

  @Override
  @ConfInfo(collectionElementName = "syseventsProperty" ,
            elementType = "java.lang.String")
  public List<String> getSyseventsProperties() {
    return getConfig().getSyseventsProperties();
  }

  @Override
  public void addSyseventsProperty(final String name,
                                   final String val) {
    getConfig().addSyseventsProperty(name, val);
  }

  @Override
  public String getSyseventsProperty(final String name) {
    return getConfig().getSyseventsProperty(name);
  }

  @Override
  public void removeSyseventsProperty(final String name) {
    getConfig().removeSyseventsProperty(name);
  }

  @Override
  public void setSyseventsProperty(final String name,
                                   final String val) {
    getConfig().setSyseventsProperty(name, val);
  }

  @Override
  public void setDbStatsEnabled(final boolean enable) {
    try {
      getSvci();

      if (svci != null) {
        svci.setDbStatsEnabled(enable);
      }
    } catch (final Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }
  }

  @Override
  public boolean getDbStatsEnabled() {
    try {
      getSvci();

      return svci != null && svci.getDbStatsEnabled();
    } catch (final Throwable t) {
      error(t);
      return false;
    } finally {
      closeSvci();
    }
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public BwStats getStats()  {
    try {
      getSvci();

      if (svci == null) {
        return null;
      }

      return svci.getStats();
    } catch (final Throwable t) {
      error(t);
      return null;
    } finally {
      closeSvci();
    }
  }

  @Override
  public void dumpDbStats() {
    try {
      getSvci();

      if (svci != null) {
        svci.dumpDbStats();
      }
    } catch (final Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }
  }

  @Override
  public String listOpenIfs() {
    final StringBuilder sb = new StringBuilder();

    try {
      getSvci();

      if (svci != null) {
        for (final CalSvcI.IfInfo ifInfo: svci.getIfInfo()) {
          sb.append(ifInfo.getId());
          sb.append("\t");
          sb.append(ifInfo.getLastStateTime());
          sb.append("\t");
          sb.append(ifInfo.getState());
          sb.append("\t");
          sb.append(ifInfo.getSeconds());
          sb.append("\n");
        }
      }
    } catch (final Throwable t) {
      error(t);
    } finally {
      closeSvci();
    }

    return sb.toString();
  }

  @Override
  public String loadConfig() {
    final String res = loadConfig(SystemPropertiesImpl.class);

    try {
      autoKiller = new AutoKillThread();

      running = true;

      autoKiller.start();
    } catch (final Throwable t) {
      error(t);
      error("Unable to start autokill process");
    }

    return res;
  }

  /** Save the configuration.
   *
   */
  @Override
  public void putConfig() {
    saveConfig();
  }

  @Override
  public SystemProperties cloneIt() {
    return null;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /** Get an svci object and return it. Also embed it in this object.
   *
   * @return svci object
   * @throws org.bedework.calfacade.exc.CalFacadeException
   */
  private CalSvcI getSvci() throws CalFacadeException {
    if (getConfig() == null) {
      return null;
    }

    if ((svci != null) && svci.isOpen()) {
      return svci;
    }

    /* Extract a root user */

    if (getRootUsers() == null) {
      return null;
    }

    final String[] rootUsers = getRootUsers().split(",");

    if ((rootUsers.length == 0) || (rootUsers[0] == null)) {
      return null;
    }

    final CalSvcIPars pars = CalSvcIPars.getServicePars(getServiceName(),
                                                        rootUsers[0],
                                                        true,   // publicAdmin
                                                        true);   // Allow super user
    svci = new CalSvcFactoryDefault().getSvc(pars);

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  /**
   */
  private void closeSvci() {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try {
      svci.endTransaction();
    } catch (final Throwable t) {
      try {
        svci.close();
      } catch (final Throwable ignored) {
      }
    }

    try {
      svci.close();
    } catch (final Throwable ignored) {
    }
  }

  /* ========================================================================
   * Lifecycle
   * ======================================================================== */
}
