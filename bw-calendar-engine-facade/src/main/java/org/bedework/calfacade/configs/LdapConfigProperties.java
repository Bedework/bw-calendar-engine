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

/** This interface defines the various properties we need to make a connection
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
public interface LdapConfigProperties extends DirConfigProperties {
  /**
   * @param val
   */
  void setInitialContextFactory(String val);

  /**
   * @return String
   */
  String getInitialContextFactory();

  /**
   * @param val
   */
  void setSecurityAuthentication(String val);

  /**
   * @return String
   */
  String getSecurityAuthentication();

  /** e.g. "ssl"
  *
  * @param val
  */
  void setSecurityProtocol(String val);

  /** e.g "ssl"
  *
  * @return String val
  */
  String getSecurityProtocol();

  /** URL of ldap server
   *
   * @param val
   */
  void setProviderUrl(String val);

  /** URL of ldap server
   *
   * @return String val
   */
  String getProviderUrl();

  /** Dn we search under for groups e.g. "ou=groups, dc=bedework, dc=org"
   *
   * @param val
   */
  void setGroupContextDn(String val);

  /** Dn we search under for groups e.g. "ou=groups, dc=bedework, dc=org"
   *
   * @return String val
   */
  String getGroupContextDn();

  /** Attribute we search for to get a group
   *
   * @param val
   */
  void setGroupIdAttr(String val);

  /** Attribute we search for to get a group
   *
   * @return String val
   */
  String getGroupIdAttr();

  /** Attribute we want back identifying a member
   *
   * @param val
   */
  void setGroupMemberAttr(String val);

  /** Attribute we want back identifying a member
   *
   * @return String val
   */
  String getGroupMemberAttr();

  /** If non-null we treat the group member entry as a value to search for
   * under this context dn. Otherwise we treat the group member entry as the
   * actual dn.
   *
   * @param val
   */
  void setGroupMemberContextDn(String val);

  /** If non-null we treat the group member entry as a value to search for
   * under this context dn. Otherwise we treat the group member entry as the
   * actual dn.
   *
   * @return String val
   */
  String getGroupMemberContextDn();

  /** If groupMemberContextDn is not null this is the attribute we search
   * for under that dn, otherwise we don't use this value.
   *
   * @param val
   */
  void setGroupMemberSearchAttr(String val);

  /** If groupMemberContextDn is not null this is the attribute we search
   * for under that dn, otherwise we don't use this value.
   *
   * @return String val
   */
  String getGroupMemberSearchAttr();

  /** Attribute we want back for a member search giving the user account
   *
   * @param val
   */
  void setGroupMemberUserIdAttr(String val);

  /** Attribute we want back for a member search giving the user account
   *
   * @return String val
   */
  String getGroupMemberUserIdAttr();

  /** Attribute we want back for a member search giving the group account
   *
   * @param val
   */
  void setGroupMemberGroupIdAttr(String val);

  /** Attribute we want back for a member search giving the group account
   *
   * @return String val
   */
  String getGroupMemberGroupIdAttr();

  /** Prefix for user principal dn
   *
   * @param val
   */
  void setUserDnPrefix(String val);

  /** Prefix for user principal dn
   *
   * @return String val
   */
  String getUserDnPrefix();

  /** Suffix for user principal dn
   *
   * @param val
   */
  void setUserDnSuffix(String val);

  /** Prefix for user principal dn
   *
   * @return String val
   */
  String getUserDnSuffix();

  /** Prefix for group principal dn
   *
   * @param val
   */
  void setGroupDnPrefix(String val);

  /** Prefix for group principal dn
   *
   * @return String val
   */
  String getGroupDnPrefix();

  /** Suffix for group principal dn
   *
   * @param val
   */
  void setGroupDnSuffix(String val) ;

  /** Prefix for group principal dn
   *
   * @return String val
   */
  String getGroupDnSuffix();

  /** An object class which identifies an entry as a user
   *
   * @param val
   */
  void setUserObjectClass(String val);

  /** An object class which identifies an entry as a user
   *
   * @return String val
   */
  String getUserObjectClass();

  /** An object class which identifies an entry as a group
   *
   * @param val
   */
  void setGroupObjectClass(String val);

  /** An object class which identifies an entry as a user
   *
   * @return String val
   */
  String getGroupObjectClass();

  /** If we need an id to authenticate this is it.
   *
   * @param val
   */
  void setAuthDn(String val);

  /** If we need an id to authenticate this is it.
   *
   * @return String val
   */
  String getAuthDn();

  /** If we need an id to authenticate this is the pw.
   *
   * @param val
   */
  void setAuthPw(String val);

  /** If we need an id to authenticate this is it.
   *
   * @return String val
   */
  String getAuthPw();
}
