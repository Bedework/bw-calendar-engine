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

import org.bedework.base.exc.BedeworkBadRequest;
import org.bedework.calfacade.BwCalendar;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.BwEventObj;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwLastMod;
import org.bedework.calfacade.exc.CalFacadeInvalidSynctoken;
import org.bedework.calfacade.svc.prefs.BwAuthUserPrefsCalendar;
import org.bedework.util.misc.Util;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** The actual collections interactions with the database.
 *
 * @author douglm
 *
 */
class CoreCalendarsDAO extends DAOBase {
  /** Constructor
   *
   * @param sess the session
   */
  CoreCalendarsDAO(final HibSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return CoreCalendarsDAO.class.getName();
  }
  
  /* ====================================================================
   *                   CalendarsI methods
   * ==================================================================== */

  private static final String getSynchInfoQuery = 
          "select lm.timestamp, lm.sequence from " + 
                  BwCollectionLastmod.class.getName() +
                  " lm where path=:path";

  protected CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    final HibSession sess = getSess();

    sess.createQuery(getSynchInfoQuery);

    sess.setString("path", path);

    final Object[] lmfields = (Object[])sess.getUnique();

    if (lmfields == null) {
      return null;
    }

    final CollectionSynchInfo csi = new CollectionSynchInfo();

    csi.token = BwLastMod.getTagValue((String)lmfields[0], (Integer)lmfields[1]);

    csi.changed = !csi.token.equals(token);

