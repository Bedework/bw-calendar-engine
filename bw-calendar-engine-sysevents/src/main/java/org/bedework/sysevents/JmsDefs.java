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
package org.bedework.sysevents;

import java.io.Serializable;

import javax.jms.Session;

/** Definitionsrelating to bedework use of jms.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public interface JmsDefs extends Serializable {
  /** */
  public static int ackMode = Session.AUTO_ACKNOWLEDGE;

  /** */
  public static final String syseventsQueueName = "sysevents";

  /** */
  public static final String syseventsLogQueueName = "syseventslog";

  /** */
  public static final String monitorQueueName = "monitor";

  /** */
  public static final String changesQueueName = "changes";

  /** */
  public static final String crawlerQueueName = "crawler";

  /** */
  public static final String schedulerInQueueName = "schedulerIn";

  /** */
  public static final String schedulerOutQueueName = "schedulerOut";

  /** */
  public static final boolean useTransactions = false;
}
