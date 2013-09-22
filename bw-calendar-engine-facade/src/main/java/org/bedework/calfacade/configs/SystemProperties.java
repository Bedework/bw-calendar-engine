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

import org.bedework.caldav.server.sysinterface.CalDAVSystemProperties;

import org.bedework.util.config.ConfInfo;
import org.bedework.util.jmx.MBeanInfo;

/** These are the system properties that the server needs to know about, either
 * because it needs to apply these limits or just to report them to clients.
 *
 * <p>Annotated to allow use by mbeans
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "system-properties")
public interface SystemProperties extends CalDAVSystemProperties {
  /** Set the default tzid
  *
  * @param val    String
  */
 void setTzid(String val);

 /** Get the default tzid.
  *
  * @return String   tzid
  */
 @MBeanInfo("Default timezone identifier")
 String getTzid();

 /** Set the default systemid
  *
  * @param val    String
  */
 void setSystemid(String val);

 /** Get the default systemid.
  *
  * @return String   systemid
  */
 @MBeanInfo("Systm identifier - used for uids etc.")
 String getSystemid();

  /** Set the root users list. This is a comma separated list of accounts that
   * have superuser status.
   *
   * @param val    String list of accounts
   */
  void setRootUsers(String val);

  /** Get the root users
   *
   * @return String   root users
   */
  @MBeanInfo("root users list. This is a comma separated list of accounts that" +
  		" have superuser status")
  String getRootUsers();

  /** Set the calws soap web service WSDL uri - null for no service
   *
   * @param val    String
   */
  void setCalSoapWsWSDLURI(String val);

  /** Get the calws soap web service WSDL uri - null for no service
   *
   * @return String
   */
  @MBeanInfo("Calws soap web service WSDL uri - null for no service")
  String getCalSoapWsWSDLURI();

  /** Set the userauth class
   *
   * @param val    String userauth class
   */
  void setUserauthClass(String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("userauth class")
  String getUserauthClass();

  /** Set the mailer class
   *
   * @param val    String mailer class
   */
  void setMailerClass(String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("mailer class")
  String getMailerClass();

  /** Set the admingroups class
   *
   * @param val    String admingroups class
   */
  void setAdmingroupsClass(String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("admingroups class")
  String getAdmingroupsClass();

  /** Set the usergroups class
   *
   * @param val    String usergroups class
   */
  void setUsergroupsClass(String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("usergroups class")
  String getUsergroupsClass();

  /** Set the supported locales list. This is maintained by getSupportedLocales and
   * setSupportedLocales and is a comma separated list of locales in the usual
   * form of the language, country and optional variant separated by "_"
   *
   * <p>The format is rigid, 2 letter language, 2 letter country. No spaces.
   *
   * @param val    String supported locales
   */
  void setLocaleList(String val);

  /** Get the supported locales
   *
   * @return String   supported locales
   */
  @MBeanInfo("Comma separated list of locales. " +
  		"The format is rigid, 2 letter language, 2 letter country. No spaces.")
  String getLocaleList();

  /** Set the token for event reg admins
   *
   * @param val
   */
  void setEventregAdminToken(String val);

  /** Get the token for event reg admins
   *
   * @return token
   */
  @MBeanInfo("The token for event reg admins")
  String getEventregAdminToken();

  /** Set the url for event reg service
   *
   * @param val
   */
  void setEventregUrl(String val);

  /** Get the url for event reg service
   *
   * @return token
   */
  @MBeanInfo("The url for event reg service")
  String getEventregUrl();

  /**
   * @return copy of this
   */
  SystemProperties cloneIt();
}
