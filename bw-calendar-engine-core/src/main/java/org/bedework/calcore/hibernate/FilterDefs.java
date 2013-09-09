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

import org.bedework.calcore.AccessUtil;
import org.bedework.calcorei.CoreFilterDefsI;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.exc.CalFacadeException;

import java.util.Collection;

/** This acts as an interface to the database for filters.
 *
 * @author Mike Douglass       douglm - bedework.edu
 */
class FilterDefs  extends CalintfHelperHib implements CoreFilterDefsI {
  /** Constructor
  *
  * @param chcb
  * @param cb
  * @param access
  * @param currentMode
  * @param sessionless
  */
 public FilterDefs(final CalintfHelperHibCb chcb,
                   final Callback cb,
                   final AccessUtil access,
                   final int currentMode,
                   final boolean sessionless) {
   super(chcb);
   super.init(cb, access, currentMode, sessionless);
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

  @Override
  public void save(final BwFilterDef val,
                   final BwPrincipal owner) throws CalFacadeException {
    HibSession sess = getSess();

    BwFilterDef fd = fetch(sess, val.getName(), owner);

    if (fd != null) {
      throw new CalFacadeException(CalFacadeException.duplicateFilter,
                                   val.getName());
    }

    sess.save(val);
  }

  @Override
  public BwFilterDef getFilterDef(final String name,
                                  final BwPrincipal owner) throws CalFacadeException {
    return fetch(getSess(), name, owner);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Collection<BwFilterDef> getAllFilterDefs(final BwPrincipal owner) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwFilterDef.class.getName());
    sb.append(" where ownerHref=:ownerHref");

    sess.createQuery(sb.toString());
    sess.setString("ownerHref", owner.getPrincipalRef());
    sess.cacheableQuery();

    return sess.getList();
  }

  @Override
  public void update(final BwFilterDef val) throws CalFacadeException {
    HibSession sess = getSess();

    sess.update(val);
  }

  @Override
  public void deleteFilterDef(final String name,
                              final BwPrincipal owner) throws CalFacadeException {
    HibSession sess = getSess();

    BwFilterDef fd = fetch(sess, name, owner);

    if (fd == null) {
      throw new CalFacadeException(CalFacadeException.unknownFilter, name);
    }

    sess.delete(fd);
  }

  private BwFilterDef fetch(final Object session,
                            final String name,
                            final BwPrincipal owner) throws CalFacadeException {
    HibSession sess = (HibSession)session;

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwFilterDef.class.getName());
    sb.append(" where ownerHref=:ownerHref and name=:name");

    sess.createQuery(sb.toString());
    sess.setString("ownerHref", owner.getPrincipalRef());
    sess.setString("name", name);
    sess.cacheableQuery();

    return (BwFilterDef)sess.getUnique();
  }
}
