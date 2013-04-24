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
package org.bedework.calsvc.scheduling.hosts;

import edu.rpi.cmt.jmx.ConfBaseMBean;
import edu.rpi.cmt.jmx.MBeanInfo;

import java.util.List;

/** Service mbean for ischedule
 *
 * @author douglm
 *
 */
public interface BwHostsMBean extends ConfBaseMBean {
  /* ================================= Operations =========================== */

  @MBeanInfo("Display information for a host")
  String getHost(String hostname);

  /** Add an ischedule host.
   *
   * @param hostname
   * @param port
   * @param secure
   * @param url
   * @param principal
   * @param pw
   * @return status message
   */
  String addIscheduleHost(@MBeanInfo("hostname") String hostname,
                          @MBeanInfo("port") int port,
                          @MBeanInfo("secure") boolean secure,
                          @MBeanInfo("url") String url,
                          @MBeanInfo("principal") String principal,
                          @MBeanInfo("pw") String pw);

  /**
   * @return all hosts
   */
  List<String> listHosts();

  /** (Re)load the configurations
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configurations")
  String loadConfigs();
}