    return csi;
  }

  private final static String findAliasQuery =
          "from " + BwCalendar.class.getName() + " as cal" +
                  " where cal.calType=:caltype" +
                  " and ownerHref=:owner" +
                  " and aliasUri=:alias" +
                  " and (cal.filterExpr = null or cal.filterExpr <> :tsfilter)";
          
  public List<BwCalendar> findCollectionAlias(final String aliasPath,
                                              final String ownerHref) {
    final HibSession sess = getSess();

    sess.createQuery(findAliasQuery);

    sess.setString("owner", ownerHref);
    sess.setString("alias", "bwcal://" + aliasPath);
    sess.setInt("caltype", BwCalendar.calTypeAlias);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    //noinspection unchecked
    return (List<BwCalendar>)sess.getList();
  }

  private static final String getCalendarByPathQuery =
         "from " + BwCalendar.class.getName() + " as cal " +
           "where cal.path=:path and " +
           "(cal.filterExpr = null or cal.filterExpr <> '--TOMBSTONED--')";

  public BwCalendar getCollection(final String path) {
    final HibSession sess = getSess();

    sess.createQuery(getCalendarByPathQuery);
    sess.setString("path", path);

    return (BwCalendar)sess.getUnique();
  }

  private final static String collectionExistsQuery =
          "select count(*) from " + BwCalendar.class.getName() + " col " +
                  "where col.path=:path and " +
                  "(col.filterExpr = null or col.filterExpr <> '--TOMBSTONED--')";

  public boolean collectionExists(final String path) {
    final HibSession sess = getSess();

    sess.createQuery(collectionExistsQuery);

    sess.setString("path", path);

    final Collection<?> refs = sess.getList();

    final Object o = refs.iterator().next();

    /* Apparently some get a Long - others get Integer */
    if (o instanceof Long) {
      final Long ct = (Long)o;
      return ct > 0;
    }
    
    final Integer ct = (Integer)o;

    return ct > 0;
  }

  private final static String touchCalendarQuery =
          "update " +
                  BwCollectionLastmod.class.getName() +
                  " set timestamp=:timestamp, sequence=:sequence" +
                  " where path=:path";
          
  protected void touchCollection(final BwCalendar col,
                                 final Timestamp ts) {
    // CALWRAPPER - if we're not cloning can we avoid this?
    //val = (BwCalendar)getSess().merge(val);

    //val = (BwCalendar)getSess().merge(val);

    final BwCollectionLastmod lm = col.getLastmod();
    lm.updateLastmod(ts);

    final HibSession sess = getSess();

    sess.createQuery(touchCalendarQuery);

    sess.setString("timestamp", lm.getTimestamp());
    sess.setInt("sequence", lm.getSequence());
    sess.setString("path", col.getPath());

    sess.executeUpdate();
  }

  public void updateCollection(final BwCalendar val) {
    getSess().update(val);
  }

  protected void addCollection(final BwCalendar val) {
    getSess().add(val);
  }

  private static final String removeCalendarPrefForAllQuery =
      "delete from " + BwAuthUserPrefsCalendar.class.getName() +
         " where calendarid=:id";

  protected void removeCalendarFromAuthPrefs(final BwCalendar val) {
    final HibSession sess = getSess();

    sess.createQuery(removeCalendarPrefForAllQuery);
    sess.setInt("id", val.getId());

    sess.executeUpdate();
  }

  protected void deleteCalendar(final BwCalendar col) {
    final HibSession sess = getSess();

    sess.delete(col);
  }

  private static final String countCalendarEventRefsQuery =
    "select count(*) from " + BwEventObj.class.getName() + " as ev " +
      "where ev.colPath = :colPath and ev.tombstoned=false";

  private static final String countCalendarChildrenQuery =
      "select count(*) from " + BwCalendar.class.getName() + " as cal " +
        "where cal.colPath = :colPath and " +
        "(cal.filterExpr = null or cal.filterExpr <> '--TOMBSTONED--')";

  public boolean isEmptyCollection(final BwCalendar val) {
    final HibSession sess = getSess();

    sess.createQuery(countCalendarEventRefsQuery);
    sess.setString("colPath", val.getPath());

    Long res = (Long)sess.getUnique();

    if (debug()) {
      debug(" ----------- count = " + res);
    }

    if ((res != null) && (res.intValue() > 0)) {
      return false;
    }

    sess.createQuery(countCalendarChildrenQuery);
    sess.setString("colPath", val.getPath());

    res = (Long)sess.getUnique();

    if (debug()) {
      debug(" ----------- count children = " + res);
    }

    return (res == null) || (res.intValue() == 0);
  }

  protected List<BwCalendar> getSynchCollections(
          final String path,
          final String token) {
    final HibSession sess = getSess();

    if (path == null) {
      sess.rollback();
      throw new BedeworkBadRequest("Missing path");
    }

    if ((token != null) && (token.length() < 18)) {
      sess.rollback();
      throw new CalFacadeInvalidSynctoken(token);
    }

    final StringBuilder sb = new StringBuilder();

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

    sess.setString("path", path);

    if (token != null) {
      sess.setString("lastmod", token.substring(0, 16));
      sess.setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsfilter", BwCalendar.tombstonedFilter);
    }

    return (List<BwCalendar>)sess.getList();
  }

  private static final String getPathPrefixQuery =
          "from " + BwCalendar.class.getName() + " col " +
                  "where col.path like :path ";
          
  public List<BwCalendar> getPathPrefix(final String path) {
    final HibSession sess = getSess();

    sess.createQuery(getPathPrefixQuery);

    sess.setString("path", path + "%");

    return (List<BwCalendar>)sess.getList();
  }

  /* ====================================================================
   *                  Admin support
   * ==================================================================== */

  private final static String getChildCollectionPathsQuery =
          "select col.path from " +
                  BwCalendar.class.getName() +
                  " col where " +
                  "(col.filterExpr is null or col.filterExpr <> :tsfilter) and " +
                  "col.colPath";
          
  public List<String> getChildrenCollections(final String parentPath,
                                             final int start,
                                             final int count) {
    final HibSession sess = getSess();

    if (parentPath == null) {
      sess.createQuery(getChildCollectionPathsQuery + " is null order by col.path");
    } else {
      sess.createQuery(getChildCollectionPathsQuery + "=:colPath order by col.path");
      sess.setString("colPath", parentPath);
    }

    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    if (start >= 0) {
      sess.setFirstResult(start);
      sess.setMaxResults(count);
    }

    final List<String> res = (List<String>)sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  private final static String getChildCollectionsQuery =
          "from " + BwCalendar.class.getName() +" col " +
                  "where (col.filterExpr is null or " +
                  "col.filterExpr <> :tsfilter) and " +
                  "col.colPath";

  @SuppressWarnings("unchecked")
  protected List<BwCalendar> getChildCollections(final String parentPath) {
    final HibSession sess = getSess();

    if (parentPath == null) {
      sess.createQuery(getChildCollectionsQuery + " is null order by col.path");
    } else {
      sess.createQuery(getChildCollectionsQuery + "=:colPath order by col.path");
      sess.setString("colPath", parentPath);
    }

    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    return (List<BwCalendar>)sess.getList();
  }

  private final static String getChildLastModsAndPathsQuery =
          "select lm.path, lm.timestamp, lm.sequence from " +
                  BwCollectionLastmod.class.getName()+ " lm, " +
                  BwCalendar.class.getName() + " col " +
                  "where col.colPath=:path and lm.path=col.path" +

                  // XXX tombstone-schema
                  " and (col.filterExpr is null or " +
                  "col.filterExpr <> :tsfilter)";
  
  static class LastModAndPath {
    String path;
    String timestamp;
    Integer sequence;

    private LastModAndPath(final String path,
                           final String timestamp,
                           final Integer sequence) {
      this.path = path;
      this.timestamp = timestamp;
      this.sequence = sequence;
    }
  }
  
  protected List<LastModAndPath> getChildLastModsAndPaths(final String parentPath) {
    final HibSession sess = getSess();

    sess.createQuery(getChildLastModsAndPathsQuery);

    sess.setString("path", parentPath);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    final List<?> chfields = sess.getList();

    final List<LastModAndPath> res = new ArrayList<>();
    
    if (chfields == null) {
      return res;
    }

    for (final Object o: chfields) {
      final Object[] fs = (Object[])o;

 
      res.add(new LastModAndPath((String)fs[0],
                                 (String)fs[1], 
                                 (Integer)fs[2]));
    }
    
    return res;
  }

  private final static String getCollectionsQuery =
          "from " + BwCalendar.class.getName() +" col " +
                  "where path in (:paths)";
  
  protected List<BwCalendar> getCollections(final List<String> paths) {
    final HibSession sess = getSess();
    sess.createQuery(getCollectionsQuery);

    sess.setParameterList("paths", paths);

    return (List<BwCalendar>)sess.getList();
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private static final String removeTombstonedCollectionEventsQuery =
          "delete from " + BwEventObj.class.getName() + " ev" +
                  " where ev.tombstoned = true and " +
                  "ev.colPath = :path";

  private static final String getTombstonedCollectionsQuery =
          "from " + BwCalendar.class.getName() + " col" +
                  " where col.colPath = :path and " +

                  // XXX tombstone-schema
                  "col.filterExpr = :tsfilter";
          
  protected void removeTombstoned(final String path) {
    final HibSession sess = getSess();

    sess.createQuery(removeTombstonedCollectionEventsQuery);

    sess.setString("path", path);

    sess.executeUpdate();

    sess.createQuery(getTombstonedCollectionsQuery);

    sess.setString("path", path);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    @SuppressWarnings("unchecked")
    final List<BwCalendar> cols = (List<BwCalendar>)sess.getList();

    if (!Util.isEmpty(cols)) {
      for (final BwCalendar col: cols) {
        sess.delete(col);
      }
    }
  }

  protected void removeTombstonedVersion(final BwCalendar val) {
    final BwCalendar col =
            getTombstonedCollection(val.getPath());

    if (col != null) {
      deleteCalendar(col);
    }
  }

  private static final String getTombstonedCollectionByPathQuery =
          "from " + BwCalendar.class.getName() + " as col " +
                  "where col.path=:path and " +

                  // XXX tombstone-schema
                  "col.filterExpr = :tsfilter";


  public BwCalendar getTombstonedCollection(final String path) {
    final HibSession sess = getSess();

    sess.createQuery(getTombstonedCollectionByPathQuery);
    sess.setString("path", path);
    sess.setString("tsfilter", BwCalendar.tombstonedFilter);

    return (BwCalendar)sess.getUnique();
  }
}
