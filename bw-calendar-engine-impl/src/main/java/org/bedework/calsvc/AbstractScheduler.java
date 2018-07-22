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

import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calsvci.CalSvcIPars;

/** Handles a queue of sysevents messages.
 *
 * In general we need to delay processing until after the initiating request is
 * processed, for example, don;t do scheduling until the event is stored.
 *
 * In addition, processing of the message can cause a significant amount of
 * traffic as each message can itself generate more messages.
 *
 * @author Mike Douglass
 */
public abstract class AbstractScheduler extends CalSvcDb implements MesssageHandler {
  /**
   */
  public AbstractScheduler() {
    super(null);
  }

  protected String getParentPath(final String href) {
    int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return null;
    }

    return href.substring(0, pos);
  }

  protected String getName(final String href) {
    int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return href;
    }

    if (pos == href.length() - 1) {
      return null;
    }

    return href.substring(pos + 1);
  }

  /** Get an svci object as a different user.
   *
   * @param principalHref of user
   * @return CalSvcI
   * @throws CalFacadeException on fatal error
   */
  protected CalSvcI getSvci(final String principalHref) throws CalFacadeException {
    CalSvcI svci;

    /* account is what we authenticated with.
     * user, if non-null, is the user calendar we want to access.
     */
    final CalSvcIPars runAsPars =
            CalSvcIPars.getServicePars("scheduler",
                                       principalHref,//principal.getAccount(),
                                       false,   // publicAdmin
                                       "/principals/users/root".equals(principalHref));  // allow SuperUser

    svci = new CalSvcFactoryDefault().getSvc(runAsPars);
    setSvc(svci);

    svci.open();
    svci.beginTransaction();

    return svci;
  }

  protected void rollback(final CalSvcI svci) {
    try {
      svci.rollbackTransaction();
    } catch (final Throwable ignored) {
      // Pretty much screwed  now
    }
  }

  protected void closeSvci(final CalSvcI svci) throws CalFacadeException {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    CalFacadeException exc = null;

    try {
      try {
        svci.endTransaction();
      } catch (final CalFacadeException cfe) {
        rollback(svci);
        exc = cfe;
      }
    } finally {
      svci.close();
    }

    if (exc != null) {
      throw exc;
    }
  }
}
