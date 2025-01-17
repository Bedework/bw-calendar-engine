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
import org.bedework.calcorei.Calintf;
import org.bedework.calcorei.CoreCalendarsI.GetSpecialCalendarResult;
import org.bedework.caldav.util.sharing.InviteType;
import org.bedework.caldav.util.sharing.UserType;
import org.bedework.calfacade.AliasesInfo;
import org.bedework.calfacade.BwCalendar;
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
import org.bedework.calsvci.CalendarsI;
import org.bedework.calsvci.ResourcesI;
import org.bedework.calsvci.SynchI;
import org.bedework.util.caching.FlushMap;
import org.bedework.util.misc.Util;
import org.bedework.util.misc.response.GetEntityResponse;
import org.bedework.util.misc.response.Response;
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
import static org.bedework.calfacade.configs.BasicSystemProperties.publicCalendarRootPath;
import static org.bedework.calfacade.indexing.BwIndexer.docTypeCollection;

/** This acts as an interface to the database for calendars.
 *
 * @author Mike Douglass       douglm - rpi.edu
 */
class Calendars extends CalSvcDb implements CalendarsI {
  /** Constructor
   *
   * @param svci interface
   */
  Calendars(final CalSvc svci) {
    super(svci);

    //userCalendarRootPath = "/" + getBasicSyspars().getUserCalendarRoot();
  }

  @Override
  public String getPublicCalendarsRootPath() {
    return publicCalendarRootPath;
  }

  @Override
  public BwCalendar getPublicCalendars() {
    return getCal().getCollectionIdx(getSvc().getIndexer(true, docTypeCollection),
                                     publicCalendarRootPath,
                                     PrivilegeDefs.privRead, true);
  }

