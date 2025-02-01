/* ********************************************************************
    Appropriate copyright notice
*/
package org.bedework.calcore.hibernate.daoimpl;

import org.bedework.calcore.rw.common.dao.CalSuitesDAO;
import org.bedework.calfacade.svc.BwAdminGroup;
import org.bedework.calfacade.svc.BwCalSuite;
import org.bedework.database.db.DbSession;

import java.util.Collection;

/**
 * User: mike
 * Date: 11/21/16
 * Time: 23:30
 */
public class CalSuitesDAOImpl extends DAOBaseImpl
        implements CalSuitesDAO {
  /**
   * @param sess             the session
   */
  public CalSuitesDAOImpl(final DbSession sess) {
    super(sess);
  }

  @Override
  public String getName() {
    return CalSuitesDAOImpl.class.getName();
  }

  private static final String getCalSuiteByGroupQuery =
          "select cal from BwCalSuite cal " +
                  "where cal.group=:group";

  @Override
  public BwCalSuite get(final BwAdminGroup group) {
    final var sess = getSess();

    sess.createQuery(getCalSuiteByGroupQuery);

    sess.setEntity("group", group);

    final BwCalSuite cs = (BwCalSuite)sess.getUnique();

    if (cs != null){
      sess.evict(cs);
    }

    return cs;
  }

  private static final String getCalSuiteQuery =
          "select cal from BwCalSuite cal " +
                  "where cal.name=:name";

  @Override
  public BwCalSuite getCalSuite(final String name) {
    final var sess = getSess();

    sess.createQuery(getCalSuiteQuery);

    sess.setString("name", name);

    return (BwCalSuite)sess.getUnique();
  }

  private static final String getAllCalSuitesQuery =
          "select ent from BwCalSuite ent";

  @SuppressWarnings("unchecked")
  @Override
  public Collection<BwCalSuite> getAllCalSuites() {
    final var sess = getSess();

    sess.createQuery(getAllCalSuitesQuery);

    return (Collection<BwCalSuite>)sess.getList();
  }
}
