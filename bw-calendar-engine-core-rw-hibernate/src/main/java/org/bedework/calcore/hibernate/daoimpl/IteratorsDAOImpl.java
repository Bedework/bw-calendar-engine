/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.rw.common.dao.IteratorsDAO;
import org.bedework.calfacade.BwPrincipal;
import org.bedework.database.db.DbSession;

import java.util.List;

/**
 * User: mike Date: 2/1/20 Time: 15:23
 */
public class IteratorsDAOImpl extends DAOBaseImpl
        implements IteratorsDAO {
  /** Constructor
   *
   * @param sess the session
   */
  public IteratorsDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return IteratorsDAOImpl.class.getName();
  }

  @Override
  public List<?> getBatch(final String className,
                          final int start,
                          final int size) {
    final var sess = getSess();

    sess.createQuery("select colPath, name from " + className +
                             " order by dtstart.dtval desc");

    sess.setFirstResult(start);
    sess.setMaxResults(size);

    return sess.getList();
  }

  @Override
  public List<?> getBatch(final String className,
                          final String colPath,
                          final String ownerHref,
                          final boolean publicAdmin,
                          final int start, final int size) {
    String query = "from " + className;

    boolean doneWhere = false;

    if (colPath != null) {
      query += " where colPath=:colPath";
      doneWhere = true;
    }

    if ((ownerHref != null) | publicAdmin) {
      if (!doneWhere) {
        query += " where";
        doneWhere = true;
      } else {
        query += " and";
      }
      query += " ownerHref=:ownerHref";
    }

    final var sess = getSess();

    sess.createQuery(query);

    if (colPath != null) {
      sess.setString("colPath", colPath);
    }

    if (publicAdmin) {
      sess.setString("ownerHref", BwPrincipal.publicUserHref);
    } else if (ownerHref != null) {
      sess.setString("ownerHref", ownerHref);
    }

    sess.setFirstResult(start);
    sess.setMaxResults(size);

    return sess.getList();
  }
}
