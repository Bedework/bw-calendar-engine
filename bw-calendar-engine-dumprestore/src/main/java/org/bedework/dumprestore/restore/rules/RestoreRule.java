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

package org.bedework.dumprestore.restore.rules;

import org.bedework.calfacade.BwVersion;
import org.bedework.dumprestore.restore.RestoreGlobals;

import org.apache.commons.digester.Rule;
import org.apache.log4j.Logger;

/**
 * @author Mike Douglass   douglm@rpi.edu
 * @version 1.0
 */
public class RestoreRule extends Rule {
  /** On stack when restoring date-time
   */
  public static class DateTimeValues {
    /** local value */
    public String dtval;

    /** */
    public String tzid;

    /** true for date only */
    public boolean dateType;

    /** UTC value */
    public String date;
  }

  protected RestoreGlobals globals;

  private transient Logger log;

  protected boolean debug;

  RestoreRule(final RestoreGlobals globals) {
    this.globals = globals;
    debug = getLog().isDebugEnabled();
  }

  protected void push(final Object o) {
    getDigester().push(o);
  }

  protected Object top() {
    return getDigester().peek();
  }

  protected Object getTop(final Class cl, final String name) throws Exception {
    if (cl.isInstance(top())) {
      return top();
    }

    error("Wrong class on top for tag " + name +
        ". Expected " + cl.getName());
    error("Stack:  " + top().getClass().getName());

    pop();
    while (top() != null) {
      error("Stack:  " + top().getClass().getName());
      pop();
    }

    throw new Exception("org.bedework.dumprestore.stacktopclasserror");
  }

  protected void dumpAndEmptyStack() {
    while (top() != null) {
      error("Stack:  " + top().getClass().getName());
      pop();
    }
  }

  protected Object pop() {
    return getDigester().pop();
  }

  /**
   * @return true if we are restoring current version data.
   */
  public boolean currentVersion() {
    if (globals.bedeworkVersion == null) {
      return false;
    }

    return globals.bedeworkVersion.equals(BwVersion.bedeworkVersion);
  }

  /**
   * @param major
   * @param minor
   * @return true if we know we are at or after the version given.
   */
  public boolean beforeVersion(final int major, final int minor) {
    return !thisOrAfterVersion(major, minor);
  }

  /**
   * @param major
   * @param minor
   * @return true if we know we are at or after the version given.
   */
  public boolean thisOrAfterVersion(final int major, final int minor) {
    if ((globals.bedeworkVersion == null) ||
        (globals.bedeworkMajorVersion == RestoreGlobals.bedeworkVersionDefault) ||
        (globals.bedeworkMinorVersion == RestoreGlobals.bedeworkVersionDefault)) {
      return false;
    }

    if ((globals.bedeworkMajorVersion < major)) {
      return false;
    }

    if ((globals.bedeworkMajorVersion > major)) {
      return true;
    }

    return globals.bedeworkMinorVersion >= minor;
  }

  protected String atLine() {
    return "Approximately at line number " +
          globals.digester.getDocumentLocator().getLineNumber();
  }

  protected void unknownTag(final String name) throws Exception {
    //String ln = atLine();

    error("Unknown tag " + name);
    //handleException(new Exception(ln + ": Unknown tag " + name + " Matched: " +
      //                            getDigester().getMatch()));
  }

  protected void handleException(final Throwable t) throws Exception {
    error("Exception: matched: " + getDigester().getMatch());

    if (!(t instanceof Exception)) {
      throw new Exception(t);
    }

    throw (Exception)t;
  }

  protected Logger getLog() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void info(final String msg) {
    getLog().info(msg);

    if (globals.info != null) {
      globals.info.addLn(msg);
    }
  }

  protected void error(final String msg) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln + ": " + msg);
    getLog().error(ln + ": " + msg);
  }

  protected void error(final String msg, final Throwable t) {
    globals.errors++;
    String ln = atLine();

    globals.messages.errorMessage(ln+ ": " + msg + ": " + t.getMessage());
    getLog().error(ln+ ": " + msg, t);
  }

  protected void warn(final String msg) {
    globals.warnings++;
    String ln = atLine();

    globals.messages.warningMessage(ln+ ": " + msg);
    getLog().warn(ln+ ": " + msg);
  }

  protected void trace(final String msg) {
    getLog().debug(msg);
  }
}

