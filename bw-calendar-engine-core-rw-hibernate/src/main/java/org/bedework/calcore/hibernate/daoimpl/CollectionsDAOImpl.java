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
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.base.exc.BedeworkBadRequest;
import org.bedework.calcore.rw.common.dao.CollectionsDAO;
import org.bedework.calfacade.BwCollection;
import org.bedework.calfacade.BwCollectionLastmod;
import org.bedework.calfacade.CollectionSynchInfo;
import org.bedework.calfacade.base.BwLastMod;
import org.bedework.calfacade.exc.CalFacadeInvalidSynctoken;
import org.bedework.database.db.DbSession;
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
public class CollectionsDAOImpl extends DAOBaseImpl
        implements CollectionsDAO {
  /** Initialise with a session
   *
   * @param sess the session
   */
  public CollectionsDAOImpl(final DbSession sess) {
    init(sess);
  }

  @Override
  public String getName() {
    return CollectionsDAOImpl.class.getName();
  }
  
  /* ============================================================
   *                   CollectionsI methods
   * ============================================================ */

  private static final String getSynchInfoQuery = 
          "select lm.timestamp, lm.sequence " +
                  "from BwCollectionLastmod lm " +
                  "where path=:path";

  @Override
  public CollectionSynchInfo getSynchInfo(final String path,
                                          final String token) {
    final var lmfields = (Object[])createQuery(getSynchInfoQuery)
            .setString("path", path)
            .getUnique();

    if (lmfields == null) {
      return null;
    }

    final CollectionSynchInfo csi = new CollectionSynchInfo();

    csi.token = BwLastMod.getTagValue((String)lmfields[0], (Integer)lmfields[1]);

    csi.changed = !csi.token.equals(token);

    return csi;
  }

  private final static String findAliasQuery =
          "select cal from BwCollection cal " +
                  "where cal.calType=:caltype " +
                  "and ownerHref=:owner " +
                  "and aliasUri=:alias " +
                  "and (cal.filterExpr is null or cal.filterExpr <> :tsfilter)";
          
  @Override
  public List<BwCollection> findCollectionAlias(final String aliasPath,
                                                final String ownerHref) {
    //noinspection unchecked
    return (List<BwCollection>)createQuery(findAliasQuery)
            .setString("owner", ownerHref)
            .setString("alias", "bwcal://" + aliasPath).setInt("caltype", BwCollection.calTypeAlias)
            .setString("tsfilter", BwCollection.tombstonedFilter)
            .getList();
  }

  private static final String getCollectionByPathQuery =
         "select cal from BwCollection cal " +
           "where cal.path=:path and " +
           "(cal.filterExpr is null or cal.filterExpr <> '--TOMBSTONED--')";

  @Override
  public BwCollection getCollection(final String path) {
    return (BwCollection)createQuery(getCollectionByPathQuery)
            .setString("path", path)
            .getUnique();
  }

  private final static String collectionExistsQuery =
          "select count(*) from BwCollection col " +
                  "where col.path=:path and " +
                  "(col.filterExpr is null or col.filterExpr <> '--TOMBSTONED--')";

  @Override
  public boolean collectionExists(final String path) {
    final Collection<?> refs = createQuery(collectionExistsQuery)
            .setString("path", path)
            .getList();

    final Object o = refs.iterator().next();

    /* Apparently some get a Long - others get Integer */
    if (o instanceof final Long ct) {
      return ct > 0;
    }
    
    final Integer ct = (Integer)o;

    return ct > 0;
  }

  private final static String touchCollectionQuery =
          "update BwCollectionLastmod " +
                  "set timestamp=:timestamp, sequence=:sequence " +
                  "where path=:path";
          
  @Override
  public void touchCollection(final BwCollection col,
                              final Timestamp ts) {
    // CALWRAPPER - if we're not cloning can we avoid this?
    //val = (BwCollection)getSess().merge(val);

    //val = (BwCollection)getSess().merge(val);

    final BwCollectionLastmod lm = col.getLastmod();
    lm.updateLastmod(ts);

    createQuery(touchCollectionQuery)
             .setString("timestamp", lm.getTimestamp())
             .setInt("sequence", lm.getSequence())
             .setString("path", col.getPath())
             .executeUpdate();
  }

  @Override
  public void updateCollection(final BwCollection val) {
    getSess().update(val);
  }

  @Override
  public void addCollection(final BwCollection val) {
    getSess().add(val);
  }

  private static final String removeCollectionPrefForAllQuery =
      "delete from BwAuthUserPrefsCalendar " +
         "where calendarid=:id";

  @Override
  public void removeCollectionFromAuthPrefs(final BwCollection val) {
    createQuery(removeCollectionPrefForAllQuery)
             .setInt("id", val.getId())
             .executeUpdate();
  }

  @Override
  public void deleteCollection(final BwCollection col) {
    final var sess = getSess();

    sess.delete(col);
  }

  private static final String countCalendarEventRefsQuery =
    "select count(*) from BwEventObj ev " +
      "where ev.colPath = :colPath and ev.tombstoned=false";

  private static final String countCollectionChildrenQuery =
      "select count(*) from BwCollection cal " +
        "where cal.colPath = :colPath and " +
        "(cal.filterExpr is null or cal.filterExpr <> '--TOMBSTONED--')";

  @Override
  public boolean isEmptyCollection(final BwCollection val) {
    var res = (Long)createQuery(countCalendarEventRefsQuery)
            .setString("colPath", val.getPath())
            .getUnique();

    if (debug()) {
      debug(" ----------- count = " + res);
    }

    if ((res != null) && (res.intValue() > 0)) {
      return false;
    }

    res = (Long)createQuery(countCollectionChildrenQuery)
            .setString("colPath", val.getPath())
            .getUnique();

    if (debug()) {
      debug(" ----------- count children = " + res);
    }

    return (res == null) || (res.intValue() == 0);
  }

  @Override
  public List<BwCollection> getSynchCollections(
          final String path,
          final String token) {
    final var sess = getSess();

    if (path == null) {
      sess.rollback();
      throw new BedeworkBadRequest("Missing path");
    }

    if ((token != null) && (token.length() < 18)) {
      sess.rollback();
      throw new CalFacadeInvalidSynctoken(token);
    }

    final StringBuilder sb = new StringBuilder()
            .append("select col from BwCollection col ")
            .append("where col.colPath=:path ");

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

    sess.createQuery(sb.toString())
        .setString("path", path);

    if (token != null) {
      sess.setString("lastmod", token.substring(0, 16))
          .setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsfilter", BwCollection.tombstonedFilter);
    }

    //noinspection unchecked
    return (List<BwCollection>)sess.getList();
  }

  private static final String getPathPrefixQuery =
          "select col from BwCollection col " +
                  "where col.path like :path ";
          
  @Override
  public List<BwCollection> getPathPrefix(final String path) {
    //noinspection unchecked
    return (List<BwCollection>)createQuery(getPathPrefixQuery)
            .setString("path", path + "%")
            .getList();
  }

  /* ============================================================
   *                  Admin support
   * ============================================================ */

  private final static String getChildCollectionPathsQuery =
          "select col.path from BwCollection col where " +
                  "(col.filterExpr is null or col.filterExpr <> :tsfilter) and " +
                  "col.colPath";
          
  @Override
  public List<String> getChildrenCollections(final String parentPath,
                                             final int start,
                                             final int count) {
    final DbSession sess;

    if (parentPath == null) {
      sess = createQuery(getChildCollectionPathsQuery +
                                 " is null order by col.path");
    } else {
      sess = createQuery(getChildCollectionPathsQuery +
                                 "=:colPath order by col.path")
              .setString("colPath", parentPath);
    }

    sess.setString("tsfilter", BwCollection.tombstonedFilter);

    if (start >= 0) {
      sess.setFirstResult(start)
          .setMaxResults(count);
    }

    //noinspection unchecked
    final List<String> res = (List<String>)sess.getList();

    if (Util.isEmpty(res)) {
      return null;
    }

    return res;
  }

  private final static String getChildCollectionsQuery =
          "select col from BwCollection col " +
                  "where (col.filterExpr is null or " +
                  "col.filterExpr <> :tsfilter) and " +
                  "col.colPath";

  @Override
  public List<BwCollection> getChildCollections(final String parentPath) {
    final DbSession sess;

    if (parentPath == null) {
      sess = createQuery(getChildCollectionsQuery +
                                 " is null order by col.path");
    } else {
      sess = createQuery(getChildCollectionsQuery +
                                 "=:colPath order by col.path")
              .setString("colPath", parentPath);
    }

    //noinspection unchecked
    return (List<BwCollection>)sess
            .setString("tsfilter", BwCollection.tombstonedFilter)
            .getList();
  }

  private final static String getChildLastModsAndPathsQuery =
          "select lm.path, lm.timestamp, lm.sequence " +
                  "from BwCollectionLastmod lm, BwCollection col " +
                  "where col.colPath=:path and lm.path=col.path" +

                  // XXX tombstone-schema
                  " and (col.filterExpr is null or " +
                  "col.filterExpr <> :tsfilter)";

  @Override
  public List<LastModAndPath> getChildLastModsAndPaths(
          final String parentPath) {
    final var chfields = createQuery(getChildLastModsAndPathsQuery)
            .setString("path", parentPath)
            .setString("tsfilter", BwCollection.tombstonedFilter)
            .getList();

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
          "select col from BwCollection col " +
                  "where path in (:paths)";
  
  @Override
  public List<BwCollection> getCollections(final List<String> paths) {
    //noinspection unchecked
    return (List<BwCollection>)createQuery(getCollectionsQuery)
            .setParameterList("paths", paths)
            .getList();
  }

  /* ============================================================
   *                   Private methods
   * ============================================================ */

  private static final String removeTombstonedCollectionEventsQuery =
          "delete from BwEventObj ev" +
                  " where ev.tombstoned = true and " +
                  "ev.colPath = :path";

  private static final String getTombstonedCollectionsQuery =
          "select col from BwCollection col " +
                  "where col.colPath = :path and " +

                  // XXX tombstone-schema
                  "col.filterExpr = :tsfilter";
          
  @Override
  public void removeTombstoned(final String path) {
    createQuery(removeTombstonedCollectionEventsQuery)
             .setString("path", path)
             .executeUpdate();

    //noinspection unchecked
    final var cols = (List<BwCollection>)
            createQuery(getTombstonedCollectionsQuery)
            .setString("path", path)
            .setString("tsfilter", BwCollection.tombstonedFilter)
            .getList();

    if (!Util.isEmpty(cols)) {
      for (final BwCollection col: cols) {
        getSess().delete(col);
      }
    }
  }

  @Override
  public void removeTombstonedVersion(final BwCollection val) {
    final BwCollection col =
            getTombstonedCollection(val.getPath());

    if (col != null) {
      deleteCollection(col);
    }
  }

  private static final String getTombstonedCollectionByPathQuery =
          "select col from BwCollection col " +
                  "where col.path=:path and " +

                  // XXX tombstone-schema
                  "col.filterExpr = :tsfilter";


  @Override
  public BwCollection getTombstonedCollection(final String path) {
    return (BwCollection)
            createQuery(getTombstonedCollectionByPathQuery)
            .setString("path", path)
            .setString("tsfilter", BwCollection.tombstonedFilter)
            .getUnique();
  }
}
