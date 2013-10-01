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
package org.bedework.chgnote;

import org.bedework.util.jmx.ConfBaseMBean;
import org.bedework.util.jmx.MBeanInfo;

/** MBean for chnage notification processing.
 *
 * @author douglm
 */
public interface BwChgNoteMBean extends ConfBaseMBean {
  /** Set the number of times we retry an incoming message when we get stale state
   * exceptions.
   *
   * @param val
   */
  public void setRetryLimit(int val);

  /**
   * @return current limit
   */
  @MBeanInfo("number of times we retry an incoming message when we get stale state" +
                     " exceptions")
  public int getRetryLimit();

  /**
   * @return some counts
   */
  @MBeanInfo("Some counts")
  public MesssageCounts getCounts();

  /** Lifecycle
   *
   */
  @MBeanInfo("Start processing change notifications")
  public void start();

  /** Lifecycle
   *
   */
  @MBeanInfo("Stop processing change notifications")
  public void stop();

  /** Lifecycle
   *
   * @return true if started
   */
  @MBeanInfo("Are we processing change notifications")
  public boolean isStarted();

}
