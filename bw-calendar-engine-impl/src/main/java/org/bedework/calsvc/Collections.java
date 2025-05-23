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

import org.bedework.access.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.base.exc.BedeworkAccessException;
import org.bedework.base.exc.BedeworkException;
import org.bedework.base.exc.BedeworkForbidden;
import org.bedework.calcorei.CoreCollectionsI.GetSpecialCollectionResult;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.calfacade.AliasesInfo;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCategory;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.CollectionAliases;
import org.bedework.calfacade.base.BwShareableDbentity;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.calfacade.svc.BwAuthUser;
import org.bedework.calfacade.svc.BwPreferences;
import org.bedework.calfacade.svc.EventInfo;
import org.bedework.calsvci.CollectionsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.SynchI;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Util;
import org.bedework.base.response.GetEntityResponse;
import org.bedework.base.response.Response;
import org.bedework.util.timezones.DateTimeUtil;
import org.bedework.util.xml.tagdefs.AppleServerTags;

import net.fortuna.ical4j.model.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;
import static org.bedework.calfacade.configs.BasicSystemProperties.publicCollectionRootPath;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;

/** This acts as an interface to the database for collections.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Collections extends CalSvcDb implements CollectionsI {
  /** Constructor
   *
   * @param svci interface
   */
  Collections(final CalSvc svci) {
    super(svci);

    //userCollectionRootPath = "/" + getBasicSyspars().getUserCollectionRoot();
  }

  @Override
  public String getPublicCollectionsRootPath() {
    return publicCollectionRootPath;
  }

  @Override
  public BwCollection getPublicCollections() {
    return getCal().getCollectionIdx(getSvc().getIndexer(true, docTypeCollection),
                                     publicCollectionRootPath,
                                     PrivilegeDefs.privRead, true);
  }

  public BwCollection getPrimaryPublicPath() {
    try {
      return findPrimary(getPublicCollections());
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private BwCollection findPrimary(final BwCollection root) {
    if (root.getAlias()) {
      return null;
    }

    final BwCollection col = resolveAlias(root, true, false);
    if (col == null) {
      // No access or gone
      return null;
    }

    if (col.getCalType() == BwCollection.calTypeCalendarCollection) {
      if (col.getPrimaryCollection()) {
        return col;
      }

      return null;
    }

    if (root.getCalendarCollection()) {
      // Leaf but cannot add here
      return null;
    }

    for (final BwCollection ch: getChildren(root)) {
      final var res = findPrimary(ch);
      if (res != null) {
        return res;
      }
    }

    return null;
  }

  @Override
  public String getHomePath() {
    if (isGuest()) {
      return publicCollectionRootPath;
    }

    if (!isPublicAdmin()) {
      if (getSvc().getPars().getPublicSubmission()) {
        return Util.buildPath(colPathEndsWithSlash,
                              getSvc().getSystemProperties().getSubmissionRoot());
      }

      return getSvc().getPrincipalInfo().getCollectionHomePath();
    }

    if (!getSvc().getSystemProperties().getWorkflowEnabled()) {
      return publicCollectionRootPath;
    }

    final BwAuthUser au = getSvc().getUserAuth()
                                  .getUser(getPars().getAuthUser());

    // Do they have approver status?
    final boolean isApprover = isSuper() ||
            ((au != null) && au.isApproverUser());
    if (isApprover) {
      return publicCollectionRootPath;
    }

    // Otherwise we point them at the unapproved home.
    return Util.buildPath(colPathEndsWithSlash,
                          getSvc().getSystemProperties().getWorkflowRoot());
  }

  @Override
  public BwCollection getHome() {
    final var home = getHomePath();
    final var cal =
            getCal().getCollection(home,
                                   PrivilegeDefs.privRead, true);
    if (cal == null) {
      warn("No home directory: " + home);
    }

    return cal;
  }

  @Override
  public BwCollection getHome(final BwPrincipal principal,
                              final boolean freeBusy) {
    final int priv;
    if (freeBusy) {
      priv = PrivilegeDefs.privReadFreeBusy;
    } else {
      priv = PrivilegeDefs.privRead;
    }
    return getCal().getCollectionIdx(getSvc().getIndexer(false, docTypeCollection),
                                     getSvc().getPrincipalInfo().getCollectionHomePath(principal),
                                     priv, true);
  }

  @Override
  public BwCollection getHomeDb(final BwPrincipal principal,
                                final boolean freeBusy) {
    final int priv;
    if (freeBusy) {
      priv = PrivilegeDefs.privReadFreeBusy;
    } else {
      priv = PrivilegeDefs.privRead;
    }
    return getCal().getCollection(getSvc().getPrincipalInfo().getCollectionHomePath(principal),
                                  priv, true);
  }
  
  private final Map<String, List<BwCollection>> vpathCache = new HashMap<>();
  
  private long lastVpathFlush = System.currentTimeMillis();
  
  private long vpathCalls;

  private long vpathHits;
  
  private final long vpathFlushPeriod = 1000 * 60 * 10;
  
  private final int maxVpathCached = 250;

  @Override
  public Collection<BwCollection> decomposeVirtualPath(final String vpath) {
    vpathCalls++;
    if (debug() && ((vpathCalls % 250) == 0)) {
      debug("Vpath calls: " + vpathCalls);
      debug(" Vpath hits: " + vpathHits);
      debug(" Vpath size: " + vpathCache.size());
    }
    
    // In the cache?

    if ((System.currentTimeMillis() - lastVpathFlush) > vpathFlushPeriod) {
      vpathCache.clear();
      lastVpathFlush = System.currentTimeMillis();
    }
    
    if (vpathCache.size() > maxVpathCached) {
      info("Flushing vpath cache - reached max size of " + maxVpathCached);
      vpathCache.clear();
    }
    
    final BwPrincipal pr = getPrincipal();
    final String cacheKey;
    
    if (pr.getUnauthenticated()) {
      cacheKey = "*|" + vpath;
    } else {
      cacheKey = pr.getPrincipalRef() + "|" + vpath;
    }
    
    List<BwCollection> cols = vpathCache.get(cacheKey);
    
    if (cols != null) {
      vpathHits++;
      return cols;
    }

    cols = new ArrayList<>();

    /* See if the vpath is an actual path - generally the case for
     * personal calendar users.
     */

    BwCollection startCol = getIdx(vpath);

    if ((startCol != null) && !startCol.getAlias()) {
      cols.add(startCol);
      
      vpathCache.put(cacheKey, cols);

      return cols;
    }

    final String[] pathEls = normalizeUri(vpath).split("/");

    if (pathEls.length == 0) {
      vpathCache.put(cacheKey, cols);
      
      return cols;
    }

    /* First, keep adding elements until we get a BwCollection result.
     * This handles the user root not being accessible
     */

    startCol = null;
    String startPath = "";
    int pathi = 1;  // Element 0 is a zero length string

    while (pathi < pathEls.length) {
      startPath = Util.buildPath(colPathEndsWithSlash, startPath, "/",
                                 pathEls[pathi]);

      pathi++;

      try {
        startCol = getIdx(startPath);
      } catch (final BedeworkAccessException ignored) {
        startCol = null;
      }

      if (startCol != null) {
        // Found the start collection
        if (debug()) {
          debug("Start vpath collection:" + startCol.getPath());
        }
        break;
      }
    }

    if (startCol == null) {
      // Bad vpath
      return null;
    }

    BwCollection curCol = startCol;

    buildCollection:
    for (;;) {
      cols.add(curCol);

      if (debug()) {
        debug("      vpath collection:" + curCol.getPath());
      }

      // Follow the chain of references for curCol until we reach a non-alias
      if (curCol.getInternalAlias()) {
        final BwCollection nextCol = resolveAliasIdx(curCol, false, false);

        if (nextCol == null) {
          // Bad vpath
          curCol.setDisabled(true);
          curCol.setLastRefreshStatus("400");
          return null;
        }

        curCol = nextCol;

        continue buildCollection;
      }

      /* Not an alias - do we have any more path elements
       */
      if (pathi >= pathEls.length) {
        break buildCollection;
      }

      /* Not an alias and we have more path elements.
       * It should be a collection. Look for the next path
       * element as a child name
       */

      if (curCol.getCalType() != BwCollection.calTypeFolder) {
        // Bad vpath
        return null;
      }

      /*
      for (BwCollection col: getChildren(curCol)) {
        if (col.getName().equals(pathEls[pathi])) {
          // Found our child
          pathi++;
          curCol = col;
          continue buildCollection;
        }
      }
      */
      final BwCollection col = getIdx(Util.buildPath(colPathEndsWithSlash,
                                                     curCol.getPath(),
                                                     "/",
                                                     pathEls[pathi]));

      if (col == null) {
        /* Child not found - bad vpath */
        return null;
      }

      pathi++;
      curCol = col;
    }

    curCol.setAliasOrigin(startCol);

    vpathCache.put(cacheKey, cols);

    return cols;
  }

  @Override
  public Collection<BwCollection> getChildren(final BwCollection col) {
    if (col.getCalType() == BwCollection.calTypeAlias) {
      resolveAlias(col, true, false);
    }
    return getCal().getCollections(col.getAliasedEntity(), null);
  }

  @Override
  public Collection<BwCollection> getChildrenIdx(final BwCollection col) {
    if (col.getCalType() == BwCollection.calTypeAlias) {
      resolveAliasIdx(col, true, false);
    }
    return getCal().getCollections(col.getAliasedEntity(),
                                   getIndexer(docTypeCollection));
  }

  @Override
  public Set<BwCollection> getAddContentCollections(
          final boolean includeAliases,
          final boolean isApprover) {
    final Set<BwCollection> cals = new TreeSet<>();

    getAddContentCollectionCollections(includeAliases,
                                       isApprover, getHome(), cals);

    return cals;
  }

  @Override
  public boolean isEmpty(final BwCollection val) {
    return getSvc().getCal().isEmpty(val);
  }

  @Override
  public BwCollection get(String path) {
    if (path == null) {
      return null;
    }

    if (path.startsWith(CalFacadeDefs.bwUriPrefix)) {
      path = path.substring(CalFacadeDefs.bwUriPrefix.length());
    }

    if ((path.length() > 1) && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return getCal().getCollection(path, PrivilegeDefs.privAny, false);
  }

  @Override
  public BwCollection getIdx(String path) {
    if (path == null) {
      return null;
    }

    if ((path.startsWith(CalFacadeDefs.bwUriPrefix))) {
      path = path.substring(CalFacadeDefs.bwUriPrefix.length());
    }

    if ((path.length() > 1) && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return getCal().getCollectionIdx(getIndexer(docTypeCollection), path,
                                     PrivilegeDefs.privAny, 
                                     true);
  }

  @Override
  public BwCollection getSpecial(final int calType,
                                 final boolean create) {
    return getSpecial(null, calType, create);
  }

  @Override
  public BwCollection getSpecial(final String principal,
                                 final int calType,
                                 final boolean create) {
    final BwPrincipal<?> pr;

    if (principal == null) {
      pr = getPrincipal();
    } else {
      pr = getPrincipal(principal);
    }

    final GetSpecialCollectionResult gscr =
            getSpecialCollection(pr, calType, create);
    if (!gscr.noUserHome) {
      return gscr.cal;
    }

    getSvc().getUsersHandler().add(getPrincipal().getAccount());

    return getSpecialCollection(pr, calType, create).cal;
  }

  @Override
  public void setPreferred(final BwCollection val) {
    final BwPreferences prefs = getPrefs();
    prefs.setDefaultCalendarPath(val.getPath());
    getSvc().getPrefsHandler().update(prefs);
  }

  @Override
  public String getPreferred(final String entityType) {
    final int calType;

    switch (entityType) {
      case Component.VEVENT:
        final String path = getPrefs().getDefaultCalendarPath();

        if (path != null) {
          return path;
        }

        calType = BwCollection.calTypeCalendarCollection;
        break;
      case Component.VTODO:
        calType = BwCollection.calTypeTasks;
        break;
      case Component.VPOLL:
        calType = BwCollection.calTypePoll;
        break;
      default:
        return null;
    }

    final GetSpecialCollectionResult gscr =
            getSpecialCollection(getPrincipal(), calType, true);

    return gscr.cal.getPath();
  }

  @Override
  public BwCollection add(BwCollection val,
                          final String parentPath) {
    if (getSvc().getPrincipalInfo().getSubscriptionsOnly()) {
      // Only allow the creation of an alias
      if (val.getCalType() != BwCollection.calTypeAlias) {
        throw new BedeworkForbidden("User has read only access");
      }
    }
    updateOK(val);

    getSvc().setupSharableEntity(val,
                                 getPrincipal().getPrincipalRef());

    if (val.getPwNeedsEncrypt() || (val.getExternalSub() && val.getRemotePw() != null)) {
      encryptPw(val);
    }

    val = getCal().add(val, parentPath);
    ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(false,
                                                               val,
                                                               null,
                                                               null,
                                                               null);

    if (val.getExternalSub()) {
      final SynchI synch = getSvc().getSynch();

      if (!synch.subscribe(val)) {
        throw new BedeworkException(
                CalFacadeErrorCode.subscriptionFailed);
      }
    }

    return val;
  }

  @Override
  public void rename(final BwCollection val,
                     final String newName) {
    getSvc().getCal().renameCollection(val, newName);
  }

  @Override
  public void move(final BwCollection val,
                   final BwCollection newParent) {
    getSvc().getCal().moveCollection(val, newParent);
  }

  @Override
  public void update(final BwCollection val) {
    /* Ensure it's not in admin prefs if it's a folder.
     * User may have switched from collection to folder.
     */
    if (!val.getCalendarCollection() && isPublicAdmin()) {
      /* Remove from preferences */
      ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true,
                                                                 val,
                                                                 null,
                                                                 null,
                                                                 null);
    }

    if (val.getPwNeedsEncrypt()) {
      encryptPw(val);
    }

    getCal().updateCollection(val);
  }

  @Override
  public boolean delete(final BwCollection val,
                        final boolean emptyIt,
                        final boolean sendSchedulingMessage) {
    return delete(val, emptyIt, false, sendSchedulingMessage, true);
  }

  @Override
  public boolean isUserRoot(final BwCollection cal) {
    if ((cal == null) || (cal.getPath() == null)) {
      return false;
    }

    final String[] ss = cal.getPath().split("/");
    final int pathLength = ss.length - 1;  // First element is empty string

    return (pathLength == 2) &&
           (ss[1].equals(BasicSystemProperties.userCollectionRoot));
  }

  @Override
  public BwCollection resolveAlias(final BwCollection val,
                                   final boolean resolveSubAlias,
                                   final boolean freeBusy) {
    return getCal().resolveAlias(val, resolveSubAlias, freeBusy,
                                 null);
  }

  @Override
  public GetEntityResponse<CollectionAliases> getAliasInfo(final BwCollection val) {
    return getCal().getAliasInfo(val);
  }

  @Override
  public BwCollection resolveAliasIdx(final BwCollection val,
                                      final boolean resolveSubAlias,
                                      final boolean freeBusy) {
    return getCal().resolveAlias(val, resolveSubAlias, freeBusy,
                                 getIndexer(docTypeCollection));
  }

  /* The key will be the full href of the entity based on the
     real collection containing it.
   */
  private static final Map<String, AliasesInfo> aliasesInfoMap =
          new FlushMap<>(100, // size
                         60 * 1000 * 30,
                         1000);

  @Override
  public AliasesInfo getAliasesInfo(final String collectionHref,
                                    final String entityName) {
    AliasesInfo ai = aliasesInfoMap.get(AliasesInfo.makeKey(collectionHref,
                                                            entityName));

    if (ai != null) {
      return ai;
    }

    /* First get the info without the name
     */

    ai = getAliasesInfo(collectionHref);
    if (ai == null) {
      return null;
    }

    if (!ai.getShared()) {
      return ai;
    }

    /* Check again for the full path - it might have appeared 
     */
    final AliasesInfo mapAi = aliasesInfoMap.get(AliasesInfo.makeKey(collectionHref,
                                                                     entityName));

    if (mapAi != null) {
      return mapAi;
    }

    /* Now clone the structure we got and test the visibility of the entity
     */
    final Events eventsH = (Events)getSvc().getEventsHandler();

    final boolean isVisible = eventsH.isVisible(ai.getCollection(),
                                                  entityName);
    
    final AliasesInfo eai = ai.copyForEntity(entityName,
                                             isVisible);

    checkAliases(eai, entityName);

    return updateAliasInfoMap(eai);
  }

  @Override
  public SynchStatusResponse getSynchStatus(final BwCollection val) {
    return getSvc().getSynch().getSynchStatus(val);
  }

  @Override
  public CheckSubscriptionResult checkSubscription(final String path) {
    return getSvc().getSynch()
                   .checkSubscription(get(path));
  }

  @Override
  public Response<?> refreshSubscription(final BwCollection val) {
    return getSvc().getSynch().refresh(val);
  }

  @Override
  public String getSyncToken(final String path) {
    return getCal().getSyncToken(path);
  }

  @Override
  public boolean getSyncTokenIsValid(final String token,
                                     final String path) {
    if (token == null) {
      return false;
    }

    final long tokenDays;

    // Ensure it's a parsable date + time.
    try {
      final var pos = token.indexOf("-");
      if (pos < 0) {
        return false;
      }

      final var date = DateTimeUtil.fromISODateTimeUTC(
              token.substring(0, pos));

      if (date == null) {
        return false;
      }

      final var maxMins = getSvc().getSystemProperties().getSynchMaxMinutes();
      if (maxMins <= 0) {
        // Assume valid - we have no valid max set
        warn("No max value set for systemProperties.synchMaxMinutes");
        return true;
      }

      final long millis = new Date().getTime() - date.getTime();

      final var mins = millis / 1000 / 60;

      if (mins > maxMins) {
        return false;
      }

      return true;
    } catch (final Throwable ignored) {
      return false;
    }
  }

  @Override
  public Set<BwCategory> getCategorySet(final String href) {
    /* The set of categories referenced by the alias and its parents */

    final Collection<BwCollection> cols;

    cols = decomposeVirtualPath(href);

    if (Util.isEmpty(cols)) {
      if (debug()) {
        debug("No collections for vpath " + href);
      }
      return null;
    }

    /* For each entry in the returned list add any category to the set.
     *
     * For the last alias entry in the list work up to the root adding any
     * categories in.
     */

    BwCollection curCol = null;
    final Set<BwCategory> cats = new TreeSet<>();

    for (final BwCollection col : cols) {
      int numCats = 0;
      final Set<BwCategory> colCats = col.getCategories();
      if (!Util.isEmpty(colCats)) {
        for (final BwCategory colCat: colCats) {
          if (!colCat.unsaved()) {
            cats.add(colCat);
            continue;
          }

          final BwCategory theCat =
                  getSvc().getCategoriesHandler()
                          .getPersistent(colCat.getUid());

          if (theCat != null) {
            cats.add(theCat);
          }
        }
        numCats = colCats.size();
      }
      if (debug()) {
        debug("For col " + col.getPath() + " found " + numCats);
      }
      if (col.getAlias()) {
        curCol = col;
      }
    }

    while (curCol != null) {
      try {
        curCol = get(curCol.getColPath());
        if (curCol != null) {
          if (!Util.isEmpty(curCol.getCategories())) {
            cats.addAll(curCol.getCategories());
          }
        }
      } catch (final BedeworkAccessException ignored) {
        // We'll assume that's OK. We'll get that for /user at least.
        break;
      }
    }
    
    return cats;
  }

  public BwCollection getSpecial(final BwPrincipal owner,
                                 final int calType,
                                 final boolean create,
                                 final int access) {
    final GetSpecialCollectionResult gscr =
            getSpecialCollection(owner, calType, create);
    if (gscr.noUserHome) {
      getSvc().getUsersHandler().add(owner.getAccount());
    }

    return getSpecialCollection(owner, calType, create).cal;
  }

  /* ====================================================================
   *                   package private methods
   * ==================================================================== */
  GetSpecialCollectionResult getSpecialCollection(final BwPrincipal owner,
                                                  final int calType,
                                                  final boolean create) {
    return getCal().getSpecialCollection(null, owner, calType, create,
                                         PrivilegeDefs.privAny);
  }

  /**
   * @param val an href
   * @return list of any aliases for the current user pointing at the given href
   */
  List<BwCollection> findUserAlias(final String val) {
    return getCal().findAlias(val);
  }

  Set<BwCollection> getSynchCols(final String path,
                                 final String lastmod) {
    return getCal().getSynchCols(path, lastmod);
  }

  boolean delete(final BwCollection val,
                 final boolean emptyIt,
                 final boolean reallyDelete,
                 final boolean sendSchedulingMessage,
                 final boolean unsubscribe) {
    if (!emptyIt) {
      /* Only allow delete if not in use
       */
      if (!getCal().isEmpty(val)) {
        throw new BedeworkException(CalFacadeErrorCode.collectionNotEmpty);
      }
    }

    final BwPreferences prefs =
            getPrefs(getPrincipal(val.getOwnerHref()));
    if (val.getPath().equals(prefs.getDefaultCalendarPath())) {
      throw new BedeworkException(CalFacadeErrorCode.cannotDeleteDefaultCalendar);
    }

    /* Remove any sharing */
    if (val.getCanAlias()) {
      getSvc().getSharingHandler().delete(val, sendSchedulingMessage);
    }

    if (unsubscribe) {
      getSvc().getSharingHandler().unsubscribe(val);
    }

    getSvc().getSynch().unsubscribe(val, true);

    if (!val.getSpecial()) {
      /* Remove from preferences */
      ((Preferences)getSvc().getPrefsHandler()).updateAdminPrefs(true,
                                                                 val,
                                                                 null,
                                                                 null,
                                                                 null);
    }

    /* If it's an alias we just delete it - otherwise we might need to empty it.
     */
    if (!val.getInternalAlias() && emptyIt) {
      if (val.getCalendarCollection()) {
        final Events events = ((Events)getSvc().getEventsHandler());
        for (final EventInfo ei: events.getSynchEvents(val.getPath(),
                                                       null)) {
          final var delresp = events.delete(ei,
                                      false,
                                      sendSchedulingMessage,
                                      true);
          if (!delresp.isOk()) {
            throw new BedeworkException("Failed to delete " + ei.getHref() +
                    " response: " + delresp);
          }
        }
      }

      /* Remove resources */
      final ResourcesI resI = getSvc().getResourcesHandler();
      final Collection<BwResource> rs = resI.getAll(val.getPath());
      if (!Util.isEmpty(rs)) {
        for (final BwResource r: rs) {
          resI.delete(Util.buildPath(false, r.getColPath(), "/", r.getName()));
        }
      }

      for (final BwCollection cal: getChildren(val)) {
        if (!delete(cal, true, true, sendSchedulingMessage, true)) {
          // Somebody else at it
          getSvc().rollbackTransaction();
          throw new BedeworkException(CalFacadeErrorCode.collectionNotFound,
                                       cal.getPath());
        }
      }
    }

    if (val.getSpecial()) {
      // Pretend we did but don't
      return true;
    }

    val.getProperties().clear();

    /* Attempt to tombstone it
     */
    return getSvc().getCal().deleteCollection(val, reallyDelete);
  }

  /* ====================================================================
   *                   private methods
   * ==================================================================== */

  private void checkAliases(final AliasesInfo rootAi,
                            final String entityName) {
    final Events eventsH = (Events)getSvc().getEventsHandler();

    for (final AliasesInfo ai: rootAi.getAliases()) {
      final boolean isVisible = eventsH.isVisible(ai.getCollection(),
                                                  entityName);
      final AliasesInfo eai = ai.copyForEntity(entityName,
                                               isVisible);
      rootAi.addSharee(eai);
      checkAliases(eai, entityName);
    }
  }

  private AliasesInfo getAliasesInfo(final String collectionHref) {
    AliasesInfo ai = aliasesInfoMap.get(AliasesInfo.makeKey(collectionHref,
                                                            null));

    if (ai != null) {
      return ai;
    }

    final BwCollection col = getCal().getCollection(collectionHref,
                                                    PrivilegeDefs.privAny,
                                                    true);
    if (col == null) {
      return null;
    }

    ai = new AliasesInfo(getPrincipal().getPrincipalRef(),
                         col,
                         null);
    findAliases(col, ai);
    return updateAliasInfoMap(ai);
  }

  private void findAliases(final BwCollection col,
                           final AliasesInfo rootAi) {
    final String collectionHref = col.getPath();

    final boolean defaultEnabled =
            !Boolean.parseBoolean(
                    System.getProperty("org.bedework.nochangenote", "false")) &&
            getSvc().getAuthProperties()
                    .getDefaultChangesNotifications();

    if (notificationsEnabled(col, defaultEnabled)) {
      rootAi.setNotificationsEnabled(true);
    }
    
    /* Handle aliases that are not a result of collection sharing.  These could be public or private.
     */
    for (final BwCollection alias:
            ((Collections)getCols()).findUserAlias(collectionHref)) {
      final AliasesInfo ai = new AliasesInfo(getPrincipal().getPrincipalRef(),
                                             alias,
                                             null);

      rootAi.addSharee(ai);
      findAliases(alias, ai);
    }
    
    /* for each sharee in the list find user collection(s) pointing to this
     * collection and add the sharee if any are enabled for notifications.
     */

    final InviteType invite =
            getSvc().getSharingHandler().getInviteStatus(col);

    if (invite == null) {
      // No sharees
      return;
    }

    /* for sharees - it's the alias which points at this collection
     * which holds the status.
     */
    for (final UserType u: invite.getUsers()) {
      final BwPrincipal principal = caladdrToPrincipal(u.getHref());

      if (principal == null) {
        final AliasesInfo ai = new AliasesInfo(u.getHref(),
                                               col,
                                               null);

        ai.setExternalCua(true);
        rootAi.addSharee(ai);
        continue;
      }

      try {
        getSvc().pushPrincipal(principal);

        for (final BwCollection alias: ((Collections)getCols()).findUserAlias(collectionHref)) {
          if (!notificationsEnabled(alias, defaultEnabled)) {
            continue;
          }

          final AliasesInfo ai = new AliasesInfo(principal.getPrincipalRef(),
                                                 alias,
                                                 null);

          rootAi.addSharee(ai);
          findAliases(alias, ai);
        }
      } finally {
        getSvc().popPrincipal();
      }
    }
  }

  /* For private collections we'll use the AppleServerTags.notifyChanges
   * property to indicate if we should notify the sharee.
   *
   * For public collections we always notify
   */
  private boolean notificationsEnabled(final BwCollection col,
                                       final boolean defaultEnabled) {
    if (col.getPublick()) {
      return true;
    }

    final String enabledVal = col.getQproperty(AppleServerTags.notifyChanges);

    if (enabledVal == null) {
      return defaultEnabled;
    }

    return Boolean.parseBoolean(enabledVal);
  }

  private AliasesInfo updateAliasInfoMap(final AliasesInfo ai) {
    synchronized (aliasesInfoMap) {
      final String key = ai.makeKey();
      final AliasesInfo mapAi = aliasesInfoMap.get(key);

      if (mapAi != null) {
        // Somebody got there before us.
        return mapAi;
      }

      aliasesInfoMap.put(key, ai);

      return ai;
    }
  }

  /* Check to see if we need to remove entries from the alias info map
   */
  private void checkAliasInfo(final String val) {
    synchronized (aliasesInfoMap) {
      final List<AliasesInfo> removals = new ArrayList<>();

      for (final AliasesInfo ai: aliasesInfoMap.values()) {
        if (ai.referencesCollection(val)) {
          removals.add(ai);
        }
      }

      for (final AliasesInfo ai: removals) {
        aliasesInfoMap.remove(ai.makeKey());
      }
    }
  }

  private void getAddContentCollectionCollections(
          final boolean includeAliases,
          final boolean isApprover,
          final BwCollection root,
          final Set<BwCollection> cals) {
    if (!includeAliases && root.getAlias()) {
      return;
    }

    final BwCollection col = resolveAlias(root, true, false);
    if (col == null) {
      // No access or gone
      return;
    }

    if (col.getCalType() == BwCollection.calTypeCalendarCollection) {
      if (isPublicAdmin() && isApprover && !col.getPrimaryCollection()) {
        return;
      }

      /* We might want to add the busy time calendar here -
       * presumably we will want availability stored somewhere.
       * These might be implicit operations however.
       */
      final CurrentAccess ca = getSvc().checkAccess(col,
                                                    PrivilegeDefs.privWriteContent,
                                                    true);

      if (ca.getAccessAllowed()) {
        cals.add(root);  // Might be an alias, might not
      }
      return;
    }

    if (root.getCalendarCollection()) {
      // Leaf but cannot add here
      return;
    }

    for (final BwCollection ch: getChildren(root)) {
      getAddContentCollectionCollections(includeAliases,
                                         isApprover,
                                         ch, cals);
    }
  }

  private void encryptPw(final BwCollection val) {
    try {
      val.setRemotePw(getSvc().getEncrypter().encrypt(val.getRemotePw()));
    } catch (final Throwable t) {
      throw new BedeworkException(t);
    }
  }

  /* This provides some limits to shareable entity updates for the
   * admin users. It is applied in addition to the normal access checks
   * applied at the lower levels.
   */
  private void updateOK(final Object o) {
    if (isGuest()) {
      throw new BedeworkAccessException();
    }

    if (isSuper()) {
      // Always ok
      return;
    }

    if (!(o instanceof BwShareableDbentity)) {
      throw new BedeworkAccessException();
    }

    if (!isPublicAdmin()) {
      // Normal access checks apply
      return;
    }

    final var ent = (BwShareableDbentity<?>)o;

    if (getPars().getAdminCanEditAllPublicContacts() ||
        ent.getCreatorHref().equals(getPrincipal().getPrincipalRef())) {
      return;
    }

    throw new BedeworkAccessException();
  }

  private String normalizeUri(String uri) {
    /*Remove all "." and ".." components */
    try {
      uri = new URI(null, null, uri, null).toString();

      uri = new URI(URLEncoder.encode(uri, StandardCharsets.UTF_8))
              .normalize()
              .getPath();

      uri = Util.buildPath(colPathEndsWithSlash,
                           URLDecoder.decode(uri,
                                             StandardCharsets.UTF_8));

      if (debug()) {
        debug("Normalized uri=" + uri);
      }

      return uri;
    } catch (final Throwable t) {
      if (debug()) {
        error(t);
      }
      throw new RuntimeException("Bad uri: " + uri);
    }
  }
}
