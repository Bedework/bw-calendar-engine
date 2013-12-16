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

import org.bedework.calfacade.BwContact;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.Contacts;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;

import java.util.Collection;

/** Class which handles manipulation of Contacts.
 *
 * @author Mike Douglass   douglm - rpi.edu
 */
public class ContactsImpl
        extends EventPropertiesImpl<BwContact>
        implements Contacts {
  /* We'll cache lists of entities by principal href - flushing them
    every so often.
   */
  private static FlushMap<String, Collection<BwContact>> cached =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  private FlushMap<String, BwContact> cachedByUid =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  /** Constructor
  *
  * @param svci calsvc object
  */
  public ContactsImpl(final CalSvc svci) {
    super(svci);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(final boolean adminCanEditAllPublic) {
    super.init(BwContact.class.getCanonicalName(),
               adminCanEditAllPublic);
  }

  @Override
  Collection<BwContact> getCached(final String ownerHref) {
    return cached.get(ownerHref);
  }

  @Override
  void putCached(final String ownerHref,
                 final Collection<BwContact> vals) {
    cached.put(ownerHref, vals);
  }

  @Override
  void removeCached(final String ownerHref) {
    cached.remove(ownerHref);
  }

  @Override
  BwContact getCachedByUid(final String uid) {
    return cachedByUid.get(uid);
  }

  @Override
  void putCachedByUid(final String uid, final BwContact val) {
    cachedByUid.put(uid, val);
  }

  @Override
  void removeCachedByUid(final String uid) {
    cachedByUid.remove(uid);
  }

  @Override
  Collection<BwContact> fetchAllIndexed(String ownerHref)
          throws CalFacadeException {
    return getIndexer(ownerHref).fetchAllContacts();
  }

  @Override
  BwContact fetchIndexedByUid(String uid) throws CalFacadeException {
    return getIndexer().fetchContact(uid, PropertyInfoIndex.UID);
  }

  BwContact findPersistent(final BwContact val,
                            final String ownerHref) throws CalFacadeException {
    return findPersistent(val.getFinderKeyValue(), ownerHref);
  }

  @Override
  public boolean exists(BwContact val) throws CalFacadeException {
    return findPersistent(val.getFinderKeyValue(), val.getOwnerHref()) != null;
  }

  @Override
  public BwContact find(final BwString val) throws CalFacadeException {
    return getIndexer().fetchContact(val.getValue(),
                                     PropertyInfoIndex.CN,
                                     PropertyInfoIndex.VALUE);
  }
}

