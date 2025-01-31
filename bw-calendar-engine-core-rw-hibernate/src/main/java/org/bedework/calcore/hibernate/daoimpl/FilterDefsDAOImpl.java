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
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.rw.common.dao.FilterDefsDAO;
import org.bedework.calfacade.BwFilterDef;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.database.db.DbSession;

import java.util.Collection;

/** This acts as an interface to the database for filters.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
public class FilterDefsDAOImpl extends DAOBaseImpl
        implements FilterDefsDAO {
  /** Constructor
  *
   * @param sess the session
  */
 public FilterDefsDAOImpl(final DbSession sess) {
   super(sess);
 }

  @Override
  public String getName() {
    return FilterDefsDAOImpl.class.getName();
  }

  private static final String getAllFilterDefsQuery =
          "from " + BwFilterDef.class.getName() +
                  " where ownerHref=:ownerHref";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwFilterDef> getAllFilterDefs(
          final BwPrincipal<?> owner) {
    final var sess = getSess();

    sess.createQuery(getAllFilterDefsQuery);
    sess.setString("ownerHref", owner.getPrincipalRef());

    return (Collection<BwFilterDef>)sess.getList();
  }

  private static final String fetchFilterDefQuery =
          "from " + BwFilterDef.class.getName() +
                  " where ownerHref=:ownerHref and name=:name";

  @Override
  public BwFilterDef fetch(final String name,
                           final BwPrincipal<?> owner) {
    final var sess = getSess();

    sess.createQuery(fetchFilterDefQuery);
    sess.setString("ownerHref", owner.getPrincipalRef());
    sess.setString("name", name);

    return (BwFilterDef)sess.getUnique();
  }
}
