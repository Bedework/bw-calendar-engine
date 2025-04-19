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
package org.bedework.calcore.rw.common;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.calcore.ro.AccessUtil;
import org.bedework.calcore.ro.CalintfHelper;
import org.bedework.calcore.ro.CollectionCache;
import org.bedework.calcore.rw.common.dao.CollectionsDAO;
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreCollectionsI;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.CollectionAliases;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwLastMod;
import org.bedework.calfacade.base.BwUnversionedDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.wrappers.CollectionWrapper;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.PropertyIndex.PropertyInfoIndex;
import org.bedework.util.misc.Util;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.base.response.Response.Status.noAccess;
import static org.bedework.base.response.Response.Status.notFound;
import static org.bedework.base.response.Response.Status.ok;
import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Class to encapsulate most of what we do with collections
 *
 * @author douglm
 *
 */
public class CoreCollections extends CalintfHelper
         implements AccessUtil.CollectionGetter, Transactions,
        CoreCollectionsI {
  private final Calintf intf;
  private final CollectionsDAO dao;
  private final String userCollectionRootPath;
  //private String groupCollectionRootPath;

  private final CollectionCache colCache;

  /** Constructor
   *
   * @param dao for db access
   * @param intf interface
   * @param ac access checker
   * @param sessionless if true
   */
  public CoreCollections(final CollectionsDAO dao,
                         final Calintf intf,
                         final AccessChecker ac,
                         final boolean sessionless) {
    this.dao = dao;
    this.intf = intf;
    super.init(intf, ac, sessionless);

    userCollectionRootPath =
            Util.buildPath(colPathEndsWithSlash, 
                           "/", BasicSystemProperties.userCollectionRoot);
    //groupCollectionRootPath = userCollectionRootPath + "/" + "groups";

    colCache =
            new CollectionCache(this,
                                intf.getStats().getCollectionCacheStats());
  }

  @Override
  public void startTransaction() {
    colCache.flush();  // Just in case
  }

  @Override
  public void endTransaction() {
    colCache.flush();
  }

  @Override
  public void rollback() {
    dao.rollback();
  }

  @Override
  public <T> T throwException(final BedeworkException be)
          {
    dao.rollback();
    throw be;
  }

  @Override
  public BwCollection getCollectionNoCheck(final String path) {
    return getCollection(path);
  }

  @Override
  public BwCollection getCollection(final String path) {
    if (path == null) {
      return null;
    }

    BwCollection col = colCache.get(path);

    if (col != null) {
      return col;
    }

    col = dao.getCollection(path);

    if (col == null) {
      if (path.equals("/")) {
        // Fake a root collection
        col = new BwCollection();
        col.setPath("/");

        // Use this for owner/creator
        final BwCollection userRoot = getCollection(
                userCollectionRootPath);

        if (userRoot == null) {
          return null;
        }

        col.setOwnerHref(userRoot.getOwnerHref());
        col.setCreatorHref(userRoot.getCreatorHref());
        col.setAccess(Access.getDefaultPublicAccess());
      } else if (!getPrincipal().getUnauthenticated()) {
        /* Didn't find it. Is this a special collection we should create,
           Only try this if authenticated.
         */
        final var gscr = getIfSpecial(getPrincipal(), path);

        if (gscr == null) {
          return null;
        }

        col = gscr.cal;
      } else {
        return null;
      }
    }

    final CollectionWrapper wcol = intf.wrap(col);
    if (wcol != null) {
      colCache.put(wcol);
    }

    return wcol;
  }

  /* ============================================================
   *                   CollectionsI methods
   * ============================================================ */

  @Override
  public void principalChanged() {
    colCache.clear();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    return dao.getSynchInfo(path, token);
  }
  
  @Override
  public Collection<BwCollection> getCollections(final BwCollection col,
                                                 final BwIndexer indexer) {
    if (indexer == null) {
      final Collection<BwCollection> ch = getChildren(col);

      return checkAccess(ch, privAny, true);
    } else {
      return indexer.fetchChildren(col.getPath());
    }
  }

  @Override
  public BwCollection resolveAlias(final BwCollection val,
                                   final boolean resolveSubAlias,
                                   final boolean freeBusy,
                                   final BwIndexer indexer) {
    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    // Create list of paths so we can detect a loop
    final ArrayList<String> pathElements = new ArrayList<>();
    pathElements.add(val.getPath());

    return resolveAlias(val, resolveSubAlias, freeBusy, 
                        pathElements, indexer);
  }

  @Override
  public GetEntityResponse<CollectionAliases> getAliasInfo(
          final BwCollection val) {
    throw new RuntimeException("Should not be called");
  }

  @Override
  public List<BwCollection> findAlias(final String val) {
    final List<BwCollection> aliases = dao.findCollectionAlias(fixPath(val),
                                                               currentPrincipal());

    final List<BwCollection> waliases = new ArrayList<>();

    if (Util.isEmpty(aliases)) {
      return waliases;
    }

    for (final BwCollection alias: aliases) {
      waliases.add(intf.wrap(alias));
    }

    return waliases;
  }

  @Override
  public BwCollection getCollection(final String path,
                                    final int desiredAccess,
                                    final boolean alwaysReturnResult) {
    BwCollection col = getCollection(path);
    /* TODO - fix on import/export of 4.0. topical areas had wrong owner
      */

    if ((col != null) &&
            (col.getCalType() == BwCollection.calTypeAlias) &&
            (!col.getOwnerHref().equals(col.getCreatorHref())) &&
            ("/principals/users/public-user".equals(col.getOwnerHref()))) {
      col.setOwnerHref(col.getCreatorHref());
    }

    col = checkAccess((CollectionWrapper)col, desiredAccess, alwaysReturnResult);

    return col;
  }

  @Override
  public BwCollection getCollectionIdx(final BwIndexer indexer,
                                       final String path,
                                       final int desiredAccess,
                                       final boolean alwaysReturnResult) {
    final BwCollection col = colCache.get(path);

    if (col != null) {
      return col;
    }

    final GetEntityResponse<BwCollection> ger =
            indexer.fetchCol(path, desiredAccess,
                             PropertyInfoIndex.HREF);

    if (ger.getStatus() == ok) {
      return ger.getEntity();
    }

    if (ger.getStatus() == notFound) {
      return null;
    }

    if (ger.getStatus() == noAccess) {
      if (alwaysReturnResult) {
        return null;
      }

      throw new BedeworkAccessException();
    }

    throw new BedeworkException(ger.getMessage());
  }

  @Override
  public GetSpecialCollectionResult getSpecialCollection(
          final BwIndexer indexer,
          final BwPrincipal<?> owner,
          final int calType,
          final boolean create,
          final int access) {
    return getSpecialCollection(owner, calType, create, true, access,
                                indexer);
  }

  @Override
  public BwCollection add(final BwCollection val,
                          final String parentPath) {
    return add(val, parentPath, false, privBind);
  }

  @Override
  public void renameCollection(BwCollection val,
                               final String newName) {
    colCache.flush();

    ac.checkAccess(val, privWriteProperties, false);

    final String parentPath = val.getColPath();
    final BwCollection parent = dao.getCollection(parentPath);

    /* Ensure the name isn't reserved and the path is unique */
    checkNewCollectionName(newName, false, parent);

    val = unwrap(val);

    val.setName(newName);
    val.updateLastmod(getCurrentTimestamp());

    /* Remove any tombstoned collection with the same name */
    dao.removeTombstonedVersion(val);
    getIndexer(val).unindexEntity(Util.buildPath(colPathEndsWithSlash,
                                                 parentPath,
                                                 "/",
                                                 val.getName()));

    /* This triggers off a cascade of updates down the tree as we are storing the
     * path in the collection objects. This may be preferable to calculating the
     * path at every access
     */
    updatePaths(val, parent);

    // Flush it again
    colCache.flush();
  }

  @Override
  public void moveCollection(BwCollection val,
                             final BwCollection newParent) {
    colCache.flush();

    /* check access - privbind on new parent privunbind on val?
     */
    ac.checkAccess(val, privUnbind, false);
    ac.checkAccess(newParent, privBind, false);

    if (newParent.getCalType() != BwCollection.calTypeFolder) {
      throw new BedeworkException(CalFacadeErrorCode.illegalCollectionCreation);
    }

    val = unwrap(val);

    val.updateLastmod(getCurrentTimestamp());

    final BwCollection tombstoned = val.makeTombstoneCopy();
    tombstoned.tombstone();

    /* This triggers off a cascade of updates down the tree as we are storing the
     * path in the collection objects. This may be preferable to calculating the
     * path at every access
     */
    updatePaths(val, newParent);

    dao.add(tombstoned);
    indexEntity(tombstoned);

    /* Remove any tombstoned collection with the same name
      probably not needed
    dao.removeTombstonedVersion(val);

    error("Unimplemented - remove tombstoned from index");
    */
    // Flush it again
    colCache.flush();
  }

  @Override
  public void touchCollection(final String path) {
    final BwCollection col = dao.getCollection(path);
    if (col == null) {
      return;
    }

    touchCollection(col);
  }

  @Override
  public void touchCollection(final BwCollection col) {
    dao.touchCollection(col, getCurrentTimestamp());
    // Remove it
    colCache.remove(col.getPath());

    if (!getForRestore()) {
      intf.indexEntityNow(col);
    }
  }

  @Override
  public void updateCollection(final BwCollection val) {
    ac.checkAccess(val, privWriteProperties, false);

    dao.updateCollection(unwrap(val));
    touchCollection(val.getPath()); // Also indexes

    notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

    colCache.put((CollectionWrapper)val);
  }

  @Override
  public void changeAccess(final BwCollection col,
                           final Collection<Ace> aces,
                           final boolean replaceAll) {
    ac.getAccessUtil().changeAccess(col, aces, replaceAll);

    // Clear the cache - inheritance makes it difficult to be sure of the effects.
    colCache.clear();

    dao.updateCollection(unwrap(col));

    touchCollection(col); // indexes as well

    ((CollectionWrapper)col).clearCurrentAccess(); // force recheck
    colCache.put((CollectionWrapper)col);

    notify(SysEvent.SysCode.COLLECTION_UPDATED, col);
  }

  @Override
  public void defaultAccess(final BwCollection cal,
                            final AceWho who) {
    ac.getAccessUtil().defaultAccess(cal, who);
    dao.updateCollection(unwrap(cal));

    indexEntity(cal);

    colCache.flush();

    notify(SysEvent.SysCode.COLLECTION_UPDATED, cal);
  }

  @Override
  public boolean deleteCollection(BwCollection val,
                                  final boolean reallyDelete) {
    colCache.flush();

    ac.checkAccess(val, privUnbind, false);

    final String parentPath = val.getColPath();
    if (parentPath == null) {
      throw new BedeworkException(CalFacadeErrorCode.cannotDeleteCollectionRoot);
    }

    /* Ensure the parent exists and we have writeContent on the parent.
     */
    final BwCollection parent = this.getCollection(parentPath, privWriteContent, false);
    if (parent == null) {
      throw new BedeworkException(CalFacadeErrorCode.collectionNotFound);
    }

    val = this.getCollection(val.getPath(), privUnbind, false);
    if (val == null) {
      throw new BedeworkException(CalFacadeErrorCode.collectionNotFound);
    }

    if (!isEmpty(val)) {
      throw new BedeworkException(CalFacadeErrorCode.collectionNotEmpty);
    }

    /* See if this is a no-op after all. We do this now to ensure the caller
     * really does have access
     */
    if (!reallyDelete && val.getTombstoned()) {
      // Nothing to do
      return true;
    }

    final BwCollection unwrapped = unwrap(val);
    final String path = val.getPath();
    
    /* Ensure it's not in any (auth)user preferences */

    dao.removeCollectionFromAuthPrefs(unwrapped);

    /* Ensure no tombstoned events or childen */
    dao.removeTombstoned(fixPath(path));
    getIndexer(val).unindexContained(fixPath(path));

    if (reallyDelete) {
      dao.deleteCollection(unwrapped);
      getIndexer(val).unindexEntity(path);
    } else {
      tombstoneEntity(unwrapped);
      unwrapped.tombstone();
      dao.updateCollection(unwrapped);
      touchCollection(unwrapped); // Indexes as well
    }

    colCache.remove(path);
    touchCollection(parent);

    notify(SysEvent.SysCode.COLLECTION_DELETED, val);

    return true;
  }

  @Override
  public boolean isEmpty(final BwCollection val) {
    return dao.isEmptyCollection(val);
  }
  
  @Override
  public void addNewCollections(final BwPrincipal<?> user) {
    /* Add a user collection to the userCollectionRoot and then
      a default collection. */

    String path = userCollectionRootPath;
    final BwCollection userrootcal = dao.getCollection(path);

    if (userrootcal == null) {
      throw new BedeworkException("No user root at " + path);
    }

    BwCollection parentCal = userrootcal;
    BwCollection usercal = null;

    /* We may have a principal e.g. /principals/resources/vcc311
     * All except the last may exist already.
     */
    final String[] upath = user.getAccountSplit();

    for (int i = 0; i < upath.length; i++) {
      final String pathSeg = upath[i];

      if ((pathSeg == null) || (pathSeg.isEmpty())) {
        // Leading or double slash - skip it
        continue;
      }

      path = Util.buildPath(colPathEndsWithSlash, path, "/", pathSeg);

      usercal = dao.getCollection(path);
      if (i == (upath.length - 1)) {
        if (usercal != null) {
          throw new BedeworkException(
                  "User collection already exists at " + path);
        }

        /* Create a folder for the user */
        usercal = new BwCollection();
        usercal.setName(pathSeg);
        usercal.setCreatorHref(user.getPrincipalRef());
        usercal.setOwnerHref(user.getPrincipalRef());
        usercal.setPublick(false);
        usercal.setPath(path);
        usercal.setColPath(parentCal.getPath());

        final var lm = usercal.getLastmod();
        lm.updateLastmod(getCurrentTimestamp());

        dao.addCollection(usercal);

        indexEntityNow(usercal);

        notify(SysEvent.SysCode.COLLECTION_ADDED, usercal);
      } else if (usercal == null) {
        /* Create a new system owned folder for part of the principal
         * hierarchy
         */
        usercal = new BwCollection();
        usercal.setName(pathSeg);
        usercal.setCreatorHref(userrootcal.getCreatorHref());
        usercal.setOwnerHref(userrootcal.getOwnerHref());
        usercal.setPublick(false);
        usercal.setPath(path);
        usercal.setColPath(parentCal.getPath());

        final BwCollectionLastmod lm = usercal.getLastmod();
        lm.updateLastmod(getCurrentTimestamp());

        dao.addCollection(usercal);

        indexEntityNow(usercal);

        notify(SysEvent.SysCode.COLLECTION_ADDED, usercal);
      }

      parentCal = usercal;
    }

    if (usercal == null) {
      throw new BedeworkException("Invalid user " + user);
    }

    /* Create a default collection */
    final var cal = new BwCollection();
    cal.setName(BasicSystemProperties.userDefaultCollection);
    cal.setCreatorHref(user.getPrincipalRef());
    cal.setOwnerHref(user.getPrincipalRef());
    cal.setPublick(false);
    cal.setPath(Util.buildPath(colPathEndsWithSlash, path, "/",
                               cal.getName()));
    cal.setColPath(usercal.getPath());
    cal.setCalType(BwCollection.calTypeCalendarCollection);
    cal.setAffectsFreeBusy(true);

    final var lm = cal.getLastmod();
    lm.updateLastmod(getCurrentTimestamp());

    dao.addCollection(cal);

    indexEntity(cal);
    notify(SysEvent.SysCode.COLLECTION_ADDED, cal);
    dao.update(user);
  }

  @Override
  public Set<BwCollection> getSynchCols(final String path,
                                        final String token) {
    final Collection<BwCollection> cols =
            dao.getSynchCollections(fixPath(path),
                                    token);

    final Set<BwCollection> res = new TreeSet<>();

    for (final BwCollection col: cols) {
      final BwCollection wcol = intf.wrap(col);
      final CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      res.add(wcol);
    }

    return res;
  }

  @Override
  public boolean testSynchCol(final BwCollection col,
                              final String token)
          {
    throw new BedeworkException("Should not get here - handled by interface");
  }

  @Override
  public String getSyncToken(final String path) {
    final BwCollection thisCol = this.getCollection(path, privAny, false);
    
    if (thisCol == null) {
      return null;
    }

    /* Because we don't have a trailing "/" on the paths the path 
       prefix may pull in more than we want. We have to check the path on 
       return.
       
       For example - if path is /a/x - we might get /a/x/y but we might
       also get /a/xxx/y
     */

    final String fpath = fixPath(path); // Removes "/"
    final String fpathSlash = fpath + "/";
    
    final List<BwCollection> cols = dao.getPathPrefix(fpath);

    String token = thisCol.getLastmod().getTagValue();

    for (final BwCollection col: cols) {
      final String colPath = col.getPath();
      
      if (!colPath.equals(fpath) && !colPath.startsWith(fpathSlash)) {
        continue;
      }
      
      final BwCollection wcol = intf.wrap(col);
      final CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      final String t = col.getLastmod().getTagValue();

      if (t.compareTo(token) > 0) {
        token = t;
      }
    }

    return token;
  }

  @Override
  public Collection<String> getChildCollections(final String parentPath,
                                                final int start,
                                                final int count) {
    return dao.getChildrenCollections(parentPath, start, count);
  }
  
  /* ==============================================================
   *                   Private methods
   * ============================================================== */

  private GetSpecialCollectionResult getIfSpecial(final BwPrincipal<?> owner,
                                                  final String path) {
    final String pathTo = intf.getPrincipalInfo().getCollectionHomePath(owner);

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.userInbox)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypeInbox,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.userPendingInbox)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypePendingInbox,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.userOutbox)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypeOutbox,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.defaultNotificationsName)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypeNotifications,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.defaultReferencesName)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypeEventList,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/",
                       BasicSystemProperties.userDefaultPollsCollection)
            .equals(path)) {
      return getSpecialCollection(owner, BwCollection.calTypePoll,
                                  true, false, PrivilegeDefs.privAny,
                                  null);
    }

    return null;
  }

  private GetSpecialCollectionResult getSpecialCollection(
          final BwPrincipal<?> owner,
          final int calType,
          final boolean create,
          final boolean tryFetch,
          final int access,
          final BwIndexer indexer) {
    final String name = intf.getCollectionNameFromType(calType);
    if (name == null) {
      // Not supported
      return null;
    }

    final String pathTo = intf.getPrincipalInfo().getCollectionHomePath(owner);

    final GetSpecialCollectionResult gscr = new GetSpecialCollectionResult();

    if (!dao.collectionExists(pathTo)) {
      gscr.noUserHome = true;
      return gscr;
    }

    if (tryFetch){
      if (indexer != null) {
        gscr.cal = getCollectionIdx(indexer,
                                    Util.buildPath(colPathEndsWithSlash,
                                                   pathTo, "/", name),
                                    access, false);
      } else {
        gscr.cal = this.getCollection(Util.buildPath(colPathEndsWithSlash,
                                                     pathTo, "/", name),
                                      access, false);
      }

      if ((gscr.cal != null) || !create) {
        return gscr;
      }
    }

    /*
    BwCollection parent = getCollection(pathTo, privRead);

    if (parent == null) {
      throw new BedeworkException("org.bedework.calcore.calenollectiondars.unabletocreate");
    }
    */

    gscr.cal = new BwCollection();
    gscr.cal.setName(name);
    gscr.cal.setCreatorHref(owner.getPrincipalRef());
    gscr.cal.setOwnerHref(owner.getPrincipalRef());
    gscr.cal.setCalType(calType);

    /* I think we're allowing privNone here because we don't mind if the
     * collection gets created even if the caller has no access.
     */
    gscr.cal = add(gscr.cal, pathTo, true, access);
    gscr.created = true;

    return gscr;
  }

  /*
    indexer != null => Use ES for the searches
   */
  private BwCollection resolveAlias(final BwCollection val,
                                    final boolean resolveSubAlias,
                                    final boolean freeBusy,
                                    final ArrayList<String> pathElements,
                                    final BwIndexer indexer) {
    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    final BwCollection c = val.getAliasTarget();
    if (c != null) {
      if (!resolveSubAlias) {
        return c;
      }

      final BwCollection res = resolveAlias(c, true, freeBusy, pathElements, indexer);
      res.setAliasOrigin(val);
      
      return res;
    }

    if (val.getDisabled()) {
      return null;
    }

    int desiredAccess = privRead;
    if (freeBusy) {
      desiredAccess = privReadFreeBusy;
    }

    final String path = val.getInternalAliasPath();

    if (pathElements.contains(path)) {
      disableAlias(val);
      return null;
    }

    pathElements.add(path);

    //if (debug()) {
    //  debug("Search for collection \"" + path + "\"");
    //}

    BwCollection col;

    try {
      if (indexer != null) {
        col = getCollectionIdx(indexer, path, desiredAccess, false);
      } else {
        col = this.getCollection(path, desiredAccess, false);
      }
    } catch (final BedeworkAccessException ignored) {
      col = null;
    }

    if (col == null) {
      /* Assume deleted - flag in the subscription if it's ours or a temp.
       */
      if (val.unsaved() ||
          val.getOwnerHref().equals(getPrincipal().getPrincipalRef())) {
        disableAlias(val);
      }

      return null;
    }

    val.setAliasTarget(col);

    if (!resolveSubAlias) {
      col.setAliasOrigin(val);
      return col;
    }

    final BwCollection res = resolveAlias(col, true, freeBusy, pathElements, indexer);
    res.setAliasOrigin(val);

    return res;
  }

  private void disableAlias(final BwCollection val) {
    val.setDisabled(true);
    if (!val.unsaved()) {
      // Save the state
      val.updateLastmod(getCurrentTimestamp());
      dao.updateCollection(unwrap(val));

      indexEntity(val);
      //touchCollection(val.getPath());

      notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

      colCache.put((CollectionWrapper)val);
    }
  }

  private void checkNewCollectionName(final String name,
                                      final boolean special,
                                      final BwCollection parent) {
    // XXX This should be accessible to all implementations.
    if (!special) {
      /* Ensure the name isn't reserved */

      switch (name) {
        case BasicSystemProperties.userInbox ->
                throw new BedeworkException(
                        CalFacadeErrorCode.illegalCollectionCreation);
        case BasicSystemProperties.userOutbox ->
                throw new BedeworkException(
                        CalFacadeErrorCode.illegalCollectionCreation);
        case BasicSystemProperties.defaultNotificationsName ->
                throw new BedeworkException(
                        CalFacadeErrorCode.illegalCollectionCreation);
      }

    }

    /* Ensure the name is not-null and contains no invalid characters
     */
    if ((name == null) ||
        name.contains("/")) {
      throw new BedeworkException(CalFacadeErrorCode.illegalCollectionCreation);
    }

    /* Ensure the new path is unique */
    String path;
    if (parent == null) {
      path = "";
    } else {
      path = parent.getPath();
    }

    path = Util.buildPath(colPathEndsWithSlash, path, "/", name);
    final BwCollection col = dao.getCollection(path);

    if (col != null) {
      if (!col.getTombstoned()) {
        throw new BedeworkException(CalFacadeErrorCode.duplicateCollection);
      }

      dao.deleteCollection(unwrap(col));
    }
  }

  private BwCollection add(final BwCollection val,
                           final String parentPath,
                           final boolean special,
                           final int access) {
    BwCollection parent = null;
    final String newPath;

    if ("/".equals(parentPath)) {
      // creating a new root
      newPath = Util.buildPath(colPathEndsWithSlash, "/", val.getName());
    } else {
      parent = this.getCollection(parentPath, access, false);

      if (parent == null) {
        throw new BedeworkException(CalFacadeErrorCode.collectionNotFound,
                                     parentPath);
      }

      /* Is the parent a calendar collection or a resource folder?
       */
      if (parent.getCalendarCollection() ||
          (parent.getCalType() == BwCollection.calTypeResourceCollection)) {
        if (val.getAlias() ||
            ((val.getCalType() != BwCollection.calTypeFolder) &&
            (val.getCalType() != BwCollection.calTypeResourceCollection))) {
          throw new BedeworkException(CalFacadeErrorCode.illegalCollectionCreation);
        }

        if (val.getCalType() == BwCollection.calTypeFolder) {
          val.setCalType(BwCollection.calTypeResourceCollection);
        }
      } else if (parent.getCalType() != BwCollection.calTypeFolder) {
        throw new BedeworkException(CalFacadeErrorCode.illegalCollectionCreation);
      }

      newPath = Util.buildPath(colPathEndsWithSlash, parent.getPath(), 
                               "/", val.getName());
    }

    /* Ensure the name isn't reserved and is unique */
    checkNewCollectionName(val.getName(), special, parent);

    val.setPath(newPath);
    if (val.getOwnerHref() == null) {
      val.setOwnerHref(getPrincipal().getPrincipalRef());
    }
    val.updateLastmod(getCurrentTimestamp());

    if (parent != null) {
      val.setColPath(parent.getPath());
      val.setPublick(parent.getPublick());
    }

    /* Remove any tombstoned collection with the same name */
    // Not necessary? We just overwrite
    dao.removeTombstonedVersion(val);

    // No cascades - explicitly save child
    dao.addCollection(unwrap(val));

    if (parent != null) {
      touchCollection(parent);
    }

    indexEntityNow(val);

    notify(SysEvent.SysCode.COLLECTION_ADDED, val);

    final CollectionWrapper wcol = intf.wrap(val);

    colCache.put(wcol);

    return checkAccess(wcol, privAny, true);
  }

  private void updatePaths(BwCollection val,
                           final BwCollection newParent) {
    final Collection<BwCollection> children = getChildren(val);

    final String oldHref = val.getPath();

    unindex(val);

    val = unwrap(val);

    final String ppath = newParent.getPath();
    val.setPath(Util.buildPath(colPathEndsWithSlash, ppath, "/", 
                               val.getName()));
    val.setColPath(ppath);

    val.getLastmod().setPath(val.getPath());
    val.updateLastmod(getCurrentTimestamp());

    indexEntity(val);

    notifyMove(SysEvent.SysCode.COLLECTION_MOVED,
               oldHref, val);

    //updateCollection(val);

    for (final BwCollection ch: children) {
      updatePaths(ch, val);
    }
  }

  /** Return a Collection of the objects after checking access and wrapping
   *
   * @param ents          Collection of BwCollection
   * @param desiredAccess access we want
   * @param nullForNoAccess boolean flag behaviour on no access
   * @return Collection   of checked objects
   */
  private Collection<BwCollection> checkAccess(
          final Collection<BwCollection> ents,
          @SuppressWarnings("SameParameterValue") final int desiredAccess,
          @SuppressWarnings("SameParameterValue") final boolean nullForNoAccess)
  {
    final TreeSet<BwCollection> out = new TreeSet<>();
    if (ents == null) {
      return out;
    }

    for (BwCollection cal: ents) {
      cal = checkAccess((CollectionWrapper)cal, desiredAccess, nullForNoAccess);
      if (cal != null) {
        out.add(cal);
      }
    }

    return out;
  }

  public BwCollection checkAccess(final CollectionWrapper col,
                                  final int desiredAccess,
                                  final boolean alwaysReturnResult)
          {
    if (col == null) {
      return null;
    }

    final boolean noAccessNeeded = desiredAccess == privNone;

    final CurrentAccess ca = 
            ac.checkAccess(col, desiredAccess,
                           alwaysReturnResult || noAccessNeeded);

    if (!noAccessNeeded && !ca.getAccessAllowed()) {
      return null;
    }

    return col;
  }

  @Override
  public BwUnversionedDbentity<?> merge(
          final BwUnversionedDbentity<?> val) {
    return dao.merge(val);
  }

  private void notify(final SysEvent.SysCode code,
                      final BwCollection val) {
    final boolean indexed = true;
    if (code.equals(SysEvent.SysCode.COLLECTION_DELETED)) {
      postNotification(
              SysEvent.makeCollectionDeletedEvent(code,
                                                  authenticatedPrincipal(),
                                                  val.getOwnerHref(),
                                                  val.getPath(),
                                                  val.getShared(),
                                                  val.getPublick(),
                                                  indexed));
    } else {
      postNotification(
              SysEvent.makeCollectionUpdateEvent(code,
                                                 authenticatedPrincipal(),
                                                 val.getOwnerHref(),
                                                 val.getPath(),
                                                 val.getShared(),
                                                 indexed));
    }
  }

  private void notifyMove(@SuppressWarnings("SameParameterValue") final SysEvent.SysCode code,
                          final String oldHref,
                          final BwCollection val) {
    final boolean indexed = true;

    postNotification(
            SysEvent.makeCollectionMovedEvent(code,
                                              authenticatedPrincipal(),
                                              val.getOwnerHref(),
                                              val.getPath(),
                                              val.getShared(),
                                              indexed,
                                              oldHref,
                                              false)); // XXX wrong
  }

  /* No access checks performed */
  private Collection<BwCollection> getChildren(final BwCollection col) {
    final List<BwCollection> ch;
    final List<BwCollection> wch = new ArrayList<>();

    if (col == null) {
      return wch;
    }

    if (sessionless) {
      /*
         Maybe we should just fetch them. We've probably not seen them and
         we're just working our way down a tree. The 2 phase might be slower.
       */

      ch = dao.getChildCollections(col.getPath());
    } else {
      /* Fetch the lastmod and paths of all children then fetch those we haven't
       * got in the cache.
       */

      final var lmps =
              dao.getChildLastModsAndPaths(col.getPath());

      final List<String> paths = new ArrayList<>();

      if (Util.isEmpty(lmps)) {
        return wch;
      }

      for (final var lmp: lmps) {
        final String token =
                BwLastMod.getTagValue(lmp.timestamp(),
                                      lmp.sequence());

        final BwCollection c = colCache.get(lmp.path(), token);

        if ((c != null) && !c.getTombstoned()) {
          wch.add(c);
          continue;
        }

        paths.add(lmp.path());
      }

      if (paths.isEmpty()) {
        return wch;
      }

      /* paths lists those we couldn't find in the cache. */

      ch = dao.getCollections(paths);
    }

    /* Wrap the resulting objects. */

    if (Util.isEmpty(ch)) {
      return wch;
    }

    for (final BwCollection c: ch) {
      final CollectionWrapper wc = intf.wrap(c);

      colCache.put(wc);
      wch.add(wc);
    }

    return wch;
  }

  /** Only valid during a transaction.
   *
   * @return a timestamp from the db
   */
  private Timestamp getCurrentTimestamp() {
    return intf.getCurrentTimestamp();
  }
}
