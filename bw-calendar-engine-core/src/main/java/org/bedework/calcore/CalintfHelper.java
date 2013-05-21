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
package org.bedework.calcore;

import org.bedework.calcorei.CalintfDefs;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.PrincipalInfo;
import org.bedework.calfacade.util.AccessUtilI;
import org.bedework.calfacade.wrappers.CalendarWrapper;

import edu.rpi.cmt.access.PrivilegeDefs;

import org.apache.log4j.Logger;

import java.io.Serializable;

/** Class used as basis for a number of helper classes.
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public abstract class CalintfHelper
        implements CalintfDefs, PrivilegeDefs, Serializable {
  /**
   */
  public static interface Callback extends Serializable {
    /**
     * @throws CalFacadeException
     */
    public void rollback() throws CalFacadeException;

    /**
     * @return BasicSystemProperties object
     * @throws CalFacadeException
     */
    public BasicSystemProperties getSyspars() throws CalFacadeException;

    /**
     * @return PrincipalInfo object
     * @throws CalFacadeException
     */
    public PrincipalInfo getPrincipalInfo() throws CalFacadeException;
  }

  protected boolean debug;

  protected boolean collectTimeStats;

  protected Callback cb;

  protected AccessUtilI access;

  protected int currentMode = guestMode;

  private transient Logger log;

  protected boolean sessionless;

  /** Initialize
   *
   * @param cb
   * @param access
   * @param currentMode
   * @param sessionless
   */
  public void init(final Callback cb,
                   final AccessUtilI access,
                   final int currentMode,
                   final boolean sessionless) {
    this.cb = cb;
    this.access = access;
    this.currentMode = currentMode;
    this.sessionless = sessionless;
    debug = getLogger().isDebugEnabled();
    collectTimeStats = Logger.getLogger("org.bedework.collectTimeStats").isDebugEnabled();
  }

  /** Called to allow setup
   *
   * @throws CalFacadeException
   */
  public abstract void startTransaction() throws CalFacadeException;

  /** Called to allow cleanup
   *
   * @throws CalFacadeException
   */
  public abstract void endTransaction() throws CalFacadeException;

  protected BasicSystemProperties getSyspars() throws CalFacadeException {
    return cb.getSyspars();
  }

  protected BwPrincipal getPrincipal() throws CalFacadeException {
    return cb.getPrincipalInfo().getPrincipal();
  }

  protected String currentPrincipal() throws CalFacadeException {
    if (cb == null) {
      return null;
    }

    if (getPrincipal() == null) {
      return null;
    }

    return getPrincipal().getPrincipalRef();
  }

  protected CalendarWrapper wrap(final BwCalendar val) {
    if (val == null) {
      return null;
    }

    if (val instanceof CalendarWrapper) {
      // CALWRAPPER get this from getEvents with an internal temp calendar
      return (CalendarWrapper)val;
    }
    return new CalendarWrapper(val, access);
  }

  protected BwCalendar unwrap(final BwCalendar val) throws CalFacadeException {
    if (val == null) {
      return null;
    }

    if (!(val instanceof CalendarWrapper)) {
      // We get these at the moment - getEvents at svci level
      return val;
      // CALWRAPPER throw new CalFacadeException("org.bedework.not.wrapped");
    }

    return ((CalendarWrapper)val).fetchEntity();
  }

  /** Get a logger for messages
   *
   * @return Logger
   */
  protected Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  protected void debugMsg(final String msg) {
    getLogger().debug(msg);
  }

  protected void trace(final String msg) {
    getLogger().debug(msg);
  }
}
