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
package org.bedework.calsvc.notifications;

/**
 * User: mike Date: 8/16/15 Time: 11:31
 */
public class NotifyMessage {
  private final String system;
  private final String token;
  private String href;
  private String resourceName;

  public NotifyMessage(final String system,
                       final String token) {
    this.system = system;
    this.token = token;
  }

  public String getSystem() {
    return system;
  }

  public String getToken() {
    return token;
  }

  public String getHref() {
    return href;
  }

  public void setHref(final String val) {
    href = val;
  }

  public String getResourceName() {
    return resourceName;
  }

  public void setResourceName(final String val) {
    resourceName = val;
  }
}
