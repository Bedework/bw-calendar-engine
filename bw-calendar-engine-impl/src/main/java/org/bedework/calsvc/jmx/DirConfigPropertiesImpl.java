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

import org.bedework.calfacade.configs.DirConfigProperties;
import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.misc.ToString;

/** This interface defines the various common directory interface properties.
 * and retrieve a group and user information via ldap.
 *
 * @author Mike Douglass
 */
@ConfInfo(elementName = "dir-config",
          type = "org.bedework.calfacade.configs.DirConfigProperties")
public class DirConfigPropertiesImpl extends ConfigBase<DirConfigPropertiesImpl>
        implements DirConfigProperties {
  private String mbeanClassName;

  private String domains;
  private String defaultDomain;

  @Override
  public void setMbeanClassName(final String val) {
    mbeanClassName = val;
  }

  @Override
  public String getMbeanClassName() {
    return mbeanClassName;
  }

  @Override
  public void setDomains(final String val)  {
    domains = val;
  }

  @Override
  public String getDomains()  {
    return domains;
  }

  @Override
  public void setDefaultDomain(final String val)  {
    defaultDomain = val;
  }

  @Override
  public String getDefaultDomain()  {
    return defaultDomain;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("mbeanClassName", getMbeanClassName());
    ts.append("domains", getDomains());
    ts.append("defaultDomain", getDefaultDomain());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
