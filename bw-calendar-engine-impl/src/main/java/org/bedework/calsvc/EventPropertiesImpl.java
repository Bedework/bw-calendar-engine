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

import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvci.EventProperties;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Util;

import org.bedework.access.PrivilegeDefs;

import java.util.ArrayList;
import java.util.Collection;

/** Class which handles manipulation of BwEventProperty subclasses which are
 * treated in the same manner, these being Category, Location and contact.
 *
 * <p>Each has a single field which together with the owner makes a unique
 * key and all operations on those classes are the same.
 *
 * @author Mike Douglass   douglm - bedework.edu
 *
 * @param <T> type of property, Location, contact etc.
 */
public class EventPropertiesImpl <T extends BwEventProperty>
        extends CalSvcDb implements EventProperties<T>, PrivilegeDefs {
  private FlushMap<String, T> cached =
      new FlushMap<String, T>(60 * 1000 * 5, // 5 mins
                              2000);  // max size

  private Class<T> ourClass;
  private CoreEventPropertiesI<T> coreHdlr;

  private boolean adminCanEditAllPublic;

  /** Constructor
  *
  * @param svci
  */
  public EventPropertiesImpl(final CalSvc svci) {
    super(svci);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(final String className,
                   final boolean adminCanEditAllPublic) {
    this.adminCanEditAllPublic = adminCanEditAllPublic;

    try {
      ourClass = (Class<T>)Class.forName(className);
      coreHdlr = getCal().getEvPropsHandler(ourClass);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#get(java.lang.String, java.lang.String)
   */
  @Override
  public Collection<T> get(final String ownerHref,
                           final String creatorHref) throws CalFacadeException {
    return coreHdlr.get(ownerHref, creatorHref);
  }

  @Override
  public Collection<T> get() throws CalFacadeException {
    BwPrincipal owner;
    if (!isPublicAdmin()) {
      owner = getPrincipal();
    } else {
      owner = getPublicUser();
    }

    return get(owner.getPrincipalRef(), null);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#getEditable()
   */
  @Override
  public Collection<T> getEditable() throws CalFacadeException {
    if (!isPublicAdmin()) {
      return get(getPrincipal().getPrincipalRef(), null);
    }

    if (isSuper() || adminCanEditAllPublic) {
      return get(getPublicUser().getPrincipalRef(), null);
    }

    return get(getPublicUser().getPrincipalRef(),
               getPrincipal().getPrincipalRef());
  }

  @Override
  public T get(final String uid) throws CalFacadeException {
    return coreHdlr.get(uid);
  }

  @SuppressWarnings("unchecked")
  @Override
  public T getCached(final String uid) throws CalFacadeException {
    T ent = cached.get(uid);

    if (ent != null) {
      return ent;
    }

    ent = get(uid);
    if (ent == null) {
      return null;
    }

    ent =  (T)ent.clone();

    cached.put(uid, ent);

    return ent;
  }

  @Override
  public Collection<T> getCached(final Collection<String> uids) throws CalFacadeException {
    Collection<T> ents = new ArrayList<T>();

    if (Util.isEmpty(uids)) {
      return ents;
    }

    for (String uid: uids) {
      ents.add(getCached(uid));
    }

    return ents;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#find(org.bedework.calfacade.BwString, java.lang.String)
   */
  @Override
  public T find(final BwString val,
                final String ownerHref) throws CalFacadeException {
    String oh;
    if (ownerHref == null) {
      oh = getPrincipal().getPrincipalRef();
    } else {
      oh = ownerHref;
    }

    return coreHdlr.find(val, oh);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#add(org.bedework.calfacade.BwEventProperty)
   */
  @Override
  public boolean add(final T val) throws CalFacadeException {
    setupSharableEntity(val, getPrincipal().getPrincipalRef());

    updateOK(val);

    if (find(val.getFinderKeyValue(), val.getOwnerHref()) != null) {
      return false;
    }

    if (debug) {
      trace("Add " + val);
    }

    if ((val.getCreatorHref() == null) ||
        (val.getOwnerHref() == null)) {
      throw new CalFacadeException("Owner and creator must be set");
    }

    getCal().saveOrUpdate(val);
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(false, val);

    coreHdlr.checkUnique(val.getFinderKeyValue(), val.getOwnerHref());

    return true;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#update(org.bedework.calfacade.BwEventProperty)
   */
  @Override
  public void update(final T val) throws CalFacadeException {
    if ((val.getCreatorHref() == null) ||
        (val.getOwnerHref() == null)) {
      throw new CalFacadeException("Owner and creator must be set");
    }

    if (check(val) == null) {
      throw new CalFacadeAccessException();
    }

    getCal().saveOrUpdate(val);
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(false, val);

    coreHdlr.checkUnique(val.getFinderKeyValue(), val.getOwnerHref());
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#delete(org.bedework.calfacade.BwEventProperty)
   */
  @Override
  public int delete(final T val) throws CalFacadeException {
    deleteOK(val);

    /** Only allow delete if not in use
     */
    if (coreHdlr.getRefsCount(val) != 0) {
      return 2;
    }

    /* Remove from preferences */
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true, val);

    coreHdlr.deleteProp(val);

    return 0;
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#getRefs(org.bedework.calfacade.BwEventProperty)
   */
  @Override
  public Collection<EventPropertiesReference> getRefs(final T val) throws CalFacadeException {
    return coreHdlr.getRefs(val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calsvci.EventProperties#ensureExists(org.bedework.calfacade.BwEventProperty, java.lang.String)
   */
  @Override
  public EnsureEntityExistsResult<T> ensureExists(final T val,
                                                  final String ownerHref)
          throws CalFacadeException {
    EnsureEntityExistsResult<T> eeer = new EnsureEntityExistsResult<T>();

    if (!val.unsaved()) {
      // Exists
      eeer.entity = val;
      return eeer;
    }

    String oh;
    if (ownerHref == null) {
      oh = getPrincipal().getPrincipalRef();
    } else {
      oh = ownerHref;
    }

    eeer.entity = find(val.getFinderKeyValue(), oh);

    if (eeer.entity != null) {
      // Exists
      return eeer;
    }

    // doesn't exist at this point, so we add it to db table
    setupSharableEntity(val, ownerHref);
    eeer.added = add(val);
    eeer.entity = val;

    return eeer;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private T check(final T ent) throws CalFacadeException {
    if (ent == null) {
      return null;
    }

    /*
    if (!access.checkAccess(ent, privRead, true).accessAllowed) {
      return null;
    }
    */

    return ent;
  }

  private void deleteOK(final T o) throws CalFacadeException {
    if (o == null) {
      return;
    }

    if (isGuest()) {
      throw new CalFacadeAccessException();
    }

    if (isSuper()) {
      // Always ok
      return;
    }

    if (!(o instanceof BwShareableDbentity)) {
      throw new CalFacadeAccessException();
    }

    if (!isPublicAdmin()) {
      // Normal access checks apply
      getSvc().checkAccess(o, privUnbind, false);
      return;
    }

    BwShareableDbentity ent = o;

    if (adminCanEditAllPublic || ent.getCreatorHref().equals(getPrincipal())) {
      return;
    }

    throw new CalFacadeAccessException();
  }

  /* This provides some limits to shareable entity updates for the
   * admin users. It is applied in addition to the normal access checks
   * applied at the lower levels.
   */
  private void updateOK(final Object o) throws CalFacadeException {
    if (isGuest()) {
      throw new CalFacadeAccessException();
    }

    if (isSuper()) {
      // Always ok
      return;
    }

    if (!(o instanceof BwShareableDbentity)) {
      throw new CalFacadeAccessException();
    }

    if (!isPublicAdmin()) {
      // Normal access checks apply
      return;
    }

    BwShareableDbentity ent = (BwShareableDbentity)o;

    if (adminCanEditAllPublic || ent.getCreatorHref().equals(getPrincipal())) {
      return;
    }

    throw new CalFacadeAccessException();
  }
}

