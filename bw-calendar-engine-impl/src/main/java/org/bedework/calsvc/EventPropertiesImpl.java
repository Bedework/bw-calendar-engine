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

import org.bedework.access.PrivilegeDefs;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calsvci.EventProperties;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.bedework.calfacade.BwEventProperty.statusDeleted;

/** Class which handles manipulation of BwEventProperty subclasses which are
 * treated in the same manner, these being Category, Location and contact.
 *
 * <p>Each has a single field which together with the owner makes a unique
 * key and all operations on those classes are the same.
 *
 * @author Mike Douglass   douglm - rpi.edu
 *
 * @param <T> type of property, Location, contact etc.
 */
public abstract class EventPropertiesImpl<T extends BwEventProperty>
        extends CalSvcDb implements EventProperties<T>, PrivilegeDefs {
  /* We'll cache the indexers we use. Use a map for non-public
   */
  private final static FlushMap<String, BwIndexer> userIndexers =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         200);  // max size

  /* We'll cache lists of entities by principal href - flushing them
    every so often.
   */
  private final FlushMap<String, Collection<T>> cached =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  private final FlushMap<String, T> cachedByUid =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  private static BwIndexer publicIndexer;

  private Class<T> ourClass;
  private CoreEventPropertiesI<T> coreHdlr;

  private boolean adminCanEditAllPublic;

  private String lastChangeToken;

  /* fetch from indexer */
  abstract Collection<T> fetchAllIndexed(boolean publick,
                                         String ownerHref) throws CalFacadeException;

  abstract T fetchIndexedByUid(String uid) throws CalFacadeException;

  abstract T fetchIndexed(String href) throws CalFacadeException;

  /** Find a persistent entry like the one given or return null.
   *
   * @param val the non-persistent form
   * @param ownerHref principal href
   * @return T or null
   * @throws CalFacadeException
   */
  abstract T findPersistent(final T val,
                            final String ownerHref) throws CalFacadeException;

  /** Check for existence
   *
   * @param val the entity
   * @return true if exists
   * @throws CalFacadeException
   */
  abstract boolean exists(T val) throws CalFacadeException;

  /** Constructor
  *
  * @param svci the service interface
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
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public Collection<T> getPublic()
          throws CalFacadeException {
    return get(true, null);
  }

  Collection<T> filterDeleted(final Collection<T> ents)
          throws CalFacadeException {
    if (isSuper()) {
      return ents;
    }

    return ents.stream()
               .filter(ent -> !statusDeleted.equals(ent.getStatus()))
               .collect(Collectors.toList());
  }

  @Override
  public Collection<T> get() throws CalFacadeException {
    return get(isPublicAdmin(), null);
  }

  @Override
  public Collection<T> getEditable() throws CalFacadeException {
    if (!isPublicAdmin()) {
      return get(false, null);
    }

    if (isSuper() || adminCanEditAllPublic) {
      return getPublic();
    }

    return get(true, getPrincipal().getPrincipalRef());
  }

  @SuppressWarnings("unchecked")
  @Override
  public T getByUid(final String uid) throws CalFacadeException {
    T ent = getCachedByUid(uid);

    if (ent != null) {
      return ent;
    }

    ent = fetchIndexedByUid(uid);

    if (ent == null) {
      return null;
    }

    putCachedByUid(uid, ent);

    return ent;
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get(final String href) throws CalFacadeException {
    return fetchIndexed(href);
  }

  @Override
  public Collection<T> getByUids(final Collection<String> uids) throws CalFacadeException {
    final Collection<T> ents = new ArrayList<>();

    if (Util.isEmpty(uids)) {
      return ents;
    }

    for (final String uid: uids) {
      final T ent = getByUid(uid);
      if (ent != null) {
        ents.add(ent);
      }
    }

    return ents;
  }

  @Override
  public T getPersistent(final String uid) throws CalFacadeException {
    return coreHdlr.get(uid);
  }

  @Override
  public T findPersistent(final BwString val) throws CalFacadeException {
    BwPrincipal owner;
    if (!isPublicAdmin()) {
      owner = getPrincipal();
    } else {
      owner = getPublicUser();
    }

    return coreHdlr.find(val, owner.getPrincipalRef());
  }

  @Override
  public boolean add(final T val) throws CalFacadeException {
    setupSharableEntity(val, getPrincipal().getPrincipalRef());

    updateOK(val);

    if (exists(val)) {
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

    getIndexer(val.getPublick(), val.getOwnerHref()).indexEntity(val);

    // Update cached
    final Collection<T> ents = get();
    if (ents != null) {
      ents.add(val);
    }

    putCachedByUid(val.getUid(), val);

    return true;
  }

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

    getIndexer(val.getPublick(), val.getOwnerHref()).indexEntity(val);

    // Update cached
    final Collection<T> ents = get();
    if (ents != null) {
      ents.remove(val);
      ents.add(val);
    }

//    removeCached(val.getOwnerHref());
    putCachedByUid(val.getUid(), val);
  }

  @Override
  public int delete(final T val) throws CalFacadeException {
    T ent = val;

    if (val.unsaved()) {
      ent = getPersistent(val.getUid());
    }

    deleteOK(ent);

    /** Only allow delete if not in use
     */
    if (coreHdlr.getRefsCount(ent) != 0) {
      return 2;
    }

    /* Remove from preferences */
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true,
                                                               ent);

    coreHdlr.deleteProp(ent);

    getIndexer(ent.getPublick(), ent.getOwnerHref()).unindexEntity(ent);

    // Update cached
    final Collection<T> ents = get();
    if (ents != null) {
      ents.remove(ent);
    }

    removeCachedByUid(ent.getUid());

    return 0;
  }

  @Override
  public Collection<EventPropertiesReference> getRefs(final T val) throws CalFacadeException {
    final T persistent = getPersistent(val.getUid());

    if (persistent == null) {
      return null;
    }
    return coreHdlr.getRefs(persistent);
  }

  @Override
  public EnsureEntityExistsResult<T> ensureExists(final T val,
                                                  final String ownerHref)
          throws CalFacadeException {
    final EnsureEntityExistsResult<T> eeer = new EnsureEntityExistsResult<T>();

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

    eeer.entity = findPersistent(val, oh);

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

  @Override
  public int reindex(BwIndexer indexer) throws CalFacadeException {
    BwPrincipal owner;
    if (!isPublicAdmin()) {
      owner = getPrincipal();
    } else {
      owner = getPublicUser();
    }

    Collection<T> ents = coreHdlr.getAll(owner.getPrincipalRef());
    if (Util.isEmpty(ents)) {
      return 0;
    }

    for (T ent: ents) {
      indexer.indexEntity(ent);
    }

    return ents.size();
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  protected BwIndexer getIndexer(final boolean getPublic,
                                 final String ownerHref) throws CalFacadeException {
    String href = checkHref(ownerHref);

    final boolean publick = getPublic || isGuest() || isPublicAdmin();

    if (publick) {
      if (publicIndexer == null) {
        publicIndexer = getSvc().getIndexer(true);
      }

      return publicIndexer;
    }

    BwIndexer idx = userIndexers.get(href);

    if (idx != null) {
      return idx;
    }

    idx = getSvc().getIndexer(href);

    userIndexers.put(href, idx);

    return idx;
  }

  /**
   * @return true if indexed data changed
   * @throws CalFacadeException
   */
  protected boolean indexChanged() throws CalFacadeException {
    final String token = getIndexer().currentChangeToken();

    final boolean changed = lastChangeToken == null ||
        !lastChangeToken.equals(token);

    lastChangeToken = token;

    return changed;
  }

  protected Collection<T> getCached(final String ownerHref) throws CalFacadeException {
    checkChache();
    return cached.get(ownerHref);
  }

  protected void putCached(final String ownerHref,
                           final Collection<T> vals) throws CalFacadeException {
    cached.put(ownerHref, vals);
  }

  protected void removeCached(final String ownerHref) throws CalFacadeException {
    cached.remove(ownerHref);
  }

  protected T getCachedByUid(final String uid) throws CalFacadeException {
    checkChache();
    return cachedByUid.get(uid);
  }

  protected void putCachedByUid(final String uid,
                                final T val) throws CalFacadeException {
    cachedByUid.put(uid, val);
  }

  protected void removeCachedByUid(final String uid) throws CalFacadeException {
    cachedByUid.remove(uid);
  }

  protected T findPersistent(final BwString val,
                             final String ownerHref) throws CalFacadeException {
    return coreHdlr.find(val, ownerHref);
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void checkChache() throws CalFacadeException {
    if (indexChanged()) {
      cached.clear();
      cachedByUid.clear();
    }
  }

  private Collection<T> get(final boolean publick,
                            final String creatorHref) throws CalFacadeException {
    final String ownerHref;

    if (publick) {
      ownerHref = getPublicUser().getPrincipalRef();
    } else {
      ownerHref = getPrincipal().getPrincipalRef();
    }


    Collection<T> ents = getCached(ownerHref);

    if (ents == null) {
      ents = fetchAllIndexed(publick, ownerHref);

      if (Util.isEmpty(ents)) {
        return new ArrayList<>();
      }

      putCached(ownerHref, ents);
    }

    /* Add them to the uid cache */
    for (final T ent: ents) {
      putCachedByUid(ent.getUid(), ent);
    }

    if (creatorHref == null) {
      return filterDeleted(ents);
    }

    final List<T> someEnts = new ArrayList<>();
    for (final T ent: ents) {
      if (ent.getCreatorHref().equals(creatorHref)) {
        someEnts.add(ent);
      }
    }

    return filterDeleted(someEnts);
  }

  private String checkHref(final String ownerHref) throws CalFacadeException {
    if (ownerHref != null) {
      return ownerHref;
    }

    // Assume public
    return getSvc().getUsersHandler().getPublicUser().getPrincipalRef();
  }

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

    if (!isPublicAdmin()) {
      // Normal access checks apply
      getSvc().checkAccess(o, privUnbind, false);
      return;
    }

    if (adminCanEditAllPublic ||
            o.getCreatorHref().equals(getPrincipal().getPrincipalRef())) {
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

    if (adminCanEditAllPublic ||
            ent.getCreatorHref().equals(getPrincipal().getPrincipalRef())) {
      return;
    }

    throw new CalFacadeAccessException();
  }
}

