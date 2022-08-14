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
import org.bedework.util.logging.BwLogger;

import javax.management.ObjectName;

/** All the configuration objects used by calendar core and its callers.
 *
 */
@SuppressWarnings("rawtypes")
public final class CoreConfigurations extends ConfBase {
  /* Name of the directory holding the config data */
  static final String confDirName = "bwcore";

  private static final Object lock = new Object();

  private static DbConfig dbConfig;

  private static CoreConfigurations configs;

  /**
   * @return a configs object
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
      } catch (final Throwable t) {
        new BwLogger().setLoggedClass(CoreConfigurations.class)
                      .error("Failed to configure", t);
      }
      return configs;
    }
  }

  /**
   * @throws CalFacadeException on fatal error
   */
  private CoreConfigurations() throws CalFacadeException {
    super("org.bedework.bwengine.core:service=Conf",
          confDirName,
          null);

    try {

      loadConfigs();
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

  /**
   * @return db config.
   */
  public DbConfig getDbConfig() {
    return dbConfig;
  }

  private void loadConfigs() throws Throwable {
    /* ------------- Db properties -------------------- */
    final DbConf dc = new DbConf();
    register(new ObjectName(dc.getServiceName()), dc);
    dc.loadConfig();
    dbConfig = dc.getConfig();
  }

  @Override
  public void stop() {
    try {
      getManagementContext().stop();
    } catch (final Throwable t){
      t.printStackTrace();
    }
  }
}