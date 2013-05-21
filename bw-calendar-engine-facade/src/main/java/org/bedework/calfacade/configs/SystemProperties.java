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
 void setTzid(final String val);

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
 void setTzServeruri(final String val);

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
 void setSystemid(final String val);

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
  void setDefaultChangesNotifications(final boolean val);

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
  void setRootUsers(final String val);

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
  void setDefaultUserViewName(final String val);

  /** Get the user default view name
   *
   * @return String   default view name
   */
  @MBeanInfo("user default view name")
  String getDefaultUserViewName();

  /**
   * @param val
   */
  void setDefaultUserHour24(final boolean val);

  /**
   * @return bool
   */
  @MBeanInfo("true for default to 24hr display.")
  boolean getDefaultUserHour24();

  /** Set the max description length for public events
   *
   * @param val    int max
   */
  public void setMaxPublicDescriptionLength(final int val);

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
  void setMaxUserDescriptionLength(final int val);

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
  void setDefaultUserQuota(final long val);

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
  void setCalSoapWsWSDLURI(final String val);

  /** Get the calws soap web service WSDL uri - null for no service
   *
   * @return String
   */
  @MBeanInfo("Calws soap web service WSDL uri - null for no service")
  String getCalSoapWsWSDLURI();

  /**
   * @param val boolean true if we are not including the full tz specification..
   */
  void setTimezonesByReference(final boolean val);

  /**
   * @return true if we are not including the full tz specification
   */
  @MBeanInfo("true if we are NOT including the full tz specification in iCalendar output")
  boolean getTimezonesByReference();

  /** Set the max time span in years for a recurring event
   *
   * @param val    int max
   */
  void setMaxYears(final int val);

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
  void setUserauthClass(final String val);

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
  void setMailerClass(final String val);

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
  void setAdmingroupsClass(final String val);

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
  void setUsergroupsClass(final String val);

  /**
   *
   * @return String
   */
  @MBeanInfo("usergroups class")
  String getUsergroupsClass();

  /** Set the use solr flag
   *
   * @param val
   */
  void setUseSolr(final boolean val);

  /** Get the use solr flag
   *
   * @return flag
   */
  @MBeanInfo("use solr")
  boolean getUseSolr();

  /** Set the solr url
   *
   * @param val
   */
  void setSolrURL(final String val);

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
  void setSolrCoreAdmin(final String val);

  /** Get the solr Root
   *
   * @return Root
   */
  @MBeanInfo("solr Root")
  String getSolrCoreAdmin();

  /** Set the solr DefaultCore
   *
   * @param val
   */
  void setSolrDefaultCore(final String val);

  /** Get the solr DefaultCore
   *
   * @return DefaultCore
   */
  @MBeanInfo("solr DefaultCore")
  String getSolrDefaultCore();

  /** Set the supported locales list. This is maintained by getSupportedLocales and
   * setSupportedLocales and is a comma separated list of locales in the usual
   * form of the language, country and optional variant separated by "_"
   *
   * <p>The format is rigid, 2 letter language, 2 letter country. No spaces.
   *
   * @param val    String supported locales
   */
  void setLocaleList(final String val);

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
  void setEventregAdminToken(final String val);

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
  void setEventregUrl(final String val);

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
