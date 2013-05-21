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
import org.bedework.calfacade.exc.CalFacadeException;

import edu.rpi.cmt.jmx.ConfBase;

/** Get the basic configuration properties for the system.
 *
 */
class BasicSystemConfigurator extends ConfBase<BasicSystemPropertiesImpl> {
  private static String basicPropsNamePart = "basicSystem";

  BasicSystemConfigurator() {
    super("org.bedework.bwengine:service=" + basicPropsNamePart);

    setConfigName(basicPropsNamePart);
    setConfigPname(SystemConf.datauriPname);
  }

  static BasicSystemProperties basicProps;

  void start() {
    try {
      getManagementContext().start();

      loadConfig(BasicSystemPropertiesImpl.class);
      basicProps = new ROBasicSystemProperties(cfg);
    } catch (Throwable t){
      t.printStackTrace();
    }
  }

  void stop() {
    try {
      getManagementContext().stop();
    } catch (Throwable t){
      t.printStackTrace();
    }
  }

  static BasicSystemProperties getProps() throws CalFacadeException {
    return basicProps;
  }
}

