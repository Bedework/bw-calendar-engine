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
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwString;
import org.bedework.calfacade.EventPropertiesReference;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calsvc.indexing.BwIndexer;
import org.bedework.calsvci.Categories;
import edu.rpi.cmt.access.PrivilegeDefs;
import edu.rpi.sss.util.FlushMap;
import edu.rpi.sss.util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Class which handles manipulation of Categories.
 *
 * @author Mike Douglass   douglm - rpi.edu
 */
public class CategoriesImpl
        extends CalSvcDb implements Categories, PrivilegeDefs {
  /* We'll cache lists of categories by principal href - flushing them
    every so often.
   */
  private static FlushMap<String, List<BwCategory>> cached =
      new FlushMap<>(60 * 1000 * 5, // 5 mins
                     2000);  // max size

  private static FlushMap<String, BwCategory> cachedByUid =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         2000);  // max size

  /* We'll cache the indexers we use. Use a map for non-public
   */
  private static FlushMap<String, BwIndexer> userIndexers =
          new FlushMap<>(60 * 1000 * 5, // 5 mins
                         200);  // max size

  private static BwIndexer publicIndexer;

  private CoreEventPropertiesI<BwCategory> coreHdlr;

  private boolean adminCanEditAllPublic;

  /** Constructor
  *
  * @param svci calsvc object
  */
  public CategoriesImpl(final CalSvc svci) {
    super(svci);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void init(final boolean adminCanEditAllPublic) {
    this.adminCanEditAllPublic = adminCanEditAllPublic;

    try {
      coreHdlr = getCal().getEvPropsHandler(BwCategory.class);
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }

  @Override
  public List<BwCategory> get(final String ownerHref,
                              final String creatorHref) throws CalFacadeException {
    List<BwCategory> cats = cached.get(ownerHref);

    if (cats == null) {
      cats = getIndexer(ownerHref).fetchAllCats();

      if (Util.isEmpty(cats)) {
        return new ArrayList<>();
      }

      cached.put(ownerHref, cats);
    }

    if (creatorHref == null) {
      return cats;
    }

    List<BwCategory> someCats = new ArrayList<>();
    for (BwCategory cat: cats) {
      if (cat.getCreatorHref().equals(creatorHref)) {
        someCats.add(cat);
      }
    }

    return someCats;
  }

  @Override
  public List<BwCategory> get() throws CalFacadeException {
    BwPrincipal owner;
    if (!isPublicAdmin()) {
      owner = getPrincipal();
    } else {
      owner = getPublicUser();
    }

    return get(owner.getPrincipalRef(), null);
  }

  @Override
  public List<BwCategory> getEditable() throws CalFacadeException {
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
  public BwCategory get(final String uid) throws CalFacadeException {
    BwCategory cat = cachedByUid.get(uid);

    if (cat != null) {
      return cat;
    }

    cat = getIndexer().fetchCat("uid", uid);

    cachedByUid.put(uid, cat);

    return cat;
  }

  @Override
  public List<BwCategory> get(final Collection<String> uids)
          throws CalFacadeException {
    List<BwCategory> cats = new ArrayList<>();

    if (Util.isEmpty(uids)) {
      return cats;
    }

    for (String uid: uids) {
      BwCategory cat = get(uid);
      if (cat != null) {
        cats.add(cat);
      }
    }

    return cats;
  }

  @Override
  public boolean exists(BwCategory cat) throws CalFacadeException {
    return find(cat.getWord()) != null;
  }

  @Override
  public BwCategory getPersistent(final String uid) throws CalFacadeException {
    return coreHdlr.get(uid);
  }

  @Override
  public BwCategory find(final BwString val) throws CalFacadeException {
    return getIndexer().fetchCat("word", val.getValue());
  }

  @Override
  public boolean add(final BwCategory val) throws CalFacadeException {
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

    getIndexer(val.getOwnerHref()).indexEntity(val);

    // Force a refetch
    cached.remove(val.getOwnerHref());
    cachedByUid.remove(val.getUid());

    return true;
  }

  @Override
  public void update(final BwCategory val) throws CalFacadeException {
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

    getIndexer(val.getOwnerHref()).indexEntity(val);

    // Force a refetch
    cached.remove(val.getOwnerHref());
    cachedByUid.remove(val.getUid());
  }

  @Override
  public int delete(final BwCategory val) throws CalFacadeException {
    deleteOK(val);

    /** Only allow delete if not in use
     */
    if (coreHdlr.getRefsCount(val) != 0) {
      return 2;
    }

    /* Remove from preferences */
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true, val);

    coreHdlr.deleteProp(val);

    getIndexer(val.getOwnerHref()).unindexEntity(val);

    // Force a refetch
    cached.remove(val.getOwnerHref());
    cachedByUid.remove(val.getUid());

    return 0;
  }

  @Override
  public Collection<EventPropertiesReference> getRefs(final BwCategory val) throws CalFacadeException {
    return coreHdlr.getRefs(val);
  }

  @Override
  public int reindex() throws CalFacadeException {
    BwPrincipal owner;
    if (!isPublicAdmin()) {
      owner = getPrincipal();
    } else {
      owner = getPublicUser();
    }

    Collection<BwCategory> cats = coreHdlr.get(owner.getPrincipalRef(),
                                               null);
    if (Util.isEmpty(cats)) {
      return 0;
    }

    BwIndexer idx = getIndexer();

    for (BwCategory cat: cats) {
      idx.indexEntity(cat);
    }

    return cats.size();
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private String checkHref(String ownerHref) throws CalFacadeException {
    if (ownerHref != null) {
      return ownerHref;
    }

    // Assume public
    return getSvc().getUsersHandler().getPublicUser().getPrincipalRef();
  }

  private BwIndexer getIndexer() throws CalFacadeException {
    return getIndexer(getOwnerHref());
  }

  private BwIndexer getIndexer(String ownerHref) throws CalFacadeException {
    String href = checkHref(ownerHref);

    boolean publick = isGuest() || isPublicAdmin();

    if (publick) {
      if (publicIndexer == null) {
        publicIndexer = getSvc().getIndexingHandler().getIndexer(true,
                                                                 href);
      }

      return publicIndexer;
    }

    BwIndexer idx = userIndexers.get(href);

    if (idx != null) {
      return idx;
    }

    idx = getSvc().getIndexingHandler().getIndexer(false,
                                                   href);

    userIndexers.put(href, idx);

    return idx;
  }

  private BwCategory check(final BwCategory ent) throws CalFacadeException {
    if (ent == null) {
      return null;
    }

    if (ent.getPublick()) {
      return ent;
    }

    if (this.getPrincipal().getPrincipalRef().equals(ent.getOwnerHref())) {
      return ent;
    }

    return null;
  }

  private void deleteOK(final BwCategory o) throws CalFacadeException {
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
  private void updateOK(final BwCategory val) throws CalFacadeException {
    if (isGuest()) {
      throw new CalFacadeAccessException();
    }

    if (isSuper()) {
      // Always ok
      return;
    }

    if (!isPublicAdmin()) {
      // Normal access checks apply
      return;
    }

    if (adminCanEditAllPublic ||
            val.getCreatorHref().equals(getPrincipal().getPrincipalRef())) {
      return;
    }

    throw new CalFacadeAccessException();
  }
}

