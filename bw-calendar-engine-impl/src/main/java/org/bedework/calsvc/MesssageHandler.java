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
package org.bedework.calsvc;

import org.bedework.sysevents.events.SysEvent;

/** Something that handles sysevents messages.
 *
 * @author Mike Douglass
 *
 */
public interface MesssageHandler {
  /** */
  public enum ProcessMessageResult {
    /** Unable to process the message after a failure indicating retry would
     * not work
     */
    FAILED_NORETRIES,

    /** Unable to process the message after retries */
    FAILED,

    /** e.g. not for this consumer */
    IGNORED,

    /** e.g. inbox message already deleted */
    NO_ACTION,

    /** Message successfully processed */
    PROCESSED,

    /** Something changed and caused a stale state exception. Should retry */
    STALE_STATE
  }
  /**
   * @param msg
   * @return ProcessMessageResult
   */
  public ProcessMessageResult processMessage(SysEvent msg);
}