  public BwCalendar getPrimaryPublicPath() {
    try {
      return findPrimary(getPublicCalendars());
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  private BwCalendar findPrimary(final BwCalendar root) {
    if (root.getAlias()) {
      return null;
    }

    final BwCalendar col = resolveAlias(root, true, false);
    if (col == null) {
      // No access or gone
      return null;
    }

    if (col.getCalType() == BwCalendar.calTypeCalendarCollection) {
      if (col.getPrimaryCollection()) {
        return col;
      }

      return null;
    }

    if (root.getCalendarCollection()) {
      // Leaf but cannot add here
      return null;
    }

    for (final BwCalendar ch: getChildren(root)) {
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
      return publicCalendarRootPath;
    }

    if (!isPublicAdmin()) {
      if (getSvc().getPars().getPublicSubmission()) {
        return Util.buildPath(colPathEndsWithSlash,
                              getSvc().getSystemProperties().getSubmissionRoot());
      }

      return getSvc().getPrincipalInfo().getCalendarHomePath();
    }

    if (!getSvc().getSystemProperties().getWorkflowEnabled()) {
      return publicCalendarRootPath;
    }

    final BwAuthUser au = getSvc().getUserAuth()
                                  .getUser(getPars().getAuthUser());

    // Do they have approver status?
    final boolean isApprover = isSuper() ||
            ((au != null) && au.isApproverUser());
    if (isApprover) {
      return publicCalendarRootPath;
    }

    // Otherwise we point them at the unapproved home.
    return Util.buildPath(colPathEndsWithSlash,
                          getSvc().getSystemProperties().getWorkflowRoot());
  }

  @Override
  public BwCalendar getHome() {
    final var home = getHomePath();
    final var cal =
            getCal().getCalendar(home,
                                 PrivilegeDefs.privRead, true);
    if (cal == null) {
      warn("No home directory: " + home);
    }

    return cal;
  }

  @Override
  public BwCalendar getHome(final BwPrincipal principal,
                            final boolean freeBusy) {
    final int priv;
    if (freeBusy) {
      priv = PrivilegeDefs.privReadFreeBusy;
    } else {
      priv = PrivilegeDefs.privRead;
    }
    return getCal().getCollectionIdx(getSvc().getIndexer(false, docTypeCollection),
                                     getSvc().getPrincipalInfo().getCalendarHomePath(principal),
                                     priv, true);
  }

  @Override
  public BwCalendar getHomeDb(final BwPrincipal principal,
                              final boolean freeBusy) {
    final int priv;
    if (freeBusy) {
      priv = PrivilegeDefs.privReadFreeBusy;
    } else {
      priv = PrivilegeDefs.privRead;
    }
    return getCal().getCalendar(getSvc().getPrincipalInfo().getCalendarHomePath(principal),
                                priv, true);
  }
  
  private final Map<String, List<BwCalendar>> vpathCache = new HashMap<>();
  
  private long lastVpathFlush = System.currentTimeMillis();
  
  private long vpathCalls;

  private long vpathHits;
  
  private final long vpathFlushPeriod = 1000 * 60 * 10;
  
  private final int maxVpathCached = 250;

  @Override
  public Collection<BwCalendar> decomposeVirtualPath(final String vpath) {
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
    
    List<BwCalendar> cols = vpathCache.get(cacheKey);
    
    if (cols != null) {
      vpathHits++;
      return cols;
    }

    cols = new ArrayList<>();

    /* See if the vpath is an actual path - generally the case for
     * personal calendar users.
     */

    BwCalendar startCol = getIdx(vpath);

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

    /* First, keep adding elements until we get a BwCalendar result.
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

    BwCalendar curCol = startCol;

    buildCollection:
    for (;;) {
      cols.add(curCol);

      if (debug()) {
        debug("      vpath collection:" + curCol.getPath());
      }

      // Follow the chain of references for curCol until we reach a non-alias
      if (curCol.getInternalAlias()) {
        final BwCalendar nextCol = resolveAliasIdx(curCol, false, false);

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

      if (curCol.getCalType() != BwCalendar.calTypeFolder) {
        // Bad vpath
        return null;
      }

      /*
      for (BwCalendar col: getChildren(curCol)) {
        if (col.getName().equals(pathEls[pathi])) {
          // Found our child
          pathi++;
          curCol = col;
          continue buildCollection;
        }
      }
      */
      final BwCalendar col = getIdx(Util.buildPath(colPathEndsWithSlash,
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
  public Collection<BwCalendar> getChildren(final BwCalendar col) {
    if (col.getCalType() == BwCalendar.calTypeAlias) {
      resolveAlias(col, true, false);
    }
    return getCal().getCalendars(col.getAliasedEntity(), null);
  }

  @Override
  public Collection<BwCalendar> getChildrenIdx(final BwCalendar col) {
    if (col.getCalType() == BwCalendar.calTypeAlias) {
      resolveAliasIdx(col, true, false);
    }
    return getCal().getCalendars(col.getAliasedEntity(),
                                 getIndexer(docTypeCollection));
  }

  @Override
  public Set<BwCalendar> getAddContentCollections(
          final boolean includeAliases,
          final boolean isApprover) {
    final Set<BwCalendar> cals = new TreeSet<>();

    getAddContentCalendarCollections(includeAliases,
                                     isApprover, getHome(), cals);

    return cals;
  }

  @Override
  public boolean isEmpty(final BwCalendar val) {
    return getSvc().getCal().isEmpty(val);
  }

  @Override
  public BwCalendar get(String path) {
    if (path == null) {
      return null;
    }

    if ((path.length() > 1) &&
        (path.startsWith(CalFacadeDefs.bwUriPrefix))) {
      path = path.substring(CalFacadeDefs.bwUriPrefix.length());
    }

    if ((path.length() > 1) && path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return getCal().getCalendar(path, PrivilegeDefs.privAny, false);
  }

  @Override
  public BwCalendar getIdx(String path) {
    if (path == null) {
      return null;
    }

    if ((path.length() > 1) &&
            (path.startsWith(CalFacadeDefs.bwUriPrefix))) {
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
  public BwCalendar getSpecial(final int calType,
                               final boolean create) {
    return getSpecial(null, calType, create);
  }

  @Override
  public BwCalendar getSpecial(final String principal,
                               final int calType,
                               final boolean create) {
    final BwPrincipal pr;

    if (principal == null) {
      pr = getPrincipal();
    } else {
      pr = getPrincipal(principal);
    }

    final Calintf.GetSpecialCalendarResult gscr =
            getSpecialCalendar(pr, calType, create);
    if (!gscr.noUserHome) {
      return gscr.cal;
    }

    getSvc().getUsersHandler().add(getPrincipal().getAccount());

    return getSpecialCalendar(pr, calType, create).cal;
  }

  @Override
  public void setPreferred(final BwCalendar val) {
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

        calType = BwCalendar.calTypeCalendarCollection;
        break;
      case Component.VTODO:
        calType = BwCalendar.calTypeTasks;
        break;
      case Component.VPOLL:
        calType = BwCalendar.calTypePoll;
        break;
      default:
        return null;
    }

    final GetSpecialCalendarResult gscr =
            getSpecialCalendar(getPrincipal(), calType, true);

    return gscr.cal.getPath();
  }

  @Override
  public BwCalendar add(BwCalendar val,
                        final String parentPath) {
    if (getSvc().getPrincipalInfo().getSubscriptionsOnly()) {
      // Only allow the creation of an alias
      if (val.getCalType() != BwCalendar.calTypeAlias) {
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
  public void rename(final BwCalendar val,
                     final String newName) {
    getSvc().getCal().renameCalendar(val, newName);
  }

  @Override
  public void move(final BwCalendar val,
                   final BwCalendar newParent) {
    getSvc().getCal().moveCalendar(val, newParent);
  }

  @Override
  public void update(final BwCalendar val) {
    /* Ensure it's not in admin prefs if it's a folder.
     * User may have switched from calendar to folder.
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

    getCal().updateCalendar(val);
  }

  @Override
  public boolean delete(final BwCalendar val,
                        final boolean emptyIt,
                        final boolean sendSchedulingMessage) {
    return delete(val, emptyIt, false, sendSchedulingMessage, true);
  }

  @Override
  public boolean isUserRoot(final BwCalendar cal) {
    if ((cal == null) || (cal.getPath() == null)) {
      return false;
    }

    final String[] ss = cal.getPath().split("/");
    final int pathLength = ss.length - 1;  // First element is empty string

    return (pathLength == 2) &&
           (ss[1].equals(BasicSystemProperties.userCalendarRoot));
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy) {
    return getCal().resolveAlias(val, resolveSubAlias, freeBusy,
                                 null);
  }

  @Override
  public GetEntityResponse<CollectionAliases> getAliasInfo(final BwCalendar val) {
    return getCal().getAliasInfo(val);
  }

  @Override
  public BwCalendar resolveAliasIdx(final BwCalendar val,
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
  public SynchStatusResponse getSynchStatus(final String path) {
    return getSvc().getSynch().getSynchStatus(get(path));
  }

  @Override
  public CheckSubscriptionResult checkSubscription(final String path) {
    return getSvc().getSynch().checkSubscription(get(path));
  }

  @Override
  public Response refreshSubscription(final BwCalendar val) {
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

    final Collection<BwCalendar> cols;

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

    BwCalendar curCol = null;
    final Set<BwCategory> cats = new TreeSet<>();

    for (final BwCalendar col : cols) {
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

  public BwCalendar getSpecial(final BwPrincipal owner,
                               final int calType,
                               final boolean create,
                               final int access) {
    final Calintf.GetSpecialCalendarResult gscr =
            getSpecialCalendar(owner, calType, create);
    if (gscr.noUserHome) {
      getSvc().getUsersHandler().add(owner.getAccount());
    }

    return getSpecialCalendar(owner, calType, create).cal;
  }

  /* ====================================================================
   *                   package private methods
   * ==================================================================== */
  GetSpecialCalendarResult getSpecialCalendar(final BwPrincipal owner,
                                              final int calType,
                                              final boolean create) {
    return getCal().getSpecialCalendar(null, owner, calType, create,
                                       PrivilegeDefs.privAny);
  }

  /**
   * @param val an href
   * @return list of any aliases for the current user pointing at the given href
   */
  List<BwCalendar> findUserAlias(final String val) {
    return getCal().findAlias(val);
  }

  Set<BwCalendar> getSynchCols(final String path,
                               final String lastmod) {
    return getCal().getSynchCols(path, lastmod);
  }

  boolean delete(final BwCalendar val,
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

      for (final BwCalendar cal: getChildren(val)) {
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
    return getSvc().getCal().deleteCalendar(val, reallyDelete);
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

    final BwCalendar col = getCal().getCalendar(collectionHref,
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

  private void findAliases(final BwCalendar col,
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
    
    /* Handle aliases that are not a result of calendar sharing.  These could be public or private.
     */
    for (final BwCalendar alias:
            ((Calendars)getCols()).findUserAlias(collectionHref)) {
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

        for (final BwCalendar alias: ((Calendars)getCols()).findUserAlias(collectionHref)) {
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
  private boolean notificationsEnabled(final BwCalendar col,
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

  private void getAddContentCalendarCollections(
          final boolean includeAliases,
          final boolean isApprover,
          final BwCalendar root,
          final Set<BwCalendar> cals) {
    if (!includeAliases && root.getAlias()) {
      return;
    }

    final BwCalendar col = resolveAlias(root, true, false);
    if (col == null) {
      // No access or gone
      return;
    }

    if (col.getCalType() == BwCalendar.calTypeCalendarCollection) {
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

    for (final BwCalendar ch: getChildren(root)) {
      getAddContentCalendarCollections(includeAliases,
                                       isApprover,
                                       ch, cals);
    }
  }

  private void encryptPw(final BwCalendar val) {
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
