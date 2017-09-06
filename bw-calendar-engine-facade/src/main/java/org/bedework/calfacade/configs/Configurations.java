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

import org.bedework.calfacade.mail.MailConfigProperties;
import org.bedework.util.http.service.HttpConfig;

import java.io.Serializable;

/** Provides access to some of the basic configuration for the system.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public interface Configurations extends Serializable {
  /**
   * @return basic system properties
   */
  BasicSystemProperties getBasicSystemProperties();

  /**
   * @param auth true for authenticated user
   * @return appropriate properties
   */
  AuthProperties getAuthProperties(boolean auth);

  /**
   * @return system properties
   */
  SystemProperties getSystemProperties();

  /**
   * @return http properties
   */
  HttpConfig getHttpConfig();

  /**
   * @return dump/restore properties
   */
  DumpRestoreProperties getDumpRestoreProperties();

  /**
   * @return indexing properties
   */
  IndexProperties getIndexProperties();

  /**
   * @return mailer properties
   */
  MailConfigProperties getMailConfigProperties();

  /**
   * @return notification properties
   */
  NotificationProperties getNotificationProps();

  /**
   * @return synch properties
   */
  SynchConfig getSynchConfig();

  /**
   * @param name of config
   * @return directory interface properties
   */
  DirConfigProperties getDirConfig(String name);

  /**
   * @param auth true for authenticated user
   * @return appropriate carddav info
   */
  CardDavInfo getCardDavInfo(boolean auth);
}
