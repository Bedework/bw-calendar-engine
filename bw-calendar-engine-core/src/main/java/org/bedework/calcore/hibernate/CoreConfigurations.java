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

import org.bedework.calfacade.exc.CalFacadeException;

import org.bedework.util.jmx.ConfBase;

import org.apache.log4j.Logger;

import javax.management.ObjectName;

/** All the configuration objects used by CalSvc and its callers.
 *
 */
@SuppressWarnings("rawtypes")
public final class CoreConfigurations extends ConfBase {
  /* Name of the property holding the location of the config data */
  static final String confuriPname = "org.bedework.bwcore.confuri";

  private static volatile Object lock = new Object();

  private static DbConfig dbConfig;

  private static CoreConfigurations configs;

  /**
   * @return a configs object
   * @throws CalFacadeException
   */
  public static CoreConfigurations getConfigs() {
    if (configs != null) {
      return configs;
    }

    synchronized (lock) {
      if (configs != null) {
        return configs;
      }

      try {
        configs = new CoreConfigurations();
      } catch (Throwable t) {
        Logger.getLogger(CoreConfigurations.class).error("Failed to configure", t);
      }
      return configs;
    }
  }

  /**
   * @throws CalFacadeException
   */
  private CoreConfigurations() throws CalFacadeException {
    super("org.bedework.bwengine.core:service=Conf");

    try {
      setConfigPname(confuriPname);

      loadConfigs();
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  @Override
  public String loadConfig() {
    return null;
  }

  /**
   * @return db config.
   * @throws CalFacadeException
   */
  public DbConfig getDbConfig() throws CalFacadeException {
    return dbConfig;
  }

  private void loadConfigs() throws Throwable {
    /* ------------- Db properties -------------------- */
    DbConf dc = new DbConf();
    register(new ObjectName(dc.getServiceName()), dc);
    dc.loadConfig();
    dbConfig = dc.getConfig();
  }

  @Override
  public void stop() {
    try {
      getManagementContext().stop();
    } catch (Throwable t){
      t.printStackTrace();
    }
  }
}