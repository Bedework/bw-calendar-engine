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

package org.bedework.calsvc.directory;

import org.bedework.calfacade.BwAuthUser;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.UserAuth;

import org.apache.log4j.Logger;

import java.util.Collection;

/** Implementation of UserAuth that handles Bedwork DB tables for authorisation.
 *
 * @author Mike Douglass    douglm@rpi.edu
 * @version 1.0
 */
public class UserAuthUWDbImpl implements UserAuth {
  /** Ideally this would trigger the debugging log in the underlying jdbc
   * implementation.
   */
  private boolean debug;

  private transient Logger log;

  protected CallBack cb;

  /** Constructor
   */
  public UserAuthUWDbImpl() {
  }

  /* ====================================================================
   *  The following affect the state of the current user.
   * ==================================================================== */

  @Override
  public void initialise(final CallBack cb) throws CalFacadeException {
    this.cb = cb;

    debug = getLogger().isDebugEnabled();
  }

  /** ===================================================================
   *  The following should not change the state of the current users
   *  access which is set and retrieved with the above methods.
   *  =================================================================== */

  @Override
  public void addUser(final BwAuthUser val) throws CalFacadeException {
    if (val.isUnauthorized()) {
      // We don't need an entry for a user with no special rights
      return;
    }

    cb.add(val);
  }

  @Override
  public boolean getUserMaintOK() {
    return true;
  }

  @Override
  public void updateUser(final BwAuthUser val) throws CalFacadeException {
    if (val.isUnauthorized()) {
      // We don't need an entry for a user with no special rights
      cb.delete(val);

      return;
    }

    cb.update(val);
  }

  @Override
  public BwAuthUser getUser(final String account) throws CalFacadeException {
    if (debug) {
      trace("getUserEntry for " + account);
    }

    final BwPrincipal p = cb.getPrincipal(account);

    if (p == null) {
      return null;
    }

    return cb.getAuthUser(p.getPrincipalRef());
  }

  @Override
  public Collection<BwAuthUser> getAll() throws CalFacadeException {
    return cb.getAll();
  }

  /*  ===================================================================
   *                   Private methods
   *  =================================================================== */

  private Logger getLogger() {
    if (log == null) {
      log = Logger.getLogger(this.getClass());
    }

    return log;
  }

  private void trace(final String msg) {
    getLogger().debug("trace: " + msg);
  }
}
