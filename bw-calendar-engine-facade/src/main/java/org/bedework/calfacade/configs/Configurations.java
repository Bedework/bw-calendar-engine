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
package org.bedework.calfacade.configs;

import org.bedework.calfacade.exc.CalFacadeException;

import java.io.Serializable;

/** Provides access to some of the basic configuration for the system.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class Configurations implements Serializable {
  /**
   * @return basic system properties
   * @throws CalFacadeException
   */
  public abstract BasicSystemProperties getBasicSystemProperties() throws CalFacadeException;

  /**
   * @param auth
   * @return appropriate system properties
   * @throws CalFacadeException
   */
  public abstract SystemProperties getSystemProperties(final boolean auth) throws CalFacadeException;
}
