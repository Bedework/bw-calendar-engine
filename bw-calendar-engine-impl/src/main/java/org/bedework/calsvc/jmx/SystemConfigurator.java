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

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;

import edu.rpi.cmt.jmx.ConfBase;

import javax.management.ObjectName;

/** All the configuration objects used by CalSvc and its callers.
 *
 */
class SystemConfigurator extends ConfBase<SystemPropertiesImpl> {
  private static String unauthSystemPropsNamePart = "unauthSystem";

  private static String authSystemPropsNamePart = "authSystem";

  SystemConfigurator() {
    super("org.bedework.bwengine:service=System");
  }

  void start() {
    try {
      getManagementContext().start();

      SystemConf conf = new SystemConf(unauthSystemPropsNamePart);
      register(getUnauthSyspropsName(), conf);
      conf.loadConfig();

      conf = new SystemConf(authSystemPropsNamePart);
      register(getAuthSyspropsName(), conf);
      conf.loadConfig();
    } catch (Throwable t){
      t.printStackTrace();
    }
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

  void stop() {
    try {
      getManagementContext().stop();
    } catch (Throwable t){
      t.printStackTrace();
    }
  }

  static SystemProperties getProps(final boolean auth) throws CalFacadeException {
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
}

