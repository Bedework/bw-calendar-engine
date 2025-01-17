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
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.calcorei.CoreEventPropertiesI;
import org.bedework.calfacade.BwEventProperty;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.EnsureEntityExistsResult;
import org.bedework.calsvci.EventProperties;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntitiesResponse;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;

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
public abstract class EventPropertiesImpl<T extends BwEventProperty<?>>
        extends CalSvcDb implements EventProperties<T>, PrivilegeDefs {
  /* We'll cache lists of entities by principal href - flushing them
    every so often.
   */
  private final FlushMap<String, Collection<T>> cached =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  private final FlushMap<String, T> cachedByUid =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  private Class<T> ourClass;
  private CoreEventPropertiesI<T> coreHdlr;

  private boolean adminCanEditAllPublic;

  private String lastChangeToken;

  abstract String getDocType();

  /* fetch from indexer */
  abstract Collection<T> fetchAllIndexed(boolean publick,
                                         String ownerHref);

  abstract T fetchIndexedByUid(String uid);

  abstract T fetchIndexed(String href);

  /** Find a persistent entry like the one given or return null.
   *
   * @param val the non-persistent form
   * @param ownerHref principal href
   * @return Status and possible T
   */
  abstract GetEntityResponse<T> findPersistent(T val,
                                               String ownerHref);

  /** Check for existence
   *
   * @param val the entity
   * @return true if exists, false for error
   */
  abstract boolean exists(Response resp, T val);

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
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public Collection<T> getPublic() {
    return get(true, null);
  }

  Collection<T> filterDeleted(final Collection<T> ents) {
    if (isSuper()) {
      return ents;
    }

    return ents.stream()
               .filter(ent -> !statusDeleted.equals(ent.getStatus()))
               .collect(Collectors.toList());
  }

  @Override
  public Collection<T> get() {
    return get(isPublicAdmin(), null);
  }

  @Override
  public Collection<T> getEditable() {
    if (!isPublicAdmin()) {
      return get(false, null);
    }

    if (isSuper() || adminCanEditAllPublic) {
      return getPublic();
    }

    return get(true, getPrincipal().getPrincipalRef());
  }

  @Override
  public GetEntityResponse<T> getByUid(final String uid) {
    final var resp = new GetEntityResponse<T>();
    T ent = getCachedByUid(uid);

    if (ent != null) {
      resp.setEntity(ent);
      return resp;
    }

    try {
      ent = fetchIndexedByUid(uid);

      if (ent == null) {
        return Response.notFound(resp);
      }

      putCachedByUid(uid, ent);

      resp.setEntity(ent);
      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  @Override
  public T get(final String href) {
    return fetchIndexed(href);
  }

  @Override
  public GetEntitiesResponse<T> getByUids(final Collection<String> uids)  {
    final GetEntitiesResponse<T> resp = new GetEntitiesResponse<>();

    if (Util.isEmpty(uids)) {
      return resp;
    }

    for (final String uid: uids) {
      final GetEntityResponse<T> ent = getByUid(uid);
      if (ent.isOk()) {
        resp.addEntity(ent.getEntity());
        continue;
      }

      if (ent.isNotFound()) {
        continue;
      }

      return Response.fromResponse(resp, ent);
    }

    return resp;
  }

  @Override
  public T getPersistent(final String uid) {
    return getCoreHdlr().get(uid);
  }

  @Override
  public GetEntityResponse<T> findPersistent(final BwString val) {
    final var resp = new GetEntityResponse<T>();
    final BwPrincipal owner = getSvc().getEntityOwner();

    try {
      final T ent = getCoreHdlr().find(val, owner.getPrincipalRef());

      if (ent == null) {
        return Response.notFound(resp);
      }
      resp.setEntity(ent);

      return Response.ok(resp, null);
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  @Override
  public Response add(final T val) {
    final Response resp = new Response();
    getSvc().setupSharableEntity(val, getPrincipal().getPrincipalRef());

    if (!updateOK(resp, val)) {
      return resp;
    }

    final var exists = exists(resp, val);
    if (resp.isError()) {
      return resp;
    }

    if (exists) {
      resp.setStatus(Response.Status.exists);
      return resp;
    }

    if (debug()) {
      debug("Add " + val);
    }

    if ((val.getCreatorHref() == null) ||
        (val.getOwnerHref() == null)) {
      return Response.error(resp, "Owner and creator must be set");
    }

    try {
      getCal().saveOrUpdate(val);
      ((Preferences)getSvc().getPrefsHandler())
              .updateAdminPrefs(false, val);

      getCoreHdlr().checkUnique(val.getFinderKeyValue(),
                           val.getOwnerHref());

      getCal().indexEntity(val);

      // Update cached
      final Collection<T> ents = get();
      if (ents != null) {
        ents.add(val);
      }

      putCachedByUid(val.getUid(), val);

      return resp;
    } catch (final Throwable t) {
      return Response.error(resp, t);
    }
  }

  @Override
  public void update(final T val) {
    if ((val.getCreatorHref() == null) ||
        (val.getOwnerHref() == null)) {
      throw new RuntimeException("Owner and creator must be set");
    }

    if (check(val) == null) {
      throw new BedeworkAccessException();
    }

    getCal().saveOrUpdate(val);
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(false, val);

    getCoreHdlr().checkUnique(val.getFinderKeyValue(), val.getOwnerHref());

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
  public int delete(final T val) {
    T ent = val;

    if (val.unsaved()) {
      ent = getPersistent(val.getUid());
    }

    deleteOK(ent);

    /* Only allow delete if not in use
     */
    if (getCoreHdlr().getRefsCount(ent) != 0) {
      return 2;
    }

    /* Remove from preferences */
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true,
                                                               ent);

    getCoreHdlr().deleteProp(ent);

    getSvc().getIndexer(ent).unindexEntity(ent);

    // Update cached
    final Collection<T> ents = get();
    if (ents != null) {
      ents.remove(ent);
    }

    removeCachedByUid(ent.getUid());

    return 0;
  }

  @Override
  public Collection<EventPropertiesReference> getRefs(final T val) {
    final T persistent = getPersistent(val.getUid());

    if (persistent == null) {
      return null;
    }
    return getCoreHdlr().getRefs(persistent);
  }

  @Override
  public EnsureEntityExistsResult<T> ensureExists(final T val,
                                                  final String ownerHref) {
    final EnsureEntityExistsResult<T> eeer = new EnsureEntityExistsResult<>();

    if (!val.unsaved()) {
      // Exists
      eeer.setEntity(val);
      return eeer;
    }

    final String oh;
    if (ownerHref == null) {
      oh = getPrincipal().getPrincipalRef();
    } else {
      oh = ownerHref;
    }

    try {
      final var resp = findPersistent(val, oh);

      if (resp.isError()) {
        Response.fromResponse(eeer, resp);
        return eeer;
      }

      if (resp.isOk()) {
        // Exists
        eeer.setEntity(resp.getEntity());
        return eeer;
      }

      // doesn't exist at this point, so we add it to db table
      getSvc().setupSharableEntity(val, ownerHref);
      final var addResp = add(val);

      if (!addResp.isOk()) {
        return Response.fromResponse(eeer, addResp);
      }
      eeer.setAdded(true);
      eeer.setEntity(val);

      return eeer;
    } catch (final Throwable t) {
      return Response.error(eeer, t);
    }
  }

  @Override
  public int reindex(final BwIndexer indexer) {
    final BwPrincipal owner = getSvc().getEntityOwner();

    final Collection<T> ents =
            getCoreHdlr().getAll(owner.getPrincipalRef());
    if (Util.isEmpty(ents)) {
      return 0;
    }

    for (final T ent: ents) {
      indexer.indexEntity(ent);
    }

    return ents.size();
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  public BwIndexer getIndexer() {
    return getIndexer(getDocType());
  }

  public BwIndexer getIndexer(final boolean getPublic,
                                 final String ownerHref) {
    final String href;
    try {
      href = checkHref(ownerHref);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }

    final boolean publick = getPublic || isGuest() || isPublicAdmin();

    if (publick) {
      return getSvc().getIndexer(true, getDocType());
    }

    return getSvc().getIndexer(href, getDocType());
  }

  protected CoreEventPropertiesI<T> getCoreHdlr() {
    if (coreHdlr == null) {
      coreHdlr = getCal().getEvPropsHandler(ourClass);
    }

    return coreHdlr;
  }

  /**
   * @return true if indexed data changed or error occurred
   */
  protected boolean indexChanged() {
    try {
      final String token = getIndexer().currentChangeToken();

      final boolean changed = lastChangeToken == null ||
              !lastChangeToken.equals(token);

      lastChangeToken = token;

      return changed;
    } catch (final Throwable t) {
      error(t);
      return true;
    }
  }

  protected Collection<T> getCached(final String ownerHref)  {
    checkChache();
    return cached.get(ownerHref);
  }

  protected void putCached(final String ownerHref,
                           final Collection<T> vals) {
    cached.put(ownerHref, vals);
  }

  /*
  protected void removeCached(final String ownerHref) {
    cached.remove(ownerHref);
  }
   */

  protected T getCachedByUid(final String uid) {
    checkChache();
    return cachedByUid.get(uid);
  }

  protected void putCachedByUid(final String uid,
                                final T val) {
    cachedByUid.put(uid, val);
  }

  protected void removeCachedByUid(final String uid) {
    cachedByUid.remove(uid);
  }

  protected GetEntityResponse<T> findPersistent(final BwString val,
                                                final String ownerHref) {
    final var resp = new GetEntityResponse<T>();

    try {
      final T ent = getCoreHdlr().find(val, ownerHref);
      if (ent == null) {
        resp.setStatus(Response.Status.notFound);
      } else {
        resp.setEntity(ent);
      }

      return resp;
    } catch (final BedeworkException be) {
      return Response.error(resp, be);
    }
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private void checkChache() {
    if (indexChanged()) {
      cached.clear();
      cachedByUid.clear();
    }
  }

  private Collection<T> get(final boolean publick,
                            final String creatorHref) {
    final String ownerHref;

    if (publick) {
      ownerHref = getSvc().getUsersHandler()
                          .getPublicUser()
                          .getPrincipalRef();
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

  private String checkHref(final String ownerHref) {
    if (ownerHref != null) {
      return ownerHref;
    }

    // Assume public
    return BwPrincipal.publicUserHref;
  }

  private T check(final T ent) {
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

  private void deleteOK(final T o) {
    if (o == null) {
      return;
    }

    if (isGuest()) {
      throw new BedeworkAccessException();
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

    throw new BedeworkAccessException();
  }

  /* This provides some limits to shareable entity updates for the
   * admin users. It is applied in addition to the normal access checks
   * applied at the lower levels.
   */
  private boolean updateOK(final Response resp,
                           final Object o) {
    if (isGuest()) {
      resp.setStatus(Response.Status.noAccess);
      return false;
    }

    if (isSuper()) {
      // Always ok
      return true;
    }

    if (!(o instanceof BwShareableDbentity)) {
      resp.setStatus(Response.Status.noAccess);
      return false;
    }

    if (!isPublicAdmin()) {
      // Normal access checks apply
      return true;
    }

    final BwShareableDbentity<?> ent = (BwShareableDbentity<?>)o;

    if (adminCanEditAllPublic ||
            ent.getCreatorHref().equals(getPrincipal().getPrincipalRef())) {
      return true;
    }

    resp.setStatus(Response.Status.noAccess);
    return false;
  }
}

