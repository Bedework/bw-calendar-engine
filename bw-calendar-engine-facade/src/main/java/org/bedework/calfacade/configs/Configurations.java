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
import org.bedework.calfacade.mail.MailConfigProperties;

import org.bedework.util.http.service.HttpConfig;

import java.io.Serializable;

/** Provides access to some of the basic configuration for the system.
 *
 * @author Mike Douglass   douglm  bedework.edu
 */
public interface Configurations extends Serializable {
  /**
   * @return basic system properties
   * @throws CalFacadeException
   */
  BasicSystemProperties getBasicSystemProperties() throws CalFacadeException;

  /**
   * @param auth
   * @return appropriate properties
   * @throws CalFacadeException
   */
  AuthProperties getAuthProperties(boolean auth) throws CalFacadeException;

  /**
   * @return system properties
   * @throws CalFacadeException
   */
  SystemProperties getSystemProperties() throws CalFacadeException;

  /**
   * @return http properties
   * @throws CalFacadeException
   */
  HttpConfig getHttpConfig() throws CalFacadeException;

  /**
   * @return indexing properties
   * @throws CalFacadeException
   */
  IndexProperties getIndexProperties() throws CalFacadeException;

  /**
   * @return mailer properties
   * @throws CalFacadeException
   */
  MailConfigProperties getMailConfigProperties() throws CalFacadeException;

  /**
   * @return synch properties
   * @throws CalFacadeException
   */
  SynchConfig getSynchConfig() throws CalFacadeException;

  /**
   * @param name
   * @return directory interface properties
   * @throws CalFacadeException
   */
  DirConfigProperties getDirConfig(String name) throws CalFacadeException;

  /**
   * @param auth
   * @return appropriate carddav info
   * @throws CalFacadeException
   */
  CardDavInfo getCardDavInfo(boolean auth) throws CalFacadeException;
}
