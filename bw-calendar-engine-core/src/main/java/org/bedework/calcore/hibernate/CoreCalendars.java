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
import org.bedework.calcore.AccessUtil;
import org.bedework.calcore.CalintfHelper;
import org.bedework.calcore.Transactions;
import org.bedework.calcorei.CoreCalendarsI;
import org.bedework.calcorei.HibSession;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.calfacade.CalFacadeDefs;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwLastMod;
import org.bedework.calfacade.configs.BasicSystemProperties;
import org.bedework.calfacade.exc.CalFacadeAccessException;
import org.bedework.calfacade.exc.CalFacadeException;
import org.bedework.calfacade.indexing.BwIndexer;
import org.bedework.calfacade.util.AccessChecker;
import org.bedework.calfacade.wrappers.CalendarWrapper;
import org.bedework.sysevents.events.SysEvent;
import org.bedework.util.calendar.PropertyIndex;
import org.bedework.util.misc.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/** Class to encapsulate most of what we do with collections
 *
 * @author douglm
 *
 */
class CoreCalendars extends CalintfHelper
         implements AccessUtil.CollectionGetter, Transactions,
        CoreCalendarsI {
  private final CoreCalendarsDAO dao;
  private final String userCalendarRootPath;
  //private String groupCalendarRootPath;
  
  private final CollectionCache colCache;

  /** Constructor
   *
   * @param sess persistance session
   * @param cb callback
   * @param ac access checker
   * @param currentMode of access
   * @param sessionless if true
   * @throws CalFacadeException on fatal error
   */
  CoreCalendars(final HibSession sess, 
                final Callback cb,
                final AccessChecker ac,
                final int currentMode,
                final boolean sessionless)
          throws CalFacadeException {
    dao = new CoreCalendarsDAO(sess);
    cb.registerDao(dao);
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

  @Override
  public void rollback() throws CalFacadeException {
    dao.rollback();
  }

  @Override
  public void throwException(final CalFacadeException cfe)
          throws CalFacadeException {
    dao.rollback();
    throw cfe;
  }

  @Override
  public BwCalendar getCollection(final String path) throws CalFacadeException {
    if (path == null) {
      return null;
    }

    BwCalendar col = colCache.get(path);

    if (col != null) {
      return col;
    }

    col = dao.getCollection(path);

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
    return dao.getSynchInfo(path, token);
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
    final List<BwCalendar> aliases = dao.findCollectionAlias(val,
                                                             currentPrincipal());

    final List<BwCalendar> waliases = new ArrayList<>();

    if (Util.isEmpty(aliases)) {
      return waliases;
    }

    for (final BwCalendar alias: aliases) {
      waliases.add(wrap(alias));
    }

    return waliases;
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

  @Override
  public GetSpecialCalendarResult getSpecialCalendar(final BwPrincipal owner,
                                                     final int calType,
                                                     final boolean create,
                                                     final int access) throws CalFacadeException {
    return getSpecialCalendar(owner, calType, create, true, access);
  }

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

    final BwCalendar parent = dao.getCollection(val.getColPath());

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
    dao.removeTombstonedVersion(val);

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
    dao.save(tombstoned);

    /* This triggers off a cascade of updates down the tree as we are storing the
     * path in the calendar objects. This may be preferable to calculating the
     * path at every access
     */
    updatePaths(val, newParent);

    /* Remove any tombstoned collection with the same name */
    dao.removeTombstonedVersion(val);

    // Flush it again
    colCache.flush();
  }

  @Override
  public void touchCalendar(final String path) throws CalFacadeException {
    final BwCalendar col = dao.getCollection(path);
    if (col == null) {
      return;
    }

    touchCalendar(col);
  }

  @Override
  public void touchCalendar(final BwCalendar col) throws CalFacadeException {
    dao.touchCollection(col, getCurrentTimestamp());
  }

  @Override
  public void updateCalendar(final BwCalendar val) throws CalFacadeException {
    ac.checkAccess(val, privWriteProperties, false);

    dao.updateCollection(unwrap(val));
    touchCalendar(val.getPath());

    notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

    colCache.put((CalendarWrapper)val);
  }

  @Override
  public void changeAccess(final BwCalendar col,
                           final Collection<Ace> aces,
                           final boolean replaceAll) throws CalFacadeException {
    ac.getAccessUtil().changeAccess(col, aces, replaceAll);

    // Clear the cache - inheritance makes it difficult to be sure of the effects.
    colCache.clear();

    dao.saveOrUpdateCollection(unwrap(col));

    ((CalendarWrapper)col).clearCurrentAccess(); // force recheck
    colCache.put((CalendarWrapper)col);

    notify(SysEvent.SysCode.COLLECTION_UPDATED, col);
  }

  @Override
  public void defaultAccess(final BwCalendar cal,
                            final AceWho who) throws CalFacadeException {
    ac.getAccessUtil().defaultAccess(cal, who);
    dao.saveOrUpdateCollection(unwrap(cal));

    colCache.flush();

    notify(SysEvent.SysCode.COLLECTION_UPDATED, cal);
  }

  @Override
  public boolean deleteCalendar(BwCalendar val,
                                final boolean reallyDelete) throws CalFacadeException {
    colCache.flush();

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

    final BwCalendar unwrapped = unwrap(val);
    final String path = val.getPath();
    
    /* Ensure it's not in any (auth)user preferences */

    dao.removeCalendarFromAuthPrefs(unwrapped);

    /* Ensure no tombstoned events or childen */
    dao.removeTombstoned(path);

    if (reallyDelete) {
      dao.deleteCalendar(unwrapped);
    } else {
      tombstoneEntity(unwrapped);
      unwrapped.tombstone();
      dao.updateCollection(unwrapped);
      touchCalendar(unwrapped);
    }

    colCache.remove(path);
    touchCalendar(parent);

    notify(SysEvent.SysCode.COLLECTION_DELETED, val);
    getIndexer(val).unindexEntity(path);

    return true;
  }

  @Override
  public boolean isEmpty(final BwCalendar val) throws CalFacadeException {
    return dao.isEmptyCollection(val);
  }
  
  @Override
  public void addNewCalendars(final BwPrincipal user) throws CalFacadeException {
    /* Add a user collection to the userCalendarRoot and then a default
       calendar collection. */

    String path =  userCalendarRootPath;
    final BwCalendar userrootcal = dao.getCollection(path);

    if (userrootcal == null) {
      throw new CalFacadeException("No user root at " + path);
    }

    BwCalendar parentCal = userrootcal;
    BwCalendar usercal = null;

    /* We may have a principal e.g. /principals/resources/vcc311
     * All except the last may exist already.
     */
    final String[] upath = user.getAccountSplit();

    for (int i = 0; i < upath.length; i++) {
      final String pathSeg = upath[i];

      if ((pathSeg == null) || (pathSeg.length() == 0)) {
        // Leading or double slash - skip it
        continue;
      }

      path = Util.buildPath(colPathEndsWithSlash, path, "/", pathSeg);

      usercal = dao.getCollection(path);
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

        dao.saveCollection(usercal);
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

        dao.saveCollection(usercal);
      }

      parentCal = usercal;
    }

    if (usercal == null) {
      throw new CalFacadeException("Invalid user " + user);
    }

    /* Create a default calendar */
    final BwCalendar cal = new BwCalendar();
    cal.setName(getSyspars().getUserDefaultCalendar());
    cal.setCreatorHref(user.getPrincipalRef());
    cal.setOwnerHref(user.getPrincipalRef());
    cal.setPublick(false);
    cal.setPath(Util.buildPath(colPathEndsWithSlash, path, "/", cal.getName()));
    cal.setColPath(usercal.getPath());
    cal.setCalType(BwCalendar.calTypeCalendarCollection);
    cal.setAffectsFreeBusy(true);

    dao.saveCollection(cal);
  }

  @Override
  public Set<BwCalendar> getSynchCols(final String path,
                                      final String token) throws CalFacadeException {
    @SuppressWarnings("unchecked")
    final List<BwCalendar> cols = dao.getSynchCollections(path, token);

    final Set<BwCalendar> res = new TreeSet<>();

    for (final BwCalendar col: cols) {
      final BwCalendar wcol = wrap(col);
      final CurrentAccess ca = ac.checkAccess(wcol, privAny, true);
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
    final BwCalendar thisCol = getCalendar(path, privAny, false);
    
    if (thisCol == null) {
      return null;
    }

    @SuppressWarnings("unchecked")
    final List<BwCalendar> cols = dao.getPathPrefix(thisCol.getPath());

    String token = thisCol.getLastmod().getTagValue();

    for (final BwCalendar col: cols) {
      final BwCalendar wcol = wrap(col);
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
  @SuppressWarnings("unchecked")
  public Collection<String> getChildCollections(final String parentPath,
                                                final int start,
                                                final int count) throws CalFacadeException {
    return dao.getChildrenCollections(parentPath, start, count);
  }
  
  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

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

    if (!dao.collectionExists(pathTo)) {
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

      final BwCalendar res = resolveAlias(c, true, freeBusy, pathElements, indexer);
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

      return null;
    }
    
    val.setAliasTarget(col);

    if (!resolveSubAlias) {
      col.setAliasOrigin(val);
      return col;
    }

    final BwCalendar res = resolveAlias(col, true, freeBusy, pathElements, indexer);
    res.setAliasOrigin(val);

    return res;
  }

  private void disableAlias(final BwCalendar val) throws CalFacadeException {
    val.setDisabled(true);
    if (val.getId() != CalFacadeDefs.unsavedItemKey) {
      // Save the state
      val.updateLastmod(getCurrentTimestamp());
      dao.updateCollection(unwrap(val));
      //touchCalendar(val.getPath());

      notify(SysEvent.SysCode.COLLECTION_UPDATED, val);

      colCache.put((CalendarWrapper)val);
    }
  }

  private void checkNewCalendarName(final String name,
                                    final boolean special,
                                    final BwCalendar parent) throws CalFacadeException {
    // XXX This should be accessible to all implementations.
    if (!special) {
      final BasicSystemProperties sys = getSyspars();

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
    final BwCalendar col = dao.getCollection(path);

    if (col != null) {
      if (!col.getTombstoned()) {
        throw new CalFacadeException(CalFacadeException.duplicateCalendar);
      }

      dao.deleteCalendar(unwrap(col));
    }
  }

  private BwCalendar add(final BwCalendar val,
                         final String parentPath,
                         final boolean special,
                         final int access) throws CalFacadeException {
    BwCalendar parent = null;
    final String newPath;

    if ("/".equals(parentPath)) {
      // creating a new root
      newPath = Util.buildPath(colPathEndsWithSlash, "/", val.getName());
    } else {
      parent = getCalendar(parentPath, access, false);

      if (parent == null) {
        throw new CalFacadeException(CalFacadeException.collectionNotFound,
                                     parentPath);
      }

      /* Is the parent a calendar collection or a resource folder?
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
    dao.removeTombstonedVersion(val);

    // No cascades - explicitly save child
    dao.saveCollection(unwrap(val));

    if (parent != null) {
      touchCalendar(parent);
    }

    notify(SysEvent.SysCode.COLLECTION_ADDED, val);

    final CalendarWrapper wcol = wrap(val);

    colCache.put(wcol);

    return checkAccess(wcol, privAny, true);
  }

  private void updatePaths(BwCalendar val,
                           final BwCalendar newParent) throws CalFacadeException {
    final Collection<BwCalendar> children = getChildren(val);

    final String oldHref = val.getPath();

    val = unwrap(val);

    final String ppath = newParent.getPath();
    val.setPath(Util.buildPath(colPathEndsWithSlash, ppath, "/", 
                               val.getName()));
    val.setColPath(ppath);

    val.getLastmod().setPath(val.getPath());
    val.updateLastmod(getCurrentTimestamp());

    notifyMove(SysEvent.SysCode.COLLECTION_MOVED,
               oldHref, val);

    //updateCalendar(val);

    for (final BwCalendar ch: children) {
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

  BwCalendar checkAccess(final CalendarWrapper col,
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
  }

  private void notifyMove(final SysEvent.SysCode code,
                          final String oldHref,
                          final BwCalendar val) throws CalFacadeException {
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
  }

  /* No access checks performed */
  @SuppressWarnings("unchecked")
  private Collection<BwCalendar> getChildren(final BwCalendar col) throws CalFacadeException {
    final List<BwCalendar> ch;
    final List<BwCalendar> wch = new ArrayList<>();

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

      final List<CoreCalendarsDAO.LastModAndPath> lmps =
              dao.getChildLastModsAndPaths(col.getPath());

      final List<String> paths = new ArrayList<>();

      if (Util.isEmpty(lmps)) {
        return wch;
      }

      for (final CoreCalendarsDAO.LastModAndPath lmp: lmps) {
        final String token = BwLastMod.getTagValue(lmp.timestamp, 
                                                   lmp.sequence);

        final BwCalendar c = colCache.get(lmp.path, token);

        if ((c != null) && !c.getTombstoned()) {
          wch.add(c);
          continue;
        }

        paths.add(lmp.path);
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

    for (final BwCalendar c: ch) {
      final CalendarWrapper wc = wrap(c);

      colCache.put(wc);
      wch.add(wc);
    }

    return wch;
  }
}
