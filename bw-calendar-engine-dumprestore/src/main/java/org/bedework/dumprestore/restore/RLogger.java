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

import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

/** Logger for restore class.
 *
 * @author Mike Douglass   douglm@rpi.edu
 * @version 3.1
 */
public class RLogger implements Logged {
  private RestoreGlobals globals;

  RLogger(final RestoreGlobals globals) {
    this.globals = globals;
  }

  @Override
  public void warn(final String msg) {
    globals.warnings++;
    String ln = atLine();

    globals.messages.warningMessage(ln+ ": " + msg);
    Logged.super.warn(ln+ ": " + msg);
  }

  @Override
  public void error(final String msg) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln + ": " + msg);
    Logged.super.error(ln + ": " + msg);
  }

  @Override
  public void error(final String msg, final Throwable t) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln+ ": " + msg + ": " + t.getMessage());
    Logged.super.error(ln+ ": " + msg, t);
  }

  protected String atLine() {
    return "Approximately at line number " +
        globals.digester.getDocumentLocator().getLineNumber();
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
