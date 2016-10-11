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
package org.bedework.dumprestore.restore;

import org.bedework.calsvci.RestoreIntf.RestoreLogger;

import org.apache.log4j.Logger;

/** Logger for restore class.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 3.1
 */
public class RLogger implements RestoreLogger {
  private transient Logger log;

  private RestoreGlobals globals;

  RLogger(final RestoreGlobals globals) {
    log = Logger.getLogger(this.getClass());

    this.globals = globals;
  }

  @Override
  public void info(final String msg) {
    log.info(msg);
  }

  @Override
  public void warn(final String msg) {
    globals.warnings++;
    String ln = atLine();

    globals.messages.warningMessage(ln+ ": " + msg);
    log.warn(ln+ ": " + msg);
  }

  @Override
  public void error(final String msg) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln + ": " + msg);
    log.error(ln + ": " + msg);
  }

  @Override
  public void error(final String msg, final Throwable t) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln+ ": " + msg + ": " + t.getMessage());
    log.error(ln+ ": " + msg, t);
  }

  @Override
  public void debug(final String msg) {
    log.debug(msg);
  }

  @Override
  public boolean isDebugEnabled() {
    return log.isDebugEnabled();
  }

  protected String atLine() {
    return "Approximately at line number " +
        globals.digester.getDocumentLocator().getLineNumber();
  }
}
