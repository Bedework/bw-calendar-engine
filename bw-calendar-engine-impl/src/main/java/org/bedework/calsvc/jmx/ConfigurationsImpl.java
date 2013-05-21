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
import org.bedework.calfacade.configs.Configurations;
import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;

/** All the configuration objects used by CalSvc and its callers.
 *
 */
public final class ConfigurationsImpl extends Configurations {
  /* JMX config */
  //private static ManagementContext mcontext;
  private static boolean configured;

  private SystemProperties authSysProperties;

  private SystemProperties unAuthSysProperties;

  private SystemConfigurator sconf;

  private BasicSystemConfigurator basicConf;

  /**
   * @param authenticated
   * @throws CalFacadeException
   */
  public ConfigurationsImpl() throws CalFacadeException {
    try {
      checkMbeansInstalled();

      authSysProperties = new ROSystemProperties(SystemConfigurator.getProps(true));

      unAuthSysProperties = new ROSystemProperties(SystemConfigurator.getProps(false));
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  /**
   * @return basic system properties
   * @throws CalFacadeException
   */
  @Override
  public BasicSystemProperties getBasicSystemProperties() throws CalFacadeException {
    return BasicSystemConfigurator.getProps();
  }

  /**
   * @param auth
   * @return appropriate system properties
   * @throws CalFacadeException
   */
  @Override
  public SystemProperties getSystemProperties(final boolean auth) throws CalFacadeException {
    if (auth) {
      return authSysProperties;
    }
    return unAuthSysProperties;
  }

  private synchronized void checkMbeansInstalled() throws CalFacadeException {
    if (configured) {
      return;
    }

    try {
      basicConf = new BasicSystemConfigurator();
      basicConf.start();

      sconf = new SystemConfigurator();
      sconf.start();

      configured = true;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    } finally {
      if (!configured) {
        if (sconf != null) {
          sconf.stop();
          sconf = null;
        }
      }
    }
  }

}