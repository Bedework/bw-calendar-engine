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

/** This class defines the various properties we need to make a connection
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
public class LdapConfigProperties extends DirConfigProperties {
  private String moduleType;

  private String initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
  private String securityAuthentication = "simple";

  private String securityProtocol = "NONE";

  private String providerUrl;

  private String groupContextDn;

  private String groupIdAttr = "cn";

  private String groupMemberAttr;

  private String groupMemberContextDn;

  private String groupMemberSearchAttr;

  private String groupMemberUserIdAttr = "uid";

  private String groupMemberGroupIdAttr = "cn";

  private String userDnPrefix;

  private String userDnSuffix;

  private String groupDnPrefix;

  private String groupDnSuffix;

  private String userObjectClass = "posixAccount";

  private String groupObjectClass = "groupOfUniqueNames";

  private String authDn;

  private String authPw;

  /** Used by configuration tools
   *
   * @param val
   */
  public void setModuleType(String val)  {
    moduleType  = val;
  }

  /**
   * @return String
   */
  public String getModuleType()  {
    return moduleType;
  }

  /**
   * @param val
   */
  public void setInitialContextFactory(String val)  {
    initialContextFactory  = val;
  }

  /**
   * @return String
   */
  public String getInitialContextFactory()  {
    return initialContextFactory;
  }

  /**
   * @param val
   */
  public void setSecurityAuthentication(String val)  {
    securityAuthentication  = val;
  }

  /**
   * @return String
   */
  public String getSecurityAuthentication()  {
    return securityAuthentication;
  }

  /** e.g. "ssl"
  *
  * @param val
  */
  public void setSecurityProtocol(String val)  {
    securityProtocol = val;
  }

  /** e.g "ssl"
  *
  * @return String val
  */
  public String getSecurityProtocol()  {
    return securityProtocol;
  }

  /** URL of ldap server
   *
   * @param val
   */
  public void setProviderUrl(String val)  {
    providerUrl = val;
  }

  /** URL of ldap server
   *
   * @return String val
   */
  public String getProviderUrl()  {
    return providerUrl;
  }

  /** Dn we search under for groups e.g. "ou=groups, dc=bedework, dc=org"
   *
   * @param val
   */
  public void setGroupContextDn(String val)  {
    groupContextDn = val;
  }

  /** Dn we search under for groups e.g. "ou=groups, dc=bedework, dc=org"
   *
   * @return String val
   */
  public String getGroupContextDn()  {
    return groupContextDn;
  }

  /** Attribute we search for to get a group
   *
   * @param val
   */
  public void setGroupIdAttr(String val)  {
    groupIdAttr = val;
  }

  /** Attribute we search for to get a group
   *
   * @return String val
   */
  public String getGroupIdAttr()  {
    return groupIdAttr;
  }

  /** Attribute we want back identifying a member
   *
   * @param val
   */
  public void setGroupMemberAttr(String val)  {
    groupMemberAttr = val;
  }

  /** Attribute we want back identifying a member
   *
   * @return String val
   */
  public String getGroupMemberAttr()  {
    return groupMemberAttr;
  }

  /** If non-null we treat the group member entry as a value to search for
   * under this context dn. Otherwise we treat the group member entry as the
   * actual dn.
   *
   * @param val
   */
  public void setGroupMemberContextDn(String val)  {
    groupMemberContextDn = val;
  }

  /** If non-null we treat the group member entry as a value to search for
   * under this context dn. Otherwise we treat the group member entry as the
   * actual dn.
   *
   * @return String val
   */
  public String getGroupMemberContextDn()  {
    return groupMemberContextDn;
  }

  /** If groupMemberContextDn is not null this is the attribute we search
   * for under that dn, otherwise we don't use this value.
   *
   * @param val
   */
  public void setGroupMemberSearchAttr(String val)  {
    groupMemberSearchAttr = val;
  }

  /** If groupMemberContextDn is not null this is the attribute we search
   * for under that dn, otherwise we don't use this value.
   *
   * @return String val
   */
  public String getGroupMemberSearchAttr()  {
    return groupMemberSearchAttr;
  }

  /** Attribute we want back for a member search giving the user account
   *
   * @param val
   */
  public void setGroupMemberUserIdAttr(String val)  {
    groupMemberUserIdAttr = val;
  }

  /** Attribute we want back for a member search giving the user account
   *
   * @return String val
   */
  public String getGroupMemberUserIdAttr()  {
    return groupMemberUserIdAttr;
  }

  /** Attribute we want back for a member search giving the group account
   *
   * @param val
   */
  public void setGroupMemberGroupIdAttr(String val)  {
    groupMemberGroupIdAttr = val;
  }

  /** Attribute we want back for a member search giving the group account
   *
   * @return String val
   */
  public String getGroupMemberGroupIdAttr()  {
    return groupMemberGroupIdAttr;
  }

  /** Prefix for user principal dn
   *
   * @param val
   */
  public void setUserDnPrefix(String val)  {
    userDnPrefix = val;
  }

  /** Prefix for user principal dn
   *
   * @return String val
   */
  public String getUserDnPrefix()  {
    return userDnPrefix;
  }

  /** Suffix for user principal dn
   *
   * @param val
   */
  public void setUserDnSuffix(String val)  {
    userDnSuffix = val;
  }

  /** Prefix for user principal dn
   *
   * @return String val
   */
  public String getUserDnSuffix()  {
    return userDnSuffix;
  }

  /** Prefix for group principal dn
   *
   * @param val
   */
  public void setGroupDnPrefix(String val)  {
    groupDnPrefix = val;
  }

  /** Prefix for group principal dn
   *
   * @return String val
   */
  public String getGroupDnPrefix()  {
    return groupDnPrefix;
  }

  /** Suffix for group principal dn
   *
   * @param val
   */
  public void setGroupDnSuffix(String val)  {
    groupDnSuffix = val;
  }

  /** Prefix for group principal dn
   *
   * @return String val
   */
  public String getGroupDnSuffix()  {
    return groupDnSuffix;
  }

  /** An object class which identifies an entry as a user
   *
   * @param val
   */
  public void setUserObjectClass(String val)  {
    userObjectClass = val;
  }

  /** An object class which identifies an entry as a user
   *
   * @return String val
   */
  public String getUserObjectClass()  {
    return userObjectClass;
  }

  /** An object class which identifies an entry as a group
   *
   * @param val
   */
  public void setGroupObjectClass(String val)  {
    groupObjectClass = val;
  }

  /** An object class which identifies an entry as a user
   *
   * @return String val
   */
  public String getGroupObjectClass()  {
    return groupObjectClass;
  }

  /** If we need an id to authenticate this is it.
   *
   * @param val
   */
  public void setAuthDn(String val)  {
    authDn = val;
  }

  /** If we need an id to authenticate this is it.
   *
   * @return String val
   */
  public String getAuthDn()  {
    return authDn;
  }

  /** If we need an id to authenticate this is the pw.
   *
   * @param val
   */
  public void setAuthPw(String val)  {
    authPw = val;
  }

  /** If we need an id to authenticate this is it.
   *
   * @return String val
   */
  public String getAuthPw()  {
    return authPw;
  }
}
