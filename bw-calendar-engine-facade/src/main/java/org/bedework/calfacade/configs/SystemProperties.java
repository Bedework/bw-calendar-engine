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

import edu.rpi.cmt.config.ConfInfo;
import edu.rpi.cmt.jmx.MBeanInfo;

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

 /** Set the timezones server uri
  *
  * @param val    String
  */
 void setTzServeruri(String val);

 /** Get the timezones server uri
  *
  * @return String   tzid
  */
 @MBeanInfo("the timezones server uri")
 String getTzServeruri();

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

  /** Set the defaultChangesNotifications
   *
   * @param val
   */
  void setDefaultChangesNotifications(boolean val);

  /** Get the defaultChangesNotifications
   *
   * @return flag
   */
  @MBeanInfo("default for change notifications")
  boolean getDefaultChangesNotifications();

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

  /** Set the user default view name
   *
   * @param val    String
   */
  void setDefaultUserViewName(String val);

  /** Get the user default view name
   *
   * @return String   default view name
   */
  @MBeanInfo("user default view name")
  String getDefaultUserViewName();

  /**
   * @param val
   */
  void setDefaultUserHour24(boolean val);

  /**
   * @return bool
   */
  @MBeanInfo("true for default to 24hr display.")
  boolean getDefaultUserHour24();

  /** Set the max description length for public events
   *
   * @param val    int max
   */
  public void setMaxPublicDescriptionLength(int val);

  /**
   *
   * @return int
   */
  @MBeanInfo("max description length for public events.")
  int getMaxPublicDescriptionLength();

  /** Set the max description length for user events
   *
   * @param val    int max
   */
  void setMaxUserDescriptionLength(int val);

  /**
   *
   * @return int
   */
  @MBeanInfo("max description length for user events.")
  public int getMaxUserDescriptionLength();

  /** Set the default quota for users. Probably an estimate
   *
   * @param val    long default
   */
  void setDefaultUserQuota(long val);

  /**
   *
   * @return long
   */
  @MBeanInfo("Default quota for users. Probably an estimate")
  long getDefaultUserQuota();

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

  /**
   * @param val boolean true if we are not including the full tz specification..
   */
  void setTimezonesByReference(boolean val);

  /**
   * @return true if we are not including the full tz specification
   */
  @MBeanInfo("true if we are NOT including the full tz specification in iCalendar output")
  boolean getTimezonesByReference();

  /** Set the max time span in years for a recurring event
   *
   * @param val    int max
   */
  void setMaxYears(int val);

  /** Get the max time span in years for a recurring event
   *
   * @return int
   */
  @MBeanInfo("Max time span in years for a recurring event")
  int getMaxYears();

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

  /** Set the solr url
   *
   * @param val
   */
  void setSolrURL(String val);

  /** Get the solr url
   *
   * @return flag
   */
  @MBeanInfo("solr url")
  String getSolrURL();

  /** Set the solr root
   *
   * @param val
   */
  void setSolrCoreAdmin(String val);

  /** Get the solr Root
   *
   * @return Root
   */
  @MBeanInfo("solr Root")
  String getSolrCoreAdmin();

  /** Set the solr public core
   *
   * @param val
   */
  void setSolrPublicCore(String val);

  /** Get the solr public core
   *
   * @return public core
   */
  @MBeanInfo("solr public core")
  String getSolrPublicCore();

  /** Set the solr user core
   *
   * @param val
   */
  void setSolrUserCore(String val);

  /** Get the solr user core
   *
   * @return user core
   */
  @MBeanInfo("solr user core")
  String getSolrUserCore();

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
