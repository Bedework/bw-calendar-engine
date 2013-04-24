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

import java.io.Serializable;

/** This class defines the various properties we need to make a connection
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
public class SystemRoots implements Serializable {
  /* Principals */
  private String principalRoot;
  private String userPrincipalRoot;
  private String groupPrincipalRoot;
  private String bwadmingroupPrincipalRoot;
  private String resourcePrincipalRoot;
  private String venuePrincipalRoot;
  private String ticketPrincipalRoot;
  private String hostPrincipalRoot;

  /** Set the principal root e.g. "/principals"
   *
   * @param val    String
   */
  public void setPrincipalRoot(final String val) {
    principalRoot = val;
  }

  /** get the principal root e.g. "/principals"
   *
   * @return String
   */
  public String getPrincipalRoot() {
    return principalRoot;
  }

  /** Set the user principal root e.g. "/principals/users"
   *
   * @param val    String
   */
  public void setUserPrincipalRoot(final String val) {
    userPrincipalRoot = val;
  }

  /** get the principal root e.g. "/principals/users"
   *
   * @return String
   */
  public String getUserPrincipalRoot() {
    return userPrincipalRoot;
  }

  /** Set the group principal root e.g. "/principals/groups"
   *
   * @param val    String
   */
  public void setGroupPrincipalRoot(final String val) {
    groupPrincipalRoot = val;
  }

  /** get the group principal root e.g. "/principals/groups"
   *
   * @return String
   */
  public String getGroupPrincipalRoot() {
    return groupPrincipalRoot;
  }

  /** Set the bedework admin group principal root
   * e.g. "/principals/groups/bwadmin"
   *
   * @param val    String
   */
  public void setBwadmingroupPrincipalRoot(final String val) {
    bwadmingroupPrincipalRoot = val;
  }

  /** Get the bedework admin group principal root
   * e.g. "/principals/groups/bwadmin"
   *
   * @return String
   */
  public String getBwadmingroupPrincipalRoot() {
    return bwadmingroupPrincipalRoot;
  }

  /** Set the resource principal root e.g. "/principals/resources"
   *
   * @param val    String
   */
  public void setResourcePrincipalRoot(final String val) {
    resourcePrincipalRoot = val;
  }

  /** get the resource principal root e.g. "/principals/resources"
   *
   * @return String
   */
  public String getResourcePrincipalRoot() {
    return resourcePrincipalRoot;
  }

  /** Set the venue principal root e.g. "/principals/locations"
   *
   * @param val    String
   */
  public void setVenuePrincipalRoot(final String val) {
    venuePrincipalRoot = val;
  }

  /** get the venue principal root e.g. "/principals/locations"
   *
   * @return String
   */
  public String getVenuePrincipalRoot() {
    return venuePrincipalRoot;
  }

  /** Set the ticket principal root e.g. "/principals/tickets"
   *
   * @param val    String
   */
  public void setTicketPrincipalRoot(final String val) {
    ticketPrincipalRoot = val;
  }

  /** get the ticket principal root e.g. "/principals/tickets"
   *
   * @return String
   */
  public String getTicketPrincipalRoot() {
    return ticketPrincipalRoot;
  }

  /** Set the host principal root e.g. "/principals/hosts"
   *
   * @param val    String
   */
  public void setHostPrincipalRoot(final String val) {
    hostPrincipalRoot = val;
  }

  /** get the host principal root e.g. "/principals/hosts"
   *
   * @return String
   */
  public String getHostPrincipalRoot() {
    return hostPrincipalRoot;
  }
}
