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
package org.bedework.calfacade.mail;

import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.MBeanInfo;

/** Properties for mailers.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "mailer")
public interface MailConfigProperties {
  /** valid protocol for which an implementation exists, e.g "imap", "smtp"
   *
   * @param val
   */
  void setProtocol(String val);

  /**
   * @return String
   */
  @MBeanInfo("valid protocol for which an implementation exists, e.g \"imap\", \"smtp\".")
  String getProtocol();

  /** Implementation for the selected protocol
   *
   * @param val
   */
  void setProtocolClass(String val);

  /**
   * @return String
   */
  @MBeanInfo("Implementation for the selected protocol.")
  String getProtocolClass();

  /** Where we send it.
   *
   * @param val
   */
  void setServerUri(String val);

  /**
   * @return String
   */
  @MBeanInfo("Location of server.")
  String getServerUri();

  /**
   * @param val
   */
  void setServerPort(String val);

  /**
   * @return String
   */
  @MBeanInfo("The server port.")
  String getServerPort();

  /** Address we use when none supplied
   *
   * @param val
   */
  void setFrom(String val);

  /**
   * @return String
   */
  @MBeanInfo("Address we use when none supplied.")
  String getFrom();

  /** Subject we use when none supplied
   *
   * @param val
   */
  void setSubject(String val);

  /**
   * @return String
   */
  @MBeanInfo("Subject we use when none supplied.")
  String getSubject();

  /** Allows mailer to be disabled
   *
   * @param val
   */
  void setDisabled(boolean val);

  /**
   * @return boolean
   */
  @MBeanInfo("Allows mailer to be disabled - mail will be discarded.")
  boolean getDisabled();
}
