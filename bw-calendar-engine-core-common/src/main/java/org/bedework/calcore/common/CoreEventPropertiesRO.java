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

package org.bedework.calcore.common;

import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.util.AccessChecker;

import java.util.Collection;
import java.util.List;

/** Implementation of core event properties interface.
 *
 * @author Mike Douglass    douglm  rpi.edu
 * @version 1.0
 * @param <T> type of property, Location, contact etc.
 */
public class CoreEventPropertiesRO<T extends BwEventProperty>
        extends CalintfHelper implements CoreEventPropertiesI<T> {
  protected boolean contacts;
  protected boolean categories;
  protected boolean locations;

  /** Constructor
   *
   * @param intf interface
   * @param ac access checker
   * @param guestMode true for a guest
   * @param sessionless if true
   * @throws CalFacadeException on fatal error
   */
  public CoreEventPropertiesRO(final Calintf intf,
                               final AccessChecker ac,
                               final boolean guestMode,
                               final boolean sessionless)
          throws CalFacadeException {
    super.init(intf, ac, guestMode, sessionless);
  }

  @Override
  public void throwException(final CalFacadeException cfe)
          throws CalFacadeException {
    throw cfe;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Collection<T> getAll(final String ownerHref) throws CalFacadeException {
    final BwIndexer indexer = intf.getIndexer(ownerHref);

    final List eps;

    if (contacts) {
      eps = indexer.fetchAllContacts();
    }else if (categories) {
      eps = indexer.fetchAllCats();
    } else if (locations) {
      eps = indexer.fetchAllLocations();
    } else {
      throw new CalFacadeException("Unimplemented or software error");
    }

    final Collection c = ac.getAccessUtil().checkAccess(eps, privRead, true);

    if (debug()) {
      debug("getAll: found: " + eps.size() + " returning: " + c.size());
    }

    return c;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(final String uid) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @SuppressWarnings("unchecked")
  @Override
  public T find(final BwString val,
                final String ownerHref) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void checkUnique(final BwString val,
                          final String ownerHref) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public void deleteProp(final T val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public List<EventPropertiesReference> getRefs(final T val)
                          throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  @Override
  public long getRefsCount(final T val) throws CalFacadeException {
    throw new RuntimeException("Read only version");
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */
}
