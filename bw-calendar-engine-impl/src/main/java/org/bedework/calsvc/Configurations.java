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
package org.bedework.calsvc;

import org.bedework.calfacade.configs.SystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.jmx.SystemConf;

import edu.rpi.cmt.jmx.ManagementContext;

import javax.management.ObjectName;

/** All the configuration objects used by CalSvc and its callers.
 *
 */
public final class Configurations {
  private boolean authenticated;

  /* JMX config */
  private static ManagementContext mcontext = new ManagementContext();

  private SystemProperties sysProperties;

  Configurations(final boolean authenticated) throws CalFacadeException {
    try {
      this.authenticated = authenticated;
      ObjectName mbeanName;

      if (!authenticated) {
        // Get unauth system properties

        mbeanName = new ObjectName(SystemConf.getServiceName("unauthSystem"));
      } else {
        // Get auth system properties

        mbeanName = new ObjectName(SystemConf.getServiceName("authSystem"));
      }

      sysProperties = (SystemProperties)mcontext.getAttribute(mbeanName, "config");
    } catch (CalFacadeException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new CalFacadeException(t);
    }
  }

  boolean authenticated() {
    return authenticated;
  }

  SystemProperties getSystemProperties() throws CalFacadeException {
    return sysProperties;
  }
}