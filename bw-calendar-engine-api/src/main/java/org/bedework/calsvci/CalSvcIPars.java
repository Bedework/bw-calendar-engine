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
package org.bedework.calsvci;

import edu.rpi.sss.util.ToString;

import java.io.Serializable;

/** These are global parameters used by the CalSvc interface.
 *
 * @author Mike Douglass       douglm@rpi.edu
 */
public class CalSvcIPars implements Serializable {
  /** The authenticated user - null for guest
   */
  private String authUser;

  /** The current user we run as. null to use calSuite
   */
  private String user;

  /** The calendar suite name. user and calSuite null for guest.
   */
  private String calSuite;

  /** True if this is for public admin
   */
  private boolean publicAdmin;

  /** True if this is for a background service
   */
  private boolean service;

  /** The clientid from headers.
   */
  private String clientId;

  /** True if we should allow super user mode in non public admin
   */
  private boolean allowSuperUser;

  private boolean adminCanEditAllPublicCategories;
  private boolean adminCanEditAllPublicLocations;
  private boolean adminCanEditAllPublicContacts;

  /** True if this is a sessionless client, e.g. caldav or rss
   */
  private boolean sessionless;

  private boolean forRestore;

  /** True if this is a web application
   */
  private boolean webMode;

  /** Constructor for this object.
   *
   * @param authUser    String authenticated user of the application
   * @param user        String user to act as
   * @param calSuite    String calSuite name
   * @param publicAdmin true for admin
   * @param allowSuperUser  true to allow superuser mode in non-admin mode
   * @param service     true for a service
   * @param adminCanEditAllPublicCategories
   * @param adminCanEditAllPublicLocations
   * @param adminCanEditAllPublicContacts
   * @param sessionless true if this is a sessionless client
   */
  public CalSvcIPars(final String authUser,
                     final String user,
                     final String calSuite,

                     final boolean publicAdmin,
                     final boolean allowSuperUser,
                     final boolean service,

                     final boolean adminCanEditAllPublicCategories,
                     final boolean adminCanEditAllPublicLocations,
                     final boolean adminCanEditAllPublicContacts,

                     final boolean sessionless) {
    this(authUser, calSuite, publicAdmin, allowSuperUser, service,
         adminCanEditAllPublicCategories,
         adminCanEditAllPublicLocations,
         adminCanEditAllPublicContacts,
         sessionless);

    this.user = user;
  }

  /** Return new parameters for a service
   *
   * @param account
   * @param publicAdmin
   * @param allowSuperUser
   * @return CalSvcIPars
   */
  public static CalSvcIPars getServicePars(final String account,
                                           final boolean publicAdmin,
                                           final boolean allowSuperUser) {
    return new CalSvcIPars(account,
                           null,   // calsuite
                           publicAdmin,
                           allowSuperUser,
                           true,   // service
                           false,  // adminCanEditAllPublicCategories
                           false,  // adminCanEditAllPublicLocations
                           false,  // adminCanEditAllPublicSponsors
                           false); // sessionless
  }

  /** Return new pars for a system restore
   *
   * @param account
   * @return CalSvcIPars
   */
  public static CalSvcIPars getRestorePars(final String account) {
    CalSvcIPars p = new CalSvcIPars(account,
                                    null,   // calsuite
                                    true,   // publicAdmin,
                                    true,   // superUser,
                                    true,   // service
                                    true,   // adminCanEditAllPublicCategories
                                    true,   // adminCanEditAllPublicLocations
                                    true,   // adminCanEditAllPublicSponsors
                                    false); // sessionless

    p.forRestore = true;

    return p;
  }

  /** Return new parameters for caldav.
   *
   * @param authUser    String authenticated user of the application
   * @param runAsUser   String user to run as
   * @param clientId    The application we're acting for.
   * @param allowSuperUser  true to allow superuser mode in non-admin mode
   * @param service - true if this is a service call - e.g. iSchedule -
   *                rather than a real user.
   * @return CalSvcIPars
   */
  public static CalSvcIPars getCaldavPars(final String authUser,
                     final String runAsUser,
                     final String clientId,
                     final boolean allowSuperUser,
                     final boolean service) {
    CalSvcIPars pars = new CalSvcIPars(authUser,
                                       runAsUser,
                                       null,    // calsuite
                                       false,   // publicAdmin
                                       allowSuperUser,   // allow SuperUser
                                       service,
                                       false,  // adminCanEditAllPublicCategories
                                       false,  // adminCanEditAllPublicLocations
                                       false,  // adminCanEditAllPublicSponsors
                                       true);  // sessionless

    pars.setClientId(clientId);

    return pars;
  }

