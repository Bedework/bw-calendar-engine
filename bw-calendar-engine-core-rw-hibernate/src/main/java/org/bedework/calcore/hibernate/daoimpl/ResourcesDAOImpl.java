/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.base.exc.BedeworkException;
import org.bedework.calcore.rw.common.dao.ResourcesDAO;
import org.bedework.calfacade.BwResource;
import org.bedework.calfacade.BwResourceContent;
import org.bedework.calfacade.exc.CalFacadeErrorCode;
import org.bedework.database.db.DbSession;
import org.bedework.util.misc.Util;

import java.util.List;

import static org.bedework.calfacade.configs.BasicSystemProperties.colPathEndsWithSlash;

/**
 * User: mike Date: 2/1/20 Time: 15:23
 */
public class ResourcesDAOImpl extends DAOBaseImpl implements
        ResourcesDAO {
  /** Constructor
   *
   * @param sess the session
   */
  public ResourcesDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return ResourcesDAOImpl.class.getName();
  }

  private static final String getResourceQuery =
          "select r from BwResource r " +
                  " where r.name=:name and r.colPath=:path" +
                  " and (r.encoding is null or r.encoding <> :tsenc)";

  @Override
  public BwResource getResource(final String name,
                                final String colPath,
                                final int desiredAccess) {
    return (BwResource)createQuery(getResourceQuery)
            .setString("name", name)
            .setString("path", colPath)
            .setString("tsenc", BwResource.tombstoned)
            .getUnique();
  }

  private static final String getResourceContentQuery =
          "select rc from BwResourceContent rc " +
                  "where rc.colPath=:path and rc.name=:name";

  @Override
  public void getResourceContent(final BwResource val) {
    final var rc = (BwResourceContent)createQuery(getResourceContentQuery)
            .setString("path", val.getColPath())
            .setString("name", val.getName())
            .getUnique();

    if (rc == null) {
      throw new BedeworkException(CalFacadeErrorCode.missingResourceContent);
    }

    val.setContent(rc);
  }

  private static final String getAllResourcesQuery =
          "select r from BwResource r " +
                  "where r.colPath=:path" +
                  // No deleted collections for null sync-token or not sync
                  " and (r.encoding is null or r.encoding <> :tsenc)";

  private static final String getAllResourcesSynchQuery =
          "select r from BwResource r " +
                  "where r.colPath=:path" +
                  // Include deleted resources after the token.
                  " and (r.lastmod>:lastmod" +
                  " or (r.lastmod=:lastmod and r.sequence>:seq))"+
                  " order by r.created desc";

  @SuppressWarnings("unchecked")
  @Override
  public List<BwResource> getAllResources(final String path,
                                          final boolean forSynch,
                                          final String token,
                                          final int count) {
    final String q;

    if (forSynch && (token != null)) {
      q = getAllResourcesSynchQuery;
    } else {
      q = getAllResourcesQuery;
    }

    final var sess = createQuery(q)
            .setString("path", Util.buildPath(colPathEndsWithSlash,
                                              path));

    if (forSynch && (token != null)) {
      sess.setString("lastmod", token.substring(0, 16))
          .setInt("seq", Integer.parseInt(token.substring(17), 16));
    } else {
      sess.setString("tsenc", BwResource.tombstoned);
    }

    sess.setFirstResult(0);

    if (count > 0) {
      sess.setMaxResults(count);
    }

    return (List<BwResource>)sess.getList();
  }
}
