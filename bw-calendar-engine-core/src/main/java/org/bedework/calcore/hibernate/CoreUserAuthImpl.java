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

package org.bedework.calcore.hibernate;

import org.bedework.calcorei.CoreUserAuthI;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefs;
import org.bedework.calfacade.util.AccessChecker;

import java.util.List;

/** Implementation of UserAuthI that handles Bedwork DB tables for authorisation.
 *
 * @author Mike Douglass    douglm@rpi.edu
 * @version 1.0
 */
public class CoreUserAuthImpl extends CalintfHelperHib implements CoreUserAuthI {
  /** Constructor
   *
   * @param chcb helper
   * @param cb calback
   * @param ac access util
   * @param currentMode how we are running
   * @param sessionless true for sessionless
   */
  public CoreUserAuthImpl(final CalintfHelperHibCb chcb,
                          final Callback cb,
                          final AccessChecker ac,
                          final int currentMode,
                          final boolean sessionless) {
    super(chcb);
    super.init(cb, ac, currentMode, sessionless);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#startTransaction()
   */
  @Override
  public void startTransaction() throws CalFacadeException {
  }

  /* (non-Javadoc)
   * @see org.bedework.calcore.CalintfHelper#endTransaction()
   */
  @Override
  public void endTransaction() throws CalFacadeException {
  }

  private final static String getUserQuery =
      "from " +
          BwAuthUser.class.getName() +
          " as au " +
          "where au.userHref = :userHref";

  @Override
  public void addAuthUser(final BwAuthUser val) throws CalFacadeException {
    final BwAuthUser ck = getAuthUser(val.getUserHref());

    if (ck != null) {
      throw new CalFacadeException(CalFacadeException.targetExists);
    }

    getSess().save(val);
  }

  @Override
  public BwAuthUser getAuthUser(final String href) throws CalFacadeException {
    final HibSession sess = getSess();
    sess.createQuery(getUserQuery);
    sess.setString("userHref", href);

    final BwAuthUser au = (BwAuthUser)sess.getUnique();

    if (au == null) {
      // Not an authorised user
      return null;
    }

    BwAuthUserPrefs prefs = au.getPrefs();

    if (prefs == null) {
      prefs = BwAuthUserPrefs.makeAuthUserPrefs();
      au.setPrefs(prefs);
    }

    return au;
  }

  @Override
  public void updateAuthUser(final BwAuthUser val) throws CalFacadeException {
    getSess().update(val);
  }

  private final static String getAllQuery =
      "from " +
          BwAuthUser.class.getName() +
          " au " +
          "order by au.userHref";

  @SuppressWarnings("unchecked")
  @Override
  public List<BwAuthUser> getAll() throws CalFacadeException {
    final HibSession sess = getSess();

    sess.createQuery(getAllQuery);

    return sess.getList();
  }
}
