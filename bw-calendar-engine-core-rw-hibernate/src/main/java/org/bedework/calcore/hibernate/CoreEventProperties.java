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

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;
import java.util.List;

/** Implementation of core event properties interface.
 *
 * @author Mike Douglass    douglm  rpi.edu
 * @version 1.0
 * @param <T> type of property, Location, contact etc.
 */
public class CoreEventProperties <T extends BwEventProperty>
        extends CalintfHelper implements CoreEventPropertiesI<T> {
  private final CoreEventPropertiesDAO dao;

  /** Constructor
   *
   * @param sess persistance session
   * @param intf interface
   * @param ac access checker
   * @param readOnlyMode true for a guest
   * @param sessionless if true
   */
  public CoreEventProperties(final HibSession sess,
                             final CalintfImpl intf,
                             final AccessChecker ac,
                             final boolean readOnlyMode,
                             final boolean sessionless,
                             final String className) {
    dao = new CoreEventPropertiesDAO(sess, className);
    intf.registerDao(dao);
    super.init(intf, ac, sessionless);
  }

  @Override
  public <T> T throwException(final BedeworkException be) {
    dao.rollback();
    throw be;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Collection<T> getAll(final String ownerHref) {
    final List eps = dao.getAll(ownerHref);

    final Collection c = ac.getAccessUtil().checkAccess(eps, privRead, true);

    if (debug()) {
      debug("getAll: found: " + eps.size() + " returning: " + c.size());
    }

    return c;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(final String uid) {
    return check((T)dao.get(uid));
  }

  @SuppressWarnings("unchecked")
  @Override
  public T find(final BwString val,
                final String ownerHref) {
    final BwEventProperty p = dao.find(val, ownerHref);

    return check((T)p);
  }

  @Override
  public void checkUnique(final BwString val,
                          final String ownerHref) {
    dao.checkUnique(val, ownerHref);
  }

  @Override
  public void deleteProp(final T val) {
    dao.delete(val);
  }

  @Override
  public List<EventPropertiesReference> getRefs(final T val) {
    return dao.getRefs(val);
  }

  @Override
  public long getRefsCount(final T val) {
    return dao.getRefsCount(val);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private T check(final T ent) {
    if (ent == null) {
      return null;
    }

    /*  XXX This is wrong but it's always been like this.
        For scheduling we end up with other users categories embedded
        in attendees events.

        This will probably get fixed when scheduling is moved out of
        the core.
    if (ent.getPublick()) {
      return ent;
    }

    if (this.getPrincipal().getPrincipalRef().equals(ent.getOwnerHref())) {
      return ent;
    }

    return null;
     */
    return ent;
  }
}
