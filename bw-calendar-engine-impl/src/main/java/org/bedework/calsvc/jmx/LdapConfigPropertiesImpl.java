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
package org.bedework.calsvc.jmx;

import org.bedework.calfacade.configs.LdapConfigProperties;

import edu.rpi.cmt.config.ConfInfo;

/** This class defines the various properties we need to make a connection
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "dir-config",
          type = "org.bedework.calfacade.configs.LdapConfigProperties")
public class LdapConfigPropertiesImpl extends DirConfigPropertiesImpl
        implements LdapConfigProperties {
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

  @Override
  public void setInitialContextFactory(final String val)  {
    initialContextFactory  = val;
  }

  @Override
  public String getInitialContextFactory()  {
    return initialContextFactory;
  }

  @Override
  public void setSecurityAuthentication(final String val)  {
    securityAuthentication  = val;
  }

  @Override
  public String getSecurityAuthentication()  {
    return securityAuthentication;
  }

  @Override
  public void setSecurityProtocol(final String val)  {
    securityProtocol = val;
  }

  @Override
  public String getSecurityProtocol()  {
    return securityProtocol;
  }

  @Override
  public void setProviderUrl(final String val)  {
    providerUrl = val;
  }

  @Override
  public String getProviderUrl()  {
    return providerUrl;
  }

  @Override
  public void setGroupContextDn(final String val)  {
    groupContextDn = val;
  }

  @Override
  public String getGroupContextDn()  {
    return groupContextDn;
  }

  @Override
  public void setGroupIdAttr(final String val)  {
    groupIdAttr = val;
  }

  @Override
  public String getGroupIdAttr()  {
    return groupIdAttr;
  }

  @Override
  public void setGroupMemberAttr(final String val)  {
    groupMemberAttr = val;
  }

  @Override
  public String getGroupMemberAttr()  {
    return groupMemberAttr;
  }

  @Override
  public void setGroupMemberContextDn(final String val)  {
    groupMemberContextDn = val;
  }

  @Override
  public String getGroupMemberContextDn()  {
    return groupMemberContextDn;
  }

  @Override
  public void setGroupMemberSearchAttr(final String val)  {
    groupMemberSearchAttr = val;
  }

  @Override
  public String getGroupMemberSearchAttr()  {
    return groupMemberSearchAttr;
  }

  @Override
  public void setGroupMemberUserIdAttr(final String val)  {
    groupMemberUserIdAttr = val;
  }

  @Override
  public String getGroupMemberUserIdAttr()  {
    return groupMemberUserIdAttr;
  }

  @Override
  public void setGroupMemberGroupIdAttr(final String val)  {
    groupMemberGroupIdAttr = val;
  }

  @Override
  public String getGroupMemberGroupIdAttr()  {
    return groupMemberGroupIdAttr;
  }

  @Override
  public void setUserDnPrefix(final String val)  {
    userDnPrefix = val;
  }

  @Override
  public String getUserDnPrefix()  {
    return userDnPrefix;
  }

  @Override
  public void setUserDnSuffix(final String val)  {
    userDnSuffix = val;
  }

  @Override
  public String getUserDnSuffix()  {
    return userDnSuffix;
  }

  @Override
  public void setGroupDnPrefix(final String val)  {
    groupDnPrefix = val;
  }

  @Override
  public String getGroupDnPrefix()  {
    return groupDnPrefix;
  }

  @Override
  public void setGroupDnSuffix(final String val)  {
    groupDnSuffix = val;
  }

  @Override
  public String getGroupDnSuffix()  {
    return groupDnSuffix;
  }

  @Override
  public void setUserObjectClass(final String val)  {
    userObjectClass = val;
  }

  @Override
  public String getUserObjectClass()  {
    return userObjectClass;
  }

  @Override
  public void setGroupObjectClass(final String val)  {
    groupObjectClass = val;
  }

  @Override
  public String getGroupObjectClass()  {
    return groupObjectClass;
  }

  @Override
  public void setAuthDn(final String val)  {
    authDn = val;
  }

  @Override
  public String getAuthDn()  {
    return authDn;
  }

  @Override
  public void setAuthPw(final String val)  {
    authPw = val;
  }

  @Override
  public String getAuthPw()  {
    return authPw;
  }
}