  /** Constructor we want for this object.
   *
   * @param authUser    String authenticated user of the application
   * @param calSuite    String calSuite name
   * @param publicAdmin true for admin
   * @param allowSuperUser  true to allow superuser mode in non-admin mode
   * @param service     true for a service
   * @param adminCanEditAllPublicCategories
   * @param adminCanEditAllPublicLocations
   * @param adminCanEditAllPublicContacts
   * @param sessionless true if this is a sessionless client
   */
  public CalSvcIPars(final String authUser,
                     final String calSuite,

                     final boolean publicAdmin,
                     final boolean allowSuperUser,
                     final boolean service,

                     final boolean adminCanEditAllPublicCategories,
                     final boolean adminCanEditAllPublicLocations,
                     final boolean adminCanEditAllPublicContacts,

                     final boolean sessionless) {
    this.authUser = authUser;
    this.calSuite = calSuite;
    this.publicAdmin = publicAdmin;
    this.allowSuperUser = allowSuperUser;
    this.service = service;
    this.adminCanEditAllPublicCategories = adminCanEditAllPublicCategories;
    this.adminCanEditAllPublicLocations = adminCanEditAllPublicLocations;
    this.adminCanEditAllPublicContacts = adminCanEditAllPublicContacts;
    this.sessionless = sessionless;
  }

  /**
   * @param val String auth user
   */
  public void setAuthUser(final String val) {
    authUser = val;
  }

  /**
   * @return String auth user
   */
  public String getAuthUser() {
    return authUser;
  }

  /**
   * @return String current user
   */
  public String getUser() {
    return user;
  }

  /**
   * @param val String calSuite
   */
  public void setCalSuite(final String  val) {
    calSuite = val;
  }

  /**
   * @return String
   */
  public String getCalSuite() {
    return calSuite;
  }

  /**
   * @param val String clientId
   */
  public void setClientId(final String  val) {
    clientId = val;
  }

  /**
   * @return String
   */
  public String getClientId() {
    return clientId;
  }

  /**
   * @return boolean true if this is a public admin object.
   */
  public boolean getPublicAdmin() {
    return publicAdmin;
  }

  /**
   * @return boolean true if this is a service.
   */
  public boolean getService() {
    return service;
  }

  /**
   * @return boolean true if we allow superuser mode in non-admin.
   */
  public boolean getAllowSuperUser() {
    return allowSuperUser;
  }

  /**
   * @return boolean
   */
  public boolean getAdminCanEditAllPublicCategories() {
    return adminCanEditAllPublicCategories;
  }

  /**
   * @return boolean
   */
  public boolean getAdminCanEditAllPublicLocations() {
    return adminCanEditAllPublicLocations;
  }

  /**
   * @return boolean
   */
  public boolean getAdminCanEditAllPublicContacts() {
    return adminCanEditAllPublicContacts;
  }

  /**
   * @param val boolean true if this is a sessionless client..
   */
  public void setSessionsless(final boolean val) {
    sessionless = val;
  }

  /**
   * @return boolean true if this is a sessionless client..
   */
  public boolean getSessionsless() {
    return sessionless;
  }

  /**
   * @param val boolean true if this is a web client..
   */
  public void setWebMode(final boolean val) {
    webMode = val;
  }

  /**
   * @return boolean true if this is a web client..
   */
  public boolean getWebMode() {
    return webMode;
  }

  /**
   * @return boolean true if this is for restore of system..
   */
  public boolean getForRestore() {
    return forRestore;
  }

  /**
   * @return boolean true for guest
   */
  public boolean isGuest() {
    return authUser == null;
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    ts.append("authUser", getAuthUser());
    ts.append("user", getUser());
    ts.append("calSuite", getCalSuite());
    ts.append("publicAdmin", getPublicAdmin());
    ts.append("service", getService());
    ts.append("adminCanEditAllPublicCategories()", getAdminCanEditAllPublicCategories());
    ts.append("adminCanEditAllPublicLocations()", getAdminCanEditAllPublicLocations());
    ts.append("adminCanEditAllPublicSponsors()", getAdminCanEditAllPublicContacts());
    ts.append("sessionless", getSessionsless());
    ts.append("forRestore", getForRestore());

    return ts.toString();
  }

  @Override
  public Object clone() {
    CalSvcIPars pars = new CalSvcIPars(getAuthUser(),
                                       getUser(),
                                       getCalSuite(),
                                       getPublicAdmin(),
                                       getAllowSuperUser(),
                                       getService(),
                                       getAdminCanEditAllPublicCategories(),
                                       getAdminCanEditAllPublicLocations(),
                                       getAdminCanEditAllPublicContacts(),
                                       getSessionsless());

    pars.setClientId(getClientId());
    pars.forRestore = getForRestore();

    return pars;
  }
}
