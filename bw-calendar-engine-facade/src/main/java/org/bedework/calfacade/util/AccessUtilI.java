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
package org.bedework.calfacade.util;

import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.base.BwShareableContainedDbentity;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.PrincipalInfo;

import edu.rpi.cmt.access.Ace;
import edu.rpi.cmt.access.AceWho;
import edu.rpi.cmt.access.Acl.CurrentAccess;
import edu.rpi.cmt.access.PrivilegeDefs;

import java.io.Serializable;
import java.util.Collection;

/** An access helper interface. This interface makes some assumptions about the
 * classes it deals with but there is no explicit hibernate, or other
 * persistence engine, dependencies.
 *
 * <p>It assumes that it has access to the parent object when needed,
 * continuing on up to the root. For systems which do not allow for a
 * retrieval of the parent on calls to the getCalendar method, the getParent
 * method for this class will need to be overridden. This would presumably
 * take place within the core implementation.
 *
 *
 * @author Mike Douglass   douglm  rpi.edu
 */
public interface AccessUtilI extends PrivilegeDefs, Serializable {
  /**
   *
   * @param cb
   * @throws CalFacadeException
   */
  public void init(PrincipalInfo cb) throws CalFacadeException;

  /** Called at request start
   *
   */
  public void open();

  /** Called at request end
   *
   */
  public void close();

  /** Called to get the parent object for a shared entity. This method should be
   * overriden if explicit calls to the back end calendar are required.
   *
   * @param val
   * @return parent calendar or null.
   * @throws CalFacadeException
   */
  public BwCalendar getParent(BwShareableContainedDbentity<? extends Object> val)
        throws CalFacadeException;

  /* ====================================================================
   *                   Access control
   * ==================================================================== */

  /** Get the default public access
   *
   * @return String value for default access
   */
  public String getDefaultPublicAccess();

  /**
   *
   * @return String default user access
   */
  public String getDefaultPersonalAccess();

  /** Change the access to the given calendar entity using the supplied aces.
   * We are changing access so we remove all access for each who in the list and
   * then add the new aces.
   *
   * @param ent        BwShareableDbentity
   * @param aces       Collection of ace objects
   * @param replaceAll true to replace the entire access list.
   * @throws CalFacadeException
   */
  public void changeAccess(BwShareableDbentity<? extends Object> ent,
                           Collection<Ace> aces,
                           boolean replaceAll) throws CalFacadeException;

  /** Remove any explicit access for the given who to the given calendar entity.
  *
  * @param ent      BwShareableDbentity
  * @param who      AceWho
  * @throws CalFacadeException
  */
 public void defaultAccess(BwShareableDbentity<? extends Object> ent,
                           AceWho who) throws CalFacadeException;

  /** Return a Collection of the objects after checking access
   *
   * @param ents          Collection of BwShareableDbentity
   * @param desiredAccess access we want
   * @param alwaysReturn boolean flag behaviour on no access
   * @return Collection   of checked objects
   * @throws CalFacadeException for no access or other failure
   */
  public Collection<? extends BwShareableDbentity<? extends Object>>
                 checkAccess(Collection<? extends BwShareableDbentity<? extends Object>> ents,
                                int desiredAccess,
                                boolean alwaysReturn)
          throws CalFacadeException;

  /** Check access for the given entity. Returns the current access
   *
   * <p>We special case the access to the user root e.g /user and the home
   * directory, e.g. /user/douglm
   *
   * We deny access to /user to anybody without superuser access. This
   * prevents user browsing. This could be made a system property if the
   * organization wants user browsing.
   *
   * Default access to the home directory is read, write-content to the owner
   * only and unlimited to superuser.
   *
   * Specific access should be no more than read, write-content to the home
   * directory.
   *
   * @param ent
   * @param desiredAccess
   * @param alwaysReturnResult
   * @return  CurrentAccess
   * @throws CalFacadeException
   */
  public CurrentAccess checkAccess(BwShareableDbentity<? extends Object> ent, int desiredAccess,
                        boolean alwaysReturnResult) throws CalFacadeException;
}
