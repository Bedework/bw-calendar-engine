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


/**
 * @author douglm
 *
 */
public class LdapDirConf extends DirConf<LdapConfigPropertiesImpl>
    implements LdapDirConfMBean {
  /* ========================================================================
   * Conf properties
   * ======================================================================== */

  @Override
  public void setInitialContextFactory(final String val)  {
    getConfig().setInitialContextFactory (val);
  }

  @Override
  public String getInitialContextFactory()  {
    return getConfig().getInitialContextFactory();
  }

  @Override
  public void setSecurityAuthentication(final String val)  {
    getConfig().setSecurityAuthentication (val);
  }

  @Override
  public String getSecurityAuthentication()  {
    return getConfig().getSecurityAuthentication();
  }

  @Override
  public void setSecurityProtocol(final String val)  {
    getConfig().setSecurityProtocol(val);
  }

  @Override
  public String getSecurityProtocol()  {
    return getConfig().getSecurityProtocol();
  }

  @Override
  public void setProviderUrl(final String val)  {
    getConfig().setProviderUrl(val);
  }

  @Override
  public String getProviderUrl()  {
    return getConfig().getProviderUrl();
  }

  @Override
  public void setGroupContextDn(final String val)  {
    getConfig().setGroupContextDn(val);
  }

  @Override
  public String getGroupContextDn()  {
    return getConfig().getGroupContextDn();
  }

  @Override
  public void setGroupIdAttr(final String val)  {
    getConfig().setGroupIdAttr(val);
  }

  @Override
  public String getGroupIdAttr()  {
    return getConfig().getGroupIdAttr();
  }

  @Override
  public void setGroupMemberAttr(final String val)  {
    getConfig().setGroupMemberAttr(val);
  }

  @Override
  public String getGroupMemberAttr()  {
    return getConfig().getGroupMemberAttr();
  }

  @Override
  public void setGroupMemberContextDn(final String val)  {
    getConfig().setGroupMemberContextDn(val);
  }

  @Override
  public String getGroupMemberContextDn()  {
    return getConfig().getGroupMemberContextDn();
  }

  @Override
  public void setGroupMemberSearchAttr(final String val)  {
    getConfig().setGroupMemberSearchAttr(val);
  }

  @Override
  public String getGroupMemberSearchAttr()  {
    return getConfig().getGroupMemberSearchAttr();
  }

  @Override
  public void setGroupMemberUserIdAttr(final String val)  {
    getConfig().setGroupMemberUserIdAttr(val);
  }

  @Override
  public String getGroupMemberUserIdAttr()  {
    return getConfig().getGroupMemberUserIdAttr();
  }

  @Override
  public void setGroupMemberGroupIdAttr(final String val)  {
    getConfig().setGroupMemberGroupIdAttr(val);
  }

  @Override
  public String getGroupMemberGroupIdAttr()  {
    return getConfig().getGroupMemberGroupIdAttr();
  }

  @Override
  public void setUserDnPrefix(final String val)  {
    getConfig().setUserDnPrefix(val);
  }

  @Override
  public String getUserDnPrefix()  {
    return getConfig().getUserDnPrefix();
  }

  @Override
  public void setUserDnSuffix(final String val)  {
    getConfig().setUserDnSuffix(val);
  }

  @Override
  public String getUserDnSuffix()  {
    return getConfig().getUserDnSuffix();
  }

  @Override
  public void setGroupDnPrefix(final String val)  {
    getConfig().setGroupDnPrefix(val);
  }

  @Override
  public String getGroupDnPrefix()  {
    return getConfig().getGroupDnPrefix();
  }

  @Override
  public void setGroupDnSuffix(final String val)  {
    getConfig().setGroupDnSuffix(val);
  }

  @Override
  public String getGroupDnSuffix()  {
    return getConfig().getGroupDnSuffix();
  }

  @Override
  public void setUserObjectClass(final String val)  {
    getConfig().setUserObjectClass(val);
  }

  @Override
  public String getUserObjectClass()  {
    return getConfig().getUserObjectClass();
  }

  @Override
  public void setGroupObjectClass(final String val)  {
    getConfig().setGroupObjectClass(val);
  }

  @Override
  public String getGroupObjectClass()  {
    return getConfig().getGroupObjectClass();
  }

  @Override
  public void setAuthDn(final String val)  {
    getConfig().setAuthDn(val);
  }

  @Override
  public String getAuthDn()  {
    return getConfig().getAuthDn();
  }

  @Override
  public void setAuthPw(final String val)  {
    getConfig().setAuthPw(val);
  }

  @Override
  public String getAuthPw()  {
    return getConfig().getAuthPw();
  }
}
