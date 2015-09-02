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
package org.bedework.calfacade.mail;

import org.bedework.util.config.ConfInfo;
import org.bedework.util.config.ConfigBase;
import org.bedework.util.jmx.MBeanInfo;
import org.bedework.util.misc.ToString;

/** Properties for mailers.
 *
 * @author douglm
 *
 */
@ConfInfo(elementName = "mailer",
          type = "org.bedework.calfacade.mail.MailConfigProperties")
public class MailConfigPropertiesImpl extends ConfigBase<MailConfigPropertiesImpl>
        implements MailConfigProperties {
  private String protocol;

  private String serverUri;

  private String serverPort;

  private boolean starttls;

  private String serverUsername;

  private String serverPassword;

  private String from;

  private String subject;

  private boolean disabled;

  @Override
  public void setProtocol(final String val)  {
    protocol  = val;
  }

  @Override
  public String getProtocol()  {
    return protocol;
  }

  @Override
  public void setServerUri(final String val)  {
    serverUri  = val;
  }

  @Override
  public String getServerUri()  {
    return serverUri;
  }

  @Override
  public void setServerPort(final String val)  {
    serverPort  = val;
  }

  @Override
  public String getServerPort()  {
    return serverPort;
  }

  @Override
  public void setStarttls(final boolean val) {
    starttls = val;
  }

  @Override
  public boolean getStarttls() {
    return starttls;
  }

  @Override
  public void setServerUsername(final String val) {
    serverUsername = val;
  }

  @Override
  @MBeanInfo("User name if authentication is required.")
  public String getServerUsername() {
    return serverUsername;
  }

  @Override
  public void setServerPassword(final String val) {
    serverPassword = val;
  }

  @Override
  @MBeanInfo("User password if authentication is required.")
  public String getServerPassword() {
    return serverPassword;
  }

  @Override
  public void setFrom(final String val)  {
    from = val;
  }

  @Override
  public String getFrom()  {
    return from;
  }

  @Override
  public void setSubject(final String val)  {
    subject = val;
  }

  @Override
  public String getSubject()  {
    return subject;
  }

  @Override
  public void setDisabled(final boolean val)  {
    disabled = val;
  }

  @Override
  public boolean getDisabled()  {
    return disabled;
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("protocol", getProtocol());
    ts.append("serverUri", getServerUri());
    ts.append("serverPort", getServerPort());
    ts.append("starttls", getStarttls());
    ts.append("from", getFrom());
  }

  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
