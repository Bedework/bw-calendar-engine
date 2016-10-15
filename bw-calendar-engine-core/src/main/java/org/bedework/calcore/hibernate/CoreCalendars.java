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
package org.bedework.calcore.hibernate;

import org.bedework.access.Access;
import org.bedework.access.Ace;
import org.bedework.access.AceWho;
import org.bedework.access.Acl.CurrentAccess;
import org.bedework.access.PrivilegeDefs;
import org.bedework.calcore.AccessUtil.CollectionGetter;
import org.bedework.calcorei.CoreCalendarsI;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.BwStats;
import org.bedework.calfacade.BwStats.CacheStats;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwLastMod;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeBadRequest;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.exc.CalFacadeInvalidSynctoken;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefsCalendar;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.NotificationException;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.misc.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Class to encapsulate most of what we do with collections
 *
 * @author douglm
 *
 */
public class CoreCalendars extends CalintfHelperHib
         implements CollectionGetter, CoreCalendarsI {
  private String userCalendarRootPath;
  //private String groupCalendarRootPath;

  /**
   * @author douglm
   *
   */
  private static class CollectionCache implements Serializable {
    private static class CacheInfo {
      CalendarWrapper col;
      String token;
      boolean checked;

      CacheInfo(final CalendarWrapper col) {
        setCol(col);
      }

      void setCol(final CalendarWrapper col) {
        this.col = col;
        token = col.getLastmod().getTagValue();
        checked = true;
      }
    }

    private Map<String, CacheInfo> cache = new HashMap<String, CacheInfo>();

    private CoreCalendars cols;

    //BwStats stats;
    CacheStats cs;

    CollectionCache(final CoreCalendars cols,
                    final BwStats stats) {
      //this.stats = stats;
      this.cols = cols;
      cs = stats.getCollectionCacheStats();
    }

    void put(final CalendarWrapper col) {
      CacheInfo ci = cache.get(col.getPath());

      if (ci != null) {
        // A refetch
        ci.setCol(col);

        cs.incRefetches();
      } else {
        ci = new CacheInfo(col);
        cache.put(col.getPath(), ci);

        cs.incCached();
      }
    }

    void remove(final String path) {
      cache.remove(path);
    }

    CalendarWrapper get(final String path) throws CalFacadeException {
      CacheInfo ci = cache.get(path);

      if (ci == null) {
        cs.incMisses();
        return null;
      }

      if (ci.checked) {
        cs.incHits();
        return ci.col;
      }

      CollectionSynchInfo csi = cols.getSynchInfo(path, ci.token);

      if (csi == null) {
        // Collection deleted?
        cs.incMisses();
        return null;
      }

      if (!csi.changed) {
        ci.checked = true;

        cs.incHits();
        return ci.col;
      }

      return null;  // force refetch
    }

    CalendarWrapper get(final String path, final String token) throws CalFacadeException {
      CacheInfo ci = cache.get(path);

      if (ci == null) {
        cs.incMisses();
        return null;
      }

      if (!ci.token.equals(token)) {
        return null;
      }

      cs.incHits();
      return ci.col;
    }

    void flushAccess(final CoreCalendars cc) throws CalFacadeException {
      for (CacheInfo ci: cache.values()) {

        Set<Integer> accesses = ci.col.evaluatedAccesses();
        if (accesses == null) {
          continue;
        }

        Set<Integer> evaluated = new TreeSet<Integer>(ci.col.evaluatedAccesses());
        ci.col.clearCurrentAccess();

        for (Integer acc: evaluated) {
          cc.checkAccess(ci.col, acc, true);
        }
      }

//      cs.incAccessFlushes();
    }

    void flush() {
      for (CacheInfo ci: cache.values()) {
        ci.checked = false;
      }

      cs.incFlushes();
    }

    void clear() {
      cache.clear();

      cs.incFlushes();
    }
  }

  private CollectionCache colCache;

  /** Constructor
   *
   * @param chcb
   * @param cb
   * @param ac
   * @param currentMode
   * @param sessionless
   * @throws CalFacadeException
   */
  public CoreCalendars(final CalintfHelperHibCb chcb, 
                       final Callback cb,
                       final AccessChecker ac,
                       final int currentMode,
                       final boolean sessionless)
                  throws CalFacadeException {
    super(chcb);
    super.init(cb, ac, currentMode, sessionless);

    userCalendarRootPath = 
            Util.buildPath(colPathEndsWithSlash, 
                           "/", getSyspars()
                                   .getUserCalendarRoot());
    //groupCalendarRootPath = userCalendarRootPath + "/" + "groups";

    colCache = new CollectionCache(this, cb.getStats());
  }

  @Override
  public void startTransaction() throws CalFacadeException {
    colCache.flush();  // Just in case
  }

  @Override
  public void endTransaction() throws CalFacadeException {
    colCache.flush();
  }

  /* ====================================================================
   *                   CalendarsI methods
   * ==================================================================== */

  @Override
  public void principalChanged() throws CalFacadeException {
    colCache.clear();
  }

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("select lm.timestamp, lm.sequence from ");
    sb.append(BwCollectionLastmod.class.getName());
    sb.append(" lm where path=:path");
    sess.createQuery(sb.toString());

    sess.setString("path", path);
    sess.cacheableQuery();

    Object[] lmfields = (Object[])sess.getUnique();

    if (lmfields == null) {
      return null;
    }

    CollectionSynchInfo csi = new CollectionSynchInfo();

    csi.token = BwLastMod.getTagValue((String)lmfields[0], (Integer)lmfields[1]);

    csi.changed = (token == null) || (!csi.token.equals(token));

    return csi;
  }

  @Override
  public Collection<BwCalendar> getCalendars(final BwCalendar col,
                                             final BwIndexer indexer) throws CalFacadeException {
    if (indexer == null) {
      final Collection<BwCalendar> ch = getChildren(col);

      return checkAccess(ch, privAny, true);
    } else {
      return indexer.fetchChildren(col.getPath());
    }
  }

  @Override
  public BwCalendar resolveAlias(final BwCalendar val,
                                 final boolean resolveSubAlias,
                                 final boolean freeBusy,
                                 final BwIndexer indexer) throws CalFacadeException {
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
  public List<BwCalendar> findAlias(final String val) throws CalFacadeException {
    final HibSession sess = getSess();

    final StringBuilder sb = new StringBuilder();

    sb.append("from org.bedework.calfacade.BwCalendar as cal");
    sb.append(" where cal.calType=:caltype");
    sb.append(" and ownerHref=:owner");
    sb.append(" and aliasUri=:alias");
    sb.append(" and (cal.filterExpr = null or cal.filterExpr <> :tsfilter)");

    sess.createQuery(sb.toString());

    sess.setString("owner", currentPrincipal());
    sess.setString("alias", "bwcal://" + val);
    sess.setInt("caltype", BwCalendar.calTypeAlias);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    sess.cacheableQuery();

    List<BwCalendar> aliases = sess.getList();

    final List<BwCalendar> waliases = new ArrayList<>();

    if (Util.isEmpty(aliases)) {
      return waliases;
    }

    for (final BwCalendar alias: aliases) {
      waliases.add(wrap(alias));
    }

    return waliases;
  }

  private static final String getCalendarByPathQuery =
         "from " + BwCalendar.class.getName() + " as cal " +
           "where cal.path=:path and " +
           "(cal.filterExpr = null or cal.filterExpr <> '--TOMBSTONED--')";

  @Override
  public BwCalendar getCollection(final String path) throws CalFacadeException {
    if (path == null) {
      return null;
    }

    BwCalendar col = colCache.get(path);

    if (col != null) {
      return col;
    }

    final HibSession sess = getSess();

    sess.createQuery(getCalendarByPathQuery);
    sess.setString("path", path);
    sess.cacheableQuery();

    col = (BwCalendar)sess.getUnique();

    if (col == null) {
      if (path.equals("/")) {
        // Fake a root collection
        col = new BwCalendar();
        col.setPath("/");

        // Use this for owner/creator
        final BwCalendar userRoot = getCollection(userCalendarRootPath);

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
        final GetSpecialCalendarResult gscr = getIfSpecial(getPrincipal(), path);

        if (gscr == null) {
          return null;
        }

        col = gscr.cal;
      } else {
        return null;
      }
    }

    final CalendarWrapper wcol = wrap(col);
    if (wcol != null) {
      colCache.put(wcol);
    }

    return wcol;
  }

  @Override
  public BwCalendar getCalendar(final String path,
                                final int desiredAccess,
                                final boolean alwaysReturnResult) throws CalFacadeException {
    BwCalendar col = getCollection(path);

    col = checkAccess((CalendarWrapper)col, desiredAccess, alwaysReturnResult);

    return col;
  }

  @Override
  public BwCalendar getCollectionIdx(final BwIndexer indexer,
                                     final String path,
                                     final int desiredAccess,
                                     final boolean alwaysReturnResult) throws CalFacadeException {
    final BwCalendar col = colCache.get(path);

    if (col != null) {
      return col;
    }

    return indexer.fetchCol(path,
                            PropertyIndex.PropertyInfoIndex.HREF);
  }

  private GetSpecialCalendarResult getIfSpecial(final BwPrincipal owner,
                                                final String path) throws CalFacadeException {
    final String pathTo = cb.getPrincipalInfo().getCalendarHomePath(owner);

    final BasicSystemProperties sys = getSyspars();

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       sys.getUserInbox()).equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypeInbox,
                                true, false, PrivilegeDefs.privAny);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       ".pendingInbox").equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypePendingInbox,
                                true, false, PrivilegeDefs.privAny);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       sys.getUserOutbox()).equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypeOutbox,
                                true, false, PrivilegeDefs.privAny);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       sys.getDefaultNotificationsName()).equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypeNotifications,
                                true, false, PrivilegeDefs.privAny);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       sys.getDefaultReferencesName()).equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypeEventList,
                                true, false, PrivilegeDefs.privAny);
    }

    if (Util.buildPath(colPathEndsWithSlash, pathTo, "/", 
                       sys.getUserDefaultPollsCalendar()).equals(path)) {
      return getSpecialCalendar(owner, BwCalendar.calTypePoll,
                                true, false, PrivilegeDefs.privAny);
    }

    return null;
  }

  @Override
  public GetSpecialCalendarResult getSpecialCalendar(final BwPrincipal owner,
                                                     final int calType,
                                                     final boolean create,
                                                     final int access) throws CalFacadeException {
    return getSpecialCalendar(owner, calType, create, true, access);
  }

  private GetSpecialCalendarResult getSpecialCalendar(final BwPrincipal owner,
                                                      final int calType,
                                                      final boolean create,
                                                      final boolean tryFetch,
                                                      final int access) throws CalFacadeException {
    final String name;
    final BasicSystemProperties sys = getSyspars();
    int ctype = calType;

    if (calType == BwCalendar.calTypeInbox) {
      name = sys.getUserInbox();
    } else if (calType == BwCalendar.calTypePendingInbox) {
      name = ".pendingInbox";// sys.getUserInbox();
    } else if (calType == BwCalendar.calTypeOutbox) {
      name = sys.getUserOutbox();
    } else if (calType == BwCalendar.calTypeNotifications) {
      name = sys.getDefaultNotificationsName();
    } else if (calType == BwCalendar.calTypeEventList) {
      name = sys.getDefaultReferencesName();
    } else if (calType == BwCalendar.calTypePoll) {
      name = sys.getUserDefaultPollsCalendar();
    } else if (calType == BwCalendar.calTypeAttachments) {
      name = sys.getDefaultAttachmentsName();
    } else if (calType == BwCalendar.calTypeCalendarCollection) {
      name = sys.getUserDefaultCalendar();
    } else if (calType == BwCalendar.calTypeTasks) {
      name = sys.getUserDefaultTasksCalendar();
      ctype = BwCalendar.calTypeCalendarCollection;
    } else {
      // Not supported
      return null;
    }

    final List<String> entityTypes = BwCalendar.entityTypes.get(calType);

    final String pathTo = cb.getPrincipalInfo().getCalendarHomePath(owner);

    final GetSpecialCalendarResult gscr = new GetSpecialCalendarResult();

    final BwCalendar userHome = getCalendar(pathTo, access, false);
    if (userHome == null) {
      gscr.noUserHome = true;
      return gscr;
    }

    if (tryFetch){
      gscr.cal = getCalendar(Util.buildPath(colPathEndsWithSlash, 
                                            pathTo, "/", name),
                             access, false);

      if ((gscr.cal != null) || !create) {
        return gscr;
      }
    }

    /*
    BwCalendar parent = getCalendar(pathTo, privRead);

    if (parent == null) {
      throw new CalFacadeException("org.bedework.calcore.calendars.unabletocreate");
    }
    */

    gscr.cal = new BwCalendar();
    gscr.cal.setName(name);
    gscr.cal.setCreatorHref(owner.getPrincipalRef());
    gscr.cal.setOwnerHref(owner.getPrincipalRef());
    gscr.cal.setCalType(ctype);

    if (entityTypes != null) {
      gscr.cal.setSupportedComponents(entityTypes);
    }

    /* I think we're allowing privNone here because we don't mind if the
     * calendar gets created even if the caller has no access.
     */
    gscr.cal = add(gscr.cal, pathTo, true, privNone);
    gscr.created = true;

    return gscr;
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#add(org.bedework.calfacade.BwCalendar, java.lang.String)
   */
  @Override
  public BwCalendar add(final BwCalendar val,
                        final String parentPath) throws CalFacadeException {
    return add(val, parentPath, false, privBind);
  }

  @Override
  public void renameCalendar(BwCalendar val,
                             final String newName) throws CalFacadeException {
    colCache.flush();

    /* update will check access
     */

    BwCalendar parent = getCollection(val.getColPath());

    /* Ensure the name isn't reserved and the path is unique */
    checkNewCalendarName(newName, false, parent);

    val = unwrap(val);

    val.setName(newName);
    val.updateLastmod(getCurrentTimestamp());

    /* This triggers off a cascade of updates down the tree as we are storing the
     * path in the calendar objects. This may be preferable to calculating the
     * path at every access
     */
    updatePaths(val, parent);

    /* Remove any tombstoned collection with the same name */
    removeTombstonedVersion(val);

    // Flush it again
    colCache.flush();
  }

  @Override
  public void moveCalendar(BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    colCache.flush();

    /* check access - privbind on new parent privunbind on val?
     */
    ac.checkAccess(val, privUnbind, false);
    ac.checkAccess(newParent, privBind, false);

    if (newParent.getCalType() != BwCalendar.calTypeFolder) {
      throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
    }

    val = unwrap(val);

    val.setColPath(newParent.getPath());
    val.updateLastmod(getCurrentTimestamp());

    final BwCalendar tombstoned = val.makeTombstoneCopy();

    tombstoned.tombstone();
    getSess().save(tombstoned);

    /* This triggers off a cascade of updates down the tree as we are storing the
     * path in the calendar objects. This may be preferable to calculating the
     * path at every access
     */
    updatePaths(val, newParent);

    /* Remove any tombstoned collection with the same name */
    removeTombstonedVersion(val);

    // Flush it again
    colCache.flush();
  }

  @Override
  public void touchCalendar(final String path) throws CalFacadeException {
    final BwCalendar col = getCollection(path);
    if (col == null) {
      return;
    }

    touchCalendar(col);
  }

  @Override
  public void touchCalendar(final BwCalendar col) throws CalFacadeException {
    // CALWRAPPER - if we're not cloning can we avoid this?
    //val = (BwCalendar)getSess().merge(val);

    //val = (BwCalendar)getSess().merge(val);

    BwLastMod lm = col.getLastmod();
    lm.updateLastmod(getCurrentTimestamp());

    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("update ");
    sb.append(BwCollectionLastmod.class.getName());
    sb.append(" set timestamp=:timestamp, sequence=:sequence where path=:path");
    sess.createQuery(sb.toString());

    sess.setString("timestamp", lm.getTimestamp());
    sess.setInt("sequence", lm.getSequence());
    sess.setString("path", col.getPath());

    sess.executeUpdate();
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#updateCalendar(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public void updateCalendar(final BwCalendar val) throws CalFacadeException {
    ac.checkAccess(val, privWriteProperties, false);

    // CALWRAPPER - did I need this?
    //val = (BwCalendar)getSess().merge(val);

    //val = (BwCalendar)getSess().merge(val);
    //val.updateLastmod(getCurrentTimestamp());
    getSess().update(unwrap(val));
    touchCalendar(val.getPath());

    notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

    colCache.put((CalendarWrapper)val);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CalendarsI#changeAccess(org.bedework.calfacade.BwCalendar, java.util.Collection)
   */
  @Override
  public void changeAccess(final BwCalendar cal,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    HibSession sess = getSess();

    try {
      ac.getAccessUtil().changeAccess(cal, aces, replaceAll);

      // Clear the cache - inheritance makes it difficult to be sure of the effects.
      colCache.clear();
    } catch (CalFacadeException cfe) {
      sess.rollback();
      throw cfe;
    }

    sess.saveOrUpdate(unwrap(cal));

    ((CalendarWrapper)cal).clearCurrentAccess(); // force recheck
    colCache.put((CalendarWrapper)cal);

    notify(SysEvent.SysCode.COLLECTION_UPDATED, cal);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) throws CalFacadeException {
    HibSession sess = getSess();

    ac.getAccessUtil().defaultAccess(cal, who);
    sess.saveOrUpdate(unwrap(cal));

    colCache.flush();

    notify(SysEvent.SysCode.COLLECTION_UPDATED, cal);
  }

  private static final String removeCalendarPrefForAllQuery =
      "delete from " + BwAuthUserPrefsCalendar.class.getName() +
         " where calendarid=:id";

  @Override
  public boolean deleteCalendar(BwCalendar val,
                                final boolean reallyDelete) throws CalFacadeException {
    colCache.flush();

    final HibSession sess = getSess();

    ac.checkAccess(val, privUnbind, false);

    final String parentPath = val.getColPath();
    if (parentPath == null) {
      throw new CalFacadeException(CalFacadeException.cannotDeleteCalendarRoot);
    }

    /* Ensure the parent exists and we have writeContent on the parent.
     */
    final BwCalendar parent = getCalendar(parentPath, privWriteContent, false);
    if (parent == null) {
      throw new CalFacadeException(CalFacadeException.collectionNotFound);
    }

    val = getCalendar(val.getPath(), privUnbind, false);
    if (val == null) {
      throw new CalFacadeException(CalFacadeException.collectionNotFound);
    }

    if (!isEmpty(val)) {
      throw new CalFacadeException(CalFacadeException.collectionNotEmpty);
    }

    /* See if this is a no-op after all. We do this now to ensure the caller
     * really does have access
     */
    if (!reallyDelete && val.getTombstoned()) {
      // Nothing to do
      return true;
    }

    /* Ensure it's not in any (auth)user preferences */

    sess.createQuery(removeCalendarPrefForAllQuery);
    sess.setInt("id", val.getId());

    sess.executeUpdate();

    final String path = val.getPath();
    final BwCalendar unwrapped = unwrap(val);

    /* Ensure no tombstoned events or childen */
    removeTombstoned(val.getPath());

    if (reallyDelete) {
      sess.delete(unwrapped);
    } else {
      tombstoneEntity(unwrapped);
      unwrapped.tombstone();
      sess.update(unwrapped);
      touchCalendar(unwrapped);
    }

    colCache.remove(path);
    touchCalendar(parent);

    notify(SysEvent.SysCode.COLLECTION_DELETED, val);
    getIndexer(val).unindexEntity(path);

    return true;
  }

  private static final String countCalendarEventRefsQuery =
    "select count(*) from " + BwEventObj.class.getName() + " as ev " +
      "where ev.colPath = :colPath and ev.tombstoned=false";

  private static final String countCalendarChildrenQuery =
      "select count(*) from " + BwCalendar.class.getName() + " as cal " +
        "where cal.colPath = :colPath and " +
        "(cal.filterExpr = null or cal.filterExpr <> '--TOMBSTONED--')";

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#isEmpty(org.bedework.calfacade.BwCalendar)
   */
  @Override
  public boolean isEmpty(final BwCalendar val) throws CalFacadeException {
    HibSession sess = getSess();

    sess.createQuery(countCalendarEventRefsQuery);
    sess.setString("colPath", val.getPath());

    Long res = (Long)sess.getUnique();

    if (debug) {
      trace(" ----------- count = " + res);
    }

    if ((res != null) && (res.intValue() > 0)) {
      return false;
    }

    sess.createQuery(countCalendarChildrenQuery);
    sess.setString("colPath", val.getPath());

    res = (Long)sess.getUnique();

    if (debug) {
      trace(" ----------- count children = " + res);
    }

    return (res == null) || (res.intValue() == 0);
  }

  /* (non-Javadoc)
   * @see org.bedework.calcorei.CoreCalendarsI#addNewCalendars(org.bedework.calfacade.BwPrincipal)
   */
  @Override
  public void addNewCalendars(final BwPrincipal user) throws CalFacadeException {
    HibSession sess = getSess();

    /* Add a user collection to the userCalendarRoot and then a default
       calendar collection. */

    sess.createQuery(getCalendarByPathQuery);

    String path =  userCalendarRootPath;
    sess.setString("path", path);

    BwCalendar userrootcal = (BwCalendar)sess.getUnique();

    if (userrootcal == null) {
      throw new CalFacadeException("No user root at " + path);
    }

    BwCalendar parentCal = userrootcal;
    BwCalendar usercal = null;

    /* We may have a principal e.g. /principals/resources/vcc311
     * All except the last may exist already.
     */
    String[] upath = user.getAccountSplit();

    for (int i = 0; i < upath.length; i++) {
      String pathSeg = upath[i];

      if ((pathSeg == null) || (pathSeg.length() == 0)) {
        // Leading or double slash - skip it
        continue;
      }

      path = Util.buildPath(colPathEndsWithSlash, path, "/", pathSeg);

      sess.createQuery(getCalendarByPathQuery);

      sess.setString("path", path);

      usercal = (BwCalendar)sess.getUnique();
      if (i == (upath.length - 1)) {
        if (usercal != null) {
          throw new CalFacadeException("User calendar already exists at " + path);
        }

        /* Create a folder for the user */
        usercal = new BwCalendar();
        usercal.setName(pathSeg);
        usercal.setCreatorHref(user.getPrincipalRef());
        usercal.setOwnerHref(user.getPrincipalRef());
        usercal.setPublick(false);
        usercal.setPath(path);
        usercal.setColPath(parentCal.getPath());

        sess.save(usercal);
      } else if (usercal == null) {
        /* Create a new system owned folder for part of the principal
         * hierarchy
         */
        usercal = new BwCalendar();
        usercal.setName(pathSeg);
        usercal.setCreatorHref(userrootcal.getCreatorHref());
        usercal.setOwnerHref(userrootcal.getOwnerHref());
        usercal.setPublick(false);
        usercal.setPath(path);
        usercal.setColPath(parentCal.getPath());

        sess.save(usercal);
      }

      parentCal = usercal;
    }

    /* Create a default calendar */
    BwCalendar cal = new BwCalendar();
    cal.setName(getSyspars().getUserDefaultCalendar());
    cal.setCreatorHref(user.getPrincipalRef());
    cal.setOwnerHref(user.getPrincipalRef());
    cal.setPublick(false);
    cal.setPath(Util.buildPath(colPathEndsWithSlash, path, "/", cal.getName()));
    cal.setColPath(usercal.getPath());
    cal.setCalType(BwCalendar.calTypeCalendarCollection);
    cal.setAffectsFreeBusy(true);

    sess.save(cal);

    sess.update(user);
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String token) throws CalFacadeException {
    final HibSession sess = getSess();

    final StringBuilder sb = new StringBuilder();

    if (path == null) {
      sess.rollback();
      throw new CalFacadeBadRequest("Missing path");
    }

    if ((token != null) && (token.length() < 18)) {
      sess.rollback();
      throw new CalFacadeInvalidSynctoken(token);
    }

    sb.append("from ");
    sb.append(BwCalendar.class.getName());
    sb.append(" col ");
    sb.append("where col.colPath=:path ");

    if (token != null) {
      /* We want any undeleted alias or external subscription or 
         any collection with a later change token.
       */
      sb.append(" and ((col.calType=7 or col.calType=8) or " +
                        "(col.lastmod.timestamp>:lastmod" +
                        "   or (col.lastmod.timestamp=:lastmod and " +
                        "  col.lastmod.sequence>:seq)))");
    } else {
      // No deleted collections for null sync-token
      sb.append("and (col.filterExpr is null or col.filterExpr <> :tsfilter)");
    }

    sess.createQuery(sb.toString());

    sess.setString("path", fixPath(path));

    if (token != null) {
      sess.setString("lastmod", token.substring(0, 16));
      sess.setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsfilter", BwCalendar.tombstonedFilter);
    }

    sess.cacheableQuery();

    @SuppressWarnings("unchecked")
    List<BwCalendar> cols = sess.getList();

    Set<BwCalendar> res = new TreeSet<BwCalendar>();

    for (BwCalendar col: cols) {
      BwCalendar wcol = wrap(col);
      CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      res.add(wcol);
    }

    return res;
  }

  @Override
  public boolean testSynchCol(final BwCalendar col,
                              final String token)
          throws CalFacadeException {
    if (token == null) {
      return true;
    }

    final String lastmod = token.substring(0, 16);
    final int seq = Integer.parseInt(token.substring(17), 16);

    final BwCollectionLastmod cl = col.getLastmod();
    final int cmp = cl.getTimestamp().compareTo(lastmod);
    if (cmp > 0) {
      return true;
    }
    
    if (cmp < 0) {
      return false;
    }
    
    return cl.getSequence() > seq;
  }

  @Override
  public String getSyncToken(final String path) throws CalFacadeException {
    BwCalendar thisCol = getCalendar(path, privAny, false);

    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwCalendar.class.getName());
    sb.append(" col ");
    sb.append("where col.path like :path ");

    sess.createQuery(sb.toString());

    sess.setString("path", path + "%");

    @SuppressWarnings("unchecked")
    List<BwCalendar> cols = sess.getList();

    String token = thisCol.getLastmod().getTagValue();

    for (BwCalendar col: cols) {
      BwCalendar wcol = wrap(col);
      CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
      if (!ca.getAccessAllowed()) {
        continue;
      }

      String t = col.getLastmod().getTagValue();

      if (t.compareTo(token) > 0) {
        token = t;
      }
    }

    return token;
  }

  /* ====================================================================
   *                  Admin support
   * ==================================================================== */

  private final static String getChildCollectionsQuery =
          "select col.path from " +
                  BwCalendar.class.getName() +
                  " col where " +
                  "(col.filterExpr is null or col.filterExpr <> :tsfilter) and " +
                  "col.colPath";
          
  @Override
  @SuppressWarnings("unchecked")
  public Collection<String> getChildCollections(final String parentPath,
                                        final int start,
                                        final int count) throws CalFacadeException {
    final HibSession sess = getSess();

    if (parentPath == null) {
      sess.createQuery(getChildCollectionsQuery + " is null");
    } else {
      sess.createQuery(getChildCollectionsQuery + "=:colPath");
      sess.setString("colPath", parentPath);
    }

    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    sess.setFirstResult(start);
    sess.setMaxResults(count);

    final List res = sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  /*
    indexer != null => Use ES for the searches
   */
  private BwCalendar resolveAlias(final BwCalendar val,
                                  final boolean resolveSubAlias,
                                  final boolean freeBusy,
                                  final ArrayList<String> pathElements,
                                  final BwIndexer indexer) throws CalFacadeException {
    if ((val == null) || !val.getInternalAlias()) {
      return val;
    }

    final BwCalendar c = val.getAliasTarget();
    if (c != null) {
      if (!resolveSubAlias) {
        return c;
      }

      return resolveAlias(c, true, freeBusy, pathElements, indexer);
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

    //if (debug) {
    //  trace("Search for calendar \"" + path + "\"");
    //}

    BwCalendar col;

    try {
      if (indexer != null) {
        col = getCollectionIdx(indexer, path, desiredAccess, false);
      } else {
        col = getCalendar(path, desiredAccess, false);
      }
    } catch (final CalFacadeAccessException cfae) {
      col = null;
    }

    if (col == null) {
      /* Assume deleted - flag in the subscription if it's ours or a temp.
       */
      if ((val.getId() == CalFacadeDefs.unsavedItemKey) ||
          val.getOwnerHref().equals(getPrincipal().getPrincipalRef())) {
        disableAlias(val);
      }
    } else {
      val.setAliasTarget(col);
    }

    if (!resolveSubAlias) {
      return col;
    }

    return resolveAlias(col, true, freeBusy, pathElements, indexer);
  }

  private void disableAlias(final BwCalendar val) throws CalFacadeException {
    val.setDisabled(true);
    if (val.getId() != CalFacadeDefs.unsavedItemKey) {
      // Save the state
      val.updateLastmod(getCurrentTimestamp());
      getSess().update(unwrap(val));
      //touchCalendar(val.getPath());

      notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

      colCache.put((CalendarWrapper)val);
    }
  }

  private void removeTombstoned(final String path) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("delete from ");
    sb.append(BwEventObj.class.getName());
    sb.append(" ev where ev.tombstoned = true and ");

    sb.append("ev.colPath = :path");

    sess.createQuery(sb.toString());

    sess.setString("path", path);

    sess.executeUpdate();

    sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwCalendar.class.getName());
    sb.append(" col where col.colPath = :path and ");

    // XXX tombstone-schema
    sb.append("col.filterExpr = :tsfilter");

    sess.createQuery(sb.toString());

    sess.setString("path", path);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    @SuppressWarnings("unchecked")
    List<BwCalendar> cols = sess.getList();

    if (!Util.isEmpty(cols)) {
      for (BwCalendar col: cols) {
        sess.delete(col);
      }
    }
  }

  private void checkNewCalendarName(final String name,
                                    final boolean special,
                                    final BwCalendar parent) throws CalFacadeException {
    // XXX This should be accessible to all implementations.
    if (!special) {
      BasicSystemProperties sys = getSyspars();

      /* Ensure the name isn't reserved */

      if (name.equals(sys.getUserInbox())) {
        throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
      }

      if (name.equals(sys.getUserOutbox())) {
        throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
      }

      if (name.equals(sys.getDefaultNotificationsName())) {
        throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
      }
    }

    /* Ensure the name is not-null and contains no invalid characters
     */
    if ((name == null) ||
        name.contains("/")) {
      throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
    }

    /* Ensure the new path is unique */
    String path;
    if (parent == null) {
      path = "";
    } else {
      path = parent.getPath();
    }

    path = Util.buildPath(colPathEndsWithSlash, path, "/", name);
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwCalendar.class.getName());
    sb.append(" col where col.path = :path");

    sess.createQuery(sb.toString());

    sess.setString("path", path);

    BwCalendar col = (BwCalendar)sess.getUnique();

    if (col != null) {
      if (!col.getTombstoned()) {
        throw new CalFacadeException(CalFacadeException.duplicateCalendar);
      }

      sess.delete(col);
    }
  }

  private BwCalendar add(final BwCalendar val,
                         final String parentPath,
                         final boolean special,
                         final int access) throws CalFacadeException {
    HibSession sess = getSess();

    BwCalendar parent = null;
    String newPath;

    if ("/".equals(parentPath)) {
      // creating a new root
      newPath = Util.buildPath(colPathEndsWithSlash, "/", val.getName());
    } else {
      parent = getCalendar(parentPath, access, false);

      if (parent == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound,
                                     parentPath);
      }

      /** Is the parent a calendar collection or a resource folder?
       */
      if (parent.getCalendarCollection() ||
          (parent.getCalType() == BwCalendar.calTypeResourceCollection)) {
        if (val.getAlias() ||
            ((val.getCalType() != BwCalendar.calTypeFolder) &&
            (val.getCalType() != BwCalendar.calTypeResourceCollection))) {
          throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
        }

        if (val.getCalType() == BwCalendar.calTypeFolder) {
          val.setCalType(BwCalendar.calTypeResourceCollection);
        }
      } else if (parent.getCalType() != BwCalendar.calTypeFolder) {
        throw new CalFacadeException(CalFacadeException.illegalCalendarCreation);
      }

      newPath = Util.buildPath(colPathEndsWithSlash, parent.getPath(), 
                               "/", val.getName());
    }

    /* Ensure the name isn't reserved and is unique */
    checkNewCalendarName(val.getName(), special, parent);

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
    removeTombstonedVersion(val);

    // No cascades - explicitly save child
    sess.save(val);

    if (parent != null) {
      touchCalendar(parent);
    }

    notify(SysEvent.SysCode.COLLECTION_ADDED, val);

    CalendarWrapper wcol = wrap(val);

    colCache.put(wcol);

    return checkAccess(wcol, privAny, true);
  }

  private void removeTombstonedVersion(final BwCalendar val) throws CalFacadeException {
    HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();

    sb.append("from ");
    sb.append(BwCalendar.class.getName());
    sb.append(" col ");
    sb.append("where col.path=:path ");

    sess.createQuery(sb.toString());

    sess.setString("path", val.getPath() + BwCalendar.tombstonedSuffix);

    BwCalendar col = (BwCalendar)sess.getUnique();

    if (col != null) {
      sess.delete(col);
    }
  }

  private void updatePaths(BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    Collection<BwCalendar> children = getChildren(val);

    String oldHref = val.getPath();

    val = unwrap(val);

    String ppath = newParent.getPath();
    val.setPath(Util.buildPath(colPathEndsWithSlash, ppath, "/", 
                               val.getName()));
    val.setColPath(ppath);

    val.getLastmod().setPath(val.getPath());
    val.updateLastmod(getCurrentTimestamp());

    notifyMove(SysEvent.SysCode.COLLECTION_MOVED,
               oldHref, val);

    //updateCalendar(val);

    for (BwCalendar ch: children) {
      updatePaths(ch, val);
    }
  }

  /** Return a Collection of the objects after checking access and wrapping
   *
   * @param ents          Collection of Bwcalendar
   * @param desiredAccess access we want
   * @param nullForNoAccess boolean flag behaviour on no access
   * @return Collection   of checked objects
   * @throws CalFacadeException for no access or other failure
   */
  private Collection<BwCalendar> checkAccess(final Collection<BwCalendar> ents,
                                             final int desiredAccess,
                                             final boolean nullForNoAccess)
          throws CalFacadeException {
    final TreeSet<BwCalendar> out = new TreeSet<>();
    if (ents == null) {
      return out;
    }

    for (BwCalendar cal: ents) {
      cal = checkAccess((CalendarWrapper)cal, desiredAccess, nullForNoAccess);
      if (cal != null) {
        out.add(cal);
      }
    }

    return out;
  }

  private BwCalendar checkAccess(final CalendarWrapper col,
                                 final int desiredAccess,
                                 final boolean alwaysReturnResult)
          throws CalFacadeException {
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

  private void notify(final SysEvent.SysCode code,
                      final BwCalendar val) throws CalFacadeException {
    try {
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
        indexEntity(val);
        postNotification(
           SysEvent.makeCollectionUpdateEvent(code,
                                              authenticatedPrincipal(),
                                              val.getOwnerHref(),
                                              val.getPath(),
                                              val.getShared(),
                                              indexed));
      }
    } catch (final NotificationException ne) {
      throw new CalFacadeException(ne);
    }
  }

  private void notifyMove(final SysEvent.SysCode code,
                          final String oldHref,
                          final BwCalendar val) throws CalFacadeException {
    try {
      final boolean indexed = true;
      getIndexer(val).unindexEntity(oldHref);
      indexEntity(val);

      postNotification(
         SysEvent.makeCollectionMovedEvent(code,
                                           authenticatedPrincipal(),
                                           val.getOwnerHref(),
                                           val.getPath(),
                                           val.getShared(),
                                           indexed,
                                           oldHref,
                                           false)); // XXX wrong
    } catch (final NotificationException ne) {
      throw new CalFacadeException(ne);
    }
  }

  /* No access checks performed */
  @SuppressWarnings("unchecked")
  private Collection<BwCalendar> getChildren(final BwCalendar col) throws CalFacadeException {
    final HibSession sess = getSess();

    StringBuilder sb = new StringBuilder();
    final List<BwCalendar> ch;
    final List<BwCalendar> wch = new ArrayList<BwCalendar>();

    if (col == null) {
      return wch;
    }

    if (sessionless) {
      /*
         Maybe we should just fetch them. We've probably not seen them and
         we're just working our way down a tree. The 2 phase might be slower.
       */

      sb.append("from ");
      sb.append(BwCalendar.class.getName());
      sb.append(" where colPath=:path");

      // XXX tombstone-schema
      sb.append(" and (filterExpr is null or filterExpr <> :tsfilter)");

      sess.createQuery(sb.toString());

      sess.setString("path", col.getPath());
      sess.setString("tsfilter", BwCalendar.tombstonedFilter);

      ch = sess.getList();
    } else {
      /* Fetch the lastmod and paths of all children then fetch those we haven't
       * got in the cache.
       */

      sb.append("select lm.path, lm.timestamp, lm.sequence from ");
      sb.append(BwCollectionLastmod.class.getName());
      sb.append(" lm, ");
      sb.append(BwCalendar.class.getName());
      sb.append(" col where col.colPath=:path and lm.path=col.path");

      // XXX tombstone-schema
      sb.append(" and (col.filterExpr is null or col.filterExpr <> :tsfilter)");

      sess.createQuery(sb.toString());

      sess.setString("path", col.getPath());
      sess.setString("tsfilter", BwCalendar.tombstonedFilter);
      sess.cacheableQuery();

      List chfields = sess.getList();

      List<String> paths = new ArrayList<String>();

      if (chfields == null) {
        return wch;
      }

      for (Object o: chfields) {
        Object[] fs = (Object[])o;

        String path = (String)fs[0];
        String token = BwLastMod.getTagValue((String)fs[1], (Integer)fs[2]);

        BwCalendar c = colCache.get(path, token);

        if ((c != null) && !c.getTombstoned()) {
          wch.add(c);
          continue;
        }

        paths.add(path);
      }

      if (paths.isEmpty()) {
        return wch;
      }

      /* paths lists those we couldn't find in the cache. */

      sb = new StringBuilder();
      sb.append("from ");
      sb.append(BwCalendar.class.getName());
      sb.append(" where path in (:paths)");

      sess.createQuery(sb.toString());

      sess.setParameterList("paths", paths);

      ch = sess.getList();
    }

    /* Wrap the resulting objects. */

    if (ch == null) {
      return wch;
    }

    for (BwCalendar c: ch) {
      CalendarWrapper wc = wrap(c);

      colCache.put(wc);
      wch.add(wc);
    }

    return wch;
  }
}
