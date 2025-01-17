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

import org.bedework.calsvci.CalSvcFactoryDefault;
import org.bedework.calsvci.CalSvcI;
import org.bedework.calfacade.svc.CalSvcIPars;

import static java.lang.String.format;

/** Handles a queue of sysevents messages.
 *<p>
 * In general, we need to delay processing until after the initiating request is
 * processed, for example, don;t do scheduling until the event is stored.
 *<p>
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
    final int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return null;
    }

    return href.substring(0, pos);
  }

  protected String getName(final String href) {
    final int pos = href.lastIndexOf("/");

    if (pos <= 0) {
      return href;
    }

    if (pos == href.length() - 1) {
      return null;
    }

    return href.substring(pos + 1);
  }

  /** Make popPrincipal visible to sub-classes
   *
   */
  protected void popPrincipal() {
    getSvc().popPrincipal();
  }

  /** Make pushPrincipalOrFail visible to sub-classes
   *
   * @param principalHref a principal href
   */
  protected void pushPrincipalOrFail(final String principalHref) {
    getSvc().pushPrincipalOrFail(principalHref);
  }

  /** Get an svci object as a different user.
   *
   * @param principalHref of user
   * @param logId for log messages
   * @return CalSvcI
   */
  protected CalSvcI getSvci(final String principalHref,
                            final String logId) {
    final CalSvcI svci;

    /* account is what we authenticated with.
     * user, if non-null, is the user calendar we want to access.
     */
    final CalSvcIPars runAsPars =
            CalSvcIPars.getServicePars(logId,
                                       principalHref,//principal.getAccount(),
                                       false,   // publicAdmin
                                       "/principals/users/root".equals(principalHref));  // allow SuperUser

    var before = System.currentTimeMillis();
    svci = new CalSvcFactoryDefault().getSvc(runAsPars);
    setSvc(svci);
    if (trace()) {
      final var after = System.currentTimeMillis();
      trace(format("Getsvc took %s", after - before));
      before = after;
    }

    svci.open();
    svci.beginTransaction();
    if (trace()) {
      final var after = System.currentTimeMillis();
      trace(format("open + beginTransaction took %s", after - before));
      before = after;
    }

    return svci;
  }

  protected void rollback(final CalSvcI svci) {
    svci.rollbackTransaction();
  }

  protected void closeSvci(final CalSvcI svci) {
    if ((svci == null) || !svci.isOpen()) {
      return;
    }

    try (svci) {
      svci.endTransaction();
    }
  }
}
