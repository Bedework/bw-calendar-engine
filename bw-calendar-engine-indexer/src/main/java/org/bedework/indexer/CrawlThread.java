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
package org.bedework.indexer;

import org.apache.log4j.Logger;

/** Runnable thread process.
 *
 * @author douglm
 *
 */
public class CrawlThread extends Thread {
  private Processor proc;

  CrawlThread(final String name,
              final Processor proc) {
    super(name);
    this.proc = proc;
  }

  @Override
  public void run() {
    try {
      proc.process();
    } catch (Throwable t) {
      Logger.getLogger(this.getClass()).error(this, t);
    } finally {
    }
  }
}
